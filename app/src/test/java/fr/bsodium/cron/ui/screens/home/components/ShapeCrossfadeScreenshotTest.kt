@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** Phase 11 (docs/color-roles.md) — verifies `TimelineTrackOverlay.drawSocket`'s Circle↔Pill crossfade
 *  blend at fixed fractions, mirroring `PillPressMorphScreenshotTest.kt`'s technique: register a fixed
 *  [AnchorDescriptor] directly against [TimelineTrackRegistry] rather than driving a real
 *  `animateFloatAsState`/`Animatable`, so the rendered geometry is deterministic and independent of
 *  spring timing — this only proves the blend logic itself is correct at a known fraction, not that the
 *  full transition feels synchronized in real time (that needs a live device, see docs/color-roles.md
 *  Round 40's own note on why this bug class specifically evades static-frame verification). */
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class ShapeCrossfadeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun circle_to_pill_crossfade_at_zero_half_and_full_progress() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val registry = rememberTimelineTrackRegistry()
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize().background(CronColors.pageBackground)) {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    Column(modifier = Modifier.padding(Spacing.xl)) {
                        ShapeCrossfadeExample(registry = registry, id = "start", fraction = 0f)
                        ShapeCrossfadeExample(registry = registry, id = "mid", fraction = 0.5f)
                        ShapeCrossfadeExample(registry = registry, id = "end", fraction = 1f)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}

@Composable
private fun ShapeCrossfadeExample(registry: TimelineTrackRegistry, id: String, fraction: Float) {
    val density = LocalDensity.current
    val accentColor = MaterialTheme.colorScheme.secondary
    Box(
        modifier = Modifier
            .size(TRACK_WIDTH)
            .onGloballyPositioned { coords -> registry.setPosition(id, coords) },
    )
    SideEffect {
        registry.setDescriptor(
            id,
            AnchorDescriptor(
                contentRadiusPx = with(density) { INTERIOR_ANCHOR_SIZE.toPx() / 2f },
                shape = AnchorShape.Pill(),
                outgoingShape = AnchorShape.Circle,
                shapeCrossfadeFraction = fraction,
                accentColor = accentColor,
                isSegmentTop = false,
                isSegmentBottom = false,
                asleepAbove = false,
                asleepBelow = false,
                isLatest = false,
            ),
        )
    }
}
