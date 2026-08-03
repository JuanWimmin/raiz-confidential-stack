# Evaluación de opciones — Special Bounty "Confidential-Token & Private-Payment Wallets"
## Stellar Summit SP 2026 · Lane Privacy (OpenZeppelin + Nethermind) · Presentando como Raiz Protocol

> Preparado el 2026-08-02. Basado en: capturas del bounty en GrantFox, documentación oficial de Confidential Tokens (OpenZeppelin) y Stellar Private Payments (Nethermind), estado de la red post-Zipper (P27), y la documentación de RAÍZ (`propuesta_raiz_ahorro_enjambre.md`, `plan_trabajo_raiz.md`). Fuentes al final.

---

## 1. El bounty, en frío

| Dato | Valor |
|---|---|
| Sub-lane | Confidential-Token & Private-Payment Wallets (lane Privacy: OpenZeppelin + Nethermind) |
| Premio | 2,000 USDC total · 2 ganadores (1º: 1,250 · 2º: 750) · escrow on-chain |
| Cierre | **6 de agosto de 2026, 5:00 PM** (~4 días desde hoy) |
| Estado | OPEN, pero "not accepting submissions right now" — abre cuando el evento esté activo |
| Submissions actuales | 0 |
| Entregables | Repo de GitHub (obligatorio) · video demo (opcional) |
| Reglas clave | 1 submission editable por sub-lane por equipo · asistencia presencial de todo el equipo · 100% trabajo original · máximo 2 sub-lanes por equipo |

Lo que piden, textual: experiencias de wallet enfocadas en privacidad usando las primitivas más recientes de Stellar, demostrando **adopción práctica de Confidential Tokens y/o Stellar Private Payments**. Sus tres ejemplos: (a) wallet con balance confidencial y envío/recepción privados, (b) wallet sobre el privacy pool SPP de Nethermind con depósitos/retiros shielded, (c) **un indexador durable de eventos** que cubra el historial más allá de la ventana de 7 días del RPC.

Dos lecturas estratégicas de la letra pequeña:

1. **"1 submission por sub-lane"** significa que el indexador y la wallet compiten entre sí si presentas ambos aquí — hay que elegir UNA carta para esta sub-lane. La regla de "2 sub-lanes" permite un segundo proyecto, pero en *otra* sub-lane del evento (vale revisar cuáles hay: si existe una de smart accounts/OpenZeppelin, tu Fase 3 de custodia encaja ahí casi sin fricción).
2. **Que los jueces sean OpenZeppelin y Nethermind** cambia el criterio: no juzgan "la mejor app cripto", juzgan quién usó *sus* primitivas con criterio, quién les reportó fricción real de integración, y quién construyó algo que a ellos les sirva mostrar. Eso premia profundidad de integración sobre pulido cosmético.

---

## 2. El terreno técnico verificado (estado real, 2 de agosto)

### Confidential Tokens (CT) — OpenZeppelin, con verificador de Nethermind
- **Qué hace:** wrapper sobre cualquier token SEP-41 (USDC incluido). Oculta **montos y balances**; las direcciones de emisor/receptor siguen visibles. "Contrapartes conocidas, montos ocultos" — diseñado para nómina, tesorería, settlement.
- **Cómo:** compromisos de Pedersen + pruebas Noir verificadas on-chain con el verificador UltraHonk de Nethermind, sobre las host functions ZK de P25 (BN254/BLS12-381, Poseidon) — la base que tu propia propuesta ya identificaba como "fundamentos ZK".
- **Features de compliance que casi nadie va a usar bien:** *auditor view keys* (un auditor designado puede ver montos), *selective disclosure* (probar una transacción a un tercero), freezing heredado del SAC, policy engine configurable.
- **Estado:** developer preview en **testnet** desde el 30 de junio de 2026. No auditado, no mainnet. Demo oficial: navegador + Freighter. Buscan activamente design partners y participantes de hackathones.

### Stellar Private Payments (SPP) — Nethermind
- **Qué hace:** privacy pool. Oculta **montos Y contrapartes** (depósitos, transferencias internas y retiros shielded), con safeguards de compliance vía ASP (Association Set Providers — el patrón Privacy Pools de Buterin et al.).
- **Cómo:** circuitos Circom + Groth16, prueba generada en el navegador vía WASM, notas guardadas localmente en SQLite/OPFS. Repo ~78% Rust, open-source reciente.
- **Estado:** PoC explícito — un solo circuito 2-inputs/2-outputs, sin auditar, con resets de testnet esperables. **24 issues abiertos, varios marcados contributor-friendly.**
- **Su herida abierta, confesada en su propio README:** el RPC solo retiene eventos 7 días. Quien pierda su estado local o llegue tarde **no puede reconstruir sus notas**. Por eso el indexador aparece como ejemplo del bounty: *es Nethermind pidiendo que alguien le construya la pieza que le falta.*

