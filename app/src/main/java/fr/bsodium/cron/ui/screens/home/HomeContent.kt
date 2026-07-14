@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.overscroll
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.withoutVisualEffect
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.util.lerp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.components.ALARM_BAR_HEIGHT
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.TimelineTrackOverlay
import fr.bsodium.cron.ui.screens.home.components.rememberTimelineTrackRegistry
import fr.bsodium.cron.ui.screens.home.components.sessionTimelineItems
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

private val ALARM_COLLAPSE_RANGE = 120.dp

/** [StickyAlarm]'s fade overlay's opacity cap while the card is actively collapsing — content
 *  scrolling underneath stays faintly visible so the collapse transition itself is watchable. */
private const val MAX_FADE_OVERLAY_ALPHA = 0.5f

/** The cap [StickyAlarm]'s overlay ramps toward once the card has fully settled into its collapsed
 *  state — high enough to read as solidly occluding, but short of 1f so the `belowFadePx` tail's
 *  transition into visible timeline content still stays gentle instead of an abrupt
 *  opaque→transparent cliff (Round 25). */
private const val MAX_FADE_OVERLAY_SETTLED_ALPHA = 0.85f

/** True once [initialized] is true AND [cardFullHeightPx] has reported a real measured value — false
 *  only for the mount/settle window, true forever after. Requires both because `HomeViewModel.uiState`
 *  is `stateIn(..., SharingStarted.WhileSubscribed(5_000), HomeUiState())`: backgrounding Home (e.g.
 *  behind Settings) for more than 5s cold-restarts the flow to its empty default, and
 *  `CollapsibleAlarmCard`'s "no alarm" empty state reports its own nonzero height almost immediately —
 *  gating on height alone would flip `settled` true on that transient wrong height before the real
 *  data arrives. Suppresses every `animateItem` spec in [sessionTimelineItems] and
 *  [TimelineTrackOverlay]'s `visible` param until both conditions hold, on every fresh mount (cold
 *  start, or regaining composition after a Settings round trip) — a later, genuine height change once
 *  already settled is real reflow and animates normally. */
@Composable
private fun rememberTimelineSettled(initialized: Boolean, cardFullHeightPx: Int): Boolean {
    var settled by remember { mutableStateOf(false) }
    if (!settled && initialized && cardFullHeightPx > 0) settled = true
    return settled
}

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
    val sharedOverscrollEffect = rememberOverscrollEffect()
    val trackRegistry = rememberTimelineTrackRegistry()
    val density = LocalDensity.current
    var cardFullHeightPx by remember { mutableIntStateOf(0) }
    var greetingHeightPx by remember { mutableIntStateOf(0) }
    val reservePx by remember { derivedStateOf { cardFullHeightPx } }
    val timelineSettled = rememberTimelineSettled(uiState.initialized, cardFullHeightPx)

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
            computeAlarmCollapse(
                visibleItems = info.visibleItemsInfo.map { VisibleItemSnapshot(it.key, it.offset) },
                viewportStartOffset = info.viewportStartOffset,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                collapseSafeTopPx = collapseSafeTopPx,
                collapseRangePx = collapseRangePx,
                collapseFadePx = collapseFadePx,
            )
        }
    }
    AlarmCollapseEffects(listState, collapseState, collapseRangePx, uiState.hapticsEnabled)

    // One Modifier.overscroll here renders the stretch over both the track and the rows as one subtree; the LazyColumn's withoutVisualEffect() view only drives it (its node attaches once, here) — AndroidX OverscrollRenderedOnTopOfLazyListDecorations sample.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .overscroll(sharedOverscrollEffect),
    ) {
        // One continuous painter behind the whole list; can't gap/re-cap while rows glide/fade since it reads each anchor's live position rather than being sliced per row.
        TimelineTrackOverlay(
            registry = trackRegistry,
            listState = listState,
            visible = timelineSettled,
        )
        LazyColumn(
            state = listState,
            overscrollEffect = sharedOverscrollEffect?.withoutVisualEffect(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // Matches StickyAlarm's own horizontal inset so the greeting, day headers, and timeline icons line up with the alarm card's left edge.
                start = Spacing.md,
                end = Spacing.md,
                top = statusInsetTop + Spacing.md,
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
                        .padding(bottom = Spacing.md)
                        .onSizeChanged { greetingHeightPx = it.height },
                    hapticsEnabled = uiState.hapticsEnabled,
                )
            }
            item(key = "alarm-spacer") {
                Spacer(Modifier.height(with(density) { reservePx.toDp() }).padding(bottom = Spacing.xxl))
            }
            sessionTimelineItems(
                timeline = uiState.timeline,
                hasMore = uiState.hasMoreHistory,
                registry = trackRegistry,
                newlyArrivedIds = uiState.newlyArrivedIds,
                suppressEntranceAnimation = !timelineSettled,
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

        StickyAlarm(
            safeTopPx = collapseSafeTopPx,
            collapse = collapseState,
        ) { collapseFraction ->
            CollapsibleAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sessionDate = uiState.sessionDisplay?.sessionDate,
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
    // Capped, not fully opaque, so scrolled-up content stays faintly visible through the fade rather than vanishing abruptly; ramps toward MAX_FADE_OVERLAY_SETTLED_ALPHA once the card stops actively collapsing so the belowFadePx tail's transition stays gentle, in both themes alike.
    val overlayAlphaCap = lerp(MAX_FADE_OVERLAY_ALPHA, MAX_FADE_OVERLAY_SETTLED_ALPHA, collapse.value.fraction)
    val background = CronColors.pageBackground.copy(alpha = overlayAlphaCap)
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
            .onSizeChanged { visiblePx = it.height },
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
