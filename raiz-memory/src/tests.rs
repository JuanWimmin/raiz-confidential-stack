//! Integration tests — deterministic, no network.
//!
//! The RPC is mocked with a local axum server speaking the exact JSON-RPC
//! shape verified against the live testnet on 2026-08-02 (see src/rpc.rs).
//! The /events API is exercised over real HTTP against the real router
//! handler. Databases are throwaway SQLite files under target/test-dbs/.

use crate::{backfill::StartSpec, db, ingest, rpc::RpcClient, AppState};
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
    /// Retention floor this mock advertises through getHealth — i.e. how far
    /// back a backfill can reach before it has to be clamped.
    oldest_ledger: i64,
}

fn mock_rpc(latest_ledger: i64, oldest_ledger: i64, responses: Vec<Value>) -> Arc<MockRpc> {
    Arc::new(MockRpc {
        responses: Mutex::new(VecDeque::from(responses)),
        requests: Mutex::new(Vec::new()),
        latest_ledger,
        oldest_ledger,
    })
}

/// A canned getEvents response that is a JSON-RPC *error* rather than a result
/// — how the real RPC answers a startLedger outside its retention window.
fn rpc_error(code: i64, message: &str) -> Value {
    json!({ "__error": { "code": code, "message": message } })
}

async fn mock_rpc_handler(State(mock): State<Arc<MockRpc>>, Json(body): Json<Value>) -> Json<Value> {
    let result = match body["method"].as_str().unwrap_or("") {
        "getLatestLedger" => json!({ "sequence": mock.latest_ledger, "protocolVersion": 27 }),
        // Shape verified against the live testnet RPC 2026-08-03 (see src/rpc.rs).
        "getHealth" => json!({
            "status": "healthy",
            "latestLedger": mock.latest_ledger,
            "oldestLedger": mock.oldest_ledger,
            "ledgerRetentionWindow": mock.latest_ledger - mock.oldest_ledger,
        }),
        "getEvents" => {
            mock.requests.lock().unwrap().push(body["params"].clone());
            let canned = mock.responses.lock().unwrap().pop_front().unwrap_or(json!({
                "events": [], "latestLedger": mock.latest_ledger, "cursor": null
            }));
            if let Some(err) = canned.get("__error") {
                return Json(json!({ "jsonrpc": "2.0", "id": 1, "error": err }));
            }
            canned
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

/// The real /coverage router, same handler main() wires up.
async fn spawn_coverage_api(pool: sqlx::SqlitePool) -> String {
    let state = Arc::new(AppState { pool, retention_simulation_ledgers: None });
    serve(Router::new().route("/coverage", get(crate::coverage)).with_state(state)).await
}

/// The /coverage entry for one contract, straight out of the query layer.
async fn coverage_of(pool: &sqlx::SqlitePool, contract: &str) -> Value {
    db::coverage(pool)
        .await
        .expect("coverage")
        .into_iter()
        .find(|c| c["contractId"] == contract)
        .unwrap_or(Value::Null)
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

/// A getEvents pagination cursor whose scan position is `ledger` (same TOID
/// encoding the live RPC uses; see `rpc::cursor_ledger`).
fn cursor_at(ledger: i64) -> String {
    format!("{:019}-{}", (ledger as u64) << 32, 4294967295u32)
}

/// One getEvents page: `events`, the chain head, and the cursor the RPC would
/// return. A cursor at the head ledger is what tells the ingestor it caught up.
fn page(events: Vec<Value>, latest_ledger: i64, cursor_ledger: i64, oldest_ledger: i64) -> Value {
    json!({
        "events": events,
        "latestLedger": latest_ledger,
        "cursor": cursor_at(cursor_ledger),
        "oldestLedger": oldest_ledger,
    })
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

    let mock = mock_rpc(
        100,
        1,
        vec![
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
        ],
    );
    let rpc_url = spawn_mock_rpc(mock.clone()).await;
    let rpc = RpcClient::new(rpc_url);

    // Batch 1 (first run with StartSpec::Head: starts at the mock's current ledger).
    ingest::tick(&pool, &rpc, contract, StartSpec::Head).await.expect("tick 1");
    let (cursor, last_ledger) = db::get_cursor(&pool, contract).await.expect("cursor 1");
    assert_eq!(cursor.as_deref(), Some(cursor_batch1.as_str()));
    assert_eq!(last_ledger, 100, "scanned-through ledger comes from the cursor TOID");
    assert_eq!(count_events(&pool).await, 2);

    // Batch 2 (resumes from the stored cursor).
    ingest::tick(&pool, &rpc, contract, StartSpec::Head).await.expect("tick 2");
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

// -------------------------------------------------- historical backfill
//
// Without these, a fresh instance starts at the chain head and holds nothing —
// an index that proves the opposite of this project's claim.

/// (e) An explicit start ledger inside the retention window is used verbatim,
/// and /coverage says history begins there.
#[tokio::test]
async fn backfill_starts_at_the_configured_ledger() {
    let pool = test_pool("backfill_configured").await;
    let contract = "CMOCKCONTRACT";
    // RPC holds ledgers 100..1000; we ask for 500, which it can serve.
    let mock = mock_rpc(1000, 100, vec![page(vec![raw_event(500, 0), raw_event(700, 0)], 1000, 1000, 100)]);
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(500)).await.expect("tick");

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests[0]["startLedger"], json!(500), "the first scan must start where configured");
    assert_eq!(count_events(&pool).await, 2, "history before the head is indexed");

    let cov = coverage_of(&pool, contract).await;
    assert_eq!(cov["historyBeginsAtLedger"], json!(500));
    assert_eq!(cov["backfill"]["mode"], json!("ledger"));
    assert_eq!(cov["backfill"]["requestedStartLedger"], json!(500));
    assert_eq!(cov["backfill"]["effectiveStartLedger"], json!(500));
    assert_eq!(cov["backfill"]["clamped"], json!(false));
    assert_eq!(cov["backfill"]["unreachableLedgers"], json!(0));
}

/// (f) The `oldest` sentinel reaches exactly as far back as the RPC still
/// holds — read from getHealth, not guessed.
#[tokio::test]
async fn backfill_oldest_sentinel_reaches_the_rpc_retention_floor() {
    let pool = test_pool("backfill_oldest").await;
    let contract = "CMOCKCONTRACT";
    let mock = mock_rpc(1000, 137, vec![page(vec![raw_event(140, 0)], 1000, 1000, 137)]);
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Oldest).await.expect("tick");

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests[0]["startLedger"], json!(137), "start at the advertised retention floor");

    let cov = coverage_of(&pool, contract).await;
    assert_eq!(cov["backfill"]["mode"], json!("oldest"));
    assert_eq!(cov["backfill"]["requestedStartLedger"], json!(null), "the sentinel names no ledger");
    assert_eq!(cov["backfill"]["effectiveStartLedger"], json!(137));
    assert_eq!(cov["backfill"]["rpcOldestLedgerAtStart"], json!(137));
    assert_eq!(cov["backfill"]["clamped"], json!(false), "asking for `oldest` can never be clamped");
}

/// (g) A start ledger older than the RPC's floor is clamped, not fatal — and
/// /coverage admits over HTTP exactly how much history was already gone.
/// This is the official-CT-demo case: deployed at 3013364, retention floor
/// ~3.83M, so most of its life is unreachable and we say so.
#[tokio::test]
async fn out_of_range_start_ledger_is_clamped_not_fatal() {
    let pool = test_pool("backfill_clamped").await;
    let contract = "CMOCKCONTRACT";
    // RPC holds 100..1000; we ask for 40 — 60 ledgers it no longer has.
    let mock = mock_rpc(1000, 100, vec![page(vec![raw_event(150, 0)], 1000, 1000, 100)]);
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(40))
        .await
        .expect("a start ledger the RPC can't serve must not be fatal");

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests[0]["startLedger"], json!(100), "clamped up to the retention floor");

    let base = spawn_coverage_api(pool.clone()).await;
    let cov = get_json(&format!("{base}/coverage")).await;
    let entry = &cov["contracts"][0];
    assert_eq!(entry["contractId"], json!(contract));
    assert_eq!(entry["historyBeginsAtLedger"], json!(100), "history begins at the floor, not at 40");
    assert_eq!(entry["backfill"]["requestedStartLedger"], json!(40));
    assert_eq!(entry["backfill"]["effectiveStartLedger"], json!(100));
    assert_eq!(entry["backfill"]["clamped"], json!(true));
    assert_eq!(entry["backfill"]["unreachableLedgers"], json!(60), "40..99 was already gone");
    assert!(
        entry["backfill"]["note"].as_str().unwrap_or("").contains("unreachable"),
        "a clamped contract must explain itself in plain words: {}",
        entry["backfill"]["note"]
    );
}

