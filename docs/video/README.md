# Screen captures for the demo video

Recorded on the real device (Vivo Y21, Android 13) with `adb shell screenrecord`,
driven by scripted taps so the timings are repeatable. **Video only — no audio.**
The narration for each beat is in [`../demo-run.md`](../demo-run.md); record it
over the top.

These files are gitignored: the final cut belongs on YouTube, not in the repo.

## `demo-completo.mp4` — 3:44, the whole run

Everything below is real: a real contribution on testnet, a real ZK proof
computed on the phone, real ledger numbers. Nothing is mocked and nothing is
sped up.

| From | Beat | What is on screen |
|---|---|---|
| 0:00 | The fund | "Techo de la casa comunal", **65 XLM**, verified against on-chain commitments |
| 0:09 | Verified live | `Verificar de nuevo` runs the five checks again on camera (4.1 s, then 3.5 s) |
| 0:28 | Who and when | The contribution timeline: address and time, a padlock and `•••` where an amount would be |
| 0:36 | Mi Sobre | **8 XLM** decrypted on the device |
| 0:51 | The tap | 2 XLM typed, `Aportar` pressed |
| 0:51 | **The proof, uncut** | The counter runs 0 s → 22.6 s while the phone computes the ZK proof. This is the beat that must never be cut: it is the evidence |
| 1:24 | Confirmed | Ledger 3970848, the tx hash, the fee. Balance drops 8 → 6 XLM |
| 1:36 | Honest, not stale | The old total greys out — *"Desactualizado: esto se verificó en el ledger 3970848, antes de lo que ves abajo. Recalculando…"* — then re-verifies to **67 XLM**. The app will not show a number it cannot currently prove |
| 2:00 | The RPC that forgets | Source switched to *RPC (simulado)*: 9 aportes collapse to 2, and the screen explains why |
| 2:52 | It refuses | The hero goes to **sin verificar** with two `[FALLA]` lines: the sum of what this source returns does not reproduce the on-chain commitment |
| 3:05 | Raiz Memory remembers | Switch back, and **67 XLM** verified returns at ledger 3970915 |

## Cutting it to 2:30

The raw run is 3:44, so about 75 seconds have to go. In order of what to cut
first:

1. The second `Verificar de nuevo` pass (0:18–0:28) — one is enough.
2. Dead holds at 2:10–2:50 while the RPC-simulado screen sits still.
3. The tail after 3:25.

**Do not cut:** the proof counter (0:51–1:24), the stale-to-verified transition
(1:36–2:00), or the switch and its return (2:00 and 3:05). Those three are the
argument.

## Re-recording

Preconditions and the exact command are in [`../demo-run.md`](../demo-run.md).
Two things that cost us a take:

- **Set Do Not Disturb to total silence** (`settings put global zen_mode 2`)
  and disable heads-up banners. A notification banner appeared in the first
  attempt and a chat opened over the app in the middle of another.
- **`screenrecord` records whatever is on screen.** The first attempt captured
  private conversations after the phone was picked up mid-take; that file was
  deleted rather than trimmed and kept. Check the tail of every capture before
  using it.
