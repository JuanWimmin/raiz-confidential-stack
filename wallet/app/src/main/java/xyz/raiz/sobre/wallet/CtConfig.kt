package xyz.raiz.sobre.wallet

/**
 * Public facts of OUR Confidential Token deployment on testnet — mirrors
 * scripts/ct-flow/deployment.json (the source of truth, Session 4). Everything
 * here is public: contract ids, the goal's G-address, network constants.
 * Secrets never live in code (project rule: .env.deploy or
 * EncryptedSharedPreferences only).
 */
object CtConfig {
    const val RPC_URL = "https://soroban-testnet.stellar.org"
    const val FRIENDBOT_URL = "https://friendbot.stellar.org"
    const val PASSPHRASE = "Test SDF Network ; September 2015"

    /** Our CT wrapper over the XLM SAC (deployed Session 4, ledger 3950128). */
    const val TOKEN = "CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT"
    const val VERIFIER = "CBFCYFND44SNQPKMQNHB3KX2C7K4U5WSVUMFJY34OV46YAN2SACM3UIA"
    const val AUDITOR = "CBUSX5B56KB73FAAIIHW7ISSZEGHDKQTOWML74LBPOWWGCEFEZPLHE25"

    /** First ledger of the wrapper's life — event replay starts here. */
    const val DEPLOYED_AT_LEDGER = 3950128

    /** The goal account (cuenta-meta), registered with auditor id 1 — the
     *  published "Verifícalo tú mismo" view key. */
    const val GOAL_ACCOUNT = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X"

    /** Contributors register under the custodian auditor id (kept private). */
    const val CONTRIBUTOR_AUDITOR_ID = 0

    const val EXPLORER_TX = "https://stellar.expert/explorer/testnet/tx/"
}
