# ct-flow — the complete Confidential Token cycle from the CLI (Session 4)

Everything below **was actually executed against testnet on 2026-08-03** — every
tx hash and ledger number is real and clickable. The scripts live in
`scripts/ct-flow/` and consume the vendor demo SDK
(`/vendor/stellar-confidential-token-demo`, read-only) exactly the way
`scripts/prover-bench` does: import the built `packages/sdk/dist` modules and
resolve `@stellar/stellar-sdk` / `bb.js` / `noir_js` out of the vendor
workspace's own `node_modules`. No vendor file was modified. OZ contracts are
consumed as-is (vendor-built WASM + verification keys).

Secrets (deployer/Marta/goal S-keys, confidential scalars, auditor Grumpkin
secrets) live only in `C:\SP_WorkShop\.env.deploy` (gitignored). Public ids
live in `scripts/ct-flow/deployment.json`; per-run tx data in
`scripts/ct-flow/run-log.json`.

---

## 1. Decision: our own CT wrapper instance, not the official demo one

**We deploy and demo on OUR OWN instance** of the OZ confidential-token stack.
The official demo wrapper (`CBF64DEO…5N3F`) stays configured in Raiz Memory as
a second indexed contract, but the wallet and the video run against ours:

| Criterion | Official demo wrapper | Own instance (chosen) |
|---|---|---|
| Auditor registry | Admin = demo team. Registering a new view key fails with `Error(Contract, #2000)` = `AccessControlError::Unauthorized` — **verified with a real call** (see friction-report.md 2026-08-03). The *published-goal-view-key pattern is impossible there.* | We are admin: auditor id 0 = custodian, id 1 = the goal's dedicated view key. **This criterion alone decides it.** |
| Stability | Shared with every hackathon team; can be polluted/reset under us | Isolated; nobody else writes to it |
| Event history | `deployedAtLedger` 3013364 — already **older than the RPC retention floor**; the contract's early history is gone from the RPC (great for the pitch, bad for a reproducible demo) | Fresh (`deployedAtLedger` 3950128): the wrapper's **complete life** fits in the retention window today, so Raiz Memory can archive it from birth |
| Deployment cost | zero | ~10 txs, one script, ran first-try in ~3 min (see §3) |

Honest weight of the con: deploying is real complexity — but the vendor repo
ships prebuilt WASM + verification keys and its `scripts/deploy.ts` as a map,
so our `deploy.mjs` is a faithful mirror (minus the factory/policy contracts
the browser "advanced mode" needs and we don't).

## 2. Cast

| Role | Address (public) |
|---|---|
| Deployer / auditor admin | `GBLXWZQ5JVGV2UTPWNZCBVTMPIFKI736CENDR3UH2R3YSDUWXG6OMDDA` |
| **Marta** (contributor) | `GDUTRPFZAL3QRHCY47A6KAI6EK4XJTZ35J5IWI7YN3VGHHWA5F77DJ2I` |
| **Goal** (cuenta-meta) | `GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X` |

All friendbot-funded. Confidential key sets are derived per the SDK
(`deriveKeys(sk, addr_f)`, contract-bound) from scalars persisted in
`.env.deploy`, so the same personas survive across sessions.

## 3. Deployment (`node deploy.mjs`, from `scripts/ct-flow/`)

One command; requires `stellar` CLI (23.2.1 used) and Node ≥ 20. Output on
2026-08-03, everything first-try:

```
verifier = CBFCYFND44SNQPKMQNHB3KX2C7K4U5WSVUMFJY34OV46YAN2SACM3UIA
auditor  = CBUSX5B56KB73FAAIIHW7ISSZEGHDKQTOWML74LBPOWWGCEFEZPLHE25
token    = CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT   (deployedAtLedger 3950128)
underlying = CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC (XLM SAC)
VKs registered: register 7332e8a7… · withdraw 43776a7d… · transfer 7c1bcdf1… ·
                spender_transfer a9fc2d29… · set_spender 7ea783b5… · revoke_spender e993d8b2…
auditor id 0 (custodian, secret PRIVATE)          tx 6b8401a7…
auditor id 1 (goal view key, secret TO PUBLISH)   tx 40ac4dcf…
```

Deviation from vendor `scripts/deploy.ts`, on purpose: no `--optimize=false`
(gone from CLI 23.2.1 — friction logged), no CLI identity pollution (deployer
secret lives in `.env.deploy`), no factory/allowlist/blocklist, **two** auditor
ids instead of one — the whole point of owning the deployment.

## 4. The cycle (`node flow.mjs`) — canonical run, real outputs

Proving is local (bb.js UltraHonk, keccak transcript), multithreaded via the
SDK's own `setUltraHonkBackendLoader` hook (22 threads; the SDK default is 1 —
see friction report of Session 1). Proofs: 14,592 B each, 0.5–2.1 s on the PC.

