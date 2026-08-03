# Demo run — the exact sequence we record

> Rule of the house (gotcha #3): the demo is RECORDED the day it works. Never
> demo live without the recording as backup.

Status: scene 3 (the purge) verified against real data on 2026-08-03.
Scenes 1-2 and 4 get filled in during Session 6, once the screens exist.

---

## Scene 3 — "the RPC that forgets vs the wallet that remembers"

This is the scene the whole synergy argument rests on, and it is already
reproducible. It needs no waiting and no fake data: the same server answers
both sides, one of them wearing the RPC's retention limits.

Setup (already in `raiz-memory/.env`):

```
RETENTION_SIMULATION_LEDGERS=2000
```

The flag is inert unless a request asks for it, so the production endpoint is
unaffected — the wallet just switches which URL it reads.

### Verified numbers (2026-08-03, testnet, chain head ~3,953,300)

Our own CT wrapper `CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT`:

| Source | Events returned | Ledger range | Declares |
| --- | --- | --- | --- |
| Raiz Memory (full history) | **18** | 3950129 → 3953087 | everything since the contract was born |
| Simulated RPC (`?source=rpc-simulation`) | **4** | 3953009 → 3953087 | `oldestLedger: 3951357` |

`goal_meta` `CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ`:

| Source | Events returned | Ledger range |
| --- | --- | --- |
| Raiz Memory | **5** | 3950217 → 3952518 |
| Simulated RPC | **1** | 3952518 only |

Commands (both sides, same server):

```bash
CT=CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT

# Side A — an RPC that forgets
curl "http://localhost:8091/events?contractId=$CT&limit=100&source=rpc-simulation"

# Side B — Raiz Memory remembers
curl "http://localhost:8091/events?contractId=$CT&limit=100"
```

### Why those numbers land the point

The forgetful side does not merely show a shorter list. Among the 14 events it
drops are the `register` events of both accounts — the ones a wallet must
replay to rebuild its confidential state at all. That is not a cosmetic gap:
it is exactly the `RPC_SYNC_GAP` failure Nethermind documents in their own
wallet, reproduced on our contract, and repaired by changing one URL.

The goal timeline tells the same story in the UI: a contribution history that
collapses to a single entry, then comes back whole.

---

## Scenes 1, 2 and 4 — TODO(session-6)

Filled in when the screens exist. Planned order:

1. **A contribution the explorer cannot read** — contribute from the phone,
   then open the transaction in stellar.expert and show that no amount exists
   anywhere in the envelope (evidence already captured by
   `scripts/ct-flow/visibility.mjs`).
2. **The fund is made of glass** — run `scripts/verify-goal-total` from a clean
   clone, outside the app, and read the verified total off the terminal.
4. **My receipt** — Marta produces her selective-disclosure receipt and a third
   party verifies it (`scripts/receipt`), including the tamper case.
