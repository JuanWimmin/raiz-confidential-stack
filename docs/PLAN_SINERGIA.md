# Plan de sinergia A+D — "Sobre del Barrio × Raiz Memory"
## Una sola submission, dos piezas que se necesitan

> **La sinergia en una frase:** la wallet reconstruye su timeline de aportes leyendo eventos; el RPC los olvida a los 7 días; Raiz Memory es la memoria que la wallet consulta. En la demo: se "purga" el RPC, la wallet oficial muere, la nuestra recuerda.
>
> **Regla que lo gobierna todo:** 1 submission por sub-lane → esto es UN proyecto: *"Sobre del Barrio — the wallet that remembers"*. Cubre los ejemplos (a) wallet CT y (c) indexer del bounty en una sola entrada.

---

## El árbol de decisión (día 0)

```
Spike proving CT en móvil (3h, criterios en propuesta A §6)
├── GO       → Plan completo: wallet + indexador (este documento)
├── GO parcial (solo Chrome móvil, no WebView)
│            → Wallet como PWA + indexador (mismo plan, R2 ajusta)
└── NO-GO    → Raiz Memory queda como submission única (propuesta D),
               y R2 libera manos para el despliegue público + video
```

**En los tres caminos, Raiz Memory se construye.** Por eso arranca hoy, en paralelo al spike, sin esperar el resultado.

## Punto de integración concreto (el corazón de la sinergia)

1. La pantalla de la meta muestra un **timeline de aportes** (quién y cuándo — nunca cuánto) y los eventos de "cosecha" (merge).
2. Ese timeline se alimenta de `GET /events` de Raiz Memory — **no** del RPC directo. La URL es un parámetro de la app: cambiarla entre RPC y Raiz Memory en vivo ES la demo de la sinergia.
3. Raiz Memory indexa dos contratos desde el día 1: el **CT wrapper** (aportes) y el **goal_meta** (metas y cosechas). Añadir el pool SPP es 1 línea de config — se menciona en README como "works for SPP wallets too", que es lo que Nethermind quiere leer.
4. Momento de la demo (día 2 se ensaya): retención simulada corta en un RPC-proxy → la vista por RPC pierde el historial viejo → se apunta a Raiz Memory → el timeline completo vuelve. *"Una wallet privada que olvida es una wallet que pierde plata."*

## Reparto por roles (2–3 personas, 4 días)

| Día | R1 · Protocolo | R2 · App Android | R3 (o mitad de R1) · Raiz Memory |
|---|---|---|---|
| **0 (hoy)** | Spike proving (con R2) · desplegar wrapper CT propio en testnet · decisión GO/NO-GO escrita | Spike proving (con R1) · esqueleto pantallas Metas/Sobre | `cargo run` del esqueleto: ingesta de un contrato cualquiera de testnet funcionando |
| **1** | `goal_meta` desplegado · flujo CT completo por CLI (register→deposit→transfer→merge) | Bridge WebView↔Kotlin: primera prueba generada DESDE la app | Cursor persistente + `/events` compatible + indexando CT wrapper y goal_meta reales |
| **2** | View key de la meta + script verificador independiente · selective disclosure | Flujo completo con passkey en el teléfono · **integración: timeline vía Raiz Memory** | `/coverage` + gaps · despliegue público (VM) · ensayo del momento "purga" |
| **3** | Hardening · congelar features 18:00 · issues de fricción a los repos de OZ/Nethermind | Pulido UX · estados de error honestos | Docker-compose final · README EN · instancia pública estable |
| **4 (6-ago)** | — Video 2:30 (guion en propuesta A §8 + escena de la purga de D §3) · README raíz final · **submission antes del mediodía** — | | |

Con 2 personas: R3 = R1 medio tiempo; el indexador recorta a MUST (ingesta + /events + demo de purga) y el despliegue público pasa a COULD.

## Los MUST combinados (lo mínimo que se envía)

1. Ciclo CT en el teléfono (o PWA): register, deposit, transfer a la meta, merge, balance descifrado en dispositivo.
2. Total de la meta verificable con view key publicada (script independiente incluido en el repo).
3. Raiz Memory indexando CT + goal_meta, `GET /events` compatible, y la demo de purga/recuperación grabada.
4. Repo único (monorepo con `/wallet`, `/contracts`, `/raiz-memory`), README EN, video 2:30.

## Advertencias operativas

- **Confirmar en el venue** si el trabajo debe realizarse durante la ventana del evento. El kit de hoy son esqueletos e infraestructura propia (RAÍZ pre-existe y se declara); la regla de "100% original work" se protege con la sección *Reused vs. Original* del README — mantenerla al día.
- La submission abre cuando el evento esté activo; enviar antes del mediodía del 6.
- Testnet flaky conocido: toda demo se graba el día que funciona. El video es el seguro de vida.
- No tocar los contratos CT de OpenZeppelin: se consumen tal cual. Nuestro código original es goal_meta, la capa móvil, el patrón view-key-pública y Raiz Memory.
