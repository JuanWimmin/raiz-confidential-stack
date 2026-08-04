# GrantFox — "Describe what you built and how it meets the deliverables"

Paste-ready. Figures measured 2026-08-04; re-run `scripts/verify-goal-total`
before sending, since the total and the retention floor both move.

---

**Sobre del Barrio × Raiz Memory — a mobile Confidential Tokens wallet, and the durable event index that keeps its history alive.**

In Latin American neighbourhoods everyone sees *who* puts money into a common pot — that is solidarity. How *much* each person puts in is nobody's business — that is dignity. We encoded that norm with OpenZeppelin's Confidential Tokens: an Android wallet where neighbours contribute to a community goal with the amount encrypted on-chain, participation public, and the goal's auditor view key **published on purpose** so anyone can audit the fund without asking us for anything.

**We built two of the three example builds, as one system**, because separately neither works. A confidential wallet has no balance to read on-chain — only Pedersen commitments and ciphertext inside events — so it reconstructs its state by replaying those events. Soroban RPC nodes drop them after about seven days. A CT wallet that cannot re-read its history does not know what it holds. So the wallet ships with its own memory.

**(a) Private balance display — the wallet.** The full CT cycle runs on a real 4 GB Android phone against testnet, keys never leaving it: `register`, `deposit`, `merge` and a confidential `transfer` to the goal, all `SUCCESS` on-chain. Zero-knowledge proofs are generated on the device (10–17 s), in a headless WebView that holds no key and persists nothing; the seed lives in Android's EncryptedSharedPreferences and Kotlin does the signing and submission. Balances decrypt locally. The contribution's amount appears nowhere in the transaction — the payload is a 15,308-byte blob of commitments, ciphertext and proof, with no amount field for an explorer to show. `deposit` is public by construction, and we say so.

**(c) Indexer — Raiz Memory.** A durable `getEvents`-shaped index in Rust that any wallet adopts by changing one URL. It backfills history rather than starting at the chain head, and `/coverage` reports honestly what it holds — including how much was already unreachable when indexing began. Right now it archives **4,456 events across four testnet contracts, 937 of which sit below the RPC's retention floor** (ledger 3847890 today) and can no longer be returned by any public RPC. That number grows every hour, because the floor does. The reference CT demo itself deployed at ledger 3013364 and has already lost its own early history — ask the public RPC and it answers `startLedger must be within the ledger range`.

**What makes this checkable rather than claimed.** The fund's total is not something you take our word for. `scripts/verify-goal-total` runs outside the app, against the public RPC and the published key alone: it authenticates that key against two independent on-chain records, decrypts each contribution into a full Pedersen opening, re-commits the sum and compares it to the live on-chain commitment. It prints `Goal total: 63 XLM — verified on-chain at ledger 3968849`. Point the wallet at a truncated event source and it **refuses to print a total at all**, naming the check that failed — because Pedersen commitments are binding, a partial history cannot be made to match. A decorative verifier would have shown a smaller number and nobody would have noticed. There is also a real selective-disclosure receipt: a contributor proves she sent exactly 25 XLM in a specific transaction, verifiable by a third party and rejected if a single proof byte is flipped.

**Deliverables.** The repository is public, MIT-licensed, with a `NOTICE.md` crediting every dependency from its actual license file. Every contract id, transaction hash and timing in the README is real and clickable; four Mermaid diagrams explain the privacy model, the retention problem, one contribution end to end, and the verification chain. An installable debug APK is attached to release `v0.1.0-summit` with its SHA-256, so no judge has to build the vendor→pnpm→Gradle chain. A 2:30 demo video is linked below. Reproducing our central claim takes about two minutes: clone, install, run the verifier.

**On originality.** OpenZeppelin's CT contracts and the demo's proving stack are consumed **unmodified** and pinned by commit; no vendor file was edited. RAÍZ, our pre-existing app, contributed 11 design-system files, each named in the README with its provenance in the header. Everything else was written for this bounty: the `goal_meta` contract (13 tests), Raiz Memory (15 tests), the whole Android CT layer, the published-view-key deployment pattern, three screens, and four scripts.

**And the limits, stated in the same breath, because that honesty is part of the pitch.** Confidential Tokens are a developer preview: testnet only, unaudited, no real money. `deposit` reveals its amount. The published view key opens the fund *contribution by contribution*, not merely the aggregate — that is what "made of glass" means here, and it is deliberate. Contributors register under an auditor id that our team currently holds, so "private from us" is not a claim this deployment can make; a real community deployment must decide who holds that key. We logged 15 frictions with verbatim errors while building, wrote 10 up as upstream issues, re-verified them, **and filed only five** — the other five did not survive checking, and one would have reported our own stale tooling as somebody else's bug.

*Contributions are sealed. The fund is made of glass, on purpose. And the wallet remembers.*

---

## Links to paste

| Field | Value |
|---|---|
| Repository | https://github.com/JuanWimmin/raiz-confidential-stack |
| Demo video | *(fill after recording)* |
| Landing | https://raizapp.xyz/sobre.html |
| Installable APK | https://github.com/JuanWimmin/raiz-confidential-stack/releases/tag/v0.1.0-summit |

## If the field is short

Use the first paragraph, the two build paragraphs, and the "checkable rather
than claimed" one. Drop originality and limits only if forced — and if you drop
the limits, drop the originality paragraph first: a judge who finds an
undisclosed limit trusts nothing else on the page.
