# Sobre del Barrio × Raiz Memory
### The first mobile Confidential Tokens wallet — and the durable event index that keeps it alive

> *Contributions are sealed. The fund is made of glass. And the wallet remembers.*

**Stellar Summit SP 2026 · Special Bounty: Confidential-Token & Private-Payment Wallets · Team: Raiz Protocol**

---

## What this is

In Latin American neighborhoods everyone sees *who* contributes to a common cause — that's solidarity. How *much* each person gives is nobody's business — that's dignity. **Sobre del Barrio** ("the neighborhood envelope") encodes that social norm with OpenZeppelin's **Confidential Tokens**: an Android wallet where neighbors and tourists contribute to community goals with encrypted amounts and visible participation, while each goal's **auditor view key is published openly** so anyone can independently verify the fund's total. Individual privacy, collective transparency.

A privacy wallet reconstructs its history from contract events — and RPC nodes forget them after ~7 days. So this submission ships with its own memory: **Raiz Memory**, a durable event index any wallet (ours, or an SPP wallet — it indexes ciphertext, it can't read your amounts) can point at by changing one URL.

This covers examples (a) *private balance display wallet* and (c) *durable event indexer* of the bounty brief as one integrated system.

## Repository layout

```
/wallet        Android app (Kotlin) — CT layer over the RAÍZ codebase
/contracts     goal_meta (Soroban) — community goal registry; amounts NEVER touch it
/raiz-memory   Rust indexer — getEvents-shaped API beyond the 7-day window
/scripts       verify-goal-total — decrypt the goal balance with the PUBLISHED view key,
               outside the app: don't trust our UI, check the chain yourself
```

## Quickstart

```bash
# Raiz Memory (indexes the CT wrapper + goal_meta on testnet)
cd raiz-memory && cp .env.example .env   # set RPC_URL + CONTRACT_IDS
cargo run                                 # or: docker compose up
curl localhost:8090/health
curl "localhost:8090/events?contractId=C...&startLedger=0"

# goal_meta contract
cd contracts/goal-meta && cargo test
stellar contract build && stellar contract deploy --network testnet ...

# Wallet: open /wallet in Android Studio, set EVENT_SOURCE_URL to your Raiz Memory
```

## How Confidential Tokens are used (the full cycle, on a phone)

| CT operation | In Sobre del Barrio | UX name |
|---|---|---|
| register | First-run setup of an account into the CT system | "Abrir mi sobre" |
| deposit | Public XLM → confidential balance | "Sellar" |
| transfer | Confidential contribution to the goal account | "Aportar" |
| merge | Goal admin folds pending contributions into available balance | "Cosechar" (public timeline event) |
| withdraw | Confidential → public XLM | "Abrir el sobre" |
| auditor view key | **Inverted**: the goal's auditor is *the public* — key published in-app and in this README | "Verifícalo tú mismo" |
| selective disclosure | Contributor's private receipt of their own contribution | "Mi recibo" |

Proofs are generated **on-device** in an isolated WebView running the unmodified CT proving stack, bridged to Kotlin (see `/wallet/.../ProverWebViewBridge.kt`). Key custody, signing and submission stay in the native wallet manager.

## Reused vs. original (in the spirit of "100% original work")

**Reused, with credit, unmodified:** OpenZeppelin's Confidential Token contracts and proving stack; the pre-existing RAÍZ app base (our own project — payments, passkeys via OZ smart accounts kit — declared as prior work).
**Original, built for this bounty:** the `goal_meta` contract; the entire mobile CT layer (WebView prover bridge, CT state management on Android, the three screens); the public-view-key transparency pattern; Raiz Memory (design + implementation); the friction report filed as issues on the CT repo: [links].

## Limitations (read this before judging the demo kindly)

- Confidential Tokens are a **developer preview: testnet only, unaudited** — the app says so on screen. No real money.
- The goal's view key is published by the goal's custodian (this demo: the team). The production design places custody in RAÍZ's communal smart account (passkeys + policies, Protocol 27 delegation) — linked in our roadmap, not faked here.
- Contributor **identities are visible by design** (that's CT's model, and here it's the feature: solidarity is public). If you need hidden identities too, that's SPP's territory — and Raiz Memory already serves SPP wallets as well.
- WebView proving is a pragmatic bridge, not the endgame (native proving à la mopro when the mobile Noir stack matures).
- Raiz Memory declares its coverage honestly at `/coverage` — including where its history starts. An indexer that lies by omission is worse than none.

## Demo video

[link] — 2:30: a contribution whose amount the explorer cannot show · the goal total verified from outside the app with the published view key · the RPC "forgetting" the timeline and Raiz Memory restoring it by changing one URL.

## After the summit

This is Phase 6.2 of the RAÍZ roadmap (private contributions, public aggregates) pulled six months forward, and the first brick of the neighborhood indexer our DePIN watcher design needs. The work stays on our critical path either way — that's what a good hackathon bet looks like.

---
*Raiz Protocol — community savings on Stellar. The value the neighborhood creates stays in the neighborhood, and the neighborhood decides.*
