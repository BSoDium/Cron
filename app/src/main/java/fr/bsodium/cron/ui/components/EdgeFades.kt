package fr.bsodium.cron.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Non-interactive top and bottom gradient scrims drawn over edge-to-edge content.
 * The top one fades content into the background under the status bar / notch; the
 * bottom one lifts the floating nav pill off the content. Placed as the last child
 * of a full-size [Box] so it overlays the screen; it has no pointer modifiers, so
 * touches pass straight through to the content below.
 *
 * [showTopScrim] is off for pages with a [PageAppBar]: the app bar already owns the
 * status-bar strip, and its scrolled `surfaceContainer` shade would otherwise show a
 * two-tone band under the `background`-tinted top scrim.
 *
 * [showNavPillClearance] is off for routes where the floating nav pill itself is hidden (settings
 * sub-screens) — without this, every route reserved [Spacing.navBarClearance] worth of scrim
 * regardless, needlessly dimming/obscuring a pill-less screen's own last few rows of content.
 */
@Composable
fun EdgeFades(modifier: Modifier = Modifier, showTopScrim: Boolean = true, showNavPillClearance: Boolean = true) {
    val background = CronColors.pageBackground
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val pillClearance = if (showNavPillClearance) Spacing.navBarClearance else 0.dp
    Box(modifier = modifier.fillMaxSize()) {
        if (showTopScrim) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(statusTop)
                    .background(Brush.verticalGradient(listOf(background, Color.Transparent))),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(navBottom + pillClearance + Spacing.xxxl)
                .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
        )
    }
}
