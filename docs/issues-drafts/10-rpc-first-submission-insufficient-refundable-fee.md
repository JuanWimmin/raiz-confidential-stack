# Simulation-sized transaction failed on submit with InsufficientRefundableFee; identical retry ~5 s later succeeded

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: stellar/stellar-rpc (simulateTransaction resource/fee estimation; possibly stellar/stellar-cli if the CLI post-processes the estimate)
- Version/commit: stellar CLI 23.2.1 against soroban-testnet.stellar.org (protocol 27), 2026-08-03

## Repro steps

First-ever invoke of a freshly deployed contract function that extends TTL on
2 persistent entries + instance (three ~30-day rent bumps in one call) and
emits one event:

```
stellar contract invoke --id CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ \
  --source-account S... --network testnet -- record_harvest --id 0 \
  --memo "primera cosecha (goal-flow 2026-08-03T15:29:48Z)"
```

## Expected

The CLI submits what its own simulation sized; submission should not underpay
the refundable (rent) fee.

## Observed (verbatim)

```
❌ error: transaction submission failed: Some(
    TransactionResult {
        fee_charged: 5090,
        result: TxFailed(
            VecM(
                [
                    OpInner(
                        InvokeHostFunction(
                            InsufficientRefundableFee,
                        ),
                    ),
                ],
            ),
        ),
        ext: V0,
    },
)
```

An identical re-invoke ~5 s later simulated fresh and succeeded. The failed
attempt still charged 5090 stroops.

## Suggested fix / question

If simulation-to-submission ledger drift can invalidate the refundable-fee
estimate (e.g. TTL/rent recomputed against a later ledger), the estimate
could include headroom for that drift, or the docs could state that
rent-bumping transactions need caller-side retry/fee-padding. Happy to
provide both envelopes if useful.
