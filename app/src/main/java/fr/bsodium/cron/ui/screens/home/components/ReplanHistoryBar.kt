@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.components.CronHaptics
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.label
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt


/** Frame cap for the auto-scroll's wait-for-scroll-range-to-settle loop (animateContentSize widens the
 *  strip over several frames after a tab is added); bounds the wait when the range never reaches the target. */
private const val MAX_SETTLE_FRAMES = 30

/** Scroll offset that centres [span] (a tab's left..right x in row coords) in the viewport, clamped to
 *  the scroll range so end tabs rest aligned with the card. */
private fun centreTarget(span: IntRange, padPx: Int, viewportPx: Int, maxScroll: Int): Int {
    val tabCentre = (span.first + span.last) / 2 + padPx
    return (tabCentre - viewportPx / 2).coerceIn(0, maxScroll)
}


/** Linear selection fraction — drives the corner-radius morph so the shape tracks the swipe smoothly.
 *  The colour inversion eases this through [fastMiddle] separately (see ReplanTab). */
private fun selectedFraction(index: Int, position: Float): Float = (1f - abs(index - position)).coerceIn(0f, 1f)

/**
 * A tab's selection fraction (0 = unselected, 1 = selected), as ONE continuous animatable — never a branch
 * switch between two value sources, which is what used to flicker at settle. While the user is actively
 * [dragging], the value is snapped to the raw pager position ([selectedFraction]) so neighbours cross-morph
 * 1:1 under the finger; when the drag ends (or on a tap / auto-nav) it springs from wherever it currently is
 * to the settled target. Because the value itself is continuous, the races at settle — [dragging] jittering
 * between drag-end and fling, or [selectedTurn] lagging the settled page by a frame — produce sub-pixel
 * spring motion instead of a visible state flip.
 */
@Composable
private fun rememberTabFraction(index: Int, turnIndex: Int, position: () -> Float, selectedTurn: Int, dragging: Boolean): Float {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val target = if (turnIndex == selectedTurn) 1f else 0f
    val fraction = remember { Animatable(target) }
    LaunchedEffect(dragging, target) {
        if (dragging) snapshotFlow { selectedFraction(index, position()) }.collect { fraction.snapTo(it) }
        else fraction.animateTo(target, spec)
    }
    return fraction.value
}

/**
 * One tab slot — the SINGLE source of the per-tab wiring (isLatest, shape, click guard), shared by the
 * probes and both strip layouts so a probe always measures exactly what the strip renders. A null
 * [onSelect] renders an inert tab (the off-screen measure passes).
 */
@Composable
private fun StripTab(
    iterations: List<AiIterationUi>,
    index: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
    selectedTurn: Int = -1,
    onSelect: ((Int) -> Unit)? = null,
    haptics: CronHaptics? = null,
) {
    val iteration = iterations[index]
    ReplanTab(
        iteration = iteration,
        fraction = fraction,
        isLatest = index == iterations.lastIndex && iterations.size > 1,
        shape = tabShape(index, iterations.lastIndex, fraction),
        isNew = isNew,
        modifier = modifier,
        onClick = {
            if (onSelect != null && iteration.turnIndex != selectedTurn) {
                haptics?.tick()
                onSelect(iteration.turnIndex)
            }
        },
    )
}


/**
 * The planning-iteration strip: connected tabs that **morph continuously** with the thread pager —
 * [position] provides the pager's fractional position (`currentPage + offsetFraction`); it is only ever
 * read inside snapshot flows / derived state, so the strip composes per selection change, not per swipe
 * frame. Stretches to fill when the tabs fit, scrolls (with tap-to-centre) when they overflow.
 */
