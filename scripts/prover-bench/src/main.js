/**
 * Browser proving benchmark for the CT spike. No Freighter, no chain, no keys
 * worth stealing: fresh random synthetic witnesses, prove, time, done.
 *
 * Pipeline per tap (same two steps the vendor SDK's CircuitProver runs):
 *   1. noir_js  Noir.execute(inputs)                  -> witness ms
 *   2. bb.js    UltraHonkBackend.generateProof(
 *                 witness, { keccak: true })          -> prove ms
 *
 * bb.js is NOT bundled. It is loaded at runtime as native ESM from
 * /vendor/bb/index.js (served straight out of the vendor package's
 * dest/browser/), because bb.js spawns its wasm Web Worker via
 * `new Worker(new URL("./main.worker.js", import.meta.url))` — once bundled
 * into a hashed chunk that sibling file no longer exists and proving hangs.
 * Same approach as the demo app's bb-loader, re-written here from scratch.
 *
 * Unlike the vendor SDK (which constructs UltraHonkBackend with no options and
 * therefore inherits bb.js's `{ threads: 1 }` default), we pass an explicit
 * thread count so a cross-origin-isolated phone actually proves multithreaded.
 */

import initACVM from "@noir-lang/acvm_js";
import initAbi from "@noir-lang/noirc_abi";
import { Noir } from "@noir-lang/noir_js";

import { INPUT_BUILDERS } from "./witness-inputs.mjs";

const KECCAK = { keccak: true }; // on-chain verifier uses a keccak256 transcript

const $ = (id) => document.getElementById(id);

// ---------------------------------------------------------------------------
// Environment banner
// ---------------------------------------------------------------------------
const isolated = globalThis.crossOriginIsolated === true;
const sabAvailable = typeof SharedArrayBuffer !== "undefined";
const hardware = navigator.hardwareConcurrency || 1;
// Mirrors bb.js fetchModuleAndThreads: shared memory needs SAB + isolation,
// then threads = min(desired, hardwareConcurrency, 32); otherwise 1.
const THREADS = isolated && sabAvailable ? Math.min(hardware, 32) : 1;

function renderEnv() {
  $("env").innerHTML = [
    `crossOriginIsolated: <b>${isolated}</b>`,
    `SharedArrayBuffer: <b>${sabAvailable}</b>`,
    `hardwareConcurrency: <b>${hardware}</b>`,
    `threads bb.js will use: <b>${THREADS}</b>`,
    `<span class="ua">${navigator.userAgent}</span>`,
  ].join("<br>");
  if (!isolated) {
    $("env").innerHTML +=
      `<br><span class="warn">Not cross-origin isolated: proving falls back to 1 thread.` +
      ` For multithreading open via http://localhost:4173 (adb reverse).</span>`;
  }
}

function log(msg, isError = false) {
  const line = document.createElement("div");
  line.textContent = msg;
  if (isError) line.className = "err";
  $("log").prepend(line);
}

// ---------------------------------------------------------------------------
// Lazy singletons: noir wasm init, bb.js module, artifacts, backends
// ---------------------------------------------------------------------------
let noirWasmReady;
function ensureNoirWasm() {
  // Explicit wasm paths (served by serve.mjs from the vendor packages) instead
  // of the default `new URL(..., import.meta.url)`, which would point inside
  // /dist/ where no wasm lives. Init is idempotent; noir_js's own internal
  // no-arg init() calls become no-ops after this.
  noirWasmReady ??= Promise.all([
    initACVM({ module_or_path: "/wasm/acvm_js_bg.wasm" }),
    initAbi({ module_or_path: "/wasm/noirc_abi_wasm_bg.wasm" }),
  ]);
  return noirWasmReady;
}

let bbModulePromise;
function loadBb() {
  // `new Function` keeps the import() invisible to esbuild so it is neither
  // bundled nor rewritten; the URL resolves at runtime against this origin.
  bbModulePromise ??= new Function("u", "return import(u)")("/vendor/bb/index.js");
  return bbModulePromise;
}

const artifacts = new Map(); // name -> Promise<circuit json>
function loadArtifact(name) {
  if (!artifacts.has(name)) {
    artifacts.set(
      name,
      fetch(`/circuits/${name}.json`).then((r) => {
        if (!r.ok) throw new Error(`fetch /circuits/${name}.json -> HTTP ${r.status}`);
        return r.json();
      }),
    );
  }
  return artifacts.get(name);
}

const backends = new Map(); // name -> backend (kept warm across runs)
async function getBackend(name, bytecode) {
  if (!backends.has(name)) {
    const bb = await loadBb();
    backends.set(name, new bb.UltraHonkBackend(bytecode, { threads: THREADS }));
  }
  return backends.get(name);
}

// ---------------------------------------------------------------------------
// Bench run
// ---------------------------------------------------------------------------
let runCounter = 0;

function addRow(cells, isError = false) {
  const tr = document.createElement("tr");
  if (isError) tr.className = "err";
  for (const c of cells) {
    const td = document.createElement("td");
    td.textContent = String(c);
    tr.appendChild(td);
  }
  $("results").prepend(tr);
}

function setBusy(busy, label) {
  for (const btn of document.querySelectorAll("button[data-circuit]")) {
    btn.disabled = busy;
  }
  $("status").textContent = busy ? label : "idle";
}

async function runBench(name) {
  const run = ++runCounter;
  setBusy(true, `run #${run}: proving ${name}... (first run also loads wasm + CRS)`);
  try {
    const tTotal0 = performance.now();

    // Phase 1: witness (inputs construction + wasm init on first run + solve).
    const tW0 = performance.now();
    await ensureNoirWasm();
    const circuit = await loadArtifact(name);
    const inputs = INPUT_BUILDERS[name]();
    const noir = new Noir(circuit);
    const { witness } = await noir.execute(inputs);
    const witnessMs = Math.round(performance.now() - tW0);

    // Phase 2: proof (backend init + CRS download included on first run).
    const backend = await getBackend(name, circuit.bytecode);
    const tP0 = performance.now();
    const { proof } = await backend.generateProof(witness, KECCAK);
    const proveMs = Math.round(performance.now() - tP0);

    const totalMs = Math.round(performance.now() - tTotal0);
    addRow([run, name, witnessMs, proveMs, totalMs, `${proof.length} B`]);
    log(`#${run} ${name}: witness ${witnessMs} ms, prove ${proveMs} ms, total ${totalMs} ms`);
  } catch (e) {
    const msg = String((e && (e.stack || e.message)) || e);
    addRow([run, name, "-", "-", "-", "ERROR"], true);
    log(`#${run} ${name} FAILED: ${msg}`, true);
  } finally {
    setBusy(false);
  }
}

// ---------------------------------------------------------------------------
// Wire-up
// ---------------------------------------------------------------------------
renderEnv();
for (const btn of document.querySelectorAll("button[data-circuit]")) {
  btn.addEventListener("click", () => runBench(btn.dataset.circuit));
}
window.addEventListener("error", (e) => log(`window.onerror: ${e.message}`, true));
window.addEventListener("unhandledrejection", (e) =>
  log(`unhandledrejection: ${String(e.reason)}`, true),
);
log("ready — tap a button to prove");
