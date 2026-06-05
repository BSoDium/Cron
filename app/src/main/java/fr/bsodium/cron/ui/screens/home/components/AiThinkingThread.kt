package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlin.math.roundToInt

private val ROW_MIN_HEIGHT = 48.dp
internal val SPINNER_SIZE = 14.dp
internal val SPINNER_STROKE = 1.5.dp

// Soft edge on the partially-revealed timeline while peeking open via the pull gesture.
private val PEEK_FADE_HEIGHT = 24.dp

/**
 * Latest-turn AI thread:
 *
 *   ( summary line  ⌄ )                               (pill: lighter bg when open)
 *   🔍 reasoning / narration (sans, markdown)         ← whole process collapses
 *   🔧 Calling [read_calendar]            12 events      inside the disclosure;
 *   🔧 Calling [set_alarm]              set for 09:30     rule emerges from pill
 *   ✓  Done
 *   {serif response body, full markdown}              ← final answer, always shown
 */
@Composable
fun AiThinkingThread(
    thread: AiThreadUi,
    isRunning: Boolean,
    modifier: Modifier = Modifier,
    // Controlled/uncontrolled: when [expanded] is null the disclosure manages its own state (previews,
    // tests); HomeScreen hoists it so the pull gesture can drive it. [expandPx] peeks the timeline open
    // by an absolute pixel height (1:1 with the drag); [onFullHeight] reports its measured full height.
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    expandPx: Float = 0f,
    onFullHeight: (Int) -> Unit = {},
    // 0f = collapsed (chevron pointing right), 1f = fully open (chevron pointing down). Drives the
    // pivot animation; tracked to the live reveal value so the chevron rotates with the drag.
    expansionFraction: Float = 0f,
) {
    var internalExpanded by rememberSaveable(thread.turnIndex) { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    Column(modifier = modifier.fillMaxWidth()) {
        // Settled/running is the WorkManager signal, not response presence — a do_nothing turn settles with none.
        val inProgress = isRunning
        if (thread.process.isNotEmpty() || inProgress) {
            ThinkingDisclosure(
                summary = thread.summary,
                process = thread.process,
                inProgress = inProgress,
                durationSeconds = thread.durationSeconds,
                expanded = isExpanded,
                onToggle = {
                    val next = !isExpanded
                    if (onExpandedChange != null) onExpandedChange(next) else internalExpanded = next
                },
                expandPx = expandPx,
                onFullHeight = onFullHeight,
                expansionFraction = expansionFraction,
            )
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            ResponseBody(thread.response)
        }
        val phase = when {
            !inProgress -> ShapePhase.Resting
            thread.response.isNullOrBlank() -> ShapePhase.Thinking
            else -> ShapePhase.Writing
        }
        ThinkingShape(phase = phase, modifier = Modifier.padding(top = Spacing.md))
    }
}

/**
 * Draws content [bleed] wider on each side than its slot — overflowing the parent's horizontal
 * content padding out to the screen edges — while still reporting the slot width to the parent
 * so siblings are unaffected. Compensate with extra start/end padding to keep inner content put.
 */
private fun Modifier.bleedHorizontally(bleed: Dp): Modifier = layout { measurable, constraints ->
    // Only meaningful under a bounded-width parent (our LazyColumn item); if width is unbounded
    // there's nothing to bleed into, so measure and place normally.
    if (constraints.maxWidth == Constraints.Infinity) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val bleedPx = bleed.roundToPx()
    val placeable = measurable.measure(constraints.copy(maxWidth = constraints.maxWidth + bleedPx * 2))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleedPx, 0) }
}

