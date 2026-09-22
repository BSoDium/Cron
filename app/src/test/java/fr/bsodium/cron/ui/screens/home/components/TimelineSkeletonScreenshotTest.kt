package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.screens.home.timelineAsleepStates
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@Suppress("DEPRECATION")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
// Default Robolectric window is only ~320x470dp — too short to show all 6 initial-load rows settling
// before the fade-out tail kicks in. A real phone-sized window instead.
@Config(qualifiers = "w360dp-h800dp")
class TimelineSkeletonScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun initial_load_six_rows() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.padding(Spacing.md)) {
                        TimelineRowsSkeleton(rowCount = 6, topCapped = true)
                    }
                }
            }
        }
        // Mid-cycle, not frame zero — every row starts its pulse at fraction 0 (the low phase) until
        // its own staggered delay elapses, so a frame-zero capture would show them all identical and
        // prove nothing about the stagger. Advancing partway makes each row's own delay-driven offset
        // visible as a distinct shade.
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Config(qualifiers = "w360dp-h800dp-night")
    @Test
    fun initial_load_six_rows_dark() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    Box(modifier = Modifier.padding(Spacing.md)) {
                        TimelineRowsSkeleton(rowCount = 6, topCapped = true)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }

    @Test
    fun append_placeholder_two_rows() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    // Extra top room so the connector overlap (uncapped by default) renders in full
                    // rather than getting clipped by this test's own canvas — see appended_after_real_rows
                    // for it against real content, which naturally provides that headroom.
                    Box(
                        modifier = Modifier.padding(
                            start = Spacing.md,
                            end = Spacing.md,
                            top = Spacing.md + Spacing.xxxl,
                            bottom = Spacing.md,
                        ),
                    ) {
                        TimelineRowsSkeleton(rowCount = 2)
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }

    /** The scroll-triggered Paging append case: real rows already on screen, chained directly into
     *  the skeleton below — not the skeleton floating on its own, like the other tests here. Confirms
     *  the two tracks line up and the seam reads as one continuous line, not a fresh cap. */
    @Test
    fun appended_after_real_rows() {
        composeTestRule.mainClock.autoAdvance = false
        val now = Instant.fromEpochMilliseconds(1_700_000_000_000L)
        val timeline = listOf(
            TimelineItem.AiRun(
                timestamp = now,
                iteration = AiIterationUi(
                    turnIndex = 1,
                    timeLabel = "07:15",
                    kind = RunKind.Replan(TriggerType.CalendarChange),
                    thread = AiThreadUi(
                        turnIndex = 1,
                        summary = "Thought for 8s",
                        process = emptyList(),
                        response = "Moved alarm to **07:15** — your first meeting shifted to 09:00.",
                    ),
                    ranAtEpochMs = now.toEpochMilliseconds(),
                ),
                sessionId = "s1",
                isStreaming = false,
                isLatest = true,
            ),
            TimelineItem.Event(
                timestamp = now,
                trigger = TriggerType.SleepOnset,
                label = "You fell asleep",
                detail = null,
            ),
        )
        val asleepStates = timelineAsleepStates(timeline)
        composeTestRule.setContent {
            CronTheme {
                Surface(color = CronColors.pageBackground) {
                    val registry = rememberTimelineTrackRegistry()
                    val listState = rememberLazyListState()
                    Box(modifier = Modifier.fillMaxSize()) {
                        TimelineTrackOverlay(registry = registry, listState = listState)
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            timeline.forEachIndexed { index, item ->
                                when (item) {
                                    is TimelineItem.AiRun -> AiRunNode(
                                        item = item,
                                        registry = registry,
                                        isSegmentTop = index == 0,
                                        isSegmentBottom = index == timeline.lastIndex,
                                        isAsleepAbove = asleepStates[index],
                                        isAsleepBelow = asleepStates.getOrNull(index + 1) ?: asleepStates[index],
                                        onClick = {},
                                    )
                                    is TimelineItem.Event -> EventNode(
                                        item = item,
                                        registry = registry,
                                        isSegmentTop = index == 0,
                                        isSegmentBottom = index == timeline.lastIndex,
                                        isAsleepAbove = asleepStates[index],
                                        isAsleepBelow = asleepStates.getOrNull(index + 1) ?: asleepStates[index],
                                    )
                                    is TimelineItem.DayHeader -> DayHeaderRow(item = item)
                                }
                            }
                            TimelineRowsSkeleton(rowCount = 2)
                        }
                    }
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(500)
        composeTestRule.onRoot().captureRoboImage()
    }
}
