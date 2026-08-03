// ⚠️ SUPERSEDED (2026-08-03): the real implementation lives at
// wallet/app/src/main/java/xyz/raiz/sobre/prover/ProverWebViewBridge.kt.
// This sketch is kept as the historical design doc, but note one KNOWN BUG in
// it: View.post() on a never-attached headless WebView queues runnables until
// window-attach — i.e. forever — so evaluateJavascript never runs. The real
// bridge posts through Handler(Looper.getMainLooper()) instead.
// See friction-report.md entry [2026-08-03 12:47].
package xyz.raiz.sobre.prover

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject

/**
 * ProverWebViewBridge — runs the OpenZeppelin CT proving stack (JS/WASM) inside an
 * isolated, headless WebView and exposes it to Kotlin as suspend functions.
 *
 * Division of labor (keep it strict):
 *  - WebView: ZK proof generation ONLY (the CT demo's proving code, unmodified).
 *  - Kotlin (RAÍZ wallet manager): key custody, tx building, signing, submission.
 *  Secrets needed for proving are passed per-call and never persisted in the WebView.
 *
 * Day-1 TODOs, in order:
 *  1. Package the demo's proving bundle under assets/prover/ and load it via
 *     WebViewAssetLoader (https://appassets.androidplatform.net) — no dev server in demo.
 *  2. If the WASM needs SharedArrayBuffer (crossOriginIsolated), serve COOP/COEP headers
 *     from the asset loader; if it still refuses → PWA fallback (decision tree, day 0).
 *  3. Define the exact window.RaizProver JS API by reading the demo's proving entry
 *     points; keep this file's contract (generateProof(kind, inputsJson)) stable.
 */
class ProverWebViewBridge(context: Context) {

    private val webView: WebView = WebView(context)
    private var pending: CompletableDeferred<JSONObject>? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(proverUrl: String, onReady: () -> Unit) {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false // assets go through WebViewAssetLoader only
        webView.addJavascriptInterface(ProverCallback(), "AndroidBridge")
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) = onReady()
        }
        webView.loadUrl(proverUrl)
    }

    /**
     * kind: "register" | "deposit" | "transfer" | "merge" | "withdraw"
     * inputsJson: operation inputs as the CT proving code expects them.
     * Returns the proof payload to embed in the Soroban invocation (signed by Kotlin).
     */
    suspend fun generateProof(kind: String, inputsJson: String): JSONObject {
        val deferred = CompletableDeferred<JSONObject>()
        pending = deferred
        val escaped = JSONObject.quote(inputsJson)
        webView.post {
            webView.evaluateJavascript(
                "window.RaizProver.generate('$kind', $escaped)" +
                    ".then(p => AndroidBridge.onProofReady(JSON.stringify(p)))" +
                    ".catch(e => AndroidBridge.onProofError(String(e)))",
                null
            )
        }
        return deferred.await() // caller wraps with withTimeout(90_000) — the spike's GO threshold
    }

    inner class ProverCallback {
        @JavascriptInterface
        fun onProofReady(json: String) {
            pending?.complete(JSONObject(json))
        }

        @JavascriptInterface
        fun onProofError(message: String) {
            // Friction-report gold: log verbatim, it becomes an OZ issue.
            pending?.completeExceptionally(RuntimeException("prover: $message"))
        }
    }

    fun destroy() = webView.destroy()
}
