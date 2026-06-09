@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.theme.CronTheme
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.components.ALARM_BAR_HEIGHT
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.ReplanHistoryBar
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Pull-to-expand feel: the release fraction (of full height) past which it snaps open (springs back
// below), and the rubber-band floor so the reveal still creeps near full instead of fully stalling.
private const val THINKING_EXPAND_THRESHOLD = 0.4f
private const val THINKING_PULL_RUBBER_FLOOR = 0.15f
// Absolute cap on the release-to-expand pull distance, so a tall timeline (where 40% of full height
// exceeds the screen) is still openable with a short drag.
private val THINKING_EXPAND_TRIGGER_MAX = 120.dp

// Pre-measurement fallback for the collapse range; the live range is the card's real shrinkage
// (full height − collapsed bar), derived once the card reports its full height.
private val ALARM_COLLAPSE_RANGE = 120.dp

/**
 * The plan layout: the AI thread scrolls in a [LazyColumn] while the alarm card acts like CSS
 * `position: sticky` (top = statusBar + gap) via the [StickyAlarm] overlay; an "alarm-spacer" item
 * holds the card's flow slot. The thread item is a horizontal [ThreadPager] over the replan iterations,
 * two-way bound to the tab selection. The collapse geometry is a single derived source feeding the
 * sticky card, the threshold haptic, and the magnetic snap.
 */
@Composable
internal fun HomePlanContent(
    plan: AiPlanUi,
    uiState: HomeUiState,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
    onAutoAlarmsChange: (Boolean) -> Unit,
) {
    val iterations = plan.iterations
    // Default to the latest; a fresh replan (new latest turnIndex) re-keys this back to newest while
    // letting the user browse older iterations.
    var selectedTurn by rememberSaveable(iterations.last().turnIndex) {
        mutableStateOf(iterations.last().turnIndex)
    }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var cardFullHeightPx by remember { mutableIntStateOf(0) }
    var tabsHeightPx by remember { mutableIntStateOf(0) }
    val hasTabs = iterations.size > 1
    val iterationsState = rememberUpdatedState(iterations)
    val selectedTurnState = rememberUpdatedState(selectedTurn)

    // --- Interactive thread pager, two-way bound to the tab selection ---
    val selectedIndex = iterations.indexOfFirst { it.turnIndex == selectedTurn }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = selectedIndex) { iterations.size }
    val swipeHaptics = rememberCronHaptics(uiState.hapticsEnabled)
    // True while the pager animates to a tap/auto-nav target — suppresses the swipe haptic so only real
    // drags tick at the midpoint (a tap ticks immediately in ReplanHistoryBar).
    var programmaticScroll by remember { mutableStateOf(false) }
    // Pager → selection: commit on settle (so a mid-fling never fights the user). No tick here — the swipe
    // haptic fires at the midpoint crossing below.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val turn = iterationsState.value.getOrNull(page)?.turnIndex ?: return@collect
            if (turn != selectedTurnState.value) selectedTurn = turn
        }
    }
    // Selection → pager: a tab tap or a fresh-replan re-key animates the pager to match. No
    // isScrollInProgress guard — a tap made while the pager is still settling must still win (drags never
    // move selectedTurn mid-gesture, so this can't fight a drag).
    LaunchedEffect(selectedTurn) {
        val idx = iterations.indexOfFirst { it.turnIndex == selectedTurn }
        if (idx >= 0 && idx != pagerState.currentPage) {
            programmaticScroll = true
            try {
                pagerState.animateScrollToPage(idx)
            } finally {
                programmaticScroll = false
            }
        }
    }
    // Swipe haptic: tick exactly at the midpoint between tabs — the nearest index flips at the .5 boundary
    // — but only for user drags, never while a tap/auto-nav animation drives the pager.
    LaunchedEffect(pagerState) {
        var lastNearest = pagerState.currentPage
        snapshotFlow { (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt() }
            .collect { nearest ->
                if (nearest != lastNearest) {
                    lastNearest = nearest
                    if (!programmaticScroll) swipeHaptics.tick()
                }
            }
    }
    // Continuous pager position (page + offset) so the tabs cross-morph with the swipe rather than
    // snapping at the midpoint.
    val pagerPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }
    // True only while the USER is dragging the pager (not a tap/auto-nav animation): the tab strip tracks the
    // raw position 1:1 then; otherwise it cross-fades to the settled selection (so a tap's cross-fade finishes).
    val draggingPager by remember {
        derivedStateOf { pagerState.isScrollInProgress && !programmaticScroll }
    }

    // Per-iteration pull-to-expand state, shared between the pager pages and the pull connection below.
    // Keyed on the session: turn indices restart at 0 across a session rollover, so without the key the
    // new session's turns would inherit the old session's expanded/height state.
    val sessionKey = uiState.sessionDisplay?.sessionDate
    val pullStates = remember(sessionKey) { mutableStateMapOf<Int, PullState>() }
    // Size the pager to the SETTLED page's natural height: a fixed height keeps the pager draggable
    // (`wrapContentHeight` disables the horizontal drag), and keying on the settled page keeps it stable
    // through a swipe (no relayout spike). Pages measure unbounded, so an expanding page grows this map
    // → the pager follows → the content isn't clipped.
    val pageHeights = remember(sessionKey) { mutableStateMapOf<Int, Int>() }
    val pagerHeightPx by remember(iterations) {
        derivedStateOf { pageHeights[iterations.getOrNull(pagerState.settledPage)?.turnIndex] ?: 0 }
    }

    // Scroll-independent layout metrics, kept as live derived state (heights are measured async). Both
    // the swipe-target sizing and the collapse gate read these, so neither captures a stale value.
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var greetingHeightPx by remember { mutableIntStateOf(0) }
    val topPadPx = with(density) { (statusInsetTop + Spacing.sm).roundToPx() }
    val mdPx = with(density) { Spacing.md.roundToPx() }
    val stripGapPx = with(density) { Spacing.sm.roundToPx() }
    // Track the strip's (animating) measured height so the reserve grows/shrinks in lockstep with the
    // tab-group enter/exit animation instead of jumping when iterations cross 1.
    val reservePx by remember(stripGapPx) {
        derivedStateOf { cardFullHeightPx + if (tabsHeightPx > 0) tabsHeightPx + stripGapPx else 0 }
    }
    val threadTopPx by remember(topPadPx, mdPx) {
        derivedStateOf { topPadPx + greetingHeightPx + mdPx + reservePx + mdPx }
    }
    // Collapse range = the card's real shrinkage (full height → collapsed bar), so the card shrinks at
    // exactly finger speed and the response holds a constant gap below the tabs until the collapse
    // completes. A fixed range that didn't match the shrinkage let a short card's response slide under
    // the tabs mid-collapse and made the collapse feel slow. Falls back to the fixed range only until the
    // card is first measured (NOT a permanent floor — flooring at 120 would re-break short cards).
    val barHeightPx = with(density) { ALARM_BAR_HEIGHT.toPx() }
    val fallbackRangePx = with(density) { ALARM_COLLAPSE_RANGE.toPx() }
    val collapseRangePx by remember(barHeightPx, fallbackRangePx) {
        derivedStateOf {
            if (cardFullHeightPx > 0) (cardFullHeightPx - barHeightPx).coerceAtLeast(1f) else fallbackRangePx
        }
    }
    // Swipe target: a short page should still expose a tall, draggable strip (the pager catches drags
    // across its whole height). Fill to the screen bottom, PLUS the greeting + its gap, PLUS any collapse
    // range beyond the 120 baseline. The collapse needs `greeting + gap + range` of scroll to complete:
    // the greeting + gap above the card scroll off FIRST, and a taller card (range > 120) needs more slack
    // than the bottom contentPadding alone gives — so a short page always collapses fully and rests (no
    // jump-back). Scroll-independent → constant across scroll.
    val minGrabPx by remember(mdPx, fallbackRangePx) {
        derivedStateOf {
            (rootHeightPx - threadTopPx).coerceAtLeast(0) + greetingHeightPx + mdPx +
                (collapseRangePx - fallbackRangePx).coerceAtLeast(0f).roundToInt()
        }
    }
    val effPagerPx = maxOf(pagerHeightPx, minGrabPx)

    val collapseSafeTopPx = with(density) { (statusInsetTop + Spacing.sm).roundToPx() }
    val collapseFadePx = with(density) { Spacing.xxl.toPx() }
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
    // Threshold haptic + magnetic snap, both keyed on listState. NOTE: the collapse geometry is consumed
    // only as State (here and in StickyAlarm) — never read in this composable's body, which would recompose
    // the whole screen on every scroll frame.
    AlarmCollapseEffects(listState, collapseState, collapseRangePx, uiState.hapticsEnabled)

    // Pull-to-expand: at the top of the list, an overscroll-down drag unwraps the collapsed thinking 1:1
    // with the finger — but only the SETTLED pager page (no horizontal swipe in flight), and each page
    // keeps its own reveal via its [PullState]. One reveal value drives the disclosure and the
    // release/tap animation, so there's no second source to dip against.
    val expandTriggerMaxPx = with(density) { THINKING_EXPAND_TRIGGER_MAX.toPx() }
    // State-wrapped so the remembered pullConnection below reads the pref-respecting instance live (a
    // haptics-pref toggle swaps it) instead of capturing one stale at construction.
    val pullHaptics = rememberUpdatedState(rememberCronHaptics(enabled = uiState.hapticsEnabled))
    val pullConnection = remember(listState, pagerState, expandTriggerMaxPx) {
        object : NestedScrollConnection {
            // The iteration the pull acts on — only while the pager rests on a page (no swipe mid-flight).
            fun activeIter(): AiIterationUi? {
                if (pagerState.isScrollInProgress || abs(pagerState.currentPageOffsetFraction) >= 0.01f) return null
                return iterationsState.value.getOrNull(pagerState.currentPage)
            }

            fun stateFor(turn: Int): PullState = pullStates.getOrPut(turn) { PullState() }

            fun triggerPx(s: PullState): Float {
                val full = s.fullPx.intValue
                return if (full > 0) minOf(full * THINKING_EXPAND_THRESHOLD, expandTriggerMaxPx) else Float.MAX_VALUE
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val iter = activeIter() ?: return Offset.Zero
                val s = stateFor(iter.turnIndex)
                // Dragging up while a partial peek is open unwinds it (1:1) before the list scrolls.
                if (available.y < 0f && s.reveal.value > 0f && !s.expanded) {
                    val next = (s.reveal.value + available.y).coerceAtLeast(0f)
                    val consumed = next - s.reveal.value
                    s.pastThreshold = next >= triggerPx(s)
                    scope.launch { s.reveal.snapTo(next) }
                    return Offset(0f, consumed)
                }
                // At the top, dragging down on a collapsible, collapsed thread grows the reveal (rubber-banded).
                val canPull = iter.thread.process.isNotEmpty() && !s.expanded
                if (available.y > 0f && canPull && !listState.canScrollBackward) {
                    val full = s.fullPx.intValue
                    val rubber = if (full > 0) (1f - s.reveal.value / full).coerceIn(THINKING_PULL_RUBBER_FLOOR, 1f) else 1f
                    val next = s.reveal.value + available.y * rubber
                    val nowPast = next >= triggerPx(s)
                    if (nowPast && !s.pastThreshold) pullHaptics.value.tick() // click as the pull reaches the open point
                    s.pastThreshold = nowPast
                    scope.launch { s.reveal.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val iter = activeIter() ?: return Velocity.Zero
                val s = stateFor(iter.turnIndex)
                if (s.reveal.value <= 0f) return Velocity.Zero
                val full = s.fullPx.intValue
                if (full > 0 && s.reveal.value >= triggerPx(s)) {
                    s.reveal.animateTo(full.toFloat())
                    s.expanded = true
                } else {
                    s.reveal.animateTo(0f)
                }
                return available
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pullConnection),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = statusInsetTop + Spacing.sm,
                // navBarClearance clears the pill; the extra xxxl matches the EdgeFades bottom
                // gradient's own xxxl so the last content (the thinking shape) rests above the fade.
                bottom = navInsetBottom + Spacing.navBarClearance + Spacing.xxxl,
            ),
            // md is the tight greeting→card gap; the thread item adds its own top padding to keep the
            // larger card→thread breathing room.
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "greeting") {
                HomeGreetingRow(
                    prefix = uiState.greetingPrefix,
                    name = uiState.greetingName,
                    autoAlarmsEnabled = uiState.autoAlarmsEnabled,
                    onAutoAlarmsChange = onAutoAlarmsChange,
                    modifier = Modifier.onSizeChanged { greetingHeightPx = it.height },
                )
            }
            item(key = "alarm-spacer") {
                // Reserve the expanded card AND the sticky tab strip below it, so scrolling content
                // starts clear of both (the strip is rendered in the StickyAlarm overlay, not the list).
                Spacer(Modifier.height(with(density) { reservePx.toDp() }))
            }
            item(key = "thread") {
                ThreadPager(
                    iterations = iterations,
                    pagerState = pagerState,
                    pullStates = pullStates,
                    onJumpToLatest = { swipeHaptics.tick(); selectedTurn = iterations.last().turnIndex },
                    onPageHeight = { turn, px -> if (pageHeights[turn] != px) pageHeights[turn] = px },
                    modifier = if (effPagerPx > 0) Modifier.height(with(density) { effPagerPx.toDp() }) else Modifier,
                )
            }
            if (!hasNotificationPermission) {
                item(key = "notif-permission") {
                    NotificationPermissionRow(onEnable = onNotifEnable)
                }
            }
        }
        StickyAlarm(
            safeTopPx = collapseSafeTopPx,
            collapse = collapseState,
            // The history tabs ride in the overlay (above the scrim), sticky right below the card, so the
            // scroll gradient never fades them. Lifted out of the list for that reason. Always supplied so
            // the strip can animate IN/OUT (visibility = hasTabs) rather than popping the layout.
            belowCardVisible = hasTabs,
            belowCard = {
                ReplanHistoryBar(
                    iterations = iterations,
                    position = pagerPosition,
                    selectedTurn = selectedTurn,
                    dragging = draggingPager,
                    onSelect = { selectedTurn = it },
                    hapticsEnabled = uiState.hapticsEnabled,
                )
            },
            onBelowCardHeight = { tabsHeightPx = it },
        ) { collapseFraction ->
            CollapsibleAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sessionDate = uiState.sessionDisplay?.sessionDate,
                sleepDurationLabel = uiState.sleepStats?.durationLabel,
                sleepSegments = uiState.sleepStats?.segments.orEmpty(),
                collapseFraction = collapseFraction, // () -> Float, read in the card's measure pass only
                onFullHeight = { cardFullHeightPx = it },
            )
        }
    }
}

