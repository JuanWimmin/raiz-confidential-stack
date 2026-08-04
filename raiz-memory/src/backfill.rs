//! Where each contract's history begins — and what to do when the RPC no
//! longer holds it.
//!
//! A fresh index that starts at the chain head proves nothing: the whole point
//! of Raiz Memory is that RPC nodes forget and we do not. So every contract
//! gets a *start spec* saying how far back to reach on its first run:
//!
//!   head          index only what happens from now on (the pre-backfill behavior)
//!   oldest        reach as far back as this RPC still holds (its retention floor)
//!   <ledger>      an explicit ledger, e.g. the contract's deployment ledger
//!
//! An explicit ledger older than the RPC's retention floor is **clamped**, not
//! fatal and not silent: we start at the floor, log exactly how many ledgers
//! were already unreachable, and `/coverage` reports it forever. History we
//! could not get is a fact about the world, not an error in the indexer.

use std::collections::HashMap;
use std::str::FromStr;

use crate::rpc::RpcClient;

/// Sentinel: as far back as this RPC still holds.
pub const OLDEST: &str = "oldest";
/// Sentinel: the chain head — index only from now on.
pub const HEAD: &str = "head";

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum StartSpec {
    /// Begin at the chain head. Nothing older is indexed.
    Head,
    /// Begin at the RPC's retention floor: everything it still remembers.
    Oldest,
    /// Begin at an explicit ledger, clamped up to the retention floor if older.
    Ledger(i64),
}

impl StartSpec {
    /// Stable tag persisted with the backfill mark and echoed by `/coverage`.
    pub fn mode(&self) -> &'static str {
        match self {
            StartSpec::Head => HEAD,
            StartSpec::Oldest => OLDEST,
            StartSpec::Ledger(_) => "ledger",
        }
    }

    /// The ledger the operator explicitly asked for, if any. `head`/`oldest`
    /// ask a question ("wherever you are now") rather than name a ledger, so
    /// they have nothing to be clamped against.
    pub fn requested(&self) -> Option<i64> {
        match self {
            StartSpec::Ledger(n) => Some(*n),
            _ => None,
        }
    }
}

impl FromStr for StartSpec {
    type Err = String;

    fn from_str(s: &str) -> Result<Self, Self::Err> {
        let t = s.trim();
        if t.eq_ignore_ascii_case(HEAD) {
            return Ok(StartSpec::Head);
        }
        if t.eq_ignore_ascii_case(OLDEST) {
            return Ok(StartSpec::Oldest);
        }
        match t.parse::<i64>() {
            Ok(n) if n > 0 => Ok(StartSpec::Ledger(n)),
            _ => Err(format!(
                "expected a positive ledger number, `{OLDEST}` or `{HEAD}`, got `{t}`"
            )),
        }
    }
}

/// Parsed `CONTRACT_START_LEDGERS` + `BACKFILL_FROM_LEDGER`.
///
/// Hard to misuse on purpose: every value must parse, and every contract id
/// named in `CONTRACT_START_LEDGERS` must also appear in `CONTRACT_IDS`.
/// Both are startup errors. A typo in a contract id used to be invisible and
/// produced exactly the failure this module exists to prevent — an index that
/// silently holds nothing.
#[derive(Debug)]
pub struct StartLedgerConfig {
    default: StartSpec,
    per_contract: HashMap<String, StartSpec>,
}

impl StartLedgerConfig {
    /// What a contract gets when nothing else is configured.
    ///
    /// `oldest`, deliberately: an indexer whose thesis is "RPCs forget, we
    /// don't" must not ship a default that starts empty. It is always safe —
    /// the floor comes from the RPC itself, so it can never be out of range.
    /// Set `BACKFILL_FROM_LEDGER=head` for the old start-at-the-head behavior.
    pub const DEFAULT: StartSpec = StartSpec::Oldest;