### El gap que ninguno de los dos demos cubre
Ambos demos oficiales son **web + Freighter + escritorio**. No existe ninguna experiencia móvil de CT ni de SPP. Y la tesis de adopción de Stellar (LatAm, pagos cotidianos, stablecoins) es una tesis **móvil**.

---

## 3. El filtro anti-genérico: qué van a presentar los demás

Predicción de lo que producirá "consultarle la idea a una IA" (y que conviene esquivar):

- **El clon del demo con otro CSS:** wallet web sobre el código de ejemplo de CT, con dark mode y un nombre tipo "ShadowPay/StealthWallet/PrivatePay". Habrá varios.
- **"Nómina privada" / "remesas privadas" genéricas:** el caso de uso que el propio blog de Stellar menciona — es la primera respuesta de cualquier LLM y no demuestra nada que el sponsor no haya dicho ya.
- **Chat/AI wrapper:** "un agente que hace pagos privados". Cero integración profunda.

Lo que sobrevive al filtro: (1) un caso de uso con **identidad propia y anclaje real** que la IA no puede inventar porque no conoce tu contexto; (2) **móvil**, porque nadie lo tiene y duele hacerlo; (3) **infraestructura que el sponsor necesita**, porque es trabajo, no idea.

Tu ventaja estructural: RAÍZ no es una idea de esta semana. Es un protocolo con paper, contratos desplegados en testnet, app Android con passkeys, y una comunidad objetivo real (Cartagena). Cualquier opción que se apoye ahí llega al pitch con una historia que ningún otro equipo puede copiar en 4 días.

---

## 4. Activos de RAÍZ directamente aprovechables

- **App Android + passkeys (OZSmartAccountKit):** ya usas el stack de smart accounts de OpenZeppelin — uno de los dos jueces. Una wallet CT con auth por passkey sobre su propio kit es un guiño directo.
- **Contratos Soroban en producción de testnet** (Pool, Governance soulbound, Treasury) + experiencia real con sus gotchas (TTL, testnet flaky).
- **El principio F6 ya escrito:** *"transparencia colectiva, privacidad individual — el fondo común jamás se oculta; se privatiza el aporte individual"*. Tu plan literalmente decía: *"esperar la salida de preview de los Confidential Tokens de OpenZeppelin"*. **La preview salió el 30 de junio. Este bounty es esa espera terminándose.**
- **Spikes ZK ya planeados** (mopro en Android, verificador en Soroban) que este bounty adelanta con excusa y premio.

---

## 5. Las opciones

### Opción A — "Sobre del Barrio": aportes confidenciales a metas comunitarias (CT, móvil) ⭐ recomendada
**Qué es.** La primera wallet móvil de Confidential Tokens: una app Android (o módulo dentro de RAÍZ) donde un vecino o un turista aporta a una meta del barrio ("la escuela: 500 USDC para diciembre") **sin revelar cuánto aportó**. Se ve *quién* participa (CT deja las direcciones visibles — solidaridad visible), no *cuánto* (montos ocultos — sin dinámicas de estatus ni exhibición de pobreza/riqueza). El giro que gana: la cuenta de la meta publica su **view key de auditor abiertamente** — cualquiera puede verificar el total del fondo. Es tu principio F6 ("transparencia colectiva, privacidad individual") implementado con las features de compliance de CT que el 95% de los equipos va a ignorar.

**Por qué merece el bounty.** Usa CT exactamente para lo que fue diseñado (contrapartes conocidas, montos ocultos — un barrio ES contrapartes conocidas); usa auditor view keys y selective disclosure con propósito, no como checkbox; es móvil-first (primera en su clase); y tiene una historia que ninguna IA genera porque sale de tu paper. Balance de envío/recepción privado incluido = cumple el ejemplo (a) del bounty y lo supera.

