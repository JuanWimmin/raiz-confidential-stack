/**
 * Node proving baseline for the CT spike (no browser, no Freighter, no chain).
 *
 * For each circuit (register, withdraw, transfer) it times two paths:
 *
 *   1. "split" — the exact two steps the browser bench page runs:
 *      noir_js `Noir.execute(inputs)` (witness solving) and then
 *      bb.js `UltraHonkBackend.generateProof(witness, { keccak: true })`
 *      with an EXPLICIT thread count. The proof is locally verified.
 *
 *   2. "sdk" — the vendor SDK's own `CircuitProver.prove(inputs)`, unmodified.
 *      Note: the SDK constructs `new UltraHonkBackend(bytecode)` with no
 *      options, and bb.js 0.87.0 defaults that to `{ threads: 1 }`, so this
 *      path is single-threaded by design. It is the demo-app-equivalent
 *      number AND the proof that our witness inputs are valid for the SDK.
 *
 * Requires network on first run: bb.js downloads the CRS from
 * https://crs.aztec.network and caches it under ~/.bb-crs.
 *
 * Run:  node bench-node.mjs           (from scripts/prover-bench/)
 */

import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";
import os from "node:os";
import path from "node:path";

import { INPUT_BUILDERS } from "./src/witness-inputs.mjs";
import { CircuitProver } from "../../vendor/stellar-confidential-token-demo/packages/sdk/dist/proving/prover.js";
import { loadCircuit } from "../../vendor/stellar-confidential-token-demo/packages/sdk/dist/proving/artifacts.js";

// ---------------------------------------------------------------------------
// Resolve noir_js and bb.js out of the vendor workspace's own node_modules
// (pnpm layout), anchored at the SDK so we get the exact installed versions.
// ---------------------------------------------------------------------------
const SDK_PROVER = new URL(
  "../../vendor/stellar-confidential-token-demo/packages/sdk/dist/proving/prover.js",
  import.meta.url,
);
const sdkRequire = createRequire(SDK_PROVER);

// noir_js: require-condition resolves to lib/index.cjs; import() interops fine.
const { Noir } = await import(pathToFileURL(sdkRequire.resolve("@noir-lang/noir_js")).href);

// bb.js: require-condition resolves dest/node-cjs/index.js; we want the same
// ESM build (dest/node) the SDK's default loader uses, so walk to the package
// root and import it by file URL.
const bbCjsEntry = sdkRequire.resolve("@aztec/bb.js"); // <root>/dest/node-cjs/index.js
const bbRoot = path.resolve(path.dirname(bbCjsEntry), "..", "..");
const { UltraHonkBackend } = await import(
  pathToFileURL(path.join(bbRoot, "dest", "node", "index.js")).href
);

const THREADS = Math.min(os.cpus().length, 32);
const KECCAK = { keccak: true }; // mandatory: on-chain verifier expects keccak transcript
const round = (x) => Math.round(x);

console.log(`prover-bench (Node ${process.version}, ${os.cpus().length} CPUs)`);
console.log(`explicit thread count for the split path: ${THREADS}`);
console.log(`(SDK CircuitProver path runs with bb.js's default { threads: 1 })\n`);

const results = [];
let failures = 0;

for (const name of ["register", "withdraw", "transfer"]) {
  const circuit = loadCircuit(name);
  const inputs = INPUT_BUILDERS[name]();
  const row = { circuit: name };

  // --- split path (mirrors the browser page) -------------------------------
  try {
    const noir = new Noir(circuit);
    let t0 = performance.now();
    const { witness } = await noir.execute(inputs);
    row.witnessMs = round(performance.now() - t0);

    const backend = new UltraHonkBackend(circuit.bytecode, { threads: THREADS });
    t0 = performance.now();
    const { proof, publicInputs } = await backend.generateProof(witness, KECCAK);
    row.proveMs = round(performance.now() - t0);
    row.proofBytes = proof.length;
    row.publicInputs = publicInputs.length;

    const ok = await backend.verifyProof({ proof, publicInputs }, KECCAK);
    row.verified = ok;
    if (!ok) failures++;
    await backend.destroy();
  } catch (e) {
    failures++;
    row.error = String(e?.message ?? e).split("\n")[0];
    console.error(`  x ${name} (split): ${row.error}`);
  }

  // --- SDK path (unmodified CircuitProver, validates the inputs) -----------
  try {
    const prover = new CircuitProver(circuit);
    const t0 = performance.now();
    const result = await prover.prove(inputs);
    row.sdkTotalMs = round(performance.now() - t0);
    row.sdkProofBytes = result.proof.length;
    await prover.destroy();
  } catch (e) {
    failures++;
    row.sdkError = String(e?.message ?? e).split("\n")[0];
    console.error(`  x ${name} (sdk): ${row.sdkError}`);
  }

  results.push(row);
  console.log(
    `  ${name.padEnd(8)}  witness ${String(row.witnessMs ?? "-").padStart(6)} ms` +
      `  prove(${THREADS}t) ${String(row.proveMs ?? "-").padStart(7)} ms` +
      `  sdk-prove(1t) ${String(row.sdkTotalMs ?? "-").padStart(7)} ms` +
      `  proof ${row.proofBytes ?? "-"} B` +
      `  verified=${row.verified ?? "-"}`,
  );
}

console.log("\nJSON:");
console.log(JSON.stringify(results, null, 2));
process.exit(failures === 0 ? 0 : 1);
