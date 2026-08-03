/**
 * raiz-shim.js — window.RaizProver, the ONLY JS surface the Kotlin bridge talks to.
 *
 * This file is OUR original code (Session 5). It wraps the vendor CT SDK's
 * witness builders (consumed AS-IS from /vendor, read-only) and the
 * noir_js + bb.js proving pipeline proven in scripts/prover-bench.
 *
 * IT IS THE ESBUILD BUNDLE ENTRY, NOT A RUNTIME FILE: the page loads
 * ./dist/prover.js, which is this file bundled together with noir_js and the
 * vendor SDK modules by wallet/tools/build-prover-assets.mjs. Regenerate with:
 *
 *   node wallet/tools/build-prover-assets.mjs
 *
 * API (consumed by ProverWebViewBridge.kt — keep in sync):
 *   window.RaizProver.generate(kind, inputsJson) -> Promise<result>
 *     kind: "register" | "transfer" | "withdraw"
 *           ("deposit" / "merge" are rejected BY DESIGN: they are the two
 *            proof-free CT operations — Kotlin submits those txs directly)
 *     inputsJson: JSON string (or object). Field/scalar values are decimal or
 *       0x-hex STRINGS (JSON has no bigint); Grumpkin points are {x, y} of the
 *       same. Per kind:
 *         register: { sk, token }
 *         transfer: { sk, token, v, r, amount, pvkB:{x,y}, kAudR:{x,y},
 *                     kAudS:{x,y}, sigma?, rE? }
 *         withdraw: { sk, token, v, r, amount, kAudS:{x,y}, sigma?, rE? }
 *       where token is the 56-char C… strkey of the CT wrapper (keys are
 *       domain-bound to it) and v/r open the current spendable commitment.
 *     result: { kind, proofBase64, proofLength, publicInputs: string[],
 *               payload, next?, witnessMs, proveMs, ms, threads }
 *       payload/next mirror the vendor witness builders' outputs (bigints as
 *       0x-hex, points as {x,y} hex) — everything Kotlin needs to build the
 *       Soroban invocation and roll its local balance state forward.
 *   window.RaizProver.selftest() -> Promise<timing>
 *     Proves register with fresh synthetic inputs (the recipe of
 *     scripts/prover-bench/src/witness-inputs.mjs). Returns timing + env,
 *     no proof bytes.
 *
 *   window.RaizChain.<fn>(inputsJson) -> Promise<result>   (Session 5 step 5)
 *     Secret-free Soroban invocation builders: build → simulate → assemble an
 *     invocation of OUR CT wrapper and return the UNSIGNED envelope XDR.
 *     Kotlin keeps key custody, signs the envelope hash and submits/polls over
 *     plain JSON-RPC (see wallet/StellarAccount.kt + SorobanRpc.kt). Mirrors
 *     vendor chain/client.ts invoke() minus sign/send, and chain/contract.ts
 *     argument shapes verbatim. Functions: prepareRegister, prepareDeposit,
 *     prepareMerge, prepareTransfer, status — parameter shapes documented at
 *     each function below.
 *
 * SECRETS POLICY (project decision, non-negotiable): inputs — including sk —
 * arrive per call and leave scope when the call resolves. Nothing here writes
 * to localStorage/IndexedDB/cookies. The only caches are public, non-secret
 * artifacts: circuit JSON, wasm modules and warm UltraHonk backends (bytecode
 * only). bb.js may cache the public Aztec CRS in IndexedDB — public data.
 *
 * THREADS: Android WebView never exposes SharedArrayBuffer/crossOriginIsolated
 * (friction-report 2026-08-03 12:00), so on-device this always computes 1. We
 * still pass `threads` EXPLICITLY to UltraHonkBackend because bb.js 0.87
 * defaults to threads:1 even when isolation IS available (friction-report
 * 2026-08-03 00:30) — this same bundle proves multithreaded in a PWA context.
 */

import initACVM from "@noir-lang/acvm_js";
import initAbi from "@noir-lang/noirc_abi";
import { Noir } from "@noir-lang/noir_js";

