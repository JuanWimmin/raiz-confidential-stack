# Session 1 / Step 1 — CT demo study & LAN-serve preparation

Target repo: `C:\SP_WorkShop\vendor\stellar-confidential-token-demo` @ commit `ac67499` (read-only).
Every claim below carries file:line evidence from that clone (or from the installed
`@aztec/bb.js@0.87.0` under its `node_modules`). Status: **dependencies installed, SDK built,
bb.js vendored — dev server NOT started** (left ready on purpose).

---

## (a) How and where proofs are generated

### The proving module

- Package: **`@ctd/sdk`**, module **`packages/sdk/src/proving/prover.ts`**.
  Class `CircuitProver` wraps one Noir circuit: `noir_js` solves the witness, then bb.js's
  `UltraHonkBackend.generateProof(witness, { keccak: true })` produces the proof —
  `prover.ts:80-85`. The keccak transcript is **mandatory** (the on-chain verifier uses
  keccak256 Fiat-Shamir; a default Poseidon-transcript proof "silently fails on-chain") —
  `prover.ts:4-7,29`.
- Backend init is expensive (WASM + CRS load) and cached per circuit — `prover.ts:57-77`;
  the app additionally caches one `CircuitProver` per circuit name — `packages/app/lib/wallet.ts:171-178`.
- Circuit artifacts are **committed JSON** (compiled with nargo 1.0.0-beta.9) at
  `packages/sdk/circuits/{register,transfer,withdraw}.json` — `packages/sdk/src/proving/artifacts.ts:4-7`;
  the browser imports them through the bundler — `packages/app/lib/wallet.ts:50-52`.

### Entry functions per operation (app orchestration, `packages/app/lib/wallet.ts`)

| Op | Proof? | Entry function | Witness builder | Submitter |
|----|--------|----------------|-----------------|-----------|
| register | YES | `ConfidentialWallet.register()` — `wallet.ts:185-194` (prove at :189) | `buildRegisterWitness` — `packages/sdk/src/witness/register.ts:18` | `submitRegister` — `packages/sdk/src/chain/contract.ts:22-36` |
| deposit | **no proof** | `deposit()` — `wallet.ts:196-200` | — | `submitDeposit` — `contract.ts:39-52` ("public → confidential, no proof", `contract.ts:38`) |
| merge | **no proof** | `merge()` — `wallet.ts:202-206` | — | `submitMerge` — `contract.ts:55-61` |
| transfer | YES | `transfer()` — `wallet.ts:208-236` (prove at :228) | `buildTransferWitness` — `packages/sdk/src/witness/transfer.ts:76` | `submitTransfer` — `contract.ts:82-96` (`confidential_transfer`) |
| withdraw | YES | `withdraw()` — `wallet.ts:238-252` (prove at :246) | `buildWithdrawWitness` — `packages/sdk/src/witness/withdraw.ts:51` | `submitWithdraw` — `contract.ts:64-79` |

Root `README.md:18-24` confirms the proof column (only register / withdraw / confidential_transfer prove).

### Web Workers, SharedArrayBuffer, multithreading

- **Yes, bb.js proving runs in a Web Worker**: it spawns
  `new Worker(new URL("./main.worker.js", import.meta.url), ...)` — installed
  `@aztec/bb.js@0.87.0` `dest/browser/index.js:4245`; per-thread workers from
  `./thread.worker.js` — `index.js:4258`. The vendored copy in the app
  (`packages/app/public/vendor/bb/`) contains `main.worker.js`, `thread.worker.js`,
  `barretenberg.js`, `barretenberg-threads.js` (verified after running `vendor:bb`).
- **Multithreaded WASM is on when cross-origin isolated**:
  `getSharedMemoryAvailable()` = `typeof SharedArrayBuffer !== "undefined" && globalScope.crossOriginIsolated`
  — `dest/browser/index.js:4209-4212`. Thread count:
  `fetchModuleAndThreads(desiredThreads = 32, ...)` takes
  `min(32, navigator.hardwareConcurrency)` when shared memory is available,
  **else falls back to 1 thread** (and fetches the non-threaded wasm) — `index.js:8704-8710`.
