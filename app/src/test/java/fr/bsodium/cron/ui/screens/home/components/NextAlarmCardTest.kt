package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
    fun renders_date_when_alarm_set() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                NextAlarmCard(
                    dateLabel = "Friday 22",
                    alarmTime = LocalTime(6, 40),
                    sessionDate = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Friday 22").assertIsDisplayed()
    }

    @Test
    fun renders_date_when_no_alarm() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                NextAlarmCard(
                    dateLabel = "Friday 22",
                    alarmTime = null,
                    sessionDate = null,
                )
            }
        }

        composeTestRule.onNodeWithText("Friday 22").assertIsDisplayed()
    }
}
