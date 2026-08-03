#![cfg(test)]
use super::*;
use soroban_sdk::{
    testutils::{storage::Persistent as _, Address as _, Events as _, Ledger as _},
    vec, Address, Bytes, Env, IntoVal, String,
};

// ── helpers ──────────────────────────────────────────────────────────────────

/// Mirror the REAL testnet state-archival config (fetched 2026-08-03 via
/// getLedgerEntries, see TTL policy comment in lib.rs) so TTL assertions run
/// against the numbers the contract will actually meet on the network.
fn testnet_like_env() -> Env {
    let env = Env::default();
    env.ledger().with_mut(|li| {
        li.timestamp = 1_785_715_200; // 2026-08-03 00:00:00 UTC (session day)
        li.sequence_number = 3_950_000; // near the real latestLedger observed
        li.min_persistent_entry_ttl = 120_960;
        li.min_temp_entry_ttl = 720;
        li.max_entry_ttl = 3_110_400;
    });
    env
}

/// Contract panics via `panic_with_error!` surface in `try_` clients as
/// `soroban_sdk::Error` with the contract error code.
fn contract_err(e: Error) -> soroban_sdk::Error {
    soroban_sdk::Error::from_contract_error(e as u32)
}

fn valid_view_key(env: &Env) -> Bytes {
    // 64 bytes = a serialized Grumpkin point, the shape the CT auditor module
    // stores (vendor: confidential/auditor/storage.rs, BytesN<64>).
    Bytes::from_slice(env, &[7u8; 64])
}

const DEADLINE_DEC_2026: u64 = 1_798_761_599; // 2026-12-31 23:59:59 UTC

struct Setup {
    env: Env,
    client: GoalMetaClient<'static>,
    admin: Address,
    goal_account: Address,
}

fn setup() -> Setup {
    let env = testnet_like_env();
    env.mock_all_auths();
    let contract_id = env.register(GoalMeta, ());
    let client = GoalMetaClient::new(&env, &contract_id);
    let admin = Address::generate(&env);
    let goal_account = Address::generate(&env);
    Setup { env, client, admin, goal_account }
}

fn create_default_goal(s: &Setup) -> u32 {
    s.client.create_goal(
        &s.admin,
        &String::from_str(&s.env, "Techo de la casa comunal"),
        &500_0000000i128, // 500 XLM display target — NEVER compared to any balance
        &DEADLINE_DEC_2026,
        &s.goal_account,
        &valid_view_key(&s.env),
    )
}

// ── happy paths ──────────────────────────────────────────────────────────────

#[test]
fn create_and_read_goal() {
    let s = setup();
    let id = create_default_goal(&s);
    assert_eq!(id, 0);

    // created event: topics (goal, created, id), data = goal_account.
    // NOTE: events().all() only holds the LAST invocation's events — assert
    // before any further client call.
    assert_eq!(
        s.env.events().all(),
        vec![
            &s.env,
            (
                s.client.address.clone(),
                (symbol_short!("goal"), symbol_short!("created"), id).into_val(&s.env),
                s.goal_account.clone().into_val(&s.env),
            )
        ]
    );

    assert_eq!(s.client.goal_count(), 1);
    let goal = s.client.get_goal(&id);
    assert_eq!(goal.name, String::from_str(&s.env, "Techo de la casa comunal"));
    assert_eq!(goal.target, 500_0000000i128);
    assert_eq!(goal.deadline, DEADLINE_DEC_2026);
    assert_eq!(goal.goal_account, s.goal_account);
    assert_eq!(goal.view_key, valid_view_key(&s.env));
    assert_eq!(goal.admin, s.admin);
    assert_eq!(goal.created_at, 1_785_715_200);
}

