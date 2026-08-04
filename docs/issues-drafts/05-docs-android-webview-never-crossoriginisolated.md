# Docs: note that Android WebView cannot be cross-origin isolated, so WebView-embedded clients always prove single-threaded

- Status: DRAFT — do not open online before human review
- Repo it belongs to: OpenZeppelin/stellar-contracts (`packages/tokens/src/confidential/docs/SDK.md`, which specifies the client SDK layer and currently says nothing about threading or isolation). A shorter version is also worth having in brozorec/stellar-confidential-token-demo, whose docs carry the COOP/COEP recipe.
- Verified: 2026-08-04. Android System WebView 150.0.7871.181 on Android 13 (Vivo V2110, 4 GB); demo @ ac67499; `@aztec/bb.js` 0.87.0

## The gap

The confidential-token client story assumes proving happens in a browser that
can be made cross-origin isolated, and the demo documents the COOP/COEP recipe
for exactly that. Anyone embedding that client in a native Android app will
reach for a WebView, follow the recipe, and find that it does not apply —
without any error to explain why.

## Repro steps

1. Serve a page that loads bb.js, sending `Cross-Origin-Opener-Policy: same-origin`
   and `Cross-Origin-Embedder-Policy: credentialless` on every response.
2. Open it at `http://localhost:<port>` (a secure context) inside an Android
   WebView, via `adb reverse tcp:<port> tcp:<port>`.
3. Open the identical URL in Chrome on the same device.
4. Print `crossOriginIsolated`, `typeof SharedArrayBuffer`, and the thread
   count bb.js resolves.

## Expected

localhost is a secure context and both isolation headers are present, so
`crossOriginIsolated` should be true — as it is in Chrome on the same device,
same URL — enabling SharedArrayBuffer multithreaded proving.

## Observed (no error anywhere — a silent capability gap)

```
WebView 150.0.7871.181:  crossOriginIsolated: false · SharedArrayBuffer: false · threads bb.js will use: 1
Chrome 150, same device, same URL:  crossOriginIsolated: true · SharedArrayBuffer: true · threads bb.js will use: 8
```

This is a platform limitation rather than anything about this codebase:
cross-origin isolation depends on a multi-process model that Android WebView
does not provide, which is discussed upstream in whatwg/html#6060 ("some
platforms like Android WebView can't easily support multiple processes, so
they can't really support crossOriginIsolated") and in the Chromium
blink-dev intent to re-enable SAB on Android behind COOP/COEP. No amount of
header injection changes it inside a WebView.

It is not fatal. bb.js falls back to one thread gracefully, and on a 4 GB
mid-range phone all three circuits still prove in 10.8-15.7 s inside the
WebView, versus 2.7-6.7 s multithreaded in Chrome on that same device.

## Suggested fix

A short paragraph in the proving/SDK docs, roughly: "Cross-origin isolation is
unavailable in Android WebView, so an embedded client always proves on a
single thread — budget roughly 2-3x the multithreaded latency, or ship as an
installable PWA opened in the browser." We spent a day-0 spike establishing
this; a sentence would have saved it, and mobile wallets are a natural
consumer of confidential tokens.
