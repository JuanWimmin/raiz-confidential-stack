package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * Generic stat box, adopted verbatim from RAIZ: gray label on top, bold value
 * below in [accent]. The canonical flat-card idiom of the design system —
 * clip(16.dp) + white background + 20.dp padding.
 *
 * On the Meta screen these hold COUNTS (aportantes, aportes, cosechas), never
 * a currency figure.
 */
@Composable
fun StatBox(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RaizBlack.copy(alpha = 0.6f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = accent,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun StatBoxPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatBox(label = "Aportantes", value = "7", accent = RaizPurple)
            StatBox(label = "Aportes", value = "12", accent = RaizGreen)
        }
    }
}