    pub fn parse(
        per_contract_raw: Option<&str>,
        default_raw: Option<&str>,
        known_contracts: &[String],
    ) -> anyhow::Result<Self> {
        let default = match default_raw.map(str::trim).filter(|s| !s.is_empty()) {
            Some(v) => StartSpec::from_str(v)
                .map_err(|e| anyhow::anyhow!("BACKFILL_FROM_LEDGER: {e}"))?,
            None => Self::DEFAULT,
        };

        let mut per_contract = HashMap::new();
        for entry in per_contract_raw.unwrap_or("").split(',') {
            let entry = entry.trim();
            if entry.is_empty() {
                continue;
            }
            // Contract ids are base32 (no colons), so the last ':' is the separator.
            let (cid, value) = entry.rsplit_once(':').ok_or_else(|| {
                anyhow::anyhow!(
                    "CONTRACT_START_LEDGERS entry `{entry}` is not `CONTRACTID:LEDGER` \
                     (LEDGER may also be `{OLDEST}` or `{HEAD}`)"
                )
            })?;
            let cid = cid.trim().to_string();
            let spec = StartSpec::from_str(value)
                .map_err(|e| anyhow::anyhow!("CONTRACT_START_LEDGERS entry `{entry}`: {e}"))?;
            if !known_contracts.iter().any(|k| k == &cid) {
                anyhow::bail!(
                    "CONTRACT_START_LEDGERS names contract `{cid}`, which is not in CONTRACT_IDS. \
                     A start ledger for a contract we do not index has no effect — fix the id or \
                     add it to CONTRACT_IDS."
                );
            }
            if per_contract.insert(cid.clone(), spec).is_some() {
                anyhow::bail!("CONTRACT_START_LEDGERS lists contract `{cid}` twice");
            }
        }

        Ok(Self { default, per_contract })
    }

    pub fn from_env(known_contracts: &[String]) -> anyhow::Result<Self> {
        Self::parse(
            std::env::var("CONTRACT_START_LEDGERS").ok().as_deref(),
            std::env::var("BACKFILL_FROM_LEDGER").ok().as_deref(),
            known_contracts,
        )
    }

    pub fn for_contract(&self, contract_id: &str) -> StartSpec {
        self.per_contract.get(contract_id).copied().unwrap_or(self.default)
    }
}

/// What the first run of a contract actually decided, after meeting the RPC's
/// real retention window. Persisted once per contract and surfaced by
/// `/coverage` so the index can never overstate how much history it holds.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct StartPlan {
    /// `head` | `oldest` | `ledger` — what was asked for.
    pub mode: &'static str,
    /// The explicitly configured ledger, if the spec named one.
    pub requested: Option<i64>,
    /// The ledger we actually start scanning from.
    pub effective: i64,
    /// The RPC's retention floor at that moment, as reported by the RPC.
    pub rpc_oldest: Option<i64>,
    /// True when `requested` was older than the floor and had to be raised.
    pub clamped: bool,
    /// How many ledgers of requested history the RPC no longer had.
    pub unreachable: i64,
}

impl StartPlan {
    fn clamp(spec: StartSpec, requested: i64, oldest: i64) -> Self {
        Self {
            mode: spec.mode(),
            requested: Some(requested),
            effective: oldest,
            rpc_oldest: Some(oldest),
            clamped: true,
            unreachable: oldest - requested,
        }
    }
}

