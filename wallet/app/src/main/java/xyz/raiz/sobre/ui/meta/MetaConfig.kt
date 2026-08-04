package xyz.raiz.sobre.ui.meta

/**
 * The goal's PUBLIC facts. Every value here mirrors
 * `scripts/verify-goal-total/config.json` — the file a judge runs the external
 * verifier from — and every one of them is public on purpose, the view key
 * included: publishing the goal's auditor view key IS the design ("el fondo es
 * de vidrio"). The custodian key (auditor id 0) is not here and never will be.
 *
 * These constants were living in the Session 5 debug screen behind a
 * `// TEMPORARY (Task C)` fence; that Activity is gone now. They belong in
 * `wallet/CtConfig.kt` next to the contract ids, which is another agent's file
 * in this session — moving them there is a one-commit cleanup afterwards.
 */
object MetaConfig {

    /** goal_meta on testnet (Session 3). */
    const val GOAL_META = "CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ"

    /**
     * THE REAL GOAL. Id 0 is a dead placeholder from the first goal-flow run
     * (its three harvest events are on chain and must never be shown here).
     */
    const val GOAL_ID = 1L

    /** The goal's auditor view key (auditor id 1), published on chain and here. */
    const val VIEW_KEY_HEX =
        "0x0066c14835195705220b7be6f1146aec9cecfb6ecb2b6667bd1d234234afdb16"

    /** Fallbacks until `goalTotal` reads the real strings off goal_meta. */
    const val FALLBACK_NAME = "Techo de la casa comunal"
    const val FALLBACK_TARGET_STROOPS = 500L * 10_000_000L // 500 XLM, display target

    /** What a judge types to check the total without trusting this app. */
    const val VERIFY_COMMAND = "node scripts/verify-goal-total/verify-goal-total.mjs"
}
