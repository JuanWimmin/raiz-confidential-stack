# RAIZ Reuse Plan — what Session 6 builds, and what it must build from scratch

Written 2026-08-03. Inputs: three read-only recon passes over
`C:\Blockota\Proyectos\Protocolo_Raiz\android` (RAIZ, **read-only, never built or edited
here**) + a direct read of our own app at `C:\SP_WorkShop\wallet`.

Every claim below carries `file:line` evidence. Where something does not exist, it says so
plainly.

---

## 1. VERDICT

**Adopt RAIZ's *design system* (3 files, ~135 lines, zero non-Compose dependencies).
Do NOT adopt RAIZ's *application stack* (Hilt/KSP, navigation-compose, the Soneso Stellar
SDK, Ktor, Mapbox, ZXing).**

### 1.1 Toolkit situation, stated exactly

| | RAIZ | Sobre del Barrio (today) |
|---|---|---|
| UI toolkit | 100% Compose + Material 3, zero XML layouts (`android/app/src/main/res/` has no `layout/`; `res/values/themes.xml:3-7` is a 6-line inert shim) | **No Compose at all.** UI is built imperatively with `LinearLayout`/`Button`/`TextView` in code — `wallet/app/src/main/java/xyz/raiz/sobre/spike/MainActivity.kt:109-182` |
| Activity base | `FragmentActivity` (`MainActivity.kt:94`, needed for `BiometricPrompt`) | `android.app.Activity` — `spike/MainActivity.kt:34` |
| Kotlin / AGP | 2.1.21 / 8.7.3 (`gradle/libs.versions.toml:2-3`) | 2.1.21 / 8.7.3 — `wallet/build.gradle.kts:9-10` |
| Gradle wrapper | 8.10.2 | 8.10.2 — `wallet/gradle/wrapper/gradle-wrapper.properties` |
| minSdk / target / compile | 26 / 35 / 35 (`android/app/build.gradle.kts:23,28-29`) | 26 / 35 / 35 — `wallet/app/build.gradle.kts:8,12-13` |
| JVM target | 17 (`android/app/build.gradle.kts:65-71`) | 17 — `wallet/app/build.gradle.kts:37-44` |
| DI | Hilt 2.56 + KSP (`libs.versions.toml:5,49-51`) | none (manual construction — `spike/MainActivity.kt:48-49`) |
| Stellar access | Soneso KMP SDK 1.6.0 (`libs.versions.toml:12,54`) | none. Hand-rolled: `HttpURLConnection` + `org.json` (`wallet/SorobanRpc.kt:3-7,96-124`), hand-rolled Ed25519 + StrKey (`wallet/StellarAccount.kt:36-48,114-142`) |

**There is no toolkit mismatch to resolve and no version skew: RAIZ and our app already run
the same Kotlin, AGP, Gradle, JDK and SDK levels.** The only thing missing on our side is
Compose itself. Nothing has to be ported *away* from Views — our one Activity is a debug
console (`spike/MainActivity.kt` header, lines 24-33: *"Still NOT the wallet UI (Session
6)"*), it is throwaway, and Compose can live beside it or replace it outright.

### 1.2 What must be added (exact, with local-cache evidence)

Root `wallet/build.gradle.kts` — add one plugin:

```kotlin
id("org.jetbrains.kotlin.plugin.compose") version "2.1.21" apply false
```

`wallet/app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")      // K2 built-in compiler plugin, pinned to Kotlin
}
android {
    buildFeatures { buildConfig = true; compose = true }   // compose = true is the addition
}
dependencies {
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.ui:ui-graphics:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling:1.7.6")
}
```

Those versions are not guesses — they are exactly what RAIZ's Compose BOM `2024.12.01`
(`libs.versions.toml:7,37`) resolves to, **and every one of them is already in this
machine's Gradle cache** (`C:\Users\juanp\.gradle\caches\modules-2\files-2.1`, verified
2026-08-03):

```
androidx.compose.ui/ui-android                      1.7.6
androidx.compose.material3/material3-android        1.3.1
androidx.compose.foundation/foundation-android      1.7.6
androidx.compose.runtime/runtime-android            1.7.6
androidx.compose.ui/ui-tooling-preview-android      1.7.6
androidx.compose.ui/ui-tooling-android              1.7.6
androidx.compose.material/material-icons-extended-android 1.7.6
androidx.activity/activity-compose                  1.9.3
androidx.lifecycle/lifecycle-viewmodel-compose-android 2.8.7
org.jetbrains.kotlin/compose-compiler-gradle-plugin 2.1.21
androidx.navigation/navigation-compose              2.8.5   (available; we are NOT using it — see 1.3)
```

The BOM artifact directory `androidx.compose/compose-bom` does **not** exist under
`files-2.1`; its descriptor is cached at
`modules-2/metadata-2.106/descriptors/androidx.compose/compose-bom/2024.12.01`. Using
explicit versions instead of the BOM removes that dependency on the metadata store
entirely — **pin the versions, skip the BOM.** This is the "venue has no wifi" hedge that
`wallet/build.gradle.kts:1-7` already commits us to.

Manifest change — `wallet/app/src/main/AndroidManifest.xml:23`: the app theme is
`@android:style/Theme.Material.Light.NoActionBar`. Keep it (RAIZ's own theme has the same
framework parent, `res/values/themes.xml:4`; **no AppCompat needed**), optionally adding
`android:windowLightStatusBar` + transparent status bar like RAIZ does.

Activity change: `setContent { }` needs `ComponentActivity` (from `activity-compose`).
`android.app.Activity` (our current base, `spike/MainActivity.kt:34`) will not work.
We do **not** need RAIZ's `FragmentActivity` — that is only there for `BiometricPrompt`
(`MainActivity.kt:94`), and we are not doing biometrics (out of scope per CLAUDE.md).

### 1.3 What we deliberately do NOT take from RAIZ