// @stellar/stellar-sdk resolves to its self-contained BROWSER bundle
// (package.json "browser": dist/stellar-sdk.min.js) under esbuild
// --platform=browser. The vendor dist modules below import the same specifier,
// so they share this single instance (XDR objects cross safely). The global
// `Buffer` those modules expect is provided by esbuild --inject
// (wallet/tools/buffer-inject.js).
import {
  TransactionBuilder,
  Contract,
  Address,
  BASE_FEE,
  nativeToScVal,
  xdr,
  rpc,
} from "@stellar/stellar-sdk";

// Vendor CT SDK (read-only, bundled as-is; paths relative to this file).
import { deriveKeys } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/keys.js";
import { addressToField } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/address.js";
import { randomScalar, toHex32, toBytes32BE } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/field.js";
import { pointFromBytes, pointCoords } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/crypto/grumpkin.js";
import { buildRegisterWitness } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/register.js";
import { buildTransferWitness } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/transfer.js";
import { buildWithdrawWitness } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/witness/withdraw.js";
import { ChainClient } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/chain/client.js";
import { encodeRegisterData, encodeTransferData } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/chain/payload.js";
import { StateEngine } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/state/engine.js";
import { MemoryStore } from "../../../../../../vendor/stellar-confidential-token-demo/packages/sdk/dist/state/store.js";

// ---------------------------------------------------------------------------
// Asset locations — resolved against this bundle's URL (…/prover/dist/), so
// the same bundle works on https://appassets.androidplatform.net and on any
// static server (the documented localhost:4173 interim fallback).
// ---------------------------------------------------------------------------
const BASE = new URL("..", import.meta.url); // …/prover/
const assetUrl = (rel) => new URL(rel, BASE).href;

// On-chain verifier uses a keccak256 transcript (vendor dist/proving/prover.js:
// a Poseidon-transcript proof "silently fails on-chain").
const KECCAK = { keccak: true };

// Mirrors bb.js fetchModuleAndThreads: shared memory needs SAB + isolation,
// then threads = min(hardware, 32); otherwise 1. In Android WebView this is
// always 1 (SAB never exposed) — passed explicitly anyway, see header.
const THREADS =
  globalThis.crossOriginIsolated === true && typeof SharedArrayBuffer !== "undefined"
    ? Math.min(navigator.hardwareConcurrency || 1, 32)
    : 1;

/** Testnet CT wrapper strkey (demo deployment.ts) — selftest key domain only;
 *  any well-formed 56-char strkey costs the same to prove against. */
const TOKEN_STRKEY = "CBF64DEOVQAXJFBSNGFEUT2AH4H7K5JBY3ZYJ5GVEINMNSDISWRG5N3F";

// ---------------------------------------------------------------------------
// Lazy singletons (same shape as scripts/prover-bench/src/main.js)
// ---------------------------------------------------------------------------
let noirWasmReady;
function ensureNoirWasm() {
  // Explicit wasm paths served from our assets; the default would point inside
  // the bundle where no wasm lives. Idempotent.
  noirWasmReady ??= Promise.all([
    initACVM({ module_or_path: assetUrl("wasm/acvm_js_bg.wasm") }),
    initAbi({ module_or_path: assetUrl("wasm/noirc_abi_wasm_bg.wasm") }),
  ]);
  return noirWasmReady;
}

let bbModulePromise;
function loadBb() {
  // `new Function` hides the import() from esbuild: bb.js MUST stay unbundled
  // (it spawns its wasm worker via `new URL("./main.worker.js",
  // import.meta.url)` — bundling breaks that sibling lookup; prover-bench
  // finding). Loaded as native ESM from our assets at runtime.
  bbModulePromise ??= new Function("u", "return import(u)")(assetUrl("vendor/bb/index.js"));
  return bbModulePromise;
}

