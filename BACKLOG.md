# BACKLOG — ideas fuera del sprint

> Regla de oro: toda idea nueva viene AQUÍ, nunca al sprint. Se revisa DESPUÉS
> de la submission (6-ago). Este archivo alimenta el SCF de octubre.

| Fecha | Idea | Origen | Notas |
|---|---|---|---|
| 2026-08-03 | raiz-memory: backfill opcional por contrato (`CONTRACT_START_LEDGERS`) — hoy el primer arranque empieza en el ledger actual (ingest.rs:33-38) y los eventos previos del contrato, aunque sigan vivos en la ventana RPC, no se archivan | Sesión 4 (la DB dedicada del test no vio los eventos del run 1) | Mitigación actual: arrancar la instancia de producción apenas se despliega un contrato |
| 2026-08-03 | goal_meta: añadir `set_view_key` y `archive_goal` (hoy no hay setter — el goal 0 placeholder quedó muerto y hubo que crear el goal 1) | Sesión 7 | Candidato a Sesión 8 si sobra tiempo; con tests de auth |
| 2026-08-03 | receipt: pasar Raiz Memory como `indexer` param del verificador de disclosure (hoy relee eventos del RPC directo) | Sesión 7 | 1 línea de config; refuerza la sinergia |
