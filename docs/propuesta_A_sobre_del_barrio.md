# Propuesta A — "Sobre del Barrio"
## Aportes confidenciales a metas comunitarias · La primera wallet móvil de Confidential Tokens
### Special Bounty: Confidential-Token & Private-Payment Wallets · Stellar Summit SP 2026 · Team: Raiz Protocol

> **La línea que se recuerda:** *"Los aportes son secretos. El fondo es de vidrio."*
> (EN: *"Contributions are sealed. The fund is made of glass."*)

---

## 1. Pitch (30 segundos)

En un barrio, todos ven quién colabora — esa es la solidaridad. Pero cuánto da cada uno es asunto de cada quien: el que puede poco no debería sentir vergüenza, y el que puede mucho no debería exhibirse. **Sobre del Barrio** lleva esa norma social milenaria on-chain: una wallet Android donde vecinos y turistas aportan a metas comunitarias ("la escuela: 500 XLM para diciembre") mediante **Confidential Tokens** — el monto de cada aporte viaja cifrado, la lista de participantes es pública, y el **total del fondo es verificable por cualquiera** gracias a la view key de auditor de la meta, publicada abiertamente en la propia app.

Es el uso exacto para el que CT fue diseñado — contrapartes conocidas, montos ocultos — aplicado a un caso que ninguna nómina corporativa puede contar: **la privacidad como dignidad, la transparencia como confianza comunitaria.**

## 2. Por qué esto y no otra cosa

- **Usa la primitiva a fondo, no de adorno.** Cubrimos el ciclo completo de CT (register → deposit → merge → transfer → withdraw) y además las dos features que el 95% ignorará: *auditor view keys* (invertidas: el auditor es el público) y *selective disclosure* (el recibo del aportante).
- **Móvil-first.** El demo oficial de CT es navegador + Freighter, escritorio. La tesis de adopción de Stellar en LatAm es móvil. Nadie tiene CT en un teléfono; nosotros llegamos con una app Android ya construida (RAÍZ) a la que este bounty le añade su capa de privacidad.
- **No es una idea de hackathon, es un roadmap encontrándose con su momento.** El plan técnico de RAÍZ (escrito en julio) decía textualmente: *"esperar la salida de preview de los Confidential Tokens de OpenZeppelin antes que reimplementar Pedersen a mano"*. La preview salió el 30 de junio. Este bounty es esa espera terminándose — y podemos probarlo con documentos fechados.
- **Contabilidad aditiva pura.** El propio equipo de OZ señala que CT rinde en "additive accounting" y no en price discovery/composability. Sumar aportes a una meta ES contabilidad aditiva — estamos en el centro del sweet spot declarado de la primitiva, no en sus bordes.

## 3. Cómo funciona (historias de usuario)

**La vecina (Marta):** abre la app con su passkey, ve la meta "Techo de la casa comunal — 62% alcanzado", toca *Aportar*, elige un monto, confirma. On-chain queda: Marta aportó a la meta (visible), monto: cifrado. Su balance confidencial baja; nadie más puede leerlo.

**El turista (Jonas):** paga su tour, la app le ofrece "deja un aporte a la meta del barrio". Deposita XLM público → balance confidencial (deposit), aporta (transfer confidencial). Al volver a casa puede generar un **recibo verificable** de su aporte (selective disclosure) — para él, para nadie más, a menos que él quiera mostrarlo.

**El barrio (cualquiera, sin cuenta):** la pantalla pública de la meta muestra el total recaudado y un botón *"Verifícalo tú mismo"*: la view key de auditor de la cuenta-meta está publicada en la app y en el README. Cualquier persona — un vecino, un juez del summit, un periodista — puede descifrar el balance de la meta de forma independiente. **El fondo común jamás se oculta.**

**El momento "merge" como UX:** en CT, lo recibido queda *pendiente* hasta ejecutar merge. En vez de esconder esa fricción, la convertimos en ritual de producto: la meta "cosecha" los aportes acumulados (un tap del custodio, visible en el timeline público como evento). La fricción de la primitiva, narrada, se vuelve feature.

## 4. Arquitectura

```
┌──────────────────── App Android (Kotlin, base RAÍZ) ────────────────────┐
│  Pantallas: Metas · Aportar · Mi Sobre (balance CT) · Verificador        │
│  Passkey auth (OZSmartAccountKit — mismo stack del sponsor)              │
│  Firma de tx: wallet manager existente de RAÍZ                           │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │ WebView "prover" aislado: stack JS/WASM del demo oficial de CT     │  │
│  │ genera las pruebas ZK LOCALMENTE en el teléfono (bridge JS↔Kotlin) │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────────────┘
                             │
┌────────────────────────────▼──────────── Soroban (testnet) ─────────────┐
│  CT wrapper (OpenZeppelin, tal cual — no lo tocamos)  ◀── XLM/SAC        │
│  goal_meta (NUESTRO): registro de metas {nombre, objetivo, deadline,     │
│    cuenta-meta, view key publicada, timeline de eventos de cosecha}      │
│  (post-evento: cuenta-meta = smart account comunal de RAÍZ F3)           │
└──────────────────────────────────────────────────────────────────────────┘
```

