# NOTICE — third-party software

This project ("Sobre del Barrio × Raiz Memory", by **Raiz Protocol**) is
released under the MIT License; see [`LICENSE`](LICENSE). Both Rust crates
declare the same license in their manifests (`raiz-memory/Cargo.toml:6`,
`contracts/goal-meta/Cargo.toml:6`).

This file records the third-party software we build on, what we use it for,
and its license. Every license statement below was read from the actual
license file or package manifest in the pinned source — not from memory.

---

## 1. What actually ships in this repository

Two things are worth separating, because the obligations differ.

**Reference clones are NOT part of this repository.** `/vendor/` is
gitignored (root `.gitignore`, "Vendor reference clones (read-only, never
committed)"). The OpenZeppelin, Nethermind and Confidential-Token-demo
sources listed in §2 are cloned locally for reading and for building against;
**none of their code is committed here, and none of it is redistributed by
this repository.** They are pinned by commit SHA so our claims can be checked
against the exact code we read.

**The only third-party bytes committed to this repository** are inside one
esbuild output:

- `scripts/prover-bench/dist/bench.js` (+ `.map`) — see §3.

**Third-party bytes are also redistributed in the released APK** (GitHub
release asset, built from `/wallet`, not committed here) — see §4.

---

## 2. Reference clones — read and built against, not redistributed

