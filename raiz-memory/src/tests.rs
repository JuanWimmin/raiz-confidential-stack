//! Integration tests — deterministic, no network.
//!
//! The RPC is mocked with a local axum server speaking the exact JSON-RPC
//! shape verified against the live testnet on 2026-08-02 (see src/rpc.rs).
//! The /events API is exercised over real HTTP against the real router
//! handler. Databases are throwaway SQLite files under target/test-dbs/.

use crate::{db, ingest, rpc::RpcClient, AppState};
use axum::{
    extract::State,
    routing::{get, post},
    Json, Router,
};
use serde_json::{json, Value};
use std::collections::VecDeque;
use std::sync::{Arc, Mutex};

// ---------------------------------------------------------------- helpers

/// Fresh throwaway SQLite database (file-backed: sqlx in-memory pools give
/// every pooled connection its own empty db, so files are the deterministic
/// choice). Unique name per test → tests can run in parallel.
async fn test_pool(name: &str) -> sqlx::SqlitePool {
    std::fs::create_dir_all("target/test-dbs").expect("create target/test-dbs");
    let path = format!("target/test-dbs/{name}.db");
    for suffix in ["", "-wal", "-shm"] {
        let _ = std::fs::remove_file(format!("{path}{suffix}"));
    }
    db::init(&format!("sqlite://{path}?mode=rwc")).await.expect("db init")
}

/// Canned-response mock of the Soroban RPC. Pops one getEvents result per
/// call (in order) and records the params it was called with.
struct MockRpc {
    responses: Mutex<VecDeque<Value>>,
    requests: Mutex<Vec<Value>>,
    latest_ledger: i64,
}

async fn mock_rpc_handler(State(mock): State<Arc<MockRpc>>, Json(body): Json<Value>) -> Json<Value> {
    let result = match body["method"].as_str().unwrap_or("") {
        "getLatestLedger" => json!({ "sequence": mock.latest_ledger, "protocolVersion": 27 }),
        "getEvents" => {
            mock.requests.lock().unwrap().push(body["params"].clone());
            mock.responses.lock().unwrap().pop_front().unwrap_or(json!({
                "events": [], "latestLedger": mock.latest_ledger, "cursor": null
            }))
        }
        other => panic!("mock RPC got unexpected method {other}"),
    };
    Json(json!({ "jsonrpc": "2.0", "id": 1, "result": result }))
}

/// Serve a router on an ephemeral local port; returns the base URL.
async fn serve(app: Router) -> String {
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.expect("bind");
    let addr = listener.local_addr().expect("local addr");
    tokio::spawn(async move {
        axum::serve(listener, app).await.expect("serve");
    });
    format!("http://{addr}")
}

async fn spawn_mock_rpc(mock: Arc<MockRpc>) -> String {
    serve(Router::new().route("/", post(mock_rpc_handler)).with_state(mock)).await
}

/// The real /events router, same handler main() wires up.
async fn spawn_events_api(pool: sqlx::SqlitePool, retention_simulation_ledgers: Option<i64>) -> String {
    let state = Arc::new(AppState { pool, retention_simulation_ledgers });
    serve(Router::new().route("/events", get(crate::events)).with_state(state)).await
}

async fn get_json(url: &str) -> Value {
    reqwest::get(url).await.expect("GET").json().await.expect("json body")
}

async fn count_events(pool: &sqlx::SqlitePool) -> i64 {
    use sqlx::Row;
    sqlx::query("SELECT COUNT(*) AS n FROM events")
        .fetch_one(pool)
        .await
        .expect("count")
        .get("n")
}

/// Event id in the RPC's paging-token format for `ledger` ("TOID-eventIndex",
/// TOID = ledger << 32), zero-padded so ids sort lexicographically in
/// emission order — the property /events pagination relies on.
fn event_id(ledger: i64, index: u32) -> String {
    format!("{:019}-{:010}", (ledger as u64) << 32, index)
}

