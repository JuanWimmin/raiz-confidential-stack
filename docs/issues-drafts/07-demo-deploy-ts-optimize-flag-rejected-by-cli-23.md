# scripts/deploy.ts passes `--optimize=false`, which stellar CLI 23.2.1 rejects — deployment fails at the first contract on a current install

- Status: **DO NOT FILE — DROP, this one is our error** (fact-check
  2026-08-04). The draft blames the demo for a flag that is fine.
  `--optimize` exists and is documented in the current stellar CLI: 27.1.0's
  `contract deploy --help` shows `--optimize [<OPTIMIZE>]  Optimize the
  generated wasm. Enabled by default; pass \`--optimize=false\` to disable.`
  So `scripts/deploy.ts:47` is correct. We hit "unexpected argument
  '--optimize' found" only because the machine had stellar CLI **23.2.1**,
  which is four major versions behind the current 27.1.0 (released
  2026-07-31) — and, decisively, *below the demo's own documented
  prerequisite*: README.md:130 requires "`stellar` CLI ≥ 25.2". Filing this
  would be reporting our failure to meet a stated prerequisite as someone
  else's bug. Fix on our side: upgrade the CLI.
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Version/commit: ac67499 (scripts/deploy.ts line 47); observed with the stale stellar CLI 23.2.1 (496ac35be7a7d8d923fcde9bbbc650ee593d1f6f)

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
