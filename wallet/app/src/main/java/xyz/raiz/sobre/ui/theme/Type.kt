package xyz.raiz.sobre.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * RAIZ typography, adopted verbatim. Material 3 defaults with four overrides:
 *   - displayLarge   48sp Bold      — the decrypted balance on "Mi sobre".
 *   - headlineMedium 22sp SemiBold  — screen titles and StatBox values.
 *   - bodyMedium     14sp Normal    — body copy, captions, monospace subtitles.
 *   - labelLarge     16sp SemiBold  — CTA buttons and row titles.
 *
 * The other 11 M3 styles stay at Material defaults. There is no res/font/ in
 * RAIZ, so FontFamily.Default is the real font — nothing to copy.
 */
val RaizTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
)
