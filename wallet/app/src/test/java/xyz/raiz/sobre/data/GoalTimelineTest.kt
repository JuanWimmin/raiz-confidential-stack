package xyz.raiz.sobre.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import xyz.raiz.sobre.wallet.CtConfig
import java.math.BigInteger

/**
 * Timeline mapping against REAL indexed events (Raiz Memory, 2026-08-03): the
 * ids, ledgers, timestamps, tx hashes and base64 topics/values below were
 * copied out of `/events` responses for goal_meta (CBNVY2AA…IQAZ) and our CT
 * wrapper (CBWSANZN…DHAT). Nothing is invented.
 *
 * These are the two Session-4/5 aportes to the real goal: one from the CLI
 * contributor, one from the phone (tx 7f9c6f9a…, the M1 milestone).
 */
class GoalTimelineTest {

    private val ct = CtConfig.TOKEN
    private val goalMeta = "CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ"

    private val goal = CtConfig.GOAL_ACCOUNT // GAJPXAL7…M73X
    private val phone = "GDUDWSVPHXPEJI4QS5GZA5IKKHPQ72DH3TLEAPYNS3SUP4K6ANOGDUXL"
    private val cli = "GDUTRPFZAL3QRHCY47A6KAI6EK4XJTZ35J5IWI7YN3VGHHWA5F77DJ2I"
    private val placeholderGoalAccount = "GAAM6BBA6TV5VIG5552BMLXBKJZGVB5SNEBTYLRQ56O5PRT62J2PXXLJ"

    // ---- raw XDR fixtures --------------------------------------------------

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
    private val addrGoal = "AAAAEgAAAAAAAAAAEvuBf9dbzjWEzu6G+9lP70ilq7oGZDnR+PZNbGiJloY="
    private val addrCli = "AAAAEgAAAAAAAAAA6Ti8uQL3CJxY58HlAR4iuXTPO+p6iyP4bupjnsDpf/E="
    private val addrPhone = "AAAAEgAAAAAAAAAA6DtKrz3eRKOQl02QdQpR3w/oZ9zWQD8NluVH8V4DXGE="
    private val addrPlaceholder = "AAAAEgAAAAAAAAAAAM8EIPTr2qDd73QWLuFScmqHsmkDPC4w753Xxn7SdPs="
    private val mapEmpty = "AAAAEQAAAAEAAAAA"
    private val mapAuditorIdZero = "AAAAEQAAAAEAAAABAAAADwAAAAphdWRpdG9yX2lkAAAAAAADAAAAAA=="
    private val mapAmount10Xlm = "AAAAEQAAAAEAAAABAAAADwAAAAZhbW91bnQAAAAAAAoAAAAAAAAAAAAAAAAF9eEA"
    private val mapUnderlyingAsset =
        "AAAAEQAAAAEAAAABAAAADwAAABB1bmRlcmx5aW5nX2Fzc2V0AAAAEgAAAAHXkotywnA8z+r365/0701QSlWouXn8m0UOoshCtNHOYQ=="
    private val strHarvestMemo =
        "AAAADgAAADBwcmltZXJhIGNvc2VjaGEgKGdvYWwtZmxvdyAyMDI2LTA4LTAzVDE1OjI5OjQ4Wik="
    private val mapTransferCiphertext =
        "AAAAEQAAAAEAAAAIAAAADwAAAAdiX2F1ZF9zAAAAAA0AAAAgHwywlNzWUBd0zI8L4M1urtlo5fvJE1ncK8nyCQy/9cUAAAAP" +
            "AAAAB2JfdGlsZGUAAAAADQAAACAio690X7SMODSrJJd+c9K/UG0NtdwEWLHDFHnI/b0IJQAAAA8AAAAHcl9hdWRfcgAAAAAN" +
            "AAAAIBSctFrCm2514/jq71MMban5+UwZfJdr98DKjbToaXYcAAAADwAAAANyX2UAAAAADQAAAEAsX71oJdlsD78c/TWeq32U" +
            "MlL5jYGOdyd+Dvj0OVUOcAy7NLODbouq28bz5EaY/saKY7+Xut6ALNlw7KzuYPvhAAAADwAAAAVzaWdtYQAAAAAAAA0AAAAg" +
            "AMomTk9jaBxFbZREx6lsTq/djc/AOXGJCH77y7ezYQQAAAAPAAAAB3ZfYXVkX3IAAAAADQAAACAV9+ZhmEP6U+KpHcs0Wy84" +
            "N1SCUsH/shQEZD7+sns9kwAAAA8AAAAHdl9hdWRfcwAAAAANAAAAIBR75lCb9fuwHnYOy4Xf9Y035z3xzikFQxrLdgs8yszd" +
            "AAAADwAAAAd2X3RpbGRlAAAAAA0AAAAgF6BxTB2Qns0LbkZW3QMWrBRt+2xSfrS08dUV6FuK9Og="

