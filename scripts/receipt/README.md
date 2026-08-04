# Mi recibo — selective disclosure of one contribution

Marta contributed 25 XLM to "Techo de la casa comunal". On-chain, that amount
exists only as Pedersen commitments and ciphertexts. **Mi recibo** lets her
prove — to exactly one party of her choosing, and no one else — that *she*
sent that specific on-chain transfer and what its amount was, without
revealing her balance, her history, or anything to bystanders.

This is the vendor CT preview's off-chain selective-disclosure layer used
**as-is** (no forks, no honest-alternative fallback needed — the preview
really can do this today):

- Circuit: `disclose_sender` (SELECTIVE_DISCLOSURE.md §7, D-sender —
  *"this on-chain payment was sent by me for this amount"*), from
  `/vendor/stellar-confidential-token-demo/packages/disclosure/`.
- Protocol: SDK `proveSenderDisclosure` / `verifyDisclosure`
  (`packages/sdk/src/disclosure/`), which implements the mandatory §5.3
  verifier steps including byte-for-byte verification-key pinning.

## Why D-sender (and not just "here is my decrypted view")

A screenshot or a signed statement proves nothing — Marta could claim any
number. The D-sender proof is different in kind:

- It proves **origination**: the prover must know the transfer-time ephemeral
  scalar `r_e`, re-derived from Marta's viewing key + the event's public
  `sigma` (§15.2). Only the event's true sender can do that.
- It proves the **amount**: the circuit re-performs the recipient-side
  decryption of the event ciphertext inside the proof (constraints DS4/DS5).
- It is **recipient-bound**: the amount travels sealed to the verifier's
  Grumpkin key `P_R` under a fresh nonce `nu` (§4, §13.2). A leaked receipt
  file reveals nothing and cannot be replayed to another party.
- The verifier **trusts only the chain**: every public input (event fields,
  the accounts' viewing keys, `addr_f`) is re-read from the ledger; the
  receipt contributes only the proof and the sealed ciphertext (§5.2).

## Files

| File | What |
|---|---|
| `make-receipt.mjs` | Demo of both roles: the verifier mints a request `(P_R, nu)`; Marta proves and writes `receipt.json` |
| `verify-receipt.mjs` | The receiving party's §5.3 verification: VK pinning → on-chain event resolution → on-chain PVK reads → UltraHonk verify → decrypt |
| `receipt.json` | The shareable artifact: plain-language claim + request + proof bundle (amount SEALED to the verifier) |
| `_vendor.mjs` | Read-only imports of the vendor SDK dist + the shared `@ctd/disclosure` artifacts |
| `demo-verifier-key.mjs` | The verifier scalar `r_R` this receipt is sealed to — **committed on purpose**, so the check below runs on a fresh clone |

Setup is the same as `../verify-goal-total/README.md` (vendor clone +
`npx -y pnpm@10.33.0 install` + `npx -y pnpm@10.33.0 build:sdk`).

**Verifying works out of the box:** `node verify-receipt.mjs` needs no secrets
of yours. Opening a receipt requires the verifier's own scalar `r_R`, so that
scalar is committed in `demo-verifier-key.mjs` — otherwise the receipt shipped
here would be cryptographically dead and this demo unrunnable. Publishing it is
safe and deliberate: it is not a Stellar seed, holds no funds, signs nothing,
and its only power is decrypting *this* receipt's amount — 25 XLM, a number
already printed in plaintext above. It does **not** open Marta's balance or
history, the goal's view key, or any other receipt. That file's header spells
out the reasoning and the one real caveat (never reuse it as a real verifier
key). Set `RECEIPT_VERIFIER_SECRET_HEX` in `.env.deploy` to override it.

**Proving is the part you cannot reproduce**, by design: `make-receipt.mjs`
needs Marta's confidential scalar `CT_MARTA_CONF_SK` (gitignored — it is her
balance and her whole history, and testnet play-money is still a real secret
here). The proof she already produced is committed as `receipt.json`.

## Real run (2026-08-03, testnet)

`node make-receipt.mjs` — proof over the real tx
[`58363138…`](https://stellar.expert/explorer/testnet/tx/5836313815618675a8530b3d3efb5e931e29ba9d49d58d90562414bc8c5463a4):

```
[verifier] request minted: fresh nonce 0x00c62667… — the proof will bind to (P_R, nu)
[marta] found: transfer GDUTRP… → GAJPXA… (ledger 3950172)
[marta] r_e re-derived from vk + event sigma; r_e·H == event R_e  OK
[marta] disclosing amount: 25 XLM (250000000 stroops)
[marta] proving D-sender disclosure (UltraHonk keccak, 22 threads)…
[marta] proof ready: 14592 bytes in 970 ms
receipt written: …scripts/receipt/receipt.json
```

`node verify-receipt.mjs`:

```
  ✔ VK pinned: disclose_sender artifact matches the shared 1760B verification key
  ✔ Resolved ref_E on-chain: transfer GDUTRP… → GAJPXA… in tx 5836313815… (ledger 3950172)
  ✔ Read PVK_A from the on-chain account at the event's "from" address (GDUTRP…)
  ✔ Read PVK_B from the on-chain account at the event's "to" address (GAJPXA…)
  ✔ Constructed the public-input vector from chain state + own (P_R, ν)
  ✔ UltraHonk proof verified against the reconstructed public inputs
  ✔ Decrypted ṽ_disc with r_R and ν → amount 250000000

VERIFIED: the on-chain transfer in tx 58363138… (ledger 3950172)
  was SENT by GDUTRPFZAL3QRHCY47A6KAI6EK4XJTZ35J5IWI7YN3VGHHWA5F77DJ2I
  for exactly 25 XLM (250000000 stroops)
  matching the receipt's claim. This is as trustworthy as the chain itself (§1.1).
```

Tamper test (flip one byte of the proof): rejected at §5.3 step 5 —
`REJECTED at §5.3 stage [verify-proof]: UltraHonk proof verification failed`.

## Honest limitations

- **Retention**: `verifyDisclosure` resolves the event from the RPC, so a
  receipt older than the ~7-day retention window stops verifying against a
  bare RPC. The vendor verifier accepts an indexer for exactly this reason —
  wiring Raiz Memory in as that indexer is the natural next step (BACKLOG).
- **Cherry-picking** (§13.3): a receipt proves what one transfer was, not that
  it is the only one. Completeness questions route through the goal's
  published view key, which sees every incoming contribution.
- The demo plays both protocol roles in one script for judgeability; in the
  wallet, the request would arrive out-of-band and only the bundle would leave
  the device.
