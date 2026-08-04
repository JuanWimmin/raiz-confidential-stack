package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * Yellow explain-and-act banner — adopted from RAIZ's `AccountSetupBanner`
 * (WalletScreen.kt:353-432), which was RAIZ's only honest "this step is not done
 * yet, here is what happens next" surface.
 *
 * NAMED EDITS vs RAIZ:
 *  1. Generalized: RAIZ hardcoded a 3-step `when(AccountSetupStep)`. This one
 *     takes ([title], [body], [error], [cta], [inProgress], [onAction]).
 *  2. [cta] is nullable — a banner with no button is a plain explanation card
 *     (used on "Cosechar" to explain what harvesting means).
 *  3. FIXED THE DRIFT: RAIZ hardcoded `Color(0xFFB00020)` for the error line
 *     (:410) instead of importing its own `RaizError` token — the same copy-paste
 *     bug repeated in 10 other RAIZ files. Here it is the token.
 *
 * For a long local computation (proof generation) use [ProofProgress] instead —
 * this banner's spinner says "working", not "how long".
 */
@Composable
fun PhaseBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    cta: String? = null,
    inProgress: Boolean = false,
    accent: Color = RaizYellow,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.18f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = RaizBlack,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = RaizBlack.copy(alpha = 0.7f),
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = RaizError,
            )
        }
        if (cta != null) {
            Button(
                onClick = onAction,
                enabled = !inProgress,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RaizGreen,
                    contentColor = RaizWhite,
                    disabledContainerColor = RaizGreen.copy(alpha = 0.5f),
                    disabledContentColor = RaizWhite.copy(alpha = 0.7f),
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (inProgress) {
                    CircularProgressIndicator(
                        color = RaizWhite,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(cta, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun PhaseBannerPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhaseBanner(
                title = "Todavía no tienes sobre",
                body = "Abrir tu sobre genera una prueba en el teléfono y te registra " +
                    "en el token confidencial. Tarda entre 10 y 30 s.",
                cta = "Abrir mi sobre",
            )
            PhaseBanner(
                title = "Abriendo tu sobre",
                body = "generando prueba de registro… (~10 s en este teléfono)",
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
                body = "Los aportes que recibes llegan a un canal aparte. Cosechar los " +
                    "suma a tu saldo disponible. No lleva prueba: es una suma cifrada.",
            )
        }
    }
}
