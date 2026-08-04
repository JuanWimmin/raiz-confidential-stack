package xyz.raiz.sobre.ui.meta

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import xyz.raiz.sobre.data.EventSource
import xyz.raiz.sobre.data.EventSourceStore
import xyz.raiz.sobre.data.EventsClient
import xyz.raiz.sobre.data.GoalTimeline
import xyz.raiz.sobre.prover.ProverWebViewBridge
import xyz.raiz.sobre.wallet.CtConfig

/**
 * State of the public goal screen — the screen the app opens on, with no login.
 *
 * Two independent loads feed it, and they are kept apart on purpose:
 *
 *  1. **The timeline** (fast, no WebView): two `GET /events` calls against the
 *     configured [EventSource]. This is what the source switch changes live, and
 *     it is the video's central scene.
 *  2. **The total** (slow-ish, WebView): `goalTotal` opens the PUBLISHED auditor
 *     view key and re-commits every decrypted opening against the on-chain
 *     Pedersen commitments. The figure is rendered ONLY when the shim says
 *     `verified` — an unverified number is worse than no number here.
 */
data class MetaUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Verbatim failure text of the events load; null when the load succeeded. */
    val error: String? = null,
    val timeline: GoalTimeline = GoalTimeline.EMPTY,
    val source: EventSource = EventSource.raizMemory(),
    val baseUrl: String = EventSource.DEFAULT_BASE_URL,
    val goalName: String = MetaConfig.FALLBACK_NAME,
    val targetStroops: Long = MetaConfig.FALLBACK_TARGET_STROOPS,
    val total: TotalState = TotalState.Idle,
) {
    /** Percentage of the display target, only meaningful with a verified total. */
    val reachedPct: Int
        get() = (total as? TotalState.Verified)?.let {
            if (targetStroops <= 0) 0
            else ((it.stroops.toDouble() / targetStroops) * 100).toInt().coerceIn(0, 100)
        } ?: 0
}

/** The lifecycle of the one number this app is not allowed to fake. */
sealed interface TotalState {
    data object Idle : TotalState

    /**
     * @param stale the figure that was on screen when this run started, if any.
     *        It is kept VISIBLE but explicitly marked as outdated: blanking the
     *        number out for 6-8 s is bad on camera, and leaving its "verified at
     *        ledger N" caption alone would claim a verification that no longer
     *        covers the timeline below it. Neither is acceptable; this is.
     */
    data class Running(
        val startedAt: Long,
        val phase: String,
        val stale: Verified? = null,
    ) : TotalState

    data class Verified(
        val stroops: Long,
        val atLedger: Long,
        val contributions: Int,
        /** Spanish label of the source the user picked, e.g. "Raiz Memory". */
        val eventSource: String,
        val eventsScanned: Int,
        val ms: Int,
        val checks: List<Check>,
    ) : TotalState

    /** The shim ran but a check failed — show the failure, never the figure. */
    data class Unverified(val message: String, val checks: List<Check>) : TotalState

    /** The shim could not run at all (prover not ready, source empty, timeout). */
    data class Failed(val message: String) : TotalState
}

/** One cross-check from the shim. `ok == null` means SKIPPED, not passed. */
data class Check(val name: String, val ok: Boolean?, val detail: String)

