package xyz.raiz.sobre.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * RAIZ palette, adopted verbatim (names and hex values unchanged) from the
 * parent RAIZ Android app — `com.raiz.app.ui.theme.Color`. Sobre del Barrio is
 * a RAIZ Protocol product; sharing the exact visual identity is the point, so
 * these constants are NOT renamed and NOT retuned.
 *
 * - RaizBlack: primary cards ("Mi sobre" balance card).
 * - RaizGreen: colorScheme PRIMARY — main CTA, confirmed value, success.
 * - RaizYellow: decorative accent (banners at low alpha, timeline icons).
 * - RaizPurple: secondary accents / badges. In THIS app it also carries the
 *   "there is a value here, deliberately not shown" semantics (hidden amounts).
 * - RaizBackground: general screen background.
 */
val RaizBlack = Color(0xFF1A1A1A)
val RaizYellow = Color(0xFFFBBF24)
val RaizPurple = Color(0xFF534AB7)
val RaizGreen = Color(0xFF0F6E56)
val RaizBackground = Color(0xFFFAFAF7)

// State / outline variants.
val RaizBlack80 = Color(0xCC1A1A1A)
val RaizGray = Color(0xFF707070)
val RaizGrayLight = Color(0xFFEAEAE6)
val RaizError = Color(0xFFB00020)
val RaizWhite = Color(0xFFFFFFFF)
