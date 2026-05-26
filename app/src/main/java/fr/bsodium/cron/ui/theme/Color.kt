package fr.bsodium.cron.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Color schemes for devices that don't support Material You dynamic color
 * (API ≤30). On API 31+, [CronTheme] uses `dynamicLightColorScheme(context)`
 * / `dynamicDarkColorScheme(context)` so the palette tracks the user's
 * wallpaper — these constants only kick in below that.
 *
 * The fallback keeps the warm cream-on-charcoal surfaces the user designed,
 * but replaces every "brand" slot with a neutral tan / warm-grey derived
 * from the same surface palette. There is intentionally no brand color.
 */

// Light surfaces (warm cream)
private val LightBackground = Color(0xFFF8F5F0)
private val LightSurface = Color(0xFFFFFFFF)
private val LightSurfaceVariant = Color(0xFFEFEAE1)
private val LightSurfaceContainerLow = Color(0xFFF2EDE5)
private val LightSurfaceContainer = Color(0xFFEAE4D9)
private val LightSurfaceContainerHigh = Color(0xFFE0D9CC)
private val LightOnBackground = Color(0xFF141414)
private val LightOnSurfaceVariant = Color(0xFF6B6B6B)
private val LightOutline = Color(0xFFD9D2C7)

// Neutral accent — warm graphite used in place of the old burnt-orange brand
private val NeutralPrimaryLight = Color(0xFF3A352D)
private val NeutralPrimaryContainerLight = Color(0xFFD8CFC1)
private val NeutralOnPrimaryContainerLight = Color(0xFF1F1B14)

// Dark surfaces (true near-black)
private val DarkBackground = Color(0xFF0A0A0A)
private val DarkSurface = Color(0xFF141414)
private val DarkSurfaceVariant = Color(0xFF1F1F1F)
private val DarkSurfaceContainerLow = Color(0xFF101010)
private val DarkSurfaceContainer = Color(0xFF1A1A1A)
private val DarkSurfaceContainerHigh = Color(0xFF252525)
private val DarkOnBackground = Color(0xFFF5F5F5)
private val DarkOnSurfaceVariant = Color(0xFFA8A8A8)
private val DarkOutline = Color(0xFF2A2A2A)

private val NeutralPrimaryDark = Color(0xFFE6DECF)
private val NeutralPrimaryContainerDark = Color(0xFF2B2620)
private val NeutralOnPrimaryContainerDark = Color(0xFFE6DECF)

val FallbackLightColors = lightColorScheme(
    primary = NeutralPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = NeutralPrimaryContainerLight,
    onPrimaryContainer = NeutralOnPrimaryContainerLight,
    secondary = NeutralPrimaryLight,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceContainer,
    onSecondaryContainer = LightOnBackground,
    tertiary = NeutralPrimaryLight,
    onTertiary = Color.White,
    tertiaryContainer = LightSurfaceContainer,
    onTertiaryContainer = LightOnBackground,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightSurfaceContainer,
    error = Color(0xFFD14638),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DA),
    onErrorContainer = Color(0xFF5C1410),
)

val FallbackDarkColors = darkColorScheme(
    primary = NeutralPrimaryDark,
    onPrimary = Color(0xFF141414),
    primaryContainer = NeutralPrimaryContainerDark,
    onPrimaryContainer = NeutralOnPrimaryContainerDark,
    secondary = NeutralPrimaryDark,
    onSecondary = Color(0xFF141414),
    secondaryContainer = DarkSurfaceContainer,
    onSecondaryContainer = DarkOnBackground,
    tertiary = NeutralPrimaryDark,
    onTertiary = Color(0xFF141414),
    tertiaryContainer = DarkSurfaceContainer,
    onTertiaryContainer = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnBackground,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = Color(0xFF202020),
    error = Color(0xFFFF6B5A),
    onError = Color(0xFF141414),
    errorContainer = Color(0xFF3D1310),
    onErrorContainer = Color(0xFFFFB4AB),
)
