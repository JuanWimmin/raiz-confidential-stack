package xyz.raiz.sobre.wallet

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import java.util.Base64

/**
 * Key custody (project decision, non-negotiable): the Ed25519 seed and the
 * confidential-token scalar live ONLY here, in EncryptedSharedPreferences
 * (AES256-GCM values under an Android-Keystore-backed master key). The WebView
 * receives the scalar per proving call and never persists it; the Ed25519 seed
 * never crosses into JS at all.
 *
 * Testnet demo custody: one account per install, created lazily, friendbot
 * play-money. API verified against security-crypto 1.1.0 (javap, 2026-08-03).
 */
class WalletStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** 32-byte Ed25519 seed; generated once per install. */
    @Synchronized
    fun getOrCreateSeed(): ByteArray {
        prefs.getString(KEY_SEED, null)?.let { return Base64.getDecoder().decode(it) }
        val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
        check(prefs.edit().putString(KEY_SEED, Base64.getEncoder().encodeToString(seed)).commit()) {
            "failed to persist wallet seed" // commit(), not apply(): losing this key = losing the account
        }
        return seed
    }

    /**
     * Confidential-token secret scalar as 0x-hex, generated once. Top 3 bits
     * cleared -> uniform over [0, 2^253), strictly below the Grumpkin scalar
     * field modulus (~2^253.6); 253 bits of entropy, no rejection loop needed.
     */
    @Synchronized
    fun getOrCreateConfSk(): String {
        prefs.getString(KEY_CONF_SK, null)?.let { return it }
        val b = ByteArray(32).also { SecureRandom().nextBytes(it) }
        b[0] = (b[0].toInt() and 0x1F).toByte()
        val hex = "0x" + b.joinToString("") { "%02x".format(it) }
        check(prefs.edit().putString(KEY_CONF_SK, hex).commit()) {
            "failed to persist confidential scalar"
        }
        return hex
    }

    private companion object {
        const val FILE_NAME = "sobre_wallet_keys"
        const val KEY_SEED = "ed25519_seed_b64"
        const val KEY_CONF_SK = "ct_conf_sk_hex"
    }
}
