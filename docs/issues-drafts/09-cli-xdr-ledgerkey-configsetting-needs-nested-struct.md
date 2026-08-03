# `xdr encode --type LedgerKey`: ConfigSetting arm needs a nested struct, and the "error reading file" message is wrong for inline input

- Status: DRAFT — do not open online before human review (day 3)
- Repo it belongs to: stellar/stellar-cli (stellar-xdr JSON handling)
- Version/commit: stellar 23.2.1 / stellar-xdr 23.0.0

## Repro steps

Fetching the live state-archival TTL config requires the LedgerKey for a
config setting:

```
stellar xdr encode --type LedgerKey '{"config_setting":"state_archival"}'
```

(Enum arms elsewhere in the CLI's XDR JSON accept plain strings, so the flat
form is the natural first guess.)

## Expected

Either the flat enum-arm string works, or the error shows the expected shape.

## Observed (verbatim)

```
❌ error: error reading file: error decoding JSON: invalid type: string "state_archival", expected struct LedgerKeyConfigSetting at line 1 column 34
```

Two problems:

1. `LedgerKey::ConfigSetting` wraps a struct; the working shape is
   `{"config_setting":{"config_setting_id":"state_archival"}}` — nothing in
   the error hints at the field name `config_setting_id`.
2. "error reading file" is wrong — the input was an inline argument, which
   sends the user hunting for a file-path problem that does not exist.

(For anyone landing here from search: with the nested form, testnet at ledger
3950042 returned min_persistent_ttl=120960, max_entry_ttl=3110400.)

## Suggested fix

- Label the error source correctly (inline argument vs file).
- On enum-arm type mismatches, print the expected struct's field names (serde
  already knows them).
