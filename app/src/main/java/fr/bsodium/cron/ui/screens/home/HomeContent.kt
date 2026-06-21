@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.components.ALARM_BAR_HEIGHT
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.PlanTimelineCard
import fr.bsodium.cron.ui.screens.home.components.sessionTimelineItems
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val ALARM_COLLAPSE_RANGE = 120.dp

@Composable
internal fun HomePlanContent(
    uiState: HomeUiState,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
    onAutoAlarmsChange: (Boolean) -> Unit,
    onAlarmTimeClick: (() -> Unit)? = null,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var cardFullHeightPx by remember { mutableIntStateOf(0) }
    var greetingHeightPx by remember { mutableIntStateOf(0) }
    var latestPlanHeightPx by remember { mutableIntStateOf(0) }
    val reservePx by remember { derivedStateOf { cardFullHeightPx } }

    val latestRun = remember(uiState.timeline) {
        uiState.timeline.firstOrNull { it is TimelineItem.AiRun && it.isLatest } as? TimelineItem.AiRun
    }
    val timelineWithoutLatest = remember(uiState.timeline, latestRun) {
        if (latestRun != null) uiState.timeline.filter { it !== latestRun } else uiState.timeline
    }

    val collapseSafeTopPx = with(density) { (statusInsetTop + Spacing.sm).roundToPx() }
    val collapseFadePx = with(density) { Spacing.xxl.toPx() }
    val barHeightPx = with(density) { ALARM_BAR_HEIGHT.toPx() }
    val fallbackRangePx = with(density) { ALARM_COLLAPSE_RANGE.toPx() }
    val collapseRangePx by remember(barHeightPx, fallbackRangePx) {
        derivedStateOf {
            if (cardFullHeightPx > 0) (cardFullHeightPx - barHeightPx).coerceAtLeast(1f) else fallbackRangePx
        }
    }
    val collapseState = remember(collapseSafeTopPx, collapseRangePx, collapseFadePx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val screenTop = info.visibleItemsInfo.firstOrNull { it.key == "alarm-spacer" }
                ?.let { it.offset - info.viewportStartOffset }
            if (screenTop == null) {
                AlarmCollapse(top = collapseSafeTopPx, gradientAlpha = 1f, fraction = 1f, distancePx = collapseRangePx)
            } else {
                val distance = (collapseSafeTopPx - screenTop).coerceAtLeast(0).toFloat()
                AlarmCollapse(
                    top = maxOf(collapseSafeTopPx, screenTop),
                    gradientAlpha = (distance / collapseFadePx).coerceIn(0f, 1f),
                    fraction = (distance / collapseRangePx).coerceIn(0f, 1f),
                    distancePx = distance,
                )
            }
        }
    }
    AlarmCollapseEffects(listState, collapseState, collapseRangePx, uiState.hapticsEnabled)

    val latestPlanPinPx by remember(collapseSafeTopPx) {
        derivedStateOf {
            val alarmBottom = collapseState.value.top +
                androidx.compose.ui.util.lerp(cardFullHeightPx.toFloat(), barHeightPx, collapseState.value.fraction)
            (alarmBottom + with(density) { Spacing.sm.toPx() }).toInt()
        }
    }
    val latestPlanTopPx by remember(latestPlanPinPx) {
        derivedStateOf {
            val info = listState.layoutInfo
            val spacerTop = info.visibleItemsInfo.firstOrNull { it.key == "latest-plan-spacer" }
                ?.let { it.offset - info.viewportStartOffset }
            if (spacerTop != null) maxOf(latestPlanPinPx, spacerTop) else latestPlanPinPx
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.md,
                end = Spacing.md,
                top = statusInsetTop + Spacing.xxl,
                bottom = navInsetBottom + Spacing.navBarClearance + Spacing.xxxl,
            ),
        ) {
            item(key = "greeting") {
                HomeGreetingRow(
                    prefix = uiState.greetingPrefix,
                    name = uiState.greetingName,
                    autoAlarmsEnabled = uiState.autoAlarmsEnabled,
                    onAutoAlarmsChange = onAutoAlarmsChange,
                    modifier = Modifier
                        .padding(horizontal = Spacing.sm)
                        .padding(bottom = Spacing.md)
                        .onSizeChanged { greetingHeightPx = it.height },
                    hapticsEnabled = uiState.hapticsEnabled,
                )
            }
            item(key = "alarm-spacer") {
                Spacer(Modifier.height(with(density) { reservePx.toDp() }).padding(bottom = Spacing.md))
            }
            if (latestRun != null) {
                item(key = "latest-plan-spacer") {
                    Spacer(Modifier.height(with(density) { latestPlanHeightPx.toDp() }))
                }
            }
            sessionTimelineItems(
                timeline = timelineWithoutLatest,
                hasMore = uiState.hasMoreHistory,
                onOpenAiRun = onOpenAiRun,
                onNavigateToHistory = onNavigateToHistory,
            )
            if (!hasNotificationPermission) {
                item(key = "notif-permission") {
                    NotificationPermissionRow(
                        onEnable = onNotifEnable,
                        modifier = Modifier.padding(horizontal = Spacing.sm),
                    )
                }
            }
        }

        if (latestRun != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { translationY = latestPlanTopPx.toFloat() }
                    .onSizeChanged { latestPlanHeightPx = it.height }
                    .padding(horizontal = Spacing.md),
            ) {
                PlanTimelineCard(
                    iteration = latestRun.iteration,
                    isLatest = true,
                    isStreaming = latestRun.isStreaming,
                    isFirst = true,
                    isLast = true,
                    onClick = { onOpenAiRun(latestRun.iteration.turnIndex, latestRun.sessionId) },
                )
            }
        }

        StickyAlarm(
            safeTopPx = collapseSafeTopPx,
            collapse = collapseState,
        ) { collapseFraction ->
            CollapsibleAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sessionDate = uiState.sessionDisplay?.sessionDate,
                sleepDurationLabel = uiState.sleepStats?.durationLabel,
                sleepSegments = uiState.sleepStats?.segments.orEmpty(),
                collapseFraction = collapseFraction,
                onFullHeight = { cardFullHeightPx = it },
                onAlarmTimeClick = onAlarmTimeClick,
            )
        }
    }
}

