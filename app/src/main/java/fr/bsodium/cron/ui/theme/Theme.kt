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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/**
 * Cron's theme wrapper. On Android 12+ the palette flows from the user's
 * wallpaper via Material You; below that we drop back to a neutral
 * greyscale scheme. Light/dark follows the system setting in both paths.
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
    // Dark neutrals ship near-black; lift them a touch so the page, list rows and icon chips read as
    // distinct dark greys (Android-Settings feel) and the predictive-back card has contrast against the
    // dimmed page behind it. Light mode is already light, so it's left alone.
    val colorScheme = if (dark) base.liftedSurfaces() else base
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** Nudge every neutral surface role toward white by [amount], leaving text and accent roles untouched. */
private fun ColorScheme.liftedSurfaces(amount: Float = 0.06f): ColorScheme {
    fun Color.lift() = lerp(this, Color.White, amount)
    return copy(
        background = background.lift(),
        surface = surface.lift(),
        surfaceBright = surfaceBright.lift(),
        surfaceDim = surfaceDim.lift(),
        surfaceContainerLowest = surfaceContainerLowest.lift(),
        surfaceContainerLow = surfaceContainerLow.lift(),
        surfaceContainer = surfaceContainer.lift(),
        surfaceContainerHigh = surfaceContainerHigh.lift(),
        surfaceContainerHighest = surfaceContainerHighest.lift(),
        surfaceVariant = surfaceVariant.lift(),
    )
}
