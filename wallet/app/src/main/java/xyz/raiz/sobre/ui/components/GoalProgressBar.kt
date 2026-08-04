package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * Stacked progress bar for "la meta" — adopted from RAIZ's `UsageBar`
 * (DashboardScreen.kt:507-565).
 *
 * NAMED EDIT vs RAIZ: `UsageBar` read its numbers off `DashboardUiState` and
 * formatted a USDC figure into the caption. This one takes plain
 * ([reachedPct], [caption]) so the caller decides what the bar MEANS.
 *
 * IMPORTANT for this project: on the public Meta screen the percentage is a
 * PARTICIPATION ratio (aportes / meta de aportes), never a currency ratio — the
 * app has no auditor-decrypt code path and must not imply it does. The amounts
 * live encrypted; the total is verified outside the app with the published view
 * key.
 *
 * @param reachedPct 0..100, clamped. The remainder renders as the "falta" track.
 * @param caption    left-hand caption, e.g. "12 aportes de 20".
 * @param trailing   right-hand caption in green, e.g. "Falta 40%". Optional.
 */
@Composable
fun GoalProgressBar(
    reachedPct: Int,
    caption: String,
    modifier: Modifier = Modifier,
    title: String = "Avance de la meta",
    trailing: String? = null,
) {
    val reached = reachedPct.coerceIn(0, 100)
    val remaining = 100 - reached

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = RaizBlack,
        )
        // Stacked bar: green (reached) + gray track (remaining), yellow accent
        // when the goal is already complete.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(RaizGrayLight),
        ) {
            if (reached > 0) {
                Box(
                    modifier = Modifier
                        .weight(reached.toFloat())
                        .fillMaxSize()
                        .background(if (reached >= 100) RaizYellow else RaizGreen),
                )
            }
            if (remaining > 0) {
                Box(
                    modifier = Modifier
                        .weight(remaining.toFloat())
                        .fillMaxSize()
                        .background(RaizGrayLight),
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = RaizBlack.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizGreen,
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun GoalProgressBarPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GoalProgressBar(
                reachedPct = 60,
                caption = "12 aportes de 20",
                trailing = "60%",
            )
            GoalProgressBar(
                reachedPct = 100,
                caption = "Meta alcanzada",
                trailing = "100%",
            )
        }
    }
}
