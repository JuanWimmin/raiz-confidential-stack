package xyz.raiz.sobre.spike

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import xyz.raiz.sobre.ui.meta.MetaViewModel
import xyz.raiz.sobre.ui.nav.ProverViewModel
import xyz.raiz.sobre.ui.nav.SobreApp
import xyz.raiz.sobre.ui.sobre.SobreViewModel
import xyz.raiz.sobre.ui.theme.SobreTheme

/**
 * Sobre del Barrio — the Compose host. Session 6 replaced the Session 5 debug
 * console that used to live here (imperative Views, six buttons) with the three
 * real screens; the pipeline underneath is byte-for-byte the same one that
 * console proved on this phone.
 *
 * The package is still `…sobre.spike` ON PURPOSE: `AndroidManifest.xml` names
 * this class fully qualified and that file belongs to another agent's lane this
 * session. Renaming the package is a two-line follow-up (this file's `package`
 * + the manifest's `android:name`), not something to do hours before a demo.
 *
 * Three ViewModels, no Hilt (raiz-reuse-plan §1.3), all scoped to this Activity:
 *  - [ProverViewModel] owns the ONE WebView bridge and the CtWallet. It is a
 *    ViewModel and not a `remember` because disposing it mid-proof kills a
 *    10-30 s operation — that bug already cost this project two fake timeouts.
 *  - [MetaViewModel] reads the goal timeline through the configurable event
 *    source and verifies the total with the published view key.
 *  - [SobreViewModel] drives register/deposit/transfer/merge.
 *
 * Rotation never reaches any of them: the manifest keeps
 * `configChanges="orientation|screenSize|screenLayout|keyboardHidden"`, so the
 * Activity is not recreated and a proof in flight simply continues.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SobreTheme {
                val prover: ProverViewModel = viewModel(
                    factory = ProverViewModel.factory(application),
                )
                val metaVm: MetaViewModel = viewModel(
                    factory = MetaViewModel.factory(application, prover),
                )
                val sobreVm: SobreViewModel = viewModel(
                    factory = SobreViewModel.factory(application, prover),
                )
                SobreApp(metaVm = metaVm, sobreVm = sobreVm, proverVm = prover)
            }
        }
    }
}
