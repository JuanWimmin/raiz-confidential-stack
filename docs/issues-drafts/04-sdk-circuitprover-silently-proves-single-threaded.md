# CircuitProver proves single-threaded: `UltraHonkBackend` is constructed without `backendOptions`, and bb.js defaults that argument to `{ threads: 1 }`

- Status: DRAFT — do not open online before human review
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Companion issue (optional, different maintainer): the surprising default itself belongs to AztecProtocol/barretenberg — see "Upstream note" below
- Verified: 2026-08-04 against demo @ ac67499 with `@aztec/bb.js` 0.87.0 and `@noir-lang/noir_js` 1.0.0-beta.9 as installed by the repo's own lockfile

## The claim, in two lines of code

`packages/sdk/src/proving/prover.ts:73-74`

```ts
const Backend = await loadUltraHonkBackend();
this.#backend = new Backend(this.#bytecode);   // no second argument
```

`node_modules/@aztec/bb.js/dest/node/barretenberg/backend.js:25` (same in the
browser bundle)

```js
constructor(acirBytecode, backendOptions = { threads: 1 }, circuitOptions = { recursive: false }) {
```

The default is a *value*, not `undefined`, so omitting the argument pins the
backend to one thread. `Barretenberg.new({ threads: 1 })` passes that straight
into `fetchModuleAndThreads(1, ...)`, and
`barretenberg_wasm/index.js:8` computes
`Math.min(desiredThreads, availableThreads, 32)` = 1.

The multi-threaded default lives in that same function's signature —
`fetchModuleAndThreads(desiredThreads = 32, ...)` — and only applies when
`options.threads` is `undefined`, i.e. when the caller passes `{}`. Passing
nothing and passing `{}` therefore mean opposite things.

## Repro steps

```
git clone https://github.com/brozorec/stellar-confidential-token-demo
cd stellar-confidential-token-demo && npx -y pnpm@10.33.0 install
npx -y pnpm@10.33.0 --filter @ct/sdk test   # or run packages/sdk/test/prove.mjs directly
```

Then time any `CircuitProver.prove()` as-is, and again with a backend
constructed as `new Backend(bytecode, { threads: N })`.

## Expected

The app ships a COOP/COEP + vendored-worker setup whose only purpose is to
enable SharedArrayBuffer multithreading, so proofs should use
`min(hardwareConcurrency, 32)` threads when `crossOriginIsolated` is true.

## Observed

No error and no warning — a silent performance trap. Every `CircuitProver`
proof (the browser demo and the SDK's own `test/prove.mjs`) runs on exactly
one wasm thread regardless of isolation. Measured on Node v25.8.1, 22 CPUs,
warm CRS:

| circuit | as shipped (1 thread) | identical witness, `{ threads: 22 }` |
|---|---|---|
| register | 2735 ms | 1413 ms |
| withdraw | 5079 ms | 1741 ms |
| transfer | 5179 ms | 1816 ms |

A 2-3x speedup is available for a one-argument change.

## Suggested fix

In `prover.ts`, pass an explicit options object rather than omitting it:

```ts
this.#backend = new Backend(this.#bytecode, {});                    // take bb.js's 32-thread default
// or, to be explicit about the isolation requirement:
this.#backend = new Backend(this.#bytecode, {
  threads: typeof navigator !== "undefined" && globalThis.crossOriginIsolated
    ? Math.min(navigator.hardwareConcurrency, 32)
    : 1,
});
```

Consumers who cannot patch the SDK can work around it today through the
exported `setUltraHonkBackendLoader` hook (`prover.ts:45`), supplying a
subclass that forwards a thread count — that is what we ended up doing.

## Upstream note

The root cause is arguably the bb.js API: a defaulted-to-`{ threads: 1 }`
parameter whose "no opinion" value is `{}` rather than omission is easy to get
wrong, and this repo is not the only caller that will. Worth a separate issue
on AztecProtocol/barretenberg (the live bb.js tracker) asking for the default
to be `{}`/`undefined`, or for a warning when a single thread is selected
while `crossOriginIsolated` is true. Adjacent existing issues there and in
AztecProtocol/aztec-packages — #19992 (HARDWARE_CONCURRENCY hardcoded to 1 in
`native_socket.js`) and #12786 (refactor of the backend classes, which changes
this very constructor) — cover neighbouring ground but not this default.