| RAIZ has | Why we skip it |
|---|---|
| Hilt 2.56 + KSP (`libs.versions.toml:5,49-51`; `di/DataModule.kt:20-24` is **empty by design**) | Adds a KSP round to every build and a new failure class, to replace ~4 constructor calls. RAIZ's own module is empty — the "DI" is just `@Singleton` constructor injection, which is literally `CtWallet(bridge, WalletStore(this))` (`spike/MainActivity.kt:48-49`) without an annotation processor. **Construct by hand.** |
| navigation-compose 2.8.5 + a 300-line inline NavHost (`MainActivity.kt:298-596`) | We have 3 screens and one settings sheet. A sealed `Screen` in `remember { mutableStateOf(...) }` plus `BackHandler` is ~20 lines. Adopt navigation-compose only if we later need deep links. |
| Soneso `stellar-sdk` 1.6.0 + Ktor + BouncyCastle, and 8 `META-INF` packaging excludes to make it link (`android/app/build.gradle.kts:78-94`) | We already replaced it: `wallet/StellarAccount.kt` signs and StrKey-encodes by hand (byte-verified against `@stellar/stellar-sdk` in `StellarAccountTest.kt`), `wallet/SorobanRpc.kt` speaks JSON-RPC over `HttpURLConnection`. Adding the SDK now buys nothing and risks the build. |
| ZXing (QR), Mapbox, `security-crypto` | ZXing only if we surface the view key as a QR (2 extra deps — see §5 cuts). Mapbox: never. `security-crypto` we already have (`wallet/app/build.gradle.kts:65`). |
| `data/stellar/ScvalParse.kt` | **Not copyable.** It imports `com.soneso.stellar.sdk.Address` / `scval.Scv` / `xdr.SCValXdr` (`ScvalParse.kt:3-5`) — it is a thin wrapper over the SDK we are not taking. See GAP 3. |

### 1.4 Honest work estimate

| Step | Estimate |
|---|---|
| Gradle + manifest + `ComponentActivity` + copied theme, green build showing a themed screen | **45-75 min** (all artifacts cached; identical toolchain; no migration work) |
| Port the 6 debug buttons of `spike/MainActivity.kt` onto Compose state | 30 min |
| Everything else (3 screens, new components, XDR decoding, HTTP client) | this is the real Session 6 — see §4 |

**Risk of the Compose adoption itself: low.** The risk in Session 6 lives elsewhere: the
WebView bridge's lifecycle inside Compose, the XDR decoding for the timeline, and the goal
total (§5).

---

## 2. COPY LIST

All sources are under `C:\Blockota\Proyectos\Protocolo_Raiz\android\app\src\main\java\com\raiz\app\`
(abbreviated `RAIZ/` below). All destinations are under
`C:\SP_WorkShop\wallet\app\src\main\java\xyz\raiz\sobre\` (abbreviated `SOBRE/`).
Every copied file needs its `package` line rewritten and its `com.raiz.app.*` imports
retargeted; that is the only edit unless stated.

### Tier 1 — verbatim, zero risk (do this first)

| From (RAIZ) | To (SOBRE) | Brings | Notes |
|---|---|---|---|
| `ui/theme/Color.kt` (29 lines) | `ui/theme/Color.kt` | The 10 palette constants: `RaizBlack #1A1A1A` (:17), `RaizYellow #FBBF24` (:18), `RaizPurple #534AB7` (:19), `RaizGreen #0F6E56` (:20), `RaizBackground #FAFAF7` (:21), `RaizBlack80` (:24), `RaizGray` (:25), `RaizGrayLight #EAEAE6` (:26), `RaizError #B00020` (:27), `RaizWhite` (:28) | Only import is `androidx.compose.ui.graphics.Color`. Copy byte-for-byte. |
| `ui/theme/Type.kt` (45 lines) | `ui/theme/Type.kt` | `RaizTypography`: overrides only `displayLarge` 48sp Bold (:19-25), `headlineMedium` 22sp SemiBold (:26-31), `bodyMedium` 14sp (:32-37), `labelLarge` 16sp SemiBold (:38-44); other 11 M3 styles stay default | `FontFamily.Default` — **there is no `res/font/` in RAIZ**, no custom font to copy. |
| `ui/theme/Theme.kt` (52 lines) | `ui/theme/Theme.kt` | `RaizTheme { }` = `lightColorScheme(...)` mapping at :29-43 + `RaizTypography` | **Rename the composable to `SobreTheme`** (one identifier) — same tokens, distinct app. Note only 13 M3 roles are set (:29-43): `surfaceVariant`, `primaryContainer`, `outlineVariant` etc. fall back to Material defaults. Do not rely on them. |
| `ui/util/StellarExpert.kt` (62 lines) | `ui/util/StellarExpert.kt` | `BASE` testnet URL (:22), `txUrl` (:29), `addressUrl` — routes `C…`→`/contract/`, `G…`→`/account/` (:36-37), `contractUrl` (:44), `open(context,url)` with the `ActivityNotFoundException` guard (:51-61) | Pure Android, no Compose, no RAIZ deps. Our `CtWallet.TxOutcome.explorerUrl` (`wallet/CtWallet.kt:39`) already builds the same tx URL from `CtConfig.EXPLORER_TX` (`wallet/CtConfig.kt:30`) — **after copying, delete one of the two so there is a single source of truth.** |
| `ui/components/StatBox.kt` (75 lines) | `ui/components/StatBox.kt` | `StatBox(label, value, accent, modifier)` (:28-53) — the canonical flat-card idiom: `.clip(RoundedCornerShape(16.dp)).background(RaizWhite).padding(20.dp)` + `spacedBy(4.dp)`, label at `bodyMedium` `RaizBlack.copy(alpha=0.6f)`, value at `headlineMedium` Bold in `accent` | Its `@Preview` (:55-75) needs `ui-tooling-preview` (already in §1.2 deps). Keep the preview — RAIZ's only verification convention is `@Preview` + logs (there is **no test source set anywhere in RAIZ**; `android/app/build.gradle.kts:97-153` declares no junit/espresso/mockk). |

### Tier 2 — copy with a named edit

