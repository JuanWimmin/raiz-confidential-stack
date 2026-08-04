# getEvents: one malformed contractId rejects the whole call, and the error identifies it only by 1-based index

- Status: DRAFT — do not open online before human review
- Repo it belongs to: stellar/stellar-rpc
- Verified: 2026-08-04 against soroban-testnet.stellar.org, protocolVersion 27 (latestLedger 3968463, oldestLedger 3847504, ledgerRetentionWindow 120960)

## Repro steps

Send a `getEvents` request whose filter batches several contract ids, one of
which fails strkey validation. Copy-pasteable:

```bash
curl -s -X POST https://soroban-testnet.stellar.org \
  -H 'Content-Type: application/json' \
  -d '{"jsonrpc":"2.0","id":1,"method":"getEvents","params":{
        "startLedger":3960000,
        "filters":[{"type":"contract","contractIds":[
          "CBF64DEOVQAXJFBSNGFEUT2AH4H7K5JBY3ZYJ5GVEINMNSDISWRG5N3F",
          "CDLZFC3SYJYDZT7K67VZ75HPJVIEUVNIXF47ZG2FB2RMQQVU2HHGCYSC",
          "CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ",
          "CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT",
          "CCREDIB3DG3IBVUKBL7QMEK4MTPSTODR7MQ34QY4SQ5LZ5L4WFWNVNXG"]}],
        "pagination":{"limit":3}}}'
```

The first four ids are real testnet contracts; the fifth is a well-formed
56-character strkey that fails the checksum. Any `startLedger` works — filter
validation runs before the ledger-range check, so the repro does not go stale
as the retention window moves.

## Expected

Either (a) an error that names the offending id string, or (b) the valid ids
still being queried, with the invalid one reported or skipped.

## Observed (verbatim)

```
{"jsonrpc":"2.0","id":1,"error":{"code":-32602,"message":"filter 1 invalid: contract ID 5 invalid"}}
```

The whole request is rejected, and the message identifies the culprit only by
1-based position. Moving the same bad id to the front changes the message to
`contract ID 1 invalid`, confirming the index is positional and the id string
is never echoed.

## Why it matters

A caller batching N contract ids — an indexer reading them from configuration,
for example — gets one error for the batch and has to bisect to find which
entry is bad. With ids that differ only in a checksum this is genuinely
awkward to debug, and one bad config entry silently takes out the whole batch
rather than the one contract it names.

## Suggested fix

Echo the rejected id in the message, e.g.
`filter 1 invalid: contract ID 5 ("CCREDIB3…WNVNXG") is not a valid contract
strkey`. That alone removes the bisection. If per-id skip-and-warn semantics
were considered and rejected, a line in the `getEvents` docs saying the filter
is all-or-nothing would set expectations for batching callers.
