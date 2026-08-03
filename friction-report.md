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

## [2026-08-03 12:00] — [CT] — Android WebView never becomes crossOriginIsolated: CT proving in a WebView is single-threaded by platform, not by configuration
- Context: day-0 spike, measuring proof generation inside the spike harness WebView (wallet/app, Vivo V2110 / Android 13 / WebView Chromium 150.0.7871.181). Page = scripts/prover-bench served at http://localhost:4173 via `adb reverse` with `Cross-Origin-Opener-Policy: same-origin` + `Cross-Origin-Embedder-Policy: credentialless` on every response.
- Verbatim error: (none — silent capability gap) page banner inside the WebView reports `crossOriginIsolated: false · SharedArrayBuffer: false · threads bb.js will use: 1`, while the IDENTICAL URL in Chrome 150 on the same device reports `crossOriginIsolated: true · SharedArrayBuffer: true · threads bb.js will use: 8`.
- Version/commit: Android System WebView 150.0.7871.181; @aztec/bb.js 0.87.0; prover-bench @ this repo.
- Expected: localhost is a secure context and both COOP/COEP headers are present, so crossOriginIsolated should be true (as it is in Chrome). Observed: Android WebView does not enable SharedArrayBuffer/crossOriginIsolated at all — the demo docs' cross-origin-isolation recipe cannot work inside a WebView regardless of headers.
- Workaround: none needed for viability — bb.js falls back to 1 thread gracefully and all three circuits still prove in 10.8-15.7 s on a 4 GB mid-range phone (vs 2.7-6.7 s multithreaded in Chrome). The wallet's UX must budget ~10-16 s per proof; the PWA path stays documented as the 2.6x-faster alternative. Relevant to OZ docs: worth stating that WebView-embedded dApps will always prove single-threaded.

## [2026-08-03 · Session 4] — [CT] — No per-account view keys in the preview; auditor registry is admin-gated, so third parties cannot mint a view key on the official deployment
- Context: Session 4 needed a "goal view key" — a key that decrypts the goal account's inflows so anyone can verify the fund total (the Sobre del Barrio public-view-key pattern). The CT preview's only view-key mechanism is the auditor registry: a `u32 auditor_id` → Grumpkin key table on the auditor contract; each account commits to ONE id at `register()` and the token fetches both parties' keys on every transfer (vendor/stellar-contracts .../confidential/storage.rs:686-687). There is no per-account key export, and `register_key` is operator/role-gated. Probe: invoked `register_key(77, <fresh Grumpkin point>, <our G-address>)` on the OFFICIAL demo auditor contract CA4II62E35TQKPGHCPBD6EBAS732GSGS6H37UUWKEDHR4YTBVMPHVY4L with a friendbot-funded account.
- Verbatim error: `simulate register_key failed: HostError: Error(Contract, #2000)` with diagnostic `["failing with contract error", 2000]` — #2000 = `AccessControlError::Unauthorized` (stellar-contracts packages/access/src/access_control/mod.rs:384).
- Version/commit: stellar-contracts @ 9b5ed96 (branch feat/confidential-verifier-ultrahonk), demo @ ac67499, RPC protocol 27.
- Expected: some path for a user/dapp to obtain a scoped view key for their own account. Observed: view keys exist only at deployment level, minted by the auditor-contract admin; on the official deployment that admin is the demo team.
- Workaround: deployed OUR OWN CT stack (scripts/ct-flow/deploy.mjs) where we are the auditor admin, and registered auditor id 1 as the goal's dedicated view key (id 0 = custodian for contributors). Marta registers under id 0, the goal under id 1: the published id-1 secret then opens ONLY the recipient channel of transfers to the goal (demonstrated on-chain: `auditTransfer(k1).channelsAgree === false`, sender channel unreadable). Design note for OZ: the recipient channel necessarily reveals each contribution's AMOUNT (+ r_tx) to the view-key holder, not just the running total — "publish the goal's view key" means per-contribution amounts become decryptable by anyone holding it. Our README must say this plainly; propuesta A §10's periodic-disclosure fallback was NOT needed (the auditor-id pattern works today), but the granularity caveat stands.

## [2026-08-03 · Session 4] — [stellar-cli] — vendor deploy.ts passes `--optimize=false`, which stellar CLI 23.2.1 rejects (version drift in the demo repo)
- Context: mirroring vendor/stellar-confidential-token-demo/scripts/deploy.ts (line 47: `"--optimize=false"` in the `contract deploy` argv) for our own deployment with the locally installed CLI.
- Verbatim error: `error: unexpected argument '--optimize' found` / `tip: to pass '--optimize' as a value, use '-- --optimize'`
- Version/commit: stellar 23.2.1 (496ac35be7a7d8d923fcde9bbbc650ee593d1f6f); demo @ ac67499.
- Expected: the demo's documented deploy command to work with a current CLI. Observed: the flag no longer exists in 23.2.1, so scripts/deploy.ts fails at the first deploy on a current install.
- Workaround: our scripts/ct-flow/deploy.mjs simply omits the flag; everything else deploys cleanly (verifier, auditor, token, 6 VKs, 2 auditor keys — all first-try on testnet).

## [2026-08-03 15:10] — [stellar-cli] — `keys generate --no-fund` removed in CLI 23.x; flag still lives in older docs/muscle memory
- Context: creating a fresh deployer identity for the goal_meta deployment without auto-funding: `stellar keys generate --no-fund goal-meta-deployer`.
- Verbatim error: `error: unexpected argument '--no-fund' found` / `tip: to pass '--no-fund' as a value, use '-- --no-fund'` / `Usage: stellar.exe keys generate [OPTIONS] <NAME>` (then, because the key was never created: `❌ error: Failed to find config identity for goal-meta-deployer`)
- Version/commit: stellar 23.2.1 (496ac35be7a7d8d923fcde9bbbc650ee593d1f6f)
- Expected: `--no-fund` as documented in pre-23 guides. Observed: in 23.2.1 the default flipped — generate does NOT fund unless `--fund` is passed, and `--no-fund` is gone.
- Workaround: plain `stellar keys generate <name>` + explicit friendbot curl (with retries).