const circuits = new Map(); // kind -> Promise<circuit json>
function loadCircuit(kind) {
  if (!circuits.has(kind)) {
    circuits.set(
      kind,
      fetch(assetUrl(`circuits/${kind}.json`)).then((r) => {
        if (!r.ok) throw new Error(`fetch circuits/${kind}.json -> HTTP ${r.status}`);
        return r.json();
      }),
    );
  }
  return circuits.get(kind);
}

const backends = new Map(); // kind -> backend (kept warm; caches bytecode only)
async function getBackend(kind, bytecode) {
  if (!backends.has(kind)) {
    const bb = await loadBb();
    backends.set(kind, new bb.UltraHonkBackend(bytecode, { threads: THREADS }));
  }
  return backends.get(kind);
}

// ---------------------------------------------------------------------------
// JSON <-> crypto-object codecs
// ---------------------------------------------------------------------------
function asBig(v, name) {
  if (typeof v === "bigint") return v;
  if (typeof v === "number" && Number.isSafeInteger(v) && v >= 0) return BigInt(v);
  if (typeof v === "string" && v.trim().length > 0) {
    try {
      return BigInt(v.trim()); // accepts both decimal and 0x-hex
    } catch {
      throw new Error(`input '${name}' is not a valid decimal/0x-hex integer: ${v}`);
    }
  }
  throw new Error(`input '${name}' must be a decimal or 0x-hex string (got ${v === undefined ? "nothing" : typeof v})`);
}

function asPoint(o, name) {
  if (!o || typeof o !== "object" || o.x == null || o.y == null) {
    throw new Error(`input '${name}' must be a point {x, y} with hex/decimal string coords`);
  }
  const bytes = new Uint8Array(64); // on-chain layout be(x) || be(y)
  bytes.set(toBytes32BE(asBig(o.x, `${name}.x`)), 0);
  bytes.set(toBytes32BE(asBig(o.y, `${name}.y`)), 32);
  return pointFromBytes(bytes); // validates the point is on Grumpkin
}

function asStrkey(v, name) {
  if (typeof v !== "string" || v.length !== 56) {
    throw new Error(`input '${name}' must be the token's 56-char C… strkey`);
  }
  return v;
}

/** bigints -> 0x-hex, Grumpkin points -> {x, y} hex, recursively. */
function serialize(v) {
  if (typeof v === "bigint") return toHex32(v);
  if (v && typeof v.toAffine === "function") {
    const { x, y } = pointCoords(v);
    return { x: toHex32(x), y: toHex32(y) };
  }
  if (Array.isArray(v)) return v.map(serialize);
  if (v && typeof v === "object") {
    return Object.fromEntries(Object.entries(v).map(([k, x]) => [k, serialize(x)]));
  }
  return v;
}

function toBase64(bytes) {
  let s = "";
  const CHUNK = 0x8000;
  for (let i = 0; i < bytes.length; i += CHUNK) {
    s += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
  }
  return btoa(s);
}

// ---------------------------------------------------------------------------
// Witness assembly per kind (vendor builders, unmodified)
// ---------------------------------------------------------------------------
const PROOF_FREE = new Set(["deposit", "merge"]);

function buildWitness(kind, p) {
  const keys = deriveKeys(asBig(p.sk, "sk"), addressToField(asStrkey(p.token, "token")));
  const opt = {};
  if (p.sigma != null) opt.sigma = asBig(p.sigma, "sigma");
  if (p.rE != null) opt.rE = asBig(p.rE, "rE");
  switch (kind) {
    case "register":
      return buildRegisterWitness(keys);
    case "withdraw":
      return buildWithdrawWitness({
        keys,
        v: asBig(p.v, "v"),
        r: asBig(p.r, "r"),
        amount: asBig(p.amount, "amount"),
        kAudS: asPoint(p.kAudS, "kAudS"),
        ...opt,
      });
    case "transfer":
      return buildTransferWitness({
        keys,
        v: asBig(p.v, "v"),
        r: asBig(p.r, "r"),
        amount: asBig(p.amount, "amount"),
        pvkB: asPoint(p.pvkB, "pvkB"),
        kAudR: asPoint(p.kAudR, "kAudR"),
        kAudS: asPoint(p.kAudS, "kAudS"),
        ...opt,
      });
    default:
      throw new Error(`unknown proof kind '${kind}' — expected register|transfer|withdraw`);
  }
}

