package xyz.raiz.sobre.ui.sobre

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.data.GoalTimeline
import xyz.raiz.sobre.data.TimelineEntry
import xyz.raiz.sobre.ui.components.AporteRow
import xyz.raiz.sobre.ui.components.PhaseBanner
import xyz.raiz.sobre.ui.components.ProofProgress
import xyz.raiz.sobre.ui.components.StepFeedback
import xyz.raiz.sobre.ui.components.StepState
import xyz.raiz.sobre.ui.nav.asRelativeEs
import xyz.raiz.sobre.ui.nav.formatXlmOrHidden
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow

/**
 * SCREEN 3 — "Cosechar". Deliberately hidden behind the overflow menu: this is
 * an operator surface, not something a neighbour taps by accident.
 *
 * It is honest about a split the word "cosechar" hides. Two different things:
 *
 *  1. **Cosechar mi sobre** — the CT `merge` of THIS device's account, moving
 *     its receiving balance into the spendable one. Proof-free, a few seconds,
 *     and fully implemented here.
 *  2. **Registrar la cosecha de la meta** — merging the *goal's* balance and
 *     calling `goal_meta.record_harvest`. Both require the goal's admin key,
 *     which is NOT on this phone (it lives in the deployment `.env`), and the
 *     prover shim exports no `prepareRecordHarvest`. We say so and show the
 *     command instead of drawing a button that cannot work.
 */
@Composable
fun CosechaScreen(
    state: SobreUiState,
    timeline: GoalTimeline,
    onCosecharMiSobre: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val cosechas = timeline.harvests

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
        item("que-es") {
            PhaseBanner(
                title = "Qué es cosechar",
                body = "Cuando alguien te aporta, el monto llega a un saldo aparte " +
                    "(\"pendiente\"). Cosechar es sumarlo a tu saldo gastable. No lleva " +
                    "prueba de conocimiento cero: es la operación barata del ciclo CT.",
            )
        }

        item("paso1") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaizWhite)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Paso 1 · Cosechar mi sobre",
                    style = MaterialTheme.typography.labelLarge,
                    color = RaizBlack,
                )
                Text(
                    text = "Pendiente de cosechar: ${state.receivingStroops.formatXlmOrHidden()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizBlack.copy(alpha = 0.7f),
                )
                Button(
                    onClick = onCosecharMiSobre,
                    enabled = !state.busy && state.registered != false,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RaizYellow),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("Cosechar", color = RaizBlack)
                }
                val op = state.op
                when {
                    op is OpUiState.Running && op.op == "merge" -> ProofProgress(
                        phase = op.phase,
                        startedAtElapsedRealtime = op.startedAt,
                        title = op.label,
                        hint = "Merge no lleva prueba ZK: solo firma y envío. Segundos, no minutos.",
                    )
                    op is OpUiState.Ok && op.op == "merge" ->
                        StepFeedback(StepState.Ok("merge · ledger ${op.ledger}"))
                    op is OpUiState.Failed && op.op == "merge" ->
                        StepFeedback(StepState.Failed(op.message))
                    else -> Unit
                }
            }
        }

        item("paso2") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaizGrayLight.copy(alpha = 0.45f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Paso 2 · Registrar la cosecha de la meta",
                    style = MaterialTheme.typography.labelLarge,
                    color = RaizBlack.copy(alpha = 0.7f),
                )
                Text(
                    text = "No disponible en este teléfono, y no lo vamos a fingir: cosechar " +
                        "la meta y registrar la cosecha en goal_meta exigen la clave de admin " +
                        "de la meta, que no está en este dispositivo. Se ejecuta desde el " +
                        "script de la meta:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizBlack.copy(alpha = 0.7f),
                )
                Text(
                    text = "bash scripts/goal-flow.sh",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = RaizBlack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(RaizWhite)
                        .padding(10.dp),
                )
                Text(
                    text = "El resultado aparece abajo en cuanto Raiz Memory lo indexa.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = RaizBlack.copy(alpha = 0.55f),
                )
            }
        }

        item("historial-head") {
            Text(
                text = "Cosechas de la meta",
                style = MaterialTheme.typography.labelLarge,
                color = RaizBlack,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (cosechas.isEmpty()) {
            item("historial-vacio") {
                PhaseBanner(
                    title = "Todavía no hay cosechas",
                    body = "Cuando la meta cosecha, queda el rastro público: el merge en el " +
                        "wrapper CT y el memo en goal_meta. Ninguno de los dos revela montos.",
                )
            }
        } else {
            items(cosechas, key = { it.id }) { entry -> CosechaRow(entry) }
        }
    }
}

@Composable
private fun CosechaRow(entry: TimelineEntry) {
    when (entry) {
        is TimelineEntry.Harvest -> AporteRow(
            titulo = entry.title,
            address = entry.contractId,
            cuando = listOfNotNull(entry.memo.ifBlank { null }, entry.atIso.asRelativeEs())
                .joinToString(" · "),
            icon = Icons.Outlined.Eco,
            accent = RaizYellow,
        )
        else -> AporteRow(
            titulo = entry.title,
            address = entry.actor.orEmpty(),
            cuando = entry.atIso.asRelativeEs(),
            icon = Icons.Outlined.CardGiftcard,
            accent = RaizYellow,
        )
    }
}

/** Kept so the shell can show the same count in the header. */
fun GoalTimeline.cosechaSubtitle(): String =
    "$harvestCount registros públicos · sin montos"
