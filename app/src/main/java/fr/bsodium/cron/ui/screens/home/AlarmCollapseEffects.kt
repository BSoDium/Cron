package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** Sticky alarm-card collapse geometry. fraction 0 = expanded, 1 = collapsed; pinned ⟺ distancePx > 0. */
internal data class AlarmCollapse(val top: Int, val gradientAlpha: Float, val fraction: Float, val distancePx: Float)

/**
 * A haptic tick each time the collapse crosses the snap threshold while scrolling. The magnetic snap
 * itself lives in the list's nested-scroll `onPostFling` (per-gesture, so it can't go silent for the
 * session) — this effect is purely the threshold feedback.
 */
@Composable
internal fun AlarmThresholdHaptic(
    listState: LazyListState,
    collapse: State<AlarmCollapse>,
    hapticsEnabled: Boolean,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    LaunchedEffect(listState) {
        snapshotFlow { collapse.value.fraction >= 0.5f }
            .distinctUntilChanged()
            .drop(1)
            .collect { haptics.tick() }
    }
}
