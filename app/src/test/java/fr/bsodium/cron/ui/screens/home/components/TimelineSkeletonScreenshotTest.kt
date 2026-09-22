package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
// Default Robolectric window is only ~320x470dp — too short to show all 6 initial-load rows settling
// before the fade-out tail kicks in. A real phone-sized window instead.
@Config(qualifiers = "w360dp-h800dp")
class TimelineSkeletonScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initial_load_six_rows() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.padding(Spacing.md)) {
                        TimelineRowsSkeleton(rowCount = 6)
                    }
                }
            }
        }
        // Mid-cycle, not frame zero — every row starts its pulse at fraction 0 (the low phase) until
        // its own staggered delay elapses, so a frame-zero capture would show them all identical and
        // prove nothing about the stagger. Advancing partway makes each row's own delay-driven offset
        // visible as a distinct shade.
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "w360dp-h800dp-night")
    @Test
    fun initial_load_six_rows_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.padding(Spacing.md)) {
                        TimelineRowsSkeleton(rowCount = 6)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun append_placeholder_two_rows() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.padding(Spacing.md)) {
                        TimelineRowsSkeleton(rowCount = 2)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
