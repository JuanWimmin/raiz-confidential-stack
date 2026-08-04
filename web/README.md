# Landing — Sobre del Barrio

One self-contained `index.html`. No build step, no dependencies, no framework:
the same shape as the existing RAÍZ landing, so it deploys the same way.

## Design notes

It inherits the RAÍZ visual identity rather than inventing a new one — the
palette is unchanged (`#FBBF24` yellow, `#0F6E56` / `#1D9E75` green, plus the
`#534AB7` purple in reserve), the fonts are the same trio (Bricolage Grotesque
for display, Instrument Sans for text, Space Mono for on-chain data), and the
roots still grow down the left margin with their yellow nodes.

What changes is the register: near-black backgrounds with a hint of green
instead of cream, and the terminal as the dominant voice.

The central idea is that **the aesthetic is the product**. This is a wallet that
redacts amounts and proves totals, so the page redacts amounts (`.tachado`, with
a tooltip saying the amount is encrypted, never revealing it on hover — that is
the joke and the point) and prints its proofs as real console output. Every
number, contract id, transaction hash and terminal block on the page is copied
from something that actually ran; the RPC error in section 01 is a real response
captured on 2026-08-04.

The one warm, living thing in an otherwise cold security aesthetic is the pair of
RAÍZ colours. That contrast is the pitch: hard cryptography in service of a
neighbourhood.

## Publishing the demo video

The video block is a click-to-load facade — YouTube is not contacted (and sets no
cookies) until a visitor asks for it. Without a video id it renders as an
intentional placeholder rather than a broken embed.

To publish: set the id near the bottom of the file.

```js
const YT_ID = 'xxxxxxxxxxx';   // just the id, not the full URL
```

## Deploying

Copy `index.html` wherever the RAÍZ site is served from. It is standalone, so
any of these work:

- drop it next to the current landing (e.g. `raizapp.xyz/sobre/index.html`)
- serve the folder from any static host
- GitHub Pages from this repo

Update `og:url` in the `<head>` to the final address so link previews resolve.

## Local preview

```bash
node web/serve.mjs      # http://localhost:4180
```

Checked on a real phone (720×1600, Chrome Android) as well as desktop widths;
the terminal blocks are the tight spot on narrow screens, so their line lengths
are hand-set rather than left to wrap.