| # | Step | Proof? | Tx (ledger) |
|---|---|---|---|
| 1 | `register(Marta, auditor_id=0)` | ZK, 1248 ms | [`27a71ac1…`](https://stellar.expert/explorer/testnet/tx/27a71ac1e03c02f32216c06600c009d4c59ef5b8a452f5c276fb16cb3cc9a522) (3950167) |
| 2 | `register(goal, auditor_id=1)` | ZK, 515 ms | [`30c9cf29…`](https://stellar.expert/explorer/testnet/tx/30c9cf299208171116391c0ab637a3dcb680c0a13603a24ef0e35936bff0d4e6) (3950168) |
| 3 | `deposit(Marta→Marta, 100 XLM)` | none | [`932590d0…`](https://stellar.expert/explorer/testnet/tx/932590d0a8b45ea2f9d5d57d9fa0dec13fc001450416be7419d6b57dd58494c9) (3950169) |
| 4 | `merge(Marta)` | none | [`9a3f25f5…`](https://stellar.expert/explorer/testnet/tx/9a3f25f5bf3e277de017bbd8e0da7393d215c7d240f26c7db404ae5a6b910945) (3950170) |
| 5 | `confidential_transfer(Marta→goal, 25 XLM)` | ZK, 1440 ms | [`58363138…`](https://stellar.expert/explorer/testnet/tx/5836313815618675a8530b3d3efb5e931e29ba9d49d58d90562414bc8c5463a4) (3950172) |
| 6 | `merge(goal)` ("cosechar") | none | [`1bbac2ee…`](https://stellar.expert/explorer/testnet/tx/1bbac2ee4bc85453933cabc8db98093927791557228cd91bb88635a7c361b97a) (3950173) |

Decrypted locally by the vendor `StateEngine` (pure event replay + ECDH) and
re-committed against the on-chain Pedersen commitments:

```
Marta spendable = 75 XLM (750000000 stroops)   matches chain: true
goal  spendable = 25 XLM (250000000 stroops)   matches chain: true
```

A second identical run (2026-08-03, while Raiz Memory watched — §7) skipped
both registers, accumulated balances to Marta 150 / goal 50 XLM (both
chain-verified), txs `07f9ee55…` (3950259), `e878f66a…` (3950260),
`d302c02f…` (3950262), `a19b9d39…` (3950263). `run-log.json` always holds the
latest run.

## 5. What the explorer sees (`node visibility.mjs`) — video material

Decoded from the actual envelopes via RPC; cross-checked against
`api.stellar.expert/explorer/testnet/tx/<hash>`:

**Visible on BOTH txs:** source account (Marta), fee, ledger, timestamp,
contract id, function name. Identity/participation is public **by design** —
that is the "solidaridad visible" half.

**`deposit` tx `932590d0…` — the public→confidential boundary, amount PUBLIC:**
- `arg[2] = i128(1000000000)` — plaintext 100 XLM in the invocation itself
- plus the underlying XLM SAC `transfer` event with the same plaintext amount
- plus the wrapper's `deposit` event carrying `amount: i128(1000000000)`

**`confidential_transfer` tx `58363138…` — amount NOWHERE:**
- `arg[0]`/`arg[1]` = Marta's and the goal's addresses (visible)
- `arg[2]` = 15,308 B opaque payload; the explorer can pretty-print its field
  *names* (`c_spend_new`, `c_tx`, `r_e`, `v_tilde`, `b_tilde`, `sigma`,
  `v_aud_r`, `r_aud_r`, `v_aud_s`, `b_aud_s`, `proof`) but every value is a
  32/64-byte field element / Grumpkin point / 14,592 B proof blob
- the `transfer` event repeats the same ciphertext map — no amount field exists
  in the whole transaction

Camera line: put the two stellar.expert tabs side by side — deposit shows
`1,000,000,000`, the aporte shows commitments.

## 6. The view key — what the preview supports TODAY (`node audit.mjs`)

Executed against the real chain events, with real keys:

1. **Goal view key = auditor id 1's Grumpkin secret** (`k1`, to be published in
   the README as "Verifícalo tú mismo"). `fetchEvents` + `auditTransferRecipientChannel(k1, ev)`
   decrypted the contribution from tx `58363138…`: **25 XLM**, matching the
   goal-side `StateEngine` decryption exactly. Summing deposits (public) +
   decrypted incoming transfers − outflows reconstructs the goal total
   **with no cooperation from anyone**: `25 XLM == owner-decrypted 25 XLM ✓`.
2. **Scoping, demonstrated:** `auditTransfer(k1).channelsAgree === false` — the
   published key does NOT open the sender channel; Marta's remaining balance is
   unreadable with it (her auditor is id 0).
3. **Custodian key `k0`** (private) opens the sender channel:
   amount 25 XLM + Marta post-balance 75 XLM. Cross-check `k0`-amount ==
   `k1`-amount: true.

**Honest limits (also in friction-report.md, 2026-08-03):**
- There are **no per-account view keys** in the preview. The unit is
  `auditor_id`, fixed per account at `register()`; minting ids is
  admin-gated on the auditor contract (verified `Error #2000 Unauthorized` on
  the official deployment). Our per-goal view key works because we admin our
  own instance — which is honestly *the* deployment model for Sobre del Barrio
  (one wrapper per community, goal accounts under the published id).
- Publishing `k1` reveals **each contribution's amount** (the recipient
  channel opens per-transfer, amount + `r_tx`), not only the total. "Los
  aportes son secretos" therefore means: hidden on-chain and from anyone
  *without* the goal's view key; the glass box shows its itemized inside to
  whoever picks up the published key. Contributor *balances* stay private
  regardless. The README must state this; no fallback from propuesta A §10 was
  needed — the auditor-id pattern is real today.

## 7. Raiz Memory verification (real run, then killed — no orphans)

Our wrapper id is appended to `raiz-memory/.env` `CONTRACT_IDS` (existing ids
kept). Verification run with a dedicated DB:

```
cd raiz-memory
PORT=8093 DATABASE_URL="sqlite://ct_flow_s4.db?mode=rwc" \
  CONTRACT_IDS=CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT \
  ./target/debug/raiz-memory.exe
```

With the indexer live, flow run #2 executed; ~10 s later:

- `GET /coverage` → `eventCount: 4, firstEventLedger: 3950259, lastEventLedger: 3950263`
- `GET /events?contractId=CBWSAN…` → getEvents-shaped JSON with all 4 events
  (`deposit`, `merge`, `transfer`, `merge`), verbatim XDR topics/values — the
  transfer event's value carries the full auditor-ciphertext map, i.e. exactly
  the payload INDEXER.md requires an archive to preserve for recovery.

Process killed afterwards; port 8093 verified free. Note for the team: the
ingestor's first run starts at the *current* ledger, so start the production
instance (port 8091 config) soon — backfill idea filed in BACKLOG.md.

## 8. Files

| Path | What |
|---|---|
| `scripts/ct-flow/_shared.mjs` | vendor-SDK plumbing, retries, .env.deploy I/O |
| `scripts/ct-flow/deploy.mjs` | our-instance deployment (mirrors vendor deploy.ts) |
| `scripts/ct-flow/flow.mjs` | the full cycle §4 |
| `scripts/ct-flow/audit.mjs` | view-key decryption §6 |
| `scripts/ct-flow/visibility.mjs` | envelope decoding §5 |
| `scripts/ct-flow/deployment.json` | public contract ids (source of truth for the app) |
| `scripts/ct-flow/run-log.json` | latest run's tx hashes/ledgers/balances |
| `C:\SP_WorkShop\.env.deploy` | ALL secrets (gitignored, testnet play-money) |
