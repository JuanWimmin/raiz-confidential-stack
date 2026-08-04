package xyz.raiz.sobre.ui.nav

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.raiz.sobre.ui.meta.MetaScreen
import xyz.raiz.sobre.ui.meta.MetaViewModel
import xyz.raiz.sobre.ui.meta.headerSubtitle
import xyz.raiz.sobre.ui.settings.AjustesScreen
import xyz.raiz.sobre.ui.sobre.CosechaScreen
import xyz.raiz.sobre.ui.sobre.SobreScreen
import xyz.raiz.sobre.ui.sobre.SobreViewModel
import xyz.raiz.sobre.ui.sobre.cosechaSubtitle
import xyz.raiz.sobre.ui.sobre.headerSubtitle
import xyz.raiz.sobre.ui.theme.RaizBackground
import xyz.raiz.sobre.ui.theme.RaizBlack
import xyz.raiz.sobre.ui.theme.RaizGreen
import xyz.raiz.sobre.ui.theme.RaizWhite

/**
 * The app shell. Two top-level destinations plus two overlays, held in
 * `rememberSaveable` state — **no navigation-compose** (raiz-reuse-plan §1.3:
 * a 300-line NavHost to move between three screens is a dependency we do not
 * need, and one more thing that can fail at the venue).
 *
 * "Cosechar" is intentionally NOT a tab: it is an operator action, reachable
 * only from the overflow menu.
 */
private enum class Destino { META, SOBRE }

private enum class Overlay { NINGUNO, COSECHA, AJUSTES }

