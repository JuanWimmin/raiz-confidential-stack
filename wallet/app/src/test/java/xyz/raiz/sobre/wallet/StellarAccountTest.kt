package xyz.raiz.sobre.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Replays the fixture emitted by wallet/tools/gen-sign-fixture.mjs, which was
 * generated with (and asserted against) the vendor-locked
 * @stellar/stellar-sdk 14.6.1: strkey derivation, tx hash, and the FULL signed
 * envelope must match byte-for-byte (Ed25519/RFC 8032 is deterministic).
 *
 * The seed is a THROWAWAY test vector (32 x 0x42) — public on purpose.
 * Regenerate the fixture with:  node wallet/tools/gen-sign-fixture.mjs
 */
class StellarAccountTest {

    // ---- fixture (gen-sign-fixture.mjs output, 2026-08-03) -----------------
    private val seed = ByteArray(32) { 0x42 }
    private val expectedAccountId = "GAQVF6GRTN4R2JCFGJBOCXZOVNWLPT72PNVF5UYAS6LA4BUYQHNRET46"
    private val passphrase = "Test SDF Network ; September 2015"
    private val unsignedXdr =
        "AAAAAgAAAAAhUvjRm3kdJEUyQuFfLqtst8/6e2pe0wCXlg4GmIHbEgAAAGQBcH2gMW7AaAAAAAEAAAAAAAAAAAAAAABqcOHQAAAA" +
            "AAAAAAEAAAAAAAAAAQAAAAAS+4F/11vONYTO7ob72U/vSKWrugZkOdH49k1saImWhgAAAAAAAAAAB00zoAAAAAAAAAAA"
    private val expectedTxHash = "2421c719e63464478457b77eabc8fc0999170443497acd9260f1917ae1a5225f"
    private val expectedSignedXdr =
        "AAAAAgAAAAAhUvjRm3kdJEUyQuFfLqtst8/6e2pe0wCXlg4GmIHbEgAAAGQBcH2gMW7AaAAAAAEAAAAAAAAAAAAAAABqcOHQAAAA" +
            "AAAAAAEAAAAAAAAAAQAAAAAS+4F/11vONYTO7ob72U/vSKWrugZkOdH49k1saImWhgAAAAAAAAAAB00zoAAAAAAAAAABmIHbEgAA" +
            "AECEt/BXpzSrc4k3vmU56B3HoKJ3Rg4ztOysW1bRKKVGMFtpV+mUSjbwxdXhe9QKvSNbY/fXlBmXUl0KnqXUiKoL"
    // -----------------------------------------------------------------------

    @Test
    fun `derives the same G address as the stellar sdk`() {
        assertEquals(expectedAccountId, StellarAccount(seed).accountId)
    }

    @Test
    fun `computes the same tx hash and signed envelope as the stellar sdk`() {
        val signed = StellarAccount(seed).signEnvelope(unsignedXdr, passphrase)
        assertEquals(expectedTxHash, signed.txHashHex)
        assertEquals(expectedSignedXdr, signed.signedXdrBase64)
    }

    @Test
    fun `rejects an already signed envelope`() {
        assertThrows(IllegalArgumentException::class.java) {
            StellarAccount(seed).signEnvelope(expectedSignedXdr, passphrase)
        }
    }

    @Test
    fun `crc16 xmodem matches the known check value`() {
        // Standard CRC-16/XMODEM check vector: "123456789" -> 0x31C3.
        assertEquals(0x31C3, StrKey.crc16Xmodem("123456789".toByteArray(Charsets.US_ASCII)))
    }
}
