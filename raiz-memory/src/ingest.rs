//! Ingestor loop: one task per contract. Polls getEvents from the last cursor,
//! persists raw events, advances the cursor. Testnet-flaky-proof: every error
//! backs off and retries; the cursor only advances after a successful write.

use crate::{db, rpc::RpcClient};
use sqlx::SqlitePool;
use std::time::Duration;

const PAGE_LIMIT: u32 = 100;

pub async fn run(pool: SqlitePool, rpc_url: String, contract_id: String, poll_secs: u64) {
    let rpc = RpcClient::new(rpc_url);
    tracing::info!(%contract_id, "ingestor started");

    loop {
        if let Err(e) = tick(&pool, &rpc, &contract_id).await {
            tracing::warn!(%contract_id, error = %e, "tick failed, backing off");
            tokio::time::sleep(Duration::from_secs(poll_secs * 3)).await;
            continue;
        }
        tokio::time::sleep(Duration::from_secs(poll_secs)).await;
    }
}

// pub(crate) so the integration tests (src/tests.rs) can drive single ingest
// batches deterministically against a mock RPC — `run` itself never returns.
pub(crate) async fn tick(pool: &SqlitePool, rpc: &RpcClient, contract_id: &str) -> anyhow::Result<()> {
    let (cursor, last_ledger) = db::get_cursor(pool, contract_id).await?;

    // First run: start at the current ledger (the RPC rejects startLedger outside
    // its retention window). Backfill of older history, if an archive is available,
    // is a stretch goal — until then /coverage declares exactly where we begin.
    let start = if cursor.is_none() && last_ledger == 0 {
        let latest = rpc.get_latest_ledger().await?;
        tracing::info!(%contract_id, latest, "first run — starting at current ledger");
        Some(latest)
    } else {
        Some(last_ledger)
    };

    let mut cursor = cursor;
    loop {
        let page = rpc.get_events(contract_id, start, cursor.as_deref(), PAGE_LIMIT).await?;
        let n = page.events.len();

        for ev in &page.events {
            let topics = serde_json::to_string(&ev.topic)?;
            // Live RPC (verified 2026-08-02) sends a plain base64 string; older
            // builds wrapped it as {"xdr": "..."} — unwrap that, don't store the
            // JSON object as if it were XDR.
            let value = match &ev.value {
                serde_json::Value::String(s) => s.clone(),
                other => other
                    .get("xdr")
                    .and_then(|v| v.as_str())
                    .map(str::to_owned)
                    .unwrap_or_else(|| other.to_string()),
            };
            db::insert_event(
                pool,
                &ev.id,
                &ev.contract_id,
                ev.ledger,
                ev.tx_hash.as_deref(),
                &topics,
                &value,
                ev.ledger_closed_at.as_deref(),
                ev.in_successful_contract_call,
            )
            .await?;
        }

        let next = page.cursor.or(page.paging_token);
        // Honest coverage: `latestLedger` is the chain head, not how far this scan
        // got (each call scans ~10k ledgers). The scanned position lives in the
        // cursor's TOID; fall back to the last event's ledger.
        let scanned_through = next
            .as_deref()
            .and_then(crate::rpc::cursor_ledger)
            .or_else(|| page.events.last().map(|e| e.ledger))
            .unwrap_or(0);
        if let Some(c) = &next {
            db::set_cursor(pool, contract_id, c, scanned_through.max(1)).await?;
        }
        let prev = cursor;
        cursor = next;

        // Caught up when the cursor's scan position reaches the chain head: the
        // live RPC caps each call at ~10k ledgers and returns an advanced cursor
        // even for empty pages (verified 2026-08-02), so an empty page mid-window
        // must NOT end the loop. Older RPCs whose cursors we can't decode fall
        // back to page-fullness; a cursor that stops advancing always ends the
        // loop so a quirky RPC can't spin us hot.
        let caught_up = match cursor.as_deref().and_then(crate::rpc::cursor_ledger) {
            Some(scanned) if page.latest_ledger > 0 => scanned >= page.latest_ledger,
            _ => n < PAGE_LIMIT as usize,
        };
        if caught_up || cursor == prev {
            break; // wait for the next poll
        }
    }
    Ok(())
}
