package fr.bsodium.cron.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/**
 * Cron's theme wrapper. Picks [BrandColors] or [BrandLightColors] based on
 * the device's dark-theme setting, so the page background flips between
 * near-black and warm cream while the brand orange stays put.
 *
 * Wraps content in [MaterialExpressiveTheme] so M3 components render with
 * their expressive variants (pill buttons, larger radii, springy motion).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) BrandColors else BrandLightColors
    MaterialExpressiveTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}
