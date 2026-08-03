# prover-bench — standalone CT proving benchmark (no Freighter, no chain)

Session 1 spike tool: measures Confidential Token **proof-generation time only**,
on this machine (Node baseline) and on a phone (browser page). The official demo
needs the Freighter extension to derive keys and sign — impossible on stock
Chrome for Android. Proving is separable from signing (see
`docs/spike-findings.md` §b), so this bench builds **synthetic witnesses**
(fresh random keys, toy amounts — the same recipe as the vendor SDK's own
headless `packages/sdk/test/prove.mjs`) and times the two real steps:

1. `noir_js` `Noir.execute(inputs)` — witness solving
2. `bb.js` `UltraHonkBackend.generateProof(witness, { keccak: true })` — proof

All vendor code (`/vendor/stellar-confidential-token-demo`) is **imported or
served as-is, never copied or modified**.

## Prerequisites

- The vendor demo must be installed and its SDK built (already done in
  Session 1 / Step 1): `npx -y pnpm@10.33.0 install` + `build:sdk` inside
  `vendor/stellar-confidential-token-demo`.
- Network on the first proof: bb.js downloads the CRS from
  `https://crs.aztec.network` (Node caches it in `~/.bb-crs`; the browser
  caches per origin).

## 1. Node baseline

```powershell
cd C:\SP_WorkShop\scripts\prover-bench
node bench-node.mjs
```

Measured on this machine (Node v25.8.1, 22 CPUs, warm CRS cache, 2026-08-03):

| circuit  | witness ms | prove ms (22 threads, split path) | prove ms (SDK path, 1 thread) | proof size |
|----------|-----------:|----------------------------------:|------------------------------:|-----------:|
| register |         70 |                               1413 |                          2735 |    14592 B |
| withdraw |         11 |                               1741 |                          5079 |    14592 B |
| transfer |         13 |                               1816 |                          5179 |    14592 B |

First-ever run (cold CRS download) added roughly 2–8 s to the first proofs.
All proofs locally verified (`verifyProof(..., { keccak: true })` → true).

**Why two prove columns:** the vendor SDK's `CircuitProver` constructs
`new UltraHonkBackend(bytecode)` with no options (`prover.ts:74`), and bb.js
0.87.0 defaults that to `{ threads: 1 }` (`dest/*/barretenberg/backend.js`).
So the SDK/demo path is single-threaded everywhere — even cross-origin
isolated in a browser. The split path passes an explicit thread count, which
is what our wallet should do too. (Logged in `friction-report.md`.)

## 2. Build the browser bundle

```powershell
node build.mjs
```

Bundles `src/main.js` → `dist/bench.js` (ESM, ~231 kB) with `npx esbuild`.
noir_js + the SDK's crypto/witness modules are bundled; the two noir wasm
blobs are fetched at runtime; **bb.js is never bundled** — it is loaded as
native ESM from `/vendor/bb/index.js` so its Web Workers
(`main.worker.js` / `thread.worker.js`) resolve next to it.

## 3. Serve

```powershell
node serve.mjs
```

Binds `0.0.0.0:4173`. Every response carries
`Cross-Origin-Opener-Policy: same-origin` and
`Cross-Origin-Embedder-Policy: credentialless` (mirrors the demo's
`next.config.mjs`; `credentialless` lets bb.js fetch the CRS cross-origin).
Serves this directory plus, read-only from the vendor tree: bb.js
`dest/browser/`, the acvm/noirc_abi web wasm, and the circuit JSONs.

## 4. Phone

`crossOriginIsolated` needs a **secure context**, not just the headers:

- **Multithreaded mode** (the real measurement):
  ```powershell
  adb reverse tcp:4173 tcp:4173
  ```
  then open **http://localhost:4173** in Chrome for Android
  (localhost is a secure context; requires USB debugging).
- **1-thread mode** (degradation data point): open
  **http://<LAN-IP>:4173** (find the IP with `ipconfig`; allow Node through
  the Windows firewall for Private networks on first bind). The banner will
  show `crossOriginIsolated: false` — expected, still worth recording.

The page shows an environment banner (crossOriginIsolated,
hardwareConcurrency, thread count bb.js will use, userAgent), one button per
circuit, and a results table (run #, circuit, witness ms, prove ms, total ms).
Nothing runs automatically — a human taps. Buttons stay disabled while a run
is in flight. **Run #1 per circuit includes wasm init + CRS download; tap
again for steady-state numbers.** Errors are printed verbatim in the log area
(copy them into `friction-report.md`).

Desktop sanity check: open http://localhost:4173 in any desktop browser —
same page, should be cross-origin isolated. (Not yet exercised in a live
browser at the time of writing; Node validates the identical witness inputs
and proving calls, and all asset routes were verified with curl.)

## Files

- `bench-node.mjs` — Node baseline (split path + unmodified SDK path, verifies proofs)
- `src/witness-inputs.mjs` — synthetic witness inputs shared by both benches
- `src/main.js` — browser entry (bundled)
- `index.html` — the page (big text, phone-friendly)
- `build.mjs` — esbuild driver (resolves `@noir-lang/*` from the vendor workspace via `NODE_PATH`)
- `serve.mjs` — dependency-free static server, port 4173, COOP/COEP on every response
- `dist/bench.js` — build output (committed builds not required; rebuild with `node build.mjs`)
