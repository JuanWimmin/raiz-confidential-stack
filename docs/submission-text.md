# GrantFox submission text — Sobre del Barrio × Raiz Memory

> Paste-ready blocks for the Stellar Summit SP 2026 submission form
> (Special Bounty: *Confidential-Token & Private-Payment Wallets*).
> Every number below is traceable to `README.md`; nothing here is new prose about
> unbuilt work. Chain-dependent figures carry the date they were measured, because
> they move — re-run them on submission morning and update if they changed.

---

## 1 · One-line pitch (pick one, all under 140 characters)

**A.** *(115 chars)*
```
Sealed contributions, a glass fund, a wallet that remembers: mobile Confidential Tokens plus a durable event index.
```

**B.** *(116 chars)*
```
An Android Confidential-Tokens wallet for community funds, and the durable event index that keeps its history alive.
```

**C.** *(112 chars)*
```
Everyone sees who contributed; nobody sees how much; anyone can verify the total. On a phone, on testnet, today.
```

**D.** *(123 chars)*
```
Confidential contributions from a 4 GB Android phone, a fund anyone can audit, and an index that outlives the RPC's 7 days.
```

**E.** *(134 chars)*
```
A mobile CT wallet and its durable event indexer: amounts encrypted, the fund total publicly verifiable, the history kept past 7 days.
```

Recommended: **C** if the form shows the line next to the title (it is the idea);
**D** if it is the only text a judge sees before clicking (it is the evidence).

---

## 2 · Short description (~50 words, for a card or list view)

```
An Android wallet for neighbourhood fundraising built on OpenZeppelin's Confidential
Tokens: contribution amounts encrypted, participants public, and the goal's auditor
view key published on purpose so anyone can verify the fund. Shipped with Raiz Memory,
a durable getEvents-shaped index that serves the history an RPC forgets after ~7 days.
```

---

## 3 · Full submission text (645 words)

```
Sobre del Barrio × Raiz Memory — Raiz Protocol

In a Latin American neighbourhood everyone sees who contributes to a common cause;
that is solidarity. How much each person gives is nobody's business; that is dignity.
Sobre del Barrio ("the neighbourhood envelope") puts that norm on-chain with
OpenZeppelin's Confidential Tokens: an Android wallet where the amount of a
contribution is encrypted, participation stays public, and the goal's auditor view key
is published on purpose so anyone can verify the fund without asking us for anything.

It covers two of the bounty's example builds as one system, because they need each
other. A confidential wallet has no balance to read: it rebuilds its state by replaying
contract events, and Soroban RPC nodes drop events after about 7 days. So this
submission ships its own memory — Raiz Memory, a durable getEvents-shaped index that
any wallet adopts by changing one URL.

Evidence, in the order you can check it:

- Four real testnet transactions from a 4 GB phone (Vivo Y21, Android 13, 2026-08-03),
  all SUCCESS, against our own CT wrapper: register (e7f9309a…, 27.5 s), deposit
  (2ff5108e…), merge (0dcbefb4…) and a 5 XLM confidential_transfer to the goal
  (7f9c6f9a…, 28.6 s, of which 17.0 s was a zero-knowledge proof computed on the
  device). The WebView only proves and builds and returns an unsigned envelope; the
  seed lives in EncryptedSharedPreferences, and Kotlin signs and submits. The phone
  then decrypted its own balance to exactly 50,000,000 stroops.

- The goal total, verified outside the app. One command in the repo
  (scripts/verify-goal-total) talks only to the public RPC and the published view key,
  and prints "Goal total: 63 XLM — verified on-chain at ledger 3968046" (2026-08-04).
  Decryption alone would prove nothing, so the script authenticates the published key
  against two independent on-chain records, decrypts each contribution into a full
  Pedersen opening, and re-commits those openings against the goal's live on-chain
  commitments.

- A contributor receipt: selective disclosure with the preview's real disclose_sender
  circuit, every public input re-read from the ledger, verifying "for exactly 25 XLM".
  Flip one byte of the proof and it is REJECTED.

- Raiz Memory, measured 2026-08-04: 4,425 events archived, of which 911 sit below the
  RPC's retention floor — 36 from the official CT demo wrapper, 875 from the EURC SAC
  that Nethermind's SPP pool settles through. No public RPC can return those 911 at any
  startLedger. The official CT demo wrapper was deployed at ledger 3013364; ask a
  public RPC for its history and it answers "startLedger must be within the ledger
  range: 3847121 - 3968080". The reference implementation of Confidential Tokens has
  already lost its own early history.

The limits, in the same breath. Confidential Tokens are a developer preview: testnet,
unaudited, no real money. deposit is public by construction — moving XLM into a
confidential balance reveals that amount; only the contribution itself is confidential.
The published view key opens the fund contribution by contribution, not just the total:
that is deliberate, and it is stated in the README's first section, not buried in the
last. It cannot read the sender side, so contributor balances stay private with it —
but contributors register under auditor id 0, a custodian key this deployment holds, so
"private from us" is not a claim we make. And Android WebView never becomes
crossOriginIsolated, so proving there is single-threaded: 10–17 s per proof, which the
UI counts out loud instead of hiding.

Friction, filed back: friction-report.md logs 15 frictions with verbatim errors,
versions and repro steps, and 10 are written up as ready-to-file issue drafts naming
their target repo (5 CT demo, 1 OpenZeppelin/stellar-contracts, 2 stellar-rpc, 2
stellar-cli). None has been posted: filing a bug on a stranger's repo is a human's call,
one by one. A preview's most useful hackathon output is a precise bug report.

Contributions are sealed. The fund is made of glass, on purpose. And the wallet
remembers.
```

