package xyz.raiz.sobre.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.data.EventSource
import xyz.raiz.sobre.ui.components.VerifyRow
import xyz.raiz.sobre.ui.nav.ProverViewModel
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.wallet.CtConfig

/**
 * Ajustes — the editable half of the event-source setting (the *switch* itself
 * lives on the goal screen, where the demo needs it one tap away).
 *
 * Also the app's honesty page: which contracts it talks to, whether the prover
 * came up, and the last lines of the WebView console — which on this device
 * family (Vivo suppresses app logcat) is the only window into JS failures.
 */
@Composable
fun AjustesScreen(
    baseUrl: String,
    sourceLabel: String,
    proverEstado: ProverViewModel.Estado,
    consola: List<String>,
    onSetBaseUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var draft by remember(baseUrl) { mutableStateOf(baseUrl) }

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
        item("fuente") {
            Card("Fuente de eventos") {
                Text(
                    text = "Activa: $sourceLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RaizBlack.copy(alpha = 0.7f),
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("URL de Raiz Memory") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Desde el teléfono, la instancia del portátil se alcanza con:\n" +
                        "adb reverse tcp:8091 tcp:8091",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                    color = RaizBlack.copy(alpha = 0.55f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { onSetBaseUrl(draft) },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RaizGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) { Text("Guardar") }
                    Button(
                        onClick = {
                            draft = EventSource.DEFAULT_BASE_URL
                            onSetBaseUrl(EventSource.DEFAULT_BASE_URL)
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = RaizPurple),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) { Text("Por defecto") }
                }
            }
        }

        item("prover") {
            Card("Prover en el teléfono") {
                when (proverEstado) {
                    is ProverViewModel.Estado.Arrancando -> Text(
                        text = "Arrancando el motor de pruebas (WebView aislado)…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RaizBlack.copy(alpha = 0.7f),
                    )
                    is ProverViewModel.Estado.Listo -> Text(
                        text = "Listo en ${proverEstado.bootMs} ms. Las pruebas corren a 1 hilo: " +
                            "el WebView de Android nunca expone SharedArrayBuffer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RaizGreen,
                    )
                    is ProverViewModel.Estado.Falló -> Text(
                        text = proverEstado.message,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = RaizError,
                    )
                }
            }
        }

        item("contratos") {
            Card("Contratos en testnet") {
                VerifyRow("CT wrapper", CtConfig.TOKEN)
                VerifyRow("verifier", CtConfig.VERIFIER)
                VerifyRow("auditor", CtConfig.AUDITOR)
                VerifyRow("cuenta de la meta", CtConfig.GOAL_ACCOUNT)
                Text(
                    text = "RPC: ${CtConfig.RPC_URL}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                    color = RaizBlack.copy(alpha = 0.5f),
                )
            }
        }

        if (consola.isNotEmpty()) {
            item("consola") {
                Card("Consola del prover (últimas líneas)") {
                    Text(
                        text = consola.takeLast(12).joinToString("\n"),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = RaizBlack.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

@Composable
private fun Card(titulo: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = titulo, style = MaterialTheme.typography.labelLarge, color = RaizBlack)
        content()
    }
}
