#![cfg(test)]
use super::*;
use soroban_sdk::{testutils::Address as _, Address, Bytes, Env, String};

#[test]
fn create_and_read_goal() {
    let env = Env::default();
    env.mock_all_auths();

    let contract_id = env.register(GoalMeta, ());
    let client = GoalMetaClient::new(&env, &contract_id);

    let admin = Address::generate(&env);
    let goal_account = Address::generate(&env);
    let view_key = Bytes::from_slice(&env, b"demo-view-key");

    let id = client.create_goal(
        &admin,
        &String::from_str(&env, "Techo de la casa comunal"),
        &500_0000000i128,
        &1767225600u64, // 2026-01-01 as placeholder deadline
        &goal_account,
        &view_key,
    );
    assert_eq!(id, 0);
    assert_eq!(client.goal_count(), 1);

    let goal = client.get_goal(&id);
    assert_eq!(goal.goal_account, goal_account);
    assert_eq!(goal.target, 500_0000000i128);

    client.record_harvest(&id, &String::from_str(&env, "primera cosecha"));
}
