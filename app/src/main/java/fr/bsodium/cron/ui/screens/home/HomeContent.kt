package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.CollapsibleAlarmCard
import fr.bsodium.cron.ui.screens.home.components.GreetingHeader
import fr.bsodium.cron.ui.screens.home.components.NotificationPermissionRow
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch

// Pull-to-expand feel: the release fraction (of full height) past which it snaps open (springs back
// below), and the rubber-band floor so the reveal still creeps near full instead of fully stalling.
private const val THINKING_EXPAND_THRESHOLD = 0.4f
private const val THINKING_PULL_RUBBER_FLOOR = 0.15f
// Absolute cap on the release-to-expand pull distance, so a tall timeline (where 40% of full height
// exceeds the screen) is still openable with a short drag.
private val THINKING_EXPAND_TRIGGER_MAX = 120.dp

// Scroll distance (past the pin point) over which the alarm card collapses full → slim bar.
private val ALARM_COLLAPSE_RANGE = 120.dp

/**
 * The plan layout: the AI thread scrolls in a [LazyColumn] while the alarm card acts like CSS
 * `position: sticky` (top = statusBar + gap) via the [StickyAlarm] overlay; an "alarm-spacer" item
 * holds the card's flow slot. The collapse geometry is a single derived source feeding the sticky
 * card, the threshold haptic, and the magnetic snap.
 */
