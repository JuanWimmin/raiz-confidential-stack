/**
 * verify-goal-total — reconstruct AND VERIFY a community goal's confidential
 * fund total from nothing but public chain data + the goal's PUBLISHED view
 * key. No trust in our app, our indexer, or our word.
 *
 * What it does, in order (every step is a real network call):
 *
 *   1. Reads the goal record from the goal_meta contract and checks that the
 *      view-key POINT stored on-chain equals k1·H for the published SECRET k1
 *      you pass in. Also checks the CT auditor registry serves the same point
 *      for the goal account's auditor_id. If either check fails, the published
 *      key is not the goal's key and the script aborts.
 *   2. Replays every confidential-token event touching the goal account
 *      (deposits, incoming transfers, merges, outflows), decrypting each
 *      incoming transfer's RECIPIENT auditor channel with k1. This yields each
 *      contribution's amount AND its Pedersen randomness r_tx — a full opening
 *      of the contribution commitment (vendor SDK auditor/decrypt.ts).
 *   3. VERIFIES: re-commits the accumulated (value, randomness) sums with the
 *      Pedersen generators and compares the resulting curve points against the
 *      goal account's on-chain spendable/receiving balance commitments,
 *      fetched live from contract storage. Pedersen commitments are binding:
 *      if the recomputed points match, the decrypted amounts are the amounts
 *      the chain itself is committed to.
 *
 * Privacy note (honest by design): the published k1 opens the recipient
 * channel of EVERY transfer to the goal, so per-contribution amounts are
 * visible to any holder of k1 — that is the disclosed, documented trade-off of
 * the public-view-key pattern. Contributor BALANCES stay private: k1 cannot
 * open sender channels (contributors register under a different auditor id).
 *
 * Usage (from scripts/verify-goal-total/, after the vendor setup in README.md):
 *
 *   node verify-goal-total.mjs
 *   node verify-goal-total.mjs --view-key 0x... --token C... --goal G... \
 *        --from-ledger N --goal-meta C... --goal-id 1 --rpc https://...
 *
 * Defaults come from config.json (our deployment; all public by design).
 * Crypto and RPC plumbing are consumed READ-ONLY from the vendor demo SDK's
 * built dist — reimplementing Poseidon2/Grumpkin by hand here would be more
 * code to trust, not less (see README.md "Why the vendor dependency").
 */

import { createRequire } from "node:module";
import { pathToFileURL, fileURLToPath } from "node:url";
import { readFileSync } from "node:fs";
import path from "node:path";

// ---------------------------------------------------------------------------
// Vendor SDK (read-only imports of the built dist; see README.md setup)
// ---------------------------------------------------------------------------
const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(HERE, "..", "..");
const SDK_DIST = path.join(
  REPO_ROOT, "vendor", "stellar-confidential-token-demo", "packages", "sdk", "dist",
);
const sdkUrl = (rel) => pathToFileURL(path.join(SDK_DIST, rel)).href;

const { ChainClient } = await import(sdkUrl("chain/client.js"));
const { fetchEvents } = await import(sdkUrl("chain/events.js"));
const { auditTransferRecipientChannel, auditTransferSenderChannel, auditWithdraw, auditorPublicKey } =
  await import(sdkUrl("auditor/decrypt.js"));
const { commit, pointCoords, pointFromBytes } = await import(sdkUrl("crypto/grumpkin.js"));
const { toHex32 } = await import(sdkUrl("crypto/field.js"));

// @stellar/stellar-sdk out of the vendor workspace (same instance the dist uses).
const sdkRequire = createRequire(pathToFileURL(path.join(SDK_DIST, "chain", "client.js")).href);
const { nativeToScVal, scValToNative } = sdkRequire("@stellar/stellar-sdk");

// ---------------------------------------------------------------------------
// Config + CLI args
// ---------------------------------------------------------------------------
const cfg = JSON.parse(readFileSync(path.join(HERE, "config.json"), "utf8"));
const args = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : fallback;
};

const RPC_URL = arg("--rpc", cfg.rpcUrl);
const TOKEN = arg("--token", cfg.token);
const GOAL = arg("--goal", cfg.goalAccount);
const VIEW_KEY = BigInt(arg("--view-key", cfg.viewKeySecretHex));
const FROM_LEDGER = Number(arg("--from-ledger", cfg.deployedAtLedger));
const GOAL_META = arg("--goal-meta", cfg.goalMeta);
const GOAL_META_ID = Number(arg("--goal-id", cfg.goalMetaGoalId));

