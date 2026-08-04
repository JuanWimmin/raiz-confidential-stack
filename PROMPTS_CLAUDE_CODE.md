# Prompts para Claude Code — Sobre del Barrio × Raiz Memory
## Secuencia de sesiones sin huecos conceptuales ni de memoria

> **Cómo usar este documento**
>
> 1. Prepara el repo UNA vez (Sesión 0) y pon `CLAUDE.md` en la raíz — Claude Code
>    lo lee automáticamente en cada sesión: esa es la memoria persistente.
> 2. **Una sesión de Claude Code por prompt.** Al terminar cada una, verifica que
>    Claude actualizó la sección ESTADO ACTUAL de CLAUDE.md y commiteó. Si abres
>    una sesión nueva a mitad de tarea, di solo: *"Lee CLAUDE.md y continúa desde
>    'Siguiente paso concreto'"* — con eso retoma sin perder nada.
> 3. Para las sesiones grandes (3, 5, 6) empieza en **modo plan** (Shift+Tab en
>    Claude Code): deja que proponga el plan, apruébalo, y luego ejecuta.
> 4. Los prompts están en español; Claude Code trabaja perfecto así. Código,
>    commits y README salen en inglés (regla en CLAUDE.md).
> 5. El orden respeta dependencias, pero las Sesiones 2 (indexador) y 1/3/4
>    (wallet/contratos) son paralelizables entre personas distintas.

---

## SESIÓN 0 — Setup del monorepo y anclas anti-alucinación (30-45 min)

```
Vas a preparar el monorepo de nuestra submission al Stellar Summit SP 2026.
Trabajamos contra un deadline real: 6 de agosto, 5 PM.

1) Crea la estructura del repo en el directorio actual:
   - Copia el contenido de ~/Downloads/raiz-summit-kit/ así:
     raiz-memory/ → /raiz-memory · goal-meta/ → /contracts/goal-meta ·
     android/ → /wallet/docs-integration/ · README.md → /README.md ·
     PLAN_SINERGIA.md y SPIKE_DIA0.md → /docs/
   - Copia también a /docs los documentos: propuesta_A_sobre_del_barrio.md,
     propuesta_D_indexador_respaldo.md, evaluacion_bounty_privacy_stellar_summit.md
     (están en ~/Downloads/).
   - Pon el CLAUDE.md que te di en la raíz del repo.
   - Crea BACKLOG.md y friction-report.md vacíos con un encabezado.
   - .gitignore: target/, *.db, .env, /vendor, build/, .gradle/

2) Anclas anti-alucinación — clona SOLO LECTURA en /vendor:
   - El repo/demo oficial de Confidential Tokens de OpenZeppelin: encuéntralo
     desde el blog "Developer Preview: Confidential Tokens on Stellar"
     (stellar.org, 30 de junio de 2026). Anota en CLAUDE.md (sección Hechos
     verificados) la URL exacta del repo y del demo desplegado.
   - https://github.com/NethermindEth/stellar-private-payments
   - https://github.com/OpenZeppelin/stellar-contracts
   REGLA PERMANENTE: antes de escribir código que llame a cualquier API de estos
   proyectos, léela en /vendor. Si el símbolo no está ahí, no existe.

3) Verifica que compila lo heredado:
   - cd raiz-memory && cargo check
   - cd contracts/goal-meta && cargo test
   (Ojo: el Cargo.lock de goal-meta fija ed25519-dalek 2.2.0 — si algo falla,
   lee el comentario KNOWN FIX en su Cargo.toml antes de tocar nada.)

4) git init, primer commit "chore: bootstrap monorepo from planning kit",
   y actualiza ESTADO ACTUAL en CLAUDE.md (Sesión 0 ✓, siguiente paso: Sesión 1 y 2 en paralelo).

Criterio de éxito: repo compilando, /vendor poblado, URLs del demo CT anotadas
en CLAUDE.md. No hagas NADA más allá de esto.
```

---

## SESIÓN 1 — Spike día 0: proving de CT en móvil (timebox 3h, con humano)

> Esta sesión es mitad manual (necesitas el teléfono). Claude Code prepara el
> arnés y analiza resultados; tú ejecutas en el dispositivo. Runbook completo: docs/SPIKE_DIA0.md.

