package nl.giejay.android.tv.immich.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val ImmichColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    tertiary = Tertiary,
    onTertiary = OnTertiary,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError,
    errorContainer = ErrorContainer,
    onErrorContainer = OnErrorContainer,
    border = Primary,
    borderVariant = Outline,
)

/** Root theme for all Compose UI in the app. TV is always dark. */
@Composable
fun ImmichTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ImmichColorScheme,
        shapes = ImmichShapes,
        typography = ImmichTypography,
        content = content,
    )
}