const fmtXlm = (stroops) => {
  const s = BigInt(stroops);
  const whole = s / 10_000_000n;
  const frac = (s % 10_000_000n).toString().padStart(7, "0").replace(/0+$/, "");
  return frac ? `${whole}.${frac}` : `${whole}`;
};
const short = (s) => `${s.slice(0, 6)}…${s.slice(-4)}`;
const samePoint = (a, b) => {
  const ca = pointCoords(a), cb = pointCoords(b);
  return ca.x === cb.x && ca.y === cb.y;
};

// Testnet is flaky in bursts: every network call gets 3 attempts, growing backoff.
async function retry(label, fn, { tries = 3, baseDelayMs = 3000 } = {}) {
  let lastErr;
  for (let i = 1; i <= tries; i++) {
    try {
      return await fn();
    } catch (e) {
      lastErr = e;
      if (i < tries) {
        const delay = baseDelayMs * 2 ** (i - 1);
        console.warn(`  ! ${label} attempt ${i}/${tries} failed (${String(e?.message ?? e).split("\n")[0]}); retrying in ${delay / 1000}s`);
        await new Promise((r) => setTimeout(r, delay));
      }
    }
  }
  throw new Error(`${label} failed after ${tries} attempts: ${lastErr?.message ?? lastErr}`);
}

