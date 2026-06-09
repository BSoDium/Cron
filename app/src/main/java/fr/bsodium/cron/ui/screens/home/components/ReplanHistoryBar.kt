@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.components.CronHaptics
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.screens.home.AiIterationUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.screens.home.label
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.roundToInt

private val TAB_MIN_HEIGHT = 48.dp
private val ICON_GLYPH = 22.dp
private val TAB_LOADER = 32.dp

/** Connected-tab corners: unselected inner corners rest at the M3 default (CornerValueSmall = 8.dp),
 *  morphing to a full pill when selected; the end tabs keep their pill OUTER corner in both states. */
private val TAB_INNER_CORNER = CornerSize(8.dp)
private val TAB_PILL_CORNER = CornerSize(50)

/** Frame cap for the auto-scroll's wait-for-scroll-range-to-settle loop (animateContentSize widens the
 *  strip over several frames after a tab is added); bounds the wait when the range never reaches the target. */
private const val MAX_SETTLE_FRAMES = 30

/** Scroll offset that centres [span] (a tab's left..right x in row coords) in the viewport, clamped to
 *  the scroll range so end tabs rest aligned with the card. */
private fun centreTarget(span: IntRange, padPx: Int, viewportPx: Int, maxScroll: Int): Int {
    val tabCentre = (span.first + span.last) / 2 + padPx
    return (tabCentre - viewportPx / 2).coerceIn(0, maxScroll)
}

/** Connected-tab shape, lerped from a rounded rectangle (unselected) to a full pill (selected) by
 *  [fraction]; the outer corner of an end tab stays pill-round in both states. */
private fun tabShape(index: Int, lastIndex: Int, fraction: Float): RoundedCornerShape {
    val inner = innerCorner(fraction)
    val start = if (index == 0) TAB_PILL_CORNER else inner
    val end = if (index == lastIndex) TAB_PILL_CORNER else inner
    return RoundedCornerShape(topStart = start, topEnd = end, bottomEnd = end, bottomStart = start)
}

/** A tab's inner corner, morphed from the rounded-rect default (dp) to a full pill (percent). The
 *  mixed dp/percent units only resolve at draw time, so the lerp lives in [CornerSize.toPx]. */
private fun innerCorner(fraction: Float): CornerSize = when (fraction) {
    0f -> TAB_INNER_CORNER
    1f -> TAB_PILL_CORNER
    else -> LerpCornerSize(TAB_INNER_CORNER, TAB_PILL_CORNER, fraction)
}

private class LerpCornerSize(val start: CornerSize, val stop: CornerSize, val t: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density): Float {
        val a = start.toPx(shapeSize, density)
        val b = stop.toPx(shapeSize, density)
        return a + (b - a) * t
    }
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
private fun rememberTabFraction(index: Int, turnIndex: Int, position: Float, selectedTurn: Int, dragging: Boolean): Float {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val raw = rememberUpdatedState(selectedFraction(index, position))
    val target = if (turnIndex == selectedTurn) 1f else 0f
    val fraction = remember { Animatable(target) }
    LaunchedEffect(dragging, target) {
        if (dragging) snapshotFlow { raw.value }.collect { fraction.snapTo(it) }
        else fraction.animateTo(target, spec)
    }
    return fraction.value
}

/** Quintic "gain" easing — slope 5 at the centre, flat at the ends. Applied to the COLOUR inversion only:
 *  a tab keeps its colour for most of the swipe and flips fast across the low-contrast crossover, so the
 *  label barely lingers unreadable. The shape morph stays linear. */
private fun fastMiddle(x: Float): Float {
    val t = x.coerceIn(0f, 1f)
    return if (t < 0.5f) 16f * t * t * t * t * t else 1f - 16f * pow5(1f - t)
}

private fun pow5(v: Float): Float = v * v * v * v * v

/**
 * The planning-iteration strip: connected tabs that **morph continuously** with the thread pager —
 * [position] is the pager's fractional position (`currentPage + offsetFraction`), so as the user swipes
 * the active tab fades from pill→rounded and the incoming tab fades rounded→pill (colours + corner radii
 * interpolating). Stretches to fill when the tabs fit, scrolls (with tap-to-centre) when they overflow.
 */
