@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        TimelineNode(
                            id = "loader",
                            registry = registry,
                            anchor = TimelineAnchor.Loader,
                            isSegmentTop = true,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Replanning", style = MaterialTheme.typography.bodyMedium) },
                            status = { Text("Latest · 07:16", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // Interior + Negative valence → smaller Triangle silhouette (in an accent container so the carved shape reads against the track).
                        TimelineNode(
                            id = "snooze",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.Snooze,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                valence = TimelineValence.Negative,
                            ),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Alarm snoozed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    MonoPill("9 min")
                                    Text("07:15", style = CronTypography.labelMonoSmall, color = dim)
                                }
                            },
                        )
                        // Interior + Positive valence → smaller Flower silhouette.
                        TimelineNode(
                            id = "gotup",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.DirectionsWalk,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                valence = TimelineValence.Positive,
                            ),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:50", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "planned",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.Schedule,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                            title = { Text("Planned", style = MaterialTheme.typography.bodyMedium) },
                            status = { Text("Latest · 23:14", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "replanned",
                            registry = registry,
                            anchor = TimelineAnchor.Latest(MaterialSymbol.Schedule),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                            title = { Text("Re-planned", style = CronTypography.timelineHeroTitle) },
                            status = { Text("Latest · 23:27", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "asleep",
                            registry = registry,
                            anchor = TimelineAnchor.Plain,
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("23:40", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "evening",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                            isSegmentTop = false,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("22:30", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                    }
                }
            }
        }
        // Let the latest-anchor's arrival morph (circle → Cookie9Sided) settle before capturing.
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun latest_anchor_nests_flush_at_the_track_s_true_top_cap() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        // isSegmentTop = true with a track below (not a lone node) — the Latest anchor must not overflow past the top rounded cap here.
                        TimelineNode(
                            id = "replanned",
                            registry = registry,
                            anchor = TimelineAnchor.Latest(MaterialSymbol.Schedule),
                            isSegmentTop = true,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                            title = { Text("Re-planned", style = CronTypography.timelineHeroTitle) },
                            status = { Text("Latest · 23:27", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "asleep",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                            isSegmentTop = false,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("23:40", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun node_with_content_area() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        TimelineNode(
                            id = "planned",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.Schedule,
                                tint = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                            isSegmentTop = true,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
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
                            id = "got_up",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.DirectionsWalk),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:50", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "dismissed",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.AlarmOff),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Alarm dismissed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:45", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "asleep",
                            registry = registry,
                            anchor = TimelineAnchor.Plain,
                            isSegmentTop = false,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("23:10", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun asleep_awake_track_shows_the_transition_and_rounded_terminal_caps() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        // True top terminus — rounded cap, fully awake below.
                        TimelineNode(
                            id = "planned",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.Schedule),
                            isSegmentTop = true,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Planned", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("21:00", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // Fully awake — uniform, no split.
                        TimelineNode(
                            id = "schedule_changed",
                            registry = registry,
                            anchor = TimelineAnchor.Plain,
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Your schedule changed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("22:15", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // The onset row itself: awake above, asleep below — the sleep pill opens its own rounded cap here instead of a flat cut.
                        TimelineNode(
                            id = "onset",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.Bedtime),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = false,
                            isAsleepBelow = true,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("23:00", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // Fully asleep — uniform, no split.
                        TimelineNode(
                            id = "movement",
                            registry = registry,
                            anchor = TimelineAnchor.Plain,
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = true,
                            isAsleepBelow = true,
                            title = { Text("Movement detected", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("03:20", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // The wake row itself: asleep above, awake below — the sleep pill closes its own rounded cap here.
                        TimelineNode(
                            id = "wake",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.DirectionsWalk),
                            isSegmentTop = false,
                            isSegmentBottom = false,
                            isAsleepAbove = true,
                            isAsleepBelow = false,
                            title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:15", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        // True bottom terminus — rounded cap, fully awake above.
                        TimelineNode(
                            id = "dismissed",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(MaterialSymbol.AlarmOff),
                            isSegmentTop = false,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            title = { Text("Alarm dismissed", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:20", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun icon_anchor_stays_separated_from_a_same_color_track_segment() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val dim = MaterialTheme.colorScheme.onSurfaceVariant
                val asleep = MaterialTheme.colorScheme.tertiaryContainer
                val onAsleep = MaterialTheme.colorScheme.onTertiaryContainer
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        // A Body-accent icon (tertiaryContainer, matching TimelineAccent.Body) sitting on an asleep track segment — without proper nesting it has zero contrast and disappears into the track.
                        TimelineNode(
                            id = "onset",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.Bedtime,
                                tint = onAsleep,
                                containerColor = asleep,
                            ),
                            isSegmentTop = true,
                            isSegmentBottom = false,
                            isAsleepAbove = true,
                            isAsleepBelow = true,
                            title = { Text("You fell asleep", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("23:14", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                        TimelineNode(
                            id = "wake",
                            registry = registry,
                            anchor = TimelineAnchor.Icon(
                                symbol = MaterialSymbol.DirectionsWalk,
                                tint = onAsleep,
                                containerColor = asleep,
                            ),
                            isSegmentTop = false,
                            isSegmentBottom = true,
                            isAsleepAbove = true,
                            isAsleepBelow = true,
                            title = { Text("You got up", style = MaterialTheme.typography.bodyMedium, color = dim) },
                            status = { Text("07:15", style = CronTypography.labelMonoSmall, color = dim) },
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
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
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        AiRunNode(
                            item = latestAiRun(
                                summary = "Moved alarm to 07:15 — your first meeting shifted to 09:00.",
                                newAlarmTime = LocalTime(7, 15),
                                previousAlarmTime = LocalTime(7, 45),
                            ),
                            registry = registry,
                            isSegmentTop = true,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                        )
                    }
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
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        AiRunNode(
                            item = latestAiRun(
                                summary = "Set your first alarm for the day.",
                                newAlarmTime = LocalTime(7, 45),
                                previousAlarmTime = null,
                            ),
                            registry = registry,
                            isSegmentTop = true,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun ai_run_node_latest_shows_the_standing_time_with_prose_demoted_below() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        AiRunNode(
                            item = latestAiRun(
                                summary = "Cancelled today's alarm — no early meetings on your calendar.",
                                newAlarmTime = null,
                                previousAlarmTime = LocalTime(7, 45),
                            ),
                            registry = registry,
                            isSegmentTop = true,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** A row that WAS latest and gets superseded (`isLatest` true → false within the same
     *  composition) keeps `everLatest = true`, and its title `Crossfade` keeps reserving hero-sized
     *  height even in the demoted single-line state. `Crossfade` has no `contentAlignment` param and
     *  its own internal `Box` defaults to `TopStart`, so the demoted `Text` must be centered
     *  explicitly or it floats at the top of that reserved height with visible dead space below —
     *  only reproduces via an actual true→false transition, not a row demoted from its first
     *  composition. */
    @Test
    fun ai_run_node_demoted_after_being_latest_centers_its_title_in_the_reserved_height() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val registry = rememberTimelineTrackRegistry()
                var isLatest by remember { mutableStateOf(true) }
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        AiRunNode(
                            item = latestAiRun(
                                summary = "Cancelled today's alarm — no early meetings on your calendar.",
                                newAlarmTime = null,
                                previousAlarmTime = LocalTime(7, 45),
                            ).copy(isLatest = isLatest),
                            registry = registry,
                            isSegmentTop = true,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                        )
                    }
                }
                LaunchedEffect(Unit) { isLatest = false }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun ai_run_node_latest_shows_a_static_label_with_no_time_at_all() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                val registry = rememberTimelineTrackRegistry()
                Box {
                    TimelineTrackOverlay(registry = registry)
                    Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                        AiRunNode(
                            item = latestAiRun(
                                summary = "No prior alarms to compare against yet.",
                                newAlarmTime = null,
                                previousAlarmTime = null,
                            ),
                            registry = registry,
                            isSegmentTop = true,
                            isSegmentBottom = true,
                            isAsleepAbove = false,
                            isAsleepBelow = false,
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(1_000L)
        composeTestRule.onRoot().captureRoboImage()
    }
}
