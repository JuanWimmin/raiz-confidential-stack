package xyz.raiz.sobre.ui.meta

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.data.EventSource
import xyz.raiz.sobre.data.TimelineEntry
import xyz.raiz.sobre.ui.components.AporteRow
import xyz.raiz.sobre.ui.components.GoalProgressBar
import xyz.raiz.sobre.ui.components.PhaseBanner
import xyz.raiz.sobre.ui.components.ProofProgress
import xyz.raiz.sobre.ui.components.StatBox
import xyz.raiz.sobre.ui.components.VerifyRow
import xyz.raiz.sobre.ui.nav.asRelativeEs
import xyz.raiz.sobre.ui.nav.formatXlm
import xyz.raiz.sobre.ui.nav.shortAddr
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.util.StellarExpert
import xyz.raiz.sobre.wallet.CtConfig

/**
 * SCREEN 1 — "Meta". Public, no login, and the first thing the app shows.
 *
 * It has to carry the whole pitch on its own:
 *  - the fund total, shown ONLY when the app has re-verified it against the
 *    on-chain commitments with the published view key;
 *  - a timeline of *aportes* that names the contributor and the moment and
 *    deliberately never names an amount — because the event carries none;
 *  - the view key itself, plus the command to check the total without us;
 *  - a source switch that swaps a durable index for a forgetful RPC, live.
 */
@Composable
fun MetaScreen(
    state: MetaUiState,
    onRefresh: () -> Unit,
    onSelectSource: (String) -> Unit,
    onVerifyTotal: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val t = state.timeline

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp + contentPadding.calculateTopPadding(),
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("hero") { TotalHero(state, onVerifyTotal) }

        item("progreso") {
            if (state.total is TotalState.Verified) {
                GoalProgressBar(
                    reachedPct = state.reachedPct,
                    caption = "${state.total.stroops.formatXlm()} de ${state.targetStroops.formatXlm()}",
                    trailing = "${state.reachedPct}%",
                )
            }
        }

        // Two boxes, not three: "Aportantes" wraps to two lines at a third of a
        // 720px screen (measured on the Vivo Y21). The harvest count lives in
        // the section header below, where it has room.
        item("stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    label = "Aportantes",
                    value = t.contributorCount.toString(),
                    accent = RaizPurple,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = "Aportes",
                    value = t.contributionCount.toString(),
                    accent = RaizGreen,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item("fuente") {
            SourceCard(
                state = state,
                onSelectSource = onSelectSource,
                onRefresh = onRefresh,
            )
        }

        item("timeline-head") {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Historial de aportes · ${t.harvestCount} cosechas",
                    style = MaterialTheme.typography.labelLarge,
                    color = RaizBlack,
                    modifier = Modifier.padding(top = 8.dp),
                )
                // The one line a judge needs to read to understand the design.
                Text(
                    text = "Quién y cuándo, nunca cuánto: el evento on-chain no lleva " +
                        "el monto. No falta — está cifrado.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = RaizBlack.copy(alpha = 0.6f),
                )
            }
        }

        when {
            state.loading -> item("cargando") { CargandoBox("Leyendo eventos…") }

            state.error != null -> item("error") {
                PhaseBanner(
                    title = "No pudimos leer los eventos",
                    body = state.error,
                    accent = RaizError,
                    cta = "Reintentar",
                    onAction = onRefresh,
                )
            }

            t.isEmpty -> item("vacio") { TimelineVacio(state) }

            else -> items(t.entries, key = { it.id }) { entry -> TimelineRow(entry) }
        }

        item("verify") {
            VerifyCard(
                state = state,
                onCopy = { label, value ->
                    copyToClipboard(context, label, value)
                    Toast.makeText(context, "$label copiado", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Hero: the number we are not allowed to fake
// ---------------------------------------------------------------------------

@Composable
private fun TotalHero(state: MetaUiState, onVerifyTotal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RaizBlack)
            .padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Meta del barrio",
            style = MaterialTheme.typography.bodyMedium,
            color = RaizGrayLight.copy(alpha = 0.8f),
        )
        Text(
            text = state.goalName,
            style = MaterialTheme.typography.headlineMedium,
            color = RaizWhite,
        )

        when (val total = state.total) {
            is TotalState.Verified -> {
                Text(
                    text = total.stroops.formatXlm(),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                    color = RaizWhite,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Verified,
                        contentDescription = null,
                        tint = RaizYellow,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "Verificado contra los compromisos on-chain · ledger ${total.atLedger}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                        color = RaizYellow,
                    )
                }
                Text(
                    text = "${total.contributions} aportes descifrados con la view key publicada · " +
                        "${total.eventsScanned} eventos de ${total.eventSource} · ${total.ms} ms",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = RaizGrayLight.copy(alpha = 0.6f),
                )
                ChecksList(total.checks)
                HeroAction("Verificar de nuevo", onVerifyTotal)
            }

            is TotalState.Running -> {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                    color = RaizWhite,
                )
                Text(
                    text = total.phase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizGrayLight.copy(alpha = 0.8f),
                )
                ProofProgress(
                    phase = "",
                    startedAtElapsedRealtime = total.startedAt,
                    title = "Abriendo la view key publicada",
                    hint = "Se descifra cada aporte y se recompone el total; luego se " +
                        "compara con los compromisos Pedersen de la cadena.",
                )
            }

            is TotalState.Unverified -> {
                Text(
                    text = "sin verificar",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RaizError,
                )
                Text(
                    text = "No mostramos el número: ${total.message}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizGrayLight,
                )
                ChecksList(total.checks)
                HeroAction("Reintentar", onVerifyTotal)
            }

            is TotalState.Failed -> {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                    color = RaizWhite,
                )
                Text(
                    text = "El total todavía no se pudo verificar en el teléfono:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizGrayLight,
                )
                Text(
                    text = total.message,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = RaizError,
                )
                Text(
                    text = "La participación de abajo sale de los eventos y no depende de esto.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = RaizGrayLight.copy(alpha = 0.6f),
                )
                HeroAction("Verificar el total", onVerifyTotal)
            }

            TotalState.Idle -> {
                Text(
                    text = "•••",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 44.sp),
                    color = RaizWhite,
                )
                Text(
                    text = "El total no lo decimos nosotros: se abre con la view key " +
                        "publicada y se comprueba contra la cadena.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizGrayLight.copy(alpha = 0.8f),
                )
                HeroAction("Verificar el total", onVerifyTotal)
            }
        }
    }
}