@Composable
private fun ThinkingDisclosure(
    summary: String?,
    process: List<ProcessItem>,
    inProgress: Boolean,
    durationSeconds: Int?,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandPx: Float = 0f,
    onFullHeight: (Int) -> Unit = {},
    expansionFraction: Float = 0f,
) {
    val canExpand = process.isNotEmpty()
    // Drives the chevron pivot from the live reveal: 0f collapsed (points right), 1f open (points down).
    val openFraction = if (expanded) 1f else expansionFraction.coerceIn(0f, 1f)
    Column(modifier = Modifier.fillMaxWidth()) {
        // The header (and its tap ripple) bleeds past the list's content padding to span the full
        // screen width; start/end padding re-insets the summary, avatars, and chevron to the margin.
        Row(
            modifier = Modifier
                .bleedHorizontally(Spacing.xl)
                .fillMaxWidth()
                .let { if (canExpand) it.clickable { onToggle() } else it }
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(start = Spacing.xl, top = Spacing.sm, end = Spacing.xl, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            if (inProgress) {
                // Live: the model's current gerund summary + a spinner.
                Text(
                    text = summary ?: "Thinking…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(SPINNER_SIZE),
                    strokeWidth = SPINNER_STROKE,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // Settled: an avatar-stack of the tools that ran + how long it took.
                val tools = process.filterIsInstance<ProcessItem.Tool>()
                if (tools.isNotEmpty()) ToolStack(tools)
                Text(
                    text = thoughtForLabel(durationSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canExpand) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { rotationZ = openFraction * 90f },
                    )
                }
            }
        }
        // Single pixel-accurate reveal: the pull drives expandPx 1:1 with the finger; tap/release animate
        // the same value (owned by HomeScreen), so there's no second source to dip against. Fully expanded
        // clips to Float.MAX_VALUE → the natural height, re-measured so a still-streaming timeline isn't
        // cut. Settled turns always compose (at h=0 when collapsed) so the full height is ready for tap/pull.
        val revealing = expanded || expandPx > 0f || !inProgress
        if (canExpand && revealing) {
            ExpandReveal(
                targetPx = if (expanded) Float.MAX_VALUE else expandPx,
                peeking = !expanded,
                onFullHeight = onFullHeight,
            ) {
                TimelineColumn {
                    process.forEachIndexed { i, item ->
                        val isFirst = i == 0
                        val isLast = inProgress && i == process.lastIndex
                        when (item) {
                            is ProcessItem.Reasoning -> ProcessTextRow(item.text, isFirst, isLast)
                            is ProcessItem.Narration -> ProcessTextRow(item.text, isFirst, isLast)
                            is ProcessItem.Tool -> ToolStepRow(item, isFirst, isLast)
                        }
                    }
                    if (!inProgress) DoneRow(isFirst = process.isEmpty(), isLast = true)
                }
            }
        }
    }
}

/** Measures [content] at full height, reports that via [onFullHeight], and clips it top-anchored to
 *  [targetPx] pixels (capped at full) — so a pull maps 1:1 to revealed pixels. [peeking] adds a soft
 *  bottom edge while partially open. */
@Composable
private fun ExpandReveal(
    targetPx: Float,
    peeking: Boolean,
    onFullHeight: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    SubcomposeLayout(
        modifier = Modifier
            .clipToBounds()
            .then(if (peeking) Modifier.fadeBottom(PEEK_FADE_HEIGHT) else Modifier),
    ) { constraints ->
        val placeable = subcompose(Unit, content).first().measure(constraints.copy(minHeight = 0))
        onFullHeight(placeable.height)
        val h = targetPx.coerceIn(0f, placeable.height.toFloat()).roundToInt()
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
}

private fun thoughtForLabel(durationSeconds: Int?): String = when {
    durationSeconds == null || durationSeconds < 1 -> "Thought for a moment"
    else -> "Thought for ${durationSeconds}s"
}

private val TOOL_DISC_SIZE = 26.dp
private val TOOL_DISC_RING = 1.5.dp
private val TOOL_DISC_ICON = 13.dp
private val TOOL_STACK_OVERLAP = 8.dp

/**
 * Avatar-stack of the tools that ran this turn. Each icon sits in a page-coloured ring, so where
 * the discs overlap (negative spacing) the later one occludes the earlier with a clean gap.
 */
@Composable
private fun ToolStack(tools: List<ProcessItem.Tool>) {
    Row(horizontalArrangement = Arrangement.spacedBy(-TOOL_STACK_OVERLAP)) {
        tools.forEach { tool ->
            Box(
                modifier = Modifier
                    .size(TOOL_DISC_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(TOOL_DISC_SIZE - TOOL_DISC_RING * 2)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = toolIcon(tool.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(TOOL_DISC_ICON),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResponseBody(text: String) {
    MarkdownBlock(
        text = text,
        bodyStyle = CronTypography.bodySerif.copy(color = MaterialTheme.colorScheme.onSurface),
        serif = true,
    )
}

/** Representative thread for previews: a long (collapsible) reasoning block, a few tool steps, and a
 *  markdown response with bold + inline code. */
private val PREVIEW_THREAD = AiThreadUi(
    turnIndex = 0,
    summary = "Setting your alarm",
    process = listOf(
        ProcessItem.Reasoning(
            "Let me read the calendar for the next 24-30 hours and find the first event you must be " +
                "ready for. All-day markers like **Office** or a city set the day's working location; a " +
                "virtual `#stand-up` is a real anchor with no commute. I subtract the travel buffer and " +
                "`preparation_time` from the anchor, then nudge into a light-sleep window.",
        ),
        ProcessItem.Tool(name = "read_calendar", isComplete = true, contextLabel = "6 events"),
        ProcessItem.Tool(name = "estimate_commute_multi_mode", isComplete = true, contextLabel = "13 min"),
        ProcessItem.Tool(name = "set_alarm", isComplete = true, contextLabel = "set for 06:40"),
    ),
    response = "Set a **6:40** alarm so you make your 9:00 stand-up.\n\n" +
        "Your first anchor is at the office, about a 25 min drive. I took the commute plus 45 min of " +
        "`preparation_time` off the start, then landed on a light-sleep moment just before.",
    durationSeconds = 15,
)

@Preview(showBackground = true, name = "Thread — settled")
@Composable
private fun AiThinkingThreadPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiThinkingThread(
                thread = PREVIEW_THREAD,
                isRunning = false,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    }
}

@Preview(showBackground = true, name = "Disclosure — expanded")
@Composable
private fun ThinkingDisclosureExpandedPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.xl)) {
                ThinkingDisclosure(
                    summary = PREVIEW_THREAD.summary,
                    process = PREVIEW_THREAD.process,
                    inProgress = false,
                    durationSeconds = PREVIEW_THREAD.durationSeconds,
                    expanded = true,
                    onToggle = {},
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Thread — running")
@Composable
private fun AiThinkingThreadRunningPreview() {
    CronTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            AiThinkingThread(
                thread = AiThreadUi(
                    turnIndex = 0,
                    summary = "Reading your calendar",
                    process = listOf(ProcessItem.Tool(name = "read_calendar", isComplete = false)),
                    response = null,
                ),
                isRunning = true,
                modifier = Modifier.padding(Spacing.xl),
            )
        }
    }
}
