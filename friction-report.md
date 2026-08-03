# Friction Report — Confidential Tokens & SPP developer previews

> Every friction with CT / SPP / RPC tooling gets logged here with the VERBATIM
> error message, version, and repro. On day 3 these become issue drafts in
> /docs/issues-drafts/ for the OpenZeppelin / Nethermind repos (after human
> review). Documented friction is a deliverable of this submission, not noise.

Entry format:

```
## [YYYY-MM-DD HH:mm] — [component: CT | SPP | RPC | stellar-cli | other] — short title
- Context: what we were trying to do (exact command/operation)
- Verbatim error: (full message, copied, not paraphrased)
- Version/commit: tool version or /vendor repo commit
- Expected vs. observed: one line each
- Workaround: what we did to keep moving (or "none yet")
```

---

## [2026-08-02 12:40] — [RPC] — getEvents rejects the WHOLE call when one contractId in a filter is invalid (placeholder id in CT demo test files)
- Context: validating raiz-memory's getEvents assumptions against the real testnet RPC. Batched 5 candidate contract ids from /vendor/stellar-confidential-token-demo into one `getEvents` filter, including `CCREDIB3DG3IBVUKBL7QMEK4MTPSTODR7MQ34QY4SQ5LZ5L4WFWNVNXG` (found in packages/sdk/test/{payload,smoke,parity,prove}.mjs). POST https://soroban-testnet.stellar.org with `{"method":"getEvents","params":{"startLedger":3820000,"filters":[{"type":"contract","contractIds":[...5 ids...]}],"pagination":{"limit":3}}}`.
- Verbatim error: `{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"filter 1 invalid: contract ID 5 invalid"}}`
- Version/commit: soroban-testnet.stellar.org (protocolVersion 27, latestLedger 3940652); stellar-confidential-token-demo @ ac67499
- Expected: either per-id validation feedback naming the bad id, or the valid ids still being queried. Observed: the whole request is rejected with a 1-based index ("contract ID 5") and no id string, so you must bisect to find the culprit.
- Workaround: dropped the offending id (it is a checksum-invalid placeholder that only appears in the demo's test scripts, not a deployed contract) and re-sent with the 4 real ids. Note for the demo repo: shipping a non-decodable contract id in test files is a small trap for anyone harvesting ids to index.

## [2026-08-02 23:22] — [other] — CT demo requires pnpm 10, but Node 25 no longer bundles corepack; one build script silently skipped on install
- Context: first local install of /vendor/stellar-confidential-token-demo (prerequisite "Node >= 20, pnpm 10", README.md:129; lockfile is pnpm-lock.yaml, package.json pins `"packageManager": "pnpm@10.33.0"`). Host has Node v25.8.1 with neither `pnpm` nor `corepack` on PATH (corepack was removed from the default Node distribution in Node 25), so the documented `pnpm install` fails out of the box on a fresh machine.
- Verbatim error: (no error after workaround; the install itself ended with) `╭ Warning ─────────────────────────────────────────────────────────────────────╮ │ Ignored build scripts: msgpackr-extract@3.0.4. │ Run "pnpm approve-builds" to pick which dependencies should be allowed │ to run scripts. │ ╰──────────────────────────────────────────────────────────────────────────────╯ Done in 3m 58.1s using pnpm v10.33.0`
- Version/commit: Node v25.8.1, npm 11.11.0, pnpm 10.33.0 (via npx); stellar-confidential-token-demo @ ac67499
- Expected: `pnpm install` works as documented on a supported Node. Observed: pnpm unavailable without extra setup on Node 25; after installing via npx, pnpm 10's secure-by-default policy skips msgpackr-extract's build script (benign here: optional native accel for the Cloudflare/Goldsky tooling, unused in local dev — `sharp`, `esbuild`, `workerd` are already allowlisted in root package.json `pnpm.onlyBuiltDependencies`).
- Workaround: run every pnpm command as `npx -y pnpm@10.33.0 <cmd>` (pinned to the packageManager version). Install + `build:sdk` + `vendor:bb` then complete cleanly; msgpackr-extract left unapproved on purpose.

## [2026-08-03 00:30] — [CT] — SDK CircuitProver silently proves single-threaded: bb.js UltraHonkBackend defaults to { threads: 1 } and the SDK never overrides it
- Context: building scripts/prover-bench (standalone proving benchmark for the Session 1 spike). Read the vendor prover to replicate its exact call sequence: `packages/sdk/src/proving/prover.ts:74` constructs `new Backend(this.#bytecode)` with NO backendOptions. In `@aztec/bb.js@0.87.0` the constructor is `constructor(acirBytecode, backendOptions = { threads: 1 }, ...)` (dest/node/barretenberg/backend.js:25 and identically in dest/browser/index.js), and `Barretenberg.new({threads: 1})` feeds `fetchModuleAndThreads(1, ...)` -> `Math.min(1, availableThreads, 32)` = 1 thread. The multithread-friendly default (`desiredThreads = 32`) only applies when `options.threads` is undefined, i.e. when the caller passes `{}` — never when the second argument is omitted entirely.
- Verbatim error: (none — silent performance trap; proving succeeds on 1 thread)
- Version/commit: stellar-confidential-token-demo @ ac67499; @aztec/bb.js 0.87.0; @noir-lang/noir_js 1.0.0-beta.9
- Expected: the demo app's whole COOP/COEP + vendored-worker setup exists to enable SharedArrayBuffer multithreading, so CircuitProver proofs should use min(hardwareConcurrency, 32) threads when crossOriginIsolated. Observed: every CircuitProver proof (browser demo AND the SDK's own test/prove.mjs) runs on exactly 1 wasm thread regardless of isolation. Measured on this machine (Node v25.8.1, 22 CPUs, warm CRS): register 2735 ms / withdraw 5079 ms / transfer 5179 ms via CircuitProver, vs 1413 / 1741 / 1816 ms for the identical witness with an explicit `{ threads: 22 }` backend — a ~2-3x free speedup the demo leaves on the table.
- Workaround: prover-bench constructs its own `UltraHonkBackend(bytecode, { threads: crossOriginIsolated ? Math.min(navigator.hardwareConcurrency, 32) : 1 })` instead of going through CircuitProver in the browser. Our wallet's proving layer must do the same (or pass `{}`). Issue-draft candidate for OpenZeppelin's demo repo.
