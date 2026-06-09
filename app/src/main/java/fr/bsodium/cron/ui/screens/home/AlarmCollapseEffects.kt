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

// Deterministic landing for the magnetic snap — a spring's asymptotic tail reads as a stall.
// Sanctioned motionScheme exception: see docs/expressive.md § Sanctioned exceptions.
private val ALARM_SNAP_SPEC = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
// A drag-release flips isScrollInProgress false for a frame BEFORE the post-release fling starts.
// Wait this gap out and re-check, so the snap runs from the TRUE settle — not the momentary gap, where
// the fling would immediately preempt it ("Mutation interrupted") and leave the card stuck mid-collapse.
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
    // Read live inside the listState-keyed effects below: a haptics-pref toggle swaps the instance, and the
    // range resolves from the card's measured height a frame or two after the effects launch — neither should
    // stay captured stale.
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))
    val range = rememberUpdatedState(rangePx)
    LaunchedEffect(listState) {
        snapshotFlow { collapse.value.fraction >= 0.5f }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.value.tick() }
    }
    // Latest scroll direction within the collapse range, so a settle-in-between completes toward where the
    // user was headed (down → collapse, up → expand) instead of a fixed midpoint that yanks a down-scroll back up.
    val scrollingDown = remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var last = collapse.value.distancePx
        snapshotFlow { collapse.value.distancePx }.collect { d ->
            if (d > last + 0.5f) scrollingDown.value = true
            else if (d < last - 0.5f) scrollingDown.value = false
            last = d
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                // The release flips this false for a frame before the fling; wait it out, then re-check
                // so the snap runs from the true settle (else the fling preempts it: "Mutation interrupted").
                delay(SETTLE_DEBOUNCE_MS)
                if (listState.isScrollInProgress) return@collect
                val c = collapse.value
                // Only nudge a scroll that came to REST between the two stable states — a momentum fling that
                // already carried through to a stable end (or into the content below) is left untouched.
                if (c.fraction <= 0.001f || c.fraction >= 0.999f) return@collect
                try {
                    if (scrollingDown.value && listState.canScrollForward) {
                        listState.animateScrollBy(range.value - c.distancePx, ALARM_SNAP_SPEC)
                        // Hit the content bottom before fully collapsing → expand to the very top instead of stalling.
                        val rest = collapse.value
                        if (rest.fraction > 0.001f && rest.fraction < 0.999f) {
                            listState.animateScrollToItem(0)
                        }
                    } else {
                        // Headed up (or can't collapse further) → land at the very top so the greeting + full
                        // card are visible (not just pinned under the status bar).
                        listState.animateScrollToItem(0)
                    }
                } catch (e: CancellationException) {
                    // User grabbed the list mid-snap → the SCROLL is cancelled, not us. Keep observing
                    // so the next settle re-snaps; rethrow only if WE were cancelled (composition gone).
                    coroutineContext.ensureActive()
                }
            }
    }
}
