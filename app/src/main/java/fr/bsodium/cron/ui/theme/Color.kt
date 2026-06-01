package fr.bsodium.cron.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Fallback color schemes used on Android < 12, where Material You dynamic colors
 * aren't available. Neutral greyscale palette with a warm accent so the app
 * still feels intentional without imposing a brand hue.
 *
 * On Android 12+ the runtime palette is sourced from
 * `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)` —
 * see [CronTheme].
 */

private val FallbackAccent = Color(0xFFB4A89A)
private val FallbackOnAccent = Color(0xFF1A1714)

// ---------------------------------------------------------------------------
// Dark fallback palette
// ---------------------------------------------------------------------------

private val DarkBackground = Color(0xFF0A0A0A)
private val DarkSurface = Color(0xFF131316)
private val DarkSurfaceContainerLow = Color(0xFF111114)
private val DarkSurfaceContainer = Color(0xFF15151A)
private val DarkSurfaceContainerHigh = Color(0xFF1E1E24)
private val DarkOnBackground = Color(0xFFF2F2F2)
private val DarkOnSurfaceVariant = Color(0xFFA8A8AC)
private val DarkOutline = Color(0xFF2A2A30)

val FallbackDarkColors = darkColorScheme(
    primary = FallbackAccent,
    onPrimary = FallbackOnAccent,
    primaryContainer = FallbackAccent,
    onPrimaryContainer = FallbackOnAccent,
    secondary = FallbackAccent,
    onSecondary = FallbackOnAccent,
    secondaryContainer = DarkSurfaceContainer,
    onSecondaryContainer = DarkOnBackground,
    tertiary = FallbackAccent,
    onTertiary = FallbackOnAccent,
    tertiaryContainer = DarkSurfaceContainer,
    onTertiaryContainer = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    inversePrimary = FallbackAccent,
    error = Color(0xFFFF6B5A),
    onError = Color(0xFF141414),
    errorContainer = Color(0xFF3D1310),
    onErrorContainer = Color(0xFFFFB4AB),
)

// ---------------------------------------------------------------------------
// Light fallback palette
// ---------------------------------------------------------------------------

private val LightBackground = Color(0xFFF8F5F0)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF2EDE5)
private val LightSurfaceContainer = Color(0xFFEAE4D9)
private val LightSurfaceContainerHigh = Color(0xFFE0D9CC)
private val LightOnBackground = Color(0xFF141414)
private val LightOnSurfaceVariant = Color(0xFF6B6B6B)
private val LightOutline = Color(0xFFD9D2C7)

val FallbackLightColors = lightColorScheme(
    primary = FallbackOnAccent,
    onPrimary = Color.White,
    primaryContainer = FallbackAccent,
    onPrimaryContainer = FallbackOnAccent,
    secondary = FallbackOnAccent,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceContainer,
    onSecondaryContainer = LightOnBackground,
    tertiary = FallbackOnAccent,
    onTertiary = Color.White,
    tertiaryContainer = LightSurfaceContainer,
    onTertiaryContainer = LightOnBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceContainer,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutline,
    inversePrimary = FallbackAccent,
    error = Color(0xFFD14638),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DA),
    onErrorContainer = Color(0xFF5C1410),
)
