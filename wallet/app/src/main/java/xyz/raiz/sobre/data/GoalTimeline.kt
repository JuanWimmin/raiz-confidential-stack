package xyz.raiz.sobre.data

import xyz.raiz.sobre.wallet.CtConfig
import java.math.BigInteger
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * Turns raw indexed events into the timeline the goal screen renders.
 *
 * THE PRODUCT RULE THIS FILE ENFORCES: an *aporte* is a contributor address and
 * a timestamp — **quién y cuándo, nunca cuánto**. That is not a UI choice we
 * could undo here: the CT wrapper's `transfer` event carries only ciphertext
 * (`b_aud_s`, `b_tilde`, `r_aud_r`, `r_e`, `sigma`, … — verified in the real
 * event values), so [TimelineEntry.Contribution] has no amount field because
 * there is no amount to have.
 *
 * EVENT SHAPES — read off the live indexer on 2026-08-03 and decoded with
 * [ScVal]; every one appears verbatim as a fixture in `GoalTimelineTest`:
 *
 * CT wrapper (`CtConfig.TOKEN`, CBWSANZN…DHAT)
 * ```
 * ["register", Addr account]              value map { auditor_id: u32 }
 * ["deposit",  Addr from, Addr to]        value map { amount: i128 }   <- public by CT design
 * ["merge",    Addr account]              value map { }                 (empty)
 * ["transfer", Addr from, Addr to]        value map { 8 ciphertext fields }
 * ["underlying_asset_set"] / ["verifier_set"] / ["auditor_set"] /
 *   ["address_as_field_set"]              value map { 1 field }         (deploy-time config)
 * ```
 * goal_meta (CBNVY2AA…IQAZ, `contracts/goal-meta/src/lib.rs`)
 * ```
 * ["goal", "created", u32 id]             value Addr  (the goal's CT account)
 * ["goal", "harvest", u32 id]             value Str   (the harvest memo)
 * ```
 *
 * `deposit` is the one event that DOES carry a plain amount. It is decoded (we
 * do not hide what the chain publishes) but [GoalTimeline.build] never puts it
 * on the goal screen: a public figure next to "los montos son secretos" would
 * be a lie by juxtaposition.
 */
sealed class TimelineEntry {
    abstract val id: String
    abstract val contractId: String
    abstract val ledger: Long

    /** `ledgerClosedAt`, ISO-8601 UTC (e.g. `2026-08-03T19:29:15Z`), or null. */
    abstract val atIso: String?

    /** Raw tx hash, kept for explorer links. */
    abstract val txHash: String?

    abstract val kind: TimelineKind

    /** Spanish row title — project vocabulary, do not paraphrase. */
    abstract val title: String

    /** The address to render as "quién", when the row has one. */
    open val actor: String? = null

    /** Extra human text (currently only the harvest memo). */
    open val detail: String? = null

    /** [atIso] as epoch seconds, or null when absent/unparseable. */
    val epochSeconds: Long? get() = parseIsoEpochSeconds(atIso)

    /** An *aporte*: a confidential transfer into a CT account. No amount exists. */
    data class Contribution(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val from: String,
        val to: String,
    ) : TimelineEntry() {
        override val kind = TimelineKind.CONTRIBUTION
        override val title = "Aportó a la meta"
        override val actor get() = from
    }

    /** CT `merge`: rolling the receiving balance into the spendable one. */
    data class Merge(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val account: String,
        /** True when the merging account is the goal's. */
        val byGoal: Boolean,
    ) : TimelineEntry() {
        override val kind = TimelineKind.MERGE
        override val title = if (byGoal) "La meta cosechó los aportes" else "Cosechó su sobre"
        override val actor get() = account
    }

    /** CT `register`: an account opened its confidential envelope. */
    data class Registration(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val account: String,
        val auditorId: Long?,
    ) : TimelineEntry() {
        override val kind = TimelineKind.REGISTRATION
        override val title = "Abrió su sobre"
        override val actor get() = account
    }

    /**
     * CT `deposit`: SEP-41 tokens wrapped into the confidential balance.
     * The amount IS public on-chain (CT design) — never rendered on the goal
     * screen; see [GoalTimeline.build].
     */
    data class Deposit(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val from: String,
        val to: String,
        val amountStroops: BigInteger?,
    ) : TimelineEntry() {
        override val kind = TimelineKind.DEPOSIT
        override val title = "Selló monedas en su sobre"
        override val actor get() = from
    }