**Qué es reusado y qué es original (declararlo protege ante la regla "100% original work"):**
- Reusado, con crédito explícito: contratos CT de OpenZeppelin (sin modificar), su stack de proving JS/WASM dentro del WebView, base de app RAÍZ (nuestra, pre-existente — se declara).
- Original de este hackathon: el contrato `goal_meta`, toda la capa móvil de CT (bridge WebView↔Kotlin, gestión de estado CT en Android), el patrón "view key pública como transparencia comunitaria", las pantallas de metas/sobre/verificador, y el informe de fricción de integración para OZ.

## 5. Alcance en 4 días — con líneas de corte

**MUST (sin esto no hay submission):**
- Ciclo CT completo desde el teléfono: register, deposit (XLM→confidencial), transfer a la meta, merge de la meta, balance descifrado en el dispositivo.
- Pantalla de meta con total público + view key publicada + botón de verificación.
- Repo limpio con README en inglés + instrucciones de reproducción en testnet.

**SHOULD (ganan el bounty):**
- Contrato `goal_meta` desplegado (si se atasca: una meta hardcodeada con la misma UX — se degrada sin perder la demo).
- Selective disclosure: el recibo del aportante.
- Video demo de 2-3 min (formalmente opcional; en la práctica, decisivo).

**COULD (solo si sobra tiempo — resistir la tentación):**
- Withdraw completo de vuelta a XLM público.
- Aporte del turista integrado al flujo de pago RAÍZ existente.
- Segunda meta + listado.

**Degradación planificada (sin duelo, decidida el día 0):**
- **A → B:** si `goal_meta` o la UX de metas consumen demasiado, se entrega "primera wallet CT móvil" pura (balance privado + send/receive) — todo el trabajo del tronco se conserva.
- **A → web:** si el WebView no rinde en el teléfono pero sí en navegador móvil, PWA con la misma UI (sigue siendo "mobile", honesto en el README).
- **A → D:** si el proving no corre en móvil de ninguna forma o los contratos CT de testnet están inestables → pivote al indexador (propuesta D, documento aparte).

## 6. Día 0 — el spike bisagra (timebox: 3 horas)

Objetivo: validar el ÚNICO riesgo que mata la propuesta. Todo lo demás es trabajo conocido.

1. (30 min) Abrir el demo oficial de CT (link en el blog del developer preview de Stellar, 30-jun-2026) en **Chrome de un teléfono Android de gama media**. Intentar register + deposit + transfer con XLM de testnet (Freighter no existe en móvil: si el demo lo exige para firmar, evaluar si separa firma de proving — lo que nos importa medir es el *proving*).
2. (60 min) Si el paso 1 es ambiguo: clonar el demo, servirlo localmente, cargarlo en un WebView Android básico y disparar la generación de una prueba. Medir tiempo y memoria.
3. (30 min) Registrar: tiempo por prueba, picos de RAM, crashes.

**Criterio GO (→ A):** las 3 pruebas del flujo básico se generan en el teléfono, <90s cada una, sin OOM.
**Criterio GO parcial (→ A en PWA):** corre en Chrome móvil pero el WebView embebido falla → misma app como PWA.
**Criterio NO-GO (→ D):** proving >3 min por operación, OOM sistemático, o contratos CT de testnet caídos/reseteados. Se abre `propuesta_D_indexador_respaldo.md` y no se mira atrás.

Registrar TODO lo que falle con detalle: ese log es el "informe de fricción" que se convierte en issues al repo de OZ y en una sección del README — ante estos jueces, la fricción documentada es puntaje, no vergüenza.

## 7. Plan día a día

| Día | R1 (Protocolo) | R2 (App) | Ambos/R3 |
|---|---|---|---|
| **0 (hoy)** | Spike WebView (bisagra) · desplegar wrapper CT propio sobre XLM testnet si el oficial es inestable | Esqueleto de pantallas Metas/Sobre | Decisión GO/NO-GO escrita |
| **1** | `goal_meta` v0 (crear meta, registrar cuenta-meta, evento de cosecha) + flujo CT por CLI end-to-end | Bridge WebView↔Kotlin: deposit + transfer desde la app | — |
| **2** | Merge + view key de la meta publicada y verificable por un script independiente | Flujo completo en el teléfono con passkey · pantalla de verificación | Ensayo de demo en frío |
| **3** | Selective disclosure (recibo) · hardening · congelar features a las 18:00 | Pulido UX · estados de error (testnet flaky: reintentos, mensajes honestos) | README final EN · issues de fricción a OZ |
| **4 (6-ago)** | — | — | Video 2-3 min en dispositivo real · submission enviada ANTES del mediodía (no a las 4:50 PM) |