    private fun ev(
        id: String,
        contractId: String,
        ledger: Long,
        at: String,
        tx: String,
        topics: List<String>,
        value: String,
    ) = EventsClient.EventRecord(
        id = id,
        contractId = contractId,
        ledger = ledger,
        txHash = tx,
        ledgerClosedAt = at,
        topics = topics,
        valueXdr = value,
        inSuccessfulContractCall = true,
    )

    // ---- real events -------------------------------------------------------

    private val evConfig = ev(
        "0016965674870009856-0000000000", ct, 3950129, "2026-08-03T15:22:10Z",
        "7256f27c4a0c4ee4dc964bab4d7b250ff730fe13613450b20dfa05f23cb9ef91",
        listOf(symUnderlyingAssetSet), mapUnderlyingAsset,
    )
    private val evTransferCli = ev(
        "0016965859553628160-0000000000", ct, 3950172, "2026-08-03T15:25:46Z",
        "5836313815618675a8530b3d3efb5e931e29ba9d49d58d90562414bc8c5463a4",
        listOf(symTransfer, addrCli, addrGoal), mapTransferCiphertext,
    )
    private val evMergeByGoal = ev(
        "0016965863848574976-0000000000", ct, 3950173, "2026-08-03T15:25:51Z",
        "1bbac2ee4bc85453933cabc8db98093927791557228cd91bb88635a7c361b97a",
        listOf(symMerge, addrGoal), mapEmpty,
    )
    private val evRegisterPhone = ev(
        "0016978044375814144-0000000000", ct, 3953009, "2026-08-03T19:22:44Z",
        "e7f9309a679fcd1e56472bd30b76c08fc5872fe59cbebb09965ca2ba42c64ec3",
        listOf(symRegister, addrPhone), mapAuditorIdZero,
    )
    private val evDepositPhone = ev(
        "0016978246239313920-0000000001", ct, 3953056, "2026-08-03T19:26:39Z",
        "2ff5108e734a2749db27de100a3586912ca84ab353dc578ed5b107e6e73f6f43",
        listOf(symDeposit, addrPhone, addrPhone), mapAmount10Xlm,
    )
    private val evMergePhone = ev(
        "0016978297778900992-0000000000", ct, 3953068, "2026-08-03T19:27:39Z",
        "0dcbefb42953d1dbfd7db620d6b33122824c66a5b33973d61d5432d97aa78038",
        listOf(symMerge, addrPhone), mapEmpty,
    )
    private val evTransferPhone = ev(
        "0016978379383279616-0000000000", ct, 3953087, "2026-08-03T19:29:15Z",
        "7f9c6f9ade687a5e222a9e5f72a104820244fb33f85f6cb3458b704446307c66",
        listOf(symTransfer, addrPhone, addrGoal), mapTransferCiphertext,
    )

    private val evGoalZeroCreated = ev(
        "0016966052827164672-0000000000", goalMeta, 3950217, "2026-08-03T15:29:31Z",
        "c6be29a9f57790a02a36d9a1663c3d9c37085ece71a8f1d953390969eb3164d0",
        listOf(symGoal, symCreated, u32Zero), addrPlaceholder,
    )
    private val evGoalZeroHarvest = ev(
        "0016966065712046080-0000000000", goalMeta, 3950220, "2026-08-03T15:29:46Z",
        "b987efdb44c2e2ad98609a6f03f1a63bdac449199a2041c801979f4a2a672d31",
        listOf(symGoal, symHarvest, u32Zero), strHarvestMemo,
    )
    private val evGoalOneCreated = ev(
        "0016975935546920960-0000000000", goalMeta, 3952518, "2026-08-03T18:41:43Z",
        "e27b9f1dba855c37ef728d3e6a43d90ca695b15191dbf7076ce1da82c5e71699",
        listOf(symGoal, symCreated, u32One), addrGoal,
    )

