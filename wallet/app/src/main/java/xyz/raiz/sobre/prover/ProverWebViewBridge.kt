package xyz.raiz.sobre.prover

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Typed failures of the proving bridge. Every one of these is ALSO logged
 * verbatim under [ProverWebViewBridge.TAG] — JS error text is friction-report
 * material (it becomes OZ issues), never swallow it.
 */
sealed class ProverException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** The prover page did not come up (missing generated assets, load error). */
    class NotReady(detail: String) : ProverException("prover not ready: $detail")

    /** The JS side rejected: bad inputs, proof-free kind (deposit/merge), bb.js failure… */
    class JsError(val kind: String, detail: String) : ProverException("prover [$kind] failed in JS: $detail")

    /** No result within the timeout (spike GO threshold: 90 s per proof). */
    class Timeout(val kind: String, val timeoutMs: Long) :
        ProverException("prover [$kind] produced no result within $timeoutMs ms")
}

/**
 * ProverWebViewBridge — runs the OpenZeppelin CT proving stack (JS/WASM) inside
 * an isolated, HEADLESS WebView (never attached to the view hierarchy) and
 * exposes it to Kotlin as suspend functions. Implements the design sketch in
 * wallet/docs-integration/ProverWebViewBridge.kt.
 *
 * Division of labor (project decision, keep it strict):
 *  - WebView: ZK proof generation ONLY — the vendor SDK's witness builders +
 *    noir_js + bb.js, bundled from /vendor into assets/prover/ by
 *    `node wallet/tools/build-prover-assets.mjs` and served on the secure
 *    https://appassets.androidplatform.net origin via WebViewAssetLoader.
 *  - Kotlin: key custody, tx building, signing, submission (Session 5 step 5+).
 *  Secrets needed for proving are passed per call and never persisted in the
 *  WebView (the JS shim stores nothing; see assets/prover/raiz-shim.js header).
 *
 * Threading: construct + [initialize] + [destroy] on the MAIN thread (WebView
 * requirement). The suspend functions may be called from any dispatcher; they
 * post to the WebView internally and are serialized by a [Mutex] (one proof at
 * a time — bb.js on this origin is single-threaded anyway: Android WebView
 * never exposes SharedArrayBuffer, friction-report 2026-08-03 12:00).
 */
class ProverWebViewBridge(context: Context) {

    private val webView: WebView = WebView(context.applicationContext)

    /**
     * HEADLESS-WEBVIEW GOTCHA (cost us two fake "timeouts" on-device): this
     * WebView is never attached to a window, and View.post() on a detached
     * view QUEUES the runnable until attach — i.e. forever here. Every hop to
     * the main thread must go through this Handler, NEVER webView.post().
     */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val assetLoader = WebViewAssetLoader.Builder()
        // Identity mapping: https://appassets.androidplatform.net/<path> -> assets/<path>.
        // Custom handler instead of stock AssetsPathHandler because the latter
        // guesses MIME types and serves .wasm (and on some devices .js) as
        // text/plain — module scripts and instantiateStreaming then fail.
        .addPathHandler("/", ExplicitMimeAssetsHandler(context.applicationContext))
        .build()

    private val ready = CompletableDeferred<Unit>()
    private val proofMutex = Mutex()
    private val requestSeq = AtomicLong(0)

    private class PendingProof(
        val id: Long,
        val kind: String,
        val deferred: CompletableDeferred<JSONObject>,
    )

    @Volatile
    private var pending: PendingProof? = null

    /**
     * Optional mirror of the page console (called on the main thread). Needed
     * because this device family (Vivo) SUPPRESSES app logcat output by
     * default — the debug screen shows the console lines instead.
     */
    @Volatile
    var consoleListener: ((String) -> Unit)? = null