**Esfuerzo 4 días / riesgo.** Medio-alto. El riesgo único es la **generación de pruebas Noir/UltraHonk en Android**: no hay camino nativo maduro. Mitigación en cascada: (1) WebView embebido corriendo el stack JS/WASM del demo oficial dentro de la app — prueba local, sin servidor, honesto; (2) si falla, web-app móvil con la identidad visual de RAÍZ y passkeys; (3) documentar la fricción encontrada e issues al repo de OZ — eso, ante estos jueces, *suma* puntos en vez de restar. Contrato `goal_vault` mínimo (crear meta, recibir aportes CT, exponer total) es terreno que dominas.

**Valor post-evento.** Es F6-fase-2 adelantada seis meses, un artefacto demostrable para la aplicación SCF de octubre ("surfeamos cada upgrade: P27 → custodia, CT preview → ahorro privado"), y posible relación de design partner con OpenZeppelin.

### Opción B — Wallet CT móvil "pura" (balance privado + envío/recepción)
**Qué es.** La opción A sin la capa de metas comunitarias: primera wallet CT en Android, passkeys, wrap/unwrap de USDC testnet, balance descifrado en el dispositivo, envío/recepción con montos ocultos.

**Análisis.** Mismo riesgo técnico que A, menor originalidad: cumple el ejemplo (a) del bounty al pie de la letra, y "al pie de la letra" es exactamente donde estará la manada (aunque en web, no en móvil — el móvil sigue diferenciando). Es el plan de repliegue natural si el contrato de metas de A se atasca: A degrada a B sin perder el trabajo. No la elegiría como plan primario teniendo A disponible por casi el mismo costo.

### Opción C — Wallet SPP móvil con prover local (mopro/rapidsnark)
**Qué es.** Depósitos y retiros shielded en el privacy pool de Nethermind desde Android, con prueba Groth16 generada nativamente en el teléfono (SPP usa Circom — compatible con el stack mopro/witnesscalc/rapidsnark que ya tenías en tu radar F6 para Semaphore).

**Análisis.** El proyecto técnicamente más impresionante de la lista y el más cercano al corazón de Nethermind. Pero es el más riesgoso en 4 días: hay que portar la gestión de notas (SQLite/OPFS → Room), el escaneo de eventos y el ciclo completo del pool a Android, contra un PoC que cambia sin aviso y con resets de testnet. Recorte honesto posible: solo depósito + retiro (sin transferencias internas). Si sale, es memorable; la probabilidad de llegar al día 6 con demo estable es la menor de las cuatro. Como spike de investigación para tu F6 vale oro incluso si no gana.

### Opción D — "La Memoria del Barrio": indexador durable de eventos CT/SPP
**Qué es.** El ejemplo (c) del bounty, que es Nethermind describiendo su propia carencia: un servicio Rust que ingiere eventos de los contratos CT/SPP y los persiste más allá de la ventana de 7 días del RPC, con API limpia para que cualquier wallet lo consulte. Demo teatral: se purga el RPC (o se simula), la wallet oficial muere, la tuya re-sincroniza desde tu indexador.

**Análisis.** La mayor probabilidad de ganar por unidad de esfuerzo: pocos lo harán (es infraestructura, no brilla en video), el sponsor lo pidió explícitamente, y el riesgo técnico es bajo-medio (ingesta de eventos Soroban + Postgres/SQLite + API REST — sin ZK en el critical path). Doble dividendo para RAÍZ: tu roadmap ya contempla un watcher/indexador del barrio (dashboard, DePIN); esto lo financia y le da nombre. Contras: demo menos emotiva, y compite en la misma sub-lane que la wallet — es *o* esto *o* A/B/C aquí. Se mitiga con un buen video (opcional pero háganlo) mostrando el caso de fallo real.

### Opción E — descartada explícitamente: ideas fuera de las primitivas
Cualquier propuesta que no demuestre adopción práctica de CT o SPP (por ejemplo, solo el enjambre/mesh, o solo smart accounts P27) no califica en esta sub-lane por definición del bounty. El enjambre y la custodia FROST siguen siendo tu artillería para SCF — no para esta cancha.

---

## 6. Comparativa