    /** goal_meta `("goal","created", id)`. */
    data class GoalCreated(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val goalId: Long,
        val goalAccount: String?,
    ) : TimelineEntry() {
        override val kind = TimelineKind.GOAL_CREATED
        override val title = "Se creó la meta"
        override val actor get() = goalAccount
    }

    /** goal_meta `("goal","harvest", id)` — the memo is the milestone text. */
    data class Harvest(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val goalId: Long,
        val memo: String,
    ) : TimelineEntry() {
        override val kind = TimelineKind.HARVEST
        override val title = "Cosecha registrada"
        override val detail get() = memo
    }

    /** Deploy-time wiring of the wrapper (`verifier_set`, `auditor_set`, …). */
    data class Configuration(
        override val id: String,
        override val contractId: String,
        override val ledger: Long,
        override val atIso: String?,
        override val txHash: String?,
        val setting: String,
    ) : TimelineEntry() {
        override val kind = TimelineKind.CONFIG
        override val title = "Se configuró el contrato"
        override val detail get() = setting
    }
}

enum class TimelineKind {
    CONTRIBUTION, MERGE, REGISTRATION, DEPOSIT, GOAL_CREATED, HARVEST, CONFIG
}

/** Newest-first, by ledger then by event id (ids sort in emission order). */
private val NEWEST_FIRST: Comparator<TimelineEntry> =
    compareByDescending<TimelineEntry> { it.ledger }.thenByDescending { it.id }

/**
 * What the goal screen needs, already filtered to ONE goal.
 *
 * [oldestLedger] is the retention floor the source admitted to. Non-null means
 * "this source has forgotten everything before that ledger" — the whole point
 * of the demo. See [truncated].
 */
data class GoalTimeline(
    val entries: List<TimelineEntry>,
    val contributions: List<TimelineEntry.Contribution>,
    val harvests: List<TimelineEntry>,
    /** Distinct contributor addresses, most recent contribution first. */
    val contributors: List<String>,
    val goalId: Long,
    val goalAccount: String,
    val createdAtIso: String?,
    val latestLedger: Long,
    val oldestLedger: Long?,
    val source: EventSource,
) {
    val contributionCount: Int get() = contributions.size
    val contributorCount: Int get() = contributors.size
    val harvestCount: Int get() = harvests.size
    val isEmpty: Boolean get() = entries.isEmpty()

    /** True when the source declared a retention floor: history is missing. */
    val truncated: Boolean get() = oldestLedger != null

    companion object {
        /** The real goal. Id 0 is a dead placeholder from the first run — never shown. */
        const val REAL_GOAL_ID = 1L

        val EMPTY = GoalTimeline(
            entries = emptyList(),
            contributions = emptyList(),
            harvests = emptyList(),
            contributors = emptyList(),
            goalId = REAL_GOAL_ID,
            goalAccount = CtConfig.GOAL_ACCOUNT,
            createdAtIso = null,
            latestLedger = 0L,
            oldestLedger = null,
            source = EventSource.raizMemory(),
        )

        /**
         * Assemble the goal timeline from the two contracts' events.
         *
         * Kept: contributions INTO [goalAccount], merges BY [goalAccount],
         * and the goal_meta creation + harvests whose id is [goalId].
         * Dropped: deposits (public amounts), other accounts' merges, deploy
         * config, and — unless [includeRegistrations] — `register` rows.
         */
        fun build(
            ctEvents: List<EventsClient.EventRecord>,
            goalMetaEvents: List<EventsClient.EventRecord>,
            source: EventSource,
            latestLedger: Long = 0L,
            oldestLedger: Long? = null,
            goalAccount: String = CtConfig.GOAL_ACCOUNT,
            goalId: Long = REAL_GOAL_ID,
            includeRegistrations: Boolean = false,
        ): GoalTimeline {
            val mapper = TimelineMapper(goalAccount)
            val kept = ArrayList<TimelineEntry>()

            for (entry in mapper.mapAll(ctEvents)) {
                when (entry) {
                    is TimelineEntry.Contribution -> if (entry.to == goalAccount) kept += entry
                    is TimelineEntry.Merge -> if (entry.byGoal) kept += entry
                    is TimelineEntry.Registration -> if (includeRegistrations) kept += entry
                    else -> Unit // deposits and deploy config never reach the goal screen
                }
            }
            for (entry in mapper.mapAll(goalMetaEvents)) {
                when (entry) {
                    is TimelineEntry.GoalCreated -> if (entry.goalId == goalId) kept += entry
                    is TimelineEntry.Harvest -> if (entry.goalId == goalId) kept += entry
                    else -> Unit
                }
            }

            val ordered = kept.sortedWith(NEWEST_FIRST)
            val contributions = ordered.filterIsInstance<TimelineEntry.Contribution>()
            return GoalTimeline(
                entries = ordered,
                contributions = contributions,
                harvests = ordered.filter {
                    it.kind == TimelineKind.HARVEST || it.kind == TimelineKind.MERGE
                },
                contributors = contributions.map { it.from }.distinct(),
                goalId = goalId,
                goalAccount = goalAccount,
                createdAtIso = ordered.filterIsInstance<TimelineEntry.GoalCreated>()
                    .lastOrNull()?.atIso,
                latestLedger = latestLedger,
                oldestLedger = oldestLedger,
                source = source,
            )
        }
    }
}