@Composable
internal fun ReplanHistoryBar(
    iterations: List<AiIterationUi>,
    position: Float,
    selectedTurn: Int,
    dragging: Boolean,
    onSelect: (Int) -> Unit,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberCronHaptics(hapticsEnabled)
    // turnIndices present when the strip first composed; any tab added later animates itself in.
    val initialTurns = remember { iterations.map { it.turnIndex }.toSet() }
    SubcomposeLayout(modifier.fillMaxWidth()) { constraints ->
        val natural = subcompose("probe") { ProbeTabs(iterations) }.first().measure(Constraints())
        val fits = natural.width <= constraints.maxWidth
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
        iterations.forEachIndexed { index, iteration ->
            ReplanTab(
                iteration = iteration,
                fraction = 0f,
                isLatest = index == iterations.lastIndex && iterations.size > 1,
                shape = tabShape(index, iterations.lastIndex, 0f),
                onClick = {},
            )
        }
    }
}

/** Tabs fill the row — used when they fit; no scroll, no centring. Each tab is weighted by its NATURAL width
 *  (not equal), so a long title never shrinks below its content and clips: since we're only here when the tabs
 *  fit, proportional distribution gives every tab ≥ its natural width while still filling the row. */
@Composable
private fun StretchedTabs(
    iterations: List<AiIterationUi>,
    position: Float,
    selectedTurn: Int,
    dragging: Boolean,
    initialTurns: Set<Int>,
    onSelect: (Int) -> Unit,
    haptics: CronHaptics,
) {
    SubcomposeLayout(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.md)
            .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
    ) { constraints ->
        val natWidths = subcompose("probe") {
            iterations.forEachIndexed { index, iteration ->
                ReplanTab(
                    iteration = iteration,
                    fraction = 0f,
                    isLatest = index == iterations.lastIndex && iterations.size > 1,
                    shape = tabShape(index, iterations.lastIndex, 0f),
                    onClick = {},
                )
            }
        }.map { it.measure(Constraints()).width }
        val content = subcompose("content") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                iterations.forEachIndexed { index, iteration ->
                    val fraction = rememberTabFraction(index, iteration.turnIndex, position, selectedTurn, dragging)
                    ReplanTab(
                        iteration = iteration,
                        fraction = fraction,
                        isLatest = index == iterations.lastIndex && iterations.size > 1,
                        shape = tabShape(index, iterations.lastIndex, fraction),
                        isNew = iteration.turnIndex !in initialTurns,
                        modifier = Modifier.weight(natWidths.getOrElse(index) { 1 }.toFloat().coerceAtLeast(1f)),
                        onClick = { if (iteration.turnIndex != selectedTurn) { haptics.tick(); onSelect(iteration.turnIndex) } },
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
    position: Float,
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
    val activeTurn = iterations[position.roundToInt().coerceIn(0, iterations.lastIndex)].turnIndex

    // Centre the active tab (keyed on the rounded position, so it tracks the swipe at each boundary).
    LaunchedEffect(activeTurn, viewportPx) {
        if (viewportPx <= 0) return@LaunchedEffect
        // Await the (possibly just-added) tab's measured span. A freshly-added tab widens the content via
        // animateContentSize, so the scroll range (maxValue) ramps up over several frames; clamping
        // centreTarget to a mid-ramp maxValue leaves the new tab clipped. Wait until the range is wide
        // enough to reach the desired (unclamped) centre — capped so a full strip can't hang us.
        val span = snapshotFlow { spans[activeTurn] }.filterNotNull().first()
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
                val fraction = rememberTabFraction(index, iteration.turnIndex, position, selectedTurn, dragging)
                ReplanTab(
                    iteration = iteration,
                    fraction = fraction,
                    isLatest = index == iterations.lastIndex && iterations.size > 1,
                    shape = tabShape(index, iterations.lastIndex, fraction),
                    isNew = iteration.turnIndex !in initialTurns,
                    modifier = Modifier.onPlaced { coords ->
                        val b = coords.boundsInParent()
                        val range = b.left.roundToInt()..b.right.roundToInt()
                        // Structural equality guard: skip the snapshot write (and invalidation) when unmoved.
                        if (spans[iteration.turnIndex] != range) spans[iteration.turnIndex] = range
                    },
                    onClick = { if (iteration.turnIndex != selectedTurn) { haptics.tick(); onSelect(iteration.turnIndex) } },
                )
            }
        }
    }
}

