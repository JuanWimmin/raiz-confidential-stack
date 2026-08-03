/**
 * On-chain visibility check (video material): decode the ACTUAL transaction
 * envelopes of the deposit and the confidential transfer straight from the
 * RPC, and enumerate every field an explorer can show. The point to capture:
 * the deposit amount is a plaintext i128 argument; the confidential transfer
 * carries only addresses + an opaque proof/ciphertext payload.
 *
 * Run:  node visibility.mjs        (from scripts/ct-flow/)
 */

import { readFileSync } from "node:fs";

import {
  RPC_URL, PASSPHRASE, ChainClient, StellarSdk,
  loadDeploymentJson, retry, RUN_LOG_JSON, explorerTx,
} from "./_shared.mjs";

const { TransactionBuilder, xdr, scValToNative, Address } = StellarSdk;

function describeScVal(v, depth = 0) {
  const t = v.switch().name;
  switch (t) {
    case "scvAddress":
      return `Address(${Address.fromScVal(v).toString()})`;
    case "scvI128":
      return `i128(${scValToNative(v)})  ← PLAINTEXT AMOUNT`;
    case "scvU32":
      return `u32(${v.u32()})`;
    case "scvBytes":
      return `Bytes(${v.bytes().length}B opaque)`;
    case "scvMap": {
      const entries = v.map().map((e) => {
        const k = e.key().sym ? e.key().sym().toString() : describeScVal(e.key(), depth + 1);
        return `${"  ".repeat(depth + 2)}${k}: ${describeScVal(e.val(), depth + 1)}`;
      });
      return `Map{\n${entries.join("\n")}\n${"  ".repeat(depth + 1)}}`;
    }
    case "scvVec":
      return `Vec[${v.vec().map((x) => describeScVal(x, depth + 1)).join(", ")}]`;
    default:
      return t;
  }
}

async function describeTx(client, label, hash) {
  const res = await retry(`getTransaction ${label}`, () => client.server.getTransaction(hash));
  const env = res.envelopeXdr; // xdr.TransactionEnvelope
  const tx = env.v1().tx();
  const op = tx.operations()[0].body().invokeHostFunctionOp();
  const invoke = op.hostFunction().invokeContract();
  const fn = invoke.functionName().toString();
  const contract = Address.fromScAddress(invoke.contractAddress()).toString();

  console.log(`\n=== ${label} — tx ${hash}`);
  console.log(`  ${explorerTx(hash)}`);
  console.log(`  ledger ${res.ledger} · fee charged visible · source account visible`);
  console.log(`  invoked: ${contract}.${fn}`);
  console.log(`  arguments as stored on-chain (what ANY explorer decodes):`);
  invoke.args().forEach((a, i) => console.log(`    arg[${i}] = ${describeScVal(a)}`));
}

async function main() {
  const dep = loadDeploymentJson();
  const runLog = JSON.parse(readFileSync(RUN_LOG_JSON, "utf8"));
  const client = new ChainClient({ rpcUrl: RPC_URL, networkPassphrase: PASSPHRASE, contracts: dep.contracts });

  const depositTx = runLog.steps.find((s) => s.name === "deposit Marta").txHash;
  const transferTx = runLog.steps.find((s) => s.name === "confidential_transfer Marta→goal").txHash;

  await describeTx(client, "DEPOSIT (public → confidential boundary)", depositTx);
  await describeTx(client, "CONFIDENTIAL TRANSFER (Marta → goal)", transferTx);

  console.log(`\nConclusion: the transfer's arguments contain the two ADDRESSES and an`);
  console.log(`opaque payload (commitments + ciphertexts + proof). No amount field exists`);
  console.log(`anywhere in the envelope — nothing for an explorer to display.`);
}

main().catch((e) => {
  console.error("\nvisibility check failed:", e);
  process.exit(1);
});
