# Test files ship a checksum-invalid placeholder contract id that poisons batched getEvents calls

- Status: **DO NOT FILE — DROP** (fact-check 2026-08-04). No duplicate exists;
  the claim is factually true but does not describe a defect.
  `packages/sdk/src/crypto/address.ts:25-31` shows `addressToField` validates
  only the 56-character *length* and then Poseidon-hashes the ASCII bytes, so
  a checksum is irrelevant to what these fixtures are for and the tests are
  correct as written. The demo publishes its real, deployed ids in README.md
  and `deployments/testnet.json`; scraping test files for deployable contract
  ids is our own unusual workflow, not a supported one. Filing this would read
  as "your test fixture broke my scraper" — and it would be the very first
  issue ever opened on that repo (it currently has none).
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Version/commit: ac67499 (id confirmed present in `packages/sdk/test/{parity,payload,prove,smoke}.mjs`)

## Repro steps

1. Grep the repo for contract ids to index (a natural thing for an indexer or
   dashboard builder to do): `packages/sdk/test/{payload,smoke,parity,prove}.mjs`
   contain `CCREDIB3DG3IBVUKBL7QMEK4MTPSTODR7MQ34QY4SQ5LZ5L4WFWNVNXG`.
2. Include that id in a `getEvents` filter alongside valid ids.

## Expected

Ids appearing in the repo either decode as valid strkeys or are visibly fake
(e.g. `C...PLACEHOLDER...`), so harvesting tools fail loudly or skip them.

## Observed (verbatim)

The id fails strkey checksum validation, and the RPC rejects the entire
batched call with:

```
{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"filter 1 invalid: contract ID 5 invalid"}}
```

(The RPC error names only an index, not the id — reported separately to
stellar-rpc — so the demo's placeholder id costs a bisection to locate.)

## Suggested fix

Use a checksum-VALID dummy id in test files (e.g. derive one from a fixed
seed), or a string that cannot be mistaken for a real id. One-line change per
test file.