/// Decide where this contract's first scan starts, asking the RPC how far back
/// it can actually serve. Clamps (loudly) instead of failing.
pub async fn resolve(
    rpc: &RpcClient,
    contract_id: &str,
    spec: StartSpec,
) -> anyhow::Result<StartPlan> {
    let range = rpc.ledger_range().await?;

    let plan = match spec {
        StartSpec::Head => StartPlan {
            mode: spec.mode(),
            requested: None,
            effective: range.latest.max(1),
            rpc_oldest: Some(range.oldest),
            clamped: false,
            unreachable: 0,
        },
        StartSpec::Oldest => StartPlan {
            mode: spec.mode(),
            requested: None,
            effective: range.oldest.max(1),
            rpc_oldest: Some(range.oldest),
            clamped: false,
            unreachable: 0,
        },
        StartSpec::Ledger(n) if n < range.oldest => StartPlan::clamp(spec, n, range.oldest),
        StartSpec::Ledger(n) => StartPlan {
            mode: spec.mode(),
            requested: spec.requested(),
            effective: n,
            rpc_oldest: Some(range.oldest),
            clamped: false,
            unreachable: 0,
        },
    };

    if plan.clamped {
        tracing::warn!(
            %contract_id,
            requested_start_ledger = plan.requested.unwrap_or(0),
            effective_start_ledger = plan.effective,
            rpc_oldest_ledger = range.oldest,
            unreachable_ledgers = plan.unreachable,
            "backfill CLAMPED: the RPC no longer holds the requested start. Asked for ledger {}, \
             starting at {} (the RPC's oldest); {} ledgers of history were already gone before this \
             index existed. /coverage reports this permanently.",
            plan.requested.unwrap_or(0),
            plan.effective,
            plan.unreachable,
        );
    } else {
        tracing::info!(
            %contract_id,
            mode = plan.mode,
            effective_start_ledger = plan.effective,
            rpc_oldest_ledger = range.oldest,
            rpc_latest_ledger = range.latest,
            "first run — backfilling from ledger {}",
            plan.effective,
        );
    }
    Ok(plan)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn start_spec_parses_sentinels_numbers_and_rejects_junk() {
        assert_eq!(StartSpec::from_str("head").unwrap(), StartSpec::Head);
        assert_eq!(StartSpec::from_str("  HEAD ").unwrap(), StartSpec::Head);
        assert_eq!(StartSpec::from_str("oldest").unwrap(), StartSpec::Oldest);
        assert_eq!(StartSpec::from_str("Oldest").unwrap(), StartSpec::Oldest);
        assert_eq!(StartSpec::from_str("3950128").unwrap(), StartSpec::Ledger(3950128));
        assert!(StartSpec::from_str("banana").is_err());
        assert!(StartSpec::from_str("-5").is_err(), "negative ledgers are nonsense");
        assert!(StartSpec::from_str("0").is_err(), "ledger 0 does not exist");
    }

    #[test]
    fn config_applies_per_contract_overrides_then_default() {
        let known = vec!["CAAA".to_string(), "CBBB".to_string(), "CCCC".to_string()];
        let cfg = StartLedgerConfig::parse(
            Some("CAAA:3950128, CBBB:oldest"),
            Some("head"),
            &known,
        )
        .expect("config parses");
        assert_eq!(cfg.for_contract("CAAA"), StartSpec::Ledger(3950128));
        assert_eq!(cfg.for_contract("CBBB"), StartSpec::Oldest);
        assert_eq!(cfg.for_contract("CCCC"), StartSpec::Head, "falls back to the default");
    }

    #[test]
    fn config_default_is_oldest_so_a_fresh_index_is_never_empty() {
        let known = vec!["CAAA".to_string()];
        let cfg = StartLedgerConfig::parse(None, None, &known).expect("config parses");
        assert_eq!(cfg.for_contract("CAAA"), StartSpec::Oldest);
    }

    #[test]
    fn config_rejects_misuse_loudly() {
        let known = vec!["CAAA".to_string()];
        // A contract id that is not indexed: silently ignoring this is how you
        // end up with an empty index and no idea why.
        let err = StartLedgerConfig::parse(Some("CTYPO:100"), None, &known).unwrap_err().to_string();
        assert!(err.contains("not in CONTRACT_IDS"), "got: {err}");
        // Unparseable values.
        assert!(StartLedgerConfig::parse(Some("CAAA:soon"), None, &known).is_err());
        assert!(StartLedgerConfig::parse(Some("CAAA"), None, &known).is_err(), "missing separator");
        assert!(StartLedgerConfig::parse(None, Some("yesterday"), &known).is_err());
        // Same contract twice: which one wins is a coin flip — refuse.
        assert!(StartLedgerConfig::parse(Some("CAAA:100,CAAA:200"), None, &known).is_err());
    }
}
