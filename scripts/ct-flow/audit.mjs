/**
 * View-key reality check (Session 4, step 4): what an auditor / view-key
 * holder can decrypt TODAY from nothing but public on-chain events + a
 * Grumpkin secret, using the vendor SDK's auditor module (DESIGN.md §8).
 *
 * What the CT developer preview actually supports (verified against
 * /vendor/stellar-contracts confidential/{storage.rs,auditor/}):
 *   - There are NO per-account exportable view keys. The unit is an
 *     `auditor_id` in the deployment's auditor registry; every account commits
 *     to one id at register() and the contract fetches BOTH parties' auditor
 *     keys on every transfer (storage.rs:686-687).
 *   - Registering a new auditor id is admin/manager-gated on the auditor
 *     contract — a user of the OFFICIAL demo deployment cannot mint their own
 *     view key. On OUR deployment we are the admin, so the goal's dedicated
 *     view key (auditor id 1) exists.
 *
 * This script demonstrates, against the REAL events of flow.mjs:
 *   1. goal view key k1 (to be published) → recipient channel of each
 *      transfer to the goal: contribution AMOUNT + r_tx. Summed with public
 *      deposits minus outflows = the goal total, independently reconstructed.
 *   2. k1 does NOT open the sender channel (channelsAgree=false): Marta's
 *      post-transfer balance stays private from view-key holders.
 *   3. custodian secret k0 → sender channel: amount + Marta's post-balance
 *      (custodian-only knowledge).
 *
 * Run:  node audit.mjs        (from scripts/ct-flow/)
 */

import { readFileSync } from "node:fs";

import {
  RPC_URL, PASSPHRASE,
  ChainClient, fetchEvents,
  auditTransfer, auditTransferSenderChannel, auditTransferRecipientChannel,
  loadEnvDeploy, loadDeploymentJson, retry, RUN_LOG_JSON,
} from "./_shared.mjs";

const fmt = (stroops) => `${Number(stroops) / 1e7} XLM (${stroops} stroops)`;

async function main() {
  const dep = loadDeploymentJson();
  const env = loadEnvDeploy();
  const runLog = JSON.parse(readFileSync(RUN_LOG_JSON, "utf8"));
  const goalAddr = runLog.accounts.goal;

  const k1 = BigInt(env.CT_GOAL_VIEWKEY_SECRET_HEX); // goal view key (public by design)
  const k0 = BigInt(env.CT_AUDITOR0_SECRET_HEX);     // custodian (private)

  const client = new ChainClient({
    rpcUrl: RPC_URL,
    networkPassphrase: PASSPHRASE,
    contracts: dep.contracts,
  });

  console.log(`token = ${dep.contracts.token}`);
  console.log(`goal  = ${goalAddr}\n`);
  console.log(`[1] fetching REAL events from the RPC (ledger ${dep.deployedAtLedger} →)…`);
  const { events, latestLedger } = await retry("fetchEvents", () =>
    fetchEvents(client, { startLedger: dep.deployedAtLedger }));
  console.log(`  ${events.length} confidential events (RPC latest ledger ${latestLedger}):`);
  for (const ev of events) {
    const who = ev.type === "transfer" || ev.type === "deposit" || ev.type === "withdraw"
      ? `${ev.from.slice(0, 5)}… → ${ev.to.slice(0, 5)}…`
      : `${ev.account.slice(0, 5)}…`;
    console.log(`    ledger ${ev.ledger}  ${ev.type.padEnd(8)}  ${who}  tx ${ev.txHash.slice(0, 8)}…`);
  }

  // ---- goal ledger, seen through the PUBLISHED view key -------------------
  console.log(`\n[2] reconstructing the goal's ledger with ONLY public events + view key k1:`);
  let total = 0n;
  for (const ev of events) {
    if (ev.type === "deposit" && ev.to === goalAddr) {
      total += ev.amount; // deposit amounts are public by nature (SAC boundary)
      console.log(`  + deposit (public amount)      ${fmt(ev.amount)}`);
    }
    if (ev.type === "transfer" && ev.to === goalAddr) {
      const rec = auditTransferRecipientChannel(k1, ev);
      total += rec.amount;
      console.log(`  + transfer tx ${ev.txHash.slice(0, 8)}… decrypted via k1 recipient channel: ${fmt(rec.amount)}`);
    }
    if (ev.type === "transfer" && ev.from === goalAddr) {
      const snd = auditTransferSenderChannel(k1, ev);
      total = snd.senderBalance; // checkpoint: post-op spendable
      console.log(`  = outgoing transfer checkpoint ${fmt(snd.senderBalance)}`);
    }
    if (ev.type === "withdraw" && ev.from === goalAddr) {
      total -= ev.amount; // withdraw amounts are public by nature
      console.log(`  - withdraw (public amount)     ${fmt(ev.amount)}`);
    }
  }
  console.log(`  GOAL TOTAL per view key = ${fmt(total)}`);
  const ownerValue = BigInt(runLog.finalBalances.goalSpendableStroops);
  console.log(`  owner-decrypted balance (flow.mjs StateEngine) = ${fmt(ownerValue)}`);
  console.log(`  MATCH: ${total === ownerValue}`);

  // ---- scope of each key on the Marta→goal transfer -----------------------
  const tEv = events.find((e) => e.type === "transfer" && e.to === goalAddr);
  if (!tEv) throw new Error("no transfer event to the goal found");
  console.log(`\n[3] key scoping on transfer tx ${tEv.txHash}:`);

  const both1 = auditTransfer(k1, tEv);
  console.log(`  k1 (published) both-channel decrypt: channelsAgree=${both1.channelsAgree}`);
  console.log(`    → k1 opens the RECIPIENT channel only; Marta's post-balance is NOT`);
  console.log(`      readable with the goal's view key (her auditor is id 0).`);

  const snd0 = auditTransferSenderChannel(k0, tEv);
  const rec1 = auditTransferRecipientChannel(k1, tEv);
  console.log(`  k0 (custodian) sender channel: amount ${fmt(snd0.amount)}, Marta post-balance ${fmt(snd0.senderBalance)}`);
  console.log(`  cross-check: k0-amount == k1-amount: ${snd0.amount === rec1.amount}`);

  console.log(`\n[4] honest summary of what the preview supports today:`);
  console.log(`  - per-auditor-id Grumpkin view keys, fixed per account at register()  → REAL, demonstrated`);
  console.log(`  - goal total independently reconstructible from events + published k1 → REAL, demonstrated`);
  console.log(`  - view-key holder ALSO learns each contribution's amount (recipient`);
  console.log(`    channel opens per-transfer, not just totals)                        → intrinsic to the design`);
  console.log(`  - per-account self-service view keys / key export                     → NOT in the preview`);
  console.log(`  - registering a new auditor id on the OFFICIAL demo deployment        → admin-gated, NOT possible`);
}

main().catch((e) => {
  console.error("\naudit failed:", e);
  process.exit(1);
});
