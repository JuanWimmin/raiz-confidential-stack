/**
 * verify-receipt — the disclosure RECEIVER's side of "Mi recibo": run the
 * mandatory verifier protocol of SELECTIVE_DISCLOSURE.md §5.3 against a
 * receipt.json produced by make-receipt.mjs.
 *
 * Everything that matters is re-read from the chain, not from the receipt
 * (§5.2 trust-boundary rule, enforced by the vendor `verifyDisclosure`):
 *
 *   1. VK pinning — the circuit artifact's derived verification key must match
 *      the pinned vk.json shipped in @ctd/disclosure, byte for byte.
 *   2. ref_E resolves to exactly one on-chain Transfer event of our token.
 *   3. The prover's PVK is read from the on-chain account the EVENT names
 *      (from = the contributor for D-sender), never from the receipt.
 *   4. Public inputs are rebuilt from chain state + the verifier's own (P_R, nu).
 *   5. UltraHonk proof verification.
 *   6. Only then is the sealed amount decrypted with the verifier's secret r_R.
 *
 * Usage:  node verify-receipt.mjs [receipt.json]
 * Needs RECEIPT_VERIFIER_SECRET_HEX in .env.deploy (the verifier's own key —
 * in a real deployment this never leaves the verifying party).
 */

import { readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

import {
  ChainClient, proverFromArtifact, enableThreadedProving,
  verifyDisclosure, DisclosureVerifyError, recipientKeysFromSecret,
  loadDisclosureArtifact, loadPinnedVk,
  loadDeployment, loadEnvDeploy, retry, fmtXlm,
} from "./_vendor.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const RECEIPT = process.argv[2] ?? path.join(HERE, "receipt.json");

async function main() {
  const receipt = JSON.parse(readFileSync(RECEIPT, "utf8"));
  const dep = loadDeployment();
  const env = loadEnvDeploy();
  if (!env.RECEIPT_VERIFIER_SECRET_HEX) {
    throw new Error("RECEIPT_VERIFIER_SECRET_HEX missing from .env.deploy — only the requesting verifier can check (and decrypt) a receipt");
  }
  if (receipt.token !== dep.contracts.token) {
    throw new Error(`receipt is for token ${receipt.token}, configured deployment is ${dep.contracts.token}`);
  }

  console.log(`receipt : ${RECEIPT}`);
  console.log(`claim   : ${receipt.claim.contributor.slice(0, 6)}… paid ${receipt.claim.amountXlm} XLM to ${receipt.claim.goal.slice(0, 6)}…`);
  console.log(`circuit : ${receipt.bundle.circuitId}\n`);

  const keys = recipientKeysFromSecret(BigInt(env.RECEIPT_VERIFIER_SECRET_HEX));
  const client = new ChainClient({
    rpcUrl: dep.rpcUrl,
    networkPassphrase: dep.passphrase,
    contracts: dep.contracts,
  });

  await enableThreadedProving();
  const prover = proverFromArtifact(loadDisclosureArtifact(receipt.bundle.circuitId));
  const pinnedVk = loadPinnedVk(receipt.bundle.circuitId);

  let verdict;
  try {
    verdict = await retry(
      "verifyDisclosure",
      () =>
        verifyDisclosure({
          client,
          bundle: receipt.bundle,
          request: receipt.request, // the verifier's own request record
          keys,
          prover,
          pinnedVk,
        }),
      // Protocol rejections are deterministic — only network flakes retry.
      { fatalIf: (e) => e instanceof DisclosureVerifyError },
    );
  } catch (e) {
    if (e instanceof DisclosureVerifyError) {
      console.error(`REJECTED at §5.3 stage [${e.stage}]: ${e.message}`);
      process.exit(1);
    }
    throw e;
  } finally {
    await prover.destroy();
  }

  for (const s of verdict.steps) console.log(`  ✔ ${s}`);

  console.log(`\nVERIFIED: the on-chain transfer in tx ${verdict.event.txHash.slice(0, 8)}… (ledger ${verdict.event.ledger})`);
  console.log(`  was SENT by ${verdict.disclosingAccount}`);
  console.log(`  for exactly ${fmtXlm(verdict.amount)}`);

  const claimed = BigInt(receipt.claim.amountStroops);
  if (claimed !== verdict.amount) {
    console.error(`\nWARNING: receipt's plain-language claim says ${fmtXlm(claimed)} but the proof discloses ${fmtXlm(verdict.amount)} — trust the proof.`);
    process.exit(1);
  }
  console.log(`  matching the receipt's claim. This is as trustworthy as the chain itself (§1.1).`);
  process.exit(0); // bb.js worker threads would otherwise keep the process alive
}

main().catch((e) => {
  console.error("\nverify-receipt failed:", e.message ?? e);
  process.exit(1);
});
