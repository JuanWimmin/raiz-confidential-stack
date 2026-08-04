package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * The black hero card of "Mi sobre" — adopted from RAIZ's `BalanceCard`.
 *
 * NAMED EDIT vs RAIZ: RAIZ called `Long.formatUsdc()` inside the card, hardcoding
 * " USDC" and 3 decimals. This card takes an ALREADY FORMATTED [balanceText]
 * instead, so the caller owns the unit (we are on XLM stroops) and so the card
 * can honestly render "•••" / "—" when the balance has not been decrypted on
 * this device yet. A card that must format cannot express "unknown".
 *
 * @param balanceText preformatted, e.g. "5 XLM", "•••" (not yet decrypted).
 * @param publicKey   full G… address; rendered truncated 8…6.
 * @param label       what the figure means. Defaults to the project vocabulary.
 * @param onAddressTap opens the address in Stellar Expert (caller wires it).
 */
@Composable
fun SobreCard(
    balanceText: String,
    publicKey: String,
    modifier: Modifier = Modifier,
    label: String = "Tu sobre",
    onAddressTap: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RaizBlack)
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountBalanceWallet,
            contentDescription = null,
            tint = RaizGreen,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RaizGrayLight,
        )
        Text(
            text = balanceText,
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 44.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = RaizWhite,
        )
        Row(
            modifier = Modifier
                .clickable(onClick = onAddressTap)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = publicKey.take(8) + "…" + publicKey.takeLast(6),
                style = MaterialTheme.typography.bodyMedium,
                color = RaizGrayLight,
            )
            Icon(
                imageVector = Icons.Outlined.OpenInNew,
                contentDescription = "Ver dirección en Stellar Expert",
                tint = RaizGrayLight.copy(alpha = 0.7f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun SobreCardPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SobreCard(
                balanceText = "5 XLM",
                publicKey = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X",
            )
            SobreCard(
                balanceText = "•••",
                publicKey = "GAJPXAL725N44NMEZ3XIN66ZJ7XURJNLXIDGIOOR7D3E23DIRGLIM73X",
                label = "Tu sobre (aún sin descifrar)",
            )
        }
    }
}