- **Consequence for our degradation tree**: losing COOP/COEP does NOT hard-fail proving —
  bb.js degrades to single-threaded WASM inside the worker. Measure both configurations in
  the spike; "no crossOriginIsolated" means slow, not broken (the app README's
  "needs `crossOriginIsolated === true`" at `packages/app/README.md:34` is about
  multithreaded performance, per the bb.js code above).
- **bb.js must never be webpack-bundled**: webpack aliases `@aztec/bb.js` to `false` on the
  client — `packages/app/next.config.mjs:34-36`; `scripts/vendor-bb.mjs` copies bb.js's
  `dest/browser/` verbatim into `public/vendor/bb/` (run automatically by `predev`/`prebuild`,
  `packages/app/package.json:8,10`); the app loads it as **native ESM** from `/vendor/bb/index.js`
  via `ensureBrowserBackend()` → SDK `setUltraHonkBackendLoader` —
  `packages/app/lib/bb-loader.ts:32-44`, hook defined at `packages/sdk/src/proving/prover.ts:45-47`,
  called once at wallet connect — `wallet.ts:103`. Rationale: bb.js resolves its worker relative
  to `import.meta.url` with `webpackIgnore`, so a hashed `_next` chunk breaks it and proving
  hangs forever — `scripts/vendor-bb.mjs:4-12`, `packages/app/README.md:42-48`.
- **COOP/COEP**: `next.config.mjs:10-21` sets `Cross-Origin-Opener-Policy: same-origin` +
  `Cross-Origin-Embedder-Policy: credentialless` on **every** route (`source: "/(.*)"`).
  `credentialless` (not `require-corp`) is deliberate: the Soroban RPC fetch passes without
  CORP headers on the RPC endpoint — `next.config.mjs:5-8`. On Cloudflare, static assets bypass
  next headers, so `packages/app/public/_headers` re-adds COOP/COEP/CORP for `/vendor/bb/*`;
  its comment confirms "**`next dev` applies COEP to every path**", so no extra work in dev.

## (b) Proving IS separable from signing — the seam

**Yes.** Proving and signing never touch:

1. The SDK's signer abstraction is two members:
   `interface Signer { publicKey: string; sign(txXdrBase64: string): Promise<string> }` —
   `packages/sdk/src/chain/client.ts:33-38`.
2. Proof generation completes **before** any signer involvement: each wallet op does
   `build*Witness → prover.prove(inputs) → submit*(client, signer, …, witness, proof)`
   (e.g. transfer: `wallet.ts:217-231`). The submitters take the **finished proof bytes** as a
   plain argument — `contract.ts:22-96`.
3. The single signing call site is inside `ChainClient.invoke` (build → simulate →
   `rpc.assembleTransaction` → **`signer.sign(assembled.toXDR())`** → send → poll) —
   `client.ts:148-187`, sign at `client.ts:169`.
4. Freighter appears ONLY in the app layer, as an adapter to that interface:
   `connectFreighter()` — `packages/app/lib/freighter.ts:25-56`; and Node scripts prove any
   signer works via `keypairSigner` (raw `Keypair`) — `client.ts:68-78`.

**Kotlin plan**: implement `Signer` in the WebView bridge — `sign(txXdrBase64)` posts the
unsigned assembled XDR to Kotlin (Keystore signs, returns signed base64 XDR). The Stellar
ed25519 key never enters the WebView. Even lower-level cut if we want tx building in Kotlin
too: the WebView only produces `{witness payload, proof}` (the ScVal encoders are
`encodeRegisterData/encodeWithdrawData/encodeTransferData` — `packages/sdk/src/chain/payload.ts`,
used at `contract.ts:33,76,93`) and Kotlin assembles/signs/submits.

**One caveat**: the confidential Grumpkin `sk` IS a witness input (`packages/sdk/src/witness/register.ts:20`),
so the proving context must hold `sk` (not the Stellar key). The demo derives
`sk = SHA-512(Freighter signMessage) mod r`, deployment-bound — `packages/app/lib/derive-key.ts:14-32`,
used at `wallet.ts:120-127`, cached in localStorage. Our wallet can derive/store `sk` in Kotlin
(EncryptedSharedPreferences) and inject it per proving session; Freighter's `signMessage` is
only the demo's way to make `sk` deterministic, not a protocol requirement.

## (c) Running locally — what it needs (all DONE except starting the server)

