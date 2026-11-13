package app.tunefy.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.tunefy.android.data.model.ThemeMode

private val LightScheme = lightColorScheme(
    primary = TuneFyColors.DeepBlue,
    onPrimary = Color.White,
    primaryContainer = TuneFyColors.PastelBlue,
    onPrimaryContainer = Color(0xFF0B2033),
    secondary = TuneFyColors.Blue,
    onSecondary = Color(0xFF07182B),
    secondaryContainer = Color(0xFFD8E7FC),
    onSecondaryContainer = Color(0xFF102A44),
    tertiary = Color(0xFF4F7FB9),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDDEAF8),
    onTertiaryContainer = Color(0xFF152C41),
    background = TuneFyColors.BackgroundLight,
    onBackground = TuneFyColors.Ink,
    surface = TuneFyColors.DaySurface,
    onSurface = TuneFyColors.Ink,
    surfaceVariant = TuneFyColors.DaySurfaceVariant,
    onSurfaceVariant = Color(0xFF44566C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFCFF),
    surfaceContainer = Color(0xFFEFF5FD),
    surfaceContainerHigh = Color(0xFFE9F1FB),
    surfaceContainerHighest = Color(0xFFE2ECF8),
    outline = TuneFyColors.DayOutline,
    outlineVariant = Color(0xFFDCE6F2),
    error = TuneFyColors.Danger,
    onError = Color.White,
    errorContainer = Color(0xFFFBE1E1),
    onErrorContainer = Color(0xFF511616),
    inverseSurface = Color(0xFF1B2735),
    inverseOnSurface = Color(0xFFEDF3FA),
    inversePrimary = TuneFyColors.PastelBlue
)

private val DarkScheme = darkColorScheme(
    primary = TuneFyColors.PastelBlue,
    onPrimary = Color(0xFF0B2033),
    primaryContainer = TuneFyColors.DeepBlue,
    onPrimaryContainer = Color(0xFFE5F0FF),
    secondary = TuneFyColors.Blue,
    onSecondary = Color(0xFF061726),
    secondaryContainer = Color(0xFF24405F),
    onSecondaryContainer = Color(0xFFD7E7FD),
    tertiary = Color(0xFF9CC2F0),
    onTertiary = Color(0xFF0B2033),
    tertiaryContainer = Color(0xFF28425F),
    onTertiaryContainer = Color(0xFFDCEBFF),
    background = TuneFyColors.BackgroundDark,
    onBackground = Color(0xFFE8F0FA),
    surface = TuneFyColors.NightSurface,
    onSurface = Color(0xFFE8F0FA),
    surfaceVariant = TuneFyColors.NightSurfaceVariant,
    onSurfaceVariant = Color(0xFFB3C4D8),
    surfaceContainerLowest = Color(0xFF0C131B),
    surfaceContainerLow = Color(0xFF131E29),
    surfaceContainer = Color(0xFF17242F),
    surfaceContainerHigh = Color(0xFF1C2A38),
    surfaceContainerHighest = Color(0xFF223141),
    outline = TuneFyColors.NightOutline,
    outlineVariant = Color(0xFF283A4E),
    error = Color(0xFFF2A6A6),
    onError = Color(0xFF3D0B0B),
    errorContainer = Color(0xFF522020),
    onErrorContainer = Color(0xFFFFDADA),
    inverseSurface = Color(0xFFE8F0FA),
    inverseOnSurface = Color(0xFF16212E),
    inversePrimary = TuneFyColors.DeepBlue
)

@Composable
fun TuneFyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TuneFyTypography,
        shapes = TuneFyShapes,
        content = content
    )
}
