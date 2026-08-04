package xyz.raiz.sobre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.raiz.sobre.wallet.CtConfig
import java.math.BigInteger

/**
 * ScVal decoding against REAL base64, captured on 2026-08-03 from the running
 * indexer:
 *
 *   curl "http://localhost:8091/events?contractId=CBNVY2AA…IQAZ&limit=20"   (goal_meta)
 *   curl "http://localhost:8091/events?contractId=CBWSANZN…DHAT&limit=20"   (our CT wrapper)
 *
 * Nothing here is synthetic. The expected strkeys are cross-checked against
 * `CtConfig` (the deployment's own source of truth), which is how we know the
 * decoder is right and not merely self-consistent.
 *
 * Offline by construction: java.util.Base64 + BigInteger only.
 */
class ScValTest {

    // ---- fixtures: topics --------------------------------------------------

    private val symGoal = "AAAADwAAAARnb2Fs"
    private val symCreated = "AAAADwAAAAdjcmVhdGVkAA=="
    private val symHarvest = "AAAADwAAAAdoYXJ2ZXN0AA=="
    private val symTransfer = "AAAADwAAAAh0cmFuc2Zlcg=="
    private val symMerge = "AAAADwAAAAVtZXJnZQAAAA=="
    private val symRegister = "AAAADwAAAAhyZWdpc3Rlcg=="
    private val symDeposit = "AAAADwAAAAdkZXBvc2l0AA=="
    private val symUnderlyingAssetSet = "AAAADwAAABR1bmRlcmx5aW5nX2Fzc2V0X3NldA=="

    private val u32Zero = "AAAAAwAAAAA="
    private val u32One = "AAAAAwAAAAE="

    /** The goal's CT account (`CtConfig.GOAL_ACCOUNT`), auditor id 1. */
    private val addrGoal = "AAAAEgAAAAAAAAAAEvuBf9dbzjWEzu6G+9lP70ilq7oGZDnR+PZNbGiJloY="

    /** The CLI contributor from the Session-4 flow. */
    private val addrCliContributor = "AAAAEgAAAAAAAAAA6Ti8uQL3CJxY58HlAR4iuXTPO+p6iyP4bupjnsDpf/E="

    /** The phone's own CT account (Session 5, M1). */
    private val addrPhone = "AAAAEgAAAAAAAAAA6DtKrz3eRKOQl02QdQpR3w/oZ9zWQD8NluVH8V4DXGE="

    /** Goal id 0's account — the dead placeholder goal. */
    private val addrPlaceholderGoal = "AAAAEgAAAAAAAAAAAM8EIPTr2qDd73QWLuFScmqHsmkDPC4w753Xxn7SdPs="

    // ---- fixtures: values --------------------------------------------------

    private val strHarvestMemo =
        "AAAADgAAADBwcmltZXJhIGNvc2VjaGEgKGdvYWwtZmxvdyAyMDI2LTA4LTAzVDE1OjI5OjQ4Wik="

    private val mapEmpty = "AAAAEQAAAAEAAAAA"
    private val mapAuditorIdZero = "AAAAEQAAAAEAAAABAAAADwAAAAphdWRpdG9yX2lkAAAAAAADAAAAAA=="
    private val mapAuditorIdOne = "AAAAEQAAAAEAAAABAAAADwAAAAphdWRpdG9yX2lkAAAAAAADAAAAAQ=="

    /** deposit of 100 XLM = 1_000_000_000 stroops, as an i128. */
    private val mapAmount100Xlm =
        "AAAAEQAAAAEAAAABAAAADwAAAAZhbW91bnQAAAAAAAoAAAAAAAAAAAAAAAA7msoA"

    /** deposit of 10 XLM = 100_000_000 stroops (the phone's, Session 5). */
    private val mapAmount10Xlm =
        "AAAAEQAAAAEAAAABAAAADwAAAAZhbW91bnQAAAAAAAoAAAAAAAAAAAAAAAAF9eEA"

    private val mapUnderlyingAsset =
        "AAAAEQAAAAEAAAABAAAADwAAABB1bmRlcmx5aW5nX2Fzc2V0AAAAEgAAAAHXkotywnA8z+r365/0701QSlWouXn8m0UOoshCtNHOYQ=="

    private val mapVerifier =
        "AAAAEQAAAAEAAAABAAAADwAAAAh2ZXJpZmllcgAAABIAAAABSiwVo+ck2D1Mg04dqvoX1cp20q0YVON8dXnsAbqQBM0="

