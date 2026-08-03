use serde_json::{json, Value};
use sqlx::{Row, SqlitePool};

pub async fn init(url: &str) -> anyhow::Result<SqlitePool> {
    let pool = SqlitePool::connect(url).await?;
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS events (
            id               TEXT PRIMARY KEY,   -- RPC event id / paging token: globally unique
            contract_id      TEXT NOT NULL,
            ledger           INTEGER NOT NULL,
            tx_hash          TEXT,
            topics_json      TEXT NOT NULL,      -- JSON array of base64 XDR topics (raw, uninterpreted)
            value_xdr        TEXT NOT NULL,      -- base64 XDR (ciphertext stays ciphertext)
            ledger_closed_at TEXT,
            in_successful_tx INTEGER DEFAULT 1
        );
        CREATE INDEX IF NOT EXISTS idx_events_contract_ledger ON events(contract_id, ledger);

        CREATE TABLE IF NOT EXISTS cursors (
            contract_id TEXT PRIMARY KEY,
            cursor      TEXT,
            last_ledger INTEGER NOT NULL DEFAULT 0
        );
        "#,
    )
    .execute(&pool)
    .await?;
    Ok(pool)
}

#[allow(clippy::too_many_arguments)]
pub async fn insert_event(
    pool: &SqlitePool,
    id: &str,
    contract_id: &str,
    ledger: i64,
    tx_hash: Option<&str>,
    topics_json: &str,
    value_xdr: &str,
    closed_at: Option<&str>,
    in_successful: bool,
) -> anyhow::Result<()> {
    sqlx::query(
        "INSERT OR IGNORE INTO events (id, contract_id, ledger, tx_hash, topics_json, value_xdr, ledger_closed_at, in_successful_tx)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(id).bind(contract_id).bind(ledger).bind(tx_hash).bind(topics_json).bind(value_xdr).bind(closed_at)
    .bind(if in_successful { 1i64 } else { 0i64 })
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn get_cursor(pool: &SqlitePool, contract_id: &str) -> anyhow::Result<(Option<String>, i64)> {
    let row = sqlx::query("SELECT cursor, last_ledger FROM cursors WHERE contract_id = ?")
        .bind(contract_id)
        .fetch_optional(pool)
        .await?;
    Ok(match row {
        Some(r) => (r.get("cursor"), r.get("last_ledger")),
        None => (None, 0),
    })
}

pub async fn set_cursor(pool: &SqlitePool, contract_id: &str, cursor: &str, ledger: i64) -> anyhow::Result<()> {
    sqlx::query(
        "INSERT INTO cursors (contract_id, cursor, last_ledger) VALUES (?, ?, ?)
         ON CONFLICT(contract_id) DO UPDATE SET cursor = excluded.cursor, last_ledger = excluded.last_ledger",
    )
    .bind(contract_id).bind(cursor).bind(ledger)
    .execute(pool)
    .await?;
    Ok(())
}

pub async fn latest_ledger(pool: &SqlitePool) -> anyhow::Result<i64> {
    let row = sqlx::query("SELECT COALESCE(MAX(ledger), 0) AS l FROM events").fetch_one(pool).await?;
    Ok(row.get("l"))
}

/// Per-contract coverage with gap detection over distinct indexed ledgers.
/// NOTE: a ledger with zero events for a contract is indistinguishable from a hole
/// by looking at `events` alone — the cursor table tells us how far we've *scanned*,
/// which is the honest upper bound we report.
pub async fn coverage(pool: &SqlitePool) -> anyhow::Result<Vec<Value>> {
    let rows = sqlx::query(
        "SELECT c.contract_id, c.last_ledger,
                COALESCE(MIN(e.ledger), 0) AS first_event_ledger,
                COALESCE(MAX(e.ledger), 0) AS last_event_ledger,
                COUNT(e.id) AS event_count
         FROM cursors c LEFT JOIN events e ON e.contract_id = c.contract_id
         GROUP BY c.contract_id",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows
        .iter()
        .map(|r| {
            json!({
                "contractId": r.get::<String, _>("contract_id"),
                "scannedThroughLedger": r.get::<i64, _>("last_ledger"),
                "firstEventLedger": r.get::<i64, _>("first_event_ledger"),
                "lastEventLedger": r.get::<i64, _>("last_event_ledger"),
                "eventCount": r.get::<i64, _>("event_count"),
            })
        })
        .collect())
}

pub async fn events(
    pool: &SqlitePool,
    contract_id: &str,
    start_ledger: Option<i64>,
    cursor: Option<&str>,
    limit: i64,
    // Purge-demo mode (see main.rs): when Some, pretend nothing before this
    // ledger exists. None → identical behavior to before the flag existed.
    retention_floor: Option<i64>,
) -> anyhow::Result<(Vec<Value>, Option<String>, i64)> {
    let floor = retention_floor.unwrap_or(0);
    // Cursor pagination: event ids sort lexicographically in emission order (RPC paging tokens).
    let rows = match cursor {
        Some(c) => {
            sqlx::query(
                "SELECT * FROM events WHERE contract_id = ? AND id > ? AND ledger >= ? ORDER BY id ASC LIMIT ?",
            )
            .bind(contract_id).bind(c).bind(floor).bind(limit)
            .fetch_all(pool)
            .await?
        }
        None => {
            sqlx::query(
                "SELECT * FROM events WHERE contract_id = ? AND ledger >= ? ORDER BY id ASC LIMIT ?",
            )
            .bind(contract_id).bind(start_ledger.unwrap_or(0).max(floor)).bind(limit)
            .fetch_all(pool)
            .await?
        }
    };
    let latest = latest_ledger(pool).await?;
    let next_cursor = rows.last().map(|r| r.get::<String, _>("id"));
    let events = rows
        .iter()
        .map(|r| {
            // Field names mirror the live RPC's getEvents events (verified
            // 2026-08-02): type/ledger/ledgerClosedAt/contractId/id/txHash/
            // inSuccessfulContractCall/topic/value. We don't store
            // operationIndex/transactionIndex (not needed by any consumer yet).
            json!({
                "type": "contract",
                "id": r.get::<String, _>("id"),
                "contractId": r.get::<String, _>("contract_id"),
                "ledger": r.get::<i64, _>("ledger"),
                "txHash": r.get::<Option<String>, _>("tx_hash"),
                "inSuccessfulContractCall": r.get::<Option<i64>, _>("in_successful_tx").unwrap_or(1) != 0,
                "topic": serde_json::from_str::<Value>(&r.get::<String, _>("topics_json")).unwrap_or(json!([])),
                "value": r.get::<String, _>("value_xdr"),
                "ledgerClosedAt": r.get::<Option<String>, _>("ledger_closed_at"),
            })
        })
        .collect();
    Ok((events, next_cursor, latest))
}