    /**
     * Loads the prover page and starts JS/WASM init. Call once, main thread.
     * [proverUrl] exists only for the documented interim fallback
     * (http://localhost:4173 via `adb reverse`, scripts/prover-bench server);
     * the default is the asset loader origin — no dev server needed.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(proverUrl: String = ENTRY_URL) {
        // chrome://inspect from a USB-attached PC — invaluable on devices that
        // suppress logcat. Debug surface only; revisit before any release build.
        WebView.setWebContentsDebuggingEnabled(true)
        webView.settings.javaScriptEnabled = true
        // IndexedDB/localStorage: bb.js caches the PUBLIC Aztec CRS there.
        // Our shim never touches storage — no secrets persist (project rule).
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false // assets go through WebViewAssetLoader only
        webView.settings.allowContentAccess = false

        webView.addJavascriptInterface(ProverCallback(), "AndroidBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? =
                // appassets requests -> APK assets; anything else (the CRS
                // download from https://crs.aztec.network) -> null = network.
                assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView?, url: String?) {
                Log.i(TAG, "prover page finished: $url")
                // Belt-and-braces readiness probe in case the module executed
                // before/without the AndroidBridge callback. Idempotent.
                view?.evaluateJavascript("!!(window.RaizProver)") { v ->
                    if (v == "true" && !ready.isCompleted) {
                        Log.i(TAG, "prover ready (detected by onPageFinished probe)")
                        ready.complete(Unit)
                    }
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    val msg = "main frame load error ${error?.errorCode}: " +
                        "${error?.description} (${request.url})"
                    Log.e(TAG, msg)
                    if (!ready.isCompleted) {
                        ready.completeExceptionally(ProverException.NotReady(msg))
                    }
                }
            }
        }

        // Page console -> logcat + optional on-screen mirror: bb.js fallback
        // notices, shim errors, timings.
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                val line = "[js] ${msg.message()} (${msg.sourceId()}:${msg.lineNumber()})"
                if (msg.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    Log.e(TAG, line)
                } else {
                    Log.i(TAG, line)
                }
                consoleListener?.let { listener -> mainHandler.post { listener(line) } }
                return true
            }
        }

        Log.i(TAG, "loading prover page: $proverUrl")
        webView.loadUrl(proverUrl)
    }

    /** Suspends until window.RaizProver is installed (or fails typed). */
    suspend fun awaitReady(timeoutMs: Long = READY_TIMEOUT_MS) {
        try {
            withTimeout(timeoutMs) { ready.await() }
        } catch (e: TimeoutCancellationException) {
            val msg = "no ready signal within $timeoutMs ms — generated assets missing? " +
                "run: node wallet/tools/build-prover-assets.mjs, then rebuild the APK"
            Log.e(TAG, msg)
            throw ProverException.NotReady(msg)
        }
    }

    /**
     * kind: "register" | "transfer" | "withdraw" — the three proved CT ops.
     * "deposit"/"merge" are rejected by the JS shim with a clear message: they
     * are the two proof-free CT operations (submit those txs directly).
     *
     * [inputsJson]: witness-builder inputs as JSON — scalars as decimal/0x-hex
     * strings, Grumpkin points as {x,y}; exact per-kind shape documented in
     * assets/prover/raiz-shim.js (the contract of window.RaizProver.generate).
     *
     * Returns the shim's result object: proofBase64, publicInputs, payload
     * (+ next state for transfer/withdraw), witnessMs/proveMs/ms, threads.
     * Signing and submission of the enclosing tx stay in Kotlin (step 5).
     */
    suspend fun generateProof(
        kind: String,
        inputsJson: String,
        timeoutMs: Long = PROOF_TIMEOUT_MS,
    ): JSONObject = runProverCall(kind, timeoutMs) { id ->
        // JSONObject.quote yields JS-safe double-quoted literals.
        "window.RaizProver.generate(${JSONObject.quote(kind)}, ${JSONObject.quote(inputsJson)})" +
            callbackTail(id)
    }

    /**
     * Proves register with fresh synthetic inputs inside the WebView (same
     * recipe as scripts/prover-bench). Returns timing + environment JSON —
     * the Session 5 on-device acceptance check.
     */
    suspend fun selftest(timeoutMs: Long = PROOF_TIMEOUT_MS): JSONObject =
        runProverCall("selftest", timeoutMs) { id ->
            "window.RaizProver.selftest()" + callbackTail(id)
        }

    /**
     * Invoke a window.RaizChain builder ([fn]: prepareRegister, prepareDeposit,
     * prepareMerge, prepareTransfer, status) — the secret-free build→simulate→
     * assemble side of Session 5 step 5. Returns the builder's JSON (unsigned
     * envelope XDR + metadata); signing and submission stay in Kotlin
     * (CtWallet). Timeout covers proving + RPC round-trips.
     */
    suspend fun chainCall(
        fn: String,
        inputsJson: String,
        timeoutMs: Long = CHAIN_TIMEOUT_MS,
    ): JSONObject = runProverCall("chain:$fn", timeoutMs) { id ->
        "if(!window.RaizChain||!window.RaizChain[${JSONObject.quote(fn)}])" +
            "{AndroidBridge.onProofError('$id','window.RaizChain.' + ${JSONObject.quote(fn)} +" +
            "' is not defined (stale prover bundle? rebuild assets)');return;}" +
            "window.RaizChain[${JSONObject.quote(fn)}](${JSONObject.quote(inputsJson)})" +
            callbackTail(id)
    }

    private fun callbackTail(id: Long): String =
        ".then(function(p){AndroidBridge.onProofReady('$id', JSON.stringify(p));})" +
            ".catch(function(e){AndroidBridge.onProofError('$id', String((e && e.stack) || e));})"