    private val mapAuditor =
        "AAAAEQAAAAEAAAABAAAADwAAAAdhdWRpdG9yAAAAABIAAAABaSv0PfKD/ZQAQg9volLJDHGqE3WYv/Fhe61jCIUmXrM="

    private val mapAddressAsField =
        "AAAAEQAAAAEAAAABAAAADwAAABBhZGRyZXNzX2FzX2ZpZWxkAAAADQAAACAOLFdYVD+LEHYHHLFMseNeZ6hfOYsKHxL/1kKuDGVKPw=="

    /** The real confidential-transfer value: 8 ciphertext fields, ZERO amount. */
    private val mapTransferCiphertext =
        "AAAAEQAAAAEAAAAIAAAADwAAAAdiX2F1ZF9zAAAAAA0AAAAgHwywlNzWUBd0zI8L4M1urtlo5fvJE1ncK8nyCQy/9cUAAAAP" +
            "AAAAB2JfdGlsZGUAAAAADQAAACAio690X7SMODSrJJd+c9K/UG0NtdwEWLHDFHnI/b0IJQAAAA8AAAAHcl9hdWRfcgAAAAAN" +
            "AAAAIBSctFrCm2514/jq71MMban5+UwZfJdr98DKjbToaXYcAAAADwAAAANyX2UAAAAADQAAAEAsX71oJdlsD78c/TWeq32U" +
            "MlL5jYGOdyd+Dvj0OVUOcAy7NLODbouq28bz5EaY/saKY7+Xut6ALNlw7KzuYPvhAAAADwAAAAVzaWdtYQAAAAAAAA0AAAAg" +
            "AMomTk9jaBxFbZREx6lsTq/djc/AOXGJCH77y7ezYQQAAAAPAAAAB3ZfYXVkX3IAAAAADQAAACAV9+ZhmEP6U+KpHcs0Wy84" +
            "N1SCUsH/shQEZD7+sns9kwAAAA8AAAAHdl9hdWRfcwAAAAANAAAAIBR75lCb9fuwHnYOy4Xf9Y035z3xzikFQxrLdgs8yszd" +
            "AAAADwAAAAd2X3RpbGRlAAAAAA0AAAAgF6BxTB2Qns0LbkZW3QMWrBRt+2xSfrS08dUV6FuK9Og="

    // ---- symbols -----------------------------------------------------------

    @Test
    fun `decodes symbol topics, padded and unpadded`() {
        // "goal" is 4 bytes (no padding); "harvest" is 7 (1 byte of padding);
        // "underlying_asset_set" is 20 (none) — all three paths exercised.
        assertEquals(ScVal.Sym("goal"), ScVal.decode(symGoal))
        assertEquals(ScVal.Sym("created"), ScVal.decode(symCreated))
        assertEquals(ScVal.Sym("harvest"), ScVal.decode(symHarvest))
        assertEquals(ScVal.Sym("transfer"), ScVal.decode(symTransfer))
        assertEquals(ScVal.Sym("merge"), ScVal.decode(symMerge))
        assertEquals(ScVal.Sym("register"), ScVal.decode(symRegister))
        assertEquals(ScVal.Sym("deposit"), ScVal.decode(symDeposit))
        assertEquals(ScVal.Sym("underlying_asset_set"), ScVal.decode(symUnderlyingAssetSet))
    }

    // ---- u32 ---------------------------------------------------------------

    @Test
    fun `decodes u32 goal ids`() {
        assertEquals(ScVal.U32(0L), ScVal.decode(u32Zero))
        assertEquals(ScVal.U32(1L), ScVal.decode(u32One))
        assertEquals(1L, ScVal.decode(u32One).asU32)
    }

    // ---- addresses ---------------------------------------------------------

    @Test
    fun `decodes account addresses to G strkeys`() {
        // Cross-checked against CtConfig, i.e. against the real deployment.
        assertEquals(CtConfig.GOAL_ACCOUNT, ScVal.decode(addrGoal).asAddress)
        assertEquals(
            "GDUTRPFZAL3QRHCY47A6KAI6EK4XJTZ35J5IWI7YN3VGHHWA5F77DJ2I",
            ScVal.decode(addrCliContributor).asAddress,
        )
        assertEquals(
            "GDUDWSVPHXPEJI4QS5GZA5IKKHPQ72DH3TLEAPYNS3SUP4K6ANOGDUXL",
            ScVal.decode(addrPhone).asAddress,
        )
        assertEquals(
            "GAAM6BBA6TV5VIG5552BMLXBKJZGVB5SNEBTYLRQ56O5PRT62J2PXXLJ",
            ScVal.decode(addrPlaceholderGoal).asAddress,
        )
    }

