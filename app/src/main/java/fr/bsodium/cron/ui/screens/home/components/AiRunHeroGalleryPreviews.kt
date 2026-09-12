package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalTime

/** Every hero-slot state `AiRunNode`'s title `when` (SessionTimeline.kt) can actually produce, plus
 *  the demoted (non-latest) row shape, gathered in one gallery so a headline-logic change — the
 *  streaming NO_ALARM_LABEL flash, the PREV›NEW / NEW-only / PREV-only(standing) / neither branches,
 *  the mocked "Test plan" label — can be screened across all of them at once. */
@PreviewLightDark
@Composable
internal fun AiRunHeroGalleryPreview() {
    CronTheme {
        val registry = rememberTimelineTrackRegistry()
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            TimelineTrackOverlay(registry = registry, listState = listState)
            Column(modifier = Modifier.padding(horizontal = Spacing.lg)) {
                GallerySectionLabel("Latest hero states")
                AiRunNode(
                    item = latestRun("prev-new", newAlarmTime = LocalTime(7, 15), previousAlarmTime = LocalTime(7, 45)),
                    registry = registry,
                    isSegmentTop = true,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    item = latestRun("new-only", newAlarmTime = LocalTime(6, 40), previousAlarmTime = null, kind = RunKind.ScheduledBase),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    // A cancel/do_nothing turn: no new time, but a prior one stands — never re-shown as a redundant "PREV › PREV" pair, just the standing time alone.
                    item = latestRun("prev-only-standing", newAlarmTime = null, previousAlarmTime = LocalTime(7, 45)),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    // The rare settled first-run do-nothing: nothing ever resolved — the one case that legitimately shows the static NO_ALARM_LABEL literal.
                    item = latestRun("no-alarm-settled", newAlarmTime = null, previousAlarmTime = null, kind = RunKind.ManualBase),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    // Streaming with nothing resolved yet: must show a blank placeholder, not a flash of NO_ALARM_LABEL that then gets replaced once the real value streams in.
                    item = latestRun(
                        "streaming",
                        newAlarmTime = null,
                        previousAlarmTime = null,
                        kind = RunKind.Replan(TriggerType.AlarmSnoozed),
                        isStreaming = true,
                    ),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    item = latestRun("mocked", newAlarmTime = LocalTime(7, 0), previousAlarmTime = null, isMocked = true),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                GallerySectionLabel("Demoted (non-latest) rows")
                AiRunNode(
                    item = demotedRun("demoted-interior", kind = RunKind.Replan(TriggerType.CalendarChange)),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = false,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
                AiRunNode(
                    item = demotedRun("demoted-cap", kind = RunKind.ScheduledBase),
                    registry = registry,
                    isSegmentTop = false,
                    isSegmentBottom = true,
                    isAsleepAbove = false,
                    isAsleepBelow = false,
                    onClick = {},
                )
            }
        }
    }
}

private fun latestRun(
    id: String,
    newAlarmTime: LocalTime?,
    previousAlarmTime: LocalTime?,
    kind: RunKind = RunKind.Replan(TriggerType.CalendarChange),
    isStreaming: Boolean = false,
    isMocked: Boolean = false,
) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(0L),
    iteration = AiIterationUi(
        turnIndex = 0,
        timeLabel = "23:14",
        kind = kind,
        thread = AiThreadUi(
            turnIndex = 0,
            summary = if (isStreaming) "Thinking..." else "Thought for 8s",
            process = emptyList(),
            response = "Moved alarm to reflect your updated schedule.",
            newAlarmTime = newAlarmTime,
            isStreaming = isStreaming,
            isMocked = isMocked,
        ),
        previousAlarmTime = previousAlarmTime,
        // A relative-time timestamp, not epoch 0 — the latest row's kicker suffix always renders "Xm/Xd ago" with no recency threshold, so a literal 1970 epoch would read as an absurd "20641d ago" instead of "just now".
        ranAtEpochMs = System.currentTimeMillis(),
    ),
    sessionId = id,
    isStreaming = isStreaming,
    isLatest = true,
)

private fun demotedRun(id: String, kind: RunKind) = TimelineItem.AiRun(
    timestamp = Instant.fromEpochMilliseconds(0L),
    iteration = AiIterationUi(
        turnIndex = 0,
        timeLabel = "21:30",
        kind = kind,
        thread = AiThreadUi(turnIndex = 0, summary = "Thought for 5s", process = emptyList(), response = null),
        ranAtEpochMs = 0L,
    ),
    sessionId = id,
    isStreaming = false,
    isLatest = false,
)