@Composable
internal fun ReplanHistoryBar(
    iterations: List<AiIterationUi>,
    position: () -> Float,
    selectedTurn: Int,
    dragging: Boolean,
    onSelect: (Int) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCronHaptics(hapticsEnabled)
    // turnIndices present when the strip first composed; any tab added later animates itself in.
    val initialTurns = remember { iterations.map { it.turnIndex }.toSet() }
    // Natural total width, cached per content (density via the lambda's measure environment): the probe
    // subcomposition is skipped entirely on re-measures with unchanged iterations.
    val naturalWidthCache = remember(iterations) { mutableListOf<Int>() }
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        if (naturalWidthCache.isEmpty()) {
            naturalWidthCache += subcompose("probe") { ProbeTabs(iterations) }.first().measure(Constraints()).width
        }
        val fits = naturalWidthCache.first() <= constraints.maxWidth
        val tabs = subcompose("tabs") {
            if (fits) StretchedTabs(iterations, position, selectedTurn, dragging, initialTurns, onSelect, haptics)
            else ScrollableTabs(iterations, position, selectedTurn, dragging, initialTurns, onSelect, haptics)
        }.first().measure(constraints)
        layout(constraints.maxWidth, tabs.height) { tabs.place(0, 0) }
    }
}

/** Off-screen measure pass: the tabs at their natural width, so the caller can compare to the viewport.
 *  Shape/colour don't affect measured width, so the resting (unselected) state is used. */
@Composable
private fun ProbeTabs(iterations: List<AiIterationUi>) {
    Row(
        modifier = Modifier.padding(horizontal = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        iterations.forEachIndexed { index, _ ->
            StripTab(iterations = iterations, index = index, fraction = 0f)
        }
    }
}

/** Tabs fill the row — used when they fit; no scroll, no centring. Each tab is weighted by its NATURAL width
 *  (not equal), so a long title never shrinks below its content and clips: since we're only here when the tabs
 *  fit, proportional distribution gives every tab ≥ its natural width while still filling the row. */
@Composable
private fun StretchedTabs(
    iterations: List<AiIterationUi>,
    position: () -> Float,
    selectedTurn: Int,
    dragging: Boolean,
    initialTurns: Set<Int>,
    onSelect: (Int) -> Unit,
    haptics: CronHaptics,
) {
    // Per-tab natural widths, cached per content — the probe subcomposition runs once per iterations change.
    val natWidths = remember(iterations) { mutableListOf<Int>() }
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) { constraints ->
        if (natWidths.isEmpty()) {
            natWidths += subcompose("probe") {
                iterations.forEachIndexed { index, _ ->
                    StripTab(iterations = iterations, index = index, fraction = 0f)
                }
            }.map { it.measure(Constraints()).width }
        }
        val content = subcompose("content") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                iterations.forEachIndexed { index, iteration ->
                    StripTab(
                        iterations = iterations,
                        index = index,
                        fraction = rememberTabFraction(index, iteration.turnIndex, position, selectedTurn, dragging),
                        isNew = iteration.turnIndex !in initialTurns,
                        selectedTurn = selectedTurn,
                        onSelect = onSelect,
                        haptics = haptics,
                        modifier = Modifier.weight(natWidths.getOrElse(index) { 1 }.toFloat().coerceAtLeast(1f)),
                    )
                }
            }
        }.first().measure(constraints)
        layout(content.width, content.height) { content.place(0, 0) }
    }
}

