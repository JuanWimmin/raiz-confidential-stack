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
cp .env.example .env      # then edit CONTRACT_IDS
cargo run
```

Configuration (env vars, `.env` supported):

| Variable | Required | Default | Meaning |
| --- | --- | --- | --- |
| `RPC_URL` | yes | — | Soroban RPC endpoint (e.g. `https://soroban-testnet.stellar.org`) |
| `CONTRACT_IDS` | yes | — | Comma-separated contract ids to index |
| `DATABASE_URL` | no | `sqlite://raiz_memory.db?mode=rwc` | SQLite database |
| `POLL_INTERVAL_SECS` | no | `5` | Poll interval per contract |
| `PORT` | no | `8090` | HTTP port |
| `RETENTION_SIMULATION_LEDGERS` | no | unset | Enables [purge demo mode](#purge-demo-mode) |

## Endpoints

- `GET /health` — `{ "status": "ok", "latest_indexed_ledger": N }`.
- `GET /coverage` — per contract: which ledger range is held and how far the
  scan has progressed. Honest by design: it reports what we have, not what you
  wish we had.
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

Deterministic and offline: the Soroban RPC is mocked by a local HTTP server
speaking the verified `getEvents` JSON shape; the `/events` API is exercised
over real HTTP against throwaway SQLite databases under `target/test-dbs/`.
