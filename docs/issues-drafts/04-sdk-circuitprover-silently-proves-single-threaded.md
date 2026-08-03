# CircuitProver silently proves single-threaded: bb.js UltraHonkBackend defaults to { threads: 1 } and the SDK never overrides it

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Version/commit: ac67499; @aztec/bb.js 0.87.0; @noir-lang/noir_js 1.0.0-beta.9

## Repro steps

1. Read `packages/sdk/src/proving/prover.ts:74`: it constructs
   `new Backend(this.#bytecode)` with NO backendOptions.
2. In `@aztec/bb.js@0.87.0` the constructor is
   `constructor(acirBytecode, backendOptions = { threads: 1 }, ...)`
   (`dest/node/barretenberg/backend.js:25`, identical in
   `dest/browser/index.js`), and `Barretenberg.new({threads: 1})` feeds
   `fetchModuleAndThreads(1, ...)` → `Math.min(1, availableThreads, 32)` = 1.
   The multithread-friendly default (`desiredThreads = 32`) applies only when
   `options.threads` is `undefined` — i.e. when the caller passes `{}` — never
   when the second argument is omitted entirely.
3. Time any CircuitProver proof with and without an explicit
   `{ threads: N }` backend.

## Expected

The app's whole COOP/COEP + vendored-worker setup exists to enable
SharedArrayBuffer multithreading, so CircuitProver proofs should use
`min(hardwareConcurrency, 32)` threads when `crossOriginIsolated`.

## Observed

No error — a silent performance trap. Every CircuitProver proof (browser demo
AND the SDK's own `test/prove.mjs`) runs on exactly 1 wasm thread regardless
of isolation. Measured (Node v25.8.1, 22 CPUs, warm CRS):

| circuit | CircuitProver (1 thread) | identical witness, `{ threads: 22 }` |
|---|---|---|
| register | 2735 ms | 1413 ms |
| withdraw | 5079 ms | 1741 ms |
| transfer | 5179 ms | 1816 ms |

A ~2-3x free speedup left on the table.

## Suggested fix

In `prover.ts`, pass `{}` (or an explicit
`{ threads: typeof navigator !== "undefined" && crossOriginIsolated ? Math.min(navigator.hardwareConcurrency, 32) : undefined }`)
as the second constructor argument. A workaround that needs no vendor change:
the exported `setUltraHonkBackendLoader` hook can supply a subclass that
forwards an explicit thread count (this is what we do).