| From (RAIZ) | To (SOBRE) | Required edit | Why |
|---|---|---|---|
| `ui/components/BalanceCard.kt` (113 lines) | `ui/components/SobreCard.kt` | Replace `com.raiz.app.data.model.formatUsdc` (imported :26, used :72) with our own formatter; change the label `"Tu saldo"` (:66) to `"Tu sobre"`; keep the black card, 44sp balance (:71-79) and truncated-pubkey row (:81-99) | `formatUsdc()` lives in `data/model/RaizConstants.kt:46-49` and hardcodes `" USDC"` + 3 decimals; we are on XLM stroops. Copy the *implementation* into our formatter (GAP 4), not the import. |
| `ui/dashboard/DashboardScreen.kt:507-565` (`UsageBar`) | `ui/components/GoalProgressBar.kt` | Take the stacked-bar body (Row `height(14.dp)`, `clip(RoundedCornerShape(7.dp))`, `background(RaizGrayLight)`, two weighted `Box`es in `RaizYellow`/`RaizGreen`, :527-550) and the caption row (:551-563). Drop `DashboardUiState` and `formatUsdc` (:509, :553) — take `(reachedPct: Int, caption: String)` instead | Straight visual match for "% de la meta alcanzado". |
| `ui/dashboard/DashboardScreen.kt:571-621` (`ExecutionRow`) | `ui/components/AporteRow.kt` | **Delete the amount `Text` at :607-611 entirely** (`exec.amountStroops.formatUsdc()` in `RaizGreen` bold). Keep: 40dp `CircleShape` icon box with `RaizYellow.copy(alpha=0.16f)` (:582-590), title at `labelLarge` (:593-597), monospace truncated address at 12sp `RaizBlack.copy(alpha=0.5f)` (:598-605), `IconButton` → `StellarExpert.open(...)` (:615-619) | The row is already "who + link, right-aligned figure". Removing the figure IS the product: *"quién y cuándo — NUNCA cuánto"*. Put the date where the amount was. |
| `ui/dashboard/DashboardScreen.kt:649-683` (`ContratoFila`) | `ui/components/VerifyRow.kt` | Keep as-is; feed it `("CT wrapper", CtConfig.TOKEN)`, `("goal_meta", GOAL_META_ID)`, `("verifier", CtConfig.VERIFIER)`, `("auditor", CtConfig.AUDITOR)` from `wallet/CtConfig.kt:16-18` | This is the "Verifícalo tú mismo" footer, already built: clickable row, `RaizPurple` monospace `take(8)…takeLast(6)`, 14dp `OpenInNew`. |
| `ui/wallet/WalletScreen.kt:353-432` (`AccountSetupBanner`) | `ui/components/PhaseBanner.kt` | Generalize `(title, body, error, cta, inProgress, onAction)`. Keep the shell: `RaizYellow.copy(alpha=0.18f)` background, `RoundedCornerShape(16.dp)`, `padding(16.dp)`, `spacedBy(8.dp)` (:388-394), title `labelLarge` (:395-399), body `bodyMedium` at alpha 0.7 (:400-404), error line (:405-412), full-width `RaizGreen` button that swaps its label for `CircularProgressIndicator` while busy (:413-431) | RAIZ's only honest long-operation UI. **Fix the drift while copying**: :410 hardcodes `Color(0xFFB00020)` instead of importing `RaizError` (same bug in 10 other files). |
| `ui/treasury/YieldScreen.kt:471-499` (`ActionFeedback`) + `ui/treasury/YieldViewModel.kt:22-25` (`TreasuryAction`) | `ui/components/StepFeedback.kt` | Rename `TreasuryAction` → `StepState` (`Idle`/`Submitting`/`Ok(msg)`/`Failed(msg)`) | Inline 3-state feedback: 14dp spinner + "Enviando a la red…", `RaizGreen` "Confirmado on-chain: …", `RaizError` message. Exactly what Aportar/Cosechar need. |

### Tier 3 — optional, only if the slot survives

| From | To | Cost |
|---|---|---|
| `ui/components/RaizSuccessAnimation.kt` (`RaizSuccessAnimation(titulo, subtitulo, tipBarrioUsdc, modifier)` :68) | `ui/components/SelloAnimation.kt` | Free dependency-wise (theme + `material-icons-extended` + coroutines, all present). **The third parameter must become participation text, never an amount.** High demo value for "Aporte sellado". |
| `ui/components/QrCard.kt` (:31) + `ui/qr/QrUtils.kt` (:23) | `ui/components/QrCard.kt` | **Drags 2 new deps**: `com.google.zxing:core:3.5.3` + `com.journeyapps:zxing-android-embedded:4.3.0` (`libs.versions.toml:18-19,82-83`). Only worth it if the view key ships as a scannable QR. Neither is confirmed in the local Gradle cache — check before committing to it. |
| `res/values/themes.xml:3-7` | `res/values/themes.xml` | 6 lines, transparent status bar + light status-bar icons. No AppCompat. |

### Do NOT copy

- `ui/components/PassportCard.kt` — 11.7 KB welded to RAIZ's passport/role domain.
- `ui/components/RaizBottomNav.kt` — imports `com.raiz.app.data.model.UserRole` (:18); we have 3 screens and no roles. If a bottom bar is wanted, lift only the `NavigationBarItemDefaults.colors(...)` block (:83-89).
- `data/stellar/*` — `ScvalParse.kt`, `SorobanClient.kt` (1475 lines), `WalletManager.kt`, `HorizonStream.kt`: all built on the Soneso SDK (`ScvalParse.kt:3-5`). We already own equivalents (`wallet/StellarAccount.kt`, `wallet/SorobanRpc.kt`, `wallet/WalletStore.kt`). **Read `SorobanClient.kt:1137-1290` for its `getEvents` cursor-pagination rules — including the documented "cuando cursor != null, startLedger DEBE ser null" (:1239) and the retention-error fallback (:1196-1228) — but do not copy the code.**
- `data/model/RaizConstants.kt` wholesale — lift only the 4-line `formatUsdc` implementation (:46-49) into our own formatter.
- `di/`, `RaizApplication.kt` — Hilt bootstrap and Conscrypt/TLS installation. Our TLS problem is already solved differently and better documented: `wallet/app/src/main/res/xml/network_security_config.xml:18-25` (additive Sectigo R46 anchor, no provider surgery).

**Total new Kotlin copied: ~430 lines across 11 files. New third-party dependencies added by
the entire copy list: zero** (beyond the Compose baseline of §1.2).

---

## 3. GAP LIST — what RAIZ simply does not have

### GAP 0 — the goal's decrypted total (the Meta screen's headline number)

