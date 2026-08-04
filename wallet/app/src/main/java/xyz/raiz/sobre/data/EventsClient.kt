package xyz.raiz.sobre.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * HTTP client for a `getEvents`-shaped event API, with a CONFIGURABLE base URL.
 *
 * That configurability is the point, not a nicety: the same screen, the same
 * code path, two sources — Raiz Memory (remembers) and an RPC that forgets.
 * Switching it live is the central scene of the demo video, so the base URL and
 * the optional `source=rpc-simulation` flag are read from an [EventSource]
 * *per call* (via the provider lambda), never captured at construction.
 *
 * Same idiom as `wallet/SorobanRpc.kt` (project decision: no Retrofit/Moshi —
 * HttpURLConnection + org.json only), plus coroutines: every call suspends on
 * [Dispatchers.IO].
 *
 * ENDPOINTS — verified against the running indexer on 2026-08-03, not assumed
 * (`raiz-memory/src/main.rs:78-154`, `raiz-memory/src/db.rs:110-165`):
 *
 *   GET /health    -> {"status":"ok","latest_indexed_ledger":3953669}
 *   GET /coverage  -> {"contracts":[{"contractId","scannedThroughLedger",
 *                                    "firstEventLedger","lastEventLedger",
 *                                    "eventCount"}]}
 *   GET /events?contractId=…[&startLedger=…][&cursor=…][&limit=…][&source=…]
 *                  -> {"latestLedger", "cursor", "events":[…]}
 *                     plus "oldestLedger" ONLY when the request opted into the
 *                     purge-demo mode and the server has it armed.
 *
 * Real quirks this client handles, each seen or documented:
 *  - **Errors arrive as HTTP 200 with an `{"error": "..."}` body** (main.rs:152).
 *    A naive client would report success and an empty timeline; we raise
 *    [EventsException.Upstream].
 *  - **Events come back OLDEST-FIRST** (`ORDER BY id ASC`, db.rs:137) and
 *    `cursor` is the last event's id; the next page is `id > cursor`. The
 *    caller is done when a page returns fewer rows than `limit`.
 *  - **`startLedger` must be omitted once a `cursor` is in play** — the real
 *    RPC rejects the combination; we drop it and log rather than send both.
 *  - **Shape drift (project gotcha #5)**: `value` and each `topic` entry may be
 *    a bare base64 string or `{"xdr": "…"}`; the paging token may be `cursor`
 *    or `pagingToken`. Both spellings are accepted.
 */
class EventsClient(
    private val sourceProvider: () -> EventSource,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    private val attempts: Int = 3,
    private val baseBackoffMs: Long = 500,
) {
    constructor(source: EventSource) : this({ source })

    // -- results ------------------------------------------------------------

    /** One contract event, fields exactly as the API names them. */
    data class EventRecord(
        val id: String,
        val contractId: String,
        val ledger: Long,
        val txHash: String?,
        /** ISO-8601 UTC, e.g. `2026-08-03T19:29:15Z`; null if the source omits it. */
        val ledgerClosedAt: String?,
        /** Raw base64 XDR topics — decode with [ScVal.decodeTopics]. */
        val topics: List<String>,
        /** Raw base64 XDR value — ciphertext stays ciphertext until asked. */
        val valueXdr: String?,
        val inSuccessfulContractCall: Boolean,
    )

    data class EventsPage(
        val events: List<EventRecord>,
        /** Paging token to pass back as `cursor`; null when the page was empty. */
        val cursor: String?,
        val latestLedger: Long,
        /**
         * Retention floor the source admits to. Raiz Memory only emits this in
         * purge-demo mode — its presence is literally "this source forgets".
         */
        val oldestLedger: Long?,
        /** Which source answered, so the UI can label the data it is showing. */
        val source: EventSource,
    )

    data class Health(val status: String, val latestIndexedLedger: Long)

    data class ContractCoverage(
        val contractId: String,
        val eventCount: Long,
        val firstEventLedger: Long,
        val lastEventLedger: Long,
        val scannedThroughLedger: Long,
    )

    // -- errors -------------------------------------------------------------

    sealed class EventsException(message: String, cause: Throwable? = null) :
        IOException(message, cause) {

        /** Transport failed on every attempt (server down, phone lost the LAN, DNS…). */
        class Network(val url: String, val attempts: Int, cause: Throwable?) :
            EventsException("no pudimos contactar $url tras $attempts intentos: ${cause?.message}", cause)

        /** A non-2xx HTTP status. [body] is truncated but verbatim — friction-report material. */
        class Http(val code: Int, val url: String, val body: String) :
            EventsException("$url respondió HTTP $code: ${body.take(300)}")

        /** HTTP 200 carrying `{"error": "…"}` — how this API reports failure. */
        class Upstream(val url: String, val error: String) :
            EventsException("$url respondió con error: $error")

        /** 200 + parseable HTTP but not the JSON we expect. */
        class Malformed(val url: String, val detail: String, cause: Throwable? = null) :
            EventsException("respuesta inesperada de $url: $detail", cause)
    }

    // -- API ----------------------------------------------------------------

    suspend fun health(source: EventSource? = null): Health {
        val src = source ?: sourceProvider()
        val json = getJson(src.endpoint("/health"))
        return Health(
            status = json.optString("status", "unknown"),
            latestIndexedLedger = json.optLong("latest_indexed_ledger", 0L),
        )
    }

    suspend fun coverage(source: EventSource? = null): List<ContractCoverage> {
        val src = source ?: sourceProvider()
        val url = src.endpoint("/coverage")
        val json = getJson(url)
        val arr = json.optJSONArray("contracts")
            ?: throw EventsException.Malformed(url, "no 'contracts' array in ${json.toString().take(200)}")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ContractCoverage(
                contractId = o.optString("contractId"),
                eventCount = o.optLong("eventCount"),
                firstEventLedger = o.optLong("firstEventLedger"),
                lastEventLedger = o.optLong("lastEventLedger"),
                scannedThroughLedger = o.optLong("scannedThroughLedger"),
            )
        }
    }

    /**
     * One page of events for [contractId], oldest-first.
     *
     * Pass [cursor] from the previous page to advance; [startLedger] is only
     * honored on the first (cursor-less) call, mirroring the real RPC's rule.
     */
    suspend fun events(
        contractId: String,
        limit: Int = DEFAULT_PAGE_SIZE,
        cursor: String? = null,
        startLedger: Long? = null,
        source: EventSource? = null,
    ): EventsPage {
        val src = source ?: sourceProvider()
        val params = LinkedHashMap<String, String>()
        params["contractId"] = contractId
        if (cursor != null) {
            params["cursor"] = cursor
            if (startLedger != null) {
                Log.w(TAG, "startLedger dropped: the RPC forbids startLedger together with cursor")
            }
        } else if (startLedger != null) {
            params["startLedger"] = startLedger.toString()
        }
        params["limit"] = limit.coerceIn(1, MAX_PAGE_SIZE).toString()
        src.sourceParam?.let { params["source"] = it }

        val url = src.endpoint("/events", params)
        val json = getJson(url)
        return parsePage(url, json, src)
    }

    /**
     * Follow the cursor until the source is exhausted (or [maxPages] is hit)
     * and return every event, still oldest-first. Bounded on purpose: an
     * unbounded loop against a busy contract is how a phone runs out of memory.
     */
    suspend fun allEvents(
        contractId: String,
        startLedger: Long? = null,
        pageSize: Int = DEFAULT_PAGE_SIZE,
        maxPages: Int = DEFAULT_MAX_PAGES,
        source: EventSource? = null,
    ): EventsPage {
        val src = source ?: sourceProvider()
        val all = ArrayList<EventRecord>()
        var cursor: String? = null
        var last: EventsPage? = null
        var pages = 0
        while (pages < maxPages) {
            val page = events(
                contractId = contractId,
                limit = pageSize,
                cursor = cursor,
                startLedger = if (cursor == null) startLedger else null,
                source = src,
            )
            last = page
            all += page.events
            pages++
            if (page.events.size < pageSize || page.cursor == null || page.cursor == cursor) break
            cursor = page.cursor
        }
        return EventsPage(
            events = all,
            cursor = last?.cursor,
            latestLedger = last?.latestLedger ?: 0L,
            oldestLedger = last?.oldestLedger,
            source = src,
        )
    }

    // -- plumbing -----------------------------------------------------------

    private fun parsePage(url: String, json: JSONObject, src: EventSource): EventsPage {
        val arr: JSONArray = json.optJSONArray("events")
            ?: throw EventsException.Malformed(url, "no 'events' array in ${json.toString().take(200)}")
        val events = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            EventRecord(
                id = o.optString("id"),
                contractId = o.optString("contractId"),
                ledger = o.optLong("ledger"),
                txHash = o.optString("txHash").ifEmpty { null },
                ledgerClosedAt = o.optString("ledgerClosedAt").ifEmpty { null },
                topics = xdrArray(o.optJSONArray("topic") ?: o.optJSONArray("topics")),
                valueXdr = xdrString(o.opt("value")),
                inSuccessfulContractCall = o.optBoolean("inSuccessfulContractCall", true),
            )
        }
        // `cursor` is ours and the modern RPC's; `pagingToken` is the older
        // spelling (project gotcha #5) — accept whichever shows up.
        val cursor = (json.optString("cursor").ifEmpty { null })
            ?: json.optString("pagingToken").ifEmpty { null }
            ?: events.lastOrNull()?.id
        return EventsPage(
            events = events,
            cursor = cursor,
            latestLedger = json.optLong("latestLedger", 0L),
            oldestLedger = if (json.has("oldestLedger")) json.optLong("oldestLedger") else null,
            source = src,
        )
    }

    /** `value`/topic entries are either `"AAAA…"` or `{"xdr":"AAAA…"}`. */
    private fun xdrString(node: Any?): String? = when (node) {
        null, JSONObject.NULL -> null
        is String -> node.ifEmpty { null }
        is JSONObject -> node.optString("xdr").ifEmpty { null }
        else -> node.toString().ifEmpty { null }
    }

    private fun xdrArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { xdrString(arr.opt(it)) }
    }

    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) {
        val text = retrying(url) { httpGet(url) }
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            throw EventsException.Malformed(url, "not a JSON object: ${text.take(200)}", e)
        }
        // This API answers HTTP 200 with {"error": "..."} (main.rs:152).
        json.optString("error").ifEmpty { null }?.let {
            throw EventsException.Upstream(url, it)
        }
        json
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw EventsException.Http(code, url, body)
            return body
        } finally {
            conn.disconnect()
        }
    }

    /**
     * [attempts] tries with exponential backoff for transport failures only.
     * A 4xx (other than 429) is a verdict, not a hiccup — it propagates at once,
     * as do our own typed errors.
     */
    private suspend fun <T> retrying(url: String, block: () -> T): T {
        var last: Throwable? = null
        for (i in 1..attempts) {
            try {
                return block()
            } catch (e: EventsException.Http) {
                if (e.code in 400..499 && e.code != 429) throw e
                last = e
            } catch (e: EventsException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
            if (i < attempts) {
                val wait = baseBackoffMs shl (i - 1)
                Log.w(TAG, "GET $url attempt $i/$attempts failed (${last?.message}); retrying in ${wait}ms")
                delay(wait)
            }
        }
        throw EventsException.Network(url, attempts, last)
    }

    companion object {
        private const val TAG = "SobreEvents"
        const val DEFAULT_PAGE_SIZE = 200
        const val MAX_PAGE_SIZE = 1000
        const val DEFAULT_MAX_PAGES = 25

        /** Build `base + path + ?query`, percent-encoding every value. Pure, hence testable. */
        fun buildUrl(baseUrl: String, path: String, params: Map<String, String> = emptyMap()): String {
            val base = baseUrl.trimEnd('/')
            val query = params.entries.joinToString("&") { (k, v) ->
                "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}"
            }
            return if (query.isEmpty()) "$base$path" else "$base$path?$query"
        }
    }
}

/** `{baseUrl}{path}?{params}` for this source, base URL already normalized. */
fun EventSource.endpoint(path: String, params: Map<String, String> = emptyMap()): String =
    EventsClient.buildUrl(baseUrl, path, params)
