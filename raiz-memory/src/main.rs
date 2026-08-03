//! Raiz Memory — durable event index for private wallets on Stellar.
//! "We index ciphertext; your privacy budget is untouched."
//!
//! Config (env / .env):
//!   RPC_URL            Soroban RPC endpoint (testnet)
//!   CONTRACT_IDS       comma-separated contract ids to index (CT wrapper, goal_meta, SPP pool)
//!   DATABASE_URL       e.g. sqlite://raiz_memory.db?mode=rwc
//!   POLL_INTERVAL_SECS default 5
//!   PORT               default 8090
//!   RETENTION_SIMULATION_LEDGERS  optional; enables the purge-demo mode
//!                      (see README "Purge demo mode"): requests carrying
//!                      `source=rpc-simulation` only see the last N ledgers.

mod db;
mod ingest;
mod rpc;

#[cfg(test)]
mod tests;

use axum::{
    extract::{Query, State},
    routing::get,
    Json, Router,
};
use serde::Deserialize;
use serde_json::json;
use std::sync::Arc;

#[derive(Clone)]
pub struct AppState {
    pub pool: sqlx::SqlitePool,
    /// Purge-demo mode (video prop): when Some(n), requests that ask for
    /// `source=rpc-simulation` only see events from the last n ledgers —
    /// an in-house stand-in for "an RPC that forgets". Normal requests are
    /// never affected.
    pub retention_simulation_ledgers: Option<i64>,
}

#[tokio::main]
async fn main() -> anyhow::Result<()> {
    dotenvy::dotenv().ok();
    tracing_subscriber::fmt::init();

    let database_url =
        std::env::var("DATABASE_URL").unwrap_or_else(|_| "sqlite://raiz_memory.db?mode=rwc".into());
    let rpc_url = std::env::var("RPC_URL").expect("RPC_URL is required");
    let contract_ids: Vec<String> = std::env::var("CONTRACT_IDS")
        .expect("CONTRACT_IDS is required")
        .split(',')
        .map(|s| s.trim().to_string())
        .filter(|s| !s.is_empty())
        .collect();
    let poll_secs: u64 = std::env::var("POLL_INTERVAL_SECS")
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(5);
    let port: u16 = std::env::var("PORT").ok().and_then(|v| v.parse().ok()).unwrap_or(8090);
    let retention_simulation_ledgers: Option<i64> = std::env::var("RETENTION_SIMULATION_LEDGERS")
        .ok()
        .and_then(|v| v.parse().ok());
    if let Some(n) = retention_simulation_ledgers {
        tracing::info!(ledgers = n, "purge-demo mode armed: ?source=rpc-simulation will only see the last {n} ledgers");
    }

    let pool = db::init(&database_url).await?;
    let state = Arc::new(AppState { pool: pool.clone(), retention_simulation_ledgers });

    // One ingestor task per contract — a lagging contract never blocks the others.
    for cid in contract_ids {
        let pool = pool.clone();
        let rpc = rpc_url.clone();
        tokio::spawn(async move {
            ingest::run(pool, rpc, cid, poll_secs).await;
        });
    }

    let app = Router::new()
        .route("/health", get(health))
        .route("/coverage", get(coverage))
        .route("/events", get(events))
        .with_state(state);

    let listener = tokio::net::TcpListener::bind(("0.0.0.0", port)).await?;
    tracing::info!("raiz-memory listening on :{port}");
    axum::serve(listener, app).await?;
    Ok(())
}

async fn health(State(st): State<Arc<AppState>>) -> Json<serde_json::Value> {
    let latest = db::latest_ledger(&st.pool).await.unwrap_or(0);
    Json(json!({ "status": "ok", "latest_indexed_ledger": latest }))
}

/// Honest coverage: which ledger ranges we hold, per contract, gaps declared.
/// An indexer that lies by omission is worse than none — this is money infrastructure.
async fn coverage(State(st): State<Arc<AppState>>) -> Json<serde_json::Value> {
    match db::coverage(&st.pool).await {
        Ok(c) => Json(json!({ "contracts": c })),
        Err(e) => Json(json!({ "error": e.to_string() })),
    }
}

#[derive(Deserialize)]
struct EventsQuery {
    #[serde(rename = "contractId")]
    contract_id: String,
    #[serde(rename = "startLedger")]
    start_ledger: Option<i64>,
    cursor: Option<String>,
    limit: Option<i64>,
    /// `source=rpc-simulation` opts a request into the purge-demo mode
    /// (only honored when RETENTION_SIMULATION_LEDGERS is set).
    source: Option<String>,
}

/// getEvents-shaped response so a wallet adopts us by changing one URL.
/// Diffed against the live RPC's getEvents JSON on 2026-08-02 (see src/rpc.rs):
/// we return the compatible subset { latestLedger, events, cursor }; events
/// mirror the RPC field-for-field except operationIndex/transactionIndex.
async fn events(
    State(st): State<Arc<AppState>>,
    Query(q): Query<EventsQuery>,
) -> Json<serde_json::Value> {
    let limit = q.limit.unwrap_or(100).min(1000);

    // Purge-demo mode: with RETENTION_SIMULATION_LEDGERS=n set AND the request
    // carrying `source=rpc-simulation`, we answer as "an RPC that forgets":
    // only events from the last n indexed ledgers exist. Either condition
    // missing → behavior is exactly as before.
    let sim_floor = match (st.retention_simulation_ledgers, q.source.as_deref()) {
        (Some(n), Some("rpc-simulation")) => {
            let latest = db::latest_ledger(&st.pool).await.unwrap_or(0);
            Some((latest - n + 1).max(0))
        }
        _ => None,
    };

    match db::events(&st.pool, &q.contract_id, q.start_ledger, q.cursor.as_deref(), limit, sim_floor).await {
        Ok((events, next_cursor, latest)) => {
            let mut body = json!({
                "latestLedger": latest,
                "events": events,
                "cursor": next_cursor,
            });
            if let Some(floor) = sim_floor {
                // Mirror the real RPC, which declares its retention floor.
                body["oldestLedger"] = json!(floor);
            }
            Json(body)
        }
        Err(e) => Json(json!({ "error": e.to_string() })),
    }
}
