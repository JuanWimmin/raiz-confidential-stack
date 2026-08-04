package xyz.raiz.sobre.ui.sobre

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.raiz.sobre.ui.components.PhaseBanner
import xyz.raiz.sobre.ui.components.ProofProgress
import xyz.raiz.sobre.ui.components.SobreCard
import xyz.raiz.sobre.ui.components.StatBox
import xyz.raiz.sobre.ui.components.StepFeedback
import xyz.raiz.sobre.ui.components.StepState
import xyz.raiz.sobre.ui.nav.formatXlm
import xyz.raiz.sobre.ui.nav.formatXlmOrHidden
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizError
import xyz.raiz.sobre.ui.theme.RaizGrayLight
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizPurple
import xyz.raiz.sobre.ui.theme.RaizWhite
import xyz.raiz.sobre.ui.theme.RaizYellow
import xyz.raiz.sobre.ui.util.StellarExpert

/** How long a finished operation keeps the above-the-fold slot. */
private const val RESULTADO_ARRIBA_MS = 90_000L


/**
 * SCREEN 2 — "Mi Sobre". The private half: a confidential balance only this
 * device can read, and the two operations that move it.
 *
 * Nothing here is mocked. "Sellar" and "Aportar" run the exact pipeline the
 * Session 5 console proved on this phone (prove in the WebView → sign in Kotlin
 * → submit → poll), and the honest cost of that is 10-30 s of on-device proving,
 * which [ProofProgress] shows as counting seconds rather than a lying spinner.
 */
