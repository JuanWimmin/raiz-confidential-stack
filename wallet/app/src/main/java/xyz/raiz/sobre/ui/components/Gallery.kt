package xyz.raiz.sobre.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.raiz.sobre.ui.theme.RaizBackground
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * DESIGN-SYSTEM GALLERY — every component of `ui/components` rendered with
 * sample data, so the screens can be assembled from a known inventory instead of
 * from guesswork. NOT wired into MainActivity and never navigated to; it exists
 * for `@Preview` (open this file in Android Studio, split view).
 *
 * Everything below is fake sample data. The only real strings are the contract
 * ids in the "Verifícalo tú mismo" block, copied from CtConfig so the row widths
 * on screen match the widths in the demo.
 */
@Composable
fun ComponentGallery(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RaizBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GallerySection("SobreCard — el saldo descifrado en el teléfono") {
            SobreCard(
                balanceText = "5 XLM",
                publicKey = SAMPLE_ACCOUNT,
            )
            SobreCard(
                balanceText = "•••",
                publicKey = SAMPLE_ACCOUNT,
                label = "Tu sobre (aún sin descifrar)",
            )
        }

        GallerySection("StatBox — SIEMPRE conteos, nunca montos") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    label = "Aportantes",
                    value = "7",
                    accent = RaizPurple,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = "Aportes",
                    value = "12",
                    accent = RaizGreen,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = "Cosechas",
                    value = "2",
                    accent = RaizYellow,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        GallerySection("GoalProgressBar — participación, no dinero") {
            GoalProgressBar(reachedPct = 60, caption = "12 aportes de 20", trailing = "60%")
            GoalProgressBar(reachedPct = 100, caption = "Meta alcanzada", trailing = "100%")
            GoalProgressBar(reachedPct = 0, caption = "Todavía sin aportes")
        }

        GallerySection("AporteRow — quién y cuándo. NUNCA cuánto.") {
            AporteRow(
                titulo = "Aporte a la meta",
                address = SAMPLE_ACCOUNT,
                cuando = "hace 3 min",
                icon = Icons.Outlined.Payments,
            )
            AporteRow(
                titulo = "Aporte a la meta",
                address = "GBLS7PL5Y65DHQIPMJO6HVQLX4FXEEHQDWHGSBUTGT4V6ZV2IOACYC2P",
                cuando = "hace 2 h",
                icon = Icons.Outlined.Payments,
            )
            AporteRow(
                titulo = "Cosecha de la meta",
                address = SAMPLE_ACCOUNT,
                cuando = "ayer",
                icon = Icons.Outlined.Eco,
                accent = RaizGreen,
            )
            Text(
                text = "Los montos viven cifrados. La participación es pública.",
                style = MaterialTheme.typography.bodyMedium,
                color = RaizBlack.copy(alpha = 0.6f),
            )
        }

        GallerySection("VerifyRow — \"Verifícalo tú mismo\"") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(RaizWhite)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                VerifyRow("CT wrapper", "CBWSANZN7YIMA4CWLNSAZO3HSD5NZDC2GZQJ434MOPQR7RDNVYQSDHAT")
                VerifyRow("goal_meta", "CBNVY2AAHA4SP3MX4XKJAZGS63SF4GIFNHUAAQPRSKYAXY3XR6HKIQAZ")
                VerifyRow("verifier", "CBFCYFND44SNQPKMQNHB3KX2C7K4U5WSVUMFJY34OV46YAN2SACM3UIA")
                VerifyRow("auditor", "CBUSX5B56KB73FAAIIHW7ISSZEGHDKQTOWML74LBPOWWGCEFEZPLHE25")
            }
        }

        GallerySection("PhaseBanner — explicar, actuar, y admitir el error") {
            PhaseBanner(
                title = "Todavía no tienes sobre",
                body = "Abrir tu sobre genera una prueba en el teléfono y te registra en " +
                    "el token confidencial.",
                cta = "Abrir mi sobre",
            )
            PhaseBanner(
                title = "Abriendo tu sobre",
                body = "firmando en Kotlin (la seed nunca sale del teléfono)…",
                cta = "Abrir mi sobre",
                inProgress = true,
            )
            PhaseBanner(
                title = "No pudimos abrir tu sobre",
                body = "La red de prueba rechazó la transacción.",
                error = "HostError: Error(Contract, #2000)",
                cta = "Reintentar",
            )
            PhaseBanner(
                title = "¿Qué es cosechar?",
                body = "Los aportes que recibes llegan a un canal aparte. Cosechar los suma " +
                    "a tu saldo disponible. No lleva prueba: es una suma cifrada.",
            )
        }

        GallerySection("StepFeedback — operaciones cortas (enviar + confirmar)") {
            StepFeedback(StepState.Submitting)
            StepFeedback(StepState.Ok("ledger 3952632 · tx 7f9c6f9a…"))
            StepFeedback(StepState.Failed("HostError: Error(Contract, #2000) — auditor no registrado"))
        }

        GallerySection("ProofProgress — operación larga, segundos reales, sin porcentaje falso") {
            ProofProgress(
                phase = "generando prueba de registro… (~10 s en este teléfono)",
                startedAtElapsedRealtime = SystemClock.elapsedRealtime() - 12_000L,
            )
            ProofProgress(
                phase = "sincronizando estado confidencial + generando prueba… (~15 s)",
                startedAtElapsedRealtime = SystemClock.elapsedRealtime() - 52_000L,
                title = "Aportando a la meta",
            )
        }
    }
}

@Composable
private fun GallerySection(
    titulo: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = RaizBlack.copy(alpha = 0.55f),
        )
        content()
    }
}

private const val SAMPLE_ACCOUNT = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X"

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7, heightDp = 2400)
@Composable
private fun ComponentGalleryPreview() {
    SobreTheme {
        ComponentGallery()
    }
}
