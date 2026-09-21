package fr.bsodium.cron.ui.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
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
        // ... (existing code)
    }

    @Test
    fun idle_state_shows_new_illustration() {
        composeTestRule.setContent {
            CronTheme {
                HomeIdleContent(
                    uiState = HomeUiState(
                        initialized = true,
                        greetingPrefix = "Good evening",
                        greetingName = "Elliot",
                        sessionDisplay = SessionDisplayState(
                            status = SessionStatus.Complete,
                            action = ActionType.DoNothing,
                            alarmTime = null,
                            reason = "",
                            sessionDate = LocalDate(2026, 6, 8),
                            snoozeCount = 0,
                        ),
                        autoAlarmsEnabled = true,
                        eveningTriggerTime = LocalTime(20, 0),
                    ),
                    statusInsetTop = 0.dp,
                    navInsetBottom = 0.dp,
                    hasNotificationPermission = true,
                    onNotifEnable = {},
                    onAutoAlarmsChange = {},
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }
}
