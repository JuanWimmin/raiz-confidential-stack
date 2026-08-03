/**
 * smoke-chain.mjs — loads the BUILT browser bundle (dist/prover.js) in Node and
 * exercises the read-only/secret-free RaizChain paths against the real testnet:
 *   - status(goal account)            (simulate confidential_balance)
 *   - prepareDeposit(Marta -> Marta)  (build + simulate + assemble; NOT submitted)
 * Only public addresses from scripts/ct-flow.md are used. Proving paths are not
 * exercised here (bb.js browser workers need a real browser) — the device run
 * covers those.
 *
 * Run: node wallet/tools/smoke-chain.mjs
 */
import path from "node:path";
import { pathToFileURL, fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const bundle = path.join(
  path.dirname(here), "app", "src", "main", "assets", "prover", "dist", "prover.js",
);

// Node lacks the browser `self` global the stellar-sdk UMD wrapper probes;
// every real target (WebView, Chrome) has it. Test-env shim only.
globalThis.self ??= globalThis;

await import(pathToFileURL(bundle).href);

const common = {
  rpcUrl: "https://soroban-testnet.stellar.org",
  token: "CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT",
  verifier: "CBFCYFND44SNQPKMQNHB3KX2C7K4U5WSVUMFJY34OV46YAN2SACM3UIA",
  auditor: "CBUSX5B56KB73FAAIIHW7ISSZEGHDKQTOWML74LBPOWWGCEFEZPLHE25",
};
const MARTA = "GDUTRPFZAL3QRHCY47A6KAI6EK4XJTZ35J5IWI7YN3VGHHWA5F77DJ2I";
const GOAL = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X";

const st = await globalThis.RaizChain.status(JSON.stringify({ ...common, account: GOAL }));
console.log("status(goal) =", JSON.stringify(st));
if (st.registered !== true) throw new Error("goal must be registered (ct-flow.md says it is)");

const dep = await globalThis.RaizChain.prepareDeposit(JSON.stringify({
  ...common, from: MARTA, to: MARTA, amountStroops: "100000000",
}));
console.log("prepareDeposit(Marta,10 XLM) =", JSON.stringify({ ...dep, unsignedXdrBase64: dep.unsignedXdrBase64.slice(0, 60) + `… (${dep.unsignedXdrBase64.length} b64 chars)` }));
const env = Buffer.from(dep.unsignedXdrBase64, "base64");
const ok = env[3] === 2 && env.readUInt32BE(env.length - 4) === 0;
console.log(`envelope sanity: type=ENVELOPE_TYPE_TX=${env.readUInt32BE(0)}, trailing sig count=${env.readUInt32BE(env.length - 4)} -> ${ok ? "OK" : "BAD"}`);
if (!ok) process.exit(1);
console.log("SMOKE OK");