/// (h) The retention floor slides forward while we work (one ledger per ~5s on
/// testnet), so a floor read a moment ago can already be rejected. The verbatim
/// -32600 message carries the new range: re-clamp from it and carry on.
#[tokio::test]
async fn stale_retention_floor_is_reclamped_from_the_rpc_error() {
    let pool = test_pool("backfill_reclamp").await;
    let contract = "CMOCKCONTRACT";
    let mock = mock_rpc(
        1000,
        100, // getHealth says 100...
        vec![
            // ...but by the time the scan lands, the floor has moved to 120.
            rpc_error(-32600, "startLedger must be within the ledger range: 120 - 1000"),
            page(vec![raw_event(130, 0)], 1000, 1000, 120),
        ],
    );
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(40))
        .await
        .expect("a floor that moved mid-flight must not fail the tick");

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests.len(), 2, "one rejected call, one retry");
    assert_eq!(requests[0]["startLedger"], json!(100), "first attempt used the advertised floor");
    assert_eq!(requests[1]["startLedger"], json!(120), "retry uses the floor from the error message");
    assert_eq!(count_events(&pool).await, 1);

    let cov = coverage_of(&pool, contract).await;
    assert_eq!(cov["backfill"]["effectiveStartLedger"], json!(120), "the mark tracks where we really began");
    assert_eq!(cov["backfill"]["rpcOldestLedgerAtStart"], json!(120));
    assert_eq!(cov["backfill"]["clamped"], json!(true));
    assert_eq!(cov["backfill"]["unreachableLedgers"], json!(80), "requested 40, got 120");
}

