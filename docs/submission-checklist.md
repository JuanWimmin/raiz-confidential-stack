# Submission checklist — Sobre del Barrio × Raiz Memory

**Compiled 2026-08-04** from four independent audits (fresh-clone judge, README truth,
bounty fit, hygiene/safety), with every contested point re-verified against the live
repo and testnet before it was written down here.

Deadline: **6 August 2026, 17:00 São Paulo**; the team sends before noon.
Working budget from now: roughly **two working days**.

State at compile time: `HEAD = b8086f5`, branch `main`, working tree clean apart from
an untracked `web/` landing page.

---

## 0. Corrections to the audits (read this before trusting them)

Four audits disagreed on five facts. Resolved by direct check:

| Question | Verdict | Evidence |
|---|---|---|
| "There is no git remote; the repo was never pushed" (Audit 3 #1, Audit 4 S3) | **STALE — already fixed** | `git remote -v` → `origin https://github.com/JuanWimmin/raiz-confidential-stack.git`. `gh repo view` → `"visibility":"PUBLIC"`, `"pushedAt":"2026-08-04T15:49:54Z"`. `git rev-list --left-right --count origin/main...main` → `0  0`. Branch is already `main`, not `master`. |
| Is `CCUUDM434…MCGZ` Nethermind's SPP pool? | **Audit 2 is right, Audit 3 is wrong** | `vendor/stellar-private-payments/deployments/testnet/deployments.json:27` lists it as `tokenContractId` (classic EURC, issuer `GB3Q6QDZ…`) of pool `CCBOHPJ2…`. It is the token SAC the pool settles against, not a pool. |
| Do the real SPP pools have events? | **No — 0, both** | Live `getEvents` from ledger 3899359 (their deployment ledger) on `CCBOHPJ2…` and `CCG3ICXN…` → `"events":[]`, `latestLedger:3967925`. |
| How many wallet files are RAIZ-derived — 11 (Audit 2) or 13 (Audit 4)? | **13. Audit 4 is right.** | `grep -rln "RAIZ\|RAÍZ" wallet/…/ui/` returns 15 files, but `ui/meta/MetaScreen.kt:548` ("RAIZ has no equivalent") and `ui/nav/Formato.kt:7` ("the four formatting helpers RAIZ never wrote") mention RAIZ only to *contrast*. The 13 adopted files are the ones Audit 4 lists. |
| Is the honest-clamp narrative visible on the running instance? | **No — Audit 2 #36 is right** | Live `GET http://localhost:8091/coverage` returns four contracts and **no `backfill` object on any of them** (the db predates `backfill_marks`). The money quote about unreachable ledgers only appears on a **freshly built** index. |

**One consequence of the repo already being public:** Audit 4's framing — *"nothing is
public — everything below is still fixable without a history rewrite"* — no longer
holds. The `C:\Users\juanp` and `C:\Blockota` paths (B6) are published. That is
low-severity and stays published; see WON'T-DO. It also makes the missing LICENSE
**more** urgent, not less: the repo is live right now under implicit all-rights-reserved.

---

## 1. VERDICT

**We are not submission-ready today, but the gap is documentation, not engineering.**
The mandatory deliverable now exists — the repo is public at
`https://github.com/JuanWimmin/raiz-confidential-stack`, in sync with local `main` —
so the one existential item flagged by the audits is closed. What is genuinely built
and verifiable is strong: a real on-device CT cycle with four testnet transactions
that resolve as `SUCCESS`; a `confidential_transfer` payload with no amount field
anywhere in it; a durable index that right now serves **906 events the public RPC can
no longer return at any `startLedger`** (36 for the official CT demo wrapper, 870 for
the EURC SAC, against a live retention floor of ledger 3846966); and an outside-the-app
verifier that, run cold from this machine minutes ago, printed
`Goal total: 63 XLM — verified on-chain at ledger 3967943` with every contribution
itemized and re-committed against live Pedersen commitments.

**The problem is that the README describes a weaker, older, and in five places a false
version of that.** It is five commits stale, so it denies our own best work: it says
Raiz Memory *"has no backfill yet"* (README:235) when `backfill.rs` shipped with four
tests; it says the app *"currently ships a working debug console … `TODO(session-6)`"*
(README:203) when three screens shipped in `576ba76`; it says the wallet *"imports no
RAÍZ code yet"* (README:215) when 13 files carry in-header "adopted from RAIZ"
provenance — and that sentence sits in the one section the "100% original work" rule
sends a judge to. Two further claims are simply false against the chain: `--rpc` at a
Raiz Memory instance returns HTTP 404 (README:133, sold as *"the whole thesis, in one
flag"*), and `CCUUDM434…MCGZ` is Nethermind's EURC token SAC, not their SPP pool
(README §2), which a Nethermind judge — co-sponsor of this lane — resolves in one click.

**Two hard gaps remain beyond documentation:** the demo video is not recorded (script
rehearsed and timed at 150 s in `docs/demo-run.md`, but the phone was wiped by a clean
reinstall and needs register + deposit + merge again first), and there is **no LICENSE
file** on a now-public repo whose own `raiz-memory/Cargo.toml:6` and
`contracts/goal-meta/Cargo.toml:6` both declare `license = "MIT"` — a repo that
contradicts itself and, as published, forbids the very reuse the bounty asks for
(*"a durable event index other builders can point a wallet at"*).

**Judge's-eye summary:** a rival reading only our README would say *"they claim a wallet,
their README says debug console; they claim originality, their README denies RAIZ reuse
that 13 of their own files declare; they claim one-flag durability, the flag 404s."*
Every one of those quotes is from our own file, every one is now false, and every one is
a text edit. Fix the README against HEAD and record the video, and this becomes a
submission whose central claim a judge can check in one command without trusting our UI —
which almost no hackathon entry can say.

---

## 2. BLOCKING

Ordered. These are the items without which we cannot submit, or would be actively
penalised for a claim a judge can disprove. **Total: ~6 h of edits + ~4 h of video.**

### B1 — README:133: the flagship claim returns 404 · **30 min**

```
README.md:133  Point `--rpc` at a Raiz Memory instance instead of the RPC and the same
               verification keeps working after the 7-day window closes. That is the
               whole thesis, in one flag.
```

Verbatim failure (Audit 1, confirmed independently against the code):

```
$ node scripts/verify-goal-total/verify-goal-total.mjs --rpc http://localhost:8099
  ! goal_meta.get_goal attempt 1/3 failed (Request failed with status code 404); retrying in 3s
verify-goal-total failed: goal_meta.get_goal failed after 3 attempts: Request failed with status code 404
```

Root cause is dispositive in the source — `raiz-memory/src/main.rs:89-92` is the whole
surface:

```rust
let app = Router::new()
    .route("/health", get(health))
    .route("/coverage", get(coverage))
    .route("/events", get(events))
```

REST, not Soroban JSON-RPC. The verifier needs `simulateTransaction`,
`getLedgerEntries` and `getLatestLedger`; none exist. This is the sentence that welds
bounty example (a) to example (c) **and** the axis on which README §2 differentiates us
from Nethermind's bootnode, which *does* speak JSON-RPC. It is a ten-second test on a
page that opens with "nothing here is a mockup".

**Action (do this one, not the big one):** rewrite L133 to what is true —
*"Raiz Memory serves the event history; the wallet reads it over `/events` and the chain
state still comes from an RPC. That split is deliberate: replaying events is what dies
after 7 days, and that is the half we made durable."* Then fix the misleading advice in
the failure path at `scripts/verify-goal-total/verify-goal-total.mjs:201` and the same
claim at `scripts/verify-goal-total/README.md:6-7` and `:95-97`.

**Optional upgrade if and only if B1–B9 are done and the video is cut (+90 min):** add a
`--events-url` flag that replays events from Raiz Memory while ledger entries still come
from the RPC. That makes the honest sentence *demonstrable* rather than merely accurate.
Do not attempt this before the video.

### B2 — README §2 + §8: "Nethermind's SPP EURC pool" is not a pool · **20 min**

`CCUUDM434BMZMYWYDITHFXHDMIVTGGD6T2I5UKNX5BSLXLW7HVR4MCGZ` is the `tokenContractId` in
`vendor/stellar-private-payments/deployments/testnet/deployments.json:27` — the classic
EURC SAC (issuer `GB3Q6QDZ…`) that pool `CCBOHPJ2…` settles against. Its events decode
to plain SAC transfers. A Nethermind judge checks this in seconds.

Honest fix has a cost worth accepting: the real pools are verifiably **empty** (live
`getEvents` from their deployment ledger 3899359 → `"events":[]` for both `CCBOHPJ2…`
and `CCG3ICXN…`), so the "3,630 events" number collapses if attributed to them. Replace
it with the number we actually measured today, which is *better evidence anyway*:

> Right now the public RPC's floor is ledger 3846966. Raiz Memory answers with **906
> events it can no longer return at any `startLedger`**: 36 for the *official* CT demo
> wrapper (history from 3837609) and 870 for the EURC SAC Nethermind's SPP pool settles
> against (from 3820211). The authors of both primitives documented this limitation.
> This is it, measured, on their own contracts.

Also drop the unsupported "in Session 2 it archived 3,630 real events" (README:39 — the
number exists only in `CLAUDE.md:201`, nowhere in the repo). Add the real pool id to
`CONTRACT_IDS` if you want, but say plainly that it is empty on testnet today.

### B3 — README:215: "imports no RAÍZ code yet" — the originality section is false · **20 min**

Thirteen files under `wallet/app/src/main/java/xyz/raiz/sobre/ui/` are RAIZ-derived and
say so themselves:

```
ui/theme/Color.kt:6-8  "RAIZ palette, adopted verbatim (names and hex values unchanged)
                        from the parent RAIZ Android app — com.raiz.app.ui.theme.Color"
ui/theme/Type.kt:10    "RAIZ typography, adopted verbatim."
```

plus `Theme.kt`, `StatBox.kt`, `SobreCard.kt`, `AporteRow.kt`, `PhaseBanner.kt`,
`VerifyRow.kt`, `StepFeedback.kt`, `ProofProgress.kt`, `GoalProgressBar.kt`,
`StellarExpert.kt`, `SobreApp.kt`. Copy list with per-file provenance:
`docs/raiz-reuse-plan.md:39-65`.

The reuse is entirely legitimate — it is the team's own declared prior work, and the
in-file attributions are exemplary. **Only the README sentence is the liability**, and
it fails in the worst direction: a judge auditing originality opens a theme file, reads
"adopted verbatim", then reads §7 saying "imports no RAÍZ code yet", and now doubts
everything else on the page.

**Action:** replace with the truth, framed as the asset it is —
*"the CT layer, key custody, prover bridge, signing and indexer are new for this bounty;
the visual layer (13 files: `ui/theme/`, 9 components in `ui/components/`,
`ui/util/StellarExpert.kt`, `ui/nav/SobreApp.kt`, ~430 lines) is adopted from RAIZ, our
own pre-existing app. Every file names its origin, its source line range and its named
edits in-header; the full copy list is `docs/raiz-reuse-plan.md` §2."*

### B4 — README:235: we disclaim a feature we shipped · **10 min**

```
README.md:235  - **Raiz Memory has no backfill yet**: an ingestor's first run starts at
                 the current ledger…
```

Contradicted by `raiz-memory/src/backfill.rs` (four dedicated tests, module doc opens
*"A fresh index that starts at the chain head proves nothing"*), commit `1073dff`,
`raiz-memory/README.md:39-70`, and `raiz-memory/.env.example:13-33`. This is the most
self-damaging line in the file: it undersells the strongest indexer feature in the
section judges read most carefully.

**Action:** replace with the *real* limitation, which is still honest and still good:
history is bounded by the RPC's retention floor at first run; a start ledger below it is
clamped, and `/coverage` reports exactly how many ledgers were already unreachable —
verified on a fresh index as
`"requested start ledger 3013364 predates this RPC's retention floor 3846730; 833366 ledgers of history were already unreachable when indexing began"`.

### B5 — README:203: we tell the judge our primary deliverable is unfinished · **15 min**

```
README.md:203  The app currently ships a working **debug console** … not the final UI.
               `TODO(session-6): replace with the Metas / Aportar / Mi Sobre screens…`
```

False since `576ba76`. `wallet/app/src/main/java/xyz/raiz/sobre/spike/MainActivity.kt`
is now a 55-line Compose host for `SobreApp`; `ui/meta/MetaScreen.kt`,
`ui/sobre/SobreScreen.kt`, `ui/sobre/CosechaScreen.kt` and the Raiz Memory-backed
timeline all exist and build. A judge reading §6 concludes there is no wallet UI and
stops reading — on a *wallet* bounty.

**Action:** describe the three screens, drop the TODO, and link 3–4 screenshots (see S5).

### B6 — No LICENSE on a public repo that already claims MIT twice · **10 min**

```
$ ls LICENSE* NOTICE* COPYING*   →  No such file or directory
$ gh repo view … --json licenseInfo  →  {"licenseInfo":null}
raiz-memory/Cargo.toml:6         license = "MIT"
contracts/goal-meta/Cargo.toml:6 license = "MIT"
```

No LICENSE means all rights reserved. The bounty asks for *"a durable event index other
builders can point a wallet at"* — as published, no other builder legally may. The repo
also contradicts its own manifests.

**Action:** add `LICENSE` (MIT, `Copyright (c) 2026 Raiz Protocol`) at root. While there,
add `NOTICE.md` for the redistributed bundle in `scripts/prover-bench/dist/bench.js`
(230.9 KB esbuild output; its sourcemap names `@noir-lang/*` 1.0.0-beta.9,
`@noble/curves` 1.9.7, `@noble/hashes` 1.8.0, `@zkpassport/poseidon2` 0.6.2, `pako` 2.1.0,
plus the demo's `packages/{sdk,tokens,disclosure}`). All permissive, no copyleft — but
MIT requires the notice to travel with the copy. One sentence each. Worth noting inside
it that `brozorec/stellar-confidential-token-demo` ships no LICENSE file at all, only
`"license": "MIT"` in `package.json`.

### B7 — The four wrong numbers · **15 min**

Free credibility currently being thrown away, on a page whose L8 says *"every timing is real"*:

| Line | Says | Truth | Check |
|---|---|---|---|
| README:178 | `# 4 tests, offline` | **15 tests** | `cargo test` in `raiz-memory`; `raiz-memory/README.md:184` already says 15 |
| README:242 | friction-report has "13 entries" | **15** | `grep -c '^## ' friction-report.md` → 16, incl. the template header |
| README:197 | prover assets "~13 MB" | **16.4 MB** | `node wallet/tools/build-prover-assets.mjs` → `16760 KB TOTAL` |
| README:128 | "Goal total: 50 XLM … at ledger 3952632" | **63 XLM at ledger 3967943** | run live today, output in §5 below |

The 50 XLM figure is also internally inconsistent: README §4 L71 claims a 5 XLM on-device
contribution to the goal, and 25+25+5 = 55 ≠ 50. Paste the fresh run and add one line:
*"the total grows as contributions land; your run will show more."*

Also re-run the §2 retention-error snippet — it quotes a stale range (`3832437 - 3953396`);
today's is `3846966 - 3967925`. It reads as un-rerun on the one block that is our thesis.

### B8 — Record the demo video · **3–4 h including device setup**

The brief lists the video as optional and only the Repository link as required — but
this is a *wallet* submission with no APK release and a long build chain
(vendor clone → `pnpm install` → `build:sdk` → `build-prover-assets.mjs` → `ANDROID_HOME`).
Realistically **no judge will build it**. The video is how the wallet gets seen at all.
It is blocking in the practical sense, not the rules sense.

Prerequisite, per `docs/demo-run.md` SETUP (§108): the phone was wiped by a clean
reinstall, so it needs `register` → `deposit` → `merge` again before the camera rolls.
Budget 30–45 min for that (register alone is 10–30 s of proving plus onboarding, and
testnet is flaky in bursts — gotcha #3).

Script is rehearsed and timed at 150 s (`docs/demo-run.md:94`). Record it the day it
works; never demo live.

### B9 — The three `TODO(session-N)` placeholders · **10 min, after B8**

`README.md:244, 248, 249`. L248 is the video link, L249 is the public Raiz Memory URL.
L249 is *already* obsolete — `scripts/serve-public.ps1` and `docs/deploy-public.md` exist.
Internal session numbers mean nothing to a judge and an open TODO reads as unfinished
regardless of what shipped. L244 (issue drafts) should become plain prose: the drafts are
a deliverable as drafts; say so and stop promising to file them.

---

## 3. SHOULD-DO

Ordered by value per minute. Everything here is a real gain; none of it is blocking.

### S1 — Commit `RECEIPT_VERIFIER_SECRET_HEX` · **10 min** · highest value/minute in the list
README §5 L139-142 shows two commands. Both fail 100% of the time on a fresh clone:

```
$ node scripts/receipt/verify-receipt.mjs
verify-receipt failed: RECEIPT_VERIFIER_SECRET_HEX missing from .env.deploy — only the requesting verifier can check (and decrypt) a receipt
```

`receipt.json` **is** committed (31 KB) but is cryptographically dead, and the docs are
circular: `scripts/receipt/README.md:50` says the secret is auto-generated on first
`make-receipt` run, which needs Marta's secret, which is also absent. The judge cannot
run the tamper test or check any of the seven `✔` lines.

The fix is safe and one line. `make-receipt.mjs:70-75` shows the value is a throwaway
verifier keypair generated for this demo; its only power is decrypting a receipt whose
amount (25 XLM) the README already prints. It is not a Stellar seed and holds no funds.
Commit it next to the receipt, or — if that feels wrong — say plainly in §5 that this one
is a recorded artifact rather than a runnable check. Silence is the only bad option.

### S2 — Attach `app-debug.apk` as a GitHub release asset · **15 min**
`wallet/app/build/outputs/apk/debug/app-debug.apk` already exists (26.5 MB, built today);
`gh release list` shows none. This removes the entire build chain for a curious judge and
is the only realistic path to anyone touching the wallet. Pair it with a one-line caveat
about `adb reverse tcp:8091 tcp:8091`, or better, do S3 first.

### S3 — Ship the tunnel URL as the app's default event source · **30 min**
`wallet/…/data/EventSourceStore.kt:49` hardcodes `DEFAULT_BASE_URL = "http://localhost:8091"`
with the comment *"port 8091, not the repo default 8090, which is taken on this machine"* —
a dev-machine detail in shipped code, and every other doc says 8090. An installed APK
therefore shows an empty timeline. Make the default the published Raiz Memory URL, keep
`localhost` as a preset, and make the failure state say so in Spanish instead of rendering
an empty list. This is what makes S2 worth doing.

### S4 — Fix the fresh-clone potholes in README §6 · **25 min**
Four concrete stumbles a judge hits in order:
- `ANDROID_HOME` / `local.properties` are never mentioned; `./gradlew :app:assembleDebug`
  fails with `SDK location not found…`. With it set: `BUILD SUCCESSFUL in 38 s`.
- `build-prover-assets.mjs` requires the `/vendor` clone from §5; §6 never says so, and
  without it dies on a raw stack trace: `Error: ENOENT: no such file or directory, lstat '…\vendor'`
  (`wallet/tools/build-prover-assets.mjs:45`).
- `raiz-memory` logs **nothing, ever** — `src/main.rs:49` is `tracing_subscriber::fmt::init()`,
  which defaults to ERROR without `RUST_LOG`. The entire clamp narrative
  (`raiz-memory/README.md:84-87`, "log exactly what was asked for") is invisible, in
  `docker logs` too. Add `RUST_LOG=info` to the quickstart.
- `bash ../../scripts/goal-flow.sh` fails with
  `GOAL_META_CONTRACT_ID: missing — deploy first and record it in .env.deploy`, but
  README:192 lists `.env.deploy` as needed only by "`receipt` and `ct-flow`", so the
  README affirmatively implies goal-flow runs. Add it to the list.

Also correct the setup-time claim: README:99 and `scripts/verify-goal-total/README.md:48`
say **"≈ 15 minutes, most of it one `pnpm install`"**; measured end to end from a clean
clone with an empty pnpm store it is **42 seconds**. Wrong in the safe direction, still wrong.

### S5 — Embed 3–4 screenshots in the README · **20 min**
`grep '!\[' README.md` → **zero images**, on a mobile wallet submission, while
`docs/spike-evidence/` holds 52 PNGs of which 37 are referenced from no markdown at all.
Embed `ui-08-mi-sobre.png`, `ui-04-timeline-rows.png`,
`demo-08b-stellar-expert-sin-monto.png` and `goal-total-ondevice.png` in §4, and add
`docs/spike-evidence/README.md` mapping each file to what it proves. Cheapest remaining
credibility win.

### S6 — Uncomment `RETENTION_SIMULATION_LEDGERS` in `.env.example` · **5 min**
`raiz-memory/.env.example:43` ships it commented out, so a judge following the quickstart
gets **identical results** with and without `&source=rpc-simulation` (verified: 100 events
both ways, `oldestLedger: None`), and the wallet's "RPC (simulado)" toggle silently does
nothing — the central scene of the video, dead out of the box. Three docs also disagree on
the value: README says 120, `raiz-memory/README.md:153` says 120,
`docs/demo-run.md:26,119` says "already in `raiz-memory/.env`: 2000" — a gitignored file
no judge has. Pick one number, ship it uncommented, make all three agree.

### S7 — Rebuild the public instance's index from scratch · **20 min (mostly waiting)**
Live `GET /coverage` on the running instance returns **no `backfill` object for any of the
four contracts** — the db predates `backfill_marks`, so the honest-clamp reporting we
brag about in §8 is invisible to anyone hitting the instance we publish. A fresh index
takes ~60 s to head and prints the quote unprompted. Do this before the video and before
publishing the URL, so the thing the README claims is the thing the judge sees.

### S8 — Trim the internal strategy language · **20 min**
Tracked, public, and reads badly to the exact people reading it:
- `docs/propuesta_D_indexador_respaldo.md:46` — *"La demo teatral (esto gana o pierde el premio)"*
- `CLAUDE.md:170` — *"Ante estos jueces, la fricción documentada SUMA puntos"* ("with these judges, documented friction SCORES POINTS") — this reframes `friction-report.md`, one of our best assets, as gaming
- `.claude/skills/friccion/SKILL.md:3` — same sentence
- `docs/evaluacion_bounty_privacy_stellar_summit.md:95,112` — prize-probability table
- `CLAUDE.md:259` — *"el agente quitó 13 afirmaciones que el código NO sostenía"*: hands a hostile judge the sentence "our README used to contain 13 unsupported claims"

Move `propuesta_A`, `propuesta_D`, `PLAN_SINERGIA.md`, `evaluacion_bounty_*` and
`PROMPTS_CLAUDE_CODE.md` to a private `planning/` folder or a separate branch, and fix the
two skill/CLAUDE.md sentences. Decide `CLAUDE.md` and `.claude/` together — the
`vendor-scout` rule ("if a symbol isn't in /vendor, it doesn't exist") is genuinely good
methodology for an infrastructure bounty, and every skill references CLAUDE.md rules by
number, so removing one orphans the other.

### S9 — One line about AI assistance in §7 · **5 min**
All 21 commits carry `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`; the README
says nothing. The trailers are honest and must **not** be stripped — that would be the
real integrity problem. The risk is purely that a judge discovers it in `git log` rather
than reading it from us. Note also that "Claude Fable 5" is an internal codename that
resolves to nothing publicly. One sentence: *"Built with AI assistance (Claude Code);
every commit records it. All design, architecture, deployment and verification are the
team's own."*

### S10 — Mark the four fixed rows in `BACKLOG.md` · **10 min**
`BACKLOG.md:11-14` still lists the stale hero total, the off-screen tx hash, the keyboard
covering `Sellar`/`Aportar`, and the `events-url` string leak — **all four fixed in
`8a12831`** (`MetaViewModel.kt:152` `autoVerify: Boolean = true`; `SobreScreen.kt:341`
`imeAction = ImeAction.Done`). A judge reads BACKLOG as the current defect list and
concludes the app is worse than it is. Mark them `FIXED (8a12831)`.

### S11 — Three small precision fixes · **20 min total**
- **§1 L16** ("Stated precisely") omits the custodian: auditor id 0 also opens the sender
  channel, which §8 L230 admits ("in this demo it is us"). §1 must not contradict §8 on
  the privacy promise — that is the anchor sentence.
- **§3 L58** — *"no such parameter anywhere in the ABI"* is refuted by
  `contracts/goal-meta/src/lib.rs:89 target: i128`. The source comment is correctly
  scoped ("never sees an amount **of any contribution**"); the README dropped the scope.
  Restore it.
- **§9 L26** — the Nethermind quote is byte-exact and at their README L121, but it is
  truncated mid-bullet with no ellipsis. Add one.

### S12 — Scrub dev-machine paths at HEAD · **15 min**
`docs/raiz-reuse-plan.md:4,5,70,130,132` (L70 exposes the OS username, L4/130 the
filesystem location of a separate private repo), `docs/spike-findings.md:179-220` (LAN IP
`192.168.0.104`, ×6), `wallet/README.md:24,34`, `scripts/prover-bench/README.md:29`,
`docs/demo-run.md:113,116,127`, `scripts/ct-flow.md:13,192`. Replace with `<RAIZ-repo>/…`,
`<LAN-IP>` and repo-relative paths. Judge-facing instructions containing `C:\SP_WorkShop`
simply do not run for anyone else.

### S13 — `scripts/serve-public.ps1:20` hardcodes cloudflared · **5 min**
`$cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'` fails for everyone
else. Fall back to `Get-Command cloudflared` and error with an install hint.

---

## 4. WON'T-DO

Conscious drops. Each is defensible if a judge asks.

| Dropped | Why it is the right call |
|---|---|
| **Filing the 10 upstream issue drafts** | Requires Juancho's explicit per-issue OK, one by one (CLAUDE.md rule). `docs/issues-drafts/` already demonstrates the work in full, and README §9 correctly frames them as unfiled drafts. Filing 10 issues against three orgs in the last 40 h risks a rushed, wrong bug report against the very judges reading it. Shipping the drafts requires no OK; **do that, file nothing.** |
| **Making Raiz Memory speak Soroban JSON-RPC** | This is the "correct" fix for B1 and it is a rewrite: `simulateTransaction`, `getLedgerEntries`, `getLatestLedger`, XDR encoding, error codes. Days, not hours, two days out. The honest sentence costs 30 minutes and is *true*. The optional `--events-url` flag (B1, +90 min) captures most of the demo value at 2% of the cost — and only after the video. |
| **`withdraw` in the app, a second goal, general P2P send, goal creation UI** | Money flows in and never out, and `MetaConfig.GOAL_ID = 1` is hardcoded. Real gaps — and correctly scoped as MUST-not per CLAUDE.md's golden rule. Adding an unrehearsed CT operation two days out risks the four working operations. Name them in §8 as limitations instead; an honest limitation costs nothing, a broken `withdraw` on camera costs everything. |
| **History rewrite to remove `C:\Users\juanp` from 8 commits** | The repo is already public and pushed. The exposure is an OS username and the path of a private repo — low severity. A rewrite two days out risks the remote, and the commit history is an **asset**: 21 commits, English, imperative, with measured evidence in the bodies (`8a12831`: *"proof 15.5 s, ledger 3966778… the goal total went 61 -> 63 XLM"*). Fix at HEAD (S12), leave history alone. |
| **Deleting `scripts/prover-bench/dist/` from git** | 904 KB of committed build output the repo's own `README.md:112` calls optional. But `.git` is only 8 MB, the blobs are already public, and `NOTICE.md` (B6) discharges the MIT obligation for a fraction of the effort. Not worth touching the tree. |
| **A stable public Raiz Memory host** | No VM available; `scripts/serve-public.ps1` brings up a cloudflared quick tunnel whose URL changes on restart. Buying and provisioning a host now is a new failure mode on the critical path. Ship the tunnel, say in §10 exactly what it is and that the URL rotates, and make the README's local quickstart the durable path. Honesty here costs nothing — the *index* is the deliverable, not our uptime. |
| **Translating the shim's English check labels on the Spanish consumer screen** | `raiz-shim.js:756,889,895,903` render `[OK] commit(Σ spendable openings) == on-chain spendable commitment` to a neighbor. Genuinely a "demo, not product" tell. But it is cosmetic next to a false originality section, and every touch of the shim risks the proving path that took a whole session to stabilize. Leave it; the video can frame it as the verification panel it is. |
| **Fixing the port-8090 bind error message** | `src/main.rs:95` binds without context, so a busy port yields `os error 10048` in the OS locale with no mention of the port or the `PORT` var. Real pothole, but one judge in ten hits it, and `PORT` is documented. Below the line. |
| **Renaming `wallet/docs-integration/ProverWebViewBridge.kt`** | A dead 3.8 KB duplicate of the real 24 KB bridge, deliberately kept (cited by `friction-report.md:96`) and already headed `⚠️ SUPERSEDED`. Two identically-named Kotlin files is a mild trap, but the warning header does the job. |

---

## 5. Submission-day runbook — morning of 6 August

Target: **send before 12:00**. Everything below B1–B9 and the video must already be done
on **5 August**. The 6th is verification and submission only — no new work.

### T−3 h · Freeze and verify the repo
1. `git status` → clean. Commit or `.gitignore` the untracked `web/` landing page; do not
   leave an unexplained untracked directory.
2. `git push origin main` → then `git rev-list --left-right --count origin/main...main`
   must print `0  0`.
3. `gh repo view JuanWimmin/raiz-confidential-stack --json visibility,licenseInfo`
   → must show `"PUBLIC"` and a **non-null** `licenseInfo`. If `licenseInfo` is still
   null, B6 was not done — stop and do it.
4. Open the repo in a logged-out browser. Confirm the README renders, images load, and no
   `TODO(session-` string survives: `grep -n "TODO(session" README.md` → **no output**.

### T−2 h · Re-run the three claims a judge will run first
5. **The verifier** — this is the strongest thing we have and it must be fresh:
   ```
   node scripts/verify-goal-total/verify-goal-total.mjs
   ```
   Expect `Goal total: NN XLM — verified on-chain at ledger …` with all three stages OK.
   **Paste today's output into README §5.** The number will have moved again; that is the
   point, and the README must say so.
6. **The retention error** — re-run the §2 `getEvents` call with `startLedger: 3013364`
   and paste the *current* ledger range into the README. A stale range on our thesis block
   is the single most checkable staleness a judge can catch.
7. **Coverage** — `curl <public-url>/coverage`. Confirm the `backfill` object is present
   (S7) and that `historyBeginsAtLedger` is below the live RPC floor for
   `CBF64DEO…` and `CCUUDM434…`. Recompute the "events the RPC can no longer return"
   figure and make sure README §2 matches it.

### T−90 min · The two artifacts
8. Bring up the public instance: `scripts/serve-public.ps1`. Capture the tunnel URL,
   paste it into README §10, and **hit `/health` and `/events` from a phone on mobile data**
   (not the LAN) to prove it is really public. Commit and push.
9. Confirm the video is uploaded, **unlisted or public**, and plays from a logged-out
   browser. Paste the link into README §10. Push.
10. Confirm the APK release asset downloads from a logged-out browser (S2), and that its
    default event source points at the tunnel, not `localhost` (S3).

### T−45 min · The hostile pass
11. `grep -rn "TODO\|FIXME\|XXX" README.md docs/*.md` → nothing judge-facing.
12. `grep -rn "C:\\\\SP_WorkShop\|C:\\\\Blockota\|juanp" README.md wallet/README.md scripts/*/README.md`
    → nothing (S12).
13. `git grep -E "\bS[A-Z2-7]{55}\b"` → **must be empty**. This has been verified clean
    across all 21 commits; verify once more after any last-minute commit.
14. Read README §1, §7 and §8 out loud, in that order, as a judge auditing originality
    would. §1 must not contradict §8 on who can read what; §7 must name the RAIZ reuse.

### T−20 min · Send
15. Submit the form with the **Repository link**
    `https://github.com/JuanWimmin/raiz-confidential-stack`, the video link, and the
    public Raiz Memory URL. One submission per sub-lane; it is editable, so **send early
    and refine after** rather than polishing past noon.
16. After sending, leave the tunnel and laptop up. If it dies, the README's local
    quickstart is the fallback and says so — but do not be the reason it dies.

### If something breaks on the morning
- **Testnet flaky in bursts** (gotcha #3): every script has retries; re-run before
  debugging. Never re-record; the video is already the recording of the day it worked.
- **Tunnel down:** delete the URL from §10 rather than shipping a dead link, and say the
  instance is run locally per §6. A missing URL is a limitation; a 404 on our own
  flagship link is a disproved claim.
- **Anything else:** ship the repo link on time. It is the only required deliverable, and
  the submission is editable.
