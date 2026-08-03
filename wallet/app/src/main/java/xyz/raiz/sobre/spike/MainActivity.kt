package xyz.raiz.sobre.spike

import android.app.Activity
import android.graphics.Typeface
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import xyz.raiz.sobre.prover.ProverWebViewBridge

/**
 * SESSION 5 DEBUG SCREEN — exercises the real [ProverWebViewBridge]: a headless
 * WebView serving the CT proving bundle from APK assets (WebViewAssetLoader,
 * https://appassets.androidplatform.net) generates a register proof with
 * synthetic inputs on demand and reports the timings here + logcat.
 *
 * This replaces the Session 1 full-screen-WebView spike harness (GO decided,
 * evidence in docs/SPIKE_DIA0.md). Still NOT the wallet UI — just the smallest
 * honest surface that proves the Kotlin<->JS bridge end to end. Watch:
 *   adb logcat -s SobreSpike
 */
class MainActivity : Activity() {

    private lateinit var bridge: ProverWebViewBridge
    private lateinit var status: TextView
    private lateinit var console: TextView
    private lateinit var selftestButton: Button
    private val consoleLines = ArrayDeque<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        bridge = ProverWebViewBridge(this)
        // This device family (Vivo) suppresses app logcat: mirror the page
        // console on screen — it is the only viewport into JS-side failures.
        bridge.consoleListener = { line ->
            consoleLines.addLast(line)
            while (consoleLines.size > CONSOLE_KEEP) consoleLines.removeFirst()
            console.text = consoleLines.joinToString("\n")
        }
        bridge.initialize() // asset-loader URL; no dev server involved

        // Surface page readiness (wasm init etc.) without blocking the button —
        // pressing it early just suspends on awaitReady() inside the bridge.
        val t0 = SystemClock.elapsedRealtime()
        scope.launch {
            try {
                bridge.awaitReady()
                setStatus("prover page ready in ${SystemClock.elapsedRealtime() - t0} ms — tap the button")
            } catch (e: Exception) {
                Log.e(TAG, "prover init failed", e)
                setStatus("prover init FAILED:\n${e.message}")
            }
        }
    }

    private fun runSelftest() {
        selftestButton.isEnabled = false
        setStatus("proving register with synthetic inputs…\n(first run also initializes wasm and downloads the public CRS)")
        val t0 = SystemClock.elapsedRealtime()
        scope.launch {
            try {
                val r = bridge.selftest()
                val bridgeMs = SystemClock.elapsedRealtime() - t0
                val line = "self-test OK: witness ${r.optLong("witnessMs")} ms + " +
                    "prove ${r.optLong("proveMs")} ms = ${r.optLong("ms")} ms in JS " +
                    "(${bridgeMs} ms through the bridge, threads=${r.optInt("threads")})"
                Log.i(TAG, line)
                setStatus("$line\n\n${r.toString(2)}")
            } catch (e: Exception) {
                // Typed ProverException reaches here; message already logged verbatim.
                Log.e(TAG, "self-test failed", e)
                setStatus("self-test FAILED (${e.javaClass.simpleName}):\n${e.message}")
            } finally {
                selftestButton.isEnabled = true
            }
        }
    }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Sobre del Barrio — bridge self-test (Session 5)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, pad / 2)
        })
        selftestButton = Button(this).apply {
            text = "Self-test register proof"
            setOnClickListener { runSelftest() }
        }
        root.addView(selftestButton)
        status = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextIsSelectable(true)
            setPadding(0, pad / 2, 0, 0)
            text = "initializing prover page…"
        }
        console = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTextIsSelectable(true)
            setPadding(0, pad / 2, 0, 0)
        }
        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(console)
        }
        root.addView(ScrollView(this).apply { addView(scrollContent) })
        setContentView(root)
    }

    private fun setStatus(text: String) {
        status.text = text
    }

    override fun onDestroy() {
        scope.cancel()
        bridge.destroy()
        super.onDestroy()
    }

    private companion object {
        const val TAG = ProverWebViewBridge.TAG // single logcat tag: SobreSpike
        const val CONSOLE_KEEP = 30 // rolling window of page console lines
    }
}
