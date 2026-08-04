package xyz.raiz.sobre.data

import xyz.raiz.sobre.wallet.StrKey
import java.math.BigInteger
import java.util.Base64

/**
 * Minimal, dependency-free SCVal (Soroban XDR) decoder for contract event
 * topics and values, as returned base64-encoded by Raiz Memory's `/events`
 * (and by the RPC's `getEvents`, same field names).
 *
 * WHY THIS EXISTS: the app has no Stellar SDK (Session 5 decision — custody and
 * submission are hand-rolled in `xyz.raiz.sobre.wallet`), and the timeline's
 * "quién" lives inside those base64 topics. RAÍZ's own `data/stellar/ScvalParse.kt`
 * is not reusable: it is a thin wrapper over the Soneso SDK.
 *
 * DISCRIMINANTS: taken from the real XDR definitions, not from memory —
 * `@stellar/stellar-base@14.1.0` `lib/generated/curr_generated.js`
 * (vendored under /vendor/stellar-confidential-token-demo/node_modules):
 *   ScValType    :7690-7712   bool 0, void 1, error 2, u32 3, i32 4, u64 5,
 *                             i64 6, timepoint 7, duration 8, u128 9, i128 10,
 *                             u256 11, i256 12, bytes 13, string 14, symbol 15,
 *                             vec 16, map 17, address 18, contractInstance 19,
 *                             ledgerKeyContractInstance 20, ledgerKeyNonce 21
 *   ScAddressType:7894-7898   account 0, contract 1, muxedAccount 2,
 *                             claimableBalance 3, liquidityPool 4
 *   Int128Parts  :7821        { int64 hi; uint64 lo }
 *   UInt128Parts :7811        { uint64 hi; uint64 lo }
 *   Int256Parts  :7845        { int64 hiHi; uint64 hiLo; uint64 loHi; uint64 loLo }
 *   ScError      :7793-7801   union on ScErrorType; every arm is 4 bytes
 *                             (contractCode: Uint32 | code: ScErrorCode)
 * StrKey version bytes from the same package's `lib/strkey.js:20,32`:
 *   ed25519PublicKey = 6 << 3 ('G'), contract = 2 << 3 ('C').
 *
 * VERIFIED AGAINST REAL DATA (2026-08-03), not against assumptions: every one
 * of the 23 events then held by Raiz Memory for goal_meta
 * (CBNVY2AA…IQAZ) and our CT wrapper (CBWSANZN…DHAT) round-trips through this
 * decoder and consumes its input byte-exactly. The shapes actually present are
 * symbol, u32, address(account|contract), string, bytes, i128 and map — every
 * one of them is covered by a fixture in `ScValTest`.
 *
 * XDR rules honored here: everything is big-endian; variable-length opaque,
 * string and symbol payloads are padded to a 4-byte boundary; `SCVec` and
 * `SCMap` are *optional pointers*, i.e. a 4-byte "present" flag precedes the
 * 4-byte element count (a merge event's value is `map, present=1, len=0`).
 *
 * Pure JVM (java.util.Base64, java.math.BigInteger) — no android.* import, so
 * it runs green in `testDebugUnitTest` without Robolectric.
 */
sealed interface ScVal {

    data class Bool(val value: Boolean) : ScVal
    data object Void : ScVal

    /** SCV_ERROR: `type` is ScErrorType, `code` is the 4-byte arm payload. */
    data class Err(val type: Long, val code: Long) : ScVal

    data class U32(val value: Long) : ScVal
    data class I32(val value: Int) : ScVal
    data class U64(val value: BigInteger) : ScVal
    data class I64(val value: Long) : ScVal
    data class Timepoint(val value: BigInteger) : ScVal
    data class Duration(val value: BigInteger) : ScVal
    data class U128(val value: BigInteger) : ScVal
    data class I128(val value: BigInteger) : ScVal
    data class U256(val value: BigInteger) : ScVal
    data class I256(val value: BigInteger) : ScVal

    /** SCV_BYTES, carried as lowercase hex so the class stays a sane `data class`. */
    data class Bytes(val hex: String) : ScVal

    data class Str(val value: String) : ScVal
    data class Sym(val value: String) : ScVal

    data class Vec(val items: List<ScVal>) : ScVal
    data class Map(val entries: List<MapEntry>) : ScVal

    /** SCV_ADDRESS already rendered as a strkey: `G…` (account) or `C…` (contract). */
    data class Addr(val value: String) : ScVal

    /**
     * A topic/value we could not decode. Never thrown at the caller by
     * [decodeTopics] / [decodeOrNull] — an unknown shape must degrade the row,
     * never crash the timeline.
     */
    data class Undecodable(val base64: String, val reason: String) : ScVal

    data class MapEntry(val key: ScVal, val value: ScVal)

