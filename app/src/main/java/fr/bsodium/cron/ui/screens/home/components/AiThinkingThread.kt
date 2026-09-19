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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.bleedHorizontally
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlin.math.roundToInt

private val ROW_MIN_HEIGHT = 48.dp

// Soft edge on the partially-revealed timeline while peeking open via the pull gesture.
private val PEEK_FADE_HEIGHT = 24.dp

/** Which iteration a thread renders: the latest carries the live shape; an older one ends on
 *  a footer linking back to the latest. */
sealed interface ThreadRole {
    data object Latest : ThreadRole
    data class Older(val ranAtEpochMs: Long?, val onJumpToLatest: () -> Unit) : ThreadRole
}

/**
 * Renders one turn's AI thread: header disclosure, answer body, and (for the latest turn) the
 * live thinking shape.
 *
 * [expanded]/[onExpandedChange] make disclosure controlled/uncontrolled: when [expanded] is null
 * the disclosure manages its own state; HomeScreen hoists it so the pull gesture
 * can drive it. [expandPx] peeks the timeline open by an absolute pixel height;
 * [onFullHeight] reports its measured full height.
 */
@Composable
fun AiThinkingThread(
    thread: AiThreadUi,
    modifier: Modifier = Modifier,
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    expandPx: () -> Float = { 0f },
    onFullHeight: (Int) -> Unit = {},
    expansionFraction: () -> Float = { 0f },
    role: ThreadRole = ThreadRole.Latest,
) {
    // Default to true while thinking, auto-collapse once response is present
    var internalExpanded by rememberSaveable(thread.turnIndex) {
        mutableStateOf(thread.response.isNullOrBlank())
    }

    // Auto-collapse internal state when response arrives
    LaunchedEffect(thread.response.isNullOrBlank()) {
        if (!thread.response.isNullOrBlank()) {
            internalExpanded = false
        }
    }

    val isExpanded = expanded ?: internalExpanded
    Column(modifier = modifier.fillMaxWidth()) {
        val inProgress = thread.isStreaming
        val thinking = inProgress && thread.response.isNullOrBlank()

        ThinkingDisclosure(
            summary = thread.summary,
            process = thread.process,
            inProgress = inProgress,
            pending = thinking,
            durationSeconds = thread.durationSeconds,
            isMocked = thread.isMocked,
            expanded = isExpanded,
            onToggle = {
                val next = !isExpanded
                if (onExpandedChange != null) onExpandedChange(next) else internalExpanded = next
            },
            expandPx = expandPx,
            onFullHeight = onFullHeight,
            expansionFraction = expansionFraction,
        )

        AnswerArea(
            response = thread.response,
            inProgress = inProgress,
            hasProcess = thread.process.isNotEmpty(),
        )

        if (role is ThreadRole.Latest) {
            val phase = when {
                !inProgress -> ShapePhase.Resting
                thread.response.isNullOrBlank() -> ShapePhase.Thinking
                else -> ShapePhase.Writing
            }

            // Hide the shape during Thinking phase if the process timeline is expanded or partially open
            val isOpen = isExpanded || expansionFraction() > 0f || expandPx() > 0f
            val showShape = phase != ShapePhase.Thinking || !isOpen

            if (showShape) {
                Row(
                    modifier = Modifier.padding(top = Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    ThinkingShape(phase = phase, restKey = thread.turnIndex)

                    // Pull cue text is shown next to the shape only during collapsed thinking phase
                    if (phase == ShapePhase.Thinking) {
                        Text(
                            text = "Pull down to show thinking",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer {
                                alpha = (1f - expansionFraction()).coerceIn(0f, 1f)
                            },
                        )
                    }
                }
            }
        } else if (role is ThreadRole.Older) {
            OldPlanFooter(ranAtEpochMs = role.ranAtEpochMs, onJumpToLatest = role.onJumpToLatest)
        }
    }
}

@Composable
internal fun ThinkingDisclosure(
    summary: String?,
    process: List<ProcessItem>,
    inProgress: Boolean,
    pending: Boolean,
    durationSeconds: Int?,
    isMocked: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    expandPx: () -> Float = { 0f },
    onFullHeight: (Int) -> Unit = {},
    expansionFraction: () -> Float = { 0f },
) {
    val canExpand = process.isNotEmpty()
    val openFraction = { if (expanded) 1f else expansionFraction().coerceIn(0f, 1f) }

    Column(modifier = Modifier.fillMaxWidth()) {
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
            val tools = process.filterIsInstance<ProcessItem.Tool>()
            ToolStack(tools, pending = pending)
            val headerColor = MaterialTheme.colorScheme.onSurfaceVariant

            if (!inProgress && isMocked) {
                val verb = "Faked thinking"
                val full = thoughtForLabel(durationSeconds, isMocked = true)
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)) {
                            append(verb)
                        }
                        append(full.removePrefix(verb))
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = if (inProgress) (summary ?: "Thinking…") else thoughtForLabel(durationSeconds),
                    style = MaterialTheme.typography.bodyLarge,
                    color = headerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }

            if (canExpand) {
                val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
                Symbol(
                    symbol = MaterialSymbol.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = (openFraction() - 1f) * 90f * (if (ltr) 1f else -1f)
                    },
                )
            }
        }

        val revealing by remember(expanded, inProgress) {
            derivedStateOf { expanded || expandPx() > 0f || !inProgress }
        }

        if (canExpand && revealing) {
            ExpandReveal(
                targetPx = { if (expanded) Float.MAX_VALUE else expandPx() },
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

@Composable
private fun ExpandReveal(
    targetPx: () -> Float,
    peeking: Boolean,
    onFullHeight: (Int) -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = Modifier
            .clipToBounds()
            .then(if (peeking) Modifier.fadeBottom(PEEK_FADE_HEIGHT) else Modifier),
    ) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints.copy(minHeight = 0))
        onFullHeight(placeable.height)
        val h = targetPx().coerceIn(0f, placeable.height.toFloat()).roundToInt()
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
}

private fun thoughtForLabel(durationSeconds: Int?, isMocked: Boolean = false): String {
    val verb = if (isMocked) "Faked thinking" else "Thought"
    return when {
        durationSeconds == null || durationSeconds < 1 -> "$verb for a moment"
        else -> "$verb for ${durationSeconds}s"
    }
}

private val TOOL_DISC_SIZE = 26.dp
private val TOOL_DISC_RING = 1.5.dp
private val TOOL_DISC_ICON = 13.dp
private val TOOL_STACK_OVERLAP = 8.dp
private val TOOL_DISC_LOADER = 18.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ToolStack(tools: List<ProcessItem.Tool>, pending: Boolean = false) {
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
            .background(CronColors.pageBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(TOOL_DISC_SIZE - TOOL_DISC_RING * 2)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

    Box(modifier = modifier.fillMaxWidth().then(if (inProgress) Modifier else Modifier.animateContentSize())) {
        AnimatedVisibility(visible = hasAnswer, enter = fadeIn(), exit = fadeOut(), label = "response-body") {
            Column {
                Spacer(Modifier.height(Spacing.sm))
                ResponseBody(response?.takeIf { it.isNotBlank() } ?: lastResponse)
                Spacer(Modifier.height(Spacing.sm))
            }
        }
        AnimatedVisibility(visible = showFallback, enter = fadeIn(), exit = fadeOut(), label = "no-response-fallback") {
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
