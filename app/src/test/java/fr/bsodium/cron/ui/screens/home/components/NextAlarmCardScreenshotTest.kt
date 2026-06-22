package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class NextAlarmCardScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun alarm_set() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                NextAlarmCard(
                    dateLabel = "Friday 22",
                    alarmTime = LocalTime(6, 40),
                    sessionDate = null,
                    sleepDurationLabel = null,
                    sleepSegments = emptyList(),
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