    private val ctEvents = listOf(
        evConfig, evTransferCli, evMergeByGoal,
        evRegisterPhone, evDepositPhone, evMergePhone, evTransferPhone,
    )
    private val goalMetaEvents = listOf(evGoalZeroCreated, evGoalZeroHarvest, evGoalOneCreated)

    private val mapper = TimelineMapper(goal)

    // ---- per-shape mapping -------------------------------------------------

    @Test
    fun `maps a confidential transfer into an aporte with who and when, never how much`() {
        val e = mapper.map(evTransferPhone) as TimelineEntry.Contribution
        assertEquals(phone, e.from)
        assertEquals(goal, e.to)
        assertEquals(phone, e.actor)
        assertEquals("Aportó a la meta", e.title)
        assertEquals("2026-08-03T19:29:15Z", e.atIso)
        assertEquals(1785785355L, e.epochSeconds)
        assertEquals(3953087L, e.ledger)
        assertEquals(
            "7f9c6f9ade687a5e222a9e5f72a104820244fb33f85f6cb3458b704446307c66",
            e.txHash,
        )
        assertEquals(TimelineKind.CONTRIBUTION, e.kind)
        // There is no amount property to read — the CT event has no amount.
        assertNull(e.detail)
    }

    @Test
    fun `maps merges and knows which one is the goal's`() {
        val byGoal = mapper.map(evMergeByGoal) as TimelineEntry.Merge
        assertTrue(byGoal.byGoal)
        assertEquals(goal, byGoal.account)
        assertEquals("La meta cosechó los aportes", byGoal.title)

        val mine = mapper.map(evMergePhone) as TimelineEntry.Merge
        assertFalse(mine.byGoal)
        assertEquals("Cosechó su sobre", mine.title)
    }

    @Test
    fun `maps register with its auditor id`() {
        val e = mapper.map(evRegisterPhone) as TimelineEntry.Registration
        assertEquals(phone, e.account)
        assertEquals(0L, e.auditorId)
        assertEquals("Abrió su sobre", e.title)
    }

    @Test
    fun `maps deposit with its public amount`() {
        val e = mapper.map(evDepositPhone) as TimelineEntry.Deposit
        assertEquals(BigInteger.valueOf(100_000_000L), e.amountStroops) // 10 XLM
        assertEquals(phone, e.from)
        assertEquals(phone, e.to)
    }

    @Test
    fun `maps goal_meta creation and harvest, decoding the memo`() {
        val created = mapper.map(evGoalOneCreated) as TimelineEntry.GoalCreated
        assertEquals(1L, created.goalId)
        assertEquals(goal, created.goalAccount)

        val harvest = mapper.map(evGoalZeroHarvest) as TimelineEntry.Harvest
        assertEquals(0L, harvest.goalId)
        assertEquals("primera cosecha (goal-flow 2026-08-03T15:29:48Z)", harvest.memo)
        assertEquals("primera cosecha (goal-flow 2026-08-03T15:29:48Z)", harvest.detail)
        assertEquals("Cosecha registrada", harvest.title)
    }

    @Test
    fun `maps deploy-time config events`() {
        val e = mapper.map(evConfig) as TimelineEntry.Configuration
        assertEquals("underlying_asset", e.setting)
    }

    @Test
    fun `drops events it does not recognize instead of crashing`() {
        val junk = ev(
            "0000000000000000000-0000000000", ct, 1, "2026-08-03T00:00:00Z", "ff",
            listOf("!!! not base64 !!!"), mapEmpty,
        )
        assertNull(mapper.map(junk))
        assertEquals(7, mapper.mapAll(ctEvents + junk).size)
    }

    @Test
    fun `mapAll sorts newest-first`() {
        val ids = mapper.mapAll(ctEvents).map { it.ledger }
        assertEquals(listOf(3953087L, 3953068L, 3953056L, 3953009L, 3950173L, 3950172L, 3950129L), ids)
    }

