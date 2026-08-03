---
name: friccion
description: Registrar una fricción con Confidential Tokens, SPP o el RPC en friction-report.md con el error textual (regla #2 de CLAUDE.md — la fricción documentada suma puntos ante estos jueces). Usar apenas ocurra el error, no al final del día. Argumento - descripción breve de lo que pasó.
---

# Registrar fricción

Añade una entrada a `friction-report.md` (raíz del repo) con este formato exacto:

```markdown
## [YYYY-MM-DD HH:mm] — [componente: CT | SPP | RPC | stellar-cli | otro] — [título corto]
- **Contexto:** qué intentábamos hacer (comando/operación exacta)
- **Error textual:**
  ```
  (el mensaje COMPLETO, copiado, sin parafrasear)
  ```
- **Versión/commit:** versión de la herramienta o commit del repo en /vendor
- **Esperado vs. observado:** una línea cada uno
- **Workaround:** qué hicimos para seguir (o "ninguno aún")
```

Reglas:
1. El error va TEXTUAL. Si no tienes el texto exacto, vuelve a reproducirlo o
   dilo explícitamente ("mensaje perdido, repro pendiente").
2. Una entrada por fricción, no mezcles varias en una.
3. Si la fricción bloquea un MUST: además, anótala como bloqueante en
   CLAUDE.md → ESTADO ACTUAL → "Bloqueantes abiertos".
4. NO abras issues en GitHub — eso pasa el día 3 tras revisión humana
   (los borradores viven en /docs/issues-drafts/, Sesión 7).
5. Tras registrar, vuelve a la tarea que estabas haciendo. El registro no es
   excusa para cambiar de foco.
