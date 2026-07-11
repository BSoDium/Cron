package fr.bsodium.cron.ui.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The timeline's left inset (`Spacing.md`) must line up with the alarm card's own left edge — this
 *  renders both together so the alignment is directly checkable. */
@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class HomeContentScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timeline_gutter_aligns_with_the_alarm_card_left_edge() {
        composeTestRule.mainClock.autoAdvance = false
        val iterations = listOf(
            AiIterationUi(
                turnIndex = 0,
                timeLabel = "21:30",
                kind = RunKind.ScheduledBase,
                thread = AiThreadUi(turnIndex = 0, summary = "Set alarm for 07:45.", process = emptyList(), response = "Alarm set for **07:45**."),
                ranAtEpochMs = System.currentTimeMillis(),
            ),
        )
        composeTestRule.setContent {
            CronTheme {
                HomePlanContent(
                    uiState = HomeUiState(
                        initialized = true,
                        dateLabel = "Friday, 3 Jul",
                        aiPlan = AiPlanUi(iterations = iterations),
                        timeline = buildTimeline(
                            listOf(
                                TimelineSession(
                                    sessionId = "s1",
                                    iterations = iterations,
                                    events = emptyList(),
                                    streamingTurnIndex = null,
                                ),
                            ),
                        ),
                    ),
                    statusInsetTop = 24.dp,
                    navInsetBottom = 0.dp,
                    hasNotificationPermission = true,
                    onNotifEnable = {},
                    onAutoAlarmsChange = {},
                    onOpenAiRun = { _, _ -> },
                    onNavigateToHistory = {},
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}
