package xyz.raiz.sobre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The event-source switch and the URLs it produces. Only the pure halves are
 * covered here ([EventSourceStore.normalizeBaseUrl], [EventSourceStore.resolve]
 * and [EventsClient.buildUrl]); the SharedPreferences wrapper needs a Context
 * and is exercised on-device.
 *
 * The expected query strings below are the ones actually served on 2026-08-03:
 * `GET /events?contractId=…&limit=…[&source=rpc-simulation]`.
 */
class EventSourceTest {

    private val goalMeta = "CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ"

    @Test
    fun `defaults to Raiz Memory on localhost 8091`() {
        val s = EventSourceStore.resolve(id = null, baseUrl = null)
        assertEquals(EventSource.ID_RAIZ_MEMORY, s.id)
        assertEquals("Raiz Memory", s.label)
        assertEquals("http://localhost:8091", s.baseUrl)
        assertNull(s.sourceParam)
        assertFalse(s.forgets)
    }

    @Test
    fun `the RPC preset is the same URL plus the simulation flag`() {
        val rpc = EventSourceStore.resolve(EventSource.ID_RPC_SIMULATION, null)
        assertEquals("RPC (simulado)", rpc.label)
        assertEquals("http://localhost:8091", rpc.baseUrl)
        assertEquals("rpc-simulation", rpc.sourceParam)
        assertTrue(rpc.forgets)
    }

    @Test
    fun `both presets are offered, Raiz Memory first`() {
        val presets = EventSource.presets("http://192.168.0.20:8091")
        assertEquals(listOf("Raiz Memory", "RPC (simulado)"), presets.map { it.label })
        assertTrue(presets.all { it.baseUrl == "http://192.168.0.20:8091" })
    }

    @Test
    fun `an unknown stored id falls back to the honest source`() {
        assertEquals(EventSource.ID_RAIZ_MEMORY, EventSourceStore.resolve("garbage", null).id)
    }

    @Test
    fun `normalizes what a human types on demo day`() {
        assertEquals("http://localhost:8091", EventSourceStore.normalizeBaseUrl("  localhost:8091/  "))
        assertEquals("http://192.168.0.20:8091", EventSourceStore.normalizeBaseUrl("192.168.0.20:8091"))
        assertEquals("https://memory.raiz.xyz", EventSourceStore.normalizeBaseUrl("https://memory.raiz.xyz/"))
        assertEquals("http://localhost:8091", EventSourceStore.normalizeBaseUrl(""))
        assertEquals("http://localhost:8091", EventSourceStore.normalizeBaseUrl(null))
    }

    @Test
    fun `builds the exact query the indexer serves`() {
        val raiz = EventSource.raizMemory()
        assertEquals(
            "http://localhost:8091/events?contractId=$goalMeta&limit=20",
            raiz.endpoint("/events", linkedMapOf("contractId" to goalMeta, "limit" to "20")),
        )
        assertEquals("http://localhost:8091/health", raiz.endpoint("/health"))
        assertEquals("http://localhost:8091/coverage", raiz.endpoint("/coverage"))
    }

    @Test
    fun `percent-encodes cursors`() {
        // Real paging token shape; the '-' is safe but encoding must not mangle it.
        assertEquals(
            "http://localhost:8091/events?cursor=0016978379383279616-0000000000",
            EventsClient.buildUrl(
                "http://localhost:8091/",
                "/events",
                mapOf("cursor" to "0016978379383279616-0000000000"),
            ),
        )
    }

    @Test
    fun `withBaseUrl keeps the preset identity`() {
        val moved = EventSource.rpcSimulation().withBaseUrl("10.0.2.2:8091/")
        assertEquals(EventSource.ID_RPC_SIMULATION, moved.id)
        assertEquals("http://10.0.2.2:8091", moved.baseUrl)
        assertEquals("rpc-simulation", moved.sourceParam)
    }
}
