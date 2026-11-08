package nl.giejay.android.tv.immich.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography

// System sans-serif only: no bundled font files, so no extra APK size or load time.
private val Sans = FontFamily.SansSerif

private fun style(size: Int, lineHeight: Int, weight: FontWeight, letterSpacing: Double = 0.0) =
    TextStyle(
        fontFamily = Sans,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        letterSpacing = letterSpacing.sp,
    )

// Sizes are larger than phone Material defaults because TVs are read from ~3 m away.
val ImmichTypography = Typography(
    displayLarge = style(57, 64, FontWeight.Normal, -0.25),
    displayMedium = style(45, 52, FontWeight.Normal),
    displaySmall = style(36, 44, FontWeight.Normal),
    headlineLarge = style(32, 40, FontWeight.SemiBold),
    headlineMedium = style(28, 36, FontWeight.SemiBold),
    headlineSmall = style(24, 32, FontWeight.SemiBold),
    titleLarge = style(22, 28, FontWeight.Medium),
    titleMedium = style(18, 24, FontWeight.Medium, 0.15),
    titleSmall = style(16, 22, FontWeight.Medium, 0.1),
    bodyLarge = style(18, 26, FontWeight.Normal, 0.3),
    bodyMedium = style(16, 22, FontWeight.Normal, 0.25),
    bodySmall = style(14, 20, FontWeight.Normal, 0.4),
    labelLarge = style(16, 20, FontWeight.Medium, 0.1),
    labelMedium = style(14, 18, FontWeight.Medium, 0.5),
    labelSmall = style(12, 16, FontWeight.Medium, 0.5),
)
