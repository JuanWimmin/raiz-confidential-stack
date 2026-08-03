# Docs: Android WebView never becomes crossOriginIsolated — WebView-embedded dApps always prove single-threaded, regardless of COOP/COEP

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: brozorec/stellar-confidential-token-demo (docs; likely also worth a note in OpenZeppelin/stellar-contracts confidential docs/SDK.md, which inherit the same browser-proving assumptions)
- Version/commit: demo @ ac67499; Android System WebView 150.0.7871.181 (Android 13, Vivo V2110); @aztec/bb.js 0.87.0

## Repro steps

1. Serve a page that loads bb.js with `Cross-Origin-Opener-Policy: same-origin`
   and `Cross-Origin-Embedder-Policy: credentialless` on every response.
2. Load it at `http://localhost:<port>` (a secure context) inside an Android
   WebView via `adb reverse`.
3. Load the identical URL in Chrome on the same device.

## Expected

localhost is a secure context and both isolation headers are present, so
`crossOriginIsolated` should be true (as it is in Chrome), enabling
SharedArrayBuffer multithreaded proving.

## Observed (verbatim page banner; no error anywhere — a silent capability gap)

```
WebView:  crossOriginIsolated: false · SharedArrayBuffer: false · threads bb.js will use: 1
Chrome 150 (same device, same URL): crossOriginIsolated: true · SharedArrayBuffer: true · threads bb.js will use: 8
```

Android WebView does not enable SharedArrayBuffer/crossOriginIsolated at all —
the cross-origin-isolation recipe in the demo's docs cannot work inside a
WebView no matter how the headers are injected.

Not fatal: bb.js falls back to 1 thread gracefully. All three circuits still
prove in 10.8–15.7 s on a 4 GB mid-range phone (vs 2.7–6.7 s multithreaded in
Chrome on the same device).

## Suggested fix

One paragraph in the proving docs: "WebView-embedded dApps will always prove
single-threaded; budget ~2-3x the multithreaded latency or ship as an
installable PWA opened in the browser". Would have saved us a day-0 spike.
