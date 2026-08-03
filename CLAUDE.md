# CLAUDE.md — Sobre del Barrio × Raiz Memory
<!-- Este archivo va en la RAÍZ del repo del hackathon. Claude Code lo lee
     automáticamente al inicio de cada sesión: es la memoria del proyecto.
     REGLA: al final de CADA sesión de trabajo, actualiza la sección
     "ESTADO ACTUAL" de este archivo. Esa disciplina es la que elimina los
     huecos de memoria entre sesiones. -->

## Qué estamos construyendo (no cambiar este encuadre)

UNA submission para el Special Bounty "Confidential-Token & Private-Payment Wallets"
(Stellar Summit SP 2026, lane Privacy de OpenZeppelin + Nethermind, GrantFox):

**"Sobre del Barrio — the wallet that remembers"**: la primera wallet móvil de
Confidential Tokens (aportes a metas comunitarias con montos cifrados, participación
visible, y el total del fondo verificable por cualquiera vía la view key de auditor
de la meta, publicada a propósito) + **Raiz Memory**, un indexador durable de eventos
que sirve el historial más allá de la ventana de retención de ~7 días del RPC.
Cubre los ejemplos (a) wallet CT y (c) indexer del bounty como un solo sistema.

- **Deadline: 6 de agosto de 2026, 5:00 PM** (hora local São Paulo). Enviar antes del mediodía.
- Entregable: repo GitHub + video demo (opcional en las reglas, obligatorio para nosotros).
- Premio: 2,000 USDC (1º: 1,250 / 2º: 750). Equipo: Raiz Protocol.
- Regla del evento: 1 submission por sub-lane · 100% trabajo original (ver "Reused vs. Original" en README.md) · asistencia presencial.
- Lema del proyecto: *"Los aportes son secretos. El fondo es de vidrio. Y la wallet recuerda."*

## Layout del monorepo

```
/wallet         App Android (Kotlin) — capa CT sobre la base RAÍZ existente
/contracts
  /goal-meta    Contrato Soroban: registro de metas, view keys publicadas, cosechas
                (LOS MONTOS JAMÁS TOCAN ESTE CONTRATO — viven cifrados en el wrapper CT)
/raiz-memory    Indexador Rust: API con forma de getEvents, más allá de los 7 días
/scripts        verify-goal-total: descifra el total de la meta con la view key
                publicada, POR FUERA de la app ("no confíes en nuestra UI")
/vendor         (gitignored) clones de referencia de repos externos — SOLO lectura
friction-report.md   Toda fricción con CT/SPP, textual — se convierte en issues a OZ
BACKLOG.md      Toda idea nueva va AQUÍ, nunca al sprint
```

## Alcance — la regla de oro

MUST (sin esto no hay submission):
1. Ciclo CT desde el teléfono (o PWA si el WebView falla): register → deposit →
   transfer a la meta → merge → balance descifrado en el dispositivo.
2. Pantalla de meta: total público + view key publicada + timeline de aportes
   (quién y cuándo — NUNCA cuánto) alimentado por Raiz Memory.
3. Raiz Memory indexando el wrapper CT + goal_meta, `GET /events` compatible,
   demo de purga/recuperación grabada.
4. Repo limpio, README en inglés, video 2:30.

SHOULD: selective disclosure (recibo del aportante) · despliegue público de Raiz
Memory · script verify-goal-total.
COULD (solo si sobra tiempo): withdraw completo · segunda meta · métricas.
**Cualquier otra idea → BACKLOG.md. El scope creep es el enemigo #1 con 4 días.**

## Árbol de degradación (decidido el día 0, sin duelo)

- Proving CT corre en WebView Android (<90s/prueba, sin OOM) → plan completo.
- Corre en Chrome móvil pero no en WebView (p.ej. COOP/COEP/SharedArrayBuffer) → misma app como **PWA instalable**; el README lo explica honestamente.
- No corre en móvil o contratos CT de testnet inestables → **Raiz Memory ES la submission** (ver propuesta D en /docs); la wallet se recorta a cliente demo del indexador.

## Hechos técnicos verificados (2-ago-2026 — NO redescubrir, NO contradecir)

