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
import org.json.JSONObject
import xyz.raiz.sobre.prover.ProverWebViewBridge
import xyz.raiz.sobre.wallet.CtConfig
import xyz.raiz.sobre.wallet.CtWallet
import xyz.raiz.sobre.wallet.WalletStore

/**
 * SESSION 5 DEBUG SCREEN — now drives the FULL on-device pipeline (M1):
 * proof/tx built in the headless WebView (RaizChain), signed in Kotlin
 * (StellarAccount, seed in EncryptedSharedPreferences), submitted + polled
 * from Kotlin (SorobanRpc) against OUR CT wrapper on testnet.
 *
 * Still NOT the wallet UI (Session 6) — the smallest honest surface that
 * proves register/deposit/merge/transfer end to end from the phone. Watch:
 *   adb logcat -s SobreSpike
 */
class MainActivity : Activity() {

    private lateinit var bridge: ProverWebViewBridge
    private lateinit var wallet: CtWallet
    private lateinit var status: TextView
    private lateinit var console: TextView
    private val buttons = mutableListOf<Button>()
    private val consoleLines = ArrayDeque<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()

        bridge = ProverWebViewBridge(this)
        wallet = CtWallet(bridge, WalletStore(this))
        // This device family (Vivo) suppresses app logcat: mirror the page
        // console on screen — it is the only viewport into JS-side failures.
        bridge.consoleListener = { line ->
            consoleLines.addLast(line)
            while (consoleLines.size > CONSOLE_KEEP) consoleLines.removeFirst()
            console.text = consoleLines.joinToString("\n")
        }
        bridge.initialize() // asset-loader URL; no dev server involved

        val t0 = SystemClock.elapsedRealtime()
        scope.launch {
            try {
                bridge.awaitReady()
                // Key material only touches Kotlin: derive off the main thread.
                val accountId = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    wallet.account.accountId
                }
                setStatus(
                    "prover listo en ${SystemClock.elapsedRealtime() - t0} ms\n" +
                        "cuenta de la app (custodia Kotlin):\n$accountId\n" +
                        "wrapper: ${CtConfig.TOKEN.take(8)}… · meta: ${CtConfig.GOAL_ACCOUNT.take(8)}…",
                )
            } catch (e: Exception) {
                Log.e(TAG, "init failed", e)
                setStatus("init FAILED:\n${e.message}")
            }
        }
    }

    /** Shared runner: progress states + honest errors, one op at a time. */
    private fun runOp(name: String, op: suspend (progress: (String) -> Unit) -> String) {
        buttons.forEach { it.isEnabled = false }
        val t0 = SystemClock.elapsedRealtime()
        setStatus("[$name] arrancando…")
        scope.launch {
            try {
                val summary = op { line -> runOnUiThread { setStatus("[$name] $line") } }
                val secs = (SystemClock.elapsedRealtime() - t0) / 1000.0
                Log.i(TAG, "$name OK in ${secs}s: $summary")
                setStatus("[$name] COMPLETADO en ${"%.1f".format(secs)} s\n$summary")
            } catch (e: Exception) {
                Log.e(TAG, "$name failed", e)
                setStatus("[$name] FALLÓ (${e.javaClass.simpleName}):\n${e.message}")
            } finally {
                buttons.forEach { it.isEnabled = true }
            }
        }
    }

    private fun outcomeText(o: CtWallet.TxOutcome): String =
        if (o.alreadyDone) {
            "${o.method}: ${o.detail}"
        } else {
            "${o.method} CONFIRMADO en ledger ${o.ledger}\n" +
                "tx ${o.txHashHex}\n" +
                (if (o.proveMs > 0) "prueba ZK en el teléfono: ${o.proveMs} ms\n" else "") +
                "${o.detail}\n${o.explorerUrl}"
        }

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Sobre del Barrio — M1 pipeline real (Session 5)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, pad / 2)
        })

        fun addButton(label: String, onClick: () -> Unit) {
            val b = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { onClick() }
            }
            buttons += b
            root.addView(b)
        }

        addButton("M1: Abrir mi sobre (register, real)") {
            runOp("register") { p -> outcomeText(wallet.register(p)) }
        }
        addButton("M1: Sellar 10 XLM (deposit, real)") {
            runOp("deposit") { p -> outcomeText(wallet.deposit(10 * STROOPS_PER_XLM, p)) }
        }
        addButton("M1: Cosechar (merge, real)") {
            runOp("merge") { p -> outcomeText(wallet.merge(p)) }
        }
        addButton("M1: Aportar 5 XLM a la meta (transfer, real)") {
            runOp("transfer") { p -> outcomeText(wallet.transferToGoal(5 * STROOPS_PER_XLM, p)) }
        }
        addButton("Estado (balances descifrados localmente)") {
            runOp("estado") { p ->
                val s: JSONObject = wallet.status(p)
                val spend = s.optString("spendableStroops", "—")
                val recv = s.optString("receivingStroops", "—")
                "registrada: ${s.optBoolean("registered")}\n" +
                    "spendable: $spend stroops\nreceiving: $recv stroops\n" +
                    "ledger: ${s.optLong("latestLedger")}"
            }
        }
        addButton("Self-test register proof (sintético)") {
            runOp("self-test") { _ ->
                val r = bridge.selftest()
                "witness ${r.optLong("witnessMs")} ms + prove ${r.optLong("proveMs")} ms " +
                    "= ${r.optLong("ms")} ms (threads=${r.optInt("threads")})"
            }
        }

        status = TextView(this).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextIsSelectable(true)
            setPadding(0, pad / 2, 0, 0)
            text = "inicializando prover…"
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
        const val STROOPS_PER_XLM = 10_000_000L
    }
}
