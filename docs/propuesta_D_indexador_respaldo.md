# Propuesta D (respaldo) — "Raiz Memory"
## Indexador durable de eventos para wallets privadas en Stellar (CT + SPP)
### Special Bounty: Confidential-Token & Private-Payment Wallets · Stellar Summit SP 2026 · Team: Raiz Protocol

> **Cuándo se activa este documento:** solo si el spike del día 0 da NO-GO (proving CT inviable en móvil o contratos de testnet inestables). Criterios exactos en `propuesta_A_sobre_del_barrio.md` §6.
>
> **La línea:** *"Una wallet privada que olvida es una wallet que pierde plata. Nosotros somos la memoria."*

---

## 1. El problema (confesado por el propio sponsor)

El README de Stellar Private Payments lo dice sin anestesia: los nodos RPC solo retienen eventos **7 días**. Una wallet SPP reconstruye sus notas (sus fondos) escaneando eventos; quien pierda su estado local — teléfono nuevo, navegador limpiado, usuario que llega tarde — y no tenga los eventos históricos, **no puede recuperar acceso a su dinero**. Lo mismo aplica a cualquier wallet CT que necesite reconstruir su historial de transferencias cifradas. Por eso el bounty lista, textual, como tercer ejemplo: *"Indexer: a durable event index other builders can point a wallet at, covering history past the RPC's 7-day window."*

No es una idea nuestra. Es Nethermind pidiendo la pieza que le falta. Nosotros la construimos bien.

## 2. Qué entregamos

**Raiz Memory**: un servicio en Rust que ingiere continuamente los eventos de los contratos CT y SPP en testnet, los persiste para siempre, y expone una API que cualquier wallet puede consumir como reemplazo drop-in de `getEvents` — más allá de la ventana de 7 días.

```
┌───────────── Raiz Memory (Rust) ─────────────┐
│  Ingestor: poll a Soroban RPC getEvents       │
│    por contract_id (CT wrapper, SPP pool)     │
│    · cursor persistente · reintentos          │
│    · detección y reporte de gaps              │
│  Store: SQLite/Postgres                       │
│    eventos crudos: ledger_seq, tx_hash,       │
│    topics XDR, data XDR, timestamp            │
│  API (axum):                                  │
│    GET /events?contract=&start_ledger=&cursor │
│      (forma compatible con getEvents del RPC) │
│    GET /health · GET /coverage (qué rangos    │
│      de ledger cubrimos, con gaps declarados) │
│  docker-compose up → corriendo en 1 comando   │
└───────────────────────────────────────────────┘
```

Decisiones de diseño que nos diferencian de un script:

- **Compatibilidad de forma con `getEvents`:** una wallet apunta su URL de RPC de eventos a Raiz Memory y no cambia ni una línea más. Adopción = cambiar una constante.
- **Gaps declarados, nunca silenciosos:** `/coverage` responde qué rangos de ledger están completos. Un indexador que miente por omisión es peor que ninguno — esto es infraestructura para dinero.
- **Eventos crudos (XDR), no interpretados:** no descifra ni interpreta nada (no podría — están cifrados; eso es exactamente por qué el indexador puede ser un tercero sin comprometer la privacidad, y lo decimos en el README: *"we index ciphertext; your privacy budget is untouched"*).
- **Sin estado del usuario:** cero custodia, cero conocimiento de notas o claves.

## 3. La demo teatral (esto gana o pierde el premio)

Guion del video (2:00):

1. **(0:00–0:20)** "Esta es la wallet SPP oficial funcionando. Y esto es lo que pasa cuando el RPC olvida" — config del indexador con retención simulada de minutos en lugar de 7 días.
2. **(0:20–1:00)** Wallet A deposita. Se "pierde" el estado local (limpiar storage en cámara). El RPC ya purgó los eventos → la wallet no puede reconstruir sus notas. **Pantalla de fondos inaccesibles.**
3. **(1:00–1:40)** Misma wallet, apuntada a Raiz Memory (cambiar una URL). Re-escaneo completo desde el ledger génesis del pool → notas reconstruidas → fondos de vuelta.
4. **(1:40–2:00)** `/coverage` en vivo: rangos, gaps (ninguno), uptime. "Cualquier wallet de esta sala puede apuntar aquí ahora mismo: [URL pública]. Raiz Memory, por Raiz Protocol."

**El toque final:** dejarlo **desplegado y público durante el summit** (una VM barata basta) e invitar a los demás equipos de la sub-lane a usarlo. Si otro equipo lo usa en SU demo, ganamos dos veces: es la definición de "other builders can point a wallet at".

## 4. Alcance en ~3 días

**MUST:** ingestor con cursor persistente + store + `GET /events` compatible + demo del caso de fallo/recuperación con la wallet SPP + README EN + docker-compose.
**SHOULD:** `/coverage` con gaps · despliegue público durante el evento · video.
**COULD:** ingesta también del wrapper CT (segundo contrato — el diseño ya es multi-contrato) · backfill desde un archivo histórico si testnet lo permite · métricas Prometheus.

| Día | Trabajo |
|---|---|
| **0 (tarde, post-pivote)** | Esqueleto Rust (axum + sqlx) · poll de getEvents contra el contrato SPP de testnet · esquema de DB |
| **1** | Cursor persistente + reintentos (testnet flaky: ya lo conocemos) + `GET /events` compatible · primera reconstrucción de la wallet SPP apuntada al indexador |
| **2** | `/coverage` + gaps · despliegue público · caso de fallo reproducible y grabable · README EN |
| **3 (6-ago)** | Video 2:00 · pulido · submission antes del mediodía |

Riesgo técnico honesto: bajo-medio. Sin ZK en el critical path; los enemigos son el testnet flaky (conocido: reintentos + demo grabada como respaldo) y la forma exacta del JSON de `getEvents` (resuelto con tests contra el RPC real el día 0).

## 5. Texto de submission (inglés, listo para pegar)

> **Raiz Memory — the durable event index private wallets on Stellar are missing.**
>
> SPP's own docs state the constraint plainly: RPC nodes retain events for ~7 days. A privacy wallet reconstructs its notes from events — lose your local state after that window and your funds become unreachable. This bounty's brief asks for exactly the missing piece: *"a durable event index other builders can point a wallet at."* We built it, deployed it, and kept it running publicly throughout the summit.
>
> Raiz Memory is a Rust service that continuously ingests events from the SPP pool (and CT wrapper) contracts and serves them through a getEvents-shaped API — a wallet adopts it by changing one URL. It indexes ciphertext only (it *can't* read your amounts — that's the point), declares its coverage and any gaps honestly via `/coverage`, and ships as a one-command docker-compose. Our demo shows the real failure: a wallet losing its notes past the retention window, then fully recovering by re-scanning against Raiz Memory.
>
> Built by Raiz Protocol — a community-savings protocol on Stellar — because our own roadmap needs durable neighborhood-level indexing; this bounty funded the first brick. Testnet, MIT-licensed, live at [URL].
>
> Repo: [link] · Demo video: [link] · Live instance: [URL]

## 6. Valor post-evento

El watcher/indexador de barrio ya estaba en el roadmap de RAÍZ (dashboard de transparencia, recompensas DePIN, "testigo del barrio"). Raiz Memory es su primer ladrillo con nombre, financiado por el bounty y con el sello de "lo pidió Nethermind". Además: es el tipo de infraestructura que el SCF financia como proyecto independiente si crece (indexador durable genérico de eventos Soroban — el problema de los 7 días no es exclusivo de SPP).