/**
 * Execute the Noir circuit on `inputs` and produce an UltraHonk proof with the
 * keccak transcript (the only transcript the on-chain verifier accepts).
 * Shared by RaizProver.generate and the RaizChain.prepare* builders. First call
 * per kind also pays wasm init + circuit fetch (witnessMs) and backend init +
 * CRS download (proveMs) — exactly as before the Session-5 step-5 refactor.
 */
async function execAndProve(kind, inputs) {
  const tW0 = performance.now();
  await ensureNoirWasm();
  const circuit = await loadCircuit(kind);
  const noir = new Noir(circuit);
  const { witness } = await noir.execute(inputs);
  const witnessMs = Math.round(performance.now() - tW0);

  const backend = await getBackend(kind, circuit.bytecode);
  const tP0 = performance.now();
  const { proof, publicInputs } = await backend.generateProof(witness, KECCAK);
  const proveMs = Math.round(performance.now() - tP0);
  return { proof, publicInputs, witnessMs, proveMs };
}

// ---------------------------------------------------------------------------
// The two entry points
// ---------------------------------------------------------------------------
async function generate(kind, inputsJson) {
  const t0 = performance.now();
  if (PROOF_FREE.has(kind)) {
    throw new Error(
      `'${kind}' needs no ZK proof: deposit and merge are the two proof-free CT ` +
        `operations — build and submit that transaction directly from Kotlin ` +
        `(only register, transfer and withdraw go through the prover).`,
    );
  }
  const p = typeof inputsJson === "string" ? JSON.parse(inputsJson) : (inputsJson ?? {});
  const { inputs, payload, next } = buildWitness(kind, p);
  const { proof, publicInputs, witnessMs, proveMs } = await execAndProve(kind, inputs);

  return {
    kind,
    proofBase64: toBase64(proof),
    proofLength: proof.length,
    publicInputs, // hex strings; debugging aid — the contract rebuilds them
    payload: serialize(payload),
    ...(next ? { next: serialize(next) } : {}),
    witnessMs,
    proveMs,
    ms: Math.round(performance.now() - t0),
    threads: THREADS,
  };
}

/** Prove register with synthetic inputs; timing + env only, no proof bytes. */
async function selftest() {
  const sk = toHex32(randomScalar());
  const { proofLength, publicInputs, witnessMs, proveMs, ms, threads } = await generate(
    "register",
    { sk, token: TOKEN_STRKEY },
  );
  return {
    ok: true,
    circuit: "register",
    witnessMs,
    proveMs,
    ms,
    threads,
    proofLength,
    publicInputCount: publicInputs.length,
    crossOriginIsolated: globalThis.crossOriginIsolated === true,
    sharedArrayBuffer: typeof SharedArrayBuffer !== "undefined",
    hardwareConcurrency: navigator.hardwareConcurrency || 1,
  };
}

// ---------------------------------------------------------------------------
// window.RaizChain — secret-free Soroban invocation builders (Session 5 §5)
//
// Division of labor (project decision, non-negotiable): this side does
// everything that needs the vendor SDK's XDR/simulation plumbing but holds NO
// lasting secrets — build → simulate → assemble — and returns the UNSIGNED
// envelope XDR (base64). Kotlin keeps key custody, signs the envelope hash and
// submits + polls over plain JSON-RPC. `sk` (needed for witnesses/decryption)
// passes through per call and leaves scope on resolve, same policy as
// RaizProver.generate.
//
// buildUnsignedInvoke mirrors vendor chain/client.ts invoke() lines 148-167
// (build → simulate → assemble) minus sign/send; argument shapes mirror
// chain/contract.ts submitters verbatim.
//
// Common params (every function): rpcUrl, token, verifier, auditor (contract
// strkeys), optional passphrase (defaults to testnet).
// ---------------------------------------------------------------------------

