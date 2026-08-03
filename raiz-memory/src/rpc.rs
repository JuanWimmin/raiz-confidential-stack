//! Minimal Soroban RPC JSON-RPC client — only what the ingestor needs.
//! TODO(day 0): run one real getEvents call against the testnet RPC and adjust
//! field names if the deployed RPC version differs (cursor vs pagingToken, etc.).

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
