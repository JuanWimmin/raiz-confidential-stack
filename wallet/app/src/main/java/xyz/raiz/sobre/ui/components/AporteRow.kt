package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.theme.SobreTheme
import xyz.raiz.sobre.ui.util.StellarExpert

/**
 * One line of the goal's timeline — adopted from RAIZ's `ExecutionRow`
 * (DashboardScreen.kt:571-621).
 *
 * NAMED EDIT vs RAIZ, AND IT IS THE WHOLE PRODUCT: RAIZ's row ended in a bold
 * green amount (`exec.amountStroops.formatUsdc()`, :607-611). That Text is
 * DELETED here and the date takes its slot.
 *
 *   "Los aportes son secretos. El fondo es de vidrio."
 *
 * An aporte row shows WHO and WHEN. It never shows HOW MUCH — the amount lives
 * encrypted in the CT wrapper and is only openable with the goal's published
 * auditor view key, outside this app. This component has no amount parameter on
 * purpose: there is nothing to forget to hide.
 *
 * @param titulo   row title, e.g. "Aporte a la meta" / "Cosecha".
 * @param address  the G… / C… address of who acted; rendered monospace 8…6.
 * @param cuando   preformatted relative date, e.g. "hace 3 min". Sits exactly
 *                 where RAIZ printed the figure.
 * @param trailing optional slot AFTER the date — the intended home for a
 *                 "monto oculto" pill, so adding one needs no edit here.
 * @param onOpen   defaults to opening [address] in Stellar Expert.
 */
@Composable
fun AporteRow(
    titulo: String,
    address: String,
    cuando: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Outlined.Storefront,
    accent: Color = RaizYellow,
    trailing: (@Composable () -> Unit)? = null,
    onOpen: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RaizWhite)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent)
        }
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.labelLarge,
                color = RaizBlack,
            )
            Text(
                text = if (address.length > 14) {
                    "${address.take(8)}…${address.takeLast(6)}"
                } else {
                    address
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = RaizBlack.copy(alpha = 0.5f),
            )
        }
        // ── Where RAIZ printed the amount. We print the date. ──────────────
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = cuando,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = RaizBlack.copy(alpha = 0.5f),
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.size(4.dp))
                trailing()
            }
        }
        Spacer(modifier = Modifier.size(4.dp))
        IconButton(
            onClick = onOpen ?: { StellarExpert.open(context, StellarExpert.addressUrl(address)) },
        ) {
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = "Ver en Stellar Expert",
                tint = RaizGreen,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun AporteRowPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AporteRow(
                titulo = "Aporte a la meta",
                address = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X",
                cuando = "hace 3 min",
            )
            AporteRow(
                titulo = "Cosecha de la meta",
                address = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X",
                cuando = "ayer",
                icon = Icons.Outlined.Eco,
                accent = RaizGreen,
            )
        }
    }
}