**This is not a UI gap, it is a missing code path, and it is the biggest one.**
`assets/prover/raiz-shim.js:534` exports exactly
`RaizChain = { prepareRegister, prepareDeposit, prepareMerge, prepareTransfer, status }`.
`status(...)` (:517-530) decrypts balances for an account **using that account's own secret
scalar** via event replay — it cannot open an *auditor* channel. The published view key
(`scripts/verify-goal-total/config.json` → `viewKeySecretHex`) is an auditor key (id 1;
`scripts/ct-flow/deployment.json` → `auditorKeys[1]`), and the only code that opens it is
`scripts/verify-goal-total/verify-goal-total.mjs`, which runs in **Node** against the vendor
SDK dist.

Three options, in increasing cost:

- **(a) Don't show a number in-app.** The Meta screen shows the goal name, target, contributor
  count, the timeline, the published view key and the exact command to verify it. Copy line:
  *"El total no lo decimos nosotros — lo verificas tú."* This is philosophically stronger and
  costs zero engineering. Progress bar shows *contributions count*, not a currency %.
- **(b) Add a `goalTotal` method to `raiz-shim.js`** reusing the same vendor SDK calls
  `verify-goal-total.mjs` already makes, then rebuild the prover assets
  (`node wallet/tools/build-prover-assets.mjs`). 1-2 h, medium risk, and it makes the Meta
  screen depend on the WebView being warm — which contradicts "pública, sin login".
- **(c) Serve the total from a small endpoint.** Out of scope for Session 6.

**Recommendation: (a) for Session 6; (b) only if Session 7 finishes early.**

### GAP 1 — no timeline component, and no date rendering anywhere in RAIZ

A search across `android/app/src` for `SimpleDateFormat|DateTimeFormatter|Instant|java.text`
returns **zero hits**. `Execution.executedAt: Long` (`data/model/Execution.kt:21`) is used
only as a `LazyColumn` key (`DashboardScreen.kt:245`); `PaymentRecord.createdAt: String`
only for lexicographic sorting (`ui/profile/ProfileViewModel.kt:218`). The single time
function in the whole app is `private fun formatCountdown(seconds: Long)`
(`DashboardScreen.kt:409-418`).

**Proposal — `ui/util/Fechas.kt` (new, ~30 lines).**
`fun String.asRelativeEs(now: Instant = Instant.now()): String` parsing the ISO-8601 the
indexer already returns (`raiz-memory/src/db.rs` → `"ledgerClosedAt"`, real sample
`"2026-08-03T15:33:22Z"`). `java.time` is available unconditionally at minSdk 26 — no
desugaring. Output in RAIZ's register: `"hace 3 min"`, `"hace 2 h"`, `"ayer"`,
`"3 ago"`. Rendered at `bodyMedium`, 12sp, `RaizBlack.copy(alpha = 0.5f)` — the exact
style `ExecutionRow` used for its monospace subtitle (`DashboardScreen.kt:598-605`) — placed
in the slot where the amount used to be.

The list scaffolding itself is not a gap: `DashboardBody` (`DashboardScreen.kt:179-260`)
already does `LazyColumn` + `item {}` section headers with counts + `items(key=…)` + per-
section empty text; copy its structure.

### GAP 2 — no masked/hidden-amount rendering (expected, but it is 100% new work)

Nothing in RAIZ ever conceals a figure — every amount path ends in `formatUsdc()`.

**Proposal — `ui/components/MontoOculto.kt` (new, ~25 lines).** A pill:
`RoundedCornerShape(50)` (the RAIZ pill idiom, `PassportCard.kt:143-144`), background
`RaizPurple.copy(alpha = 0.12f)`, content `Icons.Outlined.Lock` 12dp + text `"•••"` in
`RaizPurple`, style `labelLarge`. Purple because RAIZ reserves green for confirmed value and
yellow for decorative accents — purple is its "badge/secondary" role (`Color.kt:14`,
`Theme.kt:33`), which is precisely the semantics of "there is a value here, deliberately not
shown". A caption under the timeline in `bodyMedium` / alpha 0.6: *"Los montos viven
cifrados. La participación es pública."*

### GAP 3 — no SCVal/XDR decoding available to us

Our app has no Stellar SDK, and RAIZ's `ScvalParse.kt` is unusable (imports
`com.soneso.stellar.sdk.*`, :3-5). But the timeline's "who" lives inside base64 XDR topics.

**Verified against real indexed rows** (`raiz-memory/ct_flow_s4.db`, our own Session 4 flow
against wrapper `CBWSANZN…DHAT`):

```
merge     topics = ["AAAADwAAAAVtZXJnZQAAAA==", "AAAAEgAAAAAAAAAAEvuBf9dbzjWEzu6G+9lP70ilq7oGZDnR+PZNbGiJloY="]
transfer  topics = ["AAAADwAAAAh0cmFuc2Zlcg==", "<from Address>", "<to Address>"]
deposit   topics = ["AAAADwAAAAdkZXBvc2l0AA==", "<from Address>", "<to Address>"]
```

Discriminants needed, and only these: `0x0F` SCV_SYMBOL (4-byte len + 4-byte-padded bytes),
`0x12` SCV_ADDRESS (4-byte type: `0`=account → 32-byte ed25519, `1`=contract → 32 bytes),
`0x03` SCV_U32, `0x0E` SCV_STRING. Matches the contract source:
`vendor/stellar-contracts/packages/tokens/src/confidential/mod.rs:610-616` (`Register`:
`#[topic] account`), `:623-631` (`Deposit`: `#[topic] from, to`), `:640-645` (`Merge`:
`#[topic] account`), `:689-707` (`Transfer`: `#[topic] from, to` — all ciphertext fields are
in the value, never a topic). And for our own contract:
`contracts/goal-meta/src/lib.rs:133-136` (`(goal, created, id)` → Address) and `:178-179`
(`(goal, harvest, id)` → String memo).

**Proposal — `wallet/ScVal.kt` (new, ~70 lines):** `fun decodeTopics(base64: List<String>):
List<ScTopic>` with `ScTopic.Sym(String) | Addr(String) | U32(Int) | Str(String) | Raw`.
`Addr` reuses the StrKey encoder we already own — `StellarAccount.kt:114-142` gives base32 +
CRC16-XMODEM and `StrKey.encodeEd25519PublicKey` (:116-124); a contract variant is the same
function with version byte `0x10` instead of `0x30`. This is the one piece of genuinely new
crypto-adjacent code, it is bounded, and it is unit-testable offline with the exact strings
above (our `src/test` set already exists: `wallet/app/src/test/.../StellarAccountTest.kt`).