@Composable
private fun BoxScope.StickyAlarm(
    safeTopPx: Int,
    collapse: State<AlarmCollapse>,
    card: @Composable (collapseFraction: () -> Float) -> Unit,
) {
    val density = LocalDensity.current
    val background = CronColors.pageBackground
    var visiblePx by remember { mutableIntStateOf(0) }
    val cardBottomPx = safeTopPx + visiblePx
    val belowFadePx = with(density) { Spacing.xxxl.toPx() }
    val totalPx = cardBottomPx + belowFadePx
    val solidStop = if (totalPx > 0f) (cardBottomPx / totalPx).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { totalPx.toDp() })
            .graphicsLayer { alpha = collapse.value.gradientAlpha }
            .background(
                Brush.verticalGradient(
                    0f to background,
                    solidStop to background,
                    1f to Color.Transparent,
                ),
            ),
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { translationY = collapse.value.top.toFloat() }
            .onSizeChanged { if (it.height != visiblePx) visiblePx = it.height },
    ) {
        Box(Modifier.padding(horizontal = Spacing.md)) { card { collapse.value.fraction } }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePlanContentPreview() {
    CronTheme {
        HomePlanContent(
            uiState = HomeUiState(
                initialized = true,
                aiPlan = AiPlanUi(
                    iterations = listOf(
                        previewIteration(0, RunKind.ScheduledBase, "Alarm set for **07:45**."),
                        previewIteration(1, RunKind.Replan(TriggerType.CalendarChange), "Moved to **07:15**."),
                    ),
                ),
                timeline = buildTimeline(
                    listOf(
                        TimelineSession(
                            sessionId = "preview",
                            iterations = listOf(
                                previewIteration(0, RunKind.ScheduledBase, "Alarm set for **07:45**."),
                                previewIteration(1, RunKind.Replan(TriggerType.CalendarChange), "Moved to **07:15**."),
                            ),
                            events = emptyList(),
                            streamingTurnIndex = null,
                        ),
                    ),
                ),
            ),
            statusInsetTop = 0.dp,
            navInsetBottom = 0.dp,
            hasNotificationPermission = true,
            onNotifEnable = {},
            onAutoAlarmsChange = {},
            onOpenAiRun = { _, _ -> },
            onNavigateToHistory = {},
        )
    }
}

private fun previewIteration(turn: Int, kind: RunKind, response: String) = AiIterationUi(
    turnIndex = turn,
    timeLabel = "21:30",
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = kind.label, process = emptyList(), response = response),
    ranAtEpochMs = System.currentTimeMillis(),
)
