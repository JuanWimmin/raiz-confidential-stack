package xyz.raiz.sobre.wallet

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64

/**
 * Kotlin-side Stellar account: Ed25519 keys, G-address strkey, and byte-level
 * transaction signing. This is the custody/signing half of the Session-5
 * architecture decision (WebView proves and builds; Kotlin signs and submits).
 *
 * The signing recipe operates directly on the raw XDR of an unsigned
 * TransactionEnvelope(ENVELOPE_TYPE_TX):
 *
 *   envelope := 00 00 00 02 || tx || 00 00 00 00          (0 signatures)
 *   payload  := sha256(networkPassphrase) || 00 00 00 02 || tx
 *   txHash   := sha256(payload)
 *   signed   := 00 00 00 02 || tx || 00 00 00 01 || hint || 00 00 00 40 || sig
 *
 * VERIFIED byte-for-byte against @stellar/stellar-sdk 14.6.1 (the vendor-locked
 * SDK): wallet/tools/gen-sign-fixture.mjs asserts the manual construction
 * equals TransactionBuilder + Keypair.sign output, and emits the fixture that
 * StellarAccountTest replays against this class. Ed25519 (RFC 8032) is
 * deterministic, so equality is byte-exact.
 *
 * Ed25519 backend: net.i2p.crypto:eddsa 0.3.0 — Android Keystore has no
 * Ed25519 (checked API 33/35: KeyProperties knows only RSA/EC/AES/HMAC), and
 * the platform's Conscrypt does not expose Signature("Ed25519"). This is the
 * same library java-stellar-sdk used for years; API verified against the
 * resolved jar (javap, 2026-08-03).
 */
class StellarAccount(seed: ByteArray) {

    init {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes (got ${seed.size})" }
    }

    private val privateKey = EdDSAPrivateKey(EdDSAPrivateKeySpec(seed, CURVE))

    /** 32-byte Ed25519 public key (the strkey payload). */
    val rawPublicKey: ByteArray = privateKey.abyte

    /** G… strkey (SEP-23 account id). */
    val accountId: String = StrKey.encodeEd25519PublicKey(rawPublicKey)

    data class SignedTx(val signedXdrBase64: String, val txHashHex: String)

    /**
     * Sign an unsigned, assembled transaction envelope for [networkPassphrase]
     * and return the signed envelope plus the tx hash (the id the RPC reports).
     * Rejects anything that is not a zero-signature ENVELOPE_TYPE_TX envelope —
     * fee-bump or pre-signed input means something upstream went wrong.
     */
    fun signEnvelope(unsignedXdrBase64: String, networkPassphrase: String): SignedTx {
        val env = Base64.getDecoder().decode(unsignedXdrBase64)
        require(env.size > 8) { "envelope too short (${env.size} bytes)" }
        require(readUInt32(env, 0) == 2) {
            "expected ENVELOPE_TYPE_TX (2), got discriminant ${readUInt32(env, 0)}"
        }
        require(readUInt32(env, env.size - 4) == 0) {
            "expected an UNSIGNED envelope (signature count 0, got ${readUInt32(env, env.size - 4)})"
        }

        // payload = networkId || (envelope minus its trailing signature count):
        // the leading discriminant doubles as the TaggedTransaction union tag.
        val body = env.copyOfRange(0, env.size - 4)
        val networkId = sha256(networkPassphrase.toByteArray(Charsets.UTF_8))
        val txHash = sha256(networkId + body)

        val engine = EdDSAEngine(MessageDigest.getInstance(CURVE.hashAlgorithm))
        engine.initSign(privateKey)
        val signature = engine.signOneShot(txHash)
        check(signature.size == 64) { "ed25519 signature must be 64 bytes (got ${signature.size})" }

        val out = ByteArrayOutputStream(env.size + 76)
        out.write(body)
        out.write(byteArrayOf(0, 0, 0, 1)) // DecoratedSignature array, 1 entry
        out.write(rawPublicKey, 28, 4) // hint = last 4 bytes of the public key
        out.write(byteArrayOf(0, 0, 0, 0x40)) // opaque<64> length
        out.write(signature)

        return SignedTx(
            signedXdrBase64 = Base64.getEncoder().encodeToString(out.toByteArray()),
            txHashHex = txHash.joinToString("") { "%02x".format(it) },
        )
    }

    private companion object {
        val CURVE = EdDSANamedCurveTable.ED_25519_CURVE_SPEC

        fun sha256(data: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(data)

        fun readUInt32(b: ByteArray, off: Int): Int =
            ((b[off].toInt() and 0xFF) shl 24) or
                ((b[off + 1].toInt() and 0xFF) shl 16) or
                ((b[off + 2].toInt() and 0xFF) shl 8) or
                (b[off + 3].toInt() and 0xFF)
    }
}

/**
 * SEP-23 strkey encoding, account ids only: version byte 6<<3 ('G'),
 * CRC16-XModem checksum appended little-endian, RFC 4648 base32 without
 * padding (35 bytes = 280 bits = exactly 56 chars, no remainder).
 * Verified against @stellar/stellar-sdk Keypair.publicKey() in
 * StellarAccountTest (fixture from gen-sign-fixture.mjs).
 */
object StrKey {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun encodeEd25519PublicKey(raw: ByteArray): String {
        require(raw.size == 32) { "public key must be 32 bytes" }
        val payload = ByteArray(33)
        payload[0] = (6 shl 3).toByte() // 48 -> 'G'
        raw.copyInto(payload, 1)
        val crc = crc16Xmodem(payload)
        val full = payload + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())
        return base32(full)
    }

    private fun base32(data: ByteArray): String {
        val sb = StringBuilder((data.size * 8 + 4) / 5)
        var buffer = 0
        var bits = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                sb.append(ALPHABET[(buffer shr (bits - 5)) and 0x1F])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString()
    }

    internal fun crc16Xmodem(data: ByteArray): Int {
        var crc = 0
        for (b in data) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
                crc = crc and 0xFFFF
            }
        }
        return crc
    }
}