/// (i) Re-running an instance must be boring: no duplicate rows, no second
/// backfill, and a cursor that only ever moves forward.
#[tokio::test]
async fn rerunning_does_not_duplicate_events_or_rewind_the_cursor() {
    let pool = test_pool("backfill_rerun").await;
    let contract = "CMOCKCONTRACT";
    let mock = mock_rpc(
        1000,
        100,
        vec![
            // First run: backfill from 500, catches up to the head.
            page(vec![raw_event(500, 0), raw_event(500, 1)], 1000, 1000, 100),
            // Restart: the RPC re-sends an event we already hold, plus a new one.
            page(vec![raw_event(500, 1), raw_event(1100, 0)], 1100, 1100, 100),
        ],
    );
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(500)).await.expect("tick 1");
    let (_, scanned_1) = db::get_cursor(&pool, contract).await.expect("cursor 1");
    let mark_1 = coverage_of(&pool, contract).await["backfill"].clone();
    assert_eq!(count_events(&pool).await, 2);

    // Same process restarted, same config: must resume, not re-backfill.
    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(500)).await.expect("tick 2");

    assert_eq!(count_events(&pool).await, 3, "the re-sent event must not become a second row");
    let (_, scanned_2) = db::get_cursor(&pool, contract).await.expect("cursor 2");
    assert!(scanned_2 >= scanned_1, "the cursor must never rewind: {scanned_1} -> {scanned_2}");
    assert_eq!(scanned_2, 1100);
    assert_eq!(
        coverage_of(&pool, contract).await["backfill"],
        mark_1,
        "the backfill mark is written once and never rewritten",
    );

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests[1]["pagination"]["cursor"], json!(cursor_at(1000)), "run 2 resumes from the cursor");
    assert!(requests[1].get("startLedger").is_none(), "run 2 must not re-issue a startLedger");
}

/// (j) A contract that already has a live cursor is never dragged backwards by
/// configuration — even one asking for a much older ledger.
#[tokio::test]
async fn backfill_never_applies_to_a_contract_that_already_has_a_cursor() {
    let pool = test_pool("backfill_no_regress").await;
    let contract = "CMOCKCONTRACT";
    // A tail that is already at ledger 900.
    db::set_cursor(&pool, contract, &cursor_at(900), 900).await.expect("seed cursor");

    let mock = mock_rpc(1000, 100, vec![page(vec![raw_event(950, 0)], 1000, 1000, 100)]);
    let rpc = RpcClient::new(spawn_mock_rpc(mock.clone()).await);

    ingest::tick(&pool, &rpc, contract, StartSpec::Ledger(50)).await.expect("tick");

    let requests = mock.requests.lock().unwrap().clone();
    assert_eq!(requests.len(), 1);
    assert_eq!(requests[0]["pagination"]["cursor"], json!(cursor_at(900)), "resume from the live cursor");
    assert!(requests[0].get("startLedger").is_none(), "an existing cursor is never overridden by config");

    let (_, scanned) = db::get_cursor(&pool, contract).await.expect("cursor");
    assert_eq!(scanned, 1000, "the tail moved forward, never back to 50");

    let cov = coverage_of(&pool, contract).await;
    assert_eq!(cov["backfill"], json!(null), "no backfill mark: this contract was already being tailed");
}

/// (k) The clamp safety net depends on the RPC's exact wording — pin it to the
/// message captured live on 2026-08-03.
#[test]
fn parse_ledger_range_error_reads_the_verbatim_rpc_message() {
    let verbatim = r#"RPC error from getEvents: {"code":-32600,"message":"startLedger must be within the ledger range: 3832943 - 3953902"}"#;
    assert_eq!(crate::rpc::parse_ledger_range_error(verbatim), Some((3832943, 3953902)));
    // Anything else must not be mistaken for a range.
    assert_eq!(crate::rpc::parse_ledger_range_error("connection reset by peer"), None);
    assert_eq!(
        crate::rpc::parse_ledger_range_error(r#"{"code":-32602,"message":"filter 1 invalid: contract ID 0 invalid"}"#),
        None,
    );
}
