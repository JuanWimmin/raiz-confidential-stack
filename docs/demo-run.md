# Demo run — the exact sequence we record

> Rule of the house (gotcha #3): the demo is RECORDED the day it works. Never
> demo live without the recording as backup.

Status:

- Scene 3 (the purge) verified against real data on **2026-08-03** — kept below,
  unchanged, as the source of the numbers.
- The **full 2:30 shooting script** was rehearsed end to end on the real phone on
  **2026-08-04, 02:07–02:33** (local São Paulo). Every duration in it is a
  stopwatch reading from that run, not an estimate. It starts at
  [Shooting script](#shooting-script--230-rehearsed-on-device-2026-08-04).

---

## Scene 3 — "the RPC that forgets vs the wallet that remembers"

This is the scene the whole synergy argument rests on, and it is already
reproducible. It needs no waiting and no fake data: the same server answers
both sides, one of them wearing the RPC's retention limits.

Setup (already in `raiz-memory/.env`):

```
RETENTION_SIMULATION_LEDGERS=2000
```

The flag is inert unless a request asks for it, so the production endpoint is
unaffected — the wallet just switches which URL it reads.

### Verified numbers (2026-08-03, testnet, chain head ~3,953,300)

Our own CT wrapper `CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT`:

| Source | Events returned | Ledger range | Declares |
| --- | --- | --- | --- |
| Raiz Memory (full history) | **18** | 3950129 → 3953087 | everything since the contract was born |
| Simulated RPC (`?source=rpc-simulation`) | **4** | 3953009 → 3953087 | `oldestLedger: 3951357` |

`goal_meta` `CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ`:

| Source | Events returned | Ledger range |
| --- | --- | --- |
| Raiz Memory | **5** | 3950217 → 3952518 |
| Simulated RPC | **1** | 3952518 only |

Commands (both sides, same server):

```bash
CT=CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT

# Side A — an RPC that forgets
curl "http://localhost:8091/events?contractId=$CT&limit=100&source=rpc-simulation"

# Side B — Raiz Memory remembers
curl "http://localhost:8091/events?contractId=$CT&limit=100"
```

### Why those numbers land the point

The forgetful side does not merely show a shorter list. Among the 14 events it
drops are the `register` events of both accounts — the ones a wallet must
replay to rebuild its confidential state at all. That is not a cosmetic gap:
it is exactly the `RPC_SYNC_GAP` failure Nethermind documents in their own
wallet, reproduced on our contract, and repaired by changing one URL.

The goal timeline tells the same story in the UI: a contribution history that
collapses to a single entry, then comes back whole.

### Re-verified on 2026-08-04 (through the app, not curl)

The same flag was exercised from the phone during the rehearsal. Chain head
~3,960,290, retention window `2000` ledgers (~2 h 47 min at 5 s/ledger):

| Source | Timeline entries | Aportes / aportantes / cosechas | Hero total |
| --- | --- | --- | --- |
| Raiz Memory | 8 | 5 / 3 / 2 | **59 XLM**, 5 `[OK]` checks |
| RPC (simulado) | 2 | 2 / 1 / 0 | **"sin verificar"**, 2 `[FALLA]` checks |

Read the shape, not just the drop: what the forgetful source loses is the
*history* — the goal being created, both harvests, and every aporte older than
its window. What it keeps is this morning. That is exactly what a ~7-day RPC
does to a community fund, and it is a truer story than a timeline that goes to
zero. Evidence: `spike-evidence/demo-11-rpc-olvida.png`,
`demo-11b-total-sin-fuente.png`, `demo-12-vuelve-raiz-memory.png`.

The hero is the second half of the beat and the better half: with a truncated
source the app **refuses to print a number**, and says which check failed —
`commit(Σ spendable openings) == on-chain spendable commitment: MISMATCH`.

---

# Shooting script — 2:30, rehearsed on device (2026-08-04)

Device: Vivo Y21, Android 13, 720×1600 @ density 300, 4 GB RAM, WiFi, on USB
power (battery sat at 33–35 % the whole run). Testnet.

**How the timings were taken.** UI states were detected by polling
`adb exec-out uiautomator dump`, which costs ~1.3 s per sample — so every
*UI* figure below carries up to +1.3 s of detection lag and should be read as an
**upper bound**. On-chain figures (ledger close times, fetched from
`getTransaction`) are exact. `adb logcat` was useless here: this Vivo ROM only
surfaces `E`-level lines to the host, so the app's own
`Log.i(TAG, "$method SUCCESS tx …")` never arrives. Do not plan any capture
around logcat on this phone.

## SETUP — the exact state before the camera rolls

Nothing below is optional; each item was a real failure mode during the
rehearsal.

**Machine (Windows, repo root `C:\SP_WorkShop`)**

1. Raiz Memory running on port 8091:
   `cd C:\SP_WorkShop\raiz-memory ; .\target\debug\raiz-memory.exe`
   Confirm with `curl http://localhost:8091/coverage` — all four contracts must
   report a `scannedThroughLedger` within ~10 ledgers of the chain head.
2. `raiz-memory\.env` has `RETENTION_SIMULATION_LEDGERS=2000`. Leave it at 2000
   (see the table above for what that buys narratively).
3. `adb reverse tcp:8091 tcp:8091` established
   (`%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe reverse --list` must show
   `tcp:8091 tcp:8091`).
4. The vendor SDK is built, so `verify-goal-total` runs in ~3 s instead of
   failing: `vendor/stellar-confidential-token-demo/packages/sdk/dist/` exists.
   Setup from a clean clone: `scripts/verify-goal-total/README.md`.
5. A terminal already at `C:\SP_WorkShop`, font large enough to read at 1080p,
   with the command **typed but not entered**:
   `node scripts/verify-goal-total/verify-goal-total.mjs`

**Phone**

6. `xyz.raiz.sobre` installed, USB stay-awake on, notifications silenced.
7. **The account is already registered.** "Abrir mi sobre" (`register`) costs a
   10–30 s proof and is onboarding, not the pitch — never film it. The rehearsal
   account is `GDU644N3…7OL4C6`.
8. **A funded confidential balance.** Recorded state: **6 XLM spendable, 0
   pending**. If the sobre is empty: `Sellar` (deposit, proof-free) then
   overflow menu → *Cosechar* (merge, proof-free). Both are real transactions
   and both take tens of seconds; do them before recording. Budget ≥ 4 XLM so a
   2 XLM aporte can be retried once.
9. **Event source = Raiz Memory**, base URL `http://localhost:8091` (Meta
   screen, "¿De dónde sale este historial?"). It persists in
   `SharedPreferences`, so verify it after any reinstall.
10. **The app is WARM.** Launch it, wait for the hero to say *"Verificado contra
    los compromisos on-chain"*, then press HOME. Measured: a cold start takes
    **3.9–4.3 s to first frame, 8.5–10.3 s to the timeline and 12.8–14.4 s to
    the verified total**. A warm resume is **150 ms** and the verified hero is
    already on screen. Never open the app cold on camera.
11. **Chrome is warm** on `https://stellar.expert/explorer/testnet`. Measured
    cold: **11.2 s** from tapping the hash to a rendered page. Warm it and the
    beat costs seconds.

**One-line sanity check before rolling:** the number on the phone's hero and the
last line of `node scripts/verify-goal-total/verify-goal-total.mjs` must be the
same. On 2026-08-04 they were: **59 XLM** on both.

## What is pre-warmed vs. what runs live

| Operation | Proof? | Cost | Decision |
| --- | --- | --- | --- |
| `register` — "Abrir mi sobre" | yes | 10–30 s | **SETUP.** Onboarding, not the claim. |
| `deposit` — "Sellar" | no | ~20 s round trip | **SETUP.** The amount is public here anyway. |
| `merge` — "Cosechar" | no | tens of s | **SETUP.** Operator ritual, hidden in the overflow menu. |
| **`confidential_transfer` — "Aportar"** | **yes** | **22–28 s** | **LIVE, UNCUT.** |
| `goalTotal` (view-key verification) | no | 1.5–2.9 s | Live, twice. Cheap and it is the credibility. |

**Only one proof is filmed, and it is the aporte.** The claim of this
submission is "the first *mobile* Confidential Tokens wallet". A cut proof is
an unproven claim; a 4 GB phone visibly computing a zero-knowledge proof for
14 seconds, with a counter that counts real seconds and no fake progress bar,
*is* the evidence. Cutting it to save 20 s would be cutting the only thing no
other submission can show. Every other proof-bearing operation is onboarding
and belongs in SETUP.

## The beats

Times are the camera plan; the **Measured** column is what the phone actually
did on 2026-08-04. Narration is the Spanish the presenter says out loud.

### 1 · 0:00–0:10 (10 s) — The fund, already verified

- **Screen:** Meta, top of scroll. Hero: `Techo de la casa comunal`, **59 XLM**.
- **Taps:** none — resume the warm app (or start recording with it open).
- **Narración:** *"En el barrio todos ven quién colabora. Cuánto pone cada
  quien, no. Esta es una meta comunitaria con los montos cifrados."*
- **Measured:** warm resume 150 ms; hero already verified.
- **Evidence:** `spike-evidence/demo-01-meta-launch.png`

### 2 · 0:10–0:22 (12 s) — The five checks, run live

- **Screen:** tap `Verificar de nuevo` (yellow text inside the black hero,
  ≈ `720x1600` point **(201, 902)**). The hero flips to `•••` with
  *"Abriendo la view key publicada… 1 s"*, then back to **59 XLM** with a fresh
  ledger number and five `[OK]` lines.
- **Narración:** *"Este número no lo decimos nosotros. La app abre la view key
  que publicamos a propósito, descifra cada aporte y lo compara con los
  compromisos Pedersen de la cadena. Cinco comprobaciones."*
- **Measured:** **6.0 s** and **7.9 s** on two runs (shim work 1512 ms / 2041 ms;
  the rest is RPC round trips). The ledger in the caption changes every time —
  that is what proves it is live.
- **Evidence:** `demo-02-verificando.png` (mid-flight), `demo-01-meta-launch.png`
  (the five `[OK]`).

### 3 · 0:22–0:30 (8 s) — Who and when, never how much

- **Screen:** two swipes up (`input swipe 360 1200 360 500 300`) to the
  `Historial de aportes` list: rows with an address, a relative time, and a
  purple padlock pill reading `•••`.
- **Narración:** *"El historial dice quién y cuándo. Nunca cuánto: el evento
  on-chain no lleva el monto. No falta — está cifrado."*
- **Measured:** ~2 s of scrolling; the rest is reading time.
- **Evidence:** `demo-03-timeline.png`

### 4 · 0:30–0:38 (8 s) — Mi sobre, and the tap

- **Screen:** bottom nav `Mi sobre` **(541, 1440)** → the balance card shows
  **6 XLM** *descifrados en el teléfono*. Tap the amount field **(360, 1041)**,
  type `2`.
- **⚠ The numeric keyboard covers `Sellar`/`Aportar` on this screen size.**
  Dismiss it (BACK / the keyboard's ⌄) before tapping. Then tap **`Aportar`
  (514, 1161)** — the green one. This tap starts the clock for beat 5.
- **Narración:** *"Este es mi sobre: seis XLM que solo este teléfono puede leer.
  Aporto dos a la meta."*
- **Measured:** tab switch + balance decrypt ≈ 4 s; typing ≈ 2 s.
- **Evidence:** `demo-05-mi-sobre.png`

### 5 · 0:38–1:04 (26 s) — The proof, uncut

- **Screen:** the `ProofProgress` card, deliberately above the fold: a padlock,
  *"Aportando a la meta… N s"* counting real seconds, an indeterminate bar, the
  live phase line *"sincronizando estado confidencial + generando prueba…
  (~15 s)"*, and the honest hint *"Las pruebas tardan entre 10 y 30 s en este
  teléfono. No cierres la app."*
- **Narración (front-load, then be quiet):** *"La prueba de conocimiento cero se
  genera aquí, en un teléfono de cuatro giga. No la vamos a cortar: el contador
  cuenta segundos de verdad."* — then **silence** while it works.
- **Measured, twice, tap → ledger close (exact, from `getTransaction`):**
  - run 1: **25.2 s** — proof 18,119 ms — ledger 3960072
  - run 2: **21.6 s** — proof 14,418 ms — ledger 3960225
  The result card appears 1–3 s after the ledger closes (Kotlin polls
  `getTransaction`). **Budget 26 s, tolerate 30.**
- **Evidence:** `demo-06-proof-progress.png`

### 6 · 1:04–1:10 (6 s) — Confirmed, with the hash

- **Screen:** ⚠ **scroll down one swipe** — the green result card renders *below*
  the buttons and is off-screen on 720×1600 right after the op. It reads
  `Confirmado on-chain: aportando a la meta · ledger 3960225 · prueba 14418 ms
  en el teléfono · fee 236772 stroops`, then the tx hash in purple monospace.
- **Taps:** swipe up once, then tap the hash **(≈360, 1090)**.
- **Narración:** *"Confirmado. Catorce segundos de prueba en el teléfono, y
  ninguna clave salió de él: el WebView solo calcula, Kotlin firma."*
- **Measured:** tx `8e4e9d6826a8fd5d99cb72e342b25d418760d2959dbd64f9378b78224ec40452`
  (run 2) / `323dd7ce5faebbf80f10acbd9909c80020d814552fa7b01dc8360e221d8d92ab`
  (run 1).
- **Evidence:** `demo-07-aporte-ok.png`

### 7 · 1:10–1:23 (13 s) — The explorer cannot read it

- **Screen:** Chrome on `stellar.expert/explorer/testnet/tx/…`. Everything is
  there: Status `Successful`, ledger, source account, sequence, size,
  `Processed`, fees, `confidential_transfer(GDU6…L4C6, AAAAEQAAAE…)`, the
  signature. **No amount, anywhere.** One swipe shows the whole page.
- **Narración:** *"Este es el explorador público, no nuestra app. Cuenta,
  contrato, firma, comisión… y en ningún lado el monto. Eso es Confidential
  Tokens funcionando."*
- **Measured:** **11.2 s** to a rendered page from a cold Chrome — warm it in
  SETUP and this drops to seconds.
- **Evidence:** `demo-08-stellar-expert.png`, `demo-08b-stellar-expert-sin-monto.png`

### 8 · 1:23–1:31 (8 s) — Back to the goal

- **Taps:** BACK (returns to the app) → bottom nav `Meta` **(175, 1440)** →
  header refresh icon **(577, 130)**.
- **Narración:** *"Vuelvo a la meta y actualizo."*
- **Measured:** BACK ≈ 2 s, tab ≈ 2 s, refresh **3.9 s** for the header to go
  `3 aportes · 2 aportantes` → `4 aportes · 3 aportantes`.

### 9 · 1:31–1:42 (11 s) — The aporte lands, the total rises

- **Screen:** the new row at the top of the timeline — *"Aportó a la meta ·
  GDU644N3…7OL4C6 · hace 3 min · 🔒•••"*. Then scroll back up and tap
  `Verificar de nuevo`: **55 → 57 XLM** (rehearsal run 1) with a new ledger and
  five fresh `[OK]`s.
- **⚠ Two taps, not one.** `Actualizar` refreshes the timeline but **leaves the
  hero total pinned to the previous verification** (it still shows the older
  ledger). The presenter must also tap `Verificar de nuevo` for the number to
  move. Filed in `BACKLOG.md`.
- **Narración:** *"Ahí está mi aporte: mi dirección y la hora, con el monto en
  candado. Y el total sube — y se vuelve a verificar contra la cadena."*
- **Measured:** timeline **3.9 s**; re-verification **7.9 s**.
- **Evidence:** `demo-09a-total-stale.png` (the stale hero next to the updated
  timeline), `demo-09-total-sube.png` (55 → 57),
  `demo-10-nuevo-aporte-timeline.png`.

### 10 · 1:42–1:55 (13 s) — The RPC that forgets

- **Screen:** scroll to `¿De dónde sale este historial?`, tap the red chip
  **`RPC (simulado)`**. The timeline collapses (5 aportes / 3 aportantes /
  2 cosechas → **2 / 1 / 0**) and the card turns red:
  *"Esta fuente olvidó todo lo anterior al ledger 3958265."* Scroll up: the hero
  says **`sin verificar`** in red, with the failing check spelled out and two
  `[FALLA]` lines.
- **Narración:** *"Ahora leo el mismo historial desde un RPC con la retención
  real de siete días. Desaparecen las cosechas, los primeros aportes y hasta la
  creación de la meta. Y el total ya no se puede verificar — así que no lo
  mostramos."*
- **Measured:** **5.0 s** and **5.1 s** on two runs; +2 s to scroll up to the hero.
- **Evidence:** `demo-11-rpc-olvida.png`, `demo-11b-total-sin-fuente.png`

### 11 · 1:55–2:03 (8 s) — Raiz Memory brings it back

- **Taps:** the green chip **`Raiz Memory`** (same card, left).
- **Narración:** *"Vuelvo a Raiz Memory, nuestro indexador durable. Vuelve
  entero."*
- **Measured:** timeline back at **3.8 / 3.9 s**, hero re-verified at
  **7.7 / 7.7 s** on two runs. Very reproducible.
- **Evidence:** `demo-12-vuelve-raiz-memory.png`

### 12 · 2:03–2:17 (14 s) — Without the app

- **Screen:** cut to the terminal, hit Enter on the pre-typed command.
  Output ends with `Goal total: 59 XLM — verified on-chain at ledger 3960244`,
  preceded by the two key cross-checks and the per-contribution replay.
- **Command:** `node scripts/verify-goal-total/verify-goal-total.mjs`
- **Narración:** *"Y sin la app: este script usa la view key publicada y el RPC
  público. El mismo número — cincuenta y nueve XLM — verificado contra los
  compromisos de la cadena. No hay que confiar en nuestra interfaz."*
- **Measured:** **3.0 s** and **3.1 s**. Matched the phone exactly, both times
  (57 XLM after aporte 1, 59 XLM after aporte 2).

### 13 · 2:17–2:30 (13 s) — Close

- **Screen:** last frame of the verified hero, or a title card.
- **Narración:** *"Los aportes son secretos. El fondo es de vidrio. Y la wallet
  recuerda. Sobre del Barrio, de Raiz Protocol."*

**Total: 150 s exactly.** Mechanics account for ~92 s of it; the rest is
narration room and reading time.

## If it overruns — cuts, ranked

1. **Beat 3 (8 s), the timeline scroll.** Beat 9 shows the same list with the
   new aporte on top and a better reason to look at it. Cheapest cut.
2. **Beat 6 (6 s), the result card.** The hash can be read during the tail of
   beat 5; jump straight to the explorer, which shows the same hash bigger.
3. **Beat 13, 13 s → 8 s.** The tagline over the last frame does not need a
   dedicated card.
4. **Beat 12, 14 s → 8 s.** Pre-scroll the terminal so only the last four lines
   are on screen; the only line that must be legible is `Goal total: … verified
   on-chain at ledger N`.
5. **Already cut: "Mi recibo" (selective disclosure).** §8 of
   `propuesta_A_sobre_del_barrio.md` gave it 1:50–2:15; there is no room. It is
   a 3.1 s terminal run (`node scripts/receipt/verify-receipt.mjs`, measured
   2026-08-04, prints `VERIFIED: … for exactly 25 XLM`) and it is fully
   documented in `scripts/receipt/README.md`. If it must appear, take beats 3
   and 6 (14 s) and give it a 10 s terminal cutaway right after beat 12.
6. **Last resort — beat 7, the explorer.** Do not. For a judge who will not read
   Kotlin, "the public explorer shows no amount" is the single most legible
   proof in the whole video.

**Never cut:** beat 5 (the live proof) and beats 10–11 (the purge and the
recovery). Those two are the submission — CT on a phone, and the indexer that
makes it usable past the RPC's retention window.

## Rehearsal log — what actually happened (2026-08-04)

Two real contributions were made on testnet during the rehearsal, both from the
phone, both successful on the first attempt:

| # | tx | ledger | Amount | Proof (on device) | Tap → ledger close |
| --- | --- | --- | --- | --- | --- |
| 1 | `323dd7ce5faebbf80f10acbd9909c80020d814552fa7b01dc8360e221d8d92ab` | 3960072 | 2 XLM | 18,119 ms | 25.2 s |
| 2 | `8e4e9d6826a8fd5d99cb72e342b25d418760d2959dbd64f9378b78224ec40452` | 3960225 | 2 XLM | 14,418 ms | 21.6 s |

Goal total moved 55 → 57 → **59 XLM**, matched by `verify-goal-total` at every
step. The goal's `spendable` stayed at 50 XLM and `receiving` grew to 9 XLM —
the goal has not harvested since; that is why beat 9 works without an admin key.

Also exercised as SETUP, not filmed: one `merge` ("Cosechar", ledger 3959965)
to turn 10 XLM pending into spendable.

Nothing failed. Two things were slower than the script assumes and are handled
in SETUP: the cold start (12.8–14.4 s to a verified total) and the first
stellar.expert load (11.2 s).

**Screenshots of every beat:** `docs/spike-evidence/demo-*.png`. They were shot
across the two contributions, so `demo-09*` and `demo-10` show the 55→57 state
and the rest show the final 59 XLM state. The screen shapes are identical.
