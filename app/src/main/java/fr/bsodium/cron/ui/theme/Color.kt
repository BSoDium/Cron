package fr.bsodium.cron.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Cron's color system.
 *
 * The app ships with a single brand palette ([BrandColors]) — near-black
 * surfaces with a saturated orange accent. Material You / dynamic colors
 * are intentionally not used because the app's identity depends on the
 * fixed brand tone.
 *
 * The legacy [FallbackLightColors] / [FallbackDarkColors] are kept around
 * for any code paths that still reach for them, but [CronTheme] always
 * applies [BrandColors].
 */

// ---------------------------------------------------------------------------
// Brand palette (dark + orange) — the only palette CronTheme ever applies.
// ---------------------------------------------------------------------------

val BrandOrange = Color(0xFFFF6B2C)
val BrandOnOrange = Color(0xFFFFFFFF)
private val BrandBackground = Color(0xFF0A0A0A)
private val BrandSurface = Color(0xFF131316)
private val BrandSurfaceContainerLow = Color(0xFF111114)
private val BrandSurfaceContainer = Color(0xFF15151A)
private val BrandSurfaceContainerHigh = Color(0xFF1E1E24)
private val BrandOnBackground = Color(0xFFF2F2F2)
private val BrandOnSurfaceVariant = Color(0xFFA8A8AC)
private val BrandOutline = Color(0xFF2A2A30)

val BrandColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = BrandOnOrange,
    primaryContainer = BrandOrange,
    onPrimaryContainer = BrandOnOrange,
    secondary = BrandOrange,
    onSecondary = BrandOnOrange,
    secondaryContainer = BrandSurfaceContainer,
    onSecondaryContainer = BrandOnBackground,
    tertiary = BrandOrange,
    onTertiary = BrandOnOrange,
    tertiaryContainer = BrandSurfaceContainer,
    onTertiaryContainer = BrandOnBackground,
    background = BrandBackground,
    onBackground = BrandOnBackground,
    surface = BrandSurface,
    onSurface = BrandOnBackground,
    surfaceVariant = BrandSurfaceContainer,
    onSurfaceVariant = BrandOnSurfaceVariant,
    surfaceContainerLow = BrandSurfaceContainerLow,
    surfaceContainer = BrandSurfaceContainer,
    surfaceContainerHigh = BrandSurfaceContainerHigh,
    outline = BrandOutline,
    outlineVariant = BrandOutline,
    error = Color(0xFFFF6B5A),
    onError = Color(0xFF141414),
    errorContainer = Color(0xFF3D1310),
    onErrorContainer = Color(0xFFFFB4AB),
)

// ---------------------------------------------------------------------------
// Legacy fallback palettes — kept for code that still references them but
// no longer wired into CronTheme.
// ---------------------------------------------------------------------------

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
