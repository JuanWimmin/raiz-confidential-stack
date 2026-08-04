**Sobre del Barrio** — a mobile Confidential-Token wallet for neighbourhood fundraising goals, paired with **Raiz Memory**, a durable event indexer that serves history past the Stellar RPC's ~7-day retention window.

Submission for the Stellar Summit SP 2026 Special Bounty *"Confidential-Token & Private-Payment Wallets"* (Privacy lane — OpenZeppelin + Nethermind + GrantFox), by **Raiz Protocol**.

> *"Contributions are secret. The fund is made of glass. And the wallet remembers."*

---

## ⚠️ Read this before you install

- **Stellar TESTNET only.** No mainnet, no real money. Every balance you will see is play money from friendbot.
- **Built on a developer preview.** OpenZeppelin's Confidential Tokens have been on testnet since 2026-06-30 and are **unaudited**. So is this app. Treat both as a demo, not a wallet.
- **This is a DEBUG build**, signed with the standard Android debug certificate (`CN=Android Debug, O=Android, C=US`) — the auto-generated key every Android developer machine shares. It is **not** production-signed, so it proves nothing about who built it beyond the SHA-256 below. Android will warn you that you are installing from an unknown source; that warning is correct and you should take it seriously.
- Do not put anything you care about into this build.

## Verify what you are installing

```
app-debug.apk
SHA-256  9e87100c5a3dd9c7c42641971eedd802a9d1a0b36d2e7d890a144dfc9670535c
size     26,557,979 bytes
```

```sh
sha256sum app-debug.apk                      # Linux / macOS
certutil -hashfile app-debug.apk SHA256      # Windows
```

Signature (`apksigner verify --print-certs`): one signer, APK Signature Scheme v2, certificate SHA-256 `ab0f7acb0c3bb174f8bb98fb0a8eb6f04bf536b9c2b8c4511834fdac4009a713` — the well-known Android debug certificate.

`applicationId` `xyz.raiz.sobre` · versionName `0.1-spike` · minSdk 26 (Android 8.0) · targetSdk 35.

## What the app does

The whole confidential-token cycle runs **from the phone**, with keys that never leave it:

- **"Abrir mi sobre"** (register) — creates the confidential account. The ZK proof is generated **on the device**, inside an isolated WebView running the vendor Noir/UltraHonk stack; ~10–16 s per proof on a 4 GB Android phone. Android WebView never exposes `SharedArrayBuffer`, so proving is single-threaded there — that is a platform limit, not a bug, and the UI says so.
- **"Sellar"** (deposit) and **"Cosechar"** (merge) — no proof required by the protocol.
- **"Aportar"** (transfer) — a confidential contribution to a community goal. The **amount is encrypted**; the **participation is public**. That inversion is the point: solidarity stays visible, sums stay private.
- **Goal screen** — public total, the goal's deliberately published auditor view key, and a timeline of who contributed and when — **never how much**.
- **"Verifícalo tú mismo"** — the goal total can be re-derived outside the app, from chain data, with the published view key (`scripts/verify-goal-total`). Don't trust our UI.
- **"Mi recibo"** — a real selective-disclosure proof that one specific contribution was yours, sealed to exactly one recipient (`scripts/receipt`).

The WebView only builds and proves. Key custody, signing and submission are all Kotlin, in `EncryptedSharedPreferences`. No secret ever enters the WebView.

## The timeline needs a Raiz Memory instance

The goal timeline is served by **Raiz Memory**, the indexer in this repo — not by the app. Out of the box the APK points at `http://localhost:8091` and **the timeline will be empty until you point it somewhere real.** In the app: **⋮ → Ajustes → "Fuente de eventos" → "URL de Raiz Memory" → Guardar.**

Two ways to give it something to talk to:

1. **Indexer on your laptop, phone over USB** — run Raiz Memory (`raiz-memory/README.md`, Docker or `cargo run`; set `RUST_LOG=info` or it logs nothing), then bridge the port:
   ```sh
   adb reverse tcp:8091 tcp:8091      # match your indexer's port
   ```
   and leave the default URL as is.
2. **Any reachable instance** — paste its base URL (`http://…` or `https://…`) into that field. `scripts/serve-public.ps1` puts a local instance behind a public cloudflared tunnel if you want one from a laptop with no VM.

The same screen has the demo's centrepiece: a switch between **"Raiz Memory"** (remembers everything) and **"RPC (simulado)"** (forgets past the retention window). Same goal, same query, different history — that is the problem this project exists to solve.

## Known rough edges, stated plainly

- Renaming the app id wipes `EncryptedSharedPreferences`, so a fresh install always starts at "Abrir mi sobre".
- Android 13 and older do not carry the Sectigo R46 root that `*.stellar.org` now chains to, so the app bundles that public root in its network security config. Any native Stellar wallet hits this; it is documented in `friction-report.md`.
- Proving in the WebView is single-threaded (see above). Chrome on the same phone is 2–3× faster.

## Build it yourself instead

Everything needed is in the repository — see the README. The APK is offered only so a judge can try the wallet without an Android toolchain and a `/vendor` clone.

Licensing and third-party attribution: [`LICENSE`](https://github.com/JuanWimmin/raiz-confidential-stack/blob/main/LICENSE) (MIT) and [`NOTICE.md`](https://github.com/JuanWimmin/raiz-confidential-stack/blob/main/NOTICE.md).
