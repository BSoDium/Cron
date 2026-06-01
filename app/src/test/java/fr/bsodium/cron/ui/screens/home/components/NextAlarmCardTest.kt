package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import fr.bsodium.cron.session.model.SleepSegment
import fr.bsodium.cron.session.model.SleepStage
import fr.bsodium.cron.testutil.Fixtures
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION") // createComposeRule v1 is fine here; v2 changes dispatcher semantics.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class NextAlarmCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun renders_date_and_sleep_section_when_alarm_set() {
        // The LCD countdown polls Clock.System.now() forever; freeze the clock so the test reaches idle.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                NextAlarmCard(
                    dateLabel = "Friday 22",
                    alarmTime = LocalTime(6, 40),
                    sleepDurationLabel = "7h 30m",
                    sleepSegments = listOf(
                        SleepSegment(SleepStage.Deep, Fixtures.at("2026-05-22T01:00:00Z"), Fixtures.at("2026-05-22T05:00:00Z")),
                    ),
                )
            }
        }

        composeTestRule.onNodeWithText("Friday 22").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sleep").assertIsDisplayed()
        composeTestRule.onNodeWithText("7h 30m").assertIsDisplayed()
    }

    @Test
    fun omits_sleep_section_when_no_segments() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                NextAlarmCard(
                    dateLabel = "Friday 22",
                    alarmTime = null,
                    sleepDurationLabel = null,
                    sleepSegments = emptyList(),
                )
            }
        }

        composeTestRule.onNodeWithText("Friday 22").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sleep").assertDoesNotExist()
    }
}
