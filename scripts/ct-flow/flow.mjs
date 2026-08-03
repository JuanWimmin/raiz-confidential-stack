/**
 * Session-4 full Confidential Token cycle on testnet, CLI-only (no browser,
 * no Freighter): the exact flow the wallet will later drive from Android.
 *
 *   register(Marta, auditor id 0)   [ZK proof, proven locally]
 *   register(goal,  auditor id 1)   [ZK proof — id 1 = the published view key]
 *     → deposit(Marta, 100 XLM)     [public XLM → confidential receiving; NO proof]
 *     → merge(Marta)                [receiving → spendable; NO proof]
 *     → confidential_transfer(Marta → goal, 25 XLM)  [ZK proof; amount hidden]
 *     → merge(goal)
 *     → goal + Marta balances DECRYPTED locally via the vendor StateEngine
 *       (event replay + ECDH) and verified against on-chain commitments.
 *
 * Every artifact the video needs (tx hashes, ledgers, explorer URLs, decrypted
 * balances) is printed AND written to scripts/ct-flow/run-log.json.
 *
 * Accounts are stable across runs (persisted in .env.deploy) so the same
 * addresses feed goal_meta (Session 3/6) and the Raiz Memory timeline.
 * register is skipped when the account is already registered; deposit/transfer
 * amounts accumulate if you run it twice (fine for a demo wrapper).
 *
 * Run:  node flow.mjs        (from scripts/ct-flow/)
 */

import { writeFileSync } from "node:fs";

import {
  RPC_URL, PASSPHRASE, THREADS,
  ChainClient, keypairSigner, addressToField, deriveKeys,
  CircuitProver, loadCircuit, enableThreadedProving,
  StateEngine, MemoryStore,
  submitRegister, submitDeposit, submitMerge, submitTransfer,
  buildRegisterWitness, buildTransferWitness,
  loadEnvDeploy, saveEnvDeploy, ensureKeypair, ensureScalar, friendbotFund,
  retry, loadDeploymentJson, txLedger, explorerTx, RUN_LOG_JSON, jsonify,
} from "./_shared.mjs";

const MARTA_AUDITOR_ID = 0; // custodian auditor — contributor balances stay private
const GOAL_AUDITOR_ID = 1;  // the goal's published view key ("Verifícalo tú mismo")

const XLM = 10_000_000n; // stroops
const DEPOSIT = 100n * XLM;
const TRANSFER = 25n * XLM;

const fmt = (stroops) => `${Number(stroops) / 1e7} XLM (${stroops} stroops)`;

