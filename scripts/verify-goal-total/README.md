# verify-goal-total — "don't trust our UI"

Independently reconstructs **and verifies** the confidential fund total of a
Sobre del Barrio community goal, using nothing but:

- the public Stellar testnet RPC (or any getEvents-compatible endpoint,
  e.g. a Raiz Memory instance via `--rpc`), and
- the goal's **published** auditor view key `k1` — a Grumpkin secret scalar we
  publish on purpose ("the fund is glass").

It does **not** talk to our app, our indexer defaults, or any server we run.

## What "verified" means here (not just decrypted)

Decryption alone would prove nothing — a malicious script could print any
number. This script proves the number against the chain in three steps:

1. **Key authenticity.** The published secret `k1` is checked against two
   independent on-chain records: the view-key point stored in the `goal_meta`
   registry for this goal, and the CT auditor-registry key for the goal
   account's `auditor_id`. Both must equal `k1·H`.
2. **Openings, not just amounts.** Each incoming confidential transfer's
   recipient auditor channel is decrypted with `k1`, which yields the amount
   **and** the per-transfer Pedersen randomness `r_tx` — a full opening of the
   contribution commitment (vendor SDK `auditor/decrypt.ts`). Deposits cross
   the public SAC boundary with `r = 0`.
3. **Pedersen re-commitment.** The accumulated `(Σv, Σr)` openings are
   re-committed with the protocol's Pedersen generators (`v·G + r·H`) and the
   resulting curve points are compared against the goal account's live
   on-chain `spendable` / `receiving` balance commitments. Pedersen
   commitments are binding: a match means these amounts are the ones the chain
   itself is committed to.

The script prints each contribution (the published `k1` opens the recipient
channel of **every** transfer to the goal, so per-contribution amounts are
visible to any key holder — the documented trade-off of this pattern;
contributor *balances* remain private) and ends with one line:

```
Goal total: X XLM — verified on-chain at ledger N
```

Honest edge case: after an *outflow* from the goal (outgoing transfer or
withdraw), the spendable commitment is re-randomized with owner-only
randomness. The script keeps tracking the *value* through the `k1` sender
channel but says plainly that point-verification stops there.

## Setup from a fresh clone (~15 minutes, most of it one `pnpm install`)

Prerequisites: Node >= 20 (tested on Node 25) and git. No Rust, no wasm, no
proving backend — this script only decrypts and re-commits, so it never loads
bb.js.

From the repository root:

```sh
# 1. Vendor the CT demo repo (read-only dependency; ~1 min)
git clone https://github.com/brozorec/stellar-confidential-token-demo \
    vendor/stellar-confidential-token-demo
git -C vendor/stellar-confidential-token-demo checkout ac67499

# 2. Install its workspace + build the SDK (the dist/ this script imports; ~5 min)
#    npx pins pnpm to the repo's own packageManager version — Node 25 no longer
#    bundles corepack, so a bare `pnpm` may not exist on your machine.
cd vendor/stellar-confidential-token-demo
npx -y pnpm@10.33.0 install
npx -y pnpm@10.33.0 build:sdk
cd ../..

# 3. Run the verifier (defaults = our deployment, documented in config.json)
node scripts/verify-goal-total/verify-goal-total.mjs
```

### Why the vendor dependency instead of inlined crypto

The decision was made on real dependency weight. The script needs Grumpkin
curve arithmetic, the Poseidon2 sponge with the protocol's exact domain
separators, soroban-sdk-26 event XDR parsing, and RPC plumbing. All of that
exists, tested, in the demo SDK the CT team ships — and consuming it
**unmodified** is itself part of the verification story: the decryption logic
is the vendor's, not ours, so we cannot have bent it to flatter our numbers.
Inlining would mean hand-reimplementing Poseidon2/Grumpkin (hundreds of lines
of novel crypto for a judge to audit) to save one `pnpm install`. Not worth it.

## Options

```
--view-key 0x…    published goal view key secret (default: config.json)
--token C…        CT wrapper contract id
--goal G…         goal account (the CT-registered recipient of contributions)
--from-ledger N   first ledger to replay (default: wrapper deploy ledger)
--goal-meta C…    goal_meta registry contract (checked against k1; optional)
--goal-id N       goal id inside goal_meta (default 1 — the real demo goal;
                  id 0 is an early placeholder created before the view key existed)
--rpc URL         any getEvents-compatible endpoint — the Stellar RPC or a
                  Raiz Memory instance (this is how history older than the
                  RPC's ~7-day retention window stays verifiable)
```

## Real output (2026-08-03, testnet ledger 3952632)

```
token       = CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT
goal        = GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X
view key k1 = 0x0066c14835195705220b7be6f1146aec9cecfb6ecb2b6667bd1d234234afdb16  (published on purpose)

[1] cross-checking the published secret against on-chain state
  goal_meta[1] "Techo de la casa comunal" (target 500 XLM)
    stored view-key point == k1·H                      OK
  goal registered under auditor_id 1; registry key == k1·H   OK

[2] replaying confidential events from ledger 3950128
  + ledger 3950172  aporte   from GDUTRP…DJ2I  25 XLM (decrypted via k1)  tx 58363138…
  · ledger 3950173  merge (cosecha): pending folded into spendable
  + ledger 3950262  aporte   from GDUTRP…DJ2I  25 XLM (decrypted via k1)  tx d302c02f…
  · ledger 3950263  merge (cosecha): pending folded into spendable

[3] re-committing decrypted openings against on-chain Pedersen commitments
  commit(500000000, Σr_tx) == on-chain spendable commitment   OK
  commit(0, Σr_tx) == on-chain receiving commitment   OK

Goal total: 50 XLM — verified on-chain at ledger 3952632
```
