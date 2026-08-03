package xyz.raiz.sobre.spike

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * SESSION 1 SPIKE HARNESS — full-screen WebView pointed at the OpenZeppelin CT
 * demo dev server. Goal: answer the day-0 GO/NO-GO question ("does in-browser
 * proving run inside an Android WebView in < 90 s per proof, without OOM?").
 *
 * This is NOT the wallet. No passkeys, no key custody, no Kotlin<->JS bridge —
 * the bridge is Session 5 (see wallet/docs-integration/ProverWebViewBridge.kt
 * for the agreed design sketch).
 *
 * The URL to load is BuildConfig.DEMO_URL — edit it in app/build.gradle.kts
 * (defaultConfig block) and rebuild.
 *
 * ── crossOriginIsolated: what this WebView CAN and CANNOT do ────────────────
 * Per docs/spike-findings.md the demo NEEDS crossOriginIsolated === true for
 * multithreaded proving (bb.js checks SharedArrayBuffer && crossOriginIsolated;
 * without it, it silently degrades to 1 WASM thread — slower, not broken).
 * Two conditions must BOTH hold:
 *
 *  1. COOP/COEP response headers. A WebView loading a remote http URL CANNOT
 *     inject response headers itself — the server must send them. GOOD NEWS,
 *     per findings: the demo's `next dev` ALREADY sends
 *       Cross-Origin-Opener-Policy: same-origin
 *       Cross-Origin-Embedder-Policy: credentialless
 *     on every route (vendor next.config.mjs:10-21), so nothing to add here.
 *
 *  2. A SECURE CONTEXT. This is the one that bites:
 *       - http://localhost:3000 IS a secure context → run
 *         `adb reverse tcp:3000 tcp:3000` and keep the default DEMO_URL.
 *       - http://<LAN-IP>:3000 is NOT → crossOriginIsolated stays false and
 *         bb.js drops to 1 thread. Still worth timing as the degraded config.
 *
 * On page load we log `crossOriginIsolated` (tag "SobreSpike") so every timing
 * run states which configuration it actually measured.
 *
 * TODO(Session 5): serve the proving bundle from app assets via
 * WebViewAssetLoader (https://appassets.androidplatform.net is a secure
 * context) and inject COOP/COEP in shouldInterceptRequest — that removes the
 * dev-server dependency entirely. Sketch: docs-integration/ProverWebViewBridge.kt.
 */
class MainActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true // required: the demo is a Next.js app
        webView.settings.domStorageEnabled = true // required: demo caches sk/deployment in localStorage

        // Keep every navigation inside the WebView (no external browser).
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                view?.evaluateJavascript(
                    "console.log('[SobreSpike] crossOriginIsolated=' + self.crossOriginIsolated)",
                    null
                )
            }
        }

        // Forward the page console to logcat — proof timings and bb.js thread
        // fallback messages land there. Filter: adb logcat -s SobreSpike
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                Log.i(TAG, "[js] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})")
                return true
            }
        }

        setContentView(webView)
        Log.i(TAG, "Loading DEMO_URL=${BuildConfig.DEMO_URL}")
        webView.loadUrl(BuildConfig.DEMO_URL)
    }

    @Deprecated("Fine for the spike; the real app will use OnBackPressedDispatcher")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "SobreSpike"
    }
}