- **Package manager: pnpm 10, not npm** — `pnpm-lock.yaml` + `pnpm-workspace.yaml` at repo
  root; `package.json:6` pins `"packageManager": "pnpm@10.33.0"`; prerequisites "Node ≥ 20,
  pnpm 10" — root `README.md:129`. No yarn/npm lockfiles exist.
- **This machine**: Node v25.8.1, no `pnpm`, no `corepack` (Node 25 dropped it). Everything runs
  as **`npx -y pnpm@10.33.0 <cmd>`**. Logged in `friction-report.md` (2026-08-02 23:22 entry).
- **Completed, verified**:
  1. `npx -y pnpm@10.33.0 install` — 462 packages, done in 3m58s. Only warning: ignored build
     script `msgpackr-extract@3.0.4` (optional native accel for the Cloudflare tooling; benign
     for local dev — root allowlist `package.json:22-28` already approves esbuild/sharp/workerd).
  2. `npx -y pnpm@10.33.0 build:sdk` — clean `tsc` to `packages/sdk/dist` (required before dev:
     `packages/app/README.md:14`, root `README.md:49-50`).
  3. `npx -y pnpm@10.33.0 --filter @ctd/app vendor:bb` — bb.js browser build copied to
     `packages/app/public/vendor/bb/` (8 files incl. both workers). `predev` re-runs this
     automatically anyway (`packages/app/package.json:8`).
- **Dev server command**: root `pnpm dev` → `pnpm --filter @ctd/app dev` (root
  `package.json:13`) → **`next dev --webpack`** (`packages/app/package.json:9`; the `--webpack`
  flag is load-bearing — the bb.js handling is webpack-specific, `packages/app/README.md:38`).
  Serves at `http://localhost:3000`.
