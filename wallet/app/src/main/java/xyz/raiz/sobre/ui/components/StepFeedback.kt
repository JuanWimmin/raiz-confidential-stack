package xyz.raiz.sobre.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * State of one on-chain step — adopted from RAIZ's `TreasuryAction`
 * (YieldViewModel.kt:22-25), renamed.
 *
 * Use it for the SHORT operations (submit + poll). Proof generation is 10-30 s
 * of local computation and deserves [ProofProgress] instead.
 */
sealed interface StepState {
    data object Idle : StepState
    data object Submitting : StepState
    data class Ok(val message: String) : StepState
    data class Failed(val message: String) : StepState
}

/**
 * Inline 3-state feedback under a button — adopted from RAIZ's `ActionFeedback`
 * (YieldScreen.kt:471-499), renamed and made public.
 *
 * `Failed` renders the message VERBATIM: our `ProverException` subclasses and
 * the RPC already carry actionable text, and softening it would be lying to the
 * person holding the phone.
 */
@Composable
fun StepFeedback(
    state: StepState,
    modifier: Modifier = Modifier,
) {
    when (state) {
        StepState.Idle -> Unit
        StepState.Submitting -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                color = RaizGreen,
                strokeWidth = 2.dp,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "Enviando a la red…",
                style = MaterialTheme.typography.bodyMedium,
                color = RaizBlack.copy(alpha = 0.7f),
            )
        }
        is StepState.Ok -> Text(
            modifier = modifier,
            text = "Confirmado on-chain: ${state.message}",
            style = MaterialTheme.typography.bodyMedium,
            color = RaizGreen,
        )
        is StepState.Failed -> Text(
            modifier = modifier,
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = RaizError,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFAFAF7)
@Composable
private fun StepFeedbackPreview() {
    SobreTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepFeedback(StepState.Submitting)
            StepFeedback(StepState.Ok("ledger 3952632 · tx 7f9c6f9a…"))
            StepFeedback(StepState.Failed("HostError: Error(Contract, #2000) — auditor no registrado"))
        }
    }
}
