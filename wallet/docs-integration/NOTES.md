# Notas de integración Android (R2)

## Las tres pantallas del MVP (reusar componentes RAÍZ)

1. **Meta** (pública, sin login): nombre, objetivo, % alcanzado, timeline de aportes
   (dirección abreviada + fecha — NUNCA montos) y cosechas. Botón *"Verifícalo tú mismo"*
   → muestra la view key y el comando/script para descifrar el total por fuera de la app.
   Fuente de datos: `GET /events` de **Raiz Memory** (URL configurable — cambiarla en vivo
   entre RPC y Raiz Memory es la escena de la demo).
2. **Mi Sobre**: balance CT descifrado en el dispositivo + botón Depositar (XLM→confidencial)
   y Aportar (transfer confidencial a la cuenta-meta). Cada operación: `ProverWebViewBridge`
   genera la prueba → wallet manager RAÍZ firma y envía.
3. **Cosechar** (solo admin de la meta): ejecuta el merge CT + `goal_meta.record_harvest`.
   En UI se llama "cosecha" — la fricción del merge, narrada como ritual.

## Decisiones ya tomadas (no reabrir durante el evento)

- Passkey = auth de la app (stack existente). NO prometer passkey-como-firmante-CT
  en 4 días; se menciona como roadmap (F3/F6) en el README.
- El estado CT del usuario (randomness/claves de descifrado que la primitiva requiera)
  vive en Kotlin (EncryptedSharedPreferences / Keystore), pasado al WebView por llamada.
  El WebView no persiste nada.
- Timeouts y errores en español y honestos: "testnet está lenta, reintentando (3/5)…"
  es mejor demo que un spinner infinito.

## El guion de cambio de URL (escena de sinergia, ensayar el día 2)

1. Timeline apuntando al RPC-proxy con retención simulada corta → los aportes viejos
   desaparecen del timeline ("el RPC olvidó").
2. Settings → Event Source → `https://memory.raiz.xyz` (Raiz Memory).
3. Pull-to-refresh → historial completo. Frase del video: *"la wallet que recuerda"*.
