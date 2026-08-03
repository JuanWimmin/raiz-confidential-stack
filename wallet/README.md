# wallet/ — Session 1 spike harness

Minimal Android app (Kotlin, one Activity, full-screen WebView) that loads the
OpenZeppelin Confidential Token demo from a local dev server, to measure
in-WebView proving times for the day-0 GO/NO-GO decision. **Not the final
wallet** — no passkeys, no bridge (bridge design lives in
`docs-integration/ProverWebViewBridge.kt`, implementation is Session 5).

## Run it

1. Start the CT demo dev server on the PC (see `docs/spike-findings.md`):

   ```powershell
   cd C:\SP_WorkShop\vendor\stellar-confidential-token-demo
   npx -y pnpm@10.33.0 dev
   ```

   First bind: allow Node through the Windows Firewall (Private networks).

2. Open `wallet/` in Android Studio (it uses its bundled JDK; the PATH Java 25
   cannot run Gradle 8.10.2 — for CLI builds set
   `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`).

3. Pick the URL in `app/build.gradle.kts` → `DEMO_URL`:
   - **Recommended:** keep `http://localhost:3000` and run
     `adb reverse tcp:3000 tcp:3000` (phone plugged in, USB debugging on).
     localhost is a secure context → with the demo's COOP/COEP headers,
     `crossOriginIsolated === true` → multithreaded proving.
   - **No-USB fallback:** set it to `http://<PC-LAN-IP>:3000`. Loads fine, but
     it is not a secure context → bb.js silently falls back to 1 WASM thread.
     Time it as the degraded configuration.

4. Run the `app` configuration on a physical device. Watch timings and the
   `crossOriginIsolated` value with `adb logcat -s SobreSpike` (page console is
   forwarded there).

## Versions (pinned to what is cached on this machine, 2026-08-02)

Gradle 8.10.2 (wrapper) · AGP 8.7.3 · Kotlin 2.1.21 · compileSdk 35 · minSdk 26.