/**
 * CSS-`sticky`-with-top-offset alarm card, rendered as an overlay over the list, consuming the
 * pre-computed [collapse] geometry (single derived source in the caller). The per-frame fields are
 * read only inside graphicsLayer blocks (offset/alpha) and handed to [card] as a provider for its
 * measure pass — a scroll frame never recomposes this overlay. A full-width background→transparent
 * gradient fades in behind the card as it pins, dissolving content that slides under.
 */
@Composable
private fun BoxScope.StickyAlarm(
    safeTopPx: Int,
    collapse: State<AlarmCollapse>,
    belowCardVisible: Boolean = false,
    belowCard: (@Composable () -> Unit)? = null,
    onBelowCardHeight: (Int) -> Unit = {},
    card: @Composable (collapseFraction: () -> Float) -> Unit,
) {
    val density = LocalDensity.current
    val background = MaterialTheme.colorScheme.background
    // Scrim tracks the CURRENT (collapsing) visible height of the card + its sticky strip — independent
    // of the spacer's full reservation — so the solid band always hugs the bottom of what's pinned, and
    // the strip sits on solid background (never faded), with content below dissolving into the page.
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
        Column {
            // Card hugs at 12dp (tighter than the 20dp content), constant across collapse. The tab strip
            // below it gets a FULL-WIDTH scroll viewport (tabs clip at the screen edge) with its own
            // internal 12dp content padding, so at rest the tabs line up with the card.
            Box(Modifier.padding(horizontal = Spacing.md)) { card { collapse.value.fraction } }
            if (belowCard != null) {
                // The container animates its height on enter/exit; onSizeChanged reports that ramping
                // height so the caller's spacer follows in lockstep (no jump). Gap padding is INSIDE so it
                // collapses to 0 when hidden.
                AnimatedVisibility(
                    visible = belowCardVisible,
                    modifier = Modifier.onSizeChanged { onBelowCardHeight(it.height) },
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Box(Modifier.padding(top = Spacing.sm)) { belowCard() }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePlanContentPreview() {
    CronTheme {
        HomePlanContent(
            plan = AiPlanUi(
                iterations = listOf(
                    previewIteration(0, RunKind.ScheduledBase, "Alarm set for **07:45** to cover the 08:30 stand-up."),
                    previewIteration(1, RunKind.Replan(TriggerType.CalendarChange), "Moved to **07:15** after your first meeting shifted earlier."),
                ),
            ),
            uiState = HomeUiState(initialized = true),
            statusInsetTop = 0.dp,
            navInsetBottom = 0.dp,
            hasNotificationPermission = true,
            onNotifEnable = {},
            onAutoAlarmsChange = {},
        )
    }
}

private fun previewIteration(turn: Int, kind: RunKind, response: String) = AiIterationUi(
    turnIndex = turn,
    timeLabel = "21:30",
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = kind.label, process = emptyList(), response = response),
)