---

## 4 · Required links block

| Field | Value |
|---|---|
| **Repository** (required) | `https://github.com/JuanWimmin/raiz-confidential-stack` |
| **Release / installable APK** | `https://github.com/JuanWimmin/raiz-confidential-stack/releases/tag/v0.1.0-summit` |
| **Demo video** | `<<< PASTE VIDEO URL HERE AFTER RECORDING >>>` |
| **Landing page** | `https://raizapp.xyz/sobre.html` |

Notes to paste alongside, if the form has room for them:

- **APK caveat (say it, do not let a judge discover it):** debug-signed, testnet only,
  SHA-256 `9e87100c5a3dd9c7c42641971eedd802a9d1a0b36d2e7d890a144dfc9670535c`
  (26,557,979 bytes). The goal timeline needs a Raiz Memory instance: the build points
  at `http://localhost:8091`, so either run the indexer and `adb reverse tcp:8091
  tcp:8091`, or paste any reachable base URL into **⋮ → Ajustes → Fuente de eventos**.
  Full instructions on the release page.
- **Landing page:** live at <https://raizapp.xyz/sobre.html>, served from the team's
  existing GitHub Pages site (`JuanWimmin/JuanWimmin.github.io`), so it does not depend
  on the laptop this was built on. Source is `web/index.html` in the repo — one
  self-contained file, no build step. **Still to do before sending:** set `YT_ID` in
  both copies once the video is up, so the page embeds it instead of showing the
  placeholder.
- **Public Raiz Memory instance:** an ephemeral cloudflared quick tunnel
  (`scripts/serve-public.ps1`) — the hostname changes on every start, so no permanent
  URL is promised. The reproducible route is the container in `docs/deploy-public.md`.
  Only paste a tunnel URL into the form if it is up at the moment of sending.

**Before pasting the form:** re-run `verify-goal-total` and the retention-error snippet.
The 63 XLM total, the ledger numbers and the 4,425 / 911 event counts all move; the
text says when they were measured, and a judge running it should see *more*, never less.

---

## 5 · What to say if a judge asks X

### Q1. "Isn't this just the OpenZeppelin demo with a skin?"

No, and the difference is checkable. The demo is a desktop Next.js app that signs with
Freighter — a browser extension that does not exist on mobile, which is why the demo's
flow cannot run on a phone at all. What we consume unmodified is the *cryptography*:
OZ's contracts (`stellar-contracts` @ `9b5ed96`) deployed as-is, and the demo's proving
stack (`@aztec/bb.js` 0.87.0, `noir_js` 1.0.0-beta.9), witness builders and disclosure
artifacts (@ `ac67499`). No vendor file was edited.

Everything between is ours: a headless WebView prover bridged to Kotlin, native key
custody and Ed25519 signing (validated byte-exact against `@stellar/stellar-sdk` 14.6.1
with a generated fixture and JVM tests), the `goal_meta` contract (13 tests), Raiz
Memory (15 tests), the published-view-key pattern, the three screens, and the four
verification scripts. The demo has no goal contract, no indexer, no receipt CLI, no
in-wallet verification, and it has never run on a phone.

One concrete thing we gave back: the demo's own `CircuitProver` constructs
`UltraHonkBackend(bytecode)` with no options, and bb.js 0.87 defaults to `{threads: 1}`
— so the official demo proves single-threaded even when cross-origin isolation succeeds.
That is a 2–3× speedup left on the table, it is in our friction report, and our shim
passes threads explicitly.

### Q2. "Why should I trust your numbers?"

Don't. That is the design. Fastest ways to disprove us, in ascending cost:

1. Open the four transaction hashes on stellar.expert. They resolve, they say SUCCESS,
   and the `confidential_transfer` has no amount anywhere on the page — account,
   contract, signature, fee, and a 15,308-byte opaque payload.