**Alternative if it slips:** teach `raiz-memory` to emit an additive `topicDecoded` field.
Cheap in Rust, but it changes the indexer during Session 6 and weakens the
"getEvents-compatible, change one URL" claim (`raiz-memory/src/main.rs:117-120`). Prefer the
Kotlin decoder.

### GAP 4 — no address shortener, and the amount formatter is USDC-only

`x.take(8) + "…" + x.takeLast(6)` is inlined **9 times** in RAIZ (`BalanceCard.kt:89`,
`DashboardScreen.kt:599`, `:668`, `ui/profile/ProfileScreen.kt:233`, `:438`,
`ui/cobros/CobrosScreen.kt:276`, plus 12/8 and 8/8 variants). `formatUsdc()`
(`RaizConstants.kt:46-49`) hardcodes `" USDC"`.

**Proposal — `ui/util/Formato.kt` (new, ~20 lines):** `String.shortAddr(head: Int = 8, tail:
Int = 6)`, and `Long.formatXlm()` = RAIZ's implementation with the suffix parameterized
(`"%.3f".format(v/1e7).trimEnd('0').trimEnd('.') + " XLM"`). Write once, use everywhere —
this is the one place where being *better than RAIZ* costs nothing.

### GAP 5 — no long-operation / phase UI, no elapsed timer, no cancel

RAIZ's longest operation is a network round-trip guarded by one boolean (`submitting`,
`ui/pay/PayViewModel.kt:32`), rendered as a spinner swap (`ui/pay/PayScreen.kt:155-197`).
There is no staged progress, no elapsed counter, no determinate bar for local computation and
no cancel affordance anywhere in the codebase. Our proofs take 10-30 s on-device.

**Good news: the mechanics already exist on our side.** `CtWallet` emits Spanish phase
strings through a `progress: (String) -> Unit` callback at every stage —
`"generando prueba de registro… (~10 s en este teléfono)"` (`wallet/CtWallet.kt:45`),
`"sincronizando estado confidencial + generando prueba… (~15 s)"` (:85),
`"fondeando cuenta con friendbot (idempotente)…"` (:117),
`"firmando en Kotlin (la seed nunca sale del Keystore/ESP)…"` (:136),
`"enviando a testnet… tx …"` (:139), `"confirmando (poll getTransaction)…"` (:147).

**Proposal — `ui/components/PhaseBanner.kt` (Tier-2 copy of `AccountSetupBanner`) + an
elapsed line.** The banner shows the live phase string as its body, and under it a
`bodyMedium` alpha-0.5 line `"12 s · las pruebas tardan hasta 90 s en este teléfono"`, driven
by a `LaunchedEffect` ticking every second against `SystemClock.elapsedRealtime()` (the
pattern our debug screen already uses, `spike/MainActivity.kt:82,87`). No cancel button:
`ProverWebViewBridge` serializes calls behind a `Mutex` and enforces its own timeouts
(`PROOF_TIMEOUT_MS = 90_000`, `CHAIN_TIMEOUT_MS = 180_000`, bridge companion :380-384) —
inventing a cancel we cannot honor would be a lie in the demo.

### GAP 6 — no settings screen, no editable config field

RAIZ's `ProfileScreen` has toggles, no text input for configuration; there is no settings
surface at all. But the **configurable event-source URL is the video's central scene**
(CLAUDE.md: *"Timeline de la meta se alimenta de Raiz Memory vía URL configurable — cambiarla
en vivo es la escena central del video"*; `wallet/docs-integration/NOTES.md:26-31`).

**Proposal — `ui/meta/EventSourceSheet.kt` (new, ~80 lines).** A `ModalBottomSheet` (RAIZ has
exactly one, `ui/map/BarrioMapScreen.kt:276`, so the idiom exists) containing: two preset rows
styled like `ContratoFila` — **"RPC (olvida a los ~7 días)"** and **"Raiz Memory (recuerda)"**
— plus an `OutlinedTextField` with `RoundedCornerShape(12.dp)` (`ui/pay/PayScreen.kt:338`) for
a custom base URL. Persist in a **plain** `SharedPreferences("sobre_settings")` — precedent:
RAIZ keeps non-secrets in plain prefs (`data/security/AppLock.kt:28-29,45-48`) and secrets in
`EncryptedSharedPreferences`, which is exactly the split we already have in
`wallet/WalletStore.kt:22-33`. **Do not put the URL in `WalletStore`** — that file is for key
material only.
Cleartext LAN URLs work already: `res/xml/network_security_config.xml:19` sets
`cleartextTrafficPermitted="true"`.

### GAP 7 — no shared loading/error/empty components

RAIZ duplicates them 6-7× each (loading: `DashboardScreen.kt:690`, `WalletScreen.kt:435`,
`YieldScreen.kt:515`, `CobrosScreen.kt:357`, `ProposalsScreen.kt:399`, `ProfileScreen.kt:719`;
error: same files; best empty state `ProposalsScreen.kt:411-440`).

**Proposal — `ui/components/Estados.kt` (new, ~60 lines):** `CargandoBox(texto)`,
`ErrorBox(mensaje, onRetry)`, `VacioBox(icono, titulo, pista)`. Copy the visuals verbatim
(centered `CircularProgressIndicator(color = RaizGreen)`; `"No pudimos cargar X.\n$message"`;
56dp faded icon + headline + hint), declare them **once**. Consistent with RAIZ's look, better
than RAIZ's code.

### GAP 8 — no pull-to-refresh (and our own notes assume there is one)

`wallet/docs-integration/NOTES.md:31` scripts the demo as *"Pull-to-refresh → historial
completo"*. **RAIZ has none** — two KDoc comments claim it (`CobrosScreen.kt:68-69`,
`ProposalsScreen.kt:75`) but no `PullToRefreshBox` / `SwipeRefresh` exists in the codebase.
Refresh is a `Refresh` `IconButton` in the top bar (`DashboardScreen.kt:141`,
`CobrosScreen.kt:94`, `ProposalsScreen.kt:114`, `YieldScreen.kt:108`) plus an `ON_RESUME`
`LifecycleEventObserver` (`WalletScreen.kt:104-111`).

