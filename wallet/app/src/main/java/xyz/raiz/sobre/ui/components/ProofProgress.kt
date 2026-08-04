package xyz.raiz.sobre.ui.components

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * THE NEW COMPONENT — RAIZ has no equivalent, and everything else here needs it.
 *
 * RAIZ's longest operation is a network round-trip behind one boolean, rendered
 * as a spinner swap. Ours is a zero-knowledge proof computed ON THIS PHONE:
 * measured 10.8 s (register) to 15.7 s (transfer) on a Vivo Y21, single-threaded,
 * because WebView never grants crossOriginIsolated. That is far past the point
 * where a bare spinner reads as "the app froze".
 *
 * Design rules, all three deliberate:
 *
 *  1. ELAPSED SECONDS, NOT A PERCENTAGE. A proof has no observable progress —
 *     bb.js reports nothing between "started" and "done". Any bar that filled up
 *     would be a fabricated number, and this project's whole claim is that you
 *     do not have to take our word for anything. So: an INDETERMINATE bar plus a
 *     real, counting-up elapsed time.
 *  2. THE PHASE STRING IS THE TRUTH. [phase] is meant to be fed the live
 *     progress callback `CtWallet` already emits ("generando prueba de
 *     registro… (~10 s en este teléfono)", "firmando en Kotlin…", "enviando a
 *     testnet… tx …"). The user sees the actual stage, not a generic "Cargando".
 *  3. NO CANCEL BUTTON. `ProverWebViewBridge` serializes calls behind a Mutex
 *     and enforces its own PROOF_TIMEOUT_MS = 90_000. We cannot actually abort a
 *     running proof, so offering a cancel would be a lie on camera.
 *
 * The lock glyph is not decoration: it marks that this computation happens on
 * the device and no secret leaves it.
 *
 * @param phase live stage text from the operation's progress callback.
 * @param startedAtElapsedRealtime `SystemClock.elapsedRealtime()` captured when
 *        the operation STARTED. Recording the start instant (instead of ticking
 *        a counter in the ViewModel) keeps the count correct across
 *        recomposition, rotation and process-visible pauses. Changing this value
 *        restarts the counter.
 * @param title headline; the elapsed seconds are appended to it.
 * @param hint  the honest expectation, shown from the first second.
 * @param slowAfterSeconds when to admit it is taking longer than usual.
 */
@Composable
fun ProofProgress(
    phase: String,
    startedAtElapsedRealtime: Long,
    modifier: Modifier = Modifier,
    title: String = "Generando prueba en tu teléfono",
    hint: String = "Las pruebas tardan entre 10 y 30 s en este teléfono. No cierres la app.",
    slowAfterSeconds: Int = 45,
    slowText: String = "Está tardando más de lo normal. Se cancela sola a los 90 s.",
) {
    var nowMs by remember(startedAtElapsedRealtime) {
        mutableLongStateOf(SystemClock.elapsedRealtime())
    }
    LaunchedEffect(startedAtElapsedRealtime) {
        while (true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1_000L)
        }
    }
    val seconds = ((nowMs - startedAtElapsedRealtime).coerceAtLeast(0L) / 1000L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = RaizPurple,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "$title… $seconds s",
                style = MaterialTheme.typography.labelLarge,
                color = RaizBlack,
            )
        }
        // Indeterminate on purpose — see rule 1 above.
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = RaizGreen,
            trackColor = RaizGrayLight,
        )
        if (phase.isNotBlank()) {
            Text(
                text = phase,
                style = MaterialTheme.typography.bodyMedium,
                color = RaizBlack.copy(alpha = 0.7f),
            )
        }
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = RaizBlack.copy(alpha = 0.5f),
        )
        if (seconds >= slowAfterSeconds) {
            Text(
                text = slowText,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = RaizError,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun ProofProgressPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