@Composable
private fun HeroAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = RaizYellow,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    )
}

@Composable
private fun ChecksList(checks: List<Check>) {
    if (checks.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        checks.forEach { c ->
            val mark = when (c.ok) {
                true -> "OK"
                false -> "FALLA"
                null -> "omitida"
            }
            Text(
                text = "[$mark] ${c.name}",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = if (c.ok == false) RaizError else RaizGrayLight.copy(alpha = 0.7f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// The source switch — the video's central scene
// ---------------------------------------------------------------------------

@Composable
private fun SourceCard(
    state: MetaUiState,
    onSelectSource: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "¿De dónde sale este historial?",
            style = MaterialTheme.typography.labelLarge,
            color = RaizBlack,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceChip(
                label = "Raiz Memory",
                selected = !state.source.forgets,
                accent = RaizGreen,
                modifier = Modifier.weight(1f),
            ) { onSelectSource(EventSource.ID_RAIZ_MEMORY) }
            SourceChip(
                label = "RPC (simulado)",
                selected = state.source.forgets,
                accent = RaizError,
                modifier = Modifier.weight(1f),
            ) { onSelectSource(EventSource.ID_RPC_SIMULATION) }
        }
        Text(
            text = state.source.hint,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = RaizBlack.copy(alpha = 0.6f),
        )
        val t = state.timeline
        Text(
            text = if (t.truncated) {
                "Esta fuente olvidó todo lo anterior al ledger ${t.oldestLedger}. " +
                    "Muestra ${t.entries.size} eventos de la meta."
            } else {
                "Cubre desde el despliegue del wrapper (ledger ${CtConfig.DEPLOYED_AT_LEDGER}) " +
                    "hasta el ${t.latestLedger}. Muestra ${t.entries.size} eventos de la meta."
            },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = if (t.truncated) RaizError else RaizBlack.copy(alpha = 0.5f),
        )
        Text(
            text = state.baseUrl,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = RaizPurple,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Actualizar",
                style = MaterialTheme.typography.labelLarge,
                color = RaizGreen,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onRefresh)
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            )
            if (state.refreshing) {
                Spacer(Modifier.size(10.dp))
                CircularProgressIndicator(
                    color = RaizGreen,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) accent.copy(alpha = 0.14f) else RaizWhite)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) accent else RaizGrayLight,
                shape = RoundedCornerShape(50),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 13.sp,
            ),
            color = if (selected) accent else RaizBlack.copy(alpha = 0.6f),
        )
    }
}

// ---------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------

