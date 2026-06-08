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

private val DarkBackground = Color(0xFF0A0A0A)
private val DarkSurface = Color(0xFF131316)
private val DarkSurfaceContainerLow = Color(0xFF111114)
private val DarkSurfaceContainer = Color(0xFF15151A)
private val DarkSurfaceContainerHigh = Color(0xFF1E1E24)
private val DarkOnBackground = Color(0xFFF2F2F2)
private val DarkOnSurfaceVariant = Color(0xFFA8A8AC)
private val DarkOutline = Color(0xFF2A2A30)

// Varied accents (Material You "apply varied accents") for the non-dynamic fallback — a cool teal
// secondary and a soft blue tertiary alongside the warm primary, so accent-coded UI (trigger headers,
// containers) reads as more than one hue on Android < 12. Dynamic colour supplies these on 12+.
private val DarkSecondary = Color(0xFF8FC7B3)
private val DarkSecondaryContainer = Color(0xFF1E2A26)
private val DarkOnSecondaryContainer = Color(0xFFBFE3D5)
private val DarkTertiary = Color(0xFF9CB8E0)
private val DarkTertiaryContainer = Color(0xFF1C2330)
private val DarkOnTertiaryContainer = Color(0xFFC9DCF2)

val FallbackDarkColors = darkColorScheme(
    primary = FallbackAccent,
    onPrimary = FallbackOnAccent,
    primaryContainer = FallbackAccent,
    onPrimaryContainer = FallbackOnAccent,
    secondary = DarkSecondary,
    onSecondary = FallbackOnAccent,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = FallbackOnAccent,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
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

private val LightBackground = Color(0xFFF8F5F0)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF2EDE5)
private val LightSurfaceContainer = Color(0xFFEAE4D9)
private val LightSurfaceContainerHigh = Color(0xFFE0D9CC)
private val LightOnBackground = Color(0xFF141414)
private val LightOnSurfaceVariant = Color(0xFF6B6B6B)
private val LightOutline = Color(0xFFD9D2C7)

// Varied accents for the light fallback — deep teal secondary, deep blue tertiary (see the dark note).
private val LightSecondary = Color(0xFF3D6655)
private val LightSecondaryContainer = Color(0xFFD6E8DF)
private val LightOnSecondaryContainer = Color(0xFF15291F)
private val LightTertiary = Color(0xFF3A5680)
private val LightTertiaryContainer = Color(0xFFDAE2F1)
private val LightOnTertiaryContainer = Color(0xFF152030)

val FallbackLightColors = lightColorScheme(
    primary = FallbackOnAccent,
    onPrimary = Color.White,
    primaryContainer = FallbackAccent,
    onPrimaryContainer = FallbackOnAccent,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = Color.White,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
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