| Project | We use it for | License (as read) | Pinned commit |
|---|---|---|---|
| [OpenZeppelin **stellar-contracts**](https://github.com/OpenZeppelin/stellar-contracts) | The Confidential Tokens implementation itself (`packages/tokens/src/confidential/`), the auditor/view-key model, and its `INDEXER.md` / `SELECTIVE_DISCLOSURE.md` specifications, which our indexer and receipt scripts implement against. Consumed **as-is**; no OZ contract is forked or modified. | **MIT** — `LICENSE` line 1: `MIT License`, `Copyright (c) 2024 OpenZeppelin` | `9b5ed96f67aa28a8be73c538f7bfdef65925c6bc` |
| [**stellar-confidential-token-demo**](https://github.com/brozorec/stellar-confidential-token-demo) (brozorec) | The browser proving stack and TypeScript SDK: witness construction, Grumpkin/Poseidon2 crypto, `CircuitProver`, the `disclose_sender` disclosure circuit and its pinned verification key, and the compiled `register` / `transfer` / `withdraw` circuit JSON. Used unmodified — our code imports its built `dist/`. | **MIT** — the repository ships no `LICENSE` file; the license is declared in `package.json` line 21: `"license": "MIT"` | `ac67499a617c084b80c0e0298180b2c4faf9e2fb` |
| [Nethermind **stellar-private-payments** (SPP)](https://github.com/NethermindEth/stellar-private-payments) | Read only: its testnet deployment ids (so Raiz Memory can index an SPP pool with one line of config) and its README statement about RPC event retention. **We link no SPP code and ship no SPP artifact.** | Mixed, as stated by the project itself. Root `LICENSE` is the **Apache License, Version 2.0** (`LICENSE` line 2: `Apache License` / `Version 2.0, January 2004`). `circuits/LICENSE` line 3 states the directory is *"licensed under the Apache 2.0, consistent with the rest of the repository. The only exception is the `build.rs` file"*, which links GPLv3-licensed [circom](https://github.com/iden3/circom) tooling at build time; `circuits/COPYING` is the **GNU GENERAL PUBLIC LICENSE Version 3**, `Copyright (C) 2021 0Kims Association`. `poseidon2/` is dual **Apache-2.0 / MIT** (`LICENSE-APACHE`, `LICENSE-MIT`). Its README §License additionally describes `circuits/build.rs` as LGPLv3. | `a1bf177200b4e9622ca1605dead382c92e49e516` |

**On the SPP copyleft:** the GPLv3/LGPLv3 terms attach to SPP's circuit build
tooling and to compiled circuit artifacts. We compile no SPP circuit, link no
SPP crate, and redistribute no SPP artifact — Raiz Memory only reads that
project's public event stream from the Stellar RPC by contract id. No copyleft
obligation is triggered here. Anyone who *does* build or ship SPP circuits
must follow SPP's own README §License instructions.

Confirm the pins yourself:

```sh
git -C vendor/stellar-contracts                rev-parse HEAD
git -C vendor/stellar-confidential-token-demo  rev-parse HEAD
git -C vendor/stellar-private-payments         rev-parse HEAD
```

---

## 3. Committed bundle — `scripts/prover-bench/dist/bench.js`

This is the browser proving benchmark used for the Day-0 spike. It is an
esbuild ESM bundle of `scripts/prover-bench/src/main.js`, and it **inlines
third-party MIT-licensed code**. It is committed so the spike numbers can be
reproduced without a build step, which makes us a redistributor of that code
— hence this section.

Contents were determined by reading the `sources` array of the shipped
`dist/bench.js.map`, not by guessing. Inlined packages, with the license from
each package's own `package.json` / `LICENSE` in the pinned vendor workspace:

| Package | Version | License (as read) | Copyright |
|---|---|---|---|
| `@noble/curves` | 1.9.7 | **MIT** — `LICENSE` line 1: `The MIT License (MIT)` | Copyright (c) 2022 Paul Miller (https://paulmillr.com) |
| `@noble/hashes` | 1.8.0 | **MIT** — `LICENSE` line 1: `The MIT License (MIT)` | Copyright (c) 2022 Paul Miller (https://paulmillr.com) |
| `@noir-lang/acvm_js` | 1.0.0-beta.9 | **MIT** — `package.json`: `"license": "MIT"` (no license file shipped in the npm package) | The Noir contributors — https://noir-lang.org/ |
| `@noir-lang/noir_js` | 1.0.0-beta.9 | **MIT OR Apache-2.0** — `package.json`: `"license": "(MIT OR Apache-2.0)"`. We take the **MIT** option. | The Noir contributors — https://noir-lang.org/ |
| `@noir-lang/noirc_abi` | 1.0.0-beta.9 | **MIT OR Apache-2.0** — `package.json`: `"license": "(MIT OR Apache-2.0)"`. We take the **MIT** option. | The Noir contributors — https://noir-lang.org/ |
| `@zkpassport/poseidon2` | 0.6.2 | **MIT** — `LICENSE` line 1: `MIT License` | Copyright (c) 2023 Zero Knowledge Labs Limited; Copyright (c) 2025 ZKPassport |
| `pako` | 2.1.0 | **MIT AND Zlib** — `package.json`: `"license": "(MIT AND Zlib)"`; `LICENSE` line 1: `(The MIT License)` | Copyright (C) 2014-2017 by Vitaly Puzrin and Andrei Tuputcyn |

The bundle also inlines TypeScript sources from
`vendor/stellar-confidential-token-demo/packages/sdk/src/` (crypto and witness
modules) — MIT, see §2.

`@aztec/bb.js` (0.87.0, MIT) is **not** in this bundle: it is loaded as native
ESM at runtime, on purpose (see the comment in `scripts/prover-bench/src/main.js`).

MIT and Zlib both require the copyright notice and permission notice to travel
with redistributed copies. The full texts are shipped alongside the bundle in
[`scripts/prover-bench/dist/THIRD-PARTY-LICENSES.txt`](scripts/prover-bench/dist/THIRD-PARTY-LICENSES.txt).

---

## 4. The released APK (`app-debug.apk`, GitHub release asset)

The APK is not committed to this repository (`wallet/.gitignore`: `build/`),
but it *is* published as a release asset, so its contents are redistributed
and are listed here.

Beyond everything in §3, the APK packages (~14 MB of prover assets, generated
by `wallet/tools/build-prover-assets.mjs` from the pinned `/vendor` clone):

| Component | License (as read) |
|---|---|
| `@aztec/bb.js` 0.87.0 browser build (UltraHonk prover: `index.js`, `main.worker.js`, `thread.worker.js`, embedded wasm) | **MIT** — `package.json`: `"license": "MIT"`. The package's own bundler-emitted notices ship inside the APK as `assets/prover/vendor/bb/*.LICENSE.txt` and cover its own inlined deps (`buffer` — MIT, Feross Aboukhadijeh; `ieee754` — BSD-3-Clause; `pako` 2.1.0 — MIT AND Zlib; and Apache-2.0 code © 2019 Google LLC). |
| `@noir-lang/acvm_js` + `@noir-lang/noirc_abi` web wasm blobs | **MIT** / **MIT OR Apache-2.0** — see §3 |
| `register` / `transfer` / `withdraw` compiled circuit JSON, from the CT demo SDK | **MIT** — see §2 |
| AndroidX (Compose 1.7.6, Material3 1.3.1, `androidx.webkit` 1.12.1, `androidx.security:security-crypto` 1.1.0, activity/lifecycle) | **Apache-2.0** |
| Kotlin standard library and `kotlinx-coroutines-android` 1.10.2 (JetBrains) | **Apache-2.0** |
| `net.i2p.crypto:eddsa` 0.3.0 (Ed25519 signing — Android Keystore has no Ed25519) | **CC0-1.0** — read from `eddsa-0.3.0.pom`: `<name>CC0 1.0 Universal</name>` (public-domain dedication) |

---

## 5. Build tooling (not redistributed)

Present in the repository but only used to build, never shipped as part of a
running artifact:

- **Gradle Wrapper** (`wallet/gradlew`, `wallet/gradlew.bat`,
  `wallet/gradle/wrapper/`) — **Apache-2.0**, © Gradle Inc.; the SPDX header is
  in the scripts themselves (`wallet/gradlew:18`:
  `# SPDX-License-Identifier: Apache-2.0`). Fetches Gradle 8.10.2.
- **Rust crate dependencies** of `raiz-memory` and `contracts/goal-meta` are
  resolved by Cargo at build time from crates.io and are not vendored here;
  each carries its own license (`soroban-sdk` and the Stellar crates are
  Apache-2.0). The exact set is pinned in the committed `Cargo.lock` files.
- **esbuild**, **pnpm**, **Node.js** — invoked via `npx`; not vendored.

---

## 6. What is original work by Raiz Protocol

Everything in this repository that is not listed above, in particular:
`raiz-memory/` (the durable indexer), `contracts/goal-meta/` (the Soroban goal
registry), `wallet/app/src/` (the Android app, its WebView proving bridge and
its Kotlin custody/signing layer), `scripts/` (verify-goal-total, ct-flow,
goal-flow, receipt, prover-bench sources), and all documentation.

The narrower "reused vs. original" breakdown required by the bounty rules is in
`README.md`.
