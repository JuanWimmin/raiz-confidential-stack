/**
 * make-receipt — "Mi recibo": Marta produces a REAL selective-disclosure proof
 * of her contribution (SELECTIVE_DISCLOSURE.md §7, D-sender: "this on-chain
 * payment was sent by me for this amount"), using the vendor circuits and SDK
 * unmodified.
 *
 * The protocol has two parties; this demo script plays both, in clearly
 * marked sections:
 *
 *   [verifier]  a third party (the community treasurer, a judge, a bank desk)
 *               mints a request (P_R, nu): their long-lived Grumpkin pubkey
 *               plus a fresh nonce. The resulting proof is BOUND to that pair —
 *               useless to anyone else, non-replayable (§13.2).
 *   [marta]     the contributor proves, with her wallet keys only, that SHE
 *               originated the referenced on-chain transfer and that its
 *               sealed amount is what the verifier will decrypt. No per-tx
 *               state needed: the ephemeral scalar r_e is re-derived from her
 *               viewing key + the event's public sigma (§15.2).
 *
 * Output: receipt.json — the shareable artifact = disclosure request + proof
 * bundle + a plain-language claim. The AMOUNT itself is sealed to the
 * verifier's key: an eavesdropper who archives the receipt learns nothing.
 *
 * Usage (after the vendor setup in README.md; needs .env.deploy for Marta's
 * confidential scalar — testnet play-money):
 *
 *   node make-receipt.mjs [--tx <hash>] [--out receipt.json]
 */

import { writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  ChainClient, fetchEvents,
  deriveKeys, deriveEphemeralRE, scalarMul, H, pointCoords,
  proverFromArtifact, enableThreadedProving, THREADS,
  proveSenderDisclosure, recipientKeysFromSecret,
  newDisclosureRequest, pointFromJson, DISCLOSE_SENDER_CIRCUIT_ID,
  buildDiscloseSenderWitness, loadDisclosureArtifact,
  loadDeployment, loadEnvDeploy, retry, fmtXlm,
} from "./_vendor.mjs";
import { DEMO_VERIFIER_SECRET_HEX, DEMO_VERIFIER_BANNER } from "./demo-verifier-key.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = args.indexOf(name);
  return i >= 0 && args[i + 1] !== undefined ? args[i + 1] : fallback;
};

// Default: Marta's 25 XLM contribution from ct-flow run 1 (scripts/ct-flow.md §4).
const TX_HASH = arg("--tx", "5836313815618675a8530b3d3efb5e931e29ba9d49d58d90562414bc8c5463a4");
const OUT = arg("--out", path.join(HERE, "receipt.json"));