    companion object {
        /** Decode one base64 SCVal. Throws [ScValDecodeException] on anything unexpected. */
        fun decode(base64: String): ScVal {
            val raw = try {
                Base64.getDecoder().decode(base64.trim())
            } catch (e: IllegalArgumentException) {
                throw ScValDecodeException("not valid base64: ${e.message}", base64)
            }
            val reader = XdrReader(raw)
            val value = try {
                reader.scVal()
            } catch (e: ScValDecodeException) {
                throw ScValDecodeException(e.rawMessage, base64)
            } catch (e: IndexOutOfBoundsException) {
                throw ScValDecodeException("truncated XDR (${raw.size} bytes)", base64)
            }
            if (reader.remaining != 0) {
                // Every real sample consumes its input exactly; leftover bytes mean
                // we mis-decoded something, which is worth surfacing loudly.
                throw ScValDecodeException(
                    "trailing bytes after SCVal: ${reader.remaining} of ${raw.size}",
                    base64,
                )
            }
            return value
        }

        /** [decode], or null when the bytes are not a shape we know. */
        fun decodeOrNull(base64: String): ScVal? = try {
            decode(base64)
        } catch (e: ScValDecodeException) {
            null
        }

        /**
         * Decode a whole `topic` array. Never throws: an entry we cannot read
         * becomes [Undecodable] so the caller can still see the ones we can.
         */
        fun decodeTopics(topics: List<String>): List<ScVal> = topics.map { t ->
            try {
                decode(t)
            } catch (e: ScValDecodeException) {
                Undecodable(t, e.rawMessage)
            }
        }
    }
}

/** Raised by [ScVal.decode]; carries the offending base64 for the friction report. */
class ScValDecodeException(
    val rawMessage: String,
    val base64: String? = null,
) : IllegalArgumentException(
    if (base64 == null) rawMessage else "$rawMessage (input: ${base64.take(120)})",
)

// ---------------------------------------------------------------------------
// Convenience accessors — these keep the timeline mapper readable.
// ---------------------------------------------------------------------------

/** The symbol text, or null if this is not an SCV_SYMBOL. */
val ScVal.asSymbol: String? get() = (this as? ScVal.Sym)?.value

/** The string text, or null if this is not an SCV_STRING. */
val ScVal.asString: String? get() = (this as? ScVal.Str)?.value

/** Symbol or string text — the two shapes a human-readable topic can take. */
val ScVal.asText: String? get() = asSymbol ?: asString

/** The strkey (`G…`/`C…`), or null if this is not an SCV_ADDRESS. */
val ScVal.asAddress: String? get() = (this as? ScVal.Addr)?.value

/** The u32 value, or null if this is not an SCV_U32. */
val ScVal.asU32: Long? get() = (this as? ScVal.U32)?.value

/** Any integer variant as a [BigInteger] (u32/i32/u64/i64/u128/i128/u256/i256/timepoint/duration). */
val ScVal.asBigInteger: BigInteger?
    get() = when (this) {
        is ScVal.U32 -> BigInteger.valueOf(value)
        is ScVal.I32 -> BigInteger.valueOf(value.toLong())
        is ScVal.U64 -> value
        is ScVal.I64 -> BigInteger.valueOf(value)
        is ScVal.Timepoint -> value
        is ScVal.Duration -> value
        is ScVal.U128 -> value
        is ScVal.I128 -> value
        is ScVal.U256 -> value
        is ScVal.I256 -> value
        else -> null
    }

/** Map lookup by symbol key — CT event values are all `map { symbol -> value }`. */
operator fun ScVal.Map.get(symbolKey: String): ScVal? =
    entries.firstOrNull { it.key.asSymbol == symbolKey }?.value

/** [get] on any ScVal: null unless this is a map holding that symbol key. */
fun ScVal.mapField(symbolKey: String): ScVal? = (this as? ScVal.Map)?.get(symbolKey)

// ---------------------------------------------------------------------------

private class XdrReader(private val b: ByteArray) {
    private var o = 0
    val remaining: Int get() = b.size - o

    fun scVal(): ScVal = when (val t = u32().toInt()) {
        0 -> ScVal.Bool(u32() != 0L)
        1 -> ScVal.Void
        2 -> ScVal.Err(type = u32(), code = u32())
        3 -> ScVal.U32(u32())
        4 -> ScVal.I32(u32().toInt())
        5 -> ScVal.U64(u64Unsigned())
        6 -> ScVal.I64(i64())
        7 -> ScVal.Timepoint(u64Unsigned())
        8 -> ScVal.Duration(u64Unsigned())
        // UInt128Parts { uint64 hi; uint64 lo } -> hi * 2^64 + lo
        9 -> ScVal.U128(u64Unsigned().shiftLeft(64).add(u64Unsigned()))
        // Int128Parts { int64 hi; uint64 lo } -> hi * 2^64 + lo (hi signed)
        10 -> ScVal.I128(BigInteger.valueOf(i64()).shiftLeft(64).add(u64Unsigned()))
        11 -> ScVal.U256(parts256(signedHigh = false))
        12 -> ScVal.I256(parts256(signedHigh = true))
        13 -> ScVal.Bytes(opaque(u32().toInt()).toHex())
        14 -> ScVal.Str(String(opaque(u32().toInt()), Charsets.UTF_8))
        15 -> ScVal.Sym(String(opaque(u32().toInt()), Charsets.UTF_8))
        16 -> ScVal.Vec(optionalList())
        17 -> ScVal.Map(
            if (u32() == 0L) emptyList() else List(u32().toInt()) {
                ScVal.MapEntry(key = scVal(), value = scVal())
            },
        )
        18 -> ScVal.Addr(address())
        // 19/20/21 are ledger-key / instance shapes that never appear in a
        // contract event; refusing beats guessing a byte length.
        else -> fail("unsupported ScValType discriminant $t at offset ${o - 4}")
    }

