# Landing — Sobre del Barrio

One self-contained `index.html`. No build step, no dependencies, no framework —
the same shape as the existing RAÍZ landing, so it deploys the same way. The only
network requests it makes are for the three Google fonts, and those are loaded
non-blocking: if the venue's wifi hangs, the page still renders.

It is ~341 KB, of which ~243 KB is five images embedded as base64 `data:` URIs:
four real phone screenshots and the demo video's poster frame. That was a
deliberate trade: the product images are the strongest evidence on the page, and
inlining them keeps the "one file, zero external assets" property that makes this
deployable anywhere in one copy — and, for the video poster specifically, keeps
the page from calling `i.ytimg.com` and announcing the visitor to Google before
they have chosen to press play.

## The rule that governs the design

**The page has two layers, and only one of them moves.**

1. **What is public reads perfectly.** Full contrast, the RAÍZ type trio, zero
   motion over running text. A judge in a hurry must be able to read all of it
   without waiting for anything to finish animating.
2. **What is sealed looks sealed, and behaves like it.** The contribution amounts
   are redaction bars. Hover one and it does not decrypt — it scrambles harder,
   and the tooltip says so. That inversion is not a gag: it is the product.

And exactly once, in §07, a padlock opens, the total counts up to 67 XLM and the
five verification checks print underneath. That is the whole system in one
gesture, which is why it is the only moment on the page with a payoff.

Three performance rules fall out of that, and an earlier draft of this page got
all three wrong — which is why they are written down here rather than left to
taste:

- No `requestAnimationFrame` loop runs off-screen or with the tab hidden —
  everything is gated by `IntersectionObserver` plus a `visibilitychange` check.
- At rest the sealed amounts cost **zero**: they are static bars. They only
  scramble while the pointer or keyboard focus is on them.
- `prefers-reduced-motion` turns off absolutely everything and hides not one word
  of content.

## What each effect is for

Nothing here is decoration looking for a home. Every effect argues for the
product:

| Effect | Where | What it argues |
|---|---|---|
| Glitch | the rows tearing away when you switch to the forgetful RPC | data loss should look like data loss |
| Padlocks | §02 — two rattle shut, one opens | CT's privacy model without a word of jargon |
| Animated background | a field of commitments merging two-into-one | that is the Pedersen operation, running quietly |
| Colour transitions | `--acento` per section: green → purple → amber → red | navigation you feel rather than read |
| RAÍZ roots | growing down the left margin | the brand signature, doing a job: it **is** the scroll meter |

The scramble alphabet is ASCII on purpose. Space Mono is monospaced, so as long
as the glyphs stay inside its repertoire and the character count is preserved,
the pill is pixel-identical scrambled or not. The earlier draft used `∆◊§¶`,
fell back to another font, and every row shivered.

## Content

Eleven sections, and the substance is the point: the real captured RPC error;
Nethermind's own retention quote; `deposit` (public) versus
`confidential_transfer` (no amount field exists); the live event-source switch;
the two pieces and how Raiz Memory differs from Nethermind's `tools/bootnode`;
the 4/4 on-device transactions with clickable hashes; the Android WebView
single-threading finding; four real phone screenshots; `verify-goal-total`; the
selective-disclosure receipt; the contract table; the five issues filed upstream;
and nine limitations stated before a judge can find them.

Every number, contract id, transaction hash and terminal block is copied from
something that actually ran. Chain-dependent figures carry the ledger and the
date they were measured, because they move — that is the same discipline the
root `README.md` applies, and the reason the Meta screenshot's caption says out
loud that it shows 63 XLM at ledger 3966806 while the page total is 67.

## The demo video

The video block is a click-to-load facade: YouTube is not contacted, and sets no
cookies, until a visitor asks for it. The id lives in one constant near the
bottom of the file.

```js
const YT_ID = 'jjCPrNCHZhQ';   // https://youtu.be/jjCPrNCHZhQ
```

Published as the **complete 3:44 run**, not the 2:30 cut — verified against the
video's own metadata (`lengthSeconds: 224`, `isUnlisted: false`). The page says
so, and says that nothing was trimmed or sped up, because with the uncut run
that claim is literally true. If the video is ever replaced by a shorter cut,
the duration appears in exactly two places: the `<h2>` and the `<b>` label of
the video card.

The poster frame is inlined next to the play button — without it the facade is a
black rectangle that reads as a broken embed. It happens to be a frame of *Mi
sobre* showing the contribution confirmed on chain at ledger 3970848 with its
22,599 ms proof, so the still is evidence too. To replace it after a re-upload:
grab `https://i.ytimg.com/vi/<id>/maxresdefault.jpg`, scale it to 800×450, and
swap the `data:` URI on the `.video__poster` image.

Clearing `YT_ID` turns the block back into an honest placeholder rather than a
broken embed.

## Where it is deployed

**Live at <https://raizapp.xyz/sobre.html>.**

It is served from the team's existing GitHub Pages site, the repo
`JuanWimmin/JuanWimmin.github.io`, where `index.html` (the RAÍZ landing),
`pitch.html` and `verificar-residente.html` already live.

**To update it:** edit `index.html` here, copy it over `sobre.html` there, keep
`og:url` pointing at `https://raizapp.xyz/sobre.html`, and push. GitHub Pages
picks it up in well under a minute. There is no build step on either side, and
no asset folder to remember — the screenshots travel inside the file.