async function main() {
  const dep = loadDeployment();
  const env = loadEnvDeploy();
  if (!env.CT_MARTA_CONF_SK) throw new Error("CT_MARTA_CONF_SK missing from .env.deploy");

  const client = new ChainClient({
    rpcUrl: dep.rpcUrl,
    networkPassphrase: dep.passphrase,
    contracts: dep.contracts,
  });

  // ---- [verifier] mint the disclosure request ------------------------------
  // Long-lived keypair (r_R, P_R): stable across runs so verify-receipt.mjs
  // can decrypt; the NONCE is fresh per request. A real verifier's own key
  // (RECEIPT_VERIFIER_SECRET_HEX in .env.deploy) always wins; otherwise we
  // use the PUBLISHED demo key so a regenerated receipt.json stays verifiable
  // by anyone who cloned the repo — see demo-verifier-key.mjs for why that is
  // safe here and why no real verifier should ever do it.
  let verifierKeys;
  if (env.RECEIPT_VERIFIER_SECRET_HEX) {
    verifierKeys = recipientKeysFromSecret(BigInt(env.RECEIPT_VERIFIER_SECRET_HEX));
    console.log(`[verifier] reusing recipient key from .env.deploy (P_R = ${verifierKeys.pR.x.slice(0, 10)}…)`);
  } else {
    verifierKeys = recipientKeysFromSecret(BigInt(DEMO_VERIFIER_SECRET_HEX));
    console.log(`[verifier] ${DEMO_VERIFIER_BANNER}`);
    console.log(`[verifier] P_R = ${verifierKeys.pR.x.slice(0, 10)}…`);
  }
  const request = newDisclosureRequest(verifierKeys);
  console.log(`[verifier] request minted: fresh nonce ${request.nu.slice(0, 10)}… — the proof will bind to (P_R, nu)`);

  // ---- [marta] locate HER on-chain transfer --------------------------------
  console.log(`\n[marta] fetching events from ledger ${dep.deployedAtLedger} to find tx ${TX_HASH.slice(0, 8)}…`);
  const { events } = await retry("fetchEvents", () =>
    fetchEvents(client, { startLedger: dep.deployedAtLedger }));
  const event = events.find((e) => e.type === "transfer" && e.txHash === TX_HASH);
  if (!event) {
    throw new Error(`no transfer event with tx ${TX_HASH} found from ledger ${dep.deployedAtLedger} (outside RPC retention? wrong hash?)`);
  }
  console.log(`[marta] found: transfer ${event.from.slice(0, 6)}… → ${event.to.slice(0, 6)}… (ledger ${event.ledger})`);

  const marta = deriveKeys(BigInt(env.CT_MARTA_CONF_SK), BigInt(dep.addrF));

  // The D-sender witness needs the transfer-time ephemeral scalar r_e —
  // re-derived, not stored: r_e = Poseidon2(EPHEMERAL_KEY, vk, sigma) (§15.2).
  const rEScalar = deriveEphemeralRE(marta.vk, event.sigma);
  const rePoint = pointCoords(scalarMul(rEScalar, H));
  const evPoint = pointCoords(event.rE);
  if (rePoint.x !== evPoint.x || rePoint.y !== evPoint.y) {
    throw new Error("re-derived r_e does not match the event's R_e — is Marta really this event's sender?");
  }
  console.log(`[marta] r_e re-derived from vk + event sigma; r_e·H == event R_e  OK`);

  // PVK_B: the transfer recipient's stored viewing key, read from chain.
  const goalAccount = await retry("confidentialBalance(goal)", () => client.confidentialBalance(event.to));
  if (!goalAccount) throw new Error(`event recipient ${event.to} has no confidential account`);
  const pvkB = goalAccount.viewingPublicKey;

  // What the proof will disclose (computed locally for the claim text; the
  // bundle itself carries the amount only SEALED to the verifier's key).
  const preview = buildDiscloseSenderWitness({
    keys: marta, rEScalar,
    event: { rE: event.rE, sigma: event.sigma, vTilde: event.vTilde },
    pvkB, pR: pointFromJson(request.pR), nu: BigInt(request.nu),
  });
  console.log(`[marta] disclosing amount: ${fmtXlm(preview.vTx)}`);

  // ---- [marta] prove (vendor disclose_sender circuit, unmodified) ----------
  await enableThreadedProving();
  const prover = proverFromArtifact(loadDisclosureArtifact(DISCLOSE_SENDER_CIRCUIT_ID));
  console.log(`[marta] proving D-sender disclosure (UltraHonk keccak, ${THREADS} threads)…`);
  const t0 = Date.now();
  const bundle = await proveSenderDisclosure({ keys: marta, rEScalar, event, pvkB, request, prover });
  const provingMs = Date.now() - t0;
  console.log(`[marta] proof ready: ${(bundle.proof.length - 2) / 2} bytes in ${provingMs} ms`);
  await prover.destroy();

  // ---- the shareable artifact ----------------------------------------------
  const receipt = {
    format: "sobre-del-barrio/mi-recibo@1",
    createdAt: new Date().toISOString(),
    network: dep.network,
    token: dep.contracts.token,
    claim: {
      statement:
        "The contributor account originated the on-chain confidential transfer referenced in bundle.refE, " +
        "and its amount is exactly the value sealed in the bundle's disclosure ciphertext. " +
        "The proof is cryptographically bound to the verifier's (P_R, nu) below: it cannot be replayed to, " +
        "or decrypted by, anyone else. Verification reads every public input from the chain, never from this file.",
      contributor: event.from,
      goal: event.to,
      amountStroops: preview.vTx.toString(),
      amountXlm: Number(preview.vTx) / 1e7,
      explorer: `https://stellar.expert/explorer/testnet/tx/${event.txHash}`,
    },
    request,
    bundle,
    provingMs,
    verify: "node verify-receipt.mjs [receipt.json]  (needs RECEIPT_VERIFIER_SECRET_HEX — the verifier's own key)",
  };
  writeFileSync(OUT, JSON.stringify(receipt, null, 2) + "\n");
  console.log(`\nreceipt written: ${OUT}`);
  console.log(`  claim: ${event.from.slice(0, 6)}… paid ${fmtXlm(preview.vTx)} to ${event.to.slice(0, 6)}… in tx ${event.txHash.slice(0, 8)}…`);
  console.log(`  the amount in the bundle is SEALED to the verifier's key — the receipt leaks nothing to third parties.`);
  process.exit(0); // bb.js worker threads would otherwise keep the process alive
}

main().catch((e) => {
  console.error("\nmake-receipt failed:", e.message ?? e);
  process.exit(1);
});
