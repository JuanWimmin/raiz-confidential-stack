package xyz.raiz.sobre.wallet

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import xyz.raiz.sobre.prover.ProverWebViewBridge

/**
 * The full on-device CT pipeline (Session 5 step 5 — milestone M1):
 *
 *   WebView (RaizChain)              Kotlin (this class)
 *   ---------------------            ------------------------------------
 *   prove (register/transfer)   ->   sign envelope hash   (StellarAccount)
 *   build + simulate + assemble ->   sendTransaction      (SorobanRpc)
 *   [no secrets persisted]           poll getTransaction  (SorobanRpc)
 *
 * Progress strings are honest UI states ("generando prueba…", "enviando…",
 * "confirmado ledger N") surfaced by the debug screen; [progress] may be
 * invoked from any thread.
 */
class CtWallet(
    private val bridge: ProverWebViewBridge,
    private val store: WalletStore,
    private val rpc: SorobanRpc = SorobanRpc(),
) {

    /** Lazily materialized so first use (button press) creates + funds it. */
    val account: StellarAccount by lazy { StellarAccount(store.getOrCreateSeed()) }

    data class TxOutcome(
        val method: String,
        val txHashHex: String?,
        val ledger: Long?,
        val alreadyDone: Boolean = false,
        val proveMs: Long = 0,
        val detail: String = "",
    ) {
        val explorerUrl: String? get() = txHashHex?.let { CtConfig.EXPLORER_TX + it }
    }

    /** register(account, auditor_id=0) — ZK proof generated in the WebView. */
    suspend fun register(progress: (String) -> Unit): TxOutcome {
        ensureFunded(progress)
        progress("generando prueba de registro… (~10 s en este teléfono)")
        val prep = bridge.chainCall(
            "prepareRegister",
            commonParams()
                .put("account", account.accountId)
                .put("sk", store.getOrCreateConfSk())
                .put("auditorId", CtConfig.CONTRIBUTOR_AUDITOR_ID)
                .toString(),
        )
        if (prep.optBoolean("alreadyRegistered")) {
            return TxOutcome("register", null, null, alreadyDone = true, detail = "cuenta ya registrada en el wrapper")
        }
        return signAndSubmit("register", prep, progress)
            .copy(proveMs = prep.optLong("proveMs") + prep.optLong("witnessMs"))
    }

    /** deposit(app, app, amount) — proof-free; amount is public by design. */
    suspend fun deposit(amountStroops: Long, progress: (String) -> Unit): TxOutcome {
        ensureFunded(progress)
        progress("construyendo transacción de depósito…")
        val prep = bridge.chainCall(
            "prepareDeposit",
            commonParams()
                .put("from", account.accountId)
                .put("to", account.accountId)
                .put("amountStroops", amountStroops.toString())
                .toString(),
        )
        return signAndSubmit("deposit", prep, progress)
    }

    /** merge(account) — proof-free ("cosechar"): receiving -> spendable. */
    suspend fun merge(progress: (String) -> Unit): TxOutcome {
        progress("construyendo transacción de merge…")
        val prep = bridge.chainCall("prepareMerge", commonParams().put("account", account.accountId).toString())
        return signAndSubmit("merge", prep, progress)
    }

    /** confidential_transfer(app -> goal, amount) — amount hidden on-chain. */
    suspend fun transferToGoal(amountStroops: Long, progress: (String) -> Unit): TxOutcome {
        progress("sincronizando estado confidencial + generando prueba… (~15 s)")
        val prep = bridge.chainCall(
            "prepareTransfer",
            commonParams()
                .put("from", account.accountId)
                .put("to", CtConfig.GOAL_ACCOUNT)
                .put("amountStroops", amountStroops.toString())
                .put("sk", store.getOrCreateConfSk())
                .put("fromLedger", CtConfig.DEPLOYED_AT_LEDGER)
                .toString(),
            timeoutMs = ProverWebViewBridge.CHAIN_TIMEOUT_MS,
        )
        return signAndSubmit("confidential_transfer", prep, progress)
            .copy(proveMs = prep.optLong("proveMs") + prep.optLong("witnessMs"))
    }

    /** Read-only: registered flag + locally decrypted balances (event replay). */
    suspend fun status(progress: (String) -> Unit): JSONObject {
        progress("consultando estado on-chain…")
        return bridge.chainCall(
            "status",
            commonParams()
                .put("account", account.accountId)
                .put("sk", store.getOrCreateConfSk())
                .put("fromLedger", CtConfig.DEPLOYED_AT_LEDGER)
                .toString(),
        )
    }

    // -----------------------------------------------------------------------

    private suspend fun ensureFunded(progress: (String) -> Unit) = withContext(Dispatchers.IO) {
        progress("fondeando cuenta con friendbot (idempotente)…")
        rpc.friendbotFund(account.accountId)
    }

    private fun commonParams(): JSONObject = JSONObject()
        .put("rpcUrl", CtConfig.RPC_URL)
        .put("passphrase", CtConfig.PASSPHRASE)
        .put("token", CtConfig.TOKEN)
        .put("verifier", CtConfig.VERIFIER)
        .put("auditor", CtConfig.AUDITOR)

    private suspend fun signAndSubmit(
        method: String,
        prep: JSONObject,
        progress: (String) -> Unit,
    ): TxOutcome = withContext(Dispatchers.IO) {
        val unsignedXdr = prep.optString("unsignedXdrBase64")
        check(unsignedXdr.isNotEmpty()) { "$method: no unsignedXdrBase64 in prepare result: $prep" }

        progress("firmando en Kotlin (la seed nunca sale del Keystore/ESP)…")
        val signed = account.signEnvelope(unsignedXdr, CtConfig.PASSPHRASE)

        progress("enviando a testnet… tx ${signed.txHashHex.take(8)}…")
        val send = rpc.sendTransaction(signed.signedXdrBase64)
        // The RPC recomputes the hash from our envelope — it agreeing with the
        // hash we computed locally is an end-to-end check of the signing path.
        if (send.hash.isNotEmpty() && !send.hash.equals(signed.txHashHex, ignoreCase = true)) {
            Log.w(TAG, "$method: RPC hash ${send.hash} != local ${signed.txHashHex}")
        }

        progress("confirmando (poll getTransaction)…")
        val res = rpc.pollUntilComplete(signed.txHashHex)
        Log.i(TAG, "$method SUCCESS tx ${signed.txHashHex} ledger ${res.ledger}")
        TxOutcome(method, signed.txHashHex, res.ledger, detail = "fee ${prep.optString("feeStroops")} stroops")
    }

    private companion object {
        const val TAG = "SobreSpike"
    }
}
