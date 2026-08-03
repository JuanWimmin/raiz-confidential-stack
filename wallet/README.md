# wallet/ — Session 5: self-contained proving bridge

Android app (Kotlin) that generates Confidential Token ZK proofs ON-DEVICE with
**no dev server**: the CT proving stack (vendor SDK witness builders + noir_js +
bb.js, consumed as-is from `/vendor`) is packaged into APK assets and served to
a **headless WebView** over `https://appassets.androidplatform.net`
(WebViewAssetLoader). Kotlin talks to it through `ProverWebViewBridge`
(`app/src/main/java/xyz/raiz/sobre/prover/`) — suspend API, 90 s timeout, typed
errors. Key custody / signing / submission stay in Kotlin (Session 5 step 5,
pending ct-flow.md). **Still not the final wallet UI** — `MainActivity` is a
debug screen with one button: "Self-test register proof".

Measured on the Vivo Y21 (2026-08-03, threads=1 — Android WebView never exposes
SharedArrayBuffer): register proof **10.7 s cold** (incl. wasm init + CRS
download) / **7.3 s warm** through the bridge. Evidence:
`docs/spike-evidence/bridge-selftest.png`.

## Build & run

1. Generate the proving assets (copies bb.js/wasm/circuits from `/vendor` and
   esbuild-bundles our shim; output is gitignored, ~13 MB):

   ```powershell
   node C:\SP_WorkShop\wallet\tools\build-prover-assets.mjs
   ```

   Prereq: `/vendor/stellar-confidential-token-demo` has node_modules installed
   (Session 0 did `npx -y pnpm@10.33.0 install` there).

2. Build the APK (the PATH Java 25 cannot run Gradle 8.10.2 — use Android
   Studio's JBR):

   ```powershell
   cd C:\SP_WorkShop\wallet
   $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
   .\gradlew.bat :app:assembleDebug
   ```

3. Install on a device, launch, tap **Self-test register proof**. Timings +
   the JS result JSON appear on screen; the page console is mirrored there too
   (this Vivo suppresses app logcat — the screen is the reliable viewport;
   elsewhere use `adb logcat -s SobreSpike`).

The JS surface (`window.RaizProver.generate/selftest`, input encoding per
operation, secrets policy) is documented in
`app/src/main/assets/prover/raiz-shim.js`. `deposit`/`merge` are rejected by
design: they are the two proof-free CT operations.

Interim fallback if the asset loader ever misbehaves: serve
`scripts/prover-bench` (`node serve.mjs`, `adb reverse tcp:4173 tcp:4173`) and
call `bridge.initialize("http://localhost:4173")` — same bundle recipe.

## Versions (pinned to what is cached on this machine, 2026-08-02)

Gradle 8.10.2 (wrapper) · AGP 8.7.3 · Kotlin 2.1.21 · compileSdk 35 · minSdk 26
· androidx.webkit 1.12.1 · kotlinx-coroutines 1.10.2.