#[test]
fn ids_increment_per_goal() {
    let s = setup();
    assert_eq!(s.client.goal_count(), 0);
    assert_eq!(create_default_goal(&s), 0);

    let second_account = Address::generate(&s.env);
    let id = s.client.create_goal(
        &s.admin,
        &String::from_str(&s.env, "Cancha del barrio"),
        &1_000_0000000i128,
        &DEADLINE_DEC_2026,
        &second_account,
        &valid_view_key(&s.env),
    );
    assert_eq!(id, 1);
    assert_eq!(s.client.goal_count(), 2);
    assert_eq!(s.client.get_goal(&1).goal_account, second_account);
}

#[test]
fn harvest_emits_event() {
    let s = setup();
    let id = create_default_goal(&s);
    let memo = String::from_str(&s.env, "primera cosecha");
    s.client.record_harvest(&id, &memo);

    assert_eq!(
        s.env.events().all(),
        vec![
            &s.env,
            (
                s.client.address.clone(),
                (symbol_short!("goal"), symbol_short!("harvest"), id).into_val(&s.env),
                memo.into_val(&s.env),
            )
        ]
    );
}

// ── deadline policy ──────────────────────────────────────────────────────────

#[test]
fn create_goal_with_past_or_present_deadline_rejected() {
    let s = setup();
    let now = s.env.ledger().timestamp();

    for bad_deadline in [now - 1, now] {
        let res = s.client.try_create_goal(
            &s.admin,
            &String::from_str(&s.env, "Meta vencida"),
            &1i128,
            &bad_deadline,
            &s.goal_account,
            &valid_view_key(&s.env),
        );
        assert_eq!(res.err().unwrap(), Ok(contract_err(Error::InvalidDeadline)));
    }
    assert_eq!(s.client.goal_count(), 0);
}

#[test]
fn harvest_after_deadline_is_allowed() {
    // Spec decision (documented in lib.rs): the deadline never freezes harvesting.
    // Contributions move through the CT wrapper, which goal_meta cannot gate; a
    // final post-deadline harvest sweeping late pending contributions is legit.
    let s = setup();
    let id = create_default_goal(&s);

    s.env
        .ledger()
        .with_mut(|li| li.timestamp = DEADLINE_DEC_2026 + 86_400); // one day past
    s.client
        .record_harvest(&id, &String::from_str(&s.env, "cosecha final"));
    assert_eq!(s.env.events().all().len(), 1);
}

// ── input validation ─────────────────────────────────────────────────────────

#[test]
fn malformed_view_key_rejected() {
    let s = setup();
    for bad_key in [
        Bytes::new(&s.env),                       // empty
        Bytes::from_slice(&s.env, &[7u8; 63]),    // one byte short
        Bytes::from_slice(&s.env, &[7u8; 65]),    // one byte long
    ] {
        let res = s.client.try_create_goal(
            &s.admin,
            &String::from_str(&s.env, "Meta sin llave"),
            &1i128,
            &DEADLINE_DEC_2026,
            &s.goal_account,
            &bad_key,
        );
        assert_eq!(res.err().unwrap(), Ok(contract_err(Error::InvalidViewKey)));
    }
    assert_eq!(s.client.goal_count(), 0);
}

#[test]
fn non_positive_target_rejected() {
    let s = setup();
    for bad_target in [0i128, -500_0000000i128] {
        let res = s.client.try_create_goal(
            &s.admin,
            &String::from_str(&s.env, "Meta sin objetivo"),
            &bad_target,
            &DEADLINE_DEC_2026,
            &s.goal_account,
            &valid_view_key(&s.env),
        );
        assert_eq!(res.err().unwrap(), Ok(contract_err(Error::InvalidTarget)));
    }
}

