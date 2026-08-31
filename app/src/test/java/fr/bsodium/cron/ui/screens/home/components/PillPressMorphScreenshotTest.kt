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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
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

/** A genuine touch-driven interaction test would need to fight fling/settle timing for a fairly
 *  small visual payoff — this instead registers the exact same [AnchorShape.Pill] TimelineNode.kt
 *  builds, at fixed `pressProgress` values, directly against [TimelineTrackRegistry] (the same
 *  registry the real Composable writes into via `SideEffect`), so the rendered geometry is provably
 *  the real production shape at each program point rather than a mid-gesture guess. */
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class PillPressMorphScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun pill_press_morph_unpressed_and_pressed_render_a_rounded_square() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val registry = rememberTimelineTrackRegistry()
                val listState = rememberLazyListState()
                Box(Modifier.fillMaxSize().background(CronColors.pageBackground)) {
                    TimelineTrackOverlay(registry = registry, listState = listState)
                    Column(modifier = Modifier.padding(Spacing.xl)) {
                        PillPressExample(registry = registry, id = "unpressed", pressProgress = 0f)
                        PillPressExample(registry = registry, id = "half-pressed", pressProgress = 0.5f)
                        PillPressExample(registry = registry, id = "fully-pressed", pressProgress = 1f)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}

@Composable
private fun PillPressExample(registry: TimelineTrackRegistry, id: String, pressProgress: Float) {
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
                shape = AnchorShape.Pill(pressProgress = { pressProgress }),
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
