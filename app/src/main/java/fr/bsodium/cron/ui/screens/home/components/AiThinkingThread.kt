@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateBounds
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.bleedHorizontally
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlin.math.roundToInt

private val ROW_MIN_HEIGHT = 48.dp

// Soft edge on the partially-revealed timeline while peeking open via the pull gesture.
private val PEEK_FADE_HEIGHT = 24.dp

/**
 * One turn's AI thread:
 *
 *   ◔◑ summary line  ⌄                                 ← tool-disc stack + header; tap/pull
 *   🔍 reasoning / narration (sans, markdown)             reveals the process timeline
 *   🔧 Calling [read_calendar]            12 events
 *   ✓  Done
 *   {serif response body, full markdown}               ← final answer, always shown
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
    // The morphing shape represents where the assistant currently "is" — only meaningful on the latest
    // iteration; older (settled) tabs end on their response.
    showShape: Boolean = true,
    // For an older (non-latest) tab: when it ran, and a jump-back-to-latest action shown in its footer.
    ranAtEpochMs: Long? = null,
    onJumpToLatest: (() -> Unit)? = null,
) {
    var internalExpanded by rememberSaveable(thread.turnIndex) { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    Column(modifier = modifier.fillMaxWidth()) {
        // Settled/running is the WorkManager signal, not response presence — a do_nothing turn settles with none.
        val inProgress = isRunning
        // Loader only while genuinely thinking — it stops the instant the answer starts streaming.
        val thinking = inProgress && thread.response.isNullOrBlank()
        // Always present, even with no tools/reasoning, so the block settles into a "Thought for Xs" header
        // instead of vanishing the moment the response lands (canExpand still gates the chevron/reveal).
        ThinkingDisclosure(
            summary = thread.summary,
            process = thread.process,
            inProgress = inProgress,
            pending = thinking,
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
        // Alpha-only in AND out: any size-changing transition (e.g. shrinkVertically) makes
        // AnimatedVisibility clipToBounds the block, which clips the streaming markdown to its
        // first-frame (too-small) height as the answer appears. Pure fades never clip. Hold the last
        // answer so the exit renders real content while it fades.
        // The response and the "no response" note share one crossfading slot (see [AnswerArea]).
        AnswerArea(
            response = thread.response,
            inProgress = inProgress,
            hasProcess = thread.process.isNotEmpty(),
        )
        if (showShape) {
            val phase = when {
                !inProgress -> ShapePhase.Resting
                thread.response.isNullOrBlank() -> ShapePhase.Thinking
                else -> ShapePhase.Writing
            }
            Row(
                // Start inset centres the shape under the header's first tool disc: (disc 26 − shape 18)/2.
                modifier = Modifier.padding(start = Spacing.xs, top = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                ThinkingShape(phase = phase, restKey = thread.turnIndex)
                // The shape itself is the down-arrow cue while thinking; this label sits beside it and fades
                // out as the timeline is pulled open.
                if (phase == ShapePhase.Thinking) {
                    Text(
                        text = "Pull down to show thinking",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { alpha = (1f - expansionFraction).coerceIn(0f, 1f) },
                    )
                }
            }
        } else if (onJumpToLatest != null) {
            // Older tabs hide the shape; show how long ago this ran + a tap back to the latest plan.
            OldPlanFooter(ranAtEpochMs = ranAtEpochMs, onJumpToLatest = onJumpToLatest)
        }
    }
}

@Composable
private fun ThinkingDisclosure(
    summary: String?,
    process: List<ProcessItem>,
    inProgress: Boolean,
    pending: Boolean,
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
            // The tools called so far + a trailing pending loader disc; with no tools, a fallback assistant
            // disc (inside ToolStack) so the header always leads with an icon, never floating text.
            val tools = process.filterIsInstance<ProcessItem.Tool>()
            ToolStack(tools, pending = pending)
            Text(
                text = if (inProgress) (summary ?: "Thinking…") else thoughtForLabel(durationSeconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canExpand) {
                Symbol(
                    symbol = MaterialSymbol.KeyboardArrowRight,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    autoMirror = true,
                    modifier = Modifier.graphicsLayer { rotationZ = openFraction * 90f },
                )
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

private val TOOL_DISC_LOADER = 18.dp

/**
 * Avatar-stack of the tools that ran this turn. Each icon sits in a page-coloured ring, so where the
 * discs overlap (negative spacing) the later one occludes the earlier with a clean gap. While [pending], a
 * trailing loader disc leads the stack; as real tools land they fade in and the loader slides right
 * ([animateBounds] inside a [LookaheadScope] animates each disc's position/size).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ToolStack(tools: List<ProcessItem.Tool>, pending: Boolean = false) {
    // Tools present when the stack first composes don't individually animate (its appearance is enough);
    // any that arrive later fade in.
    val seen = remember { tools.size }
    LookaheadScope {
        Row(horizontalArrangement = Arrangement.spacedBy(-TOOL_STACK_OVERLAP)) {
            tools.forEachIndexed { index, tool ->
                ToolDisc(modifier = Modifier.animateBounds(this@LookaheadScope), isNew = index >= seen) {
                    Symbol(
                        symbol = toolSymbol(tool.name),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = TOOL_DISC_ICON,
                    )
                }
            }
            if (pending) {
                ToolDisc(modifier = Modifier.animateBounds(this@LookaheadScope)) {
                    LoadingIndicator(
                        modifier = Modifier.size(TOOL_DISC_LOADER),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (tools.isEmpty()) {
                // No tools and not pending (a no-tool turn): a fallback assistant icon so the header always
                // leads with a disc instead of floating text.
                ToolDisc(modifier = Modifier.animateBounds(this@LookaheadScope)) {
                    Symbol(
                        symbol = MaterialSymbol.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        size = TOOL_DISC_ICON,
                    )
                }
            }
        }
    }
}

/** One disc in the [ToolStack]: a page-coloured ring around a content disc; fades in once when [isNew]. */
@Composable
private fun ToolDisc(modifier: Modifier = Modifier, isNew: Boolean = false, content: @Composable () -> Unit) {
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    if (isNew) LaunchedEffect(Unit) { enter.animateTo(1f, spec) }
    Box(
        modifier = modifier
            .graphicsLayer { alpha = enter.value }
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
        ) { content() }
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

/**
 * The answer area below the timeline. The markdown response and the "no response" fallback share ONE
 * overlapping slot and crossfade (`Box` + `animateContentSize`), so switching to a no-response iteration
 * doesn't pop the fallback in stacked under the still-fading old answer and then jump when it unmounts.
 * The fallback matches the serif response size. Lives in its own composable so the two `AnimatedVisibility`
 * calls resolve to the top-level (Box) overload rather than the enclosing `ColumnScope` one.
 */
@Composable
private fun AnswerArea(
    response: String?,
    inProgress: Boolean,
    hasProcess: Boolean,
    modifier: Modifier = Modifier,
) {
    var lastResponse by remember { mutableStateOf("") }
    LaunchedEffect(response) { if (!response.isNullOrBlank()) lastResponse = response }
    val hasAnswer = !response.isNullOrBlank()
    val showFallback = !inProgress && !hasAnswer && hasProcess
    // Animate the slot size only when settled (the answer↔fallback crossfade). While streaming, the
    // typewriter outpaces the spring, so a lagging container clips the freshest lines — grow instantly.
    Box(modifier = modifier.fillMaxWidth().then(if (inProgress) Modifier else Modifier.animateContentSize())) {
        AnimatedVisibility(visible = hasAnswer, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                ResponseBody(response?.takeIf { it.isNotBlank() } ?: lastResponse)
                // Descender clearance: the serif body's last line extends below its measured line box, so
                // without this the slot/pager (sized to the measured height) clips it.
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        AnimatedVisibility(visible = showFallback, enter = fadeIn(), exit = fadeOut()) {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Cron didn't write a response.",
                    style = CronTypography.bodySerif.copy(fontStyle = FontStyle.Italic),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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
                    pending = false,
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