#[test]
fn duplicate_goal_account_rejected() {
    let s = setup();
    create_default_goal(&s);

    // Same CT account cannot back a second goal — its view key IS the goal's.
    let res = s.client.try_create_goal(
        &s.admin,
        &String::from_str(&s.env, "Otra meta, misma cuenta"),
        &1i128,
        &DEADLINE_DEC_2026,
        &s.goal_account,
        &valid_view_key(&s.env),
    );
    assert_eq!(res.err().unwrap(), Ok(contract_err(Error::DuplicateGoalAccount)));

    // A fresh account is fine (names may repeat; accounts may not).
    let fresh = Address::generate(&s.env);
    let id = s.client.create_goal(
        &s.admin,
        &String::from_str(&s.env, "Techo de la casa comunal"),
        &1i128,
        &DEADLINE_DEC_2026,
        &fresh,
        &valid_view_key(&s.env),
    );
    assert_eq!(id, 1);
}

// ── missing goals ────────────────────────────────────────────────────────────

#[test]
fn get_goal_missing_fails() {
    let s = setup();
    let res = s.client.try_get_goal(&99);
    assert_eq!(res.err().unwrap(), Ok(contract_err(Error::GoalNotFound)));
}

#[test]
fn harvest_missing_goal_fails() {
    let s = setup();
    let res = s
        .client
        .try_record_harvest(&99, &String::from_str(&s.env, "cosecha fantasma"));
    assert_eq!(res.err().unwrap(), Ok(contract_err(Error::GoalNotFound)));
}

// ── auth ─────────────────────────────────────────────────────────────────────

#[test]
fn create_goal_without_auth_fails() {
    let env = testnet_like_env();
    let contract_id = env.register(GoalMeta, ());
    let client = GoalMetaClient::new(&env, &contract_id);
    let admin = Address::generate(&env);
    let goal_account = Address::generate(&env);

    // No auths mocked at all: admin.require_auth() must abort the invocation.
    let res = client.try_create_goal(
        &admin,
        &String::from_str(&env, "Meta sin firma"),
        &1i128,
        &DEADLINE_DEC_2026,
        &goal_account,
        &valid_view_key(&env),
    );
    assert!(res.is_err());
}

#[test]
fn harvest_without_admin_auth_fails() {
    let s = setup();
    let id = create_default_goal(&s);

    // Drop the blanket auth mock: the goal admin's signature is now missing.
    s.env.set_auths(&[]);
    let res = s
        .client
        .try_record_harvest(&id, &String::from_str(&s.env, "cosecha ajena"));
    assert!(res.is_err());

    // With the admin's auth restored, the same call succeeds.
    s.env.mock_all_auths();
    s.client
        .record_harvest(&id, &String::from_str(&s.env, "cosecha legítima"));
}

// ── TTL policy ───────────────────────────────────────────────────────────────

#[test]
fn ttl_extension_applied_on_create_and_refreshed_on_harvest() {
    let s = setup();
    let id = create_default_goal(&s);

    let goal_key = DataKey::Goal(id);
    let account_key = DataKey::GoalByAccount(s.goal_account.clone());

    // create_goal must have bumped both persistent entries to ~30 days.
    let (goal_ttl, account_ttl) = s.env.as_contract(&s.client.address, || {
        (
            s.env.storage().persistent().get_ttl(&goal_key),
            s.env.storage().persistent().get_ttl(&account_key),
        )
    });
    assert_eq!(goal_ttl, 518_400);
    assert_eq!(account_ttl, 518_400);

    // Let ~10 days of ledgers pass: TTL decays...
    s.env
        .ledger()
        .with_mut(|li| li.sequence_number += 172_800);
    let decayed = s
        .env
        .as_contract(&s.client.address, || {
            s.env.storage().persistent().get_ttl(&goal_key)
        });
    assert_eq!(decayed, 518_400 - 172_800);

    // ...and a harvest refreshes it back to the full window.
    s.client
        .record_harvest(&id, &String::from_str(&s.env, "cosecha que paga la renta"));
    let refreshed = s
        .env
        .as_contract(&s.client.address, || {
            s.env.storage().persistent().get_ttl(&goal_key)
        });
    assert_eq!(refreshed, 518_400);
}