async function main() {
  await enableThreadedProving();
  const dep = loadDeploymentJson();
  const env = loadEnvDeploy();
  console.log(`token = ${dep.contracts.token} (our own instance, ledger ${dep.deployedAtLedger})`);
  console.log(`proving threads = ${THREADS}\n`);

  const client = new ChainClient({
    rpcUrl: RPC_URL,
    networkPassphrase: PASSPHRASE,
    contracts: dep.contracts,
  });

  // ---- accounts (stable, persisted) ---------------------------------------
  const marta = ensureKeypair(env, "CT_MARTA_SECRET");
  const goal = ensureKeypair(env, "CT_GOAL_SECRET");
  const martaSk = ensureScalar(env, "CT_MARTA_CONF_SK");
  const goalSk = ensureScalar(env, "CT_GOAL_CONF_SK");
  saveEnvDeploy(env);
  console.log(`Marta (contributor) = ${marta.publicKey()}`);
  console.log(`Goal  (cuenta-meta) = ${goal.publicKey()}\n`);
  await friendbotFund(marta.publicKey());
  await friendbotFund(goal.publicKey());

  const martaSigner = keypairSigner(marta.secret(), PASSPHRASE);
  const goalSigner = keypairSigner(goal.secret(), PASSPHRASE);

  // ---- confidential key sets (contract-bound) -----------------------------
  const addrF = addressToField(dep.contracts.token);
  const martaKeys = deriveKeys(martaSk, addrF);
  const goalKeys = deriveKeys(goalSk, addrF);

  const kAudMarta = await retry("auditorKey(0)", () => client.auditorKey(MARTA_AUDITOR_ID));
  const kAudGoal = await retry("auditorKey(1)", () => client.auditorKey(GOAL_AUDITOR_ID));

  const martaEngine = new StateEngine({
    client, store: new MemoryStore(), keys: martaKeys,
    address: marta.publicKey(), fromLedger: dep.deployedAtLedger,
  });
  const goalEngine = new StateEngine({
    client, store: new MemoryStore(), keys: goalKeys,
    address: goal.publicKey(), fromLedger: dep.deployedAtLedger,
  });

  const registerProver = new CircuitProver(loadCircuit("register"));
  const transferProver = new CircuitProver(loadCircuit("transfer"));

  const log = {
    ranAt: new Date().toISOString(),
    network: "testnet",
    token: dep.contracts.token,
    accounts: { marta: marta.publicKey(), goal: goal.publicKey() },
    auditorIds: { marta: MARTA_AUDITOR_ID, goal: GOAL_AUDITOR_ID },
    amounts: { depositStroops: DEPOSIT, transferStroops: TRANSFER },
    steps: [],
  };
  const step = async (name, r, extra = {}) => {
    const ledger = await txLedger(client, r.hash);
    const entry = { name, txHash: r.hash, ledger, explorer: explorerTx(r.hash), ...extra };
    log.steps.push(entry);
    console.log(`  tx ${r.hash}\n  ledger ${ledger} · ${entry.explorer}\n`);
    return entry;
  };

  try {
    // ---- 1/2. register ----------------------------------------------------
    for (const [label, kp, signer, keys, auditorId] of [
      ["Marta", marta, martaSigner, martaKeys, MARTA_AUDITOR_ID],
      ["goal", goal, goalSigner, goalKeys, GOAL_AUDITOR_ID],
    ]) {
      if (await retry(`isRegistered(${label})`, () => client.isRegistered(kp.publicKey()))) {
        console.log(`[register ${label}] already registered — skipping\n`);
        continue;
      }
      console.log(`[register ${label}] auditor id ${auditorId} — proving locally…`);
      const w = buildRegisterWitness(keys);
      const t0 = performance.now();
      const { proof } = await registerProver.prove(w.inputs);
      const proveMs = Math.round(performance.now() - t0);
      console.log(`  proof ${proof.length}B in ${proveMs}ms (${THREADS} threads); submitting…`);
      const r = await retry(`register ${label}`, () =>
        submitRegister(client, signer, kp.publicKey(), auditorId, w, proof));
      await step(`register ${label}`, r, { auditorId, proveMs, proofBytes: proof.length });
    }

    // ---- 3. deposit (public → confidential; amount is PUBLIC here) --------
    console.log(`[deposit] Marta seals ${fmt(DEPOSIT)} — no proof needed`);
    const rDep = await retry("deposit", () =>
      submitDeposit(client, martaSigner, marta.publicKey(), marta.publicKey(), DEPOSIT));
    await step("deposit Marta", rDep, { amountStroops: DEPOSIT, amountVisibleOnChain: true });

    // ---- 4. merge Marta (receiving → spendable) ---------------------------
    console.log(`[merge] Marta folds receiving into spendable — no proof needed`);
    const rMergeM = await retry("merge Marta", () =>
      submitMerge(client, martaSigner, marta.publicKey()));
    await step("merge Marta", rMergeM);

    const sMarta1 = await retry("Marta sync", () => martaEngine.sync());
    console.log(`  Marta spendable (decrypted locally) = ${fmt(sMarta1.spendable.v)}\n`);

    // ---- 5. confidential transfer Marta → goal (amount HIDDEN) ------------
    console.log(`[transfer] Marta aporta ${fmt(TRANSFER)} to the goal — proving locally…`);
    const wT = buildTransferWitness({
      keys: martaKeys,
      v: sMarta1.spendable.v,
      r: sMarta1.spendable.r,
      amount: TRANSFER,
      pvkB: goalKeys.PVK,
      kAudR: kAudGoal,  // recipient (goal) channel → decryptable with the PUBLISHED view key
      kAudS: kAudMarta, // sender channel → custodian only; Marta's balance stays private
    });
    const t0 = performance.now();
    const { proof: proofT } = await transferProver.prove(wT.inputs);
    const proveMs = Math.round(performance.now() - t0);
    console.log(`  proof ${proofT.length}B in ${proveMs}ms; submitting…`);
    const rT = await retry("confidential_transfer", () =>
      submitTransfer(client, martaSigner, marta.publicKey(), goal.publicKey(), wT, proofT));
    await step("confidential_transfer Marta→goal", rT, {
      proveMs, proofBytes: proofT.length, amountVisibleOnChain: false,
    });

    // ---- 6. goal sees the credit (decrypted from the event) ---------------
    const sGoal1 = await retry("goal sync", () => goalEngine.sync());
    console.log(`  goal receiving (decrypted from event) = ${fmt(sGoal1.receiving.v)}\n`);

    // ---- 7. merge goal ("cosechar") ---------------------------------------
    console.log(`[merge] goal harvests pending contributions — no proof needed`);
    const rMergeG = await retry("merge goal", () =>
      submitMerge(client, goalSigner, goal.publicKey()));
    await step("merge goal", rMergeG);

    // ---- 8. final decrypted balances, verified against the chain ----------
    const sMarta2 = await retry("Marta final sync", () => martaEngine.sync());
    const sGoal2 = await retry("goal final sync", () => goalEngine.sync());
    const vMarta = await retry("Marta verify", () => martaEngine.verifyAgainstChain());
    const vGoal = await retry("goal verify", () => goalEngine.verifyAgainstChain());

    console.log(`\n[balances — decrypted locally, re-committed against on-chain state]`);
    console.log(`  Marta spendable = ${fmt(sMarta2.spendable.v)}  matches chain: ${vMarta.ok}`);
    console.log(`  goal  spendable = ${fmt(sGoal2.spendable.v)}  matches chain: ${vGoal.ok}`);

    log.finalBalances = {
      martaSpendableStroops: sMarta2.spendable.v,
      goalSpendableStroops: sGoal2.spendable.v,
      martaMatchesChain: vMarta.ok,
      goalMatchesChain: vGoal.ok,
    };
    writeFileSync(RUN_LOG_JSON, jsonify(log) + "\n");
    console.log(`\nwrote ${RUN_LOG_JSON}`);

    if (!vMarta.ok || !vGoal.ok) throw new Error("local state does not match on-chain commitments");
    console.log("\nOK — full confidential cycle verified on testnet from the CLI.");
  } finally {
    await Promise.all([registerProver.destroy(), transferProver.destroy()]);
  }
}

main().catch((e) => {
  console.error("\nflow failed:", e);
  process.exit(1);
});