```
Lee CLAUDE.md y docs/SPIKE_DIA0.md. Hoy respondemos UNA pregunta: ¿se generan
pruebas de Confidential Tokens en un teléfono Android de gama media? Timebox: 3h.

1) Estudia en /vendor el demo oficial de CT: identifica (a) cómo y dónde genera
   las pruebas (módulo, función de entrada, si usa workers/SharedArrayBuffer),
   (b) si el proving es separable de la firma con Freighter, (c) qué necesita
   para servirse localmente. Escribe el resumen en docs/spike-findings.md.

2) Levanta el demo localmente (npm install && dev server) accesible desde la LAN
   para que yo lo abra en Chrome del teléfono. Dame la URL y los pasos exactos,
   incluyendo cómo fondear las cuentas de testnet (friendbot).

3) Crea en /wallet un proyecto Android mínimo (una Activity con WebView,
   javaScriptEnabled + domStorageEnabled) que cargue esa URL, siguiendo
   /wallet/docs-integration/ProverWebViewBridge.kt como referencia. Si el paso 1
   reveló que necesita crossOriginIsolated, configura los headers COOP/COEP en el
   dev server y documenta cómo replicarlo con WebViewAssetLoader.

4) Yo ejecuto en el teléfono y te pego los resultados (tiempos, errores, crashes).
   Tú: registra TODO textual en friction-report.md, evalúa contra los criterios
   GO/GO-parcial/NO-GO de docs/SPIKE_DIA0.md, y escribe la decisión en
   CLAUDE.md → ESTADO ACTUAL → "Decisión del spike".

Si la decisión es NO-GO: abre docs/propuesta_D_indexador_respaldo.md, reescribe
la lista de MUST en CLAUDE.md según esa propuesta, y anota el pivote. Sin duelo.
```

---

## SESIÓN 2 — Raiz Memory vivo contra testnet real (R3, paralelo a la Sesión 1)

```
Lee CLAUDE.md. Vas a dejar el indexador raiz-memory ingiriendo eventos REALES
de testnet. El esqueleto ya compila; hoy se valida contra el mundo.

1) Haz una llamada real getEvents al RPC de testnet (curl) contra cualquier
   contrato activo. Compara la forma EXACTA del JSON (cursor vs pagingToken,
   value string vs {"xdr":...}, nombres de campos) con lo que asumen
   src/rpc.rs y src/db.rs, y corrige lo que difiera. Anota la forma real
   verificada como comentario en src/rpc.rs con fecha.

2) cp .env.example .env con el RPC de testnet y un contract id activo (si ya
   conocemos el wrapper CT oficial —ver CLAUDE.md—, usa ese). cargo run.
   Verifica: /health responde, la tabla events crece, /events pagina bien
   (pide 2 páginas con cursor), /coverage dice la verdad.

3) Escribe un test de integración mínimo (puede usar un mock del RPC) que cubra:
   inserción idempotente, avance de cursor, paginación por cursor.

4) Prepara el "modo demo de purga": un flag RETENTION_SIMULATION_LEDGERS que
   hace que /events (cuando se consulta con ?source=rpc-simulation) devuelva
   SOLO los últimos N ledgers — así en el video mostramos lado a lado "RPC que
   olvida" vs "Raiz Memory que recuerda" sin depender de esperar 7 días.
   Documenta el flag en raiz-memory/README.md.

Cierra: commit, ESTADO ACTUAL actualizado (contratos indexados: <ids>),
siguiente paso anotado. NO despliegues a ninguna VM todavía (eso es Sesión 8).
```

---

## SESIÓN 3 — goal_meta desplegado en testnet + scripts de flujo (día 1, R1)

> Empieza en modo plan.

