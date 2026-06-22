package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SwitchRowScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun switch_on() {
        composeTestRule.setContent {
            CronTheme {
                SwitchRow(
                    title = "Haptic feedback",
                    subtitle = "Subtle ticks while the assistant writes",
                    checked = true,
                    onCheckedChange = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun switch_off() {
        composeTestRule.setContent {
            CronTheme {
                SwitchRow(
                    title = "Haptic feedback",
                    subtitle = "Subtle ticks while the assistant writes",
                    checked = false,
                    onCheckedChange = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
