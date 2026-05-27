package fr.bsodium.cron.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Cron's color system.
 *
 * The brand identity is dark surfaces + saturated orange. Dynamic Material You
 * colors are intentionally not used so the brand tone never shifts with the
 * wallpaper. The dark palette ([BrandColors]) is mirrored by a warm-cream light
 * palette ([BrandLightColors]); [CronTheme] picks one based on the system's
 * dark-theme setting.
 *
 * The `inverseSurface` / `inverseOnSurface` slots are explicitly populated on
 * both palettes so the high-contrast alarm card (white-on-dark page or
 * dark-on-light page) reads correctly in either mode.
 */

val BrandOrange = Color(0xFFFF6B2C)
val BrandOnOrange = Color(0xFFFFFFFF)

// ---------------------------------------------------------------------------
// Dark palette (default brand identity)
// ---------------------------------------------------------------------------

private val BrandDarkBackground = Color(0xFF0A0A0A)
private val BrandDarkSurface = Color(0xFF131316)
private val BrandDarkSurfaceContainerLow = Color(0xFF111114)
private val BrandDarkSurfaceContainer = Color(0xFF15151A)
private val BrandDarkSurfaceContainerHigh = Color(0xFF1E1E24)
private val BrandDarkOnBackground = Color(0xFFF2F2F2)
private val BrandDarkOnSurfaceVariant = Color(0xFFA8A8AC)
private val BrandDarkOutline = Color(0xFF2A2A30)

val BrandColors = darkColorScheme(
    primary = BrandOrange,
    onPrimary = BrandOnOrange,
    primaryContainer = BrandOrange,
    onPrimaryContainer = BrandOnOrange,
    secondary = BrandOrange,
    onSecondary = BrandOnOrange,
    secondaryContainer = BrandDarkSurfaceContainer,
    onSecondaryContainer = BrandDarkOnBackground,
    tertiary = BrandOrange,
    onTertiary = BrandOnOrange,
    tertiaryContainer = BrandDarkSurfaceContainer,
    onTertiaryContainer = BrandDarkOnBackground,
    background = BrandDarkBackground,
    onBackground = BrandDarkOnBackground,
    surface = BrandDarkSurface,
    onSurface = BrandDarkOnBackground,
    surfaceVariant = BrandDarkSurfaceContainer,
    onSurfaceVariant = BrandDarkOnSurfaceVariant,
    surfaceContainerLow = BrandDarkSurfaceContainerLow,
    surfaceContainer = BrandDarkSurfaceContainer,
    surfaceContainerHigh = BrandDarkSurfaceContainerHigh,
    outline = BrandDarkOutline,
    outlineVariant = BrandDarkOutline,
    // Inverse pair powers the high-contrast alarm card: light surface in dark mode.
    inverseSurface = Color(0xFFF2F2F2),
    inverseOnSurface = Color(0xFF0A0A0A),
    inversePrimary = BrandOrange,
    error = Color(0xFFFF6B5A),
    onError = Color(0xFF141414),
    errorContainer = Color(0xFF3D1310),
    onErrorContainer = Color(0xFFFFB4AB),
)

// ---------------------------------------------------------------------------
// Light palette (warm cream + brand orange, system-driven)
// ---------------------------------------------------------------------------

private val BrandLightBackground = Color(0xFFF8F5F0)
private val BrandLightSurface = Color(0xFFFFFFFF)
private val BrandLightSurfaceContainerLow = Color(0xFFF2EDE5)
private val BrandLightSurfaceContainer = Color(0xFFEAE4D9)
private val BrandLightSurfaceContainerHigh = Color(0xFFE0D9CC)
private val BrandLightOnBackground = Color(0xFF141414)
private val BrandLightOnSurfaceVariant = Color(0xFF6B6B6B)
private val BrandLightOutline = Color(0xFFD9D2C7)

val BrandLightColors = lightColorScheme(
    primary = BrandOrange,
    onPrimary = BrandOnOrange,
    primaryContainer = BrandOrange,
    onPrimaryContainer = BrandOnOrange,
    secondary = BrandOrange,
    onSecondary = BrandOnOrange,
    secondaryContainer = BrandLightSurfaceContainer,
    onSecondaryContainer = BrandLightOnBackground,
    tertiary = BrandOrange,
    onTertiary = BrandOnOrange,
    tertiaryContainer = BrandLightSurfaceContainer,
    onTertiaryContainer = BrandLightOnBackground,
    background = BrandLightBackground,
    onBackground = BrandLightOnBackground,
    surface = BrandLightSurface,
    onSurface = BrandLightOnBackground,
    surfaceVariant = BrandLightSurfaceContainer,
    onSurfaceVariant = BrandLightOnSurfaceVariant,
    surfaceContainerLow = BrandLightSurfaceContainerLow,
    surfaceContainer = BrandLightSurfaceContainer,
    surfaceContainerHigh = BrandLightSurfaceContainerHigh,
    outline = BrandLightOutline,
    outlineVariant = BrandLightOutline,
    // Inverse pair powers the high-contrast alarm card: dark surface in light mode.
    inverseSurface = Color(0xFF0A0A0A),
    inverseOnSurface = Color(0xFFF2F2F2),
    inversePrimary = BrandOrange,
    error = Color(0xFFD14638),
    onError = Color.White,
    errorContainer = Color(0xFFFFE0DA),
    onErrorContainer = Color(0xFF5C1410),
)