class MetaViewModel(
    app: Application,
    private val bridge: ProverWebViewBridge,
) : AndroidViewModel(app) {

    private val sourceStore = EventSourceStore(app)
    private val events = EventsClient({ sourceStore.value })

    private val _state = MutableStateFlow(
        MetaUiState(source = sourceStore.value, baseUrl = sourceStore.baseUrl()),
    )
    val state: StateFlow<MetaUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var totalJob: Job? = null

    init {
        refresh(autoVerify = true)
    }

    // -- the source switch: the demo's central control -----------------------

    /** Switch preset and reload immediately — one tap, no restart. */
    fun selectSource(id: String) {
        sourceStore.selectPreset(id)
        _state.value = _state.value.copy(
            source = sourceStore.value,
            // A source change invalidates the total: it was computed from the
            // other source's events and saying otherwise would be a lie.
            total = TotalState.Idle,
        )
        refresh(autoVerify = true)
    }

    fun setBaseUrl(raw: String) {
        sourceStore.setBaseUrl(raw)
        _state.value = _state.value.copy(
            source = sourceStore.value,
            baseUrl = sourceStore.baseUrl(),
            total = TotalState.Idle,
        )
        refresh(autoVerify = false)
    }

    // -- the timeline --------------------------------------------------------

    /**
     * Reload the timeline and — by default — re-verify the total with it.
     *
     * `autoVerify` defaults to TRUE on purpose: the two numbers on this screen
     * describe the same fund, so refreshing one while leaving the other pinned
     * to an older ledger puts a contradiction on screen ("4 aportes" next to a
     * total verified when there were 3). Re-verification costs 6-8 s and the
     * hero says so while it runs, which is cheaper than a wrong number.
     * Callers that must NOT spend it (a base-URL edit, which also invalidates
     * the total outright) pass false explicitly.
     */
    fun refresh(autoVerify: Boolean = true) {
        loadJob?.cancel()
        val first = _state.value.timeline.isEmpty
        _state.value = _state.value.copy(
            loading = first,
            refreshing = !first,
            error = null,
            source = sourceStore.value,
        )
        loadJob = viewModelScope.launch {
            try {
                val src = sourceStore.value
                val ct = events.allEvents(
                    contractId = CtConfig.TOKEN,
                    startLedger = CtConfig.DEPLOYED_AT_LEDGER.toLong(),
                    source = src,
                )
                val meta = events.allEvents(
                    contractId = MetaConfig.GOAL_META,
                    startLedger = CtConfig.DEPLOYED_AT_LEDGER.toLong(),
                    source = src,
                )
                val timeline = GoalTimeline.build(
                    ctEvents = ct.events,
                    goalMetaEvents = meta.events,
                    source = src,
                    latestLedger = maxOf(ct.latestLedger, meta.latestLedger),
                    // Either page may carry the retention floor; take the one
                    // that admits to forgetting.
                    oldestLedger = ct.oldestLedger ?: meta.oldestLedger,
                    goalId = MetaConfig.GOAL_ID,
                )
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    error = null,
                    timeline = timeline,
                    source = src,
                )
                if (autoVerify) verifyTotal()
            } catch (e: Exception) {
                Log.e(TAG, "events load failed", e)
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    // EventsException messages are already Spanish and verbatim.
                    error = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    // -- the total -----------------------------------------------------------

    /**
     * Open the published view key in the WebView and verify the sum against the
     * chain. Reads events through the SAME configured source as the timeline,
     * so a forgetful source produces an honest failure rather than a wrong
     * number — which is exactly what we want on camera.
     */
    fun verifyTotal() {
        if (_state.value.total is TotalState.Running) return
        totalJob?.cancel()
        val startedAt = SystemClock.elapsedRealtime()
        // Carry the previous figure into the running state so the hero can show
        // it greyed out and labelled "desactualizado" instead of claiming it is
        // still verified (or blanking the screen).
        val stale = _state.value.total as? TotalState.Verified
        _state.value = _state.value.copy(
            total = TotalState.Running(startedAt, "abriendo la view key publicada…", stale),
        )
        totalJob = viewModelScope.launch {
            val src = sourceStore.value
            try {
                val params = JSONObject()
                    .put("rpcUrl", CtConfig.RPC_URL)
                    .put("passphrase", CtConfig.PASSPHRASE)
                    .put("tokenContractId", CtConfig.TOKEN)
                    .put("goalAccount", CtConfig.GOAL_ACCOUNT)
                    .put("viewKeySecretHex", MetaConfig.VIEW_KEY_HEX)
                    .put("fromLedger", CtConfig.DEPLOYED_AT_LEDGER)
                    .put("auditorContractId", CtConfig.AUDITOR)
                    .put("verifierContractId", CtConfig.VERIFIER)
                    .put("goalMetaContractId", MetaConfig.GOAL_META)
                    .put("goalMetaGoalId", MetaConfig.GOAL_ID)
                src.sourceParam?.let { params.put("eventsSource", it) }

                _state.value = _state.value.copy(
                    total = TotalState.Running(
                        startedAt,
                        "leyendo eventos de ${src.label} y recomponiendo el total…",
                        stale,
                    ),
                )
                val r = bridge.goalTotal(params.toString(), eventsSourceUrl = src.baseUrl)
                applyTotal(r, src)
            } catch (e: Exception) {
                Log.e(TAG, "goalTotal failed", e)
                _state.value = _state.value.copy(
                    total = TotalState.Failed(e.message ?: e.javaClass.simpleName),
                )
            }
        }
    }

    private fun applyTotal(r: JSONObject, src: EventSource) {
        val checks = buildList {
            val arr = r.optJSONArray("checks")
            for (i in 0 until (arr?.length() ?: 0)) {
                val c = arr!!.getJSONObject(i)
                add(
                    Check(
                        name = c.optString("name"),
                        ok = if (c.isNull("ok")) null else c.optBoolean("ok"),
                        detail = c.optString("detail"),
                    ),
                )
            }
        }
        val name = r.optString("goalName").ifEmpty { _state.value.goalName }
        val target = r.optString("targetStroops").toLongOrNull()
            ?.takeIf { it > 0 } ?: _state.value.targetStroops

        val total = if (r.optBoolean("verified")) {
            TotalState.Verified(
                stroops = r.optString("totalStroops").toLongOrNull() ?: 0L,
                atLedger = r.optLong("verifiedAtLedger"),
                contributions = r.optJSONArray("contributions")?.length() ?: 0,
                // The shim reports its plumbing ("events-url" / "rpc"), which is
                // not something to print in Spanish copy. Name the source the
                // user actually picked — unless the shim says it bypassed our
                // URL and read the RPC itself, in which case say THAT.
                eventSource = if (r.optString("eventSource") == "rpc") {
                    "el RPC público"
                } else {
                    src.label
                },
                eventsScanned = r.optInt("eventsScanned"),
                ms = r.optInt("ms"),
                checks = checks,
            )
        } else {
            TotalState.Unverified(
                message = checks.firstOrNull { it.ok == false }?.let { "${it.name}: ${it.detail}" }
                    ?: "una comprobación contra los compromisos on-chain no pasó",
                checks = checks,
            )
        }
        _state.value = _state.value.copy(goalName = name, targetStroops = target, total = total)
    }

    companion object {
        private const val TAG = "SobreSpike"

        fun factory(app: Application, prover: xyz.raiz.sobre.ui.nav.ProverViewModel): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    MetaViewModel(app, prover.bridge) as T
            }
    }
}