The page is standalone, so any static host would do equally well; this one was
chosen because it lives under the team's own domain and outlives the machine
this was built on.

## Local preview

```bash
node web/serve.mjs      # http://localhost:4180
```

## Checked before shipping

Rendered headless at 320, 360, 390, 414, 768 and 1400 px and audited for
horizontal overflow at each — `documentElement.scrollWidth` equals the viewport
at every one. Note that Chrome's headless window clamps to a 512 px minimum, so
a `--window-size=390` screenshot is a 512 px layout cropped to 390 and will lie
to you about clipping; the real narrow-viewport checks were run inside a
fixed-width `<iframe>`, which does honour its width.

Every identifier on the page is also cross-checked against the repository by
script rather than by eye — four contract ids, two accounts, five transaction
hashes and the view key, plus the nine abbreviated forms (`CBNVY2AA…IQAZ`)
against the id each one actually links to.

The page was then put through an adversarial audit — fourteen agents across
seven dimensions (figures, identifiers, links, accessibility, performance, code,
copy), each finding independently re-verified by a second agent that had to
reproduce the defect itself before it counted. Sixty-five findings survived that
filter. The most valuable one could not have been caught by reading: an agent
queried Raiz Memory's own SQLite index, decoded the event topics back to
strkeys, and produced the goal's real contribution history — nine transfers at
ledgers 3950172, 3950262, 3953087, 3960072, 3960225, 3965868, 3966778, 3970209
and 3970848. The page had been listing a contribution at ledger 3970912 that
does not exist in any source, and was three contributions short. The event list
in §04 is now that exact history: 25 + 25 + 5 + 2×6 = 67 XLM, of which exactly
two survive the simulated retention floor — which is what the video shows.

Four more defects came out of the same audit and are worth stating, because they
are the kind that pass a visual review:

- **The whole page was invisible without JavaScript.** `.rv { opacity: 0 }` is
  undone only by the IntersectionObserver at the bottom of the file, so a script
  error, an extension, or a truncated download left a judge looking at an empty
  document. The rule is now `:where(.js) .rv`, with a one-line script in `<head>`
  setting that class — `:where()` keeps specificity identical, so `.rv.in` and
  the reduced-motion rule still win. Verified by rendering the page with both
  `<script>` blocks stripped: it renders in full.
- **`--texto-3` failed WCAG on every one of its eighteen uses.** `#606B63`
  measures 3.42:1 on `--tinta-2` and 3.26:1 on `--panel`, and none of its uses
  qualify as large text (0.62rem to 0.9rem). It is now `#7C8880`, 5.14:1.
- **The event-source switch leaked timers.** Pressing RPC and returning to Raiz
  Memory inside the ~1.1 s animation left live `setTimeout`s that re-tore rows
  already restored — the project's centrepiece scene, broken by a double click.
  Timers are now tracked and cleared, and each button is idempotent.
- **Three claims overstated what the project can prove.** "The total can be
  proven without revealing a single contribution" contradicts §10 and the root
  README, since the published key opens the fund itemized; "0 amounts readable
  on chain" contradicts `deposit` being public; and the RPC button advertised a
  7-day retention window when what it reproduces is the compressed simulation
  (`RETENTION_SIMULATION_LEDGERS=2000`, ~2 h 45 min — a real 7-day window would
  lose nothing, because the goal is only 29 hours old). All three now say what
  is true, and the compressed window is stated on the page rather than implied.

And four defects were found before the audit, by rendering:

- **The counter could print a negative total.** The count-up did not clamp its
  progress at the low end, and a rAF timestamp on a different time origin drove
  it below zero. Caught as a literal `-3 XLM` in a screenshot.
- **The §07 padlock never opened.** The rule that rotates the shackle was scoped
  to `.cand--abre`, which the §07 lock is not.
- **59 px of horizontal overflow on a phone.** `.sello::after` is an absolutely
  positioned `white-space: nowrap` tooltip: it contributed 108 px to the
  `scrollWidth` of a 42 px pill *while invisible at `opacity: 0`*, and that
  excess propagated up through `.fila` → `.tarjeta` → `.wrap` → `<main>` until
  the goal card was clipped off the right edge of the screen. It is now
  `display: none` until hover, with the fade preserved via `@starting-style` and
  `transition-behavior: allow-discrete` where supported.
- **Transaction hashes broke one character per line on a phone.** The table
  columns were being crushed instead of letting the table scroll, so
  `e7f9309a…` came out stacked vertically — which reads as a broken page, not a
  narrow one. Tables inside `.scroll-x` now carry a `min-width` and links never
  wrap; `word-break: break-all` is left only for the full 56-character ids that
  genuinely have to break.

That last one is a general trap, not a one-off. The previous version of this
page had exactly the same shape in its own redaction tooltip and measured
`scrollWidth` 108 against `clientWidth` 56 at 390 px; it escaped only because
its tooltip text was a few words shorter, which is a narrow margin rather than
a guarantee. If you ever add an absolutely positioned `nowrap` tooltip to a
narrow inline element, measure `document.documentElement.scrollWidth` against
the viewport before shipping it.

Terminal blocks are the tight spot on narrow screens, so their line lengths are
hand-set rather than left to wrap, and the monospace shrinks below 560 px before
anything is allowed to be cut off.