@Composable
internal fun HomePlanContent(
    thread: AiThreadUi,
    uiState: HomeUiState,
    statusInsetTop: Dp,
    navInsetBottom: Dp,
    hasNotificationPermission: Boolean,
    onNotifEnable: () -> Unit,
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    // Reserves the FULL (expanded) card height (fed by the card's own measure), decoupled from the
    // collapsing render height so collapse can't feed back.
    var cardFullHeightPx by remember { mutableIntStateOf(0) }

    val collapseSafeTopPx = with(density) { (statusInsetTop + Spacing.sm).roundToPx() }
    val collapseRangePx = with(density) { ALARM_COLLAPSE_RANGE.toPx() }
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
    val collapse by collapseState
    // Threshold haptic + magnetic snap, both keyed on listState.
    AlarmCollapseEffects(listState, collapseState, collapseRangePx, uiState.hapticsEnabled)

    // Pull-to-expand: at the top of the list, an overscroll-down drag unwraps the collapsed thinking
    // 1:1 with the finger. One reveal value (revealed pixels) drives the disclosure and the
    // release/tap animation, so there's no second source to dip against. Reset per turn.
    val scope = rememberCoroutineScope()
    var thinkingExpanded by rememberSaveable(thread.turnIndex) { mutableStateOf(false) }
    val reveal = remember(thread.turnIndex) { Animatable(0f) }
    val thinkingFullPx = remember(thread.turnIndex) { mutableIntStateOf(0) }
    val canPull = rememberUpdatedState(thread.process.isNotEmpty() && !thinkingExpanded)
    val pullHaptics = rememberCronHaptics()
    val expandTriggerMaxPx = with(density) { THINKING_EXPAND_TRIGGER_MAX.toPx() }
    // Tracks whether the pull is past the release-to-open threshold, so a click fires once per crossing.
    val pastThreshold = remember(thread.turnIndex) { mutableStateOf(false) }
    val pullConnection = remember(listState, reveal, expandTriggerMaxPx, pastThreshold) {
        object : NestedScrollConnection {
            // Pixels of pull past which releasing snaps the block open — the same point onPreFling tests.
            fun triggerPx(): Float {
                val full = thinkingFullPx.intValue
                return if (full > 0) minOf(full * THINKING_EXPAND_THRESHOLD, expandTriggerMaxPx) else Float.MAX_VALUE
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Dragging up while a partial peek is open unwinds it (1:1) before the list scrolls.
                // Skip once fully expanded, or the upward scroll is eaten instead of moving the list.
                if (available.y < 0f && reveal.value > 0f && !thinkingExpanded) {
                    val next = (reveal.value + available.y).coerceAtLeast(0f)
                    val consumed = next - reveal.value
                    pastThreshold.value = next >= triggerPx()
                    scope.launch { reveal.snapTo(next) }
                    return Offset(0f, consumed)
                }
                // At the top, dragging down on a collapsible, collapsed thread grows the reveal in
                // pixels (tracks the finger), gently resisting near full so it never runs ahead.
                if (available.y > 0f && canPull.value && !listState.canScrollBackward) {
                    val full = thinkingFullPx.intValue
                    val rubber = if (full > 0) {
                        (1f - reveal.value / full).coerceIn(THINKING_PULL_RUBBER_FLOOR, 1f)
                    } else {
                        1f
                    }
                    val next = reveal.value + available.y * rubber
                    val nowPast = next >= triggerPx()
                    if (nowPast && !pastThreshold.value) pullHaptics.tick() // click as the pull reaches the open point
                    pastThreshold.value = nowPast
                    scope.launch { reveal.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (reveal.value <= 0f) return Velocity.Zero
                val full = thinkingFullPx.intValue
                if (full > 0 && reveal.value >= triggerPx()) {
                    reveal.animateTo(full.toFloat())
                    thinkingExpanded = true
                } else {
                    reveal.animateTo(0f)
                }
                return available
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            item(key = "greeting") {
                GreetingHeader(
                    prefix = uiState.greetingPrefix,
                    name = uiState.greetingName,
                )
            }
            item(key = "alarm-spacer") {
                Spacer(Modifier.height(with(density) { cardFullHeightPx.toDp() }))
            }
            item(key = "thread") {
                AiThinkingThread(
                    thread = thread,
                    isRunning = uiState.isRetrying,
                    expanded = thinkingExpanded,
                    // Tap animates the same reveal value (snap to full before a close so it eases
                    // down from the natural height the expanded state was showing).
                    onExpandedChange = { open ->
                        scope.launch {
                            if (open) {
                                reveal.animateTo(thinkingFullPx.intValue.toFloat())
                                thinkingExpanded = true
                            } else {
                                thinkingExpanded = false
                                reveal.snapTo(thinkingFullPx.intValue.toFloat())
                                reveal.animateTo(0f)
                            }
                        }
                    },
                    expandPx = reveal.value,
                    onFullHeight = { if (it != thinkingFullPx.intValue) thinkingFullPx.intValue = it },
                    expansionFraction = if (thinkingExpanded) 1f
                        else (reveal.value / thinkingFullPx.intValue.toFloat().coerceAtLeast(1f))
                            .coerceIn(0f, 1f),
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
            top = collapse.top,
            gradientAlpha = collapse.gradientAlpha,
            collapseFraction = collapse.fraction,
        ) { collapseFraction ->
            CollapsibleAlarmCard(
                dateLabel = uiState.dateLabel,
                alarmTime = uiState.sessionDisplay?.alarmTime,
                sleepDurationLabel = uiState.sleepStats?.durationLabel,
                sleepSegments = uiState.sleepStats?.segments.orEmpty(),
                collapseFraction = collapseFraction,
                onFullHeight = { cardFullHeightPx = it },
            )
        }
    }
}

/**
 * CSS-`sticky`-with-top-offset alarm card, rendered as an overlay over the list, consuming the
 * pre-computed [top]/[gradientAlpha]/[collapseFraction] (single source in the caller). A full-width
 * background→transparent gradient ([gradientAlpha]) fades in behind it as it pins, dissolving content
 * that slides under into the page background.
 */
@Composable
private fun BoxScope.StickyAlarm(
    safeTopPx: Int,
    top: Int,
    gradientAlpha: Float,
    collapseFraction: Float,
    card: @Composable (collapseFraction: Float) -> Unit,
) {
    val density = LocalDensity.current
    val background = MaterialTheme.colorScheme.background
    // Scrim tracks the card's CURRENT (collapsing) visible height — independent of the spacer's full
    // reservation — so the solid band always hugs the shrinking card bottom.
    var visiblePx by remember { mutableIntStateOf(0) }
    val cardBottomPx = safeTopPx + visiblePx
    val belowFadePx = with(density) { Spacing.xxxl.toPx() }
    val totalPx = cardBottomPx + belowFadePx
    val solidStop = if (totalPx > 0f) (cardBottomPx / totalPx).coerceIn(0f, 1f) else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(density) { totalPx.toDp() })
            .graphicsLayer { alpha = gradientAlpha }
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
            .graphicsLayer { translationY = top.toFloat() }
            // Hugs at 12dp (tighter than the 20dp content) — kept constant so the collapsed bar
            // holds the same width/margin as the expanded card.
            .padding(horizontal = Spacing.md)
            .onSizeChanged { if (it.height != visiblePx) visiblePx = it.height },
    ) { card(collapseFraction) }
}
