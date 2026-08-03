# Runbook — Spike día 0: proving de CT en móvil (timebox 3h)

> Objetivo: responder UNA pregunta — ¿se generan pruebas de Confidential Tokens en un teléfono Android de gama media? Todo lo demás del proyecto es trabajo conocido. No optimizar, no arreglar, solo medir.

## Preparación (15 min)

- [ ] Teléfono Android de gama media real (no emulador, no flagship) con Chrome actualizado.
- [ ] Cuenta de testnet fondeada: `curl "https://friendbot.stellar.org/?addr=G..."` (o Stellar Lab → fund account).
- [ ] Localizar el demo oficial de CT: entrada = blog "Developer Preview: Confidential Tokens on Stellar" (stellar.org, 30-jun-2026) → link al demo interactivo y al repo. Guardar ambos links en este archivo:
  - Demo: `________________`
  - Repo: `________________`

## Fase 1 — Chrome móvil directo (30 min)

- [ ] Abrir el demo en Chrome del teléfono.
- [ ] Intentar el flujo: **register → deposit → transfer** (dos cuentas si el demo lo pide, estilo Alice/Bob).
- [ ] Si el demo exige Freighter (extensión de escritorio) para FIRMAR: anotar si el paso de PROVING es separable de la firma. Lo que medimos es el proving; la firma la haremos nosotros desde Kotlin de todos modos.

| Operación | ¿Completó? | Tiempo de prueba | Notas (RAM, calentamiento, crash) |
|---|---|---|---|
| register (prueba inicial) | | | |
| deposit | | | |
| transfer | | | |

## Fase 2 — Solo si la fase 1 fue ambigua: WebView crudo (60 min)

- [ ] Clonar el repo del demo, `npm install && npm run dev`, servir en la LAN.
- [ ] App Android mínima (o una Activity dentro de RAÍZ) con un WebView apuntando al dev server:
  - `settings.javaScriptEnabled = true` · `settings.domStorageEnabled = true`
  - Si el proving usa WASM multihilo: puede requerir headers COOP/COEP en el dev server (crossOriginIsolated). Anotar si el demo los pide.
- [ ] Disparar una generación de prueba dentro del WebView. Medir igual que la tabla anterior.

## Fase 3 — Registro y decisión (30 min)

**Criterios (de la propuesta A §6):**

- **GO → plan completo A+D:** las 3 pruebas corren en el teléfono, <90s cada una, sin OOM.
- **GO parcial → PWA + D:** corre en Chrome móvil pero el WebView falla (p.ej. por COOP/COEP) → la wallet se entrega como PWA instalable con la misma UI.
- **NO-GO → D solo:** >3 min por prueba, OOM sistemático, o contratos CT de testnet caídos/reseteados → `propuesta_D_indexador_respaldo.md` es la submission. Sin duelo: era la rama prevista.

- [ ] Decisión escrita aquí: `____________` · Hora: `______`
- [ ] TODO lo que falló, con mensajes de error textuales → `friction-report.md` (semilla de los issues a OZ y de la sección del README que los jueces van a leer con más cariño que el pitch).

## Mientras tanto, en paralelo (R3 — no espera al spike)

- [ ] `cd raiz-memory && cp .env.example .env` → poner RPC de testnet y CUALQUIER contract id activo (vale el CT wrapper oficial si ya se conoce, o cualquier contrato con eventos) → `cargo run`.
- [ ] Verificar: `curl localhost:8090/health` y que la tabla `events` crece.
- [ ] Éxito del día 0 para R3: eventos reales de testnet persistidos en SQLite. Nada más.