- **Env vars**: NONE required. The only variable is optional `NEXT_PUBLIC_INDEXER_URL`
  (Goldsky backfill beyond the RPC's ~7-day window) — `packages/app/.env.example:1-4`, read at
  `packages/app/lib/deployment.ts:66`. RPC URL, network passphrase, and all contract ids are
  hardcoded in `DEFAULT_DEPLOYMENT` — `deployment.ts:68-87` (`rpcUrl:
  "https://soroban-testnet.stellar.org"` at :72). Unset ⇒ RPC-only mode.
- **COOP/COEP in dev: already set** — `next.config.mjs:19-21` applies the headers to `/(.*)`;
  `public/_headers` comment confirms `next dev` applies COEP to every path. Nothing to add.

## (d) Default testnet contract ids

Source of truth in the app: `DEFAULT_DEPLOYMENT` — `packages/app/lib/deployment.ts:68-87`
(matches root `README.md:120-125`). Note: `README.md:118` mentions `deployments/testnet.json`
but that file is **absent from the clone** — `deployment.ts` is what the app actually uses.

| Contract | ID | Evidence |
|----------|----|----------|
| **token (CT wrapper — the id Session 2's indexer wants)** | `CBF64DEOVQAXJFBSNGFEUT2AH4H7K5JBY3ZYJ5GVEINMNSDISWRG5N3F` | `deployment.ts:79` |
| verifier (UltraHonk VK registry) | `CDCET36PIS44DWJM5UQSSI4ZHGRDSBIIQW4G4ALPYK3Y6FEQGY5ZWFXL` | `deployment.ts:80` |
| auditor (Grumpkin key registry) | `CA4II62E35TQKPGHCPBD6EBAS732GSGS6H37UUWKEDHR4YTBVMPHVY4L` | `deployment.ts:81` |
| underlying (native XLM SAC) | `CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC` | `deployment.ts:82` |
| factory (advanced mode) | `CDX4DBNWDMD7BVZCOJPTXVTBRXU2RG7JUOZKOOUX5RVWWWWIGV2LWS6Z` | `deployment.ts:85` |

Indexing parameters: token deployed at ledger **3013364** (`deployment.ts:75`); every account
registers under `auditorId: 0` (`deployment.ts:76`); the auditor's Grumpkin SECRET is published
on purpose for the demo persona (`deployment.ts:77`, warning at :14-17) — exactly the
"view key published on purpose" pattern our goal-meta flips into a feature.

## (e) Funding test accounts (friendbot) — human steps on the phone

The app itself has **no friendbot integration** (zero matches for friendbot/fund under
`packages/app/`); funding is external, per root `README.md:42`. The repo's scripts show the
exact endpoint: `GET https://friendbot.stellar.org/?addr=<G-address>` —
`scripts/_shared.ts:16,80-84`.

Human flow (demo as-is):

1. Install the Freighter wallet extension and create/import an account.
   **Phone reality check**: stock Chrome for Android cannot install extensions, so the demo's
   Freighter dependency does not work on a phone browser as-is. For the spike, either use a
   desktop browser, an extension-capable Android browser, or — the real plan — our Kotlin
   signer replacing Freighter at the `Signer` seam from (b). (This is a demo-app limitation,
   not a proving limitation: proving has no Freighter dependency.)
2. In Freighter: Settings → switch network to **Testnet**.
3. Fund the G-address with test XLM, any of:
   - Freighter's built-in Friendbot button (`README.md:42`);
   - Stellar Lab → Fund account: https://lab.stellar.org/account/fund (`README.md:42`);
   - direct: open `https://friendbot.stellar.org/?addr=G...` (idempotent-ish: errors if already
     funded — `scripts/_shared.ts:79-84`).
4. Open the app → persona chooser → `/wallet` → **Connect Freighter** → approve access →
   sign the key-derivation message (one-time per account+deployment, `wallet.ts:120-127`) →
   **Register** (first proof, in-browser) → Deposit → Merge → Transfer → Withdraw.

## How to serve on LAN (ready to run — NOT started)

From PowerShell on this machine (Wi-Fi LAN IP today: `192.168.0.104`; re-check with
`ipconfig` — the `192.168.56.1` adapter is a VirtualBox-style host-only NIC, not the LAN):

```powershell
cd C:\SP_WorkShop\vendor\stellar-confidential-token-demo
# already done, listed for completeness:
npx -y pnpm@10.33.0 install
npx -y pnpm@10.33.0 build:sdk

# start the dev server (next dev binds 0.0.0.0 BY DEFAULT — verified via `next dev --help`:
# "-H, --hostname <hostname> ... (default: 0.0.0.0)"):
npx -y pnpm@10.33.0 dev

# fully explicit equivalent:
npx -y pnpm@10.33.0 --filter @ctd/app exec next dev --webpack -H 0.0.0.0 -p 3000
```

Phone URL: `http://192.168.0.104:3000`. First bind will trigger a **Windows Firewall** prompt
for Node — allow on Private networks (or pre-open TCP 3000 for the Private profile).

### CRITICAL caveat for the phone: secure context vs. crossOriginIsolated

`crossOriginIsolated` requires a **secure context**. `http://localhost:3000` qualifies;
`http://192.168.0.104:3000` from the phone does NOT — the COOP/COEP headers are sent but the
browser still exposes no `SharedArrayBuffer`, so bb.js silently drops to **1 thread**
(`dest/browser/index.js:8704-8706`). Proving should still complete, just slower — measure it,
don't assume failure. Three ways to get full multithreading on the phone:

1. **`adb reverse` (cleanest for the spike, no cert, no firewall):**
   ```powershell
   adb reverse tcp:3000 tcp:3000
   ```
   Phone opens `http://localhost:3000` — a genuine secure context; COOP/COEP from `next dev`
   do the rest. Requires USB debugging; also works for the WebView test later.
2. **HTTPS dev server** (flag verified in installed next 16.2.9 `next dev --help`):
   ```powershell
   npx -y pnpm@10.33.0 --filter @ctd/app exec next dev --webpack -H 0.0.0.0 -p 3000 --experimental-https
   ```
   Generates a self-signed cert; the phone must click through the certificate warning at
   `https://192.168.0.104:3000`.
3. **Chrome flag on the phone**: `chrome://flags/#unsafely-treat-insecure-origin-as-secure`
   → add `http://192.168.0.104:3000` → relaunch. Treats the origin as secure; combined with the
   served COOP/COEP headers, `crossOriginIsolated` becomes true. (Chrome only; useless for the
   WebView path.)

Sanity check on the phone before timing proofs: open DevTools/remote-debug console and confirm
`window.crossOriginIsolated === true` (the threads-vs-1-thread fork above hinges on it).

### Verify without a phone (optional)

`curl -sI http://localhost:3000 | findstr /i "cross-origin"` after starting the server must show
both `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: credentialless`.
