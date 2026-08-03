# Confidential preview: no per-account view keys — the auditor registry is admin-gated, so users of a shared deployment cannot obtain any scoped visibility key

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: OpenZeppelin/stellar-contracts (branch feat/confidential-verifier-ultrahonk)
- Version/commit: 9b5ed96; demo deployment probed on testnet, RPC protocol 27 (2026-08-03)

## Context / use case

We build a community-fund wallet where a goal account's incoming
contributions should be verifiable by ANYONE (a deliberately published view
key), while contributor balances stay private. The preview's only view-key
mechanism is the auditor registry: a `u32 auditor_id → Grumpkin key` table on
the auditor contract; each account commits to ONE id at `register()` and the
token fetches both parties' keys on every transfer
(`packages/tokens/src/confidential/storage.rs:686-687`). There is no
per-account key export, and `register_key` is operator/role-gated.

## Repro steps

Invoke `register_key(77, <fresh Grumpkin point>, <our G-address>)` on the
official demo auditor contract
`CA4II62E35TQKPGHCPBD6EBAS732GSGS6H37UUWKEDHR4YTBVMPHVY4L` with a
friendbot-funded account.

## Expected

Some path for a user/dapp to obtain a scoped view key for their own account
without owning the deployment.

## Observed (verbatim)

```
simulate register_key failed: HostError: Error(Contract, #2000)
```

with diagnostic `["failing with contract error", 2000]` — #2000 =
`AccessControlError::Unauthorized`
(`packages/access/src/access_control/mod.rs:384`). View keys exist only at
deployment level, minted by the auditor-contract admin.

## Workaround (works today, worth documenting)

Deploy your own CT stack and be the auditor admin: we registered auditor id 0
as a private custodian key and id 1 as the goal's dedicated, published view
key. The published id-1 secret then opens ONLY the recipient channel of
transfers to the goal (`auditTransfer(k1).channelsAgree === false` on-chain:
the sender channel stays unreadable). One wrapper per community is a workable
deployment model.

## Design note for the docs (granularity)

The recipient channel necessarily reveals each transfer's AMOUNT (+ `r_tx`, a
full opening of `C_tx`) to the view-key holder — per-contribution amounts, not
just totals. "Publish the goal's view key" therefore means itemized
transparency of inflows, while contributor balances remain private. If
per-account or per-scope self-service view keys (or aggregate-only keys) are
on the roadmap, the docs stating that explicitly would help teams design
around today's granularity.