// ---------------------------------------------------------------------------
async function main() {
  console.log(`token       = ${TOKEN}`);
  console.log(`goal        = ${GOAL}`);
  console.log(`view key k1 = ${toHex32(VIEW_KEY)}  (published on purpose)`);

  const client = new ChainClient({
    rpcUrl: RPC_URL,
    networkPassphrase: cfg.passphrase,
    contracts: { token: TOKEN, verifier: cfg.verifier, auditor: cfg.auditor },
  });
  const k1Point = auditorPublicKey(VIEW_KEY); // K = k1 · H

  // ---- [1] the published secret must match the on-chain published points ----
  console.log(`\n[1] cross-checking the published secret against on-chain state`);

  if (GOAL_META) {
    const goalScv = await retry("goal_meta.get_goal", () =>
      client.simulate(GOAL_META, "get_goal", [nativeToScVal(GOAL_META_ID, { type: "u32" })]));
    const goal = scValToNative(goalScv);
    const storedPoint = pointFromBytes(Uint8Array.from(goal.view_key));
    if (goal.goal_account !== GOAL) {
      throw new Error(`goal_meta goal ${GOAL_META_ID} backs account ${goal.goal_account}, not ${GOAL}`);
    }
    if (!samePoint(storedPoint, k1Point)) {
      throw new Error("published secret does NOT match the view-key point stored in goal_meta");
    }
    console.log(`  goal_meta[${GOAL_META_ID}] "${goal.name}" (target ${fmtXlm(goal.target)} XLM)`);
    console.log(`    stored view-key point == k1·H                      OK`);
  } else {
    console.log(`  (no goal_meta contract configured; skipping registry check)`);
  }

  const account = await retry("confidential_balance(goal)", () => client.confidentialBalance(GOAL));
  if (!account) throw new Error(`goal account ${GOAL} is not registered on the CT wrapper`);
  const registryKey = await retry(`auditor_key(${account.auditorId})`, () => client.auditorKey(account.auditorId));
  if (!samePoint(registryKey, k1Point)) {
    throw new Error(`CT auditor registry key for auditor_id ${account.auditorId} does not match k1·H`);
  }
  console.log(`  goal registered under auditor_id ${account.auditorId}; registry key == k1·H   OK`);

  // ---- [2] replay the public event stream, decrypting with k1 --------------
  console.log(`\n[2] replaying confidential events from ledger ${FROM_LEDGER}`);
  const { events } = await retry("getEvents", () =>
    fetchEvents(client, { startLedger: FROM_LEDGER, contractId: TOKEN }));

  // Accumulators: (value, randomness) openings of the goal's two on-chain
  // Pedersen commitments. Deposits enter with r = 0 (public boundary, the
  // contract commits them with zero randomness); incoming transfers enter with
  // the (v_tx, r_tx) pair the k1 recipient channel decrypts.
  let spendV = 0n, spendR = 0n;      // spendable (merged) balance opening
  let recvV = 0n, recvR = 0n;        // receiving (pending) balance opening
  let spendableProvable = true;      // outflows re-randomize the spendable
  const contributions = [];          //   commitment with owner-only randomness

  for (const ev of events) {
    if (ev.type === "deposit" && ev.to === GOAL) {
      recvV += ev.amount; // amount public at the SAC boundary; r = 0
      contributions.push(`  + ledger ${ev.ledger}  deposit  from ${short(ev.from)}  ${fmtXlm(ev.amount)} XLM (public amount)  tx ${ev.txHash.slice(0, 8)}…`);
    } else if (ev.type === "transfer" && ev.to === GOAL) {
      const { amount, rTx } = auditTransferRecipientChannel(VIEW_KEY, ev);
      recvV += amount;
      recvR += rTx;
      contributions.push(`  + ledger ${ev.ledger}  aporte   from ${short(ev.from)}  ${fmtXlm(amount)} XLM (decrypted via k1)  tx ${ev.txHash.slice(0, 8)}…`);
    } else if (ev.type === "merge" && ev.account === GOAL) {
      spendV += recvV; spendR += recvR;
      recvV = 0n; recvR = 0n;
      contributions.push(`  · ledger ${ev.ledger}  merge (cosecha): pending folded into spendable`);
    } else if (ev.type === "transfer" && ev.from === GOAL) {
      // Outgoing: k1 IS the goal's sender-side auditor key, so the amount and
      // post-transfer balance still decrypt — but the new spendable commitment
      // uses fresh owner-only randomness, so point verification stops here.
      const snd = auditTransferSenderChannel(VIEW_KEY, ev);
      spendV = snd.senderBalance;
      spendableProvable = false;
      contributions.push(`  - ledger ${ev.ledger}  outgoing transfer  ${fmtXlm(snd.amount)} XLM (decrypted via k1)  tx ${ev.txHash.slice(0, 8)}…`);
    } else if (ev.type === "withdraw" && ev.from === GOAL) {
      const chk = auditWithdraw(VIEW_KEY, ev);
      spendV = chk.senderBalance; // withdrawn amount is public in the event
      spendableProvable = false;
      contributions.push(`  - ledger ${ev.ledger}  withdraw  ${fmtXlm(ev.amount)} XLM (public amount)  tx ${ev.txHash.slice(0, 8)}…`);
    }
  }
  if (contributions.length === 0) {
    throw new Error(`no events touching ${GOAL} found from ledger ${FROM_LEDGER} — outside the RPC retention window? Point --rpc at a Raiz Memory instance or lower --from-ledger.`);
  }
  contributions.forEach((l) => console.log(l));

  // ---- [3] THE VERIFICATION: re-commit and compare against the chain -------
  console.log(`\n[3] re-committing decrypted openings against on-chain Pedersen commitments`);
  const latestLedger = await retry("latestLedger", () => client.latestLedger());

  const spendOk = spendableProvable && samePoint(commit(spendV, spendR), account.spendableBalance);
  const recvOk = samePoint(commit(recvV, recvR), account.receivingBalance);

  if (spendableProvable) {
    console.log(`  commit(${spendV}, Σr_tx) == on-chain spendable commitment   ${spendOk ? "OK" : "MISMATCH"}`);
  } else {
    console.log(`  spendable commitment not point-verifiable after an outflow (owner-only randomness);`);
    console.log(`  value still tracked via the k1 sender channel = ${fmtXlm(spendV)} XLM`);
  }
  console.log(`  commit(${recvV}, Σr_tx) == on-chain receiving commitment   ${recvOk ? "OK" : "MISMATCH"}`);

  const total = spendV + recvV;
  if ((spendableProvable && !spendOk) || !recvOk) {
    console.error(`\nVERIFICATION FAILED: decrypted ledger does not match the on-chain commitments.`);
    process.exit(1);
  }

  console.log("");
  if (spendableProvable) {
    console.log(`Goal total: ${fmtXlm(total)} XLM — verified on-chain at ledger ${latestLedger}`);
  } else {
    console.log(`Goal total: ${fmtXlm(total)} XLM — decrypted via k1 (receiving commitment verified; spendable value-only after outflow) at ledger ${latestLedger}`);
  }
}

main().catch((e) => {
  console.error("\nverify-goal-total failed:", e.message ?? e);
  process.exit(1);
});
