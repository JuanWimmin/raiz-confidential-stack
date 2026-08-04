package xyz.raiz.sobre.ui.sobre

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xyz.raiz.sobre.ui.nav.ProverViewModel
import xyz.raiz.sobre.ui.nav.parseXlmToStroops
import xyz.raiz.sobre.wallet.CtWallet

/**
 * State of "Mi Sobre" — the private half of the app.
 *
 * Every operation here runs the pipeline that already worked from the Session 5
 * debug console, unchanged: the WebView proves and builds, Kotlin signs with the
 * seed from EncryptedSharedPreferences, Kotlin submits and polls. Nothing is
 * simulated; a failure surfaces verbatim.
 *
 * The balances are decrypted ON THIS DEVICE by replaying the wrapper's events
 * with the account's own secret scalar — `null` means "not decrypted yet", and
 * the UI renders that as `•••`, never as `0`.
 */
data class SobreUiState(
    val accountId: String? = null,
    val loadingStatus: Boolean = true,
    val statusError: String? = null,
    val registered: Boolean? = null,
    val spendableStroops: Long? = null,
    val receivingStroops: Long? = null,
    val syncedLedger: Long? = null,
    val amountInput: String = "",
    val op: OpUiState = OpUiState.Idle,
    val proverError: String? = null,
) {
    val amountStroops: Long? get() = parseXlmToStroops(amountInput)
    val busy: Boolean get() = op is OpUiState.Running
}

/** One operation at a time — the bridge serializes them anyway (Mutex). */
sealed interface OpUiState {
    data object Idle : OpUiState

    /**
     * @param startedAt `SystemClock.elapsedRealtime()` at kickoff — ProofProgress
     *        counts from this instant, so rotation and recomposition cannot
     *        reset the timer.
     */
    data class Running(val op: String, val label: String, val phase: String, val startedAt: Long) : OpUiState

    /**
     * @param finishedAt `SystemClock.elapsedRealtime()` when the outcome
     *        arrived. The screen pins a FRESH result above the fold (a tx hash
     *        the user cannot see without scrolling might as well not exist on a
     *        720x1600 phone) and lets an old one settle back under the buttons
     *        that produced it.
     */
    data class Ok(
        val op: String,
        val label: String,
        val ledger: Long?,
        val txHash: String?,
        val proveMs: Long,
        val note: String = "",
        val finishedAt: Long = SystemClock.elapsedRealtime(),
    ) : OpUiState

    /** Verbatim failure text: ProverException/HostError messages are the product. */
    data class Failed(
        val op: String,
        val label: String,
        val message: String,
        val finishedAt: Long = SystemClock.elapsedRealtime(),
    ) : OpUiState
}

/** When did this outcome land? `null` while idle or still running. */
val OpUiState.finishedAtOrNull: Long?
    get() = when (this) {
        is OpUiState.Ok -> finishedAt
        is OpUiState.Failed -> finishedAt
        else -> null
    }