@Composable
fun SobreScreen(
    state: SobreUiState,
    onAmountChange: (String) -> Unit,
    onAbrirSobre: () -> Unit,
    onSellar: () -> Unit,
    onAportar: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current

    // MEASURED ON THE VIVO Y21 (2026-08-04): this window does NOT resize when the
    // IME opens — the numeric keyboard simply draws on top of it, bottom nav bar
    // included, so Compose sees a viewport that is still 1600 px tall and has no
    // reason to scroll `Sellar`/`Aportar` out from under the keyboard. Asking for
    // ADJUST_RESIZE while this screen is on shrinks the real viewport, which is
    // what makes the scroll below possible at all. Scoped and restored on
    // dispose: no other screen has a text field that matters.
    ResizeParaElTeclado()

    // A result the user JUST produced belongs where the progress card was — above
    // the fold. On this 720x1600 phone anything after the amount field is
    // off-screen, so a tx hash rendered under the buttons is a tx hash nobody
    // sees at the exact moment it matters. After [RESULTADO_ARRIBA_MS] it settles
    // back under the buttons that caused it, which is where an OLD outcome reads
    // best.
    val finishedAt = state.op.finishedAtOrNull
    var resultadoReciente by remember(finishedAt) { mutableStateOf(finishedAt != null) }
    LaunchedEffect(finishedAt) {
        if (finishedAt != null) {
            delay(RESULTADO_ARRIBA_MS)
            resultadoReciente = false
        }
    }
    val opArriba = state.op is OpUiState.Running || resultadoReciente

    // Keyboard handling, second half (the first is [ResizeParaElTeclado]). The
    // viewport on this screen only ever shrinks for one reason — the IME just
    // opened — so that is the signal to scroll, and it is a far more reliable one
    // than a focus event: a `BringIntoViewRequester` on the button row was tried
    // first and never reached the scroller (verified on device, 2026-08-04).
    // Scrolling the whole "Mover monedas" card to the top puts the field AND
    // `Sellar`/`Aportar` above the keyboard, which is the state the user needs.
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var altoPrevio by remember { mutableIntStateOf(0) }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { tamano ->
                val previo = altoPrevio
                altoPrevio = tamano.height
                if (previo > 0 && tamano.height < previo) {
                    val idx = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == ITEM_ACCIONES }?.index
                    if (idx != null) scope.launch { listState.animateScrollToItem(idx) }
                }
            },
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp + contentPadding.calculateTopPadding(),
            bottom = 24.dp + contentPadding.calculateBottomPadding(),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("saldo") {
            SobreCard(
                balanceText = state.spendableStroops.formatXlmOrHidden(),
                publicKey = state.accountId ?: "…",
                label = when {
                    state.accountId == null -> "Creando tu cuenta en el teléfono…"
                    state.registered == false -> "Tu sobre todavía no está abierto"
                    state.spendableStroops == null -> "Tu sobre (aún sin descifrar)"
                    else -> "Tu sobre"
                },
                onAddressTap = {
                    state.accountId?.let { StellarExpert.open(context, StellarExpert.addressUrl(it)) }
                },
            )
        }

        // ABOVE THE FOLD ON PURPOSE: a 10-30 s proof whose progress card sits
        // below the actions is a progress card nobody sees. Measured on the
        // Vivo Y21 (720x1600): anything after the amount field is off-screen.
        // The OUTCOME inherits the same slot for its first minute and a half:
        // it replaces the progress card in place, so the hash appears exactly
        // where the user was already looking.
        if (opArriba && state.op !is OpUiState.Idle) {
            item("op-arriba") { OpBlock(state, context) }
        }

        item("descifrado") {
            Text(
                text = "Este saldo se descifra en tu teléfono con tu clave. Nadie más lo ve: " +
                    "en la cadena solo hay compromisos Pedersen.",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = RaizBlack.copy(alpha = 0.6f),
            )
        }

        item("pendiente") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatBox(
                    label = "Pendiente de cosechar",
                    value = state.receivingStroops.formatXlmOrHidden(),
                    accent = RaizYellow,
                    modifier = Modifier.weight(1f),
                )
                StatBox(
                    label = "Ledger leído",
                    value = state.syncedLedger?.toString() ?: "—",
                    accent = RaizPurple,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // First run after an install: the app id changed, so the previous CT
        // account is gone with its EncryptedSharedPreferences. Register again.
        if (state.registered == false) {
            item("registrar") {
                PhaseBanner(
                    title = "Abrir mi sobre",
                    body = "Registra tu cuenta en el token confidencial. Genera una prueba " +
                        "de conocimiento cero AQUÍ, en el teléfono: tarda entre 10 y 30 s.",
                    cta = "Abrir mi sobre",
                    inProgress = state.busy,
                    onAction = onAbrirSobre,
                )
            }
        }

        if (state.statusError != null) {
            item("status-error") {
                PhaseBanner(
                    title = "No pudimos leer tu saldo",
                    body = state.statusError,
                    accent = RaizError,
                    cta = "Reintentar",
                    onAction = onRefresh,
                )
            }
        }

        item(ITEM_ACCIONES) {
            AccionesCard(
                state = state,
                onAmountChange = onAmountChange,
                onSellar = onSellar,
                onAportar = onAportar,
            )
        }

        // An older Ok/Failed outcome settles here, under the buttons that caused
        // it. While it is fresh it lives above the fold instead (see [opArriba]).
        if (!opArriba && state.op !is OpUiState.Running) {
            item("op-result") { OpBlock(state, context) }
        }

        item("refrescar") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Actualizar saldo",
                    style = MaterialTheme.typography.labelLarge,
                    color = RaizGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = !state.busy, onClick = onRefresh)
                        .padding(vertical = 8.dp, horizontal = 2.dp),
                )
                if (state.loadingStatus) {
                    Spacer(Modifier.size(10.dp))
                    CircularProgressIndicator(
                        color = RaizGreen,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

/** LazyColumn key of the card that holds the amount field and the buttons. */
private const val ITEM_ACCIONES = "acciones"

/**
 * Ask the window for `SOFT_INPUT_ADJUST_RESIZE` while this screen is composed,
 * and put back whatever it had on the way out. Done here rather than in the
 * manifest because the manifest belongs to the app shell, and because this is
 * the only screen where the keyboard fights a button.
 */
@Composable
private fun ResizeParaElTeclado() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.actividad()?.window
        val previo = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose { if (window != null && previo != null) window.setSoftInputMode(previo) }
    }
}