- **Confidential Tokens (OpenZeppelin + verificador Nethermind):** developer preview
  en TESTNET desde 30-jun-2026, sin auditar. Wrapper sobre cualquier token SEP-41.
  Oculta montos y balances; las direcciones siguen visibles (aquí eso es feature:
  solidaridad visible, montos privados). Pedersen commitments + pruebas Noir +
  verificador UltraHonk, sobre host functions de Protocol 25 (BN254/BLS12-381,
  Poseidon). Operaciones: register, deposit, merge, transfer, withdraw. La prueba
  inicial se genera LOCALMENTE en el navegador (por eso el plan WebView es viable).
  Features de compliance que usamos con propósito: auditor view keys (invertidas:
  el auditor es el público) y selective disclosure. CT rinde en "contabilidad
  aditiva" — sumar aportes es exactamente eso. Entrada al demo y repo: blog
  "Developer Preview: Confidential Tokens on Stellar" (stellar.org, 30-jun-2026).
  **URLs verificadas (Sesión 0, 2-ago — fetched, no adivinadas):**
  blog https://stellar.org/blog/developers/developer-preview-confidential-tokens-on-stellar ·
  demo vivo https://stellar-confidential-token-demo.billowing-moon-0c6f.workers.dev/ ·
  repo del demo https://github.com/brozorec/stellar-confidential-token-demo
  (en /vendor @ ac67499; proving en navegador con @aztec/bb.js 0.87.0 +
  noir_js 1.0.0-beta.9; su app Next.js documenta cross-origin-isolation
  COOP/COEP — confirma el gotcha #4). La implementación CT vive en
  OpenZeppelin/stellar-contracts → packages/tokens/src/confidential/
  (en /vendor @ 9b5ed96: mod.rs con register/deposit/merge/withdraw/
  confidential_transfer; auditor/ para view keys — puntos Grumpkin BytesN<64>;
  circuits/ Noir; docs/ incluye INDEXER.md y SELECTIVE_DISCLOSURE.md, leer antes
  de Sesiones 2/4/7). OJO: el demo consume la rama
  feat/confidential-verifier-ultrahonk de stellar-contracts como git dep.
- **SPP (Nethermind):** privacy pool (oculta montos Y contrapartes), Circom+Groth16,
  PoC de un circuito 2-in/2-out, sin auditar. Su README confiesa la retención de
  ~7 días de eventos del RPC — la razón de ser de Raiz Memory. Repo:
  NethermindEth/stellar-private-payments. No integramos SPP en la wallet; Raiz
  Memory lo indexa con 1 línea de config y el README lo menciona.
  **Hallazgos Sesión 0 (clonado en /vendor @ a1bf177):** la cita de retención
  está en su README L121 (textual, para el pitch); sus contract ids de testnet
  están en deployments/testnet/ (la config SPP de 1 línea sale de ahí). CRÍTICO
  para el posicionamiento: Nethermind ya incluye tools/bootnode — un proxy de
  archivo getEvents con handoff al RPC (error -32002) — como parche de sync
  para SU wallet. Raiz Memory se diferencia: indexador durable GENERAL con API
  getEvents pública que sirve a cualquier contrato (CT wrapper + goal_meta +
  SPP). El README debe decirlo explícitamente.
- **Red:** Protocol 27 "Zipper" activo en mainnet desde 8-jul-2026 (delegación de
  auth para smart accounts — relevante para el roadmap RAÍZ F3, NO para este MVP).
  Testnet: RPC https://soroban-testnet.stellar.org · friendbot para fondear.
- **RAÍZ (proyecto madre, pre-existente y declarado):** app Android Kotlin con
  passkeys (OZSmartAccountKit), contratos Soroban (Pool, Governance soulbound,
  Treasury) en testnet, workspace en soroban-sdk 22.x.

## Gotchas conocidos (pagados con sangre — no re-pagarlos)

1. **ed25519-dalek 3.0 rompe soroban-env-host** (verificado 2-ago). El Cargo.lock
   de /contracts/goal-meta ya lo fija. Si se regenera el lockfile:
   `cargo update -p ed25519-dalek@3.0.0 --precise 2.2.0`.
2. **TTL de storage persistent** (lección RAÍZ/DeFindex): todo entry persistent
   se extiende explícitamente (`extend_ttl`). Revisar valores contra la config
   actual de testnet antes del día 3.
3. **Testnet flaky en ráfagas** (experiencia RAÍZ): todo script con reintentos;
   toda demo se GRABA el día que funciona; nunca demo en vivo sin grabación de respaldo.
4. **WASM multihilo en WebView** puede exigir crossOriginIsolated (headers
   COOP/COEP) — con WebViewAssetLoader hay que inyectarlos; si no se puede, PWA.
5. **getEvents del RPC**: la forma exacta del JSON varía entre versiones
   (cursor vs pagingToken; value como string o como {"xdr": ...}). raiz-memory ya
   tolera ambas, pero VALIDAR contra el RPC real el día 0 (Sesión 2).

## Decisiones tomadas — NO reabrir durante el evento

- Proving en WebView aislado con el stack JS/WASM del demo CT **sin modificar**;
  custodia de claves, firma y envío de tx SIEMPRE en Kotlin (Keystore/
  EncryptedSharedPreferences). El WebView no persiste secretos.
- Los contratos de OpenZeppelin se consumen TAL CUAL. Nuestro código original:
  goal_meta, capa móvil, patrón view-key-pública, Raiz Memory.
- Passkey = auth de la app (stack RAÍZ existente). NO prometemos passkey-como-
  firmante-CT; va como roadmap en el README.
- Nombres de UX en la app (español): register="Abrir mi sobre", deposit="Sellar",
  transfer="Aportar", merge="Cosechar", withdraw="Abrir el sobre",
  view key="Verifícalo tú mismo", disclosure="Mi recibo".
- Timeline de la meta se alimenta de Raiz Memory vía URL **configurable** —
  cambiarla en vivo (RPC↔Raiz Memory) es la escena central del video.
- Idiomas: UI en español · README, código y commits en inglés · este archivo en español.

## Reglas de trabajo para Claude Code (importantes)

1. **Nunca inventes una API externa.** Antes de escribir código contra CT, SPP o
   el RPC: lee el código real en /vendor (clonado en la Sesión 0) o haz una
   llamada real de prueba. Si un símbolo no aparece en /vendor, no existe.
2. **Toda fricción con CT/SPP se registra en friction-report.md** con el mensaje
   de error textual. Ante estos jueces, la fricción documentada SUMA puntos.
3. **Al cerrar cada sesión:** actualiza "ESTADO ACTUAL" abajo, commitea con
   mensaje claro en inglés, y deja anotado el siguiente paso concreto.
4. **No toques /vendor** (solo lectura) ni los contratos de OZ.
5. Si una tarea revienta su timebox, se anota en BACKLOG.md y se recorta según
   el árbol de degradación. El deadline no negocia.
6. Tests: goal_meta se trata como contrato que mueve plata (suite completa);
   raiz-memory con tests de integración mínimos; la app con el flujo feliz + errores honestos.

## ESTADO ACTUAL (actualizar al cierre de CADA sesión)

```
Última actualización: 2026-08-02 (Sesión 0 ejecutada)
Decisión del spike día 0:  PENDIENTE  [GO completo / GO parcial→PWA / NO-GO→D solo]
Sesión 0 (setup repo):          [x]  /vendor poblado (3 repos, SHAs en Hechos verificados) ·
                                     raiz-memory cargo check OK (0 warnings) ·
                                     goal-meta cargo test OK (1/1, dalek 2.2.0 pinned) ·
                                     URLs CT verificadas · .claude (2 agentes, 5 skills) · git init
Sesión 1 (spike proving):       [ ]  tiempos medidos: ______
Sesión 2 (raiz-memory vivo):    [ ]  contratos indexados: ______
Sesión 3 (goal_meta testnet):   [ ]  contract id: ______
Sesión 4 (ciclo CT por CLI):    [ ]  wrapper CT id: ______
Sesión 5 (bridge WebView):      [ ]
Sesión 6 (pantallas+integración):[ ]
Sesión 7 (verify script+recibo):[ ]
Sesión 8 (hardening+README):    [ ]
Sesión 9 (video+submission):    [ ]
Bloqueantes abiertos: faltan en /docs los documentos propuesta_A_sobre_del_barrio.md,
  propuesta_D_indexador_respaldo.md y evaluacion_bounty_privacy_stellar_summit.md —
  NO estaban en ~/Downloads; exportarlos de la conversación de planeación de
  claude.ai (las Sesiones 3, 7 y 9 citan sus secciones §4/§8/§9/§10).
  No bloquea las Sesiones 1 y 2.
Siguiente paso concreto: Sesión 1 (spike proving — requiere teléfono + humano) y
  Sesión 2 (raiz-memory contra testnet real) en paralelo. Deuda anotada para la
  Sesión 3: goal_meta solo trae 1 test (create_and_read_goal); ahí se endurece
  la suite completa. Arrancar con: /sesion 1 o /sesion 2.
```

## Documentos de referencia en /docs

- propuesta_A_sobre_del_barrio.md — la propuesta completa (guion de video §8, texto de submission §9)
- propuesta_D_indexador_respaldo.md — el plan si el spike da NO-GO
- PLAN_SINERGIA.md — plan día a día por roles
- SPIKE_DIA0.md — runbook del spike con criterios GO/NO-GO
- evaluacion_bounty_privacy_stellar_summit.md — el porqué de todas las decisiones