    private suspend fun runProverCall(
        kind: String,
        timeoutMs: Long,
        buildJs: (Long) -> String,
    ): JSONObject = proofMutex.withLock {
        awaitReady()
        val id = requestSeq.incrementAndGet()
        val deferred = CompletableDeferred<JSONObject>()
        pending = PendingProof(id, kind, deferred)
        val js =
            "(function(){" +
                "if(!window.RaizProver){AndroidBridge.onProofError('$id'," +
                "'window.RaizProver is not defined (bundle failed to load)');return;}" +
                buildJs(id) +
                "})();"
        Log.i(TAG, "prover call #$id [$kind] dispatched (timeout $timeoutMs ms)")
        mainHandler.post { webView.evaluateJavascript(js, null) }
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "prover call #$id [$kind] timed out after $timeoutMs ms")
            throw ProverException.Timeout(kind, timeoutMs)
        } finally {
            pending = null
        }
    }

    /** JS -> Kotlin callbacks. Invoked on the WebView's JavaBridge thread. */
    private inner class ProverCallback {

        @JavascriptInterface
        fun onProverReady() {
            Log.i(TAG, "prover ready (JS callback)")
            if (!ready.isCompleted) ready.complete(Unit)
        }

        @JavascriptInterface
        fun onProverInitError(message: String) {
            Log.e(TAG, "prover init error: $message")
            if (!ready.isCompleted) {
                ready.completeExceptionally(ProverException.NotReady(message))
            }
        }

        @JavascriptInterface
        fun onProofReady(id: String, json: String) {
            val p = pending
            if (p == null || p.id.toString() != id) {
                Log.w(TAG, "ignoring stale proof result #$id (pending: ${p?.id})")
                return
            }
            try {
                p.deferred.complete(JSONObject(json))
            } catch (e: Exception) { // malformed JSON from JS — surface, don't crash the bridge thread
                Log.e(TAG, "unparseable proof result #$id: ${e.message}")
                p.deferred.completeExceptionally(
                    ProverException.JsError(p.kind, "unparseable result JSON: ${e.message}"),
                )
            }
        }

        @JavascriptInterface
        fun onProofError(id: String, message: String) {
            val p = pending
            if (p == null || p.id.toString() != id) {
                Log.w(TAG, "ignoring stale proof error #$id: $message")
                return
            }
            // Friction-report gold: log verbatim, it becomes an OZ issue.
            Log.e(TAG, "prover call #$id [${p.kind}] JS error: $message")
            p.deferred.completeExceptionally(ProverException.JsError(p.kind, message))
        }
    }

    /** Main thread. The WebView holds no state worth keeping — no secrets. */
    fun destroy() {
        pending?.deferred?.completeExceptionally(
            ProverException.NotReady("bridge destroyed mid-call"),
        )
        pending = null
        webView.destroy()
    }

    /**
     * Serves APK assets with an EXPLICIT extension->MIME table. The stock
     * AssetsPathHandler guesses via the platform map, which returns text/plain
     * for .wasm (and .js on some builds); Chromium then refuses the module
     * script / streaming-compile. Deterministic table, no guessing.
     */
    private class ExplicitMimeAssetsHandler(context: Context) : WebViewAssetLoader.PathHandler {
        private val assets = context.assets

        override fun handle(path: String): WebResourceResponse? {
            // Path arrives with the registered "/" prefix stripped: "prover/…".
            if (!path.startsWith("prover/")) return null // only our subtree exists
            return try {
                val stream = assets.open(path)
                val ext = path.substringAfterLast('.', "").lowercase()
                val mime = MIME[ext] ?: "application/octet-stream"
                val encoding = if (mime.startsWith("text/") || mime.endsWith("json") ||
                    mime.endsWith("javascript")
                ) "utf-8" else null
                WebResourceResponse(mime, encoding, stream)
            } catch (e: IOException) {
                Log.e(TAG, "asset not found: $path (${e.message})")
                null // -> 404 in the page; the page reports it via onProverInitError
            }
        }

        private companion object {
            val MIME = mapOf(
                "html" to "text/html",
                "js" to "text/javascript", // ES modules REQUIRE a JS MIME type
                "mjs" to "text/javascript",
                "wasm" to "application/wasm",
                "json" to "application/json",
                "map" to "application/json",
                "txt" to "text/plain",
                "dat" to "application/octet-stream",
            )
        }
    }

    companion object {
        const val TAG = "SobreSpike"

        /** appassets is a secure context; SAB still never appears in a WebView
         *  (platform limitation) — bb.js runs single-threaded, by design. */
        const val ENTRY_URL = "https://appassets.androidplatform.net/prover/index.html"

        /** Spike GO threshold: 90 s per proof (docs/SPIKE_DIA0.md). */
        const val PROOF_TIMEOUT_MS = 90_000L

        /** RaizChain builders: proving (≤90 s budget) + state sync + RPC
         *  round-trips (simulate + getAccount, testnet latency). */
        const val CHAIN_TIMEOUT_MS = 180_000L

        /** Page + bundle + wasm init are seconds, not minutes. */
        const val READY_TIMEOUT_MS = 30_000L
    }
}
