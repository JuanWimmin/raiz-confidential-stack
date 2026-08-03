#!/usr/bin/env bash
# goal-flow.sh — end-to-end flow for the goal_meta contract on Stellar testnet.
#
#   create_goal "Techo de la casa comunal" -> get_goal -> record_harvest
#   -> verify the harvest event with a REAL getEvents call against the RPC.
#
# Secrets: read from .env.deploy at the repo root (gitignored). Never commit them.
# Testnet is flaky (project rule 3): every network call gets 3 attempts with
# growing backoff. Run from anywhere: bash scripts/goal-flow.sh
#
# Env overrides: GOAL_META_CONTRACT_ID, GOAL_ACCOUNT, VIEW_KEY_HEX, RPC_URL, NETWORK.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RPC_URL="${RPC_URL:-https://soroban-testnet.stellar.org}"
NETWORK="${NETWORK:-testnet}"
# Node >= 25 colorizes console.log(<number>) even when piped — the ANSI escapes
# would poison shell arithmetic. Force plain output.
export NODE_DISABLE_COLORS=1

if [ -f "$ROOT/.env.deploy" ]; then
  set -a
  # shellcheck disable=SC1091
  . "$ROOT/.env.deploy"
  set +a
fi
: "${GOAL_META_CONTRACT_ID:?missing — deploy first and record it in .env.deploy}"
: "${GOAL_META_DEPLOYER_SECRET:?missing — secret seed lives ONLY in .env.deploy}"
: "${GOAL_META_DEPLOYER_PUBLIC:?missing — deployer public address}"

# ── demo goal parameters ──────────────────────────────────────────────────────
GOAL_NAME="Techo de la casa comunal"
TARGET_STROOPS=5000000000              # 500 XLM display target (public by choice)
DEADLINE=1798761599                    # 2026-12-31 23:59:59 UTC (December 2026)
GOAL_ACCOUNT="${GOAL_ACCOUNT:-$GOAL_META_DEPLOYER_PUBLIC}"
# Placeholder 64-byte view key (Grumpkin points are BytesN<64> in the CT auditor
# module). Session 4 publishes the real point exported from the CT wrapper.
VIEW_KEY_HEX="${VIEW_KEY_HEX:-$(printf '07%.0s' $(seq 1 64))}"

MEMO="primera cosecha (goal-flow $(date -u +%Y-%m-%dT%H:%M:%SZ))"

# ── retry helper: 3 attempts, growing backoff (5s, 10s) ───────────────────────
retry() {
  local attempt=1 delay=5
  while true; do
    if "$@"; then return 0; fi
    if [ "$attempt" -ge 3 ]; then
      echo "FAILED after 3 attempts: $*" >&2
      return 1
    fi
    echo "  ...attempt $attempt failed, retrying in ${delay}s" >&2
    sleep "$delay"
    attempt=$((attempt + 1))
    delay=$((delay * 2))
  done
}

invoke() {
  stellar contract invoke \
    --id "$GOAL_META_CONTRACT_ID" \
    --source-account "$GOAL_META_DEPLOYER_SECRET" \
    --network "$NETWORK" \
    -- "$@"
}

rpc_call() { # rpc_call <json-payload>
  curl -sS --max-time 30 -X POST "$RPC_URL" \
    -H 'Content-Type: application/json' -d "$1"
}

echo "== goal-flow: contract $GOAL_META_CONTRACT_ID on $NETWORK =="

