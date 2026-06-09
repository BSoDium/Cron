package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlin.math.roundToInt

/**
 * Two-way pager ↔ tab-selection binding. The pager commits the selection on settle (a mid-fling never
 * fights the user); a selection change (tab tap, or a fresh replan re-keying to the newest turn) animates
 * the pager to match — no `isScrollInProgress` guard, so a tap made while the pager is still settling
 * still wins (drags never move the selection mid-gesture, so this can't fight a drag). A haptic ticks
 * exactly at the midpoint crossing between tabs, but only for real drags, never while a tap/auto-nav
 * animation drives the pager.
 *
 * Returns whether the USER is actively dragging the pager — the tab strip tracks the raw position 1:1
 * then, and cross-fades to the settled selection otherwise.
 */
@Composable
internal fun rememberPagerSelectionBinding(
    pagerState: PagerState,
    iterations: List<AiIterationUi>,
    selectedTurn: Int,
    onSelectedTurnChange: (Int) -> Unit,
    hapticsEnabled: Boolean,
): State<Boolean> {
    val iterationsState = rememberUpdatedState(iterations)
    val selectedTurnState = rememberUpdatedState(selectedTurn)
    val onChange = rememberUpdatedState(onSelectedTurnChange)
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))
    // True while the pager animates to a tap/auto-nav target — suppresses the swipe haptic so only real
    // drags tick at the midpoint (a tap ticks immediately in ReplanHistoryBar).
    var programmaticScroll by remember { mutableStateOf(false) }
    // Pager → selection: commit on settle. No tick here — the midpoint haptic below covers the swipe.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val turn = iterationsState.value.getOrNull(page)?.turnIndex ?: return@collect
            if (turn != selectedTurnState.value) onChange.value(turn)
        }
    }
    // Selection → pager.
    LaunchedEffect(selectedTurn) {
        val idx = iterationsState.value.indexOfFirst { it.turnIndex == selectedTurn }
        if (idx >= 0 && idx != pagerState.currentPage) {
            programmaticScroll = true
            try {
                pagerState.animateScrollToPage(idx)
            } finally {
                programmaticScroll = false
            }
        }
    }
    // Midpoint haptic: the nearest index flips at the .5 boundary.
    LaunchedEffect(pagerState) {
        var lastNearest = pagerState.currentPage
        snapshotFlow { (pagerState.currentPage + pagerState.currentPageOffsetFraction).roundToInt() }
            .collect { nearest ->
                if (nearest != lastNearest) {
                    lastNearest = nearest
                    if (!programmaticScroll) haptics.value.tick()
                }
            }
    }
    return remember { derivedStateOf { pagerState.isScrollInProgress && !programmaticScroll } }
}