## 8. Guion del video (2:30)

1. **(0:00–0:25) El problema, con rostro:** "En nuestros barrios todos colaboran. Pero en una lista pública de montos, el que da poco siente vergüenza y el que da mucho se expone. La transparencia total es una virtud de sistemas — y una crueldad social."
2. **(0:25–0:50) La primitiva:** "Confidential Tokens de OpenZeppelin: montos cifrados, identidades visibles. Exactamente la norma social del barrio, en criptografía. Nosotros lo pusimos donde vive la gente: un teléfono."
3. **(0:50–1:50) Demo en vivo:** aporte de Marta (monto oculto en el explorer — mostrar el explorer), total de la meta actualizado tras "cosechar", botón *Verifícalo tú mismo* descifrando el balance de la meta con la view key pública desde un script fuera de la app.
4. **(1:50–2:15) El recibo:** selective disclosure del aporte del turista.
5. **(2:15–2:30) Cierre:** "Los aportes son secretos. El fondo es de vidrio. Sobre del Barrio, por Raiz Protocol — construido sobre CT en testnet, primera wallet móvil de Confidential Tokens. Nuestro informe de fricción ya está en issues del repo de OpenZeppelin."

## 9. Texto de submission (inglés, listo para pegar)

> **Sobre del Barrio — sealed contributions, glass-box community funds. The first mobile Confidential Tokens wallet.**
>
> In Latin American neighborhoods, everyone sees who contributes to a common cause — that's solidarity. How *much* each person gives is nobody's business — that's dignity. Sobre del Barrio ("the neighborhood envelope") encodes this social norm with OpenZeppelin's Confidential Tokens: an Android wallet where neighbors and tourists contribute to community goals with **encrypted amounts and visible participation**, while the goal account's **auditor view key is published openly** so *anyone* can independently verify the fund's total. Individual privacy, collective transparency — the exact inversion of how auditor keys were imagined, and exactly what a community needs.
>
> We implement the full CT cycle (register, deposit, merge, transfer, selective-disclosure receipts) running **on-device** — proofs are generated locally on the phone via the CT proving stack in an isolated WebView, bridged to our Kotlin app. Built by Raiz Protocol on our existing community-savings codebase (Soroban contracts + Android app with OZ smart-account passkeys). Our roadmap, written in July, literally said "wait for the OZ Confidential Tokens preview" — this bounty is that wait, ending.
>
> Original work in this repo: the `goal_meta` Soroban contract, the entire mobile CT layer, the public-view-key transparency pattern, and an integration-friction report filed as issues against the CT repo. Testnet only, unaudited primitives — clearly flagged in-app.
>
> Repo: [link] · Demo video: [link] · Friction report: [links a issues]

## 10. Honestidad técnica (va en el README, sección "Limitations")

- CT es developer preview, **solo testnet, sin auditar** — la app lo dice en pantalla; nada de esto toca dinero real.
- La view key de la meta la publica el custodio de la meta; en esta demo el custodio es el equipo. El diseño final de RAÍZ la pone bajo la smart account comunal (F3) — enlazamos la propuesta, no fingimos que ya existe.
- Las identidades de los aportantes son visibles por diseño de CT (eso es feature aquí, no bug — y se explica). Quien necesite ocultar también la identidad necesita SPP, no CT: lo decimos.
- Proving móvil vía WebView es un puente pragmático, no la solución final (la final es proving nativo tipo mopro cuando el stack Noir móvil madure — está en nuestro roadmap F6).

## 11. Mapa contra el bounty

| El bounty pide | Sobre del Barrio entrega |
|---|---|
| "Private balance display… balances and amounts hidden on-chain, readable in-wallet" | Sí — "Mi Sobre", descifrado en el dispositivo |
| "private send/receive" | Sí — aporte confidencial + merge + (COULD) withdraw |
| "practical adoption of Confidential Tokens" | Caso de uso con comunidad real detrás, no demo sintética |
| "Projects may target end users" | Usuarios finales concretos: vecinos y turistas, en móvil |
| Repo GitHub | Limpio, reproducible, EN, con informe de fricción |
| Video (opcional) | 2:30, dispositivo real |

## 12. Después del summit (por qué esto no muere el 6 de agosto)

Este prototipo es la **Fase 6.2 de RAÍZ adelantada seis meses**: aportes privados con agregado público. Alimenta directamente la aplicación SCF de octubre (evidencia de "surfeamos cada upgrade de la red: P27 → custodia, CT preview → ahorro privado"), abre la puerta a ser design partner de OpenZeppelin (el blog los busca explícitamente entre participantes de hackathones), y el informe de fricción nos da presencia en su repo. Gane o no gane el bounty, el trabajo queda en el critical path del proyecto — esa es la definición de una buena apuesta de hackathon.
