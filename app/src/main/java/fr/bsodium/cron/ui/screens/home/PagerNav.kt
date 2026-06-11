package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import fr.bsodium.cron.ui.components.rememberCronHaptics
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * One-way pager navigation for the replan thread. The pager is the **single source of truth**: the caller
 * derives the tab selection from [PagerState.settledPage], so the lit tab, the displayed page, and the
 * "jump to latest" footer always agree — there is no second selection state to drift. This only drives
 * the pager in the other direction: [scrollToTurn] for a tab tap / jump-to-latest, and an auto-advance to
 * the newest page when a fresh replan grows the list. Every such scroll is flagged [PagerNav.dragging]
 * `= false` (programmatic) so the midpoint swipe haptic stays silent for it — only real drags tick at the
 * boundary; a tap ticks once in `ReplanHistoryBar`.
 */
internal class PagerNav(
    /** True only while the USER is actively dragging — the tab strip tracks the raw position 1:1 then,
     *  and springs to the settled tab otherwise. */
    val dragging: State<Boolean>,
    /** Animate the pager to the page for [turn] (a tab tap or jump-to-latest). No-op if already there. */
    val scrollToTurn: (Int) -> Unit,
)

@Composable
internal fun rememberPagerNav(
    pagerState: PagerState,
    iterations: List<AiIterationUi>,
    newestTurn: Int,
    hapticsEnabled: Boolean,
): PagerNav {
    val scope = rememberCoroutineScope()
    val iterationsState = rememberUpdatedState(iterations)
    val haptics = rememberUpdatedState(rememberCronHaptics(enabled = hapticsEnabled))
    // True while the pager animates to a tap / jump / auto-advance target — keeps that motion out of
    // [dragging] and silences the midpoint swipe haptic for it (a tap's own tick fires in the tab strip).
    var programmaticScroll by remember { mutableStateOf(false) }

    suspend fun scrollProgrammatically(idx: Int) {
        if (idx < 0 || idx == pagerState.currentPage) return
        programmaticScroll = true
        try {
            pagerState.animateScrollToPage(idx)
        } finally {
            programmaticScroll = false
        }
    }

    // Fresh replan → advance to the newest page, but only once the pager's pageCount reflects the grown
    // list: animateScrollToPage clamps to the current pageCount, so scrolling before it updates would
    // land on the stale last page (the desync this fix removes). Keyed on newestTurn → fires once per
    // genuinely-new iteration; on first composition the pager is already there, so it awaits then no-ops.
    LaunchedEffect(newestTurn) {
        val idx = iterationsState.value.indexOfFirst { it.turnIndex == newestTurn }
        if (idx < 0) return@LaunchedEffect
        snapshotFlow { pagerState.pageCount }.filter { it > idx }.first()
        scrollProgrammatically(idx)
    }

    // Midpoint haptic: the nearest index flips at the .5 boundary — suppressed for programmatic scrolls
    // so only real drags tick.
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

    val dragging = remember { derivedStateOf { pagerState.isScrollInProgress && !programmaticScroll } }
    return remember {
        PagerNav(
            dragging = dragging,
            scrollToTurn = { turn ->
                val idx = iterationsState.value.indexOfFirst { it.turnIndex == turn }
                scope.launch { scrollProgrammatically(idx) }
            },
        )
    }
}