```
Lee CLAUDE.md. Hoy goal_meta pasa de esqueleto a contrato desplegado en testnet
con su flujo completo scripteado.

1) Revisa contracts/goal-meta/src/lib.rs contra la spec de la propuesta
   (docs/propuesta_A_sobre_del_barrio.md §4): añade lo que falte SOLO del MUST
   (¿evento "contribution_seen" no! — los aportes se leen del wrapper CT, no de
   goal_meta; mantén el invariante: LOS MONTOS NO TOCAN ESTE CONTRATO).
   Endurece la suite de tests: metas duplicadas, harvest sin auth, deadline
   pasado, view key vacía, TTL (gotcha #2 de CLAUDE.md).

2) Compila a wasm (stellar contract build) y despliega a testnet con una cuenta
   nueva fondeada por friendbot. Guarda address y secret SOLO en
   .env.deploy (gitignored) y el contract id en CLAUDE.md → ESTADO ACTUAL.

3) Crea /scripts/goal-flow.sh (o .ts con stellar-sdk, lo que sea más robusto):
   crear meta demo "Techo de la casa comunal, 500 XLM, diciembre" → leerla →
   record_harvest → verificar el evento emitido vía getEvents del RPC.
   Con reintentos (gotcha #3: testnet flaky).

4) Añade el contract id de goal_meta al CONTRACT_IDS de raiz-memory/.env y
   verifica que el evento de la cosecha aparece en /events del indexador.
   ESE es el primer momento de sinergia real del proyecto — anótalo en
   ESTADO ACTUAL cuando lo veas pasar.

Cierra con commit + ESTADO ACTUAL. Todo lo que no sea MUST → BACKLOG.md.
```

---

## SESIÓN 4 — Ciclo CT completo por CLI (día 1, R1 tras la Sesión 3)

```
Lee CLAUDE.md y docs/spike-findings.md. Hoy el ciclo CT completo corre por
línea de comandos contra testnet, ANTES de tocar más la app. La wallet solo
"pinta" lo que hoy demuestres por CLI.

1) Desde /vendor (el demo CT y stellar-contracts de OZ), identifica cómo:
   desplegar/instanciar el wrapper CT sobre un token SEP-41 (usa XLM/SAC como
   el demo oficial), y ejecutar register, deposit, transfer, merge, y la
   lectura/descifrado de balance. NO reimplementes nada de eso: invoca su
   tooling tal cual (regla: contratos OZ se consumen sin modificar).

2) Decide con evidencia: ¿usamos el wrapper CT ya desplegado por OZ en testnet
   o desplegamos instancia propia? Criterio: estabilidad para la demo (si el
   oficial puede resetearse o llenarse de ruido de otros equipos, propia).
   Documenta la decisión y el contract id en CLAUDE.md.

3) Crea /scripts/ct-flow.md: la secuencia EXACTA de comandos reproducible
   (dos cuentas: "Marta" y la cuenta-meta) que hace: register ambas → deposit
   de Marta → transfer confidencial a la meta → merge de la meta → balance de
   la meta descifrado. Cada paso con su verificación en el explorer (mostrar
   que el monto NO es visible on-chain — captura para el video).

4) La view key de auditor de la cuenta-meta: averigua en /vendor cómo se
   designa/exporta, ejecútalo, y guárdala donde el goal de goal_meta la publica.
   Si la preview aún no expone view keys utilizables: registra la fricción en
   friction-report.md y usa el fallback documentado en la propuesta A §10
   (disclosure periódico del balance por el custodio). NO finjas la feature.

5) Añade el contract id del wrapper CT a raiz-memory/.env → verifica que los
   eventos del transfer aparecen en /events.

Cierra: commit + ESTADO ACTUAL (wrapper CT id, decisión propia/oficial, estado
de view keys). Este es el hito M-día-1: "el flujo entero existe, sin app".
```

---

## SESIÓN 5 — Bridge WebView real (día 1-2, R2; requiere GO del spike)

> Empieza en modo plan.

