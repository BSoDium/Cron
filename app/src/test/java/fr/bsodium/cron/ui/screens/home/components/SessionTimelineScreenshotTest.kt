@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.junit.runner.RunWith

private fun fixedIteration(
    turn: Int,
    kind: RunKind,
    summary: String?,
    process: List<ProcessItem> = emptyList(),
) = AiIterationUi(
    turnIndex = turn,
    timeLabel = "23:14",
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = summary, process = process, response = summary),
)

/** Deterministic day header — bypasses the real-clock [TimelineItem.DayHeader] mapper so the golden
 *  screenshot never depends on which weekday the test happens to run on. */
private fun fixedDayHeader(isoDate: String, weekdayLabel: String, dateLabel: String) = TimelineItem.DayHeader(
    date = LocalDate.parse(isoDate),
    timestamp = Instant.fromEpochMilliseconds(0L),
    weekdayLabel = weekdayLabel,
    dateLabel = dateLabel,
)

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class SessionTimelineScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun timeline_spans_two_days_with_hero_run_and_accented_events() {
        composeTestRule.mainClock.autoAdvance = false
        val timeline = listOf(
            fixedDayHeader("2026-07-03", "TODAY", "3 JUL"),
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(
                    turn = 1,
                    kind = RunKind.Replan(TriggerType.CalendarChange),
                    summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                    process = listOf(
                        ProcessItem.Reasoning("Checking calendar for changes..."),
                        ProcessItem.Tool("read_calendar", isComplete = true, contextLabel = "3 events"),
                        ProcessItem.Tool("set_alarm", isComplete = true, contextLabel = "07:15"),
                    ),
                ),
                sessionId = "s1",
                isStreaming = false,
                isLatest = true,
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.AlarmSnoozed,
                label = "Alarm snoozed",
                detail = "9 min",
            ),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.SleepOnset,
                label = "You fell asleep",
                detail = null,
            ),
            fixedDayHeader("2026-07-02", "YESTERDAY", "2 JUL"),
            TimelineItem.Event(
                timestamp = Instant.fromEpochMilliseconds(0L),
                trigger = TriggerType.OutOfBedConfirmed,
                label = "You got up",
                detail = null,
            ),
            TimelineItem.AiRun(
                timestamp = Instant.fromEpochMilliseconds(0L),
                iteration = fixedIteration(turn = 0, kind = RunKind.ScheduledBase, summary = null),
                sessionId = "s2",
                isStreaming = false,
                isLatest = false,
            ),
        )
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
                    timeline.forEachIndexed { index, item ->
                        val isFirst = index == 0 || timeline[index - 1] is TimelineItem.DayHeader
                        val isLast = index == timeline.lastIndex || timeline[index + 1] is TimelineItem.DayHeader
                        when (item) {
                            is TimelineItem.AiRun -> AiRunNode(item = item, isFirst = isFirst, isLast = isLast, onClick = {})
                            is TimelineItem.Event -> EventNode(item = item, isFirst = isFirst, isLast = isLast)
                            is TimelineItem.DayHeader -> DayHeaderRow(item = item)
                        }
                    }
                }
            }
        }
        // Let the hero anchor's arrival morph settle before capturing.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}