    // ---- the goal timeline -------------------------------------------------

    @Test
    fun `builds the goal timeline - two aportes, the meta merge, the creation`() {
        val t = GoalTimeline.build(
            ctEvents = ctEvents,
            goalMetaEvents = goalMetaEvents,
            source = EventSource.raizMemory(),
            latestLedger = 3953669L,
        )

        assertEquals(
            listOf(
                TimelineKind.CONTRIBUTION,  // 3953087 phone -> goal
                TimelineKind.GOAL_CREATED,  // 3952518 goal id 1
                TimelineKind.MERGE,         // 3950173 by the goal
                TimelineKind.CONTRIBUTION,  // 3950172 cli -> goal
            ),
            t.entries.map { it.kind },
        )
        assertEquals(2, t.contributionCount)
        assertEquals(listOf(phone, cli), t.contributors)
        assertEquals(2, t.contributorCount)
        assertEquals("2026-08-03T18:41:43Z", t.createdAtIso)
        assertEquals(3953669L, t.latestLedger)
        assertFalse(t.truncated)
    }

    @Test
    fun `the timeline never shows a deposit, a register or a config row`() {
        val t = GoalTimeline.build(ctEvents, goalMetaEvents, EventSource.raizMemory())
        assertTrue(t.entries.none { it.kind == TimelineKind.DEPOSIT })
        assertTrue(t.entries.none { it.kind == TimelineKind.REGISTRATION })
        assertTrue(t.entries.none { it.kind == TimelineKind.CONFIG })
    }

    @Test
    fun `goal id 0 is a dead placeholder and never leaks into the real goal`() {
        val t = GoalTimeline.build(ctEvents, goalMetaEvents, EventSource.raizMemory())
        // goal_meta holds a created AND a harvest for id 0; neither may appear.
        assertTrue(t.entries.filterIsInstance<TimelineEntry.Harvest>().isEmpty())
        assertTrue(
            t.entries.filterIsInstance<TimelineEntry.GoalCreated>().all { it.goalId == 1L },
        )
        assertTrue(t.entries.none { it.actor == placeholderGoalAccount })

        // ...and asking for goal 0 explicitly does show them (so the filter is
        // the id, not an accident of ordering).
        val zero = GoalTimeline.build(
            ctEvents, goalMetaEvents, EventSource.raizMemory(), goalId = 0L,
        )
        assertEquals(1, zero.entries.filterIsInstance<TimelineEntry.Harvest>().size)
    }

    @Test
    fun `registrations are opt-in`() {
        val with = GoalTimeline.build(
            ctEvents, goalMetaEvents, EventSource.raizMemory(), includeRegistrations = true,
        )
        assertEquals(1, with.entries.count { it.kind == TimelineKind.REGISTRATION })
    }

    @Test
    fun `a transfer to somebody else is not an aporte to this goal`() {
        val elsewhere = ev(
            "0016978379383279616-0000000009", ct, 3953088, "2026-08-03T19:29:20Z", "aa",
            listOf(symTransfer, addrPhone, addrCli), mapTransferCiphertext,
        )
        val t = GoalTimeline.build(ctEvents + elsewhere, goalMetaEvents, EventSource.raizMemory())
        assertEquals(2, t.contributionCount)
        assertTrue(t.contributions.all { it.to == goal })
    }

    @Test
    fun `a source that declares a retention floor is flagged as truncated`() {
        val t = GoalTimeline.build(
            ctEvents = listOf(evTransferPhone),
            goalMetaEvents = emptyList(),
            source = EventSource.rpcSimulation(),
            latestLedger = 3953669L,
            oldestLedger = 3951670L,
        )
        assertTrue(t.truncated)
        assertTrue(t.source.forgets)
        assertEquals(3951670L, t.oldestLedger)
        assertEquals(1, t.contributionCount) // the CLI aporte has been "forgotten"
    }

    @Test
    fun `an empty answer produces an empty, non-null timeline`() {
        val t = GoalTimeline.build(emptyList(), emptyList(), EventSource.raizMemory())
        assertTrue(t.isEmpty)
        assertEquals(0, t.contributionCount)
        assertNull(t.createdAtIso)
        assertTrue(GoalTimeline.EMPTY.isEmpty)
    }
}