/**
 * Decodes events into [TimelineEntry]s. Stateless and pure — no network, no
 * Android — so it is fully unit-tested against the real base64 fixtures.
 *
 * Unrecognized or undecodable events map to null and are dropped: an event
 * shape we have never seen must not crash the screen.
 */
class TimelineMapper(private val goalAccount: String = CtConfig.GOAL_ACCOUNT) {

    /** Map every event we recognize, sorted newest-first. Unknown shapes drop out. */
    fun mapAll(events: List<EventsClient.EventRecord>): List<TimelineEntry> =
        events.mapNotNull { map(it) }.sortedWith(NEWEST_FIRST)

    fun map(e: EventsClient.EventRecord): TimelineEntry? {
        val topics = ScVal.decodeTopics(e.topics)
        val head = topics.firstOrNull()?.asSymbol ?: return null
        val value = e.valueXdr?.let { ScVal.decodeOrNull(it) }

        return when (head) {
            "transfer" -> {
                val from = topics.getOrNull(1)?.asAddress ?: return null
                val to = topics.getOrNull(2)?.asAddress ?: return null
                TimelineEntry.Contribution(e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash, from, to)
            }

            "merge" -> {
                val account = topics.getOrNull(1)?.asAddress ?: return null
                TimelineEntry.Merge(
                    e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash,
                    account = account,
                    byGoal = account == goalAccount,
                )
            }

            "register" -> {
                val account = topics.getOrNull(1)?.asAddress ?: return null
                TimelineEntry.Registration(
                    e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash,
                    account = account,
                    auditorId = value?.mapField("auditor_id")?.asU32,
                )
            }

            "deposit" -> {
                val from = topics.getOrNull(1)?.asAddress ?: return null
                val to = topics.getOrNull(2)?.asAddress ?: return null
                TimelineEntry.Deposit(
                    e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash, from, to,
                    amountStroops = value?.mapField("amount")?.asBigInteger,
                )
            }

            // goal_meta: the topic head is always the literal symbol "goal".
            "goal" -> when (topics.getOrNull(1)?.asSymbol) {
                "created" -> TimelineEntry.GoalCreated(
                    e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash,
                    goalId = topics.getOrNull(2)?.asU32 ?: return null,
                    goalAccount = value?.asAddress,
                )
                "harvest" -> TimelineEntry.Harvest(
                    e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash,
                    goalId = topics.getOrNull(2)?.asU32 ?: return null,
                    memo = value?.asString.orEmpty(),
                )
                else -> null
            }

            else ->
                if (head.endsWith("_set") && topics.size == 1) {
                    TimelineEntry.Configuration(
                        e.id, e.contractId, e.ledger, e.ledgerClosedAt, e.txHash,
                        setting = head.removeSuffix("_set"),
                    )
                } else {
                    null
                }
        }
    }
}

/** `2026-08-03T19:29:15Z` -> epoch seconds; null when absent or unparseable. */
internal fun parseIsoEpochSeconds(iso: String?): Long? {
    if (iso.isNullOrBlank()) return null
    return try {
        Instant.parse(iso).epochSecond
    } catch (e: DateTimeParseException) {
        null
    }
}