@Composable
private fun ReplanTab(
    iteration: AiIterationUi,
    fraction: Float, // 0 = unselected, 1 = selected; interpolates colour + corner radius
    isLatest: Boolean,
    shape: RoundedCornerShape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isNew: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    // The latest run wears the primary accent; older iterations stay quiet (secondary).
    val accent = isLatest
    val unselectedContainer = if (accent) scheme.primaryContainer else scheme.secondaryContainer
    val selectedContainer = if (accent) scheme.primary else scheme.secondary
    val onUnselected = if (accent) scheme.onPrimaryContainer else scheme.onSecondaryContainer
    val onSelected = if (accent) scheme.onPrimary else scheme.onSecondary
    // Colour inverts fast through the middle (quintic) so the label doesn't dwell at the muddy midpoint;
    // the shape morph (in `shape`) stays linear with the swipe.
    val colorFraction = fastMiddle(fraction)
    val content = lerp(onUnselected, onSelected, colorFraction)
    // A tab added after the strip first appeared fades + scales in (once); the initial set starts settled.
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    val enterSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    if (isNew) LaunchedEffect(Unit) { enter.animateTo(1f, enterSpec) }
    Surface(
        onClick = onClick,
        modifier = modifier
            .graphicsLayer {
                alpha = enter.value
                val s = 0.85f + 0.15f * enter.value
                scaleX = s
                scaleY = s
            }
            .heightIn(min = TAB_MIN_HEIGHT),
        shape = shape,
        color = lerp(unselectedContainer, selectedContainer, colorFraction),
        contentColor = content,
    ) {
        val streaming = iteration.thread.isStreaming
        Row(
            // Tight, even leading inset in both states so neither the loader nor the icon floats with a left gap.
            modifier = Modifier.padding(start = Spacing.sm, top = Spacing.sm, end = Spacing.xl, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A still-streaming turn shows the contained Expressive loader nested in the pill; a settled
            // one shows its trigger icon. Fixed slot so the text doesn't shift when one swaps for the other.
            Box(modifier = Modifier.size(TAB_LOADER), contentAlignment = Alignment.Center) {
                if (streaming) {
                    // Colours derive from `content` (dark on an unselected tab, light on the selected one),
                    // so the loader auto-inverts with the tab and the container stays a subtle nested disc.
                    ContainedLoadingIndicator(
                        modifier = Modifier.fillMaxSize(),
                        containerShape = CircleShape,
                        containerColor = content.copy(alpha = 0.2f),
                        indicatorColor = content,
                    )
                } else {
                    Symbol(symbol = runSymbol(iteration.kind), contentDescription = null, tint = content, size = ICON_GLYPH)
                }
            }
            Spacer(Modifier.width(Spacing.xs))
            Column {
                // Clip (not Ellipsis): single-line labels sized to the tab read cleaner hard-clipped than with "…".
                Text(
                    text = iteration.systemMessage,
                    style = MaterialTheme.typography.labelLarge,
                    color = content,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
                Text(
                    text = if (isLatest) "Latest · ${iteration.timeLabel}" else iteration.timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = content.copy(alpha = 0.75f),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
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
            position = 1f,
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
            position = 1.5f, // mid-swipe between tabs 1 and 2 → both partially morphed
            selectedTurn = 2,
            dragging = true, // show the raw mid-swipe morph (dragging tracks the pager 1:1)
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}

private fun previewIteration(turn: Int, time: String, kind: RunKind, streaming: Boolean = false) = AiIterationUi(
    turnIndex = turn,
    timeLabel = time,
    kind = kind,
    thread = AiThreadUi(turnIndex = turn, summary = kind.label, process = emptyList(), response = null, isStreaming = streaming),
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
            position = 1f,
            selectedTurn = 1,
            dragging = false,
            onSelect = {},
            hapticsEnabled = false,
        )
    }
}
