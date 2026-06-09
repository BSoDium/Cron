package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs

// Pull-to-expand feel: the release fraction (of full height) past which it snaps open (springs back
// below), and the rubber-band floor so the reveal still creeps near full instead of fully stalling.
private const val THINKING_EXPAND_THRESHOLD = 0.4f
private const val THINKING_PULL_RUBBER_FLOOR = 0.15f

// Absolute cap on the release-to-expand pull distance, so a tall timeline (where 40% of full height
// exceeds the screen) is still openable with a short drag.
private val THINKING_EXPAND_TRIGGER_MAX = 120.dp

/**
 * Pull-to-expand physics for the thinking disclosure: at the top of the list, an overscroll-down drag
 * unwraps the collapsed thinking 1:1 with the finger — but only on the SETTLED pager page (no horizontal
 * swipe in flight), each page keeping its own reveal via its [PullState]. One reveal value drives the
 * disclosure and the release/tap animation, so there's no second source to dip against.
 */
@Composable
internal fun rememberThinkingPullConnection(
    listState: LazyListState,
    pagerState: PagerState,
    iterations: List<AiIterationUi>,
    pullStates: SnapshotStateMap<Int, PullState>,
    hapticsEnabled: Boolean,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val expandTriggerMaxPx = with(LocalDensity.current) { THINKING_EXPAND_TRIGGER_MAX.toPx() }
    // State-wrapped so the remembered connection reads the live values (iterations change per stream
    // frame; a haptics-pref toggle swaps the instance) instead of captures stale at construction.
    val iterationsState = rememberUpdatedState(iterations)
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))
    return remember(listState, pagerState, expandTriggerMaxPx) {
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
                    if (nowPast && !s.pastThreshold) haptics.value.tick() // click as the pull reaches the open point
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
}
