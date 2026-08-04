package xyz.raiz.sobre.ui.nav

import java.time.Instant
import java.util.Locale

/**
 * The four formatting helpers RAIZ never wrote (it inlined `take(8)…takeLast(6)`
 * nine times and hardcoded `" USDC"` in one place).
 *
 * Lives in `ui/nav` because that is the shell package shared by every screen in
 * this agent's lane; `ui/util` belongs to another lane in this session. Move it
 * next to `StellarExpert.kt` when the lanes merge.
 *
 * `Locale.US` everywhere on purpose: this device writes a decimal comma, and
 * `trimEnd('.')` then leaves "55," on screen (already paid for once — see the
 * comment in the Session 5 debug screen).
 */

private const val STROOPS_PER_UNIT = 10_000_000.0

/** `GAJPXAL7…IM73X` — the truncation idiom RAIZ repeats everywhere. */
fun String.shortAddr(head: Int = 8, tail: Int = 6): String =
    if (length <= head + tail + 1) this else take(head) + "…" + takeLast(tail)

/** Stroops -> `"55 XLM"` / `"0.5 XLM"`; trailing zeros dropped. */
fun Long.formatXlm(suffix: String = " XLM"): String {
    val v = String.format(Locale.US, "%.4f", this / STROOPS_PER_UNIT)
        .trimEnd('0')
        .trimEnd('.')
    return (if (v.isEmpty() || v == "-") "0" else v) + suffix
}

/** Same, but tolerant of the `null` that means "not decrypted yet". */
fun Long?.formatXlmOrHidden(hidden: String = "•••"): String =
    this?.formatXlm() ?: hidden

/**
 * `"12,5"` / `"12.5"` -> stroops, or null when the text is not a usable amount.
 * Accepts the comma this keyboard produces in Spanish locales.
 */
fun parseXlmToStroops(raw: String): Long? {
    val cleaned = raw.trim().replace(',', '.')
    if (cleaned.isEmpty()) return null
    val units = cleaned.toDoubleOrNull() ?: return null
    if (units <= 0.0 || !units.isFinite()) return null
    val stroops = Math.round(units * STROOPS_PER_UNIT)
    return if (stroops <= 0L) null else stroops
}

/**
 * ISO-8601 (`2026-08-03T19:29:15Z`, what the indexer returns in
 * `ledgerClosedAt`) -> Spanish relative time. Falls back to the raw date when
 * it is older than a week, and to the input itself when unparseable — never
 * silently blank, because a blank date next to a hidden amount looks like the
 * row is broken.
 */
fun String?.asRelativeEs(now: Instant = Instant.now()): String {
    if (this.isNullOrBlank()) return "fecha desconocida"
    val then = try {
        Instant.parse(this)
    } catch (e: Exception) {
        return this
    }
    val secs = (now.epochSecond - then.epochSecond).coerceAtLeast(0)
    return when {
        secs < 60 -> "hace instantes"
        secs < 3_600 -> "hace ${secs / 60} min"
        secs < 86_400 -> "hace ${secs / 3600} h"
        secs < 172_800 -> "ayer"
        secs < 604_800 -> "hace ${secs / 86_400} días"
        else -> this.take(10) // 2026-08-03
    }
}