| Criterio (1–5) | A · Sobre del Barrio | B · Wallet CT pura | C · SPP móvil | D · Indexador |
|---|---|---|---|---|
| Encaje con el bounty | 5 | 5 | 5 | 5 |
| Originalidad / anti-genérico | **5** | 3 | 4 | 4 |
| Factibilidad en 4 días | 3 | 3.5 | **2** | **4.5** |
| Reuso de activos RAÍZ | **5** | 4 | 3 | 3.5 |
| Afinidad con los jueces | 5 (OZ: CT + view keys + su SmartAccountKit) | 4 | 5 (Nethermind) | 5 (Nethermind lo pidió) |
| Valor post-evento para RAÍZ | **5** (F6 adelantada + SCF) | 4 | 4 (spike F6) | 4.5 (watcher del roadmap) |
| Probabilidad de premio (subjetiva) | Alta | Media | Media-baja | **Alta** |

---

## 7. Recomendación

**Plan primario: Opción A**, presentada con nombre propio y una línea que los jueces recuerden: *"Los aportes son secretos, el fondo es de vidrio"*. Con degradación planificada A→B si el tiempo aprieta (el trabajo es el mismo tronco). Es la única opción que junta las tres cosas que sobreviven al filtro de la sección 3: caso con identidad real, móvil-first, y uso profundo de las features del sponsor.

**Si el equipo son 3 personas y hay otra sub-lane compatible:** evaluar el indexador (D) o una entrada de smart accounts como segundo tiro *en otra sub-lane* — nunca dos en esta. Si el equipo son 2, un solo tiro bien dado vale más que dos medios tiros.

**Decisión a tomar antes de codificar (día 0):** validar en 2-3 horas el camino de proving de CT en un WebView Android real (cargar el demo oficial, generar una prueba en el teléfono). Si pasa → A a fondo. Si no pasa → decidir entre B-web con skin RAÍZ o pivotar a D, ese mismo día, sin duelo. Este experimento es la bisagra de todo el plan.

### Plan de choque sugerido (si eligen A)
- **Día 0 (hoy/mañana):** spike del proving en WebView (la bisagra). Clonar demo CT, desplegar wrapper CT sobre USDC testnet propio. Definir el contrato `goal_meta` mínimo.
- **Día 1:** contrato de metas + wrap/aporte CT funcionando por CLI. UI Android: pantalla de meta con total público y lista de aportantes sin montos.
- **Día 2:** flujo completo en el teléfono: passkey → aporte confidencial → total actualizado. View key de auditor publicada en la propia UI ("verifica el fondo tú mismo").
- **Día 3:** selective disclosure de un aporte (el "recibo" que un turista puede mostrar), pulido, README de calidad (los jueces leen el repo: arquitectura, qué es original, qué fricción encontraron en CT — con links a issues abiertos en el repo de OZ).
- **Día 4 (mañana del 6):** video demo de 2-3 min (grabación en dispositivo real, guion: problema → primitiva → demo → qué le falta a CT), submission antes de las 5 PM. Margen para el testnet flaky que ya conoces.

### Riesgos logísticos que no son técnicos
- La submission **abre cuando el evento esté activo** — confirmar en el venue cuándo exactamente, y no dejar el envío para las 4:50 PM.
- Regla presencial: todo el equipo registrado debe estar en el evento.
- CT y SPP son **testnet, sin auditar**: el pitch debe decirlo explícitamente (los jueces lo saben; fingir producción resta).
- Verificar la lista completa de sub-lanes del evento antes de decidir el segundo tiro.

---

## 8. Fuentes

- Bounty: capturas de GrantFox aportadas por el usuario (bounties.grantfox.xyz, evento Stellar Summit SP 2026).
- Confidential Tokens: [Developer Preview: Confidential Tokens on Stellar](https://stellar.org/blog/developers/developer-preview-confidential-tokens-on-stellar) · [OpenZeppelin stellar-contracts](https://github.com/OpenZeppelin/stellar-contracts)
- Stellar Private Payments: [Repo NethermindEth/stellar-private-payments](https://github.com/NethermindEth/stellar-private-payments) · [Docs SPP](https://nethermindeth.github.io/stellar-private-payments/) · [Anuncio open-source](https://x.com/StellarOrg/status/2022439480846651859)
- Marco de privacidad: [Privacy on Stellar — Stellar Docs](https://developers.stellar.org/docs/build/apps/privacy)
- Contexto de red: [Stellar Zipper, Protocol 27 Upgrade Guide](https://stellar.org/blog/foundation-news/stellar-zipper-protocol-27-upgrade-guide) · [RFP Track — SCF Handbook](https://stellar.gitbook.io/scf-handbook/scf-awards/build-award/rfp-track)
- Documentación RAÍZ: `propuesta_raiz_ahorro_enjambre.md` · `plan_trabajo_raiz.md` (aportados por el usuario).