fn raw_event(ledger: i64, index: u32) -> Value {
    json!({
        "type": "contract",
        "id": event_id(ledger, index),
        "contractId": "CMOCKCONTRACT",
        "ledger": ledger,
        "ledgerClosedAt": "2026-08-02T12:00:00Z",
        "txHash": format!("hash-{ledger}-{index}"),
        "inSuccessfulContractCall": true,
        "topic": ["AAAADwAAAAh0cmFuc2Zlcg=="],
        "value": "AAAAAQ=="
    })
}

async fn insert_plain_event(pool: &sqlx::SqlitePool, contract: &str, ledger: i64, index: u32) {
    db::insert_event(
        pool,
        &event_id(ledger, index),
        contract,
        ledger,
        Some("txhash"),
        "[\"AAAADwAAAAh0cmFuc2Zlcg==\"]",
        "AAAAAQ==",
        Some("2026-08-02T12:00:00Z"),
        true,
    )
    .await
    .expect("insert");
}

// ------------------------------------------------------------------ tests

/// (a) Same event id inserted twice → exactly one row.
#[tokio::test]
async fn insert_event_is_idempotent() {
    let pool = test_pool("idempotent").await;
    insert_plain_event(&pool, "CMOCKCONTRACT", 100, 0).await;
    insert_plain_event(&pool, "CMOCKCONTRACT", 100, 0).await; // same id again
    assert_eq!(count_events(&pool).await, 1, "duplicate insert must not create a second row");
}

/// (b) Two ingest batches against a mock RPC: the cursor advances batch to
/// batch, the second call resumes FROM the stored cursor, and an event
/// re-sent by the RPC in the second batch stays a single row.
#[tokio::test]
async fn ingest_cursor_advances_across_batches() {
    let pool = test_pool("cursor_batches").await;
    let contract = "CMOCKCONTRACT";

    // Cursors in the live format: TOID-eventIndex, ledger = TOID >> 32.
    let cursor_batch1 = format!("{:019}-{}", 100u64 << 32, 4294967295u32); // scan position: ledger 100
    let cursor_batch2 = format!("{:019}-{}", 200u64 << 32, 4294967295u32); // scan position: ledger 200

    let mock = Arc::new(MockRpc {
        responses: Mutex::new(VecDeque::from([
            // Batch 1: two events at ledger 100, scan caught up to head (100).
            json!({
                "events": [raw_event(100, 0), raw_event(100, 1)],
                "latestLedger": 100,
                "cursor": cursor_batch1,
                "oldestLedger": 1
            }),
            // Batch 2: one NEW event at ledger 200 + a re-send of an already
            // stored event (RPC pages can overlap after a restart).
            json!({
                "events": [raw_event(100, 1), raw_event(200, 0)],
                "latestLedger": 200,
                "cursor": cursor_batch2,
                "oldestLedger": 1
            }),
        ])),
        requests: Mutex::new(Vec::new()),
        latest_ledger: 100,
    });
    let rpc_url = spawn_mock_rpc(mock.clone()).await;
    let rpc = RpcClient::new(rpc_url);

    // Batch 1 (first run: starts at the mock's current ledger).
    ingest::tick(&pool, &rpc, contract).await.expect("tick 1");
    let (cursor, last_ledger) = db::get_cursor(&pool, contract).await.expect("cursor 1");
    assert_eq!(cursor.as_deref(), Some(cursor_batch1.as_str()));
    assert_eq!(last_ledger, 100, "scanned-through ledger comes from the cursor TOID");
    assert_eq!(count_events(&pool).await, 2);

    // Batch 2 (resumes from the stored cursor).
    ingest::tick(&pool, &rpc, contract).await.expect("tick 2");
    let (cursor, last_ledger) = db::get_cursor(&pool, contract).await.expect("cursor 2");
    assert_eq!(cursor.as_deref(), Some(cursor_batch2.as_str()), "cursor must advance");
    assert_eq!(last_ledger, 200);
    assert_eq!(count_events(&pool).await, 3, "re-sent event must not duplicate");

    // The mock saw: first call by startLedger, second call by stored cursor.
    let requests = mock.requests.lock().unwrap();
    assert_eq!(requests.len(), 2);
    assert_eq!(requests[0]["startLedger"], json!(100));
    assert!(requests[0]["pagination"].get("cursor").is_none());
    assert_eq!(requests[1]["pagination"]["cursor"], json!(cursor_batch1));
}

