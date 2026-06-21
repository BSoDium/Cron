@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.ALARM_BAR_HEIGHT
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.sessionTimelineItems
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

private val ALARM_COLLAPSE_RANGE = 120.dp
private const val DETAIL_COMMIT_MS = 300
private const val DETAIL_CANCEL_MS = 220
private const val DETAIL_CARD_MIN_SCALE = 0.90f
private val DETAIL_PREVIEW_SHIFT = Spacing.lg

sealed interface TimelineMode {
    data object List : TimelineMode
    data class Detail(val turnIndex: Int, val sessionId: String) : TimelineMode
}

@Composable
internal fun HomePlanContent(
    uiState: HomeUiState,
    timelineMode: TimelineMode,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
    onAutoAlarmsChange: (Boolean) -> Unit,
    onAlarmTimeClick: (() -> Unit)? = null,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var cardFullHeightPx by remember { mutableIntStateOf(0) }
    var greetingHeightPx by remember { mutableIntStateOf(0) }
    val reservePx by remember { derivedStateOf { cardFullHeightPx } }

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
            sessionTimelineItems(
                timeline = uiState.timeline,
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

        DetailOverlay(
            visible = timelineMode is TimelineMode.Detail,
            iteration = (timelineMode as? TimelineMode.Detail)?.turnIndex?.let { turn ->
                uiState.aiPlan?.iterations?.find { it.turnIndex == turn }
            },
            alarmBottomDp = with(density) { (collapseState.value.top + cardFullHeightPx).toDp() } + Spacing.lg,
            navInsetBottom = navInsetBottom,
            hapticsEnabled = uiState.hapticsEnabled,
            onBack = onBack,
        )

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
private fun BoxScope.DetailOverlay(
    visible: Boolean,
    iteration: AiIterationUi?,
    alarmBottomDp: Dp,
    navInsetBottom: Dp,
    hapticsEnabled: Boolean,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val slide = remember { Animatable(1f) }
    val backProgress = remember { Animatable(0f) }
    var edgeLeft by remember { mutableStateOf(false) }
    var committing by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }

    LaunchedEffect(visible) {
        if (visible) {
            shown = true
            slide.snapTo(1f)
            backProgress.snapTo(0f)
            committing = false
            slide.animateTo(0f, tween(DETAIL_COMMIT_MS, easing = EaseOutCubic))
        } else if (shown && !committing) {
            slide.animateTo(1f, tween(DETAIL_COMMIT_MS, easing = EaseOutCubic))
            shown = false
        }
    }

    fun animatedBack() {
        if (committing) return
        committing = true
        scope.launch {
            slide.animateTo(1f, tween(DETAIL_COMMIT_MS, easing = EaseOutCubic))
            shown = false
            committing = false
            onBack()
        }
    }

    if (shown) {
        PredictiveBackHandler(enabled = !committing) { events ->
            try {
                events.collect { event ->
                    edgeLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                    backProgress.snapTo(decelerate(event.progress))
                }
                committing = true
                backProgress.animateTo(2f, tween(DETAIL_COMMIT_MS, easing = EaseOutCubic))
                shown = false
                committing = false
                onBack()
            } catch (cancel: CancellationException) {
                scope.launch {
                    backProgress.animateTo(0f, tween(DETAIL_CANCEL_MS, easing = EaseOutCubic))
                }
                throw cancel
            }
        }

        val detailScrollState = rememberScrollState()
        val pullState = remember(iteration?.turnIndex) { PullState() }
        val previewShiftPx = with(density) { DETAIL_PREVIEW_SHIFT.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = alarmBottomDp)
                .graphicsLayer {
                    val backP = backProgress.value
                    val slideP = slide.value
                    val preview = backP.coerceIn(0f, 1f)
                    val commit = (backP - 1f).coerceIn(0f, 1f)
                    val sign = if (edgeLeft) -1f else 1f
                    val scale = lerp(1f, DETAIL_CARD_MIN_SCALE, preview)
                    scaleX = scale; scaleY = scale
                    alpha = 1f - commit
                    translationX = slideP * screenWidthPx +
                        sign * (previewShiftPx * preview + (screenWidthPx - previewShiftPx) * commit)
                    clip = true
                    shape = RoundedCornerShape(Radius.xl * preview)
                }
                .background(CronColors.pageBackground),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(detailScrollState)
                    .padding(
                        start = Spacing.xl,
                        end = Spacing.xl,
                        top = Spacing.md,
                        bottom = navInsetBottom + Spacing.navBarClearance + Spacing.xxxl,
                    ),
            ) {
                if (iteration != null) {
                    DetailBackRow(
                        title = iteration.systemMessage,
                        onBack = ::animatedBack,
                    )
                    AiThinkingThread(
                        thread = iteration.thread,
                        expanded = pullState.expanded,
                        onExpandedChange = { next ->
                            pullState.expanded = next
                            scope.launch {
                                if (next) {
                                    val full = pullState.fullPx.intValue
                                    if (full > 0) pullState.reveal.animateTo(full.toFloat())
                                } else {
                                    pullState.reveal.animateTo(0f)
                                }
                            }
                        },
                        expandPx = { pullState.reveal.value },
                        onFullHeight = { pullState.fullPx.intValue = it },
                        expansionFraction = {
                            val full = pullState.fullPx.intValue
                            if (full > 0) (pullState.reveal.value / full).coerceIn(0f, 1f) else 0f
                        },
                        modifier = Modifier.padding(top = Spacing.md),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailBackRow(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Symbol(
                symbol = MaterialSymbol.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.width(Spacing.xs))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun decelerate(raw: Float): Float {
    val x = raw.coerceIn(0f, 1f)
    return 1f - (1f - x) * (1f - x)
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
            timelineMode = TimelineMode.List,
            statusInsetTop = 0.dp,
            navInsetBottom = 0.dp,
            hasNotificationPermission = true,
            onNotifEnable = {},
            onAutoAlarmsChange = {},
            onOpenAiRun = { _, _ -> },
            onNavigateToHistory = {},
            onBack = {},
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