/** Unwrap the Compose ContextWrapper chain down to the hosting Activity. */
private tailrec fun Context.actividad(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividad()
    else -> null
}

/**
 * The amount field and the two operations.
 *
 * The numeric keyboard is 500+ px tall on this 720x1600 phone and lands right on
 * top of `Sellar`/`Aportar`, which during the rehearsal forced a BACK press
 * before the button could be tapped. `ImeAction.Done` closes it with the
 * keyboard's own action key — BACK is not a gesture anybody performs on purpose
 * mid-demo — and the caller scrolls this card above the keyboard so the buttons
 * are reachable without closing it at all.
 */
@Composable
private fun AccionesCard(
    state: SobreUiState,
    onAmountChange: (String) -> Unit,
    onSellar: () -> Unit,
    onAportar: () -> Unit,
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RaizWhite)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Mover monedas",
            style = MaterialTheme.typography.labelLarge,
            color = RaizBlack,
        )
        OutlinedTextField(
            value = state.amountInput,
            onValueChange = onAmountChange,
            label = { Text("Monto en XLM") },
            singleLine = true,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSellar,
                enabled = !state.busy && state.amountStroops != null,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaizPurple),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) { Text("Sellar") }
            Button(
                onClick = onAportar,
                enabled = !state.busy && state.amountStroops != null && state.registered != false,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RaizGreen),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
            ) { Text("Aportar") }
        }
        Text(
            text = "Sellar mete XLM público en tu sobre (ese monto SÍ es visible: así " +
                "funciona el depósito en CT). Aportar manda parte de tu sobre a la meta " +
                "con el monto cifrado — eso es lo que nadie puede ver.",
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
            color = RaizBlack.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun OpBlock(state: SobreUiState, context: android.content.Context) {
    when (val op = state.op) {
        OpUiState.Idle -> Unit

        is OpUiState.Running -> ProofProgress(
            phase = op.phase,
            startedAtElapsedRealtime = op.startedAt,
            title = op.label,
        )

        is OpUiState.Ok -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RaizWhite)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            StepFeedback(
                StepState.Ok(
                    buildString {
                        append(op.label.lowercase())
                        op.ledger?.let { append(" · ledger $it") }
                        if (op.proveMs > 0) append(" · prueba ${op.proveMs} ms en el teléfono")
                        if (op.note.isNotBlank()) append(" · ${op.note}")
                    },
                ),
            )
            op.txHash?.let { hash ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { StellarExpert.open(context, StellarExpert.txUrl(hash)) }
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = hash,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = RaizPurple,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.OpenInNew,
                        contentDescription = "ver en stellar.expert",
                        tint = RaizPurple,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            if (op.op == "confidential_transfer") {
                Text(
                    text = "Tu aporte ya está en la meta. En la pantalla Meta aparece tu " +
                        "dirección y la hora — nunca el monto.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = RaizBlack.copy(alpha = 0.6f),
                )
            }
        }

        is OpUiState.Failed -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RaizError.copy(alpha = 0.08f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${op.label}: falló",
                style = MaterialTheme.typography.labelLarge,
                color = RaizError,
            )
            // VERBATIM. A CT/RPC error message is friction-report material and
            // the judge's window into what actually broke.
            Text(
                text = op.message,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                color = RaizBlack.copy(alpha = 0.8f),
            )
        }
    }
}

/** Compact one-liner the shell shows under the title. */
fun SobreUiState.headerSubtitle(): String = when {
    accountId == null -> "creando tu cuenta…"
    registered == false -> "sobre sin abrir"
    spendableStroops != null -> "${spendableStroops.formatXlm()} descifrados en el teléfono"
    else -> "descifrando tu saldo…"
}

/** Kept next to the screen so the divider style is defined once. */
@Composable
internal fun Divisor() {
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(RaizGrayLight),
    )
}
