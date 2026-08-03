# Runbook — Spike día 0: proving de CT en móvil (timebox 3h)

> Objetivo: responder UNA pregunta — ¿se generan pruebas de Confidential Tokens en un teléfono Android de gama media? Todo lo demás del proyecto es trabajo conocido. No optimizar, no arreglar, solo medir.

## Preparación (15 min)

- [ ] Teléfono Android de gama media real (no emulador, no flagship) con Chrome actualizado.
- [ ] Cuenta de testnet fondeada: `curl "https://friendbot.stellar.org/?addr=G..."` (o Stellar Lab → fund account).
- [x] Localizar el demo oficial de CT: entrada = blog "Developer Preview: Confidential Tokens on Stellar" (stellar.org, 30-jun-2026) → link al demo interactivo y al repo. Guardar ambos links en este archivo:
  - Demo: `https://stellar-confidential-token-demo.billowing-moon-0c6f.workers.dev/`
  - Repo: `https://github.com/brozorec/stellar-confidential-token-demo` (clonado en /vendor @ ac67499)

## Fase 1 — Chrome móvil directo (30 min)

- [ ] Abrir el demo en Chrome del teléfono.
- [ ] Intentar el flujo: **register → deposit → transfer** (dos cuentas si el demo lo pide, estilo Alice/Bob).
- [ ] Si el demo exige Freighter (extensión de escritorio) para FIRMAR: anotar si el paso de PROVING es separable de la firma. Lo que medimos es el proving; la firma la haremos nosotros desde Kotlin de todos modos.

> **Adaptación (3-ago):** Chrome Android no instala extensiones → el flujo del
> demo (firma Freighter) no corre tal cual en el teléfono. Como el proving es
> separable (seam Signer verificado en código), se midió con
> `/scripts/prover-bench` (misma pipeline noir_js + bb.js UltraHonk, sin firma).
> deposit NO lleva prueba ZK (hallazgo de spike-findings) — se mide withdraw.
> Dispositivo: **Vivo V2110 (Y21), Android 13, 4 GB RAM, 8 cores, Chrome/WebView 150.**

| Operación | ¿Completó? | Tiempo de prueba | Notas (RAM, calentamiento, crash) |
|---|---|---|---|
| register (prueba inicial) | ✅ | **2.7 s** warm / 6.4 s cold (8 hilos) · 8.6 s (1 hilo) | sin crash, sin OOM |
| transfer | ✅ | **5.4–6.7 s** (8 hilos) · 15.7 s (1 hilo) | sin crash, sin OOM |
| withdraw | ✅ | **4.6–6.1 s** (8 hilos) · 15.6 s (1 hilo) | sin crash, sin OOM |

Config 8 hilos = `adb reverse` + http://localhost:4173 (secure context + COOP/COEP → crossOriginIsolated).
Config 1 hilo = mismo bench sin COOP/COEP (`NO_COI=1`, puerto 4174) — equivalente honesto de "IP LAN" (la Wi-Fi del teléfono estaba en otra subred y no alcanzaba el PC).

## Fase 2 — Solo si la fase 1 fue ambigua: WebView crudo (60 min)

- [ ] Clonar el repo del demo, `npm install && npm run dev`, servir en la LAN.
- [ ] App Android mínima (o una Activity dentro de RAÍZ) con un WebView apuntando al dev server:
  - `settings.javaScriptEnabled = true` · `settings.domStorageEnabled = true`
  - Si el proving usa WASM multihilo: puede requerir headers COOP/COEP en el dev server (crossOriginIsolated). Anotar si el demo los pide.
- [x] Disparar una generación de prueba dentro del WebView. Medir igual que la tabla anterior.

**Resultados WebView (3-ago, APK spike con DEMO_URL=http://localhost:4173 vía adb reverse):**

| Operación | ¿Completó? | Tiempo | Notas |
|---|---|---|---|
| register | ✅ | **10.8 s** | sin OOM, proof 14,592 B válida |
| transfer | ✅ | **15.7 s** | sin OOM |
| withdraw | ✅ | **14.2 s** | sin OOM |

**Hallazgo de plataforma:** el WebView de Android (Chromium 150) NO expone
SharedArrayBuffer ni crossOriginIsolated aunque el servidor mande COOP/COEP y
localhost sea secure context (el mismo URL en Chrome da `true`) → el proving en
WebView es SIEMPRE a 1 hilo. bb.js degrada con gracia; no es bloqueante
(registrado en friction-report.md). Implicación UX: "generando prueba… ~10-16 s";
la ruta PWA/Chrome queda documentada como 2.6x más rápida.

## Fase 3 — Registro y decisión (30 min)

**Criterios (de la propuesta A §6):**

- **GO → plan completo A+D:** las 3 pruebas corren en el teléfono, <90s cada una, sin OOM.
- **GO parcial → PWA + D:** corre en Chrome móvil pero el WebView falla (p.ej. por COOP/COEP) → la wallet se entrega como PWA instalable con la misma UI.
- **NO-GO → D solo:** >3 min por prueba, OOM sistemático, o contratos CT de testnet caídos/reseteados → `propuesta_D_indexador_respaldo.md` es la submission. Sin duelo: era la rama prevista.

- [x] Decisión escrita aquí: `GO COMPLETO — las 3 pruebas corren DENTRO del WebView en 10.8-15.7 s (<90 s), sin OOM, en gama media real` · Hora: `2026-08-03 12:00`
- [ ] TODO lo que falló, con mensajes de error textuales → `friction-report.md` (semilla de los issues a OZ y de la sección del README que los jueces van a leer con más cariño que el pitch).

## Mientras tanto, en paralelo (R3 — no espera al spike)

- [ ] `cd raiz-memory && cp .env.example .env` → poner RPC de testnet y CUALQUIER contract id activo (vale el CT wrapper oficial si ya se conoce, o cualquier contrato con eventos) → `cargo run`.
- [ ] Verificar: `curl localhost:8090/health` y que la tabla `events` crece.
- [ ] Éxito del día 0 para R3: eventos reales de testnet persistidos en SQLite. Nada más.
