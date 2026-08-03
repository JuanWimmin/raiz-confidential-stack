//! Minimal Soroban RPC JSON-RPC client — only what the ingestor needs.
//!
//! Real testnet getEvents shape verified 2026-08-02 (soroban-testnet.stellar.org,
//! protocolVersion 27, latestLedger ~3940700):
//!   result: { "events": [...], "cursor": "0016449724743679999-4294967295",
//!             "latestLedger": 3940684, "oldestLedger": 3819725,
//!             "latestLedgerCloseTime": "1785723206", "oldestLedgerCloseTime": "1785117110" }
//!   event:  { "type": "contract", "ledger": 3820041 (number),
//!             "ledgerClosedAt": "2026-07-27T02:18:13Z", "contractId": "C...",
//!             "id": "0016406951164395520-0000000000", "operationIndex": 0,
//!             "transactionIndex": 4, "txHash": "846e21ff...",
//!             "inSuccessfulContractCall": true,
//!             "topic": ["<base64 XDR>", ...], "value": "<base64 XDR string>" }
//! Notes from the live calls:
//!   - top-level pagination token is `cursor` (no `pagingToken` seen); we keep
//!     tolerating both spellings for older RPC builds (gotcha #5).
//!   - `value` is a plain base64 string today; older RPCs wrapped it as
//!     {"xdr": "..."} — the ingestor unwraps both.
//!   - a `cursor` is returned even when `events` is empty: each getEvents call
//!     scans at most ~10k ledgers, so empty pages still advance the cursor.
//!   - `latestLedger` is the chain head, NOT how far the scan got; the scanned
//!     position is encoded in the cursor's TOID (ledger = toid >> 32).
//!   - getLatestLedger result: { "id", "protocolVersion": 27, "sequence": 3940652,
//!     "headerXdr", "metadataXdr" } — we only read `sequence`.
//!   - an invalid contract id in a filter rejects the WHOLE call with
//!     code -32602 "filter 1 invalid: contract ID N invalid" (see friction-report).

use serde::Deserialize;
use serde_json::{json, Value};

pub struct RpcClient {
    http: reqwest::Client,
    url: String,
}

#[derive(Debug, Deserialize)]
pub struct RawEvent {
    pub id: String,
    #[serde(rename = "contractId")]
    pub contract_id: String,
    pub ledger: i64,
    #[serde(rename = "txHash")]
    pub tx_hash: Option<String>,
    #[serde(default)]
    pub topic: Vec<String>, // base64 XDR
    pub value: Value, // base64 XDR string (older RPCs wrap it in an object)
    #[serde(rename = "ledgerClosedAt")]
    pub ledger_closed_at: Option<String>,
    /// Present on the live testnet RPC (verified 2026-08-02); default to true
    /// for older builds that omit it — they only returned successful calls.
    #[serde(rename = "inSuccessfulContractCall", default = "default_true")]
    pub in_successful_contract_call: bool,
}

fn default_true() -> bool {
    true
}

#[derive(Debug, Deserialize)]
pub struct GetEventsResult {
    #[serde(default)]
    pub events: Vec<RawEvent>,
    #[serde(rename = "latestLedger", default)]
    pub latest_ledger: i64,
    /// Newer RPCs return `cursor`; keep both spellings just in case.
    pub cursor: Option<String>,
    #[serde(rename = "pagingToken")]
    pub paging_token: Option<String>,
    /// Retention floor of this RPC (verified live 2026-08-02: ~7 days behind
    /// head). Not consumed yet — first-run backfill from the retention floor
    /// is a stretch goal (see ingest.rs); kept so the shape is documented.
    #[allow(dead_code)]
    #[serde(rename = "oldestLedger")]
    pub oldest_ledger: Option<i64>,
}

/// The scan position encoded in a getEvents cursor: "TOID-eventIndex",
/// where ledger = TOID >> 32. Verified against live cursors 2026-08-02
/// (e.g. "0016449724743679999-4294967295" -> ledger 3829999).
pub fn cursor_ledger(cursor: &str) -> Option<i64> {
    let toid: u64 = cursor.split('-').next()?.parse().ok()?;
    Some((toid >> 32) as i64)
}

impl RpcClient {
    pub fn new(url: String) -> Self {
        Self { http: reqwest::Client::new(), url }
    }

    async fn call(&self, method: &str, params: Value) -> anyhow::Result<Value> {
        let body = json!({ "jsonrpc": "2.0", "id": 1, "method": method, "params": params });
        let resp: Value = self.http.post(&self.url).json(&body).send().await?.json().await?;
        if let Some(err) = resp.get("error") {
            anyhow::bail!("RPC error from {method}: {err}");
        }
        Ok(resp.get("result").cloned().unwrap_or(Value::Null))
    }

    pub async fn get_latest_ledger(&self) -> anyhow::Result<i64> {
        let r = self.call("getLatestLedger", json!({})).await?;
        Ok(r.get("sequence").and_then(|v| v.as_i64()).unwrap_or(0))
    }

    /// Fetch one page of contract events, either from a cursor or a start ledger.
    pub async fn get_events(
        &self,
        contract_id: &str,
        start_ledger: Option<i64>,
        cursor: Option<&str>,
        limit: u32,
    ) -> anyhow::Result<GetEventsResult> {
        let mut params = json!({
            "filters": [{ "type": "contract", "contractIds": [contract_id] }],
            "pagination": { "limit": limit }
        });
        match cursor {
            Some(c) => params["pagination"]["cursor"] = json!(c),
            None => params["startLedger"] = json!(start_ledger.unwrap_or(0).max(1)),
        }
        let r = self.call("getEvents", params).await?;
        Ok(serde_json::from_value(r)?)
    }
}