const DEFAULT_PASSPHRASE = "Test SDF Network ; September 2015";
const dec = (v) => v.toString(); // bigint -> decimal string (JSON-safe)

function parseParams(json) {
  return typeof json === "string" ? JSON.parse(json) : (json ?? {});
}

function reqStr(p, name) {
  const v = p[name];
  if (typeof v !== "string" || v.length === 0) {
    throw new Error(`param '${name}' (string) is required`);
  }
  return v;
}

function chainClient(p) {
  return new ChainClient({
    rpcUrl: reqStr(p, "rpcUrl"),
    networkPassphrase: typeof p.passphrase === "string" ? p.passphrase : DEFAULT_PASSPHRASE,
    contracts: {
      token: asStrkey(reqStr(p, "token"), "token"),
      verifier: reqStr(p, "verifier"),
      auditor: reqStr(p, "auditor"),
    },
  });
}

const addrScVal = (a) => new Address(a).toScVal();

/** build → simulate → assemble; returns the unsigned envelope for Kotlin. */
async function buildUnsignedInvoke(client, source, method, args) {
  const acct = await client.server.getAccount(source);
  const tx = new TransactionBuilder(acct, {
    fee: BASE_FEE,
    networkPassphrase: client.cfg.networkPassphrase,
  })
    .addOperation(new Contract(client.cfg.contracts.token).call(method, ...args))
    .setTimeout(180) // proving happened BEFORE this; 180 s covers sign+submit
    .build();

  const sim = await client.server.simulateTransaction(tx);
  if (rpc.Api.isSimulationError(sim)) {
    throw new Error(`simulate ${method} failed: ${sim.error}`);
  }
  const assembled = rpc.assembleTransaction(tx, sim).build();
  return {
    method,
    source,
    unsignedXdrBase64: assembled.toXDR(),
    feeStroops: String(assembled.fee), // post-assembly: base fee + resource fee
    latestLedger: sim.latestLedger,
  };
}

/** Per-call state sync — MemoryStore only: the WebView persists nothing. */
async function syncState(client, p, address) {
  const keys = deriveKeys(asBig(p.sk, "sk"), addressToField(asStrkey(p.token, "token")));
  const engine = new StateEngine({
    client,
    store: new MemoryStore(),
    keys,
    address,
    fromLedger: Number(p.fromLedger ?? 0),
  });
  return { keys, state: await engine.sync() };
}

/**
 * prepareRegister({ ...common, account, sk, auditorId?, fromLedger? })
 * Proves the register circuit, encodes RegisterData, builds the tx.
 * Short-circuits with { alreadyRegistered: true } — pressing the button twice
 * must not burn 10 s of proving to earn a simulation error.
 */
async function prepareRegister(json) {
  const p = parseParams(json);
  const client = chainClient(p);
  const account = reqStr(p, "account");
  if (await client.isRegistered(account)) {
    return { method: "register", account, alreadyRegistered: true };
  }
  const w = buildWitness("register", { sk: p.sk, token: p.token });
  const { proof, witnessMs, proveMs } = await execAndProve("register", w.inputs);
  const auditorId = Number(p.auditorId ?? 0);
  const args = [addrScVal(account), xdr.ScVal.scvU32(auditorId), encodeRegisterData(w, proof)];
  const tx = await buildUnsignedInvoke(client, account, "register", args);
  return { ...tx, auditorId, witnessMs, proveMs, proofLength: proof.length };
}

/** prepareDeposit({ ...common, from, to, amountStroops }) — proof-free. */
async function prepareDeposit(json) {
  const p = parseParams(json);
  const client = chainClient(p);
  const from = reqStr(p, "from");
  const to = reqStr(p, "to");
  const amount = asBig(p.amountStroops, "amountStroops");
  if (amount <= 0n) throw new Error(`amountStroops must be positive (got ${dec(amount)})`);
  const args = [addrScVal(from), addrScVal(to), nativeToScVal(amount, { type: "i128" })];
  const tx = await buildUnsignedInvoke(client, from, "deposit", args);
  return { ...tx, amountStroops: dec(amount) };
}

