package fr.bsodium.cron.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable

/**
 * Cron's theme wrapper. Always applies the brand dark-orange palette so
 * the app's identity is fixed regardless of system theme or wallpaper.
 *
 * Wraps content in [MaterialExpressiveTheme] so M3 components render with
 * their expressive variants (pill buttons, larger radii, springy motion).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = BrandColors,
        typography = Typography,
        content = content,
    )
}
