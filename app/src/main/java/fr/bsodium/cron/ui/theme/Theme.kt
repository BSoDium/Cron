package fr.bsodium.cron.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Cron's theme wrapper. On Android 12+ the palette flows from the user's
 * wallpaper via Material You; below that we drop back to a neutral
 * greyscale scheme. Light/dark follows the system setting in both paths.
 *
 * The `inverseSurface` / `inverseOnSurface` pair is overridden after the
 * fact so the hero alarm card retains a strong light-on-dark / dark-on-light
 * contrast regardless of which dynamic palette the user lands on. (Some
 * wallpapers produce inverse pairs that are too close in luminance.)
 *
 * Wraps content in [MaterialExpressiveTheme] so M3 components render with
 * their expressive variants (pill buttons, larger radii, springy motion).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val base = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) FallbackDarkColors else FallbackLightColors
    }
    val colors = base.withHighContrastInverse(dark)
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}

private fun ColorScheme.withHighContrastInverse(dark: Boolean): ColorScheme =
    if (dark) copy(
        inverseSurface = Color(0xFFF2F2F2),
        inverseOnSurface = Color(0xFF0A0A0A),
    ) else copy(
        inverseSurface = Color(0xFF0A0A0A),
        inverseOnSurface = Color(0xFFF2F2F2),
    )
