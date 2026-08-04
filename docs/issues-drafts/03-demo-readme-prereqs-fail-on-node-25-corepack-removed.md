# README prerequisites ("Node >= 20, pnpm 10") no longer bootstrap on Node 25 — corepack was removed from the Node distribution

- Status: **DO NOT FILE as an issue — DROP** (fact-check 2026-08-04). Every
  fact still checks out: README.md:129 does say "Node ≥ 20, pnpm 10",
  `package.json` does pin `packageManager: pnpm@10.33.0`, and corepack is
  genuinely absent from the Node 25.8.1 distribution (not merely off PATH).
  It is still too thin to file. The README never instructs anyone to use
  corepack — installing pnpm is the reader's job — and this draft's own text
  concedes the second half (the msgpackr-extract warning) is "benign here".
  If we want to be helpful, the right vehicle is a one-line README pull
  request suggesting `npx -y pnpm@10.33.0 <cmd>` as the portable invocation,
  not an issue.
- Repo it belongs to: brozorec/stellar-confidential-token-demo
- Version/commit: ac67499 (README.md:129); host Node v25.8.1, npm 11.11.0

## Repro steps

1. Fresh machine with Node 25 (current release line) and no globally installed pnpm.
2. Follow the README: `pnpm install`.

## Expected

The documented prerequisite path works on any Node satisfying ">= 20".

## Observed

`pnpm` is not on PATH and `corepack enable` is not available either — corepack
was removed from the default Node distribution in Node 25 — so the documented
install fails before it starts. After installing pnpm out-of-band, pnpm 10's
secure-by-default build-script policy additionally skips one dependency's
build script with (verbatim):

```
╭ Warning ─────────────────────────────────────────────────────────────────────╮
│ Ignored build scripts: msgpackr-extract@3.0.4.                               │
│ Run "pnpm approve-builds" to pick which dependencies should be allowed       │
│ to run scripts.                                                              │
╰──────────────────────────────────────────────────────────────────────────────╯
Done in 3m 58.1s using pnpm v10.33.0
```

(benign here — optional native acceleration for the Cloudflare/Goldsky
tooling, unused in local dev; `sharp`, `esbuild`, `workerd` are already
allowlisted in root package.json `pnpm.onlyBuiltDependencies`).

## Suggested fix

One line in the README: recommend `npx -y pnpm@10.33.0 <cmd>` (pinned to the
repo's `packageManager` version) as the portable invocation, and note the
msgpackr-extract warning is expected/benign. Optionally add
`msgpackr-extract` to `onlyBuiltDependencies` or document why not.