@Composable
fun SobreApp(
    metaVm: MetaViewModel,
    sobreVm: SobreViewModel,
    proverVm: ProverViewModel,
) {
    val meta by metaVm.state.collectAsState()
    val sobre by sobreVm.state.collectAsState()
    val proverEstado by proverVm.estado.collectAsState()
    val consola by proverVm.console.collectAsState()

    var destino by rememberSaveable { mutableStateOf(Destino.META) }
    var overlay by rememberSaveable { mutableStateOf(Overlay.NINGUNO) }
    var menuAbierto by rememberSaveable { mutableStateOf(false) }

    // A confirmed aporte has to show up on the goal timeline: wire it once.
    LaunchedEffect(Unit) {
        sobreVm.onAporteConfirmed = { metaVm.refresh() }
    }
    // Entering "Mi Sobre" re-reads the decrypted balance (RAIZ's ON_RESUME
    // idiom, simplified: destination changes are the only entry point here).
    LaunchedEffect(destino) {
        if (destino == Destino.SOBRE) sobreVm.refreshStatus()
    }

    BackHandler(enabled = overlay != Overlay.NINGUNO || destino != Destino.META) {
        when {
            overlay != Overlay.NINGUNO -> overlay = Overlay.NINGUNO
            else -> destino = Destino.META
        }
    }

    val titulo = when (overlay) {
        Overlay.COSECHA -> "Cosechar"
        Overlay.AJUSTES -> "Ajustes"
        Overlay.NINGUNO -> if (destino == Destino.META) "La meta" else "Mi sobre"
    }
    val subtitulo = when (overlay) {
        Overlay.COSECHA -> meta.timeline.cosechaSubtitle()
        Overlay.AJUSTES -> meta.baseUrl
        Overlay.NINGUNO -> if (destino == Destino.META) meta.headerSubtitle() else sobre.headerSubtitle()
    }

    Scaffold(
        containerColor = RaizBackground,
        topBar = {
            Header(
                titulo = titulo,
                subtitulo = subtitulo,
                mostrarVolver = overlay != Overlay.NINGUNO,
                onVolver = { overlay = Overlay.NINGUNO },
                onActualizar = {
                    if (overlay == Overlay.NINGUNO && destino == Destino.SOBRE) {
                        sobreVm.refreshStatus()
                    } else {
                        metaVm.refresh()
                    }
                },
                menuAbierto = menuAbierto,
                onMenu = { menuAbierto = it },
                onCosechar = {
                    menuAbierto = false
                    overlay = Overlay.COSECHA
                },
                onAjustes = {
                    menuAbierto = false
                    overlay = Overlay.AJUSTES
                },
            )
        },
        bottomBar = {
            if (overlay == Overlay.NINGUNO) {
                NavigationBar(containerColor = RaizWhite) {
                    NavigationBarItem(
                        selected = destino == Destino.META,
                        onClick = { destino = Destino.META },
                        icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                        label = { Text("Meta") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RaizGreen,
                            selectedTextColor = RaizGreen,
                            indicatorColor = RaizGreen.copy(alpha = 0.12f),
                        ),
                    )
                    NavigationBarItem(
                        selected = destino == Destino.SOBRE,
                        onClick = { destino = Destino.SOBRE },
                        icon = { Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null) },
                        label = { Text("Mi sobre") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RaizGreen,
                            selectedTextColor = RaizGreen,
                            indicatorColor = RaizGreen.copy(alpha = 0.12f),
                        ),
                    )
                }
            }
        },
    ) { padding ->
        // Screens take contentPadding explicitly (RAIZ convention) and add their
        // own horizontal insets, so only the vertical part travels down.
        val inner = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        )

        when (overlay) {
            Overlay.COSECHA -> CosechaScreen(
                state = sobre,
                timeline = meta.timeline,
                onCosecharMiSobre = sobreVm::cosecharMiSobre,
                contentPadding = inner,
            )

            Overlay.AJUSTES -> AjustesScreen(
                baseUrl = meta.baseUrl,
                sourceLabel = meta.source.label,
                proverEstado = proverEstado,
                consola = consola,
                onSetBaseUrl = metaVm::setBaseUrl,
                contentPadding = inner,
            )

            Overlay.NINGUNO -> when (destino) {
                Destino.META -> MetaScreen(
                    state = meta,
                    onRefresh = { metaVm.refresh() },
                    onSelectSource = metaVm::selectSource,
                    onVerifyTotal = metaVm::verifyTotal,
                    contentPadding = inner,
                )

                Destino.SOBRE -> SobreScreen(
                    state = sobre,
                    onAmountChange = sobreVm::onAmountChange,
                    onAbrirSobre = sobreVm::abrirSobre,
                    onSellar = sobreVm::sellar,
                    onAportar = sobreVm::aportar,
                    onRefresh = sobreVm::refreshStatus,
                    contentPadding = inner,
                )
            }
        }
    }
}

@Composable
private fun Header(
    titulo: String,
    subtitulo: String,
    mostrarVolver: Boolean,
    onVolver: () -> Unit,
    onActualizar: () -> Unit,
    menuAbierto: Boolean,
    onMenu: (Boolean) -> Unit,
    onCosechar: () -> Unit,
    onAjustes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RaizBackground)
            .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (mostrarVolver) {
            IconButton(onClick = onVolver) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Volver", tint = RaizBlack)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (mostrarVolver) 0.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineMedium,
                color = RaizBlack,
            )
            Text(
                text = subtitulo,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                color = RaizBlack.copy(alpha = 0.55f),
            )
        }
        IconButton(onClick = onActualizar) {
            Icon(Icons.Filled.Refresh, contentDescription = "Actualizar", tint = RaizGreen)
        }
        Box {
            IconButton(onClick = { onMenu(true) }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Más", tint = RaizBlack)
            }
            DropdownMenu(expanded = menuAbierto, onDismissRequest = { onMenu(false) }) {
                DropdownMenuItem(
                    text = { Text("Cosechar") },
                    leadingIcon = {
                        Icon(Icons.Outlined.Eco, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = onCosechar,
                )
                DropdownMenuItem(
                    text = { Text("Ajustes") },
                    leadingIcon = {
                        Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = onAjustes,
                )
            }
        }
    }
}
