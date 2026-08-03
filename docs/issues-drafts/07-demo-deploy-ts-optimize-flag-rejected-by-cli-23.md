# scripts/deploy.ts passes `--optimize=false`, which stellar CLI 23.2.1 rejects — deployment fails at the first contract on a current install

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Version/commit: ac67499 (scripts/deploy.ts line 47); stellar CLI 23.2.1 (496ac35be7a7d8d923fcde9bbbc650ee593d1f6f)

## Repro steps

1. Install the current stellar CLI (23.2.1).
2. Run the repo's documented deployment path (`pnpm deploy:contracts`, which
   execs `scripts/deploy.ts`); its `contract deploy` argv includes
   `"--optimize=false"`.

## Expected

The demo's documented deploy command works with a current CLI.

## Observed (verbatim)

```
error: unexpected argument '--optimize' found

  tip: to pass '--optimize' as a value, use '-- --optimize'
```

The flag no longer exists in 23.2.1, so deploy.ts fails at the first deploy.

## Suggested fix

Drop the flag (our mirror script simply omits it; every contract — verifier,
auditor, token, 6 verification keys — then deploys cleanly first-try on
testnet), or gate it on a CLI version check if older CLIs still need it.
