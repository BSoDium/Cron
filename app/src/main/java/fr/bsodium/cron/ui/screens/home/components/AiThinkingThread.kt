package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.outlined.KeyboardArrowDown
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
import androidx.compose.ui.graphics.Color
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
import kotlin.math.max
import kotlin.math.roundToInt

private val ROW_MIN_HEIGHT = 48.dp
internal val SPINNER_SIZE = 14.dp
internal val SPINNER_STROKE = 1.5.dp

// Tap/snap expand animation for the thinking timeline; the pull gesture overrides it manually.
private val DISCLOSURE_EXPAND_SPEC = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
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
    // tests); HomeScreen hoists it so the pull gesture can drive it. [expandFraction] peeks it open.
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    expandFraction: Float = 0f,
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
                expandFraction = expandFraction,
            )
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            ResponseBody(thread.response)
        }
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
    expandFraction: Float = 0f,
) {
    val canExpand = process.isNotEmpty()
    // Full-bleed bar: transparent collapsed, a quiet fill when open. It bleeds past the side content
    // padding (Spacing.xl) to hug both edges; compensating start/end padding keeps the summary text and chevron in place.
    Row(
        modifier = Modifier
            .bleedHorizontally(Spacing.xl)
            .fillMaxWidth()
            .let { if (canExpand) it.clickable { onToggle() } else it }
            .background(if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
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
                    imageVector = if (expanded) Icons.Outlined.KeyboardArrowDown else Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    // The timeline reveals to max(tap/snap animation, live pull fraction) so a drag peeks it open
    // continuously and a tap (or snap past the pull threshold) animates it the rest of the way.
    val expandedFraction by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = DISCLOSURE_EXPAND_SPEC,
        label = "disclosure-expand",
    )
    val revealFraction = max(expandedFraction, expandFraction)
    if (canExpand && revealFraction > 0f) {
        ExpandReveal(fraction = revealFraction) {
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

/** Renders [content] at full height but clips it top-anchored to [fraction] of that height, with a soft
 *  bottom edge while partially revealed — the continuous unwrap driven by tap-snap or the pull gesture. */
@Composable
private fun ExpandReveal(fraction: Float, content: @Composable () -> Unit) {
    val peeking = fraction < 1f
    SubcomposeLayout(
        modifier = Modifier
            .clipToBounds()
            .then(if (peeking) Modifier.fadeBottom(PEEK_FADE_HEIGHT) else Modifier),
    ) { constraints ->
        val placeable = subcompose(Unit, content).first().measure(constraints.copy(minHeight = 0))
        val h = (placeable.height * fraction).roundToInt().coerceIn(0, placeable.height)
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
