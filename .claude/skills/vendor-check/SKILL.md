---
name: vendor-check
description: Verificar que un símbolo/API/función existe en el código real de /vendor antes de usarlo (regla #1 de CLAUDE.md). Usar cuando el usuario diga "verifícalo en /vendor" o antes de escribir código contra CT/SPP/stellar-contracts. Argumento - el símbolo o la pregunta de API.
---

# Verificación en /vendor

1. Lanza el agente `vendor-scout` con la pregunta exacta (símbolo, firma esperada,
   contexto de uso previsto).
2. Con el veredicto:
   - **EXISTE:** cita la evidencia file:line en tu respuesta y procede a usar la
     firma REAL reportada (no la que imaginabas).
   - **EXISTE CON OTRA FIRMA:** adapta tu plan a la firma real. Si el cambio
     invalida una decisión de diseño, dilo antes de seguir.
   - **NO EXISTE:** NO escribas código contra ese símbolo. Opciones en orden:
     (a) usar el símbolo alternativo real que reportó el scout,
     (b) llamada real de prueba con el agente testnet-prober si es cosa del RPC,
     (c) registrar la carencia con /friccion y aplicar el fallback documentado
         en la propuesta correspondiente.
3. Si /vendor está vacío o el repo relevante no está clonado: eso es un
   bloqueante de Sesión 0 — repórtalo, no lo rodees inventando.
