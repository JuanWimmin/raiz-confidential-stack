package xyz.raiz.sobre.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the goal timeline gets its events from — the one setting the demo video
 * is built around ("cambiar la fuente en vivo es la escena central").
 *
 * Two presets, same base URL, same screen:
 *  - **Raiz Memory** — the durable index; the whole history.
 *  - **RPC (simulado)** — the identical endpoint with `source=rpc-simulation`,
 *    which the indexer answers as an RPC that only kept the last N ledgers
 *    (`RETENTION_SIMULATION_LEDGERS`, `raiz-memory/src/main.rs:127-137`). It
 *    also returns `oldestLedger`, i.e. it *declares* what it has forgotten.
 *
 * The `source` flag rides on the [EventSource], not on each call site, so a
 * screen swaps sources by swapping one object.
 */
data class EventSource(
    /** Stable key persisted in prefs; also the id of a preset. */
    val id: String,
    /** Spanish label for the UI — project vocabulary, do not paraphrase. */
    val label: String,
    /** One-line Spanish explanation for the source picker. */
    val hint: String,
    /** Normalized, no trailing slash, e.g. `http://localhost:8091`. */
    val baseUrl: String,
    /** Value of the `source` query param, or null for the honest index. */
    val sourceParam: String?,
) {
    /** True when this source admits to forgetting — drives the UI badge. */
    val forgets: Boolean get() = sourceParam == RPC_SIMULATION_PARAM

    fun withBaseUrl(raw: String): EventSource =
        copy(baseUrl = EventSourceStore.normalizeBaseUrl(raw))

    companion object {
        /**
         * Raiz Memory on the phone: `adb reverse tcp:8091 tcp:8091` makes the
         * laptop's indexer reachable at localhost (port 8091, not the repo
         * default 8090, which is taken on this machine — see raiz-memory/.env).
         * Cleartext is already permitted by
         * `app/src/main/res/xml/network_security_config.xml`.
         */
        const val DEFAULT_BASE_URL = "http://localhost:8091"
        const val RPC_SIMULATION_PARAM = "rpc-simulation"

        const val ID_RAIZ_MEMORY = "raiz-memory"
        const val ID_RPC_SIMULATION = "rpc-simulado"

        fun raizMemory(baseUrl: String = DEFAULT_BASE_URL) = EventSource(
            id = ID_RAIZ_MEMORY,
            label = "Raiz Memory",
            hint = "Recuerda todo: el historial completo, más allá de los ~7 días del RPC.",
            baseUrl = EventSourceStore.normalizeBaseUrl(baseUrl),
            sourceParam = null,
        )

        fun rpcSimulation(baseUrl: String = DEFAULT_BASE_URL) = EventSource(
            id = ID_RPC_SIMULATION,
            label = "RPC (simulado)",
            hint = "Olvida: solo conserva los últimos ledgers, como el RPC público.",
            baseUrl = EventSourceStore.normalizeBaseUrl(baseUrl),
            sourceParam = RPC_SIMULATION_PARAM,
        )

        /** The two named presets, in the order the picker should show them. */
        fun presets(baseUrl: String = DEFAULT_BASE_URL): List<EventSource> =
            listOf(raizMemory(baseUrl), rpcSimulation(baseUrl))
    }
}

/**
 * Persists the chosen [EventSource]. Plain [SharedPreferences] on purpose: a
 * base URL is not a secret, and `WalletStore` (EncryptedSharedPreferences) is
 * reserved for key material — that split is a project rule, not a preference.
 *
 * Observe [current] to re-render when the user switches sources mid-demo.
 */
class EventSourceStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _current = MutableStateFlow(read())

    /** The active source; emits on every [select]/[setBaseUrl]/[reset]. */
    val current: StateFlow<EventSource> = _current.asStateFlow()

    /** Snapshot for non-Compose callers (e.g. `EventsClient { store.value }`). */
    val value: EventSource get() = _current.value

    /** The presets, already carrying the base URL the user configured. */
    fun presets(): List<EventSource> = EventSource.presets(baseUrl())

    fun baseUrl(): String =
        normalizeBaseUrl(prefs.getString(KEY_BASE_URL, null) ?: EventSource.DEFAULT_BASE_URL)

    /** Select a preset by id, keeping the currently configured base URL. */
    fun selectPreset(id: String) {
        prefs.edit().putString(KEY_SOURCE_ID, id).apply()
        _current.value = read()
    }

    /** Select a source object; adopts BOTH its id and its base URL. */
    fun select(source: EventSource) {
        prefs.edit()
            .putString(KEY_SOURCE_ID, source.id)
            .putString(KEY_BASE_URL, normalizeBaseUrl(source.baseUrl))
            .apply()
        _current.value = read()
    }

    /** Point every preset at a new host (the editable field in the sheet). */
    fun setBaseUrl(raw: String) {
        prefs.edit().putString(KEY_BASE_URL, normalizeBaseUrl(raw)).apply()
        _current.value = read()
    }

    /** Back to Raiz Memory on [EventSource.DEFAULT_BASE_URL]. */
    fun reset() {
        prefs.edit().clear().apply()
        _current.value = read()
    }

    private fun read(): EventSource = resolve(
        id = prefs.getString(KEY_SOURCE_ID, null),
        baseUrl = prefs.getString(KEY_BASE_URL, null),
    )

    companion object {
        const val FILE_NAME = "sobre_event_source"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_BASE_URL = "base_url"

        /**
         * Trim, drop trailing slashes, and default a bare host to `http://`
         * (a LAN address typed without a scheme is the common demo-day typo).
         * Blank input falls back to [EventSource.DEFAULT_BASE_URL].
         *
         * Pure — unit-tested without a Context.
         */
        fun normalizeBaseUrl(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return EventSource.DEFAULT_BASE_URL
            val withScheme = when {
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> "http://$trimmed"
            }
            return withScheme.trimEnd('/').ifEmpty { EventSource.DEFAULT_BASE_URL }
        }

        /**
         * Stored (id, baseUrl) -> [EventSource]. Unknown or missing id means
         * Raiz Memory: the honest source is the default, always.
         *
         * Pure — unit-tested without a Context.
         */
        fun resolve(id: String?, baseUrl: String?): EventSource {
            val url = normalizeBaseUrl(baseUrl)
            return when (id) {
                EventSource.ID_RPC_SIMULATION -> EventSource.rpcSimulation(url)
                else -> EventSource.raizMemory(url)
            }
        }
    }
}
