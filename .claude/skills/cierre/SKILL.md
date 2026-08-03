---
name: cierre
description: Ritual de cierre de sesión del proyecto (regla #3 de CLAUDE.md) — actualizar ESTADO ACTUAL en CLAUDE.md, registrar fricción pendiente, commitear en inglés y anotar el siguiente paso concreto. Usar al terminar cada sesión de trabajo o cuando el usuario diga "cierra la sesión" / "actualiza estado y commitea".
---

# Cierre de sesión

Ejecuta EN ORDEN, sin saltarte pasos:

1. **Inventario honesto:** `git status` + `git diff --stat`. ¿Qué se logró
   REALMENTE en esta sesión? No reportes intenciones como hechos. Si algo quedó
   a medias, se reporta a medias.
2. **CLAUDE.md → ESTADO ACTUAL:**
   - Marca [x] solo los hitos completados y verificados HOY, con sus datos
     medidos (contract ids, tiempos de proving, contratos indexados…).
   - "Última actualización": fecha de hoy.
   - "Bloqueantes abiertos": lista honesta, o "ninguno".
   - "Siguiente paso concreto": UNA acción específica y ejecutable (comando,
     archivo, decisión), no una intención vaga.
3. **Fricción:** si hubo CUALQUIER fricción con CT/SPP/RPC no registrada aún,
   añádela a friction-report.md con el error textual ANTES de commitear (regla #2).
4. **Ideas sueltas:** todo lo que surgió y no es MUST/SHOULD → BACKLOG.md.
5. **Commit:** mensaje en INGLÉS, modo imperativo, específico (qué y por qué).
   Prohibido "wip", "updates", "fixes". Un commit por unidad lógica si hay varias.
6. **Reporte al usuario:** qué quedó hecho, qué quedó abierto, y el siguiente
   paso tal como quedó anotado en CLAUDE.md.