/** prepareMerge({ ...common, account }) — proof-free ("cosechar"). */
async function prepareMerge(json) {
  const p = parseParams(json);
  const client = chainClient(p);
  const account = reqStr(p, "account");
  return buildUnsignedInvoke(client, account, "merge", [addrScVal(account)]);
}

/**
 * prepareTransfer({ ...common, from, to, amountStroops, sk, fromLedger })
 * Syncs the sender's confidential state (event replay + ECDH, in memory),
 * reads the recipient's PVK + both auditor keys from chain, proves the
 * transfer circuit, encodes TransferData, builds the tx.
 */
async function prepareTransfer(json) {
  const p = parseParams(json);
  const client = chainClient(p);
  const from = reqStr(p, "from");
  const to = reqStr(p, "to");
  const amount = asBig(p.amountStroops, "amountStroops");
  if (amount <= 0n) throw new Error(`amountStroops must be positive (got ${dec(amount)})`);

  const { keys, state } = await syncState(client, p, from);
  if (state.spendable.v < amount) {
    throw new Error(
      `insufficient confidential spendable balance: have ${dec(state.spendable.v)} stroops, ` +
        `need ${dec(amount)} (deposit first, or merge pending receiving balance)`,
    );
  }
  const fromAcct = await client.confidentialBalance(from);
  const toAcct = await client.confidentialBalance(to);
  if (!fromAcct) throw new Error(`sender ${from} is not registered on the wrapper`);
  if (!toAcct) throw new Error(`recipient ${to} is not registered on the wrapper`);
  const kAudS = await client.auditorKey(fromAcct.auditorId);
  const kAudR = await client.auditorKey(toAcct.auditorId);

  const w = buildTransferWitness({
    keys,
    v: state.spendable.v,
    r: state.spendable.r,
    amount,
    pvkB: toAcct.viewingPublicKey,
    kAudR,
    kAudS,
  });
  const { proof, witnessMs, proveMs } = await execAndProve("transfer", w.inputs);
  const args = [addrScVal(from), addrScVal(to), encodeTransferData(w, proof)];
  const tx = await buildUnsignedInvoke(client, from, "confidential_transfer", args);
  return {
    ...tx,
    amountStroops: dec(amount),
    spendableBeforeStroops: dec(state.spendable.v),
    recipientAuditorId: toAcct.auditorId,
    witnessMs,
    proveMs,
    proofLength: proof.length,
  };
}

/**
 * status({ ...common, account, sk?, fromLedger? })
 * registered flag + (when sk is given and registered) locally decrypted
 * balances via event replay. Read-only; nothing to sign.
 */
async function status(json) {
  const p = parseParams(json);
  const client = chainClient(p);
  const account = reqStr(p, "account");
  const registered = await client.isRegistered(account);
  const out = { account, registered, latestLedger: await client.latestLedger() };
  if (registered && p.sk != null) {
    const { state } = await syncState(client, p, account);
    out.spendableStroops = dec(state.spendable.v);
    out.receivingStroops = dec(state.receiving.v);
    out.syncedLedger = state.syncedLedger;
  }
  return out;
}

// ---------------------------------------------------------------------------
// Install + tell Kotlin we are alive
// ---------------------------------------------------------------------------
globalThis.RaizProver = { generate, selftest };
globalThis.RaizChain = { prepareRegister, prepareDeposit, prepareMerge, prepareTransfer, status };

console.log(
  `[RaizProver] ready — threads=${THREADS} crossOriginIsolated=${globalThis.crossOriginIsolated === true} ` +
    `SAB=${typeof SharedArrayBuffer !== "undefined"} cores=${navigator.hardwareConcurrency || 1}`,
);

// Only present when loaded by ProverWebViewBridge; harmless in a plain browser.
try {
  globalThis.AndroidBridge?.onProverReady?.();
} catch (e) {
  console.warn(`[RaizProver] onProverReady callback failed: ${e}`);
}
