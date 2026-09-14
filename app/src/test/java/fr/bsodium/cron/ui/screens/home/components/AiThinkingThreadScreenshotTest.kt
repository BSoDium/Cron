package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class AiThinkingThreadScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val thread = AiThreadUi(
        turnIndex = 0,
        summary = "Replanned for your schedule",
        process = listOf(
            ProcessItem.Tool(name = "read_calendar", isComplete = true, contextLabel = "3 events"),
            ProcessItem.Narration("The earliest anchor is a 07:15 train, so the alarm needs to move earlier."),
            ProcessItem.Tool(name = "compute_commute", isComplete = true, contextLabel = "22 min"),
        ),
        response = "Alarm moved to 06:20 to make the 07:15 train.",
        durationSeconds = 8,
    )

    @Test
    fun collapsed() {
        composeTestRule.setContent {
            CronTheme {
                AiThinkingThread(thread = thread, expanded = false)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun expanded() {
        composeTestRule.setContent {
            CronTheme {
                AiThinkingThread(thread = thread, expanded = true)
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
