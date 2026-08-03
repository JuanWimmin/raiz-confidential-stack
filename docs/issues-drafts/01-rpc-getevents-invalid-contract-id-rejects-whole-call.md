# getEvents: one invalid contractId rejects the whole call, and the error names only a 1-based index

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: stellar/stellar-rpc
- Version/commit: soroban-testnet.stellar.org, protocolVersion 27, observed at latestLedger 3940652 (2026-08-02)

## Repro steps

POST to the testnet RPC with 5 contract ids in one filter, of which the 5th is
checksum-invalid (a placeholder harvested from a demo repo's test files):

```json
{"jsonrpc":"2.0","id":1,"method":"getEvents","params":{
  "startLedger":3820000,
  "filters":[{"type":"contract","contractIds":["<4 valid ids>","CCREDIB3DG3IBVUKBL7QMEK4MTPSTODR7MQ34QY4SQ5LZ5L4WFWNVNXG"]}],
  "pagination":{"limit":3}}}
```

## Expected

Either (a) per-id validation feedback that names the offending id string, or
(b) the valid ids still being queried with the invalid one reported/skipped.

## Observed (verbatim)

```
{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"filter 1 invalid: contract ID 5 invalid"}}
```

The whole request is rejected. The message gives a 1-based index ("contract
ID 5") and never the id string itself, so a caller batching N ids must bisect
to find the culprit. For indexers that harvest contract ids from configs (our
use case: a durable getEvents archive), this turns one bad entry into a full
outage of the batch.

## Suggested fix

Include the rejected id string in the error message (`contract ID
"CCREDIB3…" invalid`), and consider documenting whether per-id skip-and-warn
semantics were considered and rejected. Even just echoing the string would
remove the bisection dance.
