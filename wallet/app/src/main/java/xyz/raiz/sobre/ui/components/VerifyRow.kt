package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.SobreTheme
import xyz.raiz.sobre.ui.util.StellarExpert

/**
 * One row of the "Verifícalo tú mismo" footer — adopted from RAIZ's
 * `ContratoFila` (DashboardScreen.kt:649-683), kept as-is and made public.
 *
 * Feed it the real deployment ids from `CtConfig`: the CT wrapper, goal_meta,
 * the verifier and the auditor contract. Tapping opens Stellar Expert — the
 * point being that nothing on this screen has to be taken on our word.
 *
 * Blank [address] renders nothing (RAIZ's guard, kept).
 */
@Composable
fun VerifyRow(
    nombre: String,
    address: String,
    modifier: Modifier = Modifier,
    onOpen: (() -> Unit)? = null,
) {
    if (address.isBlank()) return
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClick = onOpen
                    ?: { StellarExpert.open(context, StellarExpert.addressUrl(address)) },
            )
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = nombre,
            style = MaterialTheme.typography.bodyMedium,
            color = RaizBlack.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
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
            color = RaizPurple,
        )
        Spacer(modifier = Modifier.size(4.dp))
        Icon(
            imageVector = Icons.Outlined.OpenInNew,
            contentDescription = "Ver $nombre en Stellar Expert",
            tint = RaizPurple,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun VerifyRowPreview() {
    SobreTheme {
        Column(
            modifier = Modifier
                .padding(16.dp)
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
}
