package fr.bsodium.cron.ui.screens.home

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
class PlanDetailScreenScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun settled_turn_uses_shared_page_app_bar() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                PlanDetailScreen(
                    iteration = AiIterationUi(
                        turnIndex = 0,
                        timeLabel = "23:14",
                        kind = RunKind.ScheduledBase,
                        thread = AiThreadUi(
                            turnIndex = 0,
                            summary = "Set a 6:40 alarm",
                            process = listOf(
                                ProcessItem.Tool(name = "read_calendar", isComplete = true, contextLabel = "6 events"),
                                ProcessItem.Tool(name = "set_alarm", isComplete = true, contextLabel = "set for 06:40"),
                            ),
                            response = "Set a **6:40** alarm so you make your 9:00 stand-up.",
                            durationSeconds = 15,
                        ),
                        ranAtEpochMs = 0L,
                    ),
                    hapticsEnabled = false,
                    onBack = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
