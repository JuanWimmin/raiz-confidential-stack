package xyz.raiz.sobre.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

/**
 * Centralized builder/opener for Stellar Expert (testnet) URLs, adopted from
 * RAIZ's `com.raiz.app.ui.util.StellarExpert`.
 *
 * Typical use:
 *   StellarExpert.open(context, StellarExpert.txUrl(hash))
 *   StellarExpert.open(context, StellarExpert.addressUrl(address))
 *   StellarExpert.open(context, StellarExpert.contractUrl(contractId))
 *
 * Nothing here knows about Compose or ViewModels — it is pure navigation to the
 * external explorer.
 *
 * DUPLICATION NOTE (for whoever owns wallet/): [BASE] + [txUrl] produce exactly
 * the string `CtConfig.EXPLORER_TX` + hash, which `CtWallet.TxOutcome.explorerUrl`
 * already builds. Two sources of truth for one URL. Collapsing them means
 * editing CtConfig/CtWallet, which is outside this agent's file lane — flagged
 * here so the owner can delete one side.
 */
object StellarExpert {

    /** Testnet explorer base URL. */
    const val BASE = "https://stellar.expert/explorer/testnet"

    /**
     * URL of a Stellar transaction by hash (64 hex chars, as returned by
     * SorobanRpc.sendTransaction).
     */
    fun txUrl(hash: String): String = "$BASE/tx/$hash"

    /**
     * URL of an account or contract, routed by address prefix:
     *   - C… → `/contract/` (Soroban contract)
     *   - G… → `/account/`  (classic Ed25519 account)
     */
    fun addressUrl(addr: String): String =
        "$BASE/${if (addr.startsWith("C")) "contract" else "account"}/$addr"

    /**
     * Explicit contract URL (C…). Semantic alias of [addressUrl] for the call
     * sites where we know the address is a contract.
     */
    fun contractUrl(c: String): String = "$BASE/contract/$c"

    /**
     * Fires a VIEW intent at [url] using the system browser. Wrapped in
     * try/catch: some devices have no default browser and would throw
     * ActivityNotFoundException.
     */
    fun open(context: Context, url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (e: Exception) {
            Log.w("SobreSpike", "StellarExpert: could not open browser — $url: ${e.message}")
        }
    }
}