**Proposal: change the script, not the app.** Use the `Refresh` IconButton — on camera it is
*more* legible (a visible tap on a labelled control beats an off-screen gesture). Material3
1.3.1 does ship `pulltorefresh`, but it is `@ExperimentalMaterial3Api` and unverified on our
device; spending demo-day risk on a gesture is not a trade we should take.
**Action: update `NOTES.md:31` when this plan is accepted.**

---

## 4. SCREEN BLUEPRINTS

Conventions taken from RAIZ and kept: screen = top-level `@Composable` with `on*` navigation
lambdas defaulted + the state holder last (`ui/pay/PayScreen.kt:63-67`); state = sealed
interface with `Loading`/`Ready`/`Error` collected via `collectAsState()`
(`ui/wallet/WalletViewModel.kt:37-48`, `ui/pay/PayViewModel.kt:71-72`); actions = plain public
methods referenced as `viewModel::method`; errors never thrown into the UI
(`data/model/RaizResult.kt:7-33`); `Scaffold(containerColor = MaterialTheme.colorScheme.background)`
per screen; private sub-composables take `contentPadding: PaddingValues` explicitly.
**Difference from RAIZ:** no Hilt — construct view models with a plain
`viewModel(factory = viewModelFactory { … })`, or hold them in the Activity.

### 4.1 Screen A — "Meta" (public, no login)

Closest RAIZ ancestor: `ui/dashboard/DashboardScreen.kt` (787 lines) — already a public
transparency screen reachable without login (`MainActivity.kt:586-592`).

| Part | Renderer |
|---|---|
| Scaffold + top bar (title + `Refresh` IconButton) | copy `TopBar` `DashboardScreen.kt:125-146` |
| Goal header (name, deadline countdown) | new `MetaHeader`; countdown from `formatCountdown` `DashboardScreen.kt:409-418` |
| Contribution progress | **new** `GoalProgressBar` (Tier-2 copy of `UsageBar` :507-565). Caption: contributor/contribution counts, **not** currency — see GAP 0 |
| Stat row: aportantes · aportes · cosechas | `StatBox` (Tier 1) in a `Row(spacedBy(12.dp))` |
| "Verifícalo tú mismo" card: view key hex + contract rows + copy-to-clipboard | **new** `VerifyCard` wrapping copied `VerifyRow` (`ContratoFila` :649-683); clipboard idiom from `ProfileScreen.kt:238-247` |
| Timeline list | `LazyColumn` structured like `DashboardBody` :179-260; rows = **new** `AporteRow` (copy of `ExecutionRow` :571-621 **minus :607-611**) + `MontoOculto` pill (GAP 2) + relative date (GAP 1) |
| Event-source badge ("RPC" vs "Raiz Memory") + link to the sheet | **new** `SourceChip`, styled as `AssistChip` (`DashboardScreen.kt:148-177`) |
| Empty / loading / error | **new** `Estados.kt` (GAP 7) |

```kotlin
sealed interface MetaUiState {
    data object Loading : MetaUiState
    data class Ready(
        val goalName: String,
        val goalAccount: String,          // CtConfig.GOAL_ACCOUNT
        val viewKeyHex: String,           // published on purpose
        val deadlineEpoch: Long,
        val aportes: List<TimelineEntry>, // transfers TO the goal
        val cosechas: List<TimelineEntry>,// merges BY the goal + goal_meta harvest events
        val aportantes: Int,              // distinct `from`
        val source: EventSource,          // RAIZ_MEMORY | RPC_SIMULATION | CUSTOM
        val oldestLedger: Long?,          // present when the source declares a retention floor
        val latestLedger: Long,
        val refreshing: Boolean = false,
    ) : MetaUiState
    data class Error(val message: String) : MetaUiState
}

data class TimelineEntry(
    val kind: Kind,          // APORTE | COSECHA | APERTURA
    val who: String,         // G… — never an amount
    val whenIso: String,     // ledgerClosedAt, ISO-8601
    val ledger: Long,
    val txHash: String?,
) { enum class Kind { APORTE, COSECHA, APERTURA } }

enum class EventSource(val label: String) {
    RAIZ_MEMORY("Raiz Memory — recuerda"),
    RPC_SIMULATION("RPC — olvida a los ~7 días"),
    CUSTOM("Personalizado"),
}
```

**Data source — `wallet/RaizMemoryClient.kt` (new, ~90 lines).** Same idiom as
`wallet/SorobanRpc.kt`: `HttpURLConnection` + `org.json` + the `retrying()` backoff
(`SorobanRpc.kt:129-147`), called on `Dispatchers.IO`. Request, verified against
`raiz-memory/src/main.rs:104-114`:

```
GET {baseUrl}/events?contractId=<CtConfig.TOKEN>&startLedger=<CtConfig.DEPLOYED_AT_LEDGER>&limit=200
        [&source=rpc-simulation]     ← ONLY for the "RPC forgets" preset
```

Response fields we read (`raiz-memory/src/main.rs:88-99`, `src/db.rs`):
`latestLedger`, `cursor`, optional `oldestLedger` (only in purge-demo mode), and per event
`id`, `ledger`, `ledgerClosedAt`, `txHash`, `topic[]` (base64 XDR), `value`.
Pagination: pass `cursor` back and **omit `startLedger`** when a cursor is present (the RPC
rule RAIZ documents at `SorobanClient.kt:1239`; our indexer's SQL honors the same at
`raiz-memory/src/db.rs`).

