package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.launch

private const val THINKING_EXPAND_THRESHOLD = 0.4f
private const val THINKING_PULL_RUBBER_FLOOR = 0.15f
private val THINKING_EXPAND_TRIGGER_MAX = 120.dp

internal class PullState {
    val reveal = Animatable(0f)
    val fullPx = mutableIntStateOf(0)
    var expanded by mutableStateOf(false)
    var pastThreshold by mutableStateOf(false)
}

@Composable
internal fun rememberDetailPullConnection(
    listState: LazyListState,
    pullState: PullState,
    hasProcess: Boolean,
    hapticsEnabled: Boolean,
): NestedScrollConnection {
    val scope = rememberCoroutineScope()
    val expandTriggerMaxPx = with(LocalDensity.current) { THINKING_EXPAND_TRIGGER_MAX.toPx() }
    val hasProcessState = rememberUpdatedState(hasProcess)
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))

    return remember(listState, pullState, expandTriggerMaxPx) {
        object : NestedScrollConnection {
            fun triggerPx(): Float {
                val full = pullState.fullPx.intValue
                return if (full > 0) minOf(full * THINKING_EXPAND_THRESHOLD, expandTriggerMaxPx) else Float.MAX_VALUE
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y < 0f && pullState.reveal.value > 0f && !pullState.expanded) {
                    val next = (pullState.reveal.value + available.y).coerceAtLeast(0f)
                    val consumed = next - pullState.reveal.value
                    pullState.pastThreshold = next >= triggerPx()
                    scope.launch { pullState.reveal.snapTo(next) }
                    return Offset(0f, consumed)
                }
                val canPull = hasProcessState.value && !pullState.expanded
                if (available.y > 0f && canPull && !listState.canScrollBackward) {
                    val full = pullState.fullPx.intValue
                    val rubber = if (full > 0) (1f - pullState.reveal.value / full).coerceIn(THINKING_PULL_RUBBER_FLOOR, 1f) else 1f
                    val next = pullState.reveal.value + available.y * rubber
                    val nowPast = next >= triggerPx()
                    if (nowPast && !pullState.pastThreshold) haptics.value.tick()
                    pullState.pastThreshold = nowPast
                    scope.launch { pullState.reveal.snapTo(next) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullState.reveal.value <= 0f) return Velocity.Zero
                val full = pullState.fullPx.intValue
                if (full > 0 && pullState.reveal.value >= triggerPx()) {
                    pullState.reveal.animateTo(full.toFloat())
                    pullState.expanded = true
                } else {
                    pullState.reveal.animateTo(0f)
                }
                return available
            }
        }
    }
}