## [2026-08-03 15:12] — [stellar-cli] — LedgerKey JSON for config settings needs a nested struct, not the enum-arm string the error message implies
- Context: fetching the REAL testnet state-archival TTL config before choosing extend_ttl values for goal_meta (project gotcha #2): `stellar xdr encode --type LedgerKey '{"config_setting":"state_archival"}'`.
- Verbatim error: `❌ error: error reading file: error decoding JSON: invalid type: string "state_archival", expected struct LedgerKeyConfigSetting at line 1 column 34`
- Version/commit: stellar 23.2.1 / stellar-xdr 23.0.0
- Expected: enum arms elsewhere in the CLI's XDR JSON accept plain strings, so the flat form seems natural. Observed: LedgerKey::ConfigSetting wraps a struct — the working shape is `{"config_setting":{"config_setting_id":"state_archival"}}` (also: "error reading file" is misleading for inline-argument input).
- Workaround: nested form works; decoded testnet values (ledger 3950042): min_persistent_ttl=120960, max_entry_ttl=3110400.

## [2026-08-03 15:30] — [RPC] — first submission of a fresh contract's invoke failed with InsufficientRefundableFee; identical retry succeeded
- Context: scripts/goal-flow.sh, first-ever `record_harvest` on the just-deployed goal_meta (CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ): `stellar contract invoke --id C... --source-account S... --network testnet -- record_harvest --id 0 --memo "primera cosecha (goal-flow 2026-08-03T15:29:48Z)"`. The function extends TTL on 2 persistent entries + instance (rent bump), then emits one event.
- Verbatim error: `❌ error: transaction submission failed: Some( TransactionResult { fee_charged: 5090, result: TxFailed( VecM( [ OpInner( InvokeHostFunction( InsufficientRefundableFee, ), ), ], ), ), ext: V0, } )`
- Version/commit: stellar 23.2.1 against soroban-testnet.stellar.org (protocol 27)
- Expected: the CLI's own simulation sizes the resource/refundable fee, so submission should not underpay rent. Observed: the first submission underestimated the refundable fee (three TTL bumps to ~30 days in one call); an identical re-invoke ~5 s later simulated fresh and succeeded.
- Workaround: the 3-attempt growing-backoff retry loop (project rule 3) absorbed it — attempt 2 landed. Scripts that bump TTLs should never fire-and-forget a single submission.

## [2026-08-03 15:35] — [other] — Node 25 console.log() colorizes numbers even when piped, breaking shell arithmetic on captured output
- Context: scripts/goal-flow.sh captures `getLatestLedger` via `curl ... | node -e '...console.log(r.result.sequence)'` and computes `START=$((LATEST - 120))`.
- Verbatim error: `scripts/goal-flow.sh: line 129: \e[33m3950220\e[39m: syntax error: operand expected (error token is "[33m3950220[39m")`
- Version/commit: Node.js v25.8.1 (Windows, Git Bash pipe)
- Expected: console.log of a number into a pipe emits plain text (pre-25 behavior). Observed: ANSI color escapes wrap the number even with stdout not a TTY.
- Workaround: `export NODE_DISABLE_COLORS=1` in the script + `console.log(String(...))` + `tr -cd '0-9'` sanitize. Any repo script that parses node output through a shell needs the same guard.

## [2026-08-03 12:47] — [other] — Headless (never-attached) WebView: View.post() queues forever, so evaluateJavascript never runs — proofs "time out" while JS sits idle
- Context: Session 5 bridge (wallet/app/src/main/java/xyz/raiz/sobre/prover/ProverWebViewBridge.kt): a headless WebView (never attached to a window, by design — the prover is invisible) serving the CT proving bundle from APK assets via WebViewAssetLoader. Kotlin dispatched `window.RaizProver.generate(...)` with `webView.post { webView.evaluateJavascript(...) }`, the pattern from our own agreed design sketch (wallet/docs-integration/ProverWebViewBridge.kt).
- Verbatim error: none — that is the trap. On-device symptom: `prover [selftest] produced no result within 90000 ms` (our typed Timeout) twice in a row, while page load and the JS ready callback had worked instantly. No JS console output for the call, ever.
- Version/commit: Android WebView on Vivo Y21 (Android 12, 720x1600); androidx.webkit 1.12.1; app @ Session 5 work tree.
- Expected: View.post on a WebView runs the runnable on the main thread. Observed: on a view that was NEVER attached to a window, View.post enqueues into the view's attach-time RunQueue — which only drains on attach — so the runnable waits forever and evaluateJavascript is never called. Page loading (loadUrl direct on main thread) and JS→Kotlin @JavascriptInterface callbacks are unaffected, which makes the half-alive bridge extra confusing. Compounded by: this Vivo suppresses app logcat wholesale (no SobreSpike lines ever reach `adb logcat`), so the "dispatched" log that would have exposed the hang was invisible — we added an on-screen console mirror to see anything at all.
- Workaround: post through a plain `Handler(Looper.getMainLooper())` instead of `View.post` for EVERY main-thread hop of a headless WebView (fixed in ProverWebViewBridge.kt; the docs-integration sketch still shows the buggy pattern — kept as history, superseded by the real implementation). After the fix, the full CT proving stack ran from APK assets first try: register 10.7 s cold / 7.3 s warm on the Vivo Y21, threads=1.
