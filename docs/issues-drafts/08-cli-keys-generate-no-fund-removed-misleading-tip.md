# `keys generate --no-fund` removed in 23.x: default silently flipped, and the error's tip is misleading

- Status: **DO NOT FILE — DROP** (fact-check 2026-08-04). The behaviour still
  reproduces verbatim on the current stellar CLI **27.1.0** (re-run there:
  same "unexpected argument '--no-fund'" and same tip; `--fund` is the only
  funding flag). But the draft's framing is wrong on the substance: the
  default did not "silently flip". stellar/stellar-cli#1407 ("[22.0]
  `stellar keys generate` should no longer fund", labelled `bug` /
  `breaking change`, CLOSED) removed `--no-fund` deliberately and announced
  it. What is left is only "clap prints a generic tip for an unknown flag",
  which is stock clap behaviour for any typo, not a stellar-cli defect — and
  we would be raising it three major versions after the removal. Not worth a
  maintainer's time.
- Repo it belongs to: stellar/stellar-cli
- Version/commit: originally observed on stellar 23.2.1; re-confirmed on 27.1.0 (8e402ea28202950b272fbabc34caad4d2f64fe87)

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
