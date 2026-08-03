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

async fn tick(pool: &SqlitePool, rpc: &RpcClient, contract_id: &str) -> anyhow::Result<()> {
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
            let value = match &ev.value {
                serde_json::Value::String(s) => s.clone(),
                other => other.to_string(), // older RPC shape: {"xdr": "..."}
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
            )
            .await?;
        }

        let next = page.cursor.or(page.paging_token);
        let scanned_through = page.events.last().map(|e| e.ledger).unwrap_or(page.latest_ledger);
        if let Some(c) = &next {
            db::set_cursor(pool, contract_id, c, scanned_through.max(1)).await?;
        }
        cursor = next;

        if n < PAGE_LIMIT as usize {
            break; // caught up; wait for the next poll
        }
    }
    Ok(())
}
