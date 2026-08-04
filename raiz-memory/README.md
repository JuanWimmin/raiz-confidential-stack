# Raiz Memory

Durable event index for private wallets on Stellar. Soroban RPC nodes retain
contract events for only about 7 days; Raiz Memory polls `getEvents`, persists
the raw events (base64 XDR, uninterpreted — ciphertext stays ciphertext) in
SQLite, and serves them forever through a `getEvents`-shaped HTTP API. A wallet
adopts it by changing one URL.

Built for the Confidential Token wrapper and the `goal_meta` contract of
*Sobre del Barrio*; any contract id works (indexing the SPP pool is one line of
config).

> We index ciphertext; your privacy budget is untouched.

## Quickstart

```bash
cp .env.example .env      # ships with our real contracts already configured
cargo run
```

Out of the box this indexes the full on-chain history of the *Sobre del Barrio*
contracts, not just what happens after you start it — see
[Backfill](#backfill-where-history-begins).

Configuration (env vars, `.env` supported):

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `RPC_URL` | yes | — | Soroban RPC endpoint (e.g. `https://soroban-testnet.stellar.org`) |
| `CONTRACT_IDS` | yes | — | Comma-separated contract ids to index |
| `BACKFILL_FROM_LEDGER` | no | `oldest` | How far back a contract's **first run** reaches when it has no entry in `CONTRACT_START_LEDGERS`: `oldest`, `head`, or a ledger number |
| `CONTRACT_START_LEDGERS` | no | unset | Per-contract overrides: comma-separated `CONTRACTID:LEDGER` pairs, where `LEDGER` may also be `oldest` or `head` |
| `DATABASE_URL` | no | `sqlite://raiz_memory.db?mode=rwc` | SQLite database |
| `POLL_INTERVAL_SECS` | no | `5` | Poll interval per contract |
| `PORT` | no | `8090` | HTTP port |
| `RETENTION_SIMULATION_LEDGERS` | no | unset | Enables [purge demo mode](#purge-demo-mode) |

## Backfill: where history begins

An index that starts at the chain head proves nothing. Everything Raiz Memory
claims — *RPC nodes forget, we don't* — is only demonstrable if a fresh instance
first goes and **gets** the history that is still sitting inside the RPC's
retention window, before it too is dropped.

So every contract has a *start spec* for its first run:

| Value | Meaning |
| --- | --- |
| `oldest` | Reach as far back as this RPC still holds — its retention floor. **The default.** |
| `head` | Only index what happens from now on (the behavior before backfill existed). |
| `<ledger>` | Start at an explicit ledger, typically the contract's deployment ledger. |

```bash
# every contract: grab the whole retention window (the default)
BACKFILL_FROM_LEDGER=oldest

# ...except these, which start at their deployment ledger
CONTRACT_START_LEDGERS=CBWSANZN...:3950128,CBNVY2AA...:3950200
```

Rules, all of them deliberate:

- **First run only.** A start spec applies to a contract that has no cursor yet.
  A contract already being tailed is never dragged backwards by a config
  change, and restarting an instance never rewinds it.
- **Idempotent.** Events are keyed by the RPC's own event id, so a re-run — or
  an RPC page that overlaps one we already stored — cannot create a second row.
- **Then it tails.** Once the backfill reaches the chain head, the normal poll
  loop continues exactly as before; backfill and live tail are the same loop.
- **A typo is a startup error.** Every value must parse, and every contract id
  in `CONTRACT_START_LEDGERS` must also appear in `CONTRACT_IDS`. Silently
  ignoring a mistyped id is how you end up with an empty index and no idea why.

### The clamp: when the RPC has already forgotten

Ask for a ledger the RPC no longer holds and it refuses the call outright,
verbatim (code `-32600`):

```
startLedger must be within the ledger range: 3832943 - 3953902
```

We neither crash nor pretend. We **clamp**: start at the RPC's oldest available
ledger, log exactly what was asked for, what was used, and how many ledgers were
already unreachable — and record it permanently, so `/coverage` can never
overstate how much history this index holds:

```json
{
  "contractId": "CBF64DEOVQAXJFBSNGFEUT2AH4H7K5JBY3ZYJ5GVEINMNSDISWRG5N3F",
  "historyBeginsAtLedger": 3833016,
  "backfill": {
    "mode": "ledger",
    "requestedStartLedger": 3013364,
    "effectiveStartLedger": 3833016,
    "rpcOldestLedgerAtStart": 3833016,
    "clamped": true,
    "unreachableLedgers": 819652,
    "note": "requested start ledger 3013364 predates this RPC's retention floor 3833016; 819652 ledgers of history were already unreachable when indexing began"
  }
}
```

That entry is the official Confidential Token demo wrapper, and it is in
`.env.example` on purpose. It was deployed at ledger 3013364; when the run above
was made (2026-08-03) the testnet RPC's floor was 3833016. **819,652 ledgers of
the official demo's own history — about seven weeks — were already gone**, and
no amount of asking brings them back. That is the argument for this project,
printed by the software itself. The exact numbers move every time you run it;
that is the point.

`historyBeginsAtLedger` is the honest answer to "how far back does this index
go": the first ledger we actually scanned. Anything older is not *"no events"*,
it is *"we weren't there"* — and `/coverage` distinguishes the two.

The retention floor is read from the RPC's `getHealth` (`oldestLedger`,
`latestLedger`), a structured field rather than prose: the wording of the error
message is not part of any API contract, while that field name is the same one
`getEvents` responses already carry. The error text *is* still parsed, but only
as a retry safety net — the floor advances one ledger every ~5s, so a floor read
a moment ago can already be stale by the time the scan lands (observed live on
2026-08-03). When that happens the ingestor re-clamps from the error and carries
on instead of failing the tick.

## Endpoints

- `GET /health` — `{ "status": "ok", "latest_indexed_ledger": N }`.
- `GET /coverage` — per contract: which ledger range is held, how far the scan
  has progressed (`scannedThroughLedger`), where history begins
  (`historyBeginsAtLedger`) and, when the RPC could not serve the requested
  start, a [`backfill` object](#the-clamp-when-the-rpc-has-already-forgotten)
  declaring how much was already unreachable. Honest by design: it reports what
  we have, not what you wish we had.
- `GET /events?contractId=C...&startLedger=N&cursor=...&limit=100` —
  `getEvents`-shaped response: `{ latestLedger, events, cursor }`. Event fields
  mirror the live RPC (verified against testnet on 2026-08-02): `type`, `id`,
  `contractId`, `ledger`, `ledgerClosedAt`, `txHash`,
  `inSuccessfulContractCall`, `topic` (base64 XDR array), `value` (base64 XDR).
  Pass the returned `cursor` back to get the next page.

## Purge demo mode

The point of Raiz Memory is remembering what the RPC forgets — which is hard to
show live without waiting 7 days. Purge demo mode simulates the forgetting on
demand, powering the side-by-side scene in the demo video: "the RPC that
forgets vs Raiz Memory that remembers".

Start the server with the flag (N = how many trailing ledgers the simulated
RPC "retains"):

```bash
RETENTION_SIMULATION_LEDGERS=120 cargo run
```

Then compare both sides against the same server:

```bash
# Side A — "an RPC that forgets": only events from the last 120 ledgers,
# older history is gone; the response declares its retention floor
# (oldestLedger), just like the real RPC does.
curl "http://localhost:8090/events?contractId=CXXXX...&source=rpc-simulation"

# Side B — Raiz Memory remembers: the full indexed history.
curl "http://localhost:8090/events?contractId=CXXXX..."
```

Rules of the flag:

- Only requests carrying `source=rpc-simulation` are affected. Every other
  request — same server, same flag — gets the normal, complete answer.
- If `RETENTION_SIMULATION_LEDGERS` is unset, `source=rpc-simulation` is
  inert: behavior is identical to a server without the feature.
- The cutoff is relative to the latest *indexed* ledger: with latest ledger
  `L`, only events with `ledger >= L - N + 1` are returned, and the response
  gains an `oldestLedger` field with that floor.

## Tests

```bash
cargo test
```

15 tests, all deterministic and offline: the Soroban RPC is mocked by a local
HTTP server speaking the verified `getEvents` / `getHealth` JSON shapes — down
to the exact `-32600` out-of-range message — and the `/events` and `/coverage`
APIs are exercised over real HTTP against throwaway SQLite databases under
`target/test-dbs/`. The backfill tests cover starting at a configured ledger,
clamping an out-of-range one instead of dying, re-clamping when the retention
floor moves mid-scan, and re-running without duplicating events or rewinding a
live cursor.
