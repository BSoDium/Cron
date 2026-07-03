@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
class TimelineNodeScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mixed_anchor_states() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    TimelineNode(
                        anchor = TimelineAnchor.Loader,
                        isFirst = true,
                        isLast = false,
                        title = { Text("Replanning", style = MaterialTheme.typography.bodyMedium) },
                        status = { Text("Latest · 07:16", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.Snooze),
                        isFirst = false,
                        isLast = false,
                        title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = {
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                MonoPill("9 min")
                                Text("07:15", style = CronTypography.labelMonoSmall, color = dim)
                            }
                        },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(
                            symbol = MaterialSymbol.Schedule,
                            tint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        isFirst = false,
                        isLast = false,
                        onClick = {},
                        title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                        status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Latest(MaterialSymbol.Schedule),
                        isFirst = false,
                        isLast = false,
                        onClick = {},
                        title = { Text("Re-planned", style = CronTypography.timelineHeroTitle) },
                        status = { Text("Latest · 23:27", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Plain,
                        isFirst = false,
                        isLast = false,
                        title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = { Text("23:40", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                        isFirst = false,
                        isLast = true,
                        title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = { Text("22:30", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                }
            }
        }
        // Let the latest-anchor's arrival morph (circle → Cookie9Sided) settle before capturing.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun node_with_content_area() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(
                            symbol = MaterialSymbol.Schedule,
                            tint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                        isFirst = true,
                        isLast = false,
                        onClick = {},
                        title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                        status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = dim) },
                        content = {
                            Text(
                                "Set alarm for 07:45. You have an 08:30 standup.",
                                style = MaterialTheme.typography.bodySmall,
                                color = dim,
                            )
                        },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.DirectionsWalk),
                        isFirst = false,
                        isLast = false,
                        title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = { Text("07:50", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Icon(MaterialSymbol.AlarmOff),
                        isFirst = false,
                        isLast = false,
                        title = { Text("Alarm dismissed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = { Text("07:45", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                    TimelineNode(
                        anchor = TimelineAnchor.Plain,
                        isFirst = false,
                        isLast = true,
                        title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                        status = { Text("23:10", style = CronTypography.labelMonoSmall, color = dim) },
                    )
                }
            }
        }
        composeTestRule.onRoot().captureRoboImage()
    }

    private fun latestAiRun(
        summary: String?,
        newAlarmTime: LocalTime?,
        previousAlarmTime: LocalTime?,
    ) = TimelineItem.AiRun(
        timestamp = Instant.fromEpochMilliseconds(0L),
        iteration = AiIterationUi(
            turnIndex = 0,
            timeLabel = "23:14",
            kind = RunKind.ScheduledBase,
            thread = AiThreadUi(
                turnIndex = 0,
                summary = summary,
                process = emptyList(),
                response = summary,
                newAlarmTime = newAlarmTime,
            ),
            previousAlarmTime = previousAlarmTime,
        ),
        sessionId = "s1",
        isStreaming = false,
        isLatest = true,
    )

    @Test
    fun ai_run_node_latest_shows_a_prev_new_time_pair_headline() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    AiRunNode(
                        item = latestAiRun(
                            summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                            newAlarmTime = LocalTime(7, 15),
                            previousAlarmTime = LocalTime(7, 45),
                        ),
                        isFirst = true,
                        isLast = true,
                        onClick = {},
                    )
                }
            }
        }
        // Let the latest-anchor's arrival morph (circle → Cookie9Sided) settle before capturing.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun ai_run_node_latest_shows_the_new_time_alone_with_no_previous() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    AiRunNode(
                        item = latestAiRun(
                            summary = "Set your first alarm for the day.",
                            newAlarmTime = LocalTime(7, 45),
                            previousAlarmTime = null,
                        ),
                        isFirst = true,
                        isLast = true,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun ai_run_node_latest_falls_back_to_prose_with_no_new_time() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                    AiRunNode(
                        item = latestAiRun(
                            summary = "Cancelled today's alarm — no early meetings on your calendar.",
                            newAlarmTime = null,
                            previousAlarmTime = LocalTime(7, 45),
                        ),
                        isFirst = true,
                        isLast = true,
                        onClick = {},
                    )
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}