class SobreViewModel(
    app: Application,
    private val wallet: CtWallet,
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(SobreUiState())
    val state: StateFlow<SobreUiState> = _state.asStateFlow()

    private var opJob: Job? = null

    /** Set by the shell so a confirmed *aporte* refreshes the goal timeline. */
    var onAporteConfirmed: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            // Deriving the account touches EncryptedSharedPreferences + Ed25519:
            // off the main thread, exactly as the debug console did it.
            val id = withContext(Dispatchers.Default) {
                runCatching { wallet.account.accountId }.getOrNull()
            }
            _state.value = _state.value.copy(accountId = id)
            refreshStatus()
        }
    }

    fun onAmountChange(raw: String) {
        // Digits, one separator: the CT amount is stroops, never a free string.
        val filtered = raw.filter { it.isDigit() || it == '.' || it == ',' }.take(12)
        _state.value = _state.value.copy(amountInput = filtered)
    }

    fun dismissOp() {
        if (_state.value.op !is OpUiState.Running) {
            _state.value = _state.value.copy(op = OpUiState.Idle)
        }
    }

    // -- read ----------------------------------------------------------------

    /** Decrypted balances via event replay; safe to call on every resume. */
    fun refreshStatus() {
        if (_state.value.busy) return
        _state.value = _state.value.copy(loadingStatus = true, statusError = null)
        viewModelScope.launch {
            try {
                val s = wallet.status { }
                _state.value = _state.value.copy(
                    loadingStatus = false,
                    statusError = null,
                    registered = s.optBoolean("registered"),
                    spendableStroops = s.optString("spendableStroops").toLongOrNull(),
                    receivingStroops = s.optString("receivingStroops").toLongOrNull(),
                    syncedLedger = s.optLong("latestLedger").takeIf { it > 0 },
                )
            } catch (e: Exception) {
                Log.e(TAG, "status failed", e)
                _state.value = _state.value.copy(
                    loadingStatus = false,
                    statusError = e.message ?: e.javaClass.simpleName,
                )
            }
        }
    }

    // -- write ---------------------------------------------------------------

    /** register — "Abrir mi sobre". First run after an install must do this. */
    fun abrirSobre() = run("register", "Abriendo tu sobre") { p ->
        val o = wallet.register(p)
        if (o.alreadyDone) o.copy(detail = "tu sobre ya estaba abierto") else o
    }

    /** deposit — "Sellar": public XLM in, confidential balance out. */
    fun sellar() {
        val stroops = _state.value.amountStroops ?: return failAmount("Sellar")
        run("deposit", "Sellando monedas") { p -> wallet.deposit(stroops, p) }
    }

    /** confidential_transfer to the goal — "Aportar". The amount is hidden. */
    fun aportar() {
        val stroops = _state.value.amountStroops ?: return failAmount("Aportar")
        run("confidential_transfer", "Aportando a la meta", after = { onAporteConfirmed?.invoke() }) { p ->
            wallet.transferToGoal(stroops, p)
        }
    }

    /** merge — "Cosechar": receiving -> spendable. Proof-free, seconds. */
    fun cosecharMiSobre() = run("merge", "Cosechando tu sobre") { p -> wallet.merge(p) }

    private fun failAmount(label: String) {
        _state.value = _state.value.copy(
            op = OpUiState.Failed(
                op = label.lowercase(),
                label = label,
                message = "Escribe un monto en XLM mayor que cero (por ejemplo 5).",
            ),
        )
    }

    private fun run(
        op: String,
        label: String,
        after: (() -> Unit)? = null,
        block: suspend (progress: (String) -> Unit) -> CtWallet.TxOutcome,
    ) {
        if (_state.value.busy) return
        opJob?.cancel()
        val startedAt = SystemClock.elapsedRealtime()
        _state.value = _state.value.copy(
            op = OpUiState.Running(op, label, "preparando…", startedAt),
        )
        opJob = viewModelScope.launch {
            try {
                val outcome = block { line ->
                    val cur = _state.value.op
                    if (cur is OpUiState.Running && cur.startedAt == startedAt) {
                        _state.value = _state.value.copy(op = cur.copy(phase = line))
                    }
                }
                _state.value = _state.value.copy(
                    op = OpUiState.Ok(
                        op = op,
                        label = label,
                        ledger = outcome.ledger,
                        txHash = outcome.txHashHex,
                        proveMs = outcome.proveMs,
                        note = outcome.detail,
                    ),
                    amountInput = if (op == "deposit" || op == "confidential_transfer") {
                        ""
                    } else {
                        _state.value.amountInput
                    },
                )
                after?.invoke()
                refreshStatus()
            } catch (e: Exception) {
                Log.e(TAG, "$op failed", e)
                _state.value = _state.value.copy(
                    op = OpUiState.Failed(op, label, e.message ?: e.javaClass.simpleName),
                )
            }
        }
    }

    companion object {
        private const val TAG = "SobreSpike"

        fun factory(app: Application, prover: ProverViewModel): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    SobreViewModel(app, prover.wallet) as T
            }
    }
}
