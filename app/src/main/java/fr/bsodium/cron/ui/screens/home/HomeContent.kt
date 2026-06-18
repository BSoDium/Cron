@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.components.ALARM_BAR_HEIGHT
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.HomeGreetingRow
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.screens.home.components.ReplanHistoryBar
import fr.bsodium.cron.ui.screens.home.components.ThreadSkeleton
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// Pre-measurement fallback for the collapse range; the live range is the card's real shrinkage
// (full height − collapsed bar), derived once the card reports its full height.
private val ALARM_COLLAPSE_RANGE = 120.dp

// Hold the markdown-heavy thread until the tab fade-through entrance (≈TAB_OUT_MS + TAB_IN_MS in
// MainActivity) has settled, so its synchronous parse doesn't stutter the transition. Tuned on device.
private const val HEAVY_DEFER_MS = 260L

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
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var cardFullHeightPx by remember { mutableIntStateOf(0) }
    var tabsHeightPx by remember { mutableIntStateOf(0) }
    val hasTabs = iterations.size > 1

    // Staged render: paint the cheap header + alarm card + tab strip on the first frame, then let the
    // markdown-heavy thread pager compose once the tab-slide entrance has settled — so its synchronous
    // parse lands on a still screen instead of stuttering mid-slide. withFrameNanos waits for frame 1 to
    // draw, then we hold past the entrance. Re-stages on every entry (plain remember).
    var deferHeavy by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        withFrameNanos {}
        delay(HEAVY_DEFER_MS)
        deferHeavy = false
    }

    // The pager is the single source of truth (see PagerNav.kt): the selection IS the settled page's
    // turn, so the lit tab, the displayed thread, and the "jump to latest" footer can never diverge.
    // pagerNav drives the pager one-way (tab tap / jump-to-latest / fresh-replan auto-advance) and reports
    // active dragging. rememberPagerState's own Saver persists the page across config change.
    val pagerState = rememberPagerState(initialPage = iterations.lastIndex.coerceAtLeast(0)) { iterations.size }
    val swipeHaptics = rememberCronHaptics(uiState.hapticsEnabled)
    val pagerNav = rememberPagerNav(
        pagerState = pagerState,
        iterations = iterations,
        newestTurn = iterations.last().turnIndex,
        hapticsEnabled = uiState.hapticsEnabled,
    )
    val draggingPager by pagerNav.dragging
    // getOrNull guards the transient post-replan frame where settledPage can lead the list (mirrors
    // pagerHeightPx below). Recomputes on settle, not per swipe frame, so the strip composes per selection.
    val selectedTurn by remember(iterations) {
        derivedStateOf { iterations.getOrNull(pagerState.settledPage)?.turnIndex ?: iterations.last().turnIndex }
    }
    // Continuous pager position (page + offset) handed to the tab strip as a PROVIDER: the strip reads it
    // inside snapshot flows / derived state, so a swipe frame never recomposes this screen or the strip.
    val pagerPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

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

    // Pull-to-expand physics (see ThinkingPullConnection.kt): pure, page-scoped, testable in isolation.
    val pullConnection = rememberThinkingPullConnection(
        listState = listState,
        pagerState = pagerState,
        iterations = iterations,
        pullStates = pullStates,
        hapticsEnabled = uiState.hapticsEnabled,
    )

    Box(modifier = Modifier.fillMaxSize().onSizeChanged { rootHeightPx = it.height }) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().nestedScroll(pullConnection),
            contentPadding = PaddingValues(
                start = Spacing.xl,
                end = Spacing.xl,
                top = statusInsetTop + Spacing.xxl,
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
                    hapticsEnabled = uiState.hapticsEnabled,
                )
            }
            item(key = "alarm-spacer") {
                // Reserve the expanded card AND the sticky tab strip below it, so scrolling content
                // starts clear of both (the strip is rendered in the StickyAlarm overlay, not the list).
                Spacer(Modifier.height(with(density) { reservePx.toDp() }))
            }
            item(key = "thread") {
                // While the thread is deferred off the first frame, the page (greeting/card/strip) is up but
                // the response isn't — show a thread-shaped skeleton there, then crossfade into the real
                // pager when it composes. Reserve the known pager height so the list doesn't jump on
                // re-entry; the first load wraps until the page reports its height.
                Crossfade(
                    targetState = deferHeavy,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "thread",
                    modifier = if (effPagerPx > 0) Modifier.height(with(density) { effPagerPx.toDp() }) else Modifier,
                ) { deferred ->
                    if (deferred) {
                        ThreadSkeleton(Modifier.fillMaxWidth())
                    } else {
                        ThreadPager(
                            iterations = iterations,
                            pagerState = pagerState,
                            pullStates = pullStates,
                            onJumpToLatest = { swipeHaptics.tick(); pagerNav.scrollToTurn(iterations.last().turnIndex) },
                            onPageHeight = { turn, px -> if (pageHeights[turn] != px) pageHeights[turn] = px },
                            // Fill the tall effPagerPx slot (not just content height) so the horizontal
                            // plan-swipe is catchable across the whole area down to the nav bar — pages are
                            // top-aligned and measured unbounded, so a long response still grows/scrolls.
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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
            // scroll gradient never fades them. Lifted out of the list for that reason. Bound to hasTabs
            // alone (not deferHeavy) so the strip is present from the first frame and slides in with the
            // page: on a plain re-entry its AnimatedVisibility starts visible (no enter replay), and the
            // expand/fade entrance only fires when a replan actually creates the 2nd tab (1 → multiple).
            belowCardVisible = hasTabs,
            belowCard = {
                ReplanHistoryBar(
                    iterations = iterations,
                    position = pagerPosition,
                    selectedTurn = selectedTurn,
                    dragging = draggingPager,
                    onSelect = { pagerNav.scrollToTurn(it) },
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
    val background = CronColors.pageBackground
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