/// (c) /events cursor pagination over HTTP: two pages, stable ascending
/// order, no overlap between pages.
#[tokio::test]
async fn events_endpoint_paginates_without_overlap() {
    let pool = test_pool("pagination").await;
    let contract = "CPAGINATE";
    for ledger in [101, 102, 103, 104, 105] {
        insert_plain_event(&pool, contract, ledger, 0).await;
    }
    let base = spawn_events_api(pool, None).await;

    let page1 = get_json(&format!("{base}/events?contractId={contract}&limit=2")).await;
    let ids1: Vec<&str> = page1["events"].as_array().unwrap().iter().map(|e| e["id"].as_str().unwrap()).collect();
    assert_eq!(ids1, vec![event_id(101, 0), event_id(102, 0)]);
    let cursor = page1["cursor"].as_str().expect("page 1 must return a cursor");
    assert_eq!(cursor, event_id(102, 0), "cursor is the last returned event id");

    let page2 = get_json(&format!("{base}/events?contractId={contract}&limit=2&cursor={cursor}")).await;
    let ids2: Vec<&str> = page2["events"].as_array().unwrap().iter().map(|e| e["id"].as_str().unwrap()).collect();
    assert_eq!(ids2, vec![event_id(103, 0), event_id(104, 0)], "page 2 continues after the cursor");

    assert!(ids1.iter().all(|id| !ids2.contains(id)), "pages must not overlap");
    assert_eq!(page1["latestLedger"], json!(105));
}

/// Purge-demo mode: `source=rpc-simulation` + RETENTION_SIMULATION_LEDGERS
/// hides everything older than the last N ledgers; every other combination
/// leaves behavior untouched.
#[tokio::test]
async fn purge_demo_mode_forgets_old_ledgers_only_when_asked() {
    let pool = test_pool("purge_demo").await;
    let contract = "CPURGEDEMO";
    for ledger in [100, 195, 200] {
        insert_plain_event(&pool, contract, ledger, 0).await;
    }

    // Flag armed: simulate an RPC that only retains the last 10 ledgers.
    let base = spawn_events_api(pool.clone(), Some(10)).await;

    let normal = get_json(&format!("{base}/events?contractId={contract}")).await;
    assert_eq!(normal["events"].as_array().unwrap().len(), 3, "no source param → full history");
    assert!(normal.get("oldestLedger").is_none(), "normal response shape unchanged");

    let simulated = get_json(&format!("{base}/events?contractId={contract}&source=rpc-simulation")).await;
    let ledgers: Vec<i64> = simulated["events"].as_array().unwrap().iter().map(|e| e["ledger"].as_i64().unwrap()).collect();
    assert_eq!(ledgers, vec![195, 200], "latest=200, N=10 → floor 191: ledger 100 is forgotten");
    assert_eq!(simulated["oldestLedger"], json!(191), "simulated RPC declares its retention floor");

    // Flag not set: the source param is inert.
    let base_off = spawn_events_api(pool, None).await;
    let unaffected = get_json(&format!("{base_off}/events?contractId={contract}&source=rpc-simulation")).await;
    assert_eq!(unaffected["events"].as_array().unwrap().len(), 3, "no flag → param ignored");
    assert!(unaffected.get("oldestLedger").is_none());
}