    @Test
    fun `decodes contract addresses to C strkeys`() {
        // These come out of map values, and each one must equal the id we
        // actually deployed (CtConfig) or the well-known XLM SAC.
        assertEquals(
            "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
            ScVal.decode(mapUnderlyingAsset).mapField("underlying_asset")?.asAddress,
        )
        assertEquals(CtConfig.VERIFIER, ScVal.decode(mapVerifier).mapField("verifier")?.asAddress)
        assertEquals(CtConfig.AUDITOR, ScVal.decode(mapAuditor).mapField("auditor")?.asAddress)
    }

    // ---- strings -----------------------------------------------------------

    @Test
    fun `decodes the harvest memo string`() {
        assertEquals(
            "primera cosecha (goal-flow 2026-08-03T15:29:48Z)",
            ScVal.decode(strHarvestMemo).asString,
        )
    }

    // ---- maps --------------------------------------------------------------

    @Test
    fun `decodes the empty map that merge events carry`() {
        val v = ScVal.decode(mapEmpty)
        assertEquals(ScVal.Map(emptyList()), v)
        assertNull(v.mapField("anything"))
    }

    @Test
    fun `decodes auditor_id out of register values`() {
        assertEquals(0L, ScVal.decode(mapAuditorIdZero).mapField("auditor_id")?.asU32)
        assertEquals(1L, ScVal.decode(mapAuditorIdOne).mapField("auditor_id")?.asU32)
    }

    @Test
    fun `decodes i128 deposit amounts in stroops`() {
        assertEquals(
            BigInteger.valueOf(1_000_000_000L), // 100 XLM
            ScVal.decode(mapAmount100Xlm).mapField("amount")?.asBigInteger,
        )
        assertEquals(
            BigInteger.valueOf(100_000_000L), // 10 XLM
            ScVal.decode(mapAmount10Xlm).mapField("amount")?.asBigInteger,
        )
    }

    @Test
    fun `decodes 32-byte bytes fields`() {
        assertEquals(
            ScVal.Bytes("0e2c5758543f8b1076071cb14cb1e35e67a85f398b0a1f12ffd642ae0c654a3f"),
            ScVal.decode(mapAddressAsField).mapField("address_as_field"),
        )
    }

    /**
     * THE LOAD-BEARING ASSERTION of the whole product: a confidential transfer
     * publishes eight ciphertext fields and no amount. If this ever fails,
     * "los aportes son secretos" stopped being true.
     */
    @Test
    fun `a confidential transfer value carries ciphertext and no amount`() {
        val v = ScVal.decode(mapTransferCiphertext)
        val map = v as ScVal.Map
        assertEquals(
            listOf("b_aud_s", "b_tilde", "r_aud_r", "r_e", "sigma", "v_aud_r", "v_aud_s", "v_tilde"),
            map.entries.map { it.key.asSymbol },
        )
        assertNull(map["amount"])
        // r_e is the 64-byte field; the rest are 32-byte Grumpkin points.
        assertEquals(64, (map["r_e"] as ScVal.Bytes).hex.length / 2)
        assertEquals(32, (map["sigma"] as ScVal.Bytes).hex.length / 2)
    }

    // ---- robustness --------------------------------------------------------

    @Test
    fun `decodeTopics never throws and marks what it cannot read`() {
        val decoded = ScVal.decodeTopics(listOf(symGoal, "not base64 at all!!", "AAAAFA=="))
        assertEquals(ScVal.Sym("goal"), decoded[0])
        assertTrue(decoded[1] is ScVal.Undecodable)
        // discriminant 20 = SCV_LEDGER_KEY_CONTRACT_INSTANCE: never in an event.
        assertTrue(decoded[2] is ScVal.Undecodable)
    }

    @Test
    fun `rejects trailing bytes instead of silently mis-decoding`() {
        // A valid u32(1) with four extra bytes glued on.
        val padded = java.util.Base64.getEncoder().encodeToString(
            java.util.Base64.getDecoder().decode(u32One) + byteArrayOf(0, 0, 0, 9),
        )
        assertNull(ScVal.decodeOrNull(padded))
    }

    @Test
    fun `helpers return null for the wrong shape`() {
        val sym = ScVal.decode(symGoal)
        assertNull(sym.asAddress)
        assertNull(sym.asU32)
        assertNull(sym.asString)
        assertEquals("goal", sym.asText)
        assertEquals("primera cosecha (goal-flow 2026-08-03T15:29:48Z)", ScVal.decode(strHarvestMemo).asText)
    }
}
