package xyz.raiz.sobre.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Sobre del Barrio MaterialTheme — RAIZ's color scheme and typography, renamed.
 * The ONE identifier that differs from RAIZ is the composable name
 * (`RaizTheme` -> `SobreTheme`): same tokens, distinct app.
 *
 * Palette -> Material 3 roles:
 *   - primary    = green  (dominant CTA, indicators, success)
 *   - onPrimary  = white
 *   - secondary  = purple (accents, badges, hidden-amount pills)
 *   - tertiary   = yellow (decorative accent only, never a call to action)
 *   - background = #FAFAF7
 *   - surface    = white  (normal cards)
 *   - onSurface  = black
 *
 * For "black" cards (the sobre balance) use `RaizBlack` directly instead of a
 * colorScheme role — explicit and it does not fight Material.
 *
 * Light scheme only: the approved design has no dark mode for the MVP.
 *
 * ONLY these 13 M3 roles are set. `surfaceVariant`, `primaryContainer`,
 * `outlineVariant` and friends fall back to Material defaults — do not rely on
 * them for brand color.
 */
private val SobreLightColorScheme = lightColorScheme(
    primary = RaizGreen,
    onPrimary = RaizWhite,
    secondary = RaizPurple,
    onSecondary = RaizWhite,
    tertiary = RaizYellow,
    onTertiary = RaizBlack,
    background = RaizBackground,
    onBackground = RaizBlack,
    surface = RaizWhite,
    onSurface = RaizBlack,
    error = RaizError,
    onError = RaizWhite,
    outline = RaizGrayLight,
)

@Composable
fun SobreTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SobreLightColorScheme,
        typography = RaizTypography,
        content = content,
    )
}
