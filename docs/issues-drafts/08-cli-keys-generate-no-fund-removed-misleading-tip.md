# `keys generate --no-fund` removed in 23.x: default silently flipped, and the error's tip is misleading

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: stellar/stellar-cli
- Version/commit: stellar 23.2.1 (496ac35be7a7d8d923fcde9bbbc650ee593d1f6f)

## Repro steps

```
stellar keys generate --no-fund goal-meta-deployer
```

(`--no-fund` per pre-23 guides and muscle memory.)

## Expected

Either the flag still works, or the error explains the change ("generate no
longer funds by default; use --fund to fund").

## Observed (verbatim)

```
error: unexpected argument '--no-fund' found

  tip: to pass '--no-fund' as a value, use '-- --no-fund'

Usage: stellar.exe keys generate [OPTIONS] <NAME>
```

then, because the key was never created:

```
❌ error: Failed to find config identity for goal-meta-deployer
```

In 23.2.1 the default flipped — `generate` does NOT fund unless `--fund` is
passed, and `--no-fund` is gone. The tip actively misleads (passing
`-- --no-fund` would treat it as the NAME).

## Suggested fix

Special-case known removed flags in clap error output ("--no-fund was removed
in v23; funding is now opt-in via --fund"), the way several CLIs do for
renamed flags. Also worth a line in the v23 migration notes if not already
there.
