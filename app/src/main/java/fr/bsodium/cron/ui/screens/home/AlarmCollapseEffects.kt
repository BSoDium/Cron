package fr.bsodium.cron.ui.screens.home

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
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
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    LaunchedEffect(listState) {
        snapshotFlow { collapse.value.fraction >= 0.5f }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.tick() }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                // The release flips this false for a frame before the fling; wait it out, then re-check.
                delay(SETTLE_DEBOUNCE_MS)
                if (listState.isScrollInProgress) {
                    Log.d("AlarmSnap", "settle deferred — fling in progress")
                    return@collect
                }
                val c = collapse.value
                Log.d(
                    "AlarmSnap",
                    "settle fraction=${"%.3f".format(c.fraction)} dist=${c.distancePx} canFwd=${listState.canScrollForward} " +
                        "idx=${listState.firstVisibleItemIndex} off=${listState.firstVisibleItemScrollOffset} rangePx=$rangePx",
                )
                if (c.fraction <= 0.001f || c.fraction >= 0.999f) {
                    Log.d("AlarmSnap", "  → at end, skip")
                    return@collect
                }
                try {
                    if (c.fraction >= 0.5f && listState.canScrollForward) {
                        Log.d("AlarmSnap", "  → COLLAPSE animateScrollBy(${rangePx - c.distancePx})")
                        listState.animateScrollBy(rangePx - c.distancePx, ALARM_SNAP_SPEC)
                        // Hit the content bottom before fully collapsing → expand instead of stalling.
                        val rest = collapse.value
                        if (rest.fraction > 0.001f && rest.fraction < 0.999f) {
                            listState.animateScrollBy(-rest.distancePx, ALARM_SNAP_SPEC)
                        }
                    } else {
                        Log.d("AlarmSnap", "  → EXPAND animateScrollBy(${-c.distancePx})")
                        listState.animateScrollBy(-c.distancePx, ALARM_SNAP_SPEC)
                    }
                    Log.d("AlarmSnap", "  ← done fraction=${"%.3f".format(collapse.value.fraction)}")
                } catch (e: CancellationException) {
                    Log.d("AlarmSnap", "  ✗ cancelled: ${e.message}")
                    // User grabbed the list mid-snap → the SCROLL is cancelled, not us. Keep observing
                    // so the next settle re-snaps; rethrow only if WE were cancelled (composition gone).
                    coroutineContext.ensureActive()
                }
            }
    }
}
