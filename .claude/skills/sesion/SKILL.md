---
name: sesion
description: Arrancar una sesión de trabajo numerada del plan (0-9) o el prompt de emergencia. Lee el prompt correspondiente de PROMPTS_CLAUDE_CODE.md, verifica prerequisitos contra ESTADO ACTUAL de CLAUDE.md y ejecuta. Argumento - número de sesión (0-9) o "emergencia".
---

# Arrancar Sesión N

1. Lee `CLAUDE.md` → ESTADO ACTUAL. Verifica:
   - ¿Las sesiones previas de las que depende esta están marcadas [x]?
   - ¿Hay bloqueantes abiertos que la afecten?
   - Si la sesión requiere la decisión del spike (Sesiones 4-7 requieren GO) y
     está PENDIENTE o NO-GO: dilo y detente — no ejecutes sobre una premisa falsa.
2. Lee el prompt de la sesión pedida en `PROMPTS_CLAUDE_CODE.md` y tómalo como
   la orden de trabajo COMPLETA. Sus límites de alcance son ley
   ("No hagas NADA más allá de esto" significa eso).
3. Sesiones 3, 5 y 6: son las grandes — propone primero un plan corto (qué vas a
   tocar, en qué orden, qué puede salir mal) y espera el OK del usuario antes de
   ejecutar, salvo que él ya haya dicho "ejecuta directo".
4. Durante la sesión aplican las reglas permanentes:
   - API externa → verificar en /vendor primero (agente vendor-scout) o llamada
     real (agente testnet-prober). Nunca de memoria.
   - Fricción → skill /friccion apenas ocurra.
   - Idea nueva → skill /al-backlog.
   - Timebox reventado → BACKLOG.md + recorte según árbol de degradación.
5. Al terminar (o al agotar el timebox): ejecuta el skill /cierre. Una sesión
   sin cierre no cuenta como sesión.
