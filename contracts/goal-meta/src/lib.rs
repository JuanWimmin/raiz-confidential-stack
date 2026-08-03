//! goal_meta — community goal registry for "Sobre del Barrio".
//!
//! Design invariant: THIS CONTRACT NEVER SEES AN AMOUNT. Contribution amounts live
//! encrypted inside the OpenZeppelin Confidential Token wrapper. Here lives only what
//! is deliberately public: the goal's story, its account, its published auditor view
//! key (collective transparency), and the harvest (merge) timeline.
//!
//! Events feed the wallet's public timeline — served past the RPC's 7-day window by
//! Raiz Memory. That pairing is the whole submission.

#![no_std]
use soroban_sdk::{
    contract, contractimpl, contracttype, symbol_short, Address, Bytes, Env, String,
};

#[contracttype]
#[derive(Clone)]
pub struct Goal {
    pub name: String,
    pub target: i128,        // display target in stroops/units of the wrapped asset (public by choice)
    pub deadline: u64,       // unix seconds
    pub goal_account: Address, // the CT-registered account that receives confidential contributions
    pub view_key: Bytes,     // the goal's auditor view key, published ON PURPOSE: "the fund is glass"
    pub admin: Address,      // demo: the team; post-summit: the RAÍZ communal smart account (F3)
    pub created_at: u64,
}

#[contracttype]
pub enum DataKey {
    Count,
    Goal(u32),
}

#[contract]
pub struct GoalMeta;

#[contractimpl]
impl GoalMeta {
    /// Register a new community goal. The admin is who can harvest (execute CT merge
    /// off-contract and record it here).
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
        env.storage().instance().set(&DataKey::Count, &(id + 1));

        // RAÍZ TTL lesson (the DeFindex gotcha): bump persistent entries explicitly.
        // TODO(day 1): tune min/max TTL against current testnet network settings.
        env.storage().persistent().extend_ttl(&DataKey::Goal(id), 100_000, 500_000);

        env.events()
            .publish((symbol_short!("goal"), symbol_short!("created"), id), goal.goal_account);
        id
    }

    pub fn get_goal(env: Env, id: u32) -> Goal {
        env.storage().persistent().get(&DataKey::Goal(id)).unwrap()
    }

    pub fn goal_count(env: Env) -> u32 {
        env.storage().instance().get(&DataKey::Count).unwrap_or(0)
    }

    /// Record a harvest: the moment pending confidential contributions were merged
    /// into the goal's available balance (the CT `merge` op happens off-contract; this
    /// is its public, timestamped trace — the wallet renders it as "cosecha").
    pub fn record_harvest(env: Env, id: u32, memo: String) {
        let goal: Goal = env.storage().persistent().get(&DataKey::Goal(id)).unwrap();
        goal.admin.require_auth();
        env.events()
            .publish((symbol_short!("goal"), symbol_short!("harvest"), id), memo);
    }
}

mod test;