@Composable
private fun TimelineRow(entry: TimelineEntry) {
    val context = LocalContext.current
    when (entry) {
        is TimelineEntry.Contribution -> AporteRow(
            titulo = entry.title,
            address = entry.from,
            cuando = entry.atIso.asRelativeEs(),
            icon = Icons.Outlined.Payments,
            accent = RaizGreen,
            trailing = { MontoOculto() },
            // The tx is the more useful destination for an aporte: it shows the
            // confidential transfer itself, amount-free, on a public explorer.
            onOpen = entry.txHash?.let { hash ->
                { StellarExpert.open(context, StellarExpert.txUrl(hash)) }
            },
        )

        is TimelineEntry.Merge -> AporteRow(
            titulo = entry.title,
            address = entry.account,
            cuando = entry.atIso.asRelativeEs(),
            icon = Icons.Outlined.CardGiftcard,
            accent = RaizYellow,
        )

        is TimelineEntry.Harvest -> AporteRow(
            titulo = entry.title,
            address = MetaConfig.GOAL_META,
            cuando = listOfNotNull(
                entry.memo.ifBlank { null },
                entry.atIso.asRelativeEs(),
            ).joinToString(" · "),
            icon = Icons.Outlined.Eco,
            accent = RaizYellow,
        )

        is TimelineEntry.GoalCreated -> AporteRow(
            titulo = entry.title,
            address = entry.goalAccount ?: CtConfig.GOAL_ACCOUNT,
            cuando = entry.atIso.asRelativeEs(),
            icon = Icons.Outlined.Star,
            accent = RaizPurple,
        )

        else -> AporteRow(
            titulo = entry.title,
            address = entry.actor.orEmpty(),
            cuando = entry.atIso.asRelativeEs(),
        )
    }
}

/**
 * The pill where every other wallet prints a figure. RAIZ has no equivalent —
 * nothing in it ever conceals a number.
 */
@Composable
private fun MontoOculto() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(RaizPurple.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = "monto cifrado",
            tint = RaizPurple,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = "•••",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = RaizPurple,
        )
    }
}

@Composable
private fun TimelineVacio(state: MetaUiState) {
    val t = state.timeline
    if (t.truncated) {
        PhaseBanner(
            title = "Esta fuente ya olvidó la meta",
            body = "El RPC simulado solo conserva desde el ledger ${t.oldestLedger}, y los " +
                "aportes de esta meta ocurrieron antes. No están borrados de la cadena: " +
                "están fuera de la ventana de retención. Cambia a Raiz Memory arriba y vuelven.",
            accent = RaizError,
        )
    } else {
        PhaseBanner(
            title = "Todavía no hay aportes",
            body = "Cuando alguien aporte desde su sobre, aparecerá aquí con su dirección " +
                "y la hora. El monto no: vive cifrado en la cadena.",
        )
    }
}

@Composable
private fun CargandoBox(texto: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = RaizGreen, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(12.dp))
        Text(text = texto, style = MaterialTheme.typography.bodyMedium, color = RaizBlack)
    }
}

// ---------------------------------------------------------------------------
// "Verifícalo tú mismo"
// ---------------------------------------------------------------------------

@Composable
private fun VerifyCard(state: MetaUiState, onCopy: (String, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Verifícalo tú mismo",
            style = MaterialTheme.typography.labelLarge,
            color = RaizBlack,
        )
        Text(
            text = "La view key de auditor de la meta está publicada a propósito. Con ella " +
                "cualquiera descifra el total del fondo — sin la app y sin nuestro permiso.",
            style = MaterialTheme.typography.bodyMedium,
            color = RaizBlack.copy(alpha = 0.7f),
        )

        CopiableBlock(
            label = "View key publicada",
            value = MetaConfig.VIEW_KEY_HEX,
            onCopy = onCopy,
        )
        CopiableBlock(
            label = "Comando para verificar por fuera",
            value = MetaConfig.VERIFY_COMMAND,
            onCopy = onCopy,
        )

        Spacer(Modifier.size(4.dp))
        VerifyRow("Cuenta de la meta", state.timeline.goalAccount)
        VerifyRow("CT wrapper", CtConfig.TOKEN)
        VerifyRow("goal_meta (meta #${MetaConfig.GOAL_ID})", MetaConfig.GOAL_META)
        VerifyRow("verifier", CtConfig.VERIFIER)
        VerifyRow("auditor", CtConfig.AUDITOR)
    }
}

@Composable
private fun CopiableBlock(label: String, value: String, onCopy: (String, String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RaizGrayLight.copy(alpha = 0.5f))
            .clickable { onCopy(label, value) }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = RaizBlack.copy(alpha = 0.55f),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "copiar",
                tint = RaizPurple,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            ),
            color = RaizBlack,
        )
    }
}

private fun copyToClipboard(context: android.content.Context, label: String, value: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
        as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
}

/** Subtitle under the app-bar title: what is on screen and where it came from. */
fun MetaUiState.headerSubtitle(): String =
    "${timeline.contributionCount} aportes · ${timeline.contributorCount} aportantes · " +
        "${source.label} · meta ${timeline.goalAccount.shortAddr(6, 4)}"