# ── 1. create_goal (idempotent: a re-run finds the goal instead of failing) ───
echo "-- create_goal: \"$GOAL_NAME\" (target 500 XLM, deadline Dec 2026)"
create_attempt=1
GOAL_ID=""
while [ -z "$GOAL_ID" ]; do
  set +e
  OUT=$(invoke create_goal \
    --admin "$GOAL_META_DEPLOYER_PUBLIC" \
    --name "$GOAL_NAME" \
    --target "$TARGET_STROOPS" \
    --deadline "$DEADLINE" \
    --goal_account "$GOAL_ACCOUNT" \
    --view_key "$VIEW_KEY_HEX" 2>&1)
  status=$?
  set -e
  if [ $status -eq 0 ]; then
    GOAL_ID=$(echo "$OUT" | tail -1)
    echo "   created goal id=$GOAL_ID"
  elif echo "$OUT" | grep -q 'Error(Contract, #4)'; then
    # DuplicateGoalAccount: this account already backs a goal — find its id.
    echo "   goal already exists for $GOAL_ACCOUNT (Error #4); scanning ids..."
    COUNT=$(retry invoke goal_count)
    for ((i = 0; i < COUNT; i++)); do
      if retry invoke get_goal --id "$i" | grep -q "$GOAL_ACCOUNT"; then
        GOAL_ID=$i
        echo "   reusing goal id=$GOAL_ID"
        break
      fi
    done
    [ -n "$GOAL_ID" ] || { echo "duplicate reported but goal not found" >&2; exit 1; }
  else
    if [ "$create_attempt" -ge 3 ]; then
      echo "create_goal FAILED after 3 attempts:" >&2
      echo "$OUT" >&2
      exit 1
    fi
    echo "  ...create attempt $create_attempt failed, retrying in $((create_attempt * 5))s" >&2
    sleep $((create_attempt * 5))
    create_attempt=$((create_attempt + 1))
  fi
done

# ── 2. read it back ───────────────────────────────────────────────────────────
echo "-- get_goal --id $GOAL_ID"
retry invoke get_goal --id "$GOAL_ID"

# ── 3. record_harvest ─────────────────────────────────────────────────────────
echo "-- record_harvest --id $GOAL_ID --memo \"$MEMO\""
retry invoke record_harvest --id "$GOAL_ID" --memo "$MEMO" > /dev/null
echo "   harvest recorded"

# ── 4. verify the harvest event with a real getEvents call ────────────────────
echo "-- verifying via getEvents on $RPC_URL"
get_latest() {
  rpc_call '{"jsonrpc":"2.0","id":1,"method":"getLatestLedger","params":{}}' \
    | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>{const r=JSON.parse(d);if(!r.result||!r.result.sequence)process.exit(1);console.log(String(r.result.sequence))})'
}
LATEST=$(retry get_latest | tr -cd '0-9') # belt and braces: digits only
[ -n "$LATEST" ] || { echo "could not read latest ledger" >&2; exit 1; }
START=$((LATEST - 120)) # ~10 min window: plenty for the tx we just confirmed

find_harvest() {
  rpc_call "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"getEvents\",\"params\":{\"startLedger\":$START,\"filters\":[{\"type\":\"contract\",\"contractIds\":[\"$GOAL_META_CONTRACT_ID\"]}],\"pagination\":{\"limit\":100}}}" \
    | node -e '
      let d = "";
      process.stdin.on("data", c => d += c).on("end", () => {
        const r = JSON.parse(d);
        const evs = (r.result && r.result.events) || [];
        for (const e of evs.reverse()) {
          const topics = e.topic || e.topics || [];
          // ScSymbol XDR carries the raw symbol bytes: match "harvest" directly.
          if (topics.some(t => Buffer.from(t, "base64").includes("harvest"))) {
            // RPC-version tolerance (project gotcha #5): value may be a base64
            // string or an {"xdr": ...} object.
            const val = typeof e.value === "string" ? e.value : (e.value && e.value.xdr) || "";
            console.log([e.id, e.ledger, topics.join(" "), val].join("|"));
            process.exit(0);
          }
        }
        process.exit(3); // not found (retry — the RPC may lag a ledger or two)
      });'
}
EVENT_LINE=$(retry find_harvest)
IFS='|' read -r EV_ID EV_LEDGER EV_TOPICS EV_VALUE <<< "$EVENT_LINE"

echo "   HARVEST EVENT FOUND"
echo "   event id : $EV_ID"
echo "   ledger   : $EV_LEDGER"
echo "   topics   :"
for t in $EV_TOPICS; do
  echo "     $(stellar xdr decode --type ScVal --output json "$t")"
done
echo "   data     : $(stellar xdr decode --type ScVal --output json "$EV_VALUE")"
echo "== goal-flow complete: goal id=$GOAL_ID, harvest event verified on-chain =="
