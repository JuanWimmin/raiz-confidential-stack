//! goal_meta — community goal registry for "Sobre del Barrio".
//!
//! Design invariant: THIS CONTRACT NEVER SEES AN AMOUNT of any contribution.
//! Contribution amounts live encrypted inside the OpenZeppelin Confidential Token
//! wrapper. Here lives only what is deliberately public: the goal's story, its
//! account, its published auditor view key (collective transparency), and the
//! harvest (merge) timeline. `target` is a display-only fundraising objective,
//! public by choice — it is never compared against any balance on-chain.
//!
//! Events feed the wallet's public timeline — served past the RPC's ~7-day window
//! by Raiz Memory. That pairing is the whole submission.

#![no_std]
use soroban_sdk::{
    contract, contracterror, contractimpl, contracttype, panic_with_error, symbol_short, Address,
    Bytes, Env, String,
};

// ── TTL policy ────────────────────────────────────────────────────────────────
// RAÍZ TTL lesson (the DeFindex gotcha): bump every persistent entry explicitly.
// Values checked against the REAL testnet state-archival config on 2026-08-03
// (getLedgerEntries, ConfigSettingId state_archival, latestLedger 3950042):
//   min_persistent_ttl = 120_960 ledgers (~7 days @ ~5s/ledger)
//   max_entry_ttl      = 3_110_400 ledgers (~180 days)
// We extend goal entries to ~30 days (17_280 ledgers/day × 30 = 518_400):
// comfortably above the 7-day minimum, far below the 180-day cap, and refreshed
// on every harvest so active goals never expire during the demo window.
// threshold == extend_to means "always bump on touch" — simplest correct policy.
const TTL_EXTEND_TO: u32 = 518_400; // ~30 days
const TTL_THRESHOLD: u32 = TTL_EXTEND_TO;

/// Published auditor view keys are Grumpkin curve points serialized as 64 bytes.
/// Source of truth: vendor/stellar-contracts/packages/tokens/src/confidential/
/// auditor/storage.rs (`get_key`/`register_key` use `BytesN<64>`). We accept
/// `Bytes` at the ABI boundary so we can reject malformed input with a typed
/// error instead of a host trap, but the length rule is the same 64 bytes.
const VIEW_KEY_LEN: u32 = 64;

#[contracterror]
#[derive(Copy, Clone, Debug, Eq, PartialEq)]
#[repr(u32)]
pub enum Error {
    /// No goal stored under the requested id.
    GoalNotFound = 1,
    /// View key must be exactly 64 bytes (a serialized Grumpkin point).
    InvalidViewKey = 2,
    /// Deadline must be strictly in the future at creation time.
    InvalidDeadline = 3,
    /// Each CT goal account can back at most one goal (its view key IS the goal's).
    DuplicateGoalAccount = 4,
    /// Display target must be positive.
    InvalidTarget = 5,
}

#[contracttype]
#[derive(Clone)]
pub struct Goal {
    pub name: String,
    pub target: i128, // display target in stroops/units of the wrapped asset (public by choice)
    pub deadline: u64, // unix seconds
    pub goal_account: Address, // the CT-registered account that receives confidential contributions
    pub view_key: Bytes, // the goal's auditor view key, published ON PURPOSE: "the fund is glass"
    pub admin: Address, // demo: the team; post-summit: the RAÍZ communal smart account (F3)
    pub created_at: u64,
}

#[contracttype]
pub enum DataKey {
    Count,
    Goal(u32),
    /// Uniqueness guard: one goal per CT goal account.
    GoalByAccount(Address),
}

#[contract]
pub struct GoalMeta;

#[contractimpl]
impl GoalMeta {
    /// Register a new community goal. The admin is who can harvest (execute the
    /// CT `merge` off-contract and record its public trace here).
    ///
    /// Rejects: non-64-byte view keys, past deadlines, non-positive targets, and
    /// a goal account that already backs another goal.
    pub fn create_goal(
        env: Env,
        admin: Address,
        name: String,
        target: i128,
        deadline: u64,
        goal_account: Address,
        view_key: Bytes,
    ) -> u32 {
        admin.require_auth();

        if view_key.len() != VIEW_KEY_LEN {
            panic_with_error!(&env, Error::InvalidViewKey);
        }
        if deadline <= env.ledger().timestamp() {
            panic_with_error!(&env, Error::InvalidDeadline);
        }
        if target <= 0 {
            panic_with_error!(&env, Error::InvalidTarget);
        }
        let by_account = DataKey::GoalByAccount(goal_account.clone());
        if env.storage().persistent().has(&by_account) {
            panic_with_error!(&env, Error::DuplicateGoalAccount);
        }

        let id: u32 = env.storage().instance().get(&DataKey::Count).unwrap_or(0);
        let goal = Goal {
            name,
            target,
            deadline,
            goal_account,
            view_key,
            admin: admin.clone(),
            created_at: env.ledger().timestamp(),
        };
        env.storage().persistent().set(&DataKey::Goal(id), &goal);
        env.storage().persistent().set(&by_account, &id);
        env.storage().instance().set(&DataKey::Count, &(id + 1));

        // Explicit TTL bumps (see TTL policy above).
        env.storage()
            .persistent()
            .extend_ttl(&DataKey::Goal(id), TTL_THRESHOLD, TTL_EXTEND_TO);
        env.storage()
            .persistent()
            .extend_ttl(&by_account, TTL_THRESHOLD, TTL_EXTEND_TO);
        env.storage().instance().extend_ttl(TTL_THRESHOLD, TTL_EXTEND_TO);

        env.events().publish(
            (symbol_short!("goal"), symbol_short!("created"), id),
            goal.goal_account,
        );
        id
    }

    pub fn get_goal(env: Env, id: u32) -> Goal {
        match env.storage().persistent().get(&DataKey::Goal(id)) {
            Some(goal) => goal,
            None => panic_with_error!(&env, Error::GoalNotFound),
        }
    }

    pub fn goal_count(env: Env) -> u32 {
        env.storage().instance().get(&DataKey::Count).unwrap_or(0)
    }

    /// Record a harvest: the moment pending confidential contributions were merged
    /// into the goal's available balance (the CT `merge` op happens off-contract;
    /// this is its public, timestamped trace — the wallet renders it as "cosecha").
    ///
    /// Deadline policy (deliberate): harvesting is ALLOWED after the deadline.
    /// Contributions flow through the CT wrapper, which this contract cannot and
    /// must not gate; a final harvest that sweeps late pending contributions is a
    /// legitimate closing ritual. The deadline is display metadata plus a
    /// wallet-level gate for new contributions, never an on-chain freeze.
    pub fn record_harvest(env: Env, id: u32, memo: String) {
        let goal: Goal = match env.storage().persistent().get(&DataKey::Goal(id)) {
            Some(goal) => goal,
            None => panic_with_error!(&env, Error::GoalNotFound),
        };
        goal.admin.require_auth();

        // A harvest proves the goal is alive — refresh its rent (TTL policy above).
        env.storage()
            .persistent()
            .extend_ttl(&DataKey::Goal(id), TTL_THRESHOLD, TTL_EXTEND_TO);
        env.storage().persistent().extend_ttl(
            &DataKey::GoalByAccount(goal.goal_account),
            TTL_THRESHOLD,
            TTL_EXTEND_TO,
        );
        env.storage().instance().extend_ttl(TTL_THRESHOLD, TTL_EXTEND_TO);

        env.events()
            .publish((symbol_short!("goal"), symbol_short!("harvest"), id), memo);
    }
}

mod test;
