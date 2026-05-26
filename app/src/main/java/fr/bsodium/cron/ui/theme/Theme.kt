package fr.bsodium.cron.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Cron's theme wrapper.
 *
 * On API 31+, the color scheme is sourced from Material You so the app
 * tracks the user's wallpaper. Pre-31 devices fall back to a neutral
 * cream/charcoal palette with no brand color — see [FallbackLightColors]
 * and [FallbackDarkColors] for the constants.
 *
 * Either path wraps in [MaterialExpressiveTheme] so M3 components render
 * with their expressive variants (pill buttons, larger radii, springy
 * motion). Typography is still [Typography] (Google Sans Flex / Roboto
 * Flex with the expressive scale).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else ->
            if (darkTheme) FallbackDarkColors else FallbackLightColors
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
