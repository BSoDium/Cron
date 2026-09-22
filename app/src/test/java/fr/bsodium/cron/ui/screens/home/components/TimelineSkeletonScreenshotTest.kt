package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
// Default Robolectric window is only ~320x470dp — too short to show the row stack settling before
// the fade-out, which is the whole point of this screenshot. A real phone-sized window instead.
@Config(qualifiers = "w360dp-h800dp")
class TimelineSkeletonScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun full_screen() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TimelineSkeleton(statusInsetTop = 24.dp, navInsetBottom = 0.dp)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "w360dp-h800dp-night")
    @Test
    fun full_screen_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        TimelineSkeleton(statusInsetTop = 24.dp, navInsetBottom = 0.dp)
                    }
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