```
Lee CLAUDE.md, docs/spike-findings.md y /wallet/docs-integration/
ProverWebViewBridge.kt. Hoy la app genera su primera prueba CT on-device.

1) Empaqueta el bundle de proving del demo CT (identificado en el spike) bajo
   /wallet/app/src/main/assets/prover/ y sírvelo con WebViewAssetLoader
   (https://appassets.androidplatform.net). Si el spike mostró que necesita
   COOP/COEP, implementa la inyección de headers en el asset loader
   (interceptRequest). Si ahí muere: PWA fallback (árbol de degradación de
   CLAUDE.md) — no insistas más de 2 horas.

2) Define window.RaizProver en un shim JS NUESTRO (original) que envuelve las
   funciones de proving del demo con la API estable:
   generate(kind, inputsJson) → Promise<proof> para kind register|deposit|
   transfer|merge. El shim vive en assets/prover/raiz-shim.js.

3) Implementa ProverWebViewBridge.kt de verdad (partiendo del sketch):
   inicialización headless, generateProof con withTimeout(90_000), manejo de
   errores → friction-report.md. El WebView NO persiste secretos (regla).

4) Prueba instrumentada o manual: desde una pantalla de debug, generar una
   prueba de register en el dispositivo y loguear el tiempo. Ese número va a
   ESTADO ACTUAL y al README (sección de performance honesta).

5) Cablea firma y envío: la prueba que sale del bridge se inserta en la
   invocación Soroban firmada por el wallet manager existente de RAÍZ (Kotlin),
   replicando lo que /scripts/ct-flow.md hace por CLI.

Cierra: commit + ESTADO ACTUAL. Hito: register + deposit REALES desde la app.
```

---

## SESIÓN 6 — Las tres pantallas + integración Raiz Memory (día 2, R2)

> Empieza en modo plan.

```
Lee CLAUDE.md y /wallet/docs-integration/NOTES.md. Hoy la app cuenta la
historia completa. Reusa componentes y estilo de la app RAÍZ existente.

1) Pantalla "Meta" (pública, sin login): nombre, objetivo, % alcanzado, timeline
   (dirección abreviada + fecha — NUNCA montos; eventos "harvest" como hitos
   "Cosecha"), botón "Verifícalo tú mismo" que muestra la view key y el comando
   del script de verificación. Fuente de datos: GET /events de Raiz Memory con
   URL CONFIGURABLE en settings (la escena del video depende de poder cambiarla
   en vivo entre RPC-simulado y Raiz Memory).

2) Pantalla "Mi Sobre": balance CT descifrado en el dispositivo, botones
   "Sellar" (deposit) y "Aportar" (transfer a la cuenta-meta del goal), cada uno
   con estados de carga honestos ("generando prueba en tu teléfono… ~Xs",
   "testnet lenta, reintentando 3/5…"). Nombres de UX exactos de CLAUDE.md.

3) Flujo "Cosechar" (solo admin): merge CT + goal_meta.record_harvest en
   secuencia, con el evento apareciendo en el timeline al refrescar.

4) Ensayo integrado (guion en NOTES.md): aporte → explorer sin monto → cosecha →
   total actualizado → cambio de URL de eventos en vivo → "la wallet que
   recuerda". Documenta en docs/demo-run.md los pasos exactos y GRABA pantalla
   en cuanto funcione (gotcha #3: la grabación es el seguro de vida).

Cierra: commit + ESTADO ACTUAL + lista honesta de asperezas de UX en BACKLOG.md
(se puleN en Sesión 8 SOLO si sobra tiempo).
```

---

## SESIÓN 7 — Verificación externa + recibo (día 2-3, R1)

```
Lee CLAUDE.md. Hoy construimos la credibilidad: que NADIE tenga que confiar en
nuestra UI.

1) /scripts/verify-goal-total: script standalone (TS o Rust, lo que menos
   dependencias arrastre) que recibe la view key publicada + el contract id del
   wrapper CT y la cuenta-meta, y descifra/verifica el total de la meta
   directamente contra la cadena. Salida clara: "Goal total: X XLM — verified
   on-chain at ledger N". Es la materialización de "el fondo es de vidrio";
   los jueces deben poder correrlo ellos mismos (instrucciones en README).
   La mecánica exacta de la view key sal de /vendor (y de lo aprendido en
   Sesión 4.4); si la preview no lo permite aún, implementa el fallback
   documentado y dilo con honestidad en README + friction-report.md.

2) Selective disclosure ("Mi recibo"): flujo mínimo donde Marta genera la
   prueba/disclosure de SU aporte y la app la muestra como recibo compartible.
   Si el costo excede medio día: recórtalo a demo por CLI en /scripts y
   documenta — es SHOULD, no MUST.

3) Pasa por friction-report.md: convierte cada entrada en un borrador de issue
   (título, versión, repro, comportamiento esperado/observado) en
   /docs/issues-drafts/. NO los publiques aún — los abrimos el día 3 tras
   revisión humana.

Cierra: commit + ESTADO ACTUAL.
```

---