    private fun optionalList(): List<ScVal> =
        if (u32() == 0L) emptyList() else List(u32().toInt()) { scVal() }

    private fun address(): String = when (val at = u32().toInt()) {
        // SC_ADDRESS_TYPE_ACCOUNT -> PublicKey union (PUBLIC_KEY_TYPE_ED25519 = 0) + 32 bytes
        0 -> {
            val keyType = u32().toInt()
            if (keyType != 0) fail("unsupported PublicKey type $keyType (only ed25519 = 0)")
            // Reuses the strkey encoder the wallet already owns and that is
            // byte-verified against @stellar/stellar-sdk in StellarAccountTest.
            StrKey.encodeEd25519PublicKey(fixed(32))
        }
        // SC_ADDRESS_TYPE_CONTRACT -> 32-byte contract id, no inner union
        1 -> encodeContractStrKey(fixed(32))
        else -> fail("unsupported ScAddressType $at (only account = 0 and contract = 1)")
    }

    private fun parts256(signedHigh: Boolean): BigInteger {
        val hiHi = if (signedHigh) BigInteger.valueOf(i64()) else u64Unsigned()
        val hiLo = u64Unsigned()
        val loHi = u64Unsigned()
        val loLo = u64Unsigned()
        return hiHi.shiftLeft(192)
            .add(hiLo.shiftLeft(128))
            .add(loHi.shiftLeft(64))
            .add(loLo)
    }

    private fun u32(): Long {
        need(4)
        val v = ((b[o].toLong() and 0xFF) shl 24) or
            ((b[o + 1].toLong() and 0xFF) shl 16) or
            ((b[o + 2].toLong() and 0xFF) shl 8) or
            (b[o + 3].toLong() and 0xFF)
        o += 4
        return v
    }

    private fun i64(): Long {
        need(8)
        var v = 0L
        for (i in 0 until 8) v = (v shl 8) or (b[o + i].toLong() and 0xFF)
        o += 8
        return v
    }

    /** uint64 as a non-negative BigInteger (Long would wrap above 2^63). */
    private fun u64Unsigned(): BigInteger {
        need(8)
        val slice = b.copyOfRange(o, o + 8)
        o += 8
        return BigInteger(1, slice)
    }

    /** Variable-length opaque/string/symbol body: [n] bytes, padded to 4. */
    private fun opaque(n: Int): ByteArray {
        if (n < 0 || n > remaining) fail("length $n exceeds remaining $remaining")
        val out = fixed(n)
        val pad = (4 - (n % 4)) % 4
        need(pad)
        o += pad
        return out
    }

    private fun fixed(n: Int): ByteArray {
        need(n)
        val out = b.copyOfRange(o, o + n)
        o += n
        return out
    }

    private fun need(n: Int) {
        if (remaining < n) fail("truncated: need $n more bytes, have $remaining")
    }

    private fun fail(msg: String): Nothing = throw ScValDecodeException(msg)
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

/**
 * SEP-23 strkey for a contract id (`C…`).
 *
 * The CRC16-XModem step is [StrKey.crc16Xmodem] — the wallet's, not a copy.
 * The base32 alphabet loop below IS a second copy of ~10 lines: `StrKey.base32`
 * is `private` and `wallet/StellarAccount.kt` belongs to another agent's file
 * lane this session, so widening it there was not an option. If the two files
 * ever merge, delete this and call `StrKey.base32`.
 *
 * Version byte 2 << 3 = 0x10 -> 'C' (`@stellar/stellar-base` `lib/strkey.js:32`).
 * Verified against real data: the wrapper's `underlying_asset_set` value decodes
 * to CDLZFC3S…CYSC (the XLM SAC) and `verifier_set` to CBFCYFND…3UIA, both
 * matching `scripts/ct-flow/deployment.json` / `CtConfig`.
 */
private fun encodeContractStrKey(raw: ByteArray): String {
    require(raw.size == 32) { "contract id must be 32 bytes" }
    val payload = ByteArray(33)
    payload[0] = (2 shl 3).toByte() // 16 -> 'C'
    raw.copyInto(payload, 1)
    val crc = StrKey.crc16Xmodem(payload)
    val full = payload + byteArrayOf((crc and 0xFF).toByte(), ((crc shr 8) and 0xFF).toByte())

    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    val sb = StringBuilder((full.size * 8 + 4) / 5)
    var buffer = 0
    var bits = 0
    for (byte in full) {
        buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
        bits += 8
        while (bits >= 5) {
            sb.append(alphabet[(buffer shr (bits - 5)) and 0x1F])
            bits -= 5
        }
    }
    if (bits > 0) sb.append(alphabet[(buffer shl (5 - bits)) and 0x1F])
    return sb.toString()
}
