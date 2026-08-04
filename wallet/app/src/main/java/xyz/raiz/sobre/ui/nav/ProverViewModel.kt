package xyz.raiz.sobre.ui.nav

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import xyz.raiz.sobre.prover.ProverWebViewBridge
import xyz.raiz.sobre.wallet.CtWallet
import xyz.raiz.sobre.wallet.WalletStore

/**
 * Owns the single [ProverWebViewBridge] of the app — and it is a ViewModel for
 * one reason, documented in raiz-reuse-plan §5 risk 3: a `remember` or a
 * `DisposableEffect` disposing this bridge on recomposition or rotation kills a
 * 10-30 s proof mid-flight. It cost this project two fake "timeouts" already.
 *
 * Lifetime = the Activity's ViewModelStore. Constructed on the main thread
 * during composition (WebView requirement), destroyed only in [onCleared].
 * Rotation does not reach it at all: the manifest keeps
 * `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`, so the
 * Activity is never recreated.
 *
 * Both feature ViewModels take the objects held here — one WebView, one wallet,
 * one account. Never construct a second one.
 */
class ProverViewModel(app: Application) : AndroidViewModel(app) {

    val bridge = ProverWebViewBridge(app)
    val wallet = CtWallet(bridge, WalletStore(app))

    sealed interface Estado {
        data object Arrancando : Estado
        data class Listo(val bootMs: Long) : Estado
        /** Verbatim failure text — friction-report material, never paraphrased. */
        data class Falló(val message: String) : Estado
    }

    private val _estado = MutableStateFlow<Estado>(Estado.Arrancando)
    val estado: StateFlow<Estado> = _estado.asStateFlow()

    /** Rolling mirror of the page console: this device family suppresses logcat. */
    private val _console = MutableStateFlow<List<String>>(emptyList())
    val console: StateFlow<List<String>> = _console.asStateFlow()

    init {
        bridge.consoleListener = { line ->
            _console.value = (_console.value + line).takeLast(CONSOLE_KEEP)
        }
        bridge.initialize()
        val t0 = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                bridge.awaitReady()
                _estado.value = Estado.Listo(System.currentTimeMillis() - t0)
            } catch (e: Exception) {
                Log.e(TAG, "prover init failed", e)
                _estado.value = Estado.Falló(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Suspends until the prover page is up; rethrows the typed failure. */
    suspend fun awaitReady() = bridge.awaitReady()

    override fun onCleared() {
        bridge.destroy()
        super.onCleared()
    }

    companion object {
        private const val TAG = ProverWebViewBridge.TAG
        private const val CONSOLE_KEEP = 40

        /** No Hilt in this app (raiz-reuse-plan §1.3): hand-built factory. */
        fun factory(app: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    ProverViewModel(app) as T
            }
    }
}
