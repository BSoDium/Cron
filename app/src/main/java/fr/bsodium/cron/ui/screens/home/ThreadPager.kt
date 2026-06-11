package fr.bsodium.cron.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import fr.bsodium.cron.ui.components.bleedHorizontally
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.ThreadRole
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.launch

/** Per-iteration pull-to-expand state for the thinking disclosure — one instance per `turnIndex`, so
 *  each pager page keeps its own reveal/expanded state and the pull gesture drives only the settled page. */
internal class PullState {
    val reveal = Animatable(0f)
    val fullPx = mutableIntStateOf(0)
    var expanded by mutableStateOf(false)
    var pastThreshold by mutableStateOf(false)
}

/**
 * The thread, horizontally pageable: one page per replan iteration, bound to [pagerState] — the single
 * source of truth the caller derives the tab selection from. Each page carries its own [PullState] (from
 * [pullStates]) so the pull-to-expand reveal is per-iteration, and only the latest page shows the morphing
 * thinking shape.
 *
 * The caller sizes the pager to the settled page's height (a fixed height keeps the pager draggable —
 * `wrapContentHeight` silently disables the horizontal drag). Each page measures **unbounded** and
 * reports its natural height via [onPageHeight], so when its thinking expands the natural height grows
 * past the current pager height, the caller's height follows, and the content is no longer clipped.
 * [beyondViewportPageCount] pre-composes the neighbour so the swipe doesn't stall parsing its markdown.
 *
 * The pager bleeds past the list's horizontal content padding so the swipe/clip area spans the full
 * screen (the neighbour clips at the edge, not mid-screen); each page re-insets its content by the same
 * [Spacing.xl] so the text stays put and [AiThinkingThread]'s own header bleed still lands on the edge.
 */
@Composable
internal fun ThreadPager(
    iterations: List<AiIterationUi>,
    pagerState: PagerState,
    pullStates: SnapshotStateMap<Int, PullState>,
    onJumpToLatest: () -> Unit,
    onPageHeight: (turn: Int, px: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.bleedHorizontally(Spacing.xl),
        // getOrNull: the pager's pageCount (iterations.size, read in the caller) can update a frame before
        // this lambda recomposes, so the key map may probe an index past the current list — fall back to
        // the index itself for that transient frame instead of crashing.
        key = { iterations.getOrNull(it)?.turnIndex ?: it },
        verticalAlignment = Alignment.Top,
        // No pageSpacing: each page's own xl padding forms a symmetric 2·xl gutter between adjacent content.
        // 0 (not 1): entry parses only the visible page's markdown instead of two — the neighbour composes
        // when a swipe starts, where a tiny cost is fine. Halves the thread's first-frame cost.
        beyondViewportPageCount = 0,
    ) { page ->
        val iter = iterations.getOrNull(page) ?: return@HorizontalPager
        // getOrPut once per turnIndex; the current page always has its state ready for the caller's pull
        // connection. Wrapping in remember keeps the map write off the per-frame recomposition path.
        val pull = remember(iter.turnIndex) { pullStates.getOrPut(iter.turnIndex) { PullState() } }
        val scope = rememberCoroutineScope()
        val isLatest = page == iterations.lastIndex
        // unbounded so the page measures its NATURAL height even when the pager is momentarily shorter
        // (during an expand) — onSizeChanged then reports the real height instead of the clipped one.
        Box(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .onSizeChanged { onPageHeight(iter.turnIndex, it.height) },
        ) {
            AiThinkingThread(
                thread = iter.thread,
                modifier = Modifier.padding(horizontal = Spacing.xl), // re-inset after the pager's full-screen bleed
                expanded = pull.expanded,
                onExpandedChange = { open ->
                    scope.launch {
                        if (open) {
                            pull.reveal.animateTo(pull.fullPx.intValue.toFloat())
                            pull.expanded = true
                        } else {
                            pull.expanded = false
                            pull.reveal.snapTo(pull.fullPx.intValue.toFloat())
                            pull.reveal.animateTo(0f)
                        }
                    }
                },
                // Per-frame reveal values as providers: the drag invalidates the reveal's measure pass and
                // the chevron/hint draw layers — never this page's composition.
                expandPx = { pull.reveal.value },
                onFullHeight = { if (it != pull.fullPx.intValue) pull.fullPx.intValue = it },
                expansionFraction = {
                    if (pull.expanded) 1f
                    else (pull.reveal.value / pull.fullPx.intValue.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                },
                role = if (isLatest) ThreadRole.Latest else ThreadRole.Older(iter.ranAtEpochMs, onJumpToLatest),
            )
        }
    }
}
