# Docs: spell out that confidential view keys are per-deployment (auditor registry), not per-account — and what the recipient channel actually reveals

- Status: DRAFT — do not open online before human review
- Repo it belongs to: OpenZeppelin/stellar-contracts
- Verified: 2026-08-04 against `main` @ 9b5ed96 (the confidential token code is on `main`; the demo app pins the `feat/confidential-verifier-ultrahonk` branch as a git dependency)
- Prior art we read first: #702 (auditor contract spec — states the admin gating is deliberate, with the identity/zero-key rationale) and #770 (Audit L-02, clarified that `register` binds an account to *any existing* `auditor_id`, existence-checked only). This issue is the downstream documentation gap those two leave, not a re-report of either.

## Context / use case

We are building a community-fund wallet: contributions to a goal account
should be verifiable by *anyone* through a deliberately published view key,
while contributors' own balances stay private. That made us look for the
smallest scope a view key can have.

## What the code says

The only view-key mechanism in the preview is the auditor registry — a
`u32 auditor_id → Grumpkin point` table on the auditor contract:

- an account commits to one `auditor_id` at registration
  (`packages/tokens/src/confidential/storage.rs:63` and `:422-430`; the doc
  comment at `:397` calls it "the auditor key index this account commits to")
- every transfer fetches *both* parties' keys by that id
  (`packages/tokens/src/confidential/storage.rs:686-687`)
- there is no per-account key export

So the granularity of visibility is the deployment, not the account. #770
clarified that an account may *select* among existing auditor ids; it does not
say that no smaller scope exists, which is the part a reader needs.

## What we hit in practice

On a shared deployment, obtaining a view key means getting an id registered,
and that is gated. Calling `register_key` on the demo deployment's auditor
contract `CA4II62E35TQKPGHCPBD6EBAS732GSGS6H37UUWKEDHR4YTBVMPHVY4L` from a
friendbot-funded account fails at simulation:

```
simulate register_key failed: HostError: Error(Contract, #2000)
```

with diagnostic `["failing with contract error", 2000]` — #2000 is
`AccessControlError::Unauthorized`
(`packages/access/src/access_control/mod.rs:384`).

To be precise about whose decision that is: the library's
`auditor::register_key` (`packages/tokens/src/confidential/auditor/storage.rs:64`)
carries no authorization of its own; the role check comes from the deployed
contract, which declares `#[only_role(operator, "manager")]`. In the demo that
is `contracts/auditor/src/lib.rs:40-41` in
brozorec/stellar-confidential-token-demo. We are not asking for that gate to
be removed — #702 explains why it exists. We are reporting that a reader of
the confidential docs cannot easily work out that this is the situation.

## Workaround (works today, and may be worth documenting as the pattern)

Deploy your own CT stack and be the auditor admin. We registered auditor id 0
as a private custodian key and id 1 as the goal's dedicated, published view
key. One wrapper per community turns out to be a perfectly workable deployment
model, and "run your own auditor contract if you need a view key you control"
is a sentence the docs could say outright.

## The part that most needs documenting: what a view key reveals

We expected a published view key to expose aggregate inflows. It does more
than that. Holding the id-1 secret opens the *recipient* channel of every
transfer into the goal, which is a full opening of `C_tx` (amount plus `r_tx`)
— that is, per-contribution amounts, not just a total. It does not open the
sender channel: with our published key,
`auditTransfer(k1).channelsAgree === false` on-chain, and contributors'
balances stay private.

That asymmetry is exactly what we wanted, but we only established it by
reading `storage.rs` and then testing it on-chain. A short paragraph in
`packages/tokens/src/confidential/docs/COMPLIANCE.md` (or `DESIGN.md` §8.1,
next to the L-02 clarification) saying "an auditor key opens both channels of
transfers involving accounts bound to that id, at per-transfer granularity" —
plus a note on whether per-account, per-scope, or aggregate-only keys are on
the roadmap — would let teams reason about disclosure before they build.

Happy to open a PR against the docs if that is easier than a discussion here.