Mapping (topics decoded by GAP 3's `ScVal.kt`):
- `["transfer", from, to]` with `to == CtConfig.GOAL_ACCOUNT` → `APORTE(who = from)`
- `["merge", account]` with `account == CtConfig.GOAL_ACCOUNT` → `COSECHA`
- `["register", account]` → `APERTURA` (optional, low value on the public screen)
- everything else → dropped. **Deposits are never rendered on the Meta screen** — their
  amount is public by CT design (`vendor/.../confidential/mod.rs:623-631`) and this screen
  must not display a figure.
- Second call, same client, `contractId = goal_meta` (`CBNVY2AA…IQAZ`, from
  `scripts/verify-goal-total/config.json`) → `("goal","harvest",id)` events give the harvest
  memo (`contracts/goal-meta/src/lib.rs:178-179`).

Base URL comes from `SettingsStore` (GAP 6), never a constant. **This is the video: one field,
two presets, the same screen.**

### 4.2 Screen B — "Mi Sobre"

Closest RAIZ ancestors: `ui/wallet/WalletScreen.kt` (layout: title → banner → balance card →
stats → pinned CTA, :218-337) and `ui/pay/PayScreen.kt` (submit button semantics :155-197).

| Part | Renderer |
|---|---|
| Balance card (decrypted on-device) | `SobreCard` (Tier-2 copy of `BalanceCard.kt`), amount = `spendableStroops` |
| "Pendiente de cosechar" | `StatBox(label="Pendiente de cosechar", value=…, accent=RaizYellow)` fed by `receivingStroops` |
| Not-registered state → "Abrir mi sobre" | `PhaseBanner` (GAP 5), CTA fires `CtWallet.register` |
| Amount field + "Sellar" / "Aportar" | `OutlinedTextField` `RoundedCornerShape(12.dp)` (`PayScreen.kt:338`) + two buttons, 56dp tall, `RoundedCornerShape(16.dp)` (`ui/welcome/WelcomeScreen.kt:89-111`) |
| In-flight proof | `PhaseBanner` body = the live `progress` string from `CtWallet` + elapsed seconds |
| Success | `StepFeedback.Ok` + optional `SelloAnimation` (Tier 3); tx link via `StellarExpert.txUrl` |
| Errors | `StepFeedback.Failed` with the verbatim message — `ProverException` subclasses already carry actionable text (`prover/ProverWebViewBridge.kt:31-41`) |
| Address row | `SobreCard`'s truncated pubkey → `StellarExpert.addressUrl` |

```kotlin
sealed interface SobreUiState {
    data object Loading : SobreUiState
    data class Ready(
        val accountId: String,
        val registered: Boolean,
        val spendableStroops: Long?,      // null = not yet decrypted
        val receivingStroops: Long?,
        val syncedLedger: Long?,
        val amountInput: String = "",
        val op: OpState = OpState.Idle,
    ) : SobreUiState
    data class Error(val message: String) : SobreUiState
}

sealed interface OpState {
    data object Idle : OpState
    data class Running(val op: String, val phase: String, val elapsedMs: Long) : OpState
    data class Ok(val method: String, val ledger: Long?, val txHash: String?, val proveMs: Long) : OpState
    data class Failed(val message: String) : OpState
}
```

**Data source — `CtWallet` as it stands, no changes:** `status()` → `registered`,
`spendableStroops`, `receivingStroops`, `syncedLedger` (`wallet/CtWallet.kt:102-112`, JSON
keys confirmed in `assets/prover/raiz-shim.js:517-530`); `register()` :43-59;
`deposit(amountStroops)` :62-74; `transferToGoal(amountStroops)` :84-99; `merge()` :77-81.
Each takes `progress: (String) -> Unit` → map directly onto `OpState.Running(phase = it)`.

**WebView lifecycle (the real integration risk).** `ProverWebViewBridge` must be constructed,
`initialize()`d and `destroy()`ed on the main thread (class KDoc, :56-59), holds a **headless**
WebView built from `applicationContext` (:66), and serializes calls behind a `Mutex` (:85).
Therefore: **own it in the ViewModel, not in a composable.** Construct in the VM's
constructor (created on the main thread from composition), `destroy()` in `onCleared()`, never
inside a `remember`/`DisposableEffect` keyed on recomposition — an accidental dispose mid-proof
kills a 15 s operation. Keep the config-change armor already in the manifest
(`AndroidManifest.xml:33-35`, `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`).
Also mirror the page console: `bridge.consoleListener` (used at `spike/MainActivity.kt:52-56`)
is the only viewport into JS failures on this device family — keep it behind a debug toggle.

Auto-refresh on return: `DisposableEffect` + `LifecycleEventObserver(ON_RESUME)`
(`WalletScreen.kt:104-111`).

### 4.3 Screen C — "Cosechar"

**Read this before planning the screen.** Two different operations share the word "cosechar":

1. **The contributor's own merge** — `CtWallet.merge()` (`wallet/CtWallet.kt:77-81`), proof-free,
   works today, already wired in the debug screen (`spike/MainActivity.kt:138-140`). This is
   the `merge` step of the MUST-tier CT cycle in CLAUDE.md.
2. **The goal admin's harvest** — merging the *goal account's* receiving balance and then
   calling `goal_meta.record_harvest` (`contracts/goal-meta/src/lib.rs:160-181`). **Neither
   has a code path in the app:** `CtWallet.merge` merges `account` (the device's own account,
   :29) and there is no goal-admin signer on the device; and `raiz-shim.js:534` exports no
   `prepareRecordHarvest`, while `SorobanRpc` has no `simulateTransaction`
   (`wallet/SorobanRpc.kt` exposes only `sendTransaction`/`getTransaction`/`pollUntilComplete`/
   `friendbotFund`).

**Recommendation: Screen C is "Cosechar mi sobre" (case 1) plus a read-only harvest history
(case 2 rendered from events).** The admin harvest itself runs from `scripts/goal-flow.sh` and
appears in the timeline via `goal_meta`'s `("goal","harvest",id)` event. That is honest, it
demos identically on camera, and it costs zero new plumbing. Building case 2 into the app
means a new shim method + rebuilt prover assets — that is a Session 7 decision, not a
Session 6 screen.

| Part | Renderer |
|---|---|
| Explanation card ("qué es cosechar") | `PhaseBanner` shell, no CTA |
| Step 1 "Cosechar mi sobre" (merge) | Button + `StepFeedback` (copy of `ActionFeedback` `YieldScreen.kt:471-499`) |
| Step 2 "Registrar la cosecha de la meta" | **admin-only, hidden by default.** If enabled: same shell, disabled with the honest caption *"Se ejecuta desde el script de la meta"* |
| Harvest history | `LazyColumn` of `AporteRow(kind = COSECHA)` fed by the same `RaizMemoryClient` |

```kotlin
sealed interface CosechaUiState {
    data object Loading : CosechaUiState
    data class Ready(
        val receivingStroops: Long?,       // what would be harvested
        val misCosechas: List<TimelineEntry>,
        val cosechasDeLaMeta: List<TimelineEntry>,
        val step: StepState = StepState.Idle,
    ) : CosechaUiState
    data class Error(val message: String) : CosechaUiState
}
// StepState = RAIZ's TreasuryAction, renamed (ui/treasury/YieldViewModel.kt:22-25)
sealed interface StepState {
    data object Idle : StepState
    data object Submitting : StepState
    data class Ok(val message: String) : StepState
    data class Failed(val message: String) : StepState
}
```

Note RAIZ's precedent for chained operations (`DashboardViewModel.closeVoting` :105-132 then
`executeProposal` :135-168): **it does not auto-chain** — the operator taps twice and the
second button appears after a refresh. There is no sequential runner to reuse; do not plan one.

