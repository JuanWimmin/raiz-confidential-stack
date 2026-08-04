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

        -- Where each contract's history actually begins, decided on its first
        -- run and never revised afterwards. Separate from `cursors` on purpose:
        -- the cursor says how far forward we got, this says how far back we
        -- could reach — and whether the RPC had already forgotten some of it.
        CREATE TABLE IF NOT EXISTS backfill_marks (
            contract_id            TEXT PRIMARY KEY,
            mode                   TEXT    NOT NULL,   -- 'head' | 'oldest' | 'ledger'
            requested_start_ledger INTEGER,            -- NULL for the sentinels
            effective_start_ledger INTEGER NOT NULL,   -- where we really started
            rpc_oldest_ledger      INTEGER,            -- the RPC's floor at that moment
            clamped                INTEGER NOT NULL DEFAULT 0,
            unreachable_ledgers    INTEGER NOT NULL DEFAULT 0,
            recorded_at            TEXT    NOT NULL
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

/// Record where a contract's backfill starts.
///
/// Only ever called on the first-run path (no cursor yet), so it refreshes
/// while the first scan is still failing and retrying, and is then frozen
/// forever by the existence of a cursor. Restarting the process cannot rewrite
/// it into a prettier number.
pub async fn record_backfill_mark(
    pool: &SqlitePool,
    contract_id: &str,
    plan: &crate::backfill::StartPlan,
) -> anyhow::Result<()> {
    sqlx::query(
        "INSERT INTO backfill_marks
             (contract_id, mode, requested_start_ledger, effective_start_ledger,
              rpc_oldest_ledger, clamped, unreachable_ledgers, recorded_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, strftime('%Y-%m-%dT%H:%M:%SZ','now'))
         ON CONFLICT(contract_id) DO UPDATE SET
             mode = excluded.mode,
             requested_start_ledger = excluded.requested_start_ledger,
             effective_start_ledger = excluded.effective_start_ledger,
             rpc_oldest_ledger = excluded.rpc_oldest_ledger,
             clamped = excluded.clamped,
             unreachable_ledgers = excluded.unreachable_ledgers,
             recorded_at = excluded.recorded_at",
    )
    .bind(contract_id)
    .bind(plan.mode)
    .bind(plan.requested)
    .bind(plan.effective)
    .bind(plan.rpc_oldest)
    .bind(if plan.clamped { 1i64 } else { 0i64 })
    .bind(plan.unreachable)
    .execute(pool)
    .await?;
    Ok(())
}

/// Re-clamp a mark after the RPC rejected our start ledger mid-flight (the
/// retention floor slid forward between reading it and using it). Recomputes
/// `clamped`/`unreachable_ledgers` from the ledger actually requested, so the
/// numbers stay consistent with what the operator asked for.
pub async fn reclamp_backfill_mark(
    pool: &SqlitePool,
    contract_id: &str,
    rpc_oldest: i64,
) -> anyhow::Result<()> {
    sqlx::query(
        "UPDATE backfill_marks SET
             effective_start_ledger = ?1,
             rpc_oldest_ledger      = ?1,
             clamped = CASE WHEN requested_start_ledger IS NOT NULL
                             AND requested_start_ledger < ?1 THEN 1 ELSE clamped END,
             unreachable_ledgers = CASE WHEN requested_start_ledger IS NOT NULL
                                         AND requested_start_ledger < ?1
                                    THEN ?1 - requested_start_ledger
                                    ELSE unreachable_ledgers END
         WHERE contract_id = ?2",
    )
    .bind(rpc_oldest)
    .bind(contract_id)
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
///
/// Also reports where history *begins*: `historyBeginsAtLedger` is the first
/// ledger we actually scanned, and the `backfill` object says what was asked
/// for versus what the RPC could still give. A clamped contract admits how many
/// ledgers were already unreachable — an indexer that hides that is worse than
/// no indexer. Contracts appear here from their first run onwards, even before
/// they have a cursor or a single event.
pub async fn coverage(pool: &SqlitePool) -> anyhow::Result<Vec<Value>> {
    let rows = sqlx::query(
        "SELECT ids.contract_id AS contract_id,
                COALESCE(c.last_ledger, 0) AS last_ledger,
                COALESCE(MIN(e.ledger), 0) AS first_event_ledger,
                COALESCE(MAX(e.ledger), 0) AS last_event_ledger,
                COUNT(e.id) AS event_count,
                b.mode AS mode,
                b.requested_start_ledger AS requested_start_ledger,
                b.effective_start_ledger AS effective_start_ledger,
                b.rpc_oldest_ledger AS rpc_oldest_ledger,
                b.clamped AS clamped,
                b.unreachable_ledgers AS unreachable_ledgers,
                b.recorded_at AS recorded_at
         FROM (SELECT contract_id FROM cursors
               UNION SELECT contract_id FROM backfill_marks) ids
         LEFT JOIN cursors        c ON c.contract_id = ids.contract_id
         LEFT JOIN backfill_marks b ON b.contract_id = ids.contract_id
         LEFT JOIN events         e ON e.contract_id = ids.contract_id
         GROUP BY ids.contract_id
         ORDER BY ids.contract_id",
    )
    .fetch_all(pool)
    .await?;
    Ok(rows
        .iter()
        .map(|r| {
            let first_event: i64 = r.get("first_event_ledger");
            let effective: Option<i64> = r.get("effective_start_ledger");
            let mut out = json!({
                "contractId": r.get::<String, _>("contract_id"),
                "scannedThroughLedger": r.get::<i64, _>("last_ledger"),
                "firstEventLedger": first_event,
                "lastEventLedger": r.get::<i64, _>("last_event_ledger"),
                "eventCount": r.get::<i64, _>("event_count"),
                // The first ledger this index ever looked at for this contract.
                // Anything older is not "no events" — it is "we were not there".
                "historyBeginsAtLedger": effective.unwrap_or(first_event),
            });
            if let Some(mode) = r.get::<Option<String>, _>("mode") {
                let requested: Option<i64> = r.get("requested_start_ledger");
                let clamped = r.get::<i64, _>("clamped") != 0;
                let unreachable: i64 = r.get("unreachable_ledgers");
                let mut backfill = json!({
                    "mode": mode,
                    "requestedStartLedger": requested,
                    "effectiveStartLedger": effective,
                    "rpcOldestLedgerAtStart": r.get::<Option<i64>, _>("rpc_oldest_ledger"),
                    "clamped": clamped,
                    "unreachableLedgers": unreachable,
                    "recordedAt": r.get::<Option<String>, _>("recorded_at"),
                });
                if clamped {
                    backfill["note"] = json!(format!(
                        "requested start ledger {} predates this RPC's retention floor {}; \
                         {} ledgers of history were already unreachable when indexing began",
                        requested.unwrap_or(0),
                        effective.unwrap_or(0),
                        unreachable
                    ));
                }
                out["backfill"] = backfill;
            }
            out
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
