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
//!
//! Retention window, verified live 2026-08-03 (same node):
//!   getHealth result: { "status": "healthy", "latestLedger": 3953872,
//!     "latestLedgerCloseTime": "1785789293", "oldestLedger": 3832913,
//!     "oldestLedgerCloseTime": "1785183187", "ledgerRetentionWindow": 120960 }
//!   A startLedger below `oldestLedger` rejects the call with, verbatim:
//!     { "code": -32600,
//!       "message": "startLedger must be within the ledger range: 3832943 - 3953902" }
//!   The floor slides forward by one ledger every ~5s, so a floor read a moment
//!   ago can already be stale — measured 3832922 -> 3832936 -> 3832944 within a
//!   couple of minutes (see friction-report). That is why the range is read
//!   structurally AND parsed back out of the error as a retry safety net.

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
    /// head). Consumed by the backfill resolver as the fallback source of the
    /// ledger range when `getHealth` is unavailable (see `ledger_range`).
    #[serde(rename = "oldestLedger")]
    pub oldest_ledger: Option<i64>,
}

/// The window of history this RPC can still serve, right now.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct LedgerRange {
    pub oldest: i64,
    pub latest: i64,
}

/// Pull `(oldest, latest)` out of the RPC's verbatim out-of-range complaint:
///   "startLedger must be within the ledger range: 3832943 - 3953902"
/// (code -32600, captured live 2026-08-03). Used only as a retry safety net —
/// see `ledger_range` for why we do not depend on this string.
pub fn parse_ledger_range_error(message: &str) -> Option<(i64, i64)> {
    let tail = message.split("ledger range:").nth(1)?;
    let mut numbers = tail
        .split(|c: char| !c.is_ascii_digit())
        .filter(|s| !s.is_empty())
        .map(str::parse::<i64>);
    let oldest = numbers.next()?.ok()?;
    let latest = numbers.next()?.ok()?;
    Some((oldest, latest))
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

    /// How far back this RPC can still serve events — the backfill floor.
    ///
    /// Source of truth is `getHealth`, which returns `oldestLedger` and
    /// `latestLedger` as *structured fields* (verified live 2026-08-03). We
    /// prefer that over parsing the -32600 error message because the message
    /// is prose: its wording is not part of any API contract and can change
    /// between RPC builds, while the field name is the same one `getEvents`
    /// already returns. The error text is still parsed
    /// (`parse_ledger_range_error`) as a retry safety net, because the floor
    /// moves while we work.
    ///
    /// Fallback for RPC builds whose `getHealth` omits the range: read
    /// `oldestLedger` off a real `getEvents` response, which every version
    /// carries — startLedger = head is always inside the window.
    pub async fn ledger_range(&self) -> anyhow::Result<LedgerRange> {
        if let Ok(health) = self.call("getHealth", json!({})).await {
            let oldest = health.get("oldestLedger").and_then(|v| v.as_i64()).unwrap_or(0);
            let latest = health.get("latestLedger").and_then(|v| v.as_i64()).unwrap_or(0);
            if oldest > 0 && latest > 0 {
                return Ok(LedgerRange { oldest, latest });
            }
        }

        let head = self.get_latest_ledger().await?;
        let probe = self
            .call(
                "getEvents",
                json!({ "startLedger": head.max(1), "pagination": { "limit": 1 } }),
            )
            .await?;
        let probe: GetEventsResult = serde_json::from_value(probe)?;
        let latest = if probe.latest_ledger > 0 { probe.latest_ledger } else { head };
        Ok(LedgerRange { oldest: probe.oldest_ledger.unwrap_or(latest).max(1), latest })
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