### 4.4 Suggested Session 6 order

1. Gradle + `ComponentActivity` + Tier-1 copies → themed empty screen builds. *(~1 h)*
2. `Formato.kt`, `Estados.kt`, `MontoOculto.kt`, `Fechas.kt` — the four things RAIZ lacks. *(~1 h)*
3. `ScVal.kt` + unit test against the three real topic strings in §GAP 3. *(~1 h)*
4. `RaizMemoryClient.kt` + `SettingsStore` + `EventSourceSheet`. *(~1.5 h)*
5. Meta screen (highest demo value, no WebView dependency). *(~2 h)*
6. Mi Sobre on top of `CtWallet` (VM owns the bridge). *(~2 h)*
7. Cosechar (thin — merge + history). *(~1 h)*

Screens 5 and 6 are independent; Meta is the one that must not slip because it is the video's
spine.

### 4.5 Two housekeeping notes

- `wallet/app/build.gradle.kts:7` still declares `namespace = "xyz.raiz.sobre.spike"` while all
  code lives in `xyz.raiz.sobre.*`. Changing the **namespace** is free (`BuildConfig.DEMO_URL`
  is declared at :24 but referenced nowhere in Kotlin — verified by grep). Changing the
  **`applicationId`** (:11) is NOT free: it reinstalls as a different app, wiping
  `EncryptedSharedPreferences` (`WalletStore.FILE_NAME = "sobre_wallet_keys"`, :64) and with it
  the seed and the CT scalar — the device's registered CT account is gone and `register` must
  run again (~10 s proof). If we want a clean `applicationId`, **do it in Session 6, not on
  demo day.**
- RAIZ has **no tests at all** (no `src/test`, no `src/androidTest`, zero `@Test`, no test deps
  in `android/app/build.gradle.kts:97-153`). We already have a JVM test source set
  (`wallet/app/src/test/java/xyz/raiz/sobre/wallet/StellarAccountTest.kt`). Keep it and add the
  `ScVal.kt` decoder test — that is a deviation from RAIZ, and the right one.

---

## 5. RISKS AND CUTS (ordered — cut from the top when time runs out)

**Cut list, first to go:**

1. **`SelloAnimation` / `RaizSuccessAnimation`** (Tier 3). Pure delight, zero information.
2. **QrCard + ZXing** for the view key. Two new dependencies, unverified in the local cache;
   a copy-to-clipboard button plus the monospace hex conveys the same and matches
   `ProfileScreen.kt:238-247`.
3. **Screen C's admin harvest step.** Ship "cosechar mi sobre" + read-only history; run the
   goal harvest from `scripts/goal-flow.sh`. Do not add a shim method under time pressure.
4. **The `APERTURA` (register) entries** in the public timeline. Noise; the aportes are the story.
5. **Screen C entirely.** CLAUDE.md's MUST list needs `merge` as part of the CT cycle — that can
   live as a secondary button on Mi Sobre. A third screen is not a MUST.
6. **`goalTotal` in-app** (GAP 0 option b). Already recommended as a non-goal; keep it that way
   unless Session 7 finishes early.

**Top risks, with the mitigation each:**

| # | Risk | Evidence | Mitigation |
|---|---|---|---|
| 1 | **The Meta screen's headline number has no code path.** `raiz-shim.js:534` exports no auditor-decrypt; only `scripts/verify-goal-total/verify-goal-total.mjs` (Node) can open the published view key. Discovering this on day 8 means either a hurried shim rebuild or an empty hero slot. | shim exports :534; `status()` :517-530 uses the account's own `sk`; `verify-goal-total.mjs` header §1-3 | Decide **now** for option (a): the Meta screen shows participation, the view key and the verification command — not a currency total. It is a stronger pitch and it removes the dependency. |
| 2 | **Timeline "quién" requires XDR decoding we do not have.** RAIZ's `ScvalParse.kt` is unusable (Soneso SDK imports :3-5), and adding the SDK drags Ktor/BouncyCastle plus 8 packaging excludes (`android/app/build.gradle.kts:78-94`). Without a decoder, the timeline is a list of opaque base64. | `raiz-memory/src/db.rs` stores `topics_json` as raw base64 XDR; real samples in `ct_flow_s4.db` | Write `ScVal.kt` (~70 lines, 4 discriminants) on day 1 of Session 6 and unit-test it offline against the three real topic strings quoted in GAP 3. Fallback: additive `topicDecoded` field in `raiz-memory`. |
| 3 | **WebView-in-Compose lifecycle kills long proofs.** `ProverWebViewBridge` is main-thread-bound, headless, mutex-serialized, and has burned us before (the class KDoc documents two fake "timeouts" caused by `View.post()` on a detached view, :68-73). A `DisposableEffect` disposing it on recomposition or rotation destroys a 15-30 s proof mid-flight. | `prover/ProverWebViewBridge.kt:56-59,66-74,85`; `AndroidManifest.xml:33-35` | Bridge is owned by the ViewModel, created in its constructor, destroyed only in `onCleared()`. Never `remember { ProverWebViewBridge(...) }`. Keep `configChanges` in the manifest. Test rotation during a proof before the video. |
| 4 | Compose adoption blows the timebox | — | Low: identical toolchain, all artifacts cached (§1.2). If the Compose compiler plugin misbehaves, the fallback is the existing imperative UI — ugly, but the pipeline already works there (`spike/MainActivity.kt`). |
| 5 | `NOTES.md:31` scripts a pull-to-refresh that RAIZ does not have and we should not build | `CobrosScreen.kt:68-69` and `ProposalsScreen.kt:75` claim it; no implementation exists anywhere | Rewrite the demo beat as a visible "Actualizar" tap (`DashboardScreen.kt:141` idiom). More legible on camera. |
| 6 | `applicationId` rename late in the week wipes the device's CT account | `WalletStore.kt:36-61` (seed + scalar in `EncryptedSharedPreferences`), `app/build.gradle.kts:11` | Rename in Session 6 or never. |