/** Full-bleed, horizontally scrollable strip with tap-to-centre — used when the tabs overflow. */
@Composable
private fun ScrollableTabs(
    iterations: List<AiIterationUi>,
    position: () -> Float,
    selectedTurn: Int,
    dragging: Boolean,
    initialTurns: Set<Int>,
    onSelect: (Int) -> Unit,
    haptics: CronHaptics,
) {
    // Start pinned to the RIGHT end (ScrollState clamps the huge initial to maxValue on first measure):
    // the latest tab lives there, so the strip never flashes scrolled-left before the centring effect runs.
    val scrollState = rememberScrollState(initial = Int.MAX_VALUE)
    // Each tab's [left, right] x within the scroll content, for bring-into-view.
    val spans = remember { mutableStateMapOf<Int, IntRange>() }
    var viewportPx by remember { mutableIntStateOf(0) }
    var didInitialScroll by remember { mutableStateOf(false) }
    val padPx = with(LocalDensity.current) { Spacing.md.roundToPx() }
    // Expressive spatial spring so the tap-to-centre scroll glides rather than snapping.
    val scrollSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(selectedTurn, viewportPx) {
        if (viewportPx <= 0) return@LaunchedEffect
        val span = snapshotFlow { spans[selectedTurn] }.filterNotNull().first()
        val wanted = (span.first + span.last) / 2 + padPx - viewportPx / 2
        var frames = 0
        while (scrollState.maxValue < wanted && frames < MAX_SETTLE_FRAMES) {
            withFrameNanos { }
            frames++
        }
        val target = centreTarget(span, padPx, viewportPx, scrollState.maxValue)
        if (!didInitialScroll) {
            scrollState.scrollTo(target)
            didInitialScroll = true
        } else {
            scrollState.animateScrollTo(target, scrollSpec)
        }
    }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { viewportPx = it.width }) {
        Row(
            // The md padding is INSIDE the scroll so the resting first/last tabs line up with the card.
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = Spacing.md)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            iterations.forEachIndexed { index, iteration ->
                StripTab(
                    iterations = iterations,
                    index = index,
                    fraction = rememberTabFraction(index, iteration.turnIndex, position, selectedTurn, dragging),
                    isNew = iteration.turnIndex !in initialTurns,
                    selectedTurn = selectedTurn,
                    onSelect = onSelect,
                    haptics = haptics,
                    modifier = Modifier.onPlaced { coords ->
                        val b = coords.boundsInParent()
                        val range = b.left.roundToInt()..b.right.roundToInt()
                        // Structural equality guard: skip the snapshot write (and invalidation) when unmoved.
                        if (spans[iteration.turnIndex] != range) spans[iteration.turnIndex] = range
                    },
                )
            }
        }
    }
}


@Preview(showBackground = true, name = "Tabs — fit (stretched)")
@Composable
private fun ReplanHistoryBarFitPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                previewIteration(0, "21:30", RunKind.ScheduledBase),
                previewIteration(1, "23:10", RunKind.Replan(TriggerType.CalendarChange)),
            ),
            position = { 1f },
            selectedTurn = 1,
            dragging = false,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

@Preview(showBackground = true, name = "Tabs — overflow (scroll), mid-swipe")
@Composable
private fun ReplanHistoryBarOverflowPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                previewIteration(0, "21:30", RunKind.ScheduledBase),
                previewIteration(1, "23:10", RunKind.Replan(TriggerType.CalendarChange)),
                previewIteration(2, "02:10", RunKind.Replan(TriggerType.WakeWindowOpportunity)),
            ),
            position = { 1.5f }, // mid-swipe between tabs 1 and 2 → both partially morphed
            selectedTurn = 2,
            dragging = true, // show the raw mid-swipe morph (dragging tracks the pager 1:1)
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

private fun previewIteration(turn: Int, time: String, kind: RunKind, streaming: Boolean = false, isMocked: Boolean = false) = AiIterationUi(
    turnIndex = turn,
    timeLabel = time,
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = kind.label, process = emptyList(), response = null, isStreaming = streaming, isMocked = isMocked),
)

@Preview(showBackground = true, name = "Tabs — streaming (loader)")
@Composable
private fun ReplanHistoryBarStreamingPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                previewIteration(0, "21:30", RunKind.ScheduledBase),
                previewIteration(1, "23:10", RunKind.Replan(TriggerType.EveningPlan), streaming = true),
            ),
            position = { 1f },
            selectedTurn = 1,
            dragging = false,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

@Preview(showBackground = true, name = "Tabs — mocked selected")
@Composable
private fun ReplanHistoryBarMockedSelectedPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                previewIteration(0, "21:30", RunKind.ScheduledBase),
                previewIteration(1, "23:10", RunKind.Replan(TriggerType.CalendarChange), isMocked = true),
            ),
            position = { 1f },
            selectedTurn = 1,
            dragging = false,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

@Preview(showBackground = true, name = "Tabs — mocked unselected")
@Composable
private fun ReplanHistoryBarMockedUnselectedPreview() {
    CronTheme {
        ReplanHistoryBar(
            iterations = listOf(
                previewIteration(0, "21:30", RunKind.ScheduledBase),
                previewIteration(1, "23:10", RunKind.Replan(TriggerType.CalendarChange), isMocked = true),
            ),
            position = { 0f },
            selectedTurn = 0,
            dragging = false,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}