## SESIÓN 8 — Hardening, README final, despliegue público (día 3)

```
Lee CLAUDE.md. Hoy se congela el alcance a las 18:00. Nada nuevo después.

1) Raiz Memory: despliegue público (VM/servicio que te indique — pídeme la
   info de acceso; si no la tengo lista, docker compose local expuesto con
   túnel y documenta ambas rutas). URL pública → README + app settings default.
2) Pasada de robustez guiada por los gotchas de CLAUDE.md: reintentos en todos
   los scripts, TTLs revisados contra config real de testnet, manejo del caso
   "RPC caído" en la app (mensaje honesto, no crash).
3) README.md final (inglés): completa TODOS los placeholders [link] — repo del
   friction report, contract ids reales, tiempos de proving medidos, la sección
   Reused vs. Original actualizada con TODO lo que realmente reutilizamos.
   Léelo como un juez: ¿puedo clonar y reproducir en 15 minutos? Ajusta hasta
   que la respuesta sea sí.
4) Publica los issues de /docs/issues-drafts/ en los repos de OZ (tras mi OK
   explícito en esta sesión — pídemelo mostrándome cada uno) y enlázalos en README.
5) Ensayo completo de la demo con cronómetro + grabación de respaldo actualizada.

Cierra: commit + tag "freeze-d3" + ESTADO ACTUAL con la checklist de mañana.
```

---

## SESIÓN 9 — Día de entrega (6 de agosto, mañana)

```
Lee CLAUDE.md. Hoy SOLO se entrega. Cero features. El freeze de ayer manda.

1) Checklist de submission (verifica una por una, contra las capturas del bounty
   en /docs): repo público con README final · link de Repository listo ·
   video subido y enlazado · texto de submission (propuesta A §9, actualizado
   con los ids y números reales) listo para pegar en GrantFox.
2) El video: guion en propuesta A §8 + la escena de purga (propuesta D §3).
   Ayúdame a recortar el guion a 2:30 con los tiempos reales que medimos.
3) Smoke test completo de la demo grabada + verify-goal-total corrido en limpio
   desde un clon fresco del repo en /tmp (como lo haría un juez).
4) Yo envío la submission en GrantFox ANTES del mediodía. Tú: última
   actualización de ESTADO ACTUAL ("SUBMITTED, <hora>") y tag "submission".

Después de enviar, si queda energía: BACKLOG.md está lleno de COULDs — pero
solo con la submission ya asegurada y editable.
```

---

## PROMPT DE EMERGENCIA — pivote a D (usar solo si el spike dio NO-GO)

```
Lee CLAUDE.md (la decisión NO-GO está registrada) y docs/
propuesta_D_indexador_respaldo.md. Raiz Memory ES ahora la submission.




1) Reescribe los MUST de CLAUDE.md según la propuesta D §4 y ajusta ESTADO
   ACTUAL: las sesiones 4-7 de wallet se cancelan; las reemplazan: (a) modo
   demo de purga pulido, (b) despliegue público temprano, (c) demo de
   recuperación con la wallet SPP oficial de /vendor apuntada a nuestro
   /events, (d) cliente demo mínimo si hay tiempo.
2) La escena central del video pasa a ser la de propuesta D §3 (wallet que
   pierde sus notas → Raiz Memory las recupera). Prepara docs/demo-run.md.
3) El texto de submission es el de propuesta D §5 — actualízalo con ids reales.
El resto de reglas de CLAUDE.md (fricción, commits, estado) siguen igual.
```

---

## Chuleta de hábitos en Claude Code (para ti, Juancho)

- Sesión nueva a mitad de algo: *"Lee CLAUDE.md y continúa desde 'Siguiente paso concreto'"*.
- Si Claude propone algo fuera de MUST/SHOULD: *"A BACKLOG.md"* — y sigue.
- Si Claude "recuerda" una API que no está en /vendor: *"verifícalo en /vendor antes"*.
- Contexto largo y respuestas lentas → /compact (resume la sesión y sigue).
- Antes de cada cierre de sesión: *"actualiza ESTADO ACTUAL y commitea"* si no lo hizo solo.
- El día 6: nada de ideas nuevas. El BACKLOG es para la semana siguiente — el SCF de octubre se alimenta de él.
```