2. `node scripts/verify-goal-total/verify-goal-total.mjs`. It never contacts our app or
   any server we run — public RPC plus the view key we published. The check itself runs
   in ~2 s; the one-time cost is the vendor clone and `build:sdk`.
3. `cd raiz-memory && cargo test` — 15 tests, offline, no RPC, throwaway SQLite. Then
   `curl localhost:8090/coverage` and compare with what the public RPC will still serve.

Chain-dependent numbers are stamped with the moment they were measured (63 XLM at
ledger 3968046; 4,425 events with 911 below the floor, 2026-08-04) precisely because
they move. If your run shows a bigger total and more unreachable events, that is the
system working, not a discrepancy.

### Q3. "What did YOU build versus what did you consume?"

README §7 answers this file by file, and it is auditable rather than asserted.

- **Consumed unmodified, with credit:** OpenZeppelin's CT wrapper, auditor registry and
  Noir circuits (`9b5ed96`); the demo's proving stack, SDK witness builders, auditor
  decryption and `disclose_sender` artifacts (`ac67499`); Nethermind's repo read-only
  (`a1bf177`) for the retention quote and the deployment file naming the EURC SAC.
- **Declared prior team work:** 13 files of RAÍZ's design system (our own pre-existing
  Android app), each carrying an in-header note naming its RAÍZ source and any edit. The
  copy plan, `docs/raiz-reuse-plan.md`, was written before a line was copied, and RAÍZ's
  Stellar stack was deliberately *not* adopted.
- **Original for this bounty:** `goal_meta`; Raiz Memory including its `getEvents`-shaped
  API, `/coverage`, the clamped backfill and the purge-demo mode; the entire Android CT
  layer (prover bridge, APK-packaged proving assets, the JS shim with in-wallet
  verification, key custody, signing, RPC submission); the three screens and their data
  layer; the published-view-key deployment pattern; `scripts/` (ct-flow,
  verify-goal-total, receipt, prover-bench, goal-flow.sh); and the friction report with
  its 10 issue drafts.

We deployed our own instance of the OZ stack for one reason, and it is verifiable: on
the official deployment the auditor registry is admin-gated and registering a view key
fails with `Error(Contract, #2000)` = `AccessControlError::Unauthorized`, so the
published-view-key pattern is impossible there.

### Q4. "Why does it say *sin verificar* when I switch sources — is it broken?"

That is the feature, and it is the centre of the demo. The Meta screen does not trust a
number it was handed: it replays the goal's events, decrypts each contribution with the
published view key, and re-commits the openings against the goal's live on-chain Pedersen
commitments — five checks, printed where other wallets print a number and ask for trust.

Point it at a source with a 7-day-style retention window (the red **RPC (simulado)**
chip) and the replay silently loses the earlier contributions, both harvests and even the
goal's creation. The re-commitment then mismatches —
`commit(Σ spendable openings) == on-chain spendable commitment: MISMATCH` — so the wallet
refuses to print a total and names the failing check instead. Measured through the app on
2026-08-04: Raiz Memory → 8 timeline entries and a verified **59 XLM**; the forgetful
source → 2 entries and **sin verificar**. Tap the green chip and it comes back whole in
~3.8 s.

The money is still on-chain; what is missing are the events. A wallet that printed the
number anyway would be guessing. Refusing to print an unprovable number is the point of
the whole submission.

### Q5. "Why no SPP wallet?"

Because the bounty says CT *and/or* SPP, and because building a second, unrehearsed
privacy stack in four days would have cost us the one that works. CT's model — visible
identities, hidden amounts — is also exactly right for a neighbourhood fund, where seeing
who showed up *is* the product.

SPP is covered where we can genuinely serve it: infrastructure. Raiz Memory indexes any
contract id — one line of `CONTRACT_IDS` — and it is Nethermind's own README (L121) that
documents the ~7-day retention limitation the indexer exists to fix. Precisely: their
testnet pool contracts have emitted zero events so far (live `getEvents` from their
deployment ledger 3899359 returns `[]`), so what we actually index beside them is the
EURC SAC the pool settles through — 875 of our archived events that no RPC can return.

And it is not their `tools/bootnode` again: that is a deployment-scoped archive proxy for
one SPP wallet that hands the client back to an RPC with a `-32002` once the request is
inside the retention window. Raiz Memory is a general durable index — any contract, no
handoff, one endpoint for a contract's whole life, plus `/coverage` stating honestly what
it holds and what was already gone before it existed.

### Bonus — if a judge runs `git log`: "was this written by AI?"

Built with AI assistance (Claude Code); every commit records it in a trailer, and we did
not strip them. The design, the architecture, the deployments, the on-device runs and
every verification in this repo are the team's own, and all of it is checkable against
testnet rather than taken on our word.
