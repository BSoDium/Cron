package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

/** Sticky alarm-card collapse geometry. fraction 0 = expanded, 1 = collapsed; pinned ⟺ distancePx > 0. */
internal data class AlarmCollapse(val top: Int, val gradientAlpha: Float, val fraction: Float, val distancePx: Float)

/**
 * Deterministic landing for the magnetic snap — a spring's asymptotic tail reads as a stall.
 * Sanctioned motionScheme exception: see docs/expressive.md § Sanctioned exceptions.
 */
private val ALARM_SNAP_SPEC = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)

/** Debounce for the gap where `isScrollInProgress` flips false a frame before the post-release fling starts, so the snap waits for the TRUE settle instead of the fling preempting it. */
private const val SETTLE_DEBOUNCE_MS = 50L

/**
 * Wires the two scroll-driven side effects for the collapsing alarm card, both keyed on [listState]:
 * a haptic tick when [collapse] crosses the snap threshold, and the magnetic snap that animates a
 * mid-collapse settle to the nearest reachable end. Collapsing scrolls the list UP (stalls when the
 * content below is too short to finish), expanding scrolls DOWN (always reachable via the off-screen
 * greeting) — so it only commits to collapsing when there's room, and never rests half-way.
 */
@Composable
internal fun AlarmCollapseEffects(
    listState: LazyListState,
    collapse: State<AlarmCollapse>,
    rangePx: Float,
    hapticsEnabled: Boolean,
) {
    // Read live inside the listState-keyed effects below: haptics/range/collapse can each change or resolve late, so none should stay captured stale.
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))
    val range = rememberUpdatedState(rangePx)
    val collapseRef = rememberUpdatedState(collapse)
    LaunchedEffect(listState) {
        snapshotFlow { collapseRef.value.value.fraction >= 0.5f }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.value.tick() }
    }
    // Latest scroll direction within the collapse range, so a settle-in-between completes toward where the user was headed instead of a fixed midpoint that yanks a down-scroll back up.
    val scrollingDown = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var last = collapseRef.value.value.distancePx
        snapshotFlow { collapseRef.value.value.distancePx }.collect { d ->
            if (d > last + 0.5f) scrollingDown.value = true
            else if (d < last - 0.5f) scrollingDown.value = false
            last = d
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                // Wait out the frame where release flips this false before the fling, then re-check, so the snap runs from the true settle (else the fling preempts it).
                delay(SETTLE_DEBOUNCE_MS)
                if (listState.isScrollInProgress) return@collect
                val c = collapseRef.value.value
                // Only nudge a scroll that came to REST between the two stable states — a momentum fling that already reached a stable end is left untouched.
                if (c.fraction <= 0.001f || c.fraction >= 0.999f) return@collect
                try {
                    if (scrollingDown.value && listState.canScrollForward) {
                        listState.animateScrollBy(range.value - c.distancePx, ALARM_SNAP_SPEC)
                        // Hit the content bottom before fully collapsing → expand to the very top instead of stalling.
                        val rest = collapseRef.value.value
                        if (rest.fraction > 0.001f && rest.fraction < 0.999f) {
                            listState.animateScrollToItem(0)
                        }
                    } else {
                        // Headed up (or can't collapse further) → land at the very top so the greeting + full card are visible (not just pinned under the status bar).
                        listState.animateScrollToItem(0)
                    }
                } catch (e: CancellationException) {
                    // User grabbed the list mid-snap → the SCROLL is cancelled, not us; rethrow only if WE were cancelled (composition gone).
                    coroutineContext.ensureActive()
                }
            }
    }
}
