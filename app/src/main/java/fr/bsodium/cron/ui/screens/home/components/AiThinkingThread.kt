package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownBulletList
import com.mikepenz.markdown.compose.elements.MarkdownHeader
import com.mikepenz.markdown.compose.elements.MarkdownOrderedList
import com.mikepenz.markdown.compose.elements.MarkdownParagraph
import com.mikepenz.markdown.compose.elements.listDepth
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CodeFontFamily
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.SerifFontFamily
import fr.bsodium.cron.ui.theme.Spacing

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
fun AiThinkingThread(thread: AiThreadUi, isRunning: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // The turn's settled/running state is the real WorkManager signal, not whether a text
        // response exists — a do_nothing turn finishes with no response and must still settle.
        val inProgress = isRunning
        if (thread.process.isNotEmpty() || inProgress) {
            ThinkingDisclosure(
                summary = thread.summary,
                process = thread.process,
                inProgress = inProgress,
                durationSeconds = thread.durationSeconds,
            )
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.sm))
            ResponseBody(thread.response)
        }
    }
}

private val GUTTER_WIDTH = 28.dp
private val TIMELINE_RULE_WIDTH = 2.dp
private val STEP_ICON_SIZE = 16.dp
private val ICON_MASK_SIZE = 24.dp
// Vertical padding around each row's content. Doubles as the gap between steps —
// kept at sm so a clear segment of the timeline rule shows between icon discs.
private val TIMELINE_CONTENT_VPAD = Spacing.sm

// Centre the glyph within its line box (the default trims the first line's top leading and
// seats the glyph high), so the first line's optical centre lands at lineHeight/2 — where the
// gutter icon disc is positioned.
private val STEP_LINE_HEIGHT = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

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
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = process.isNotEmpty()
    // Full-bleed square bar: transparent when collapsed, a quiet fill when open (a step below
    // the alarm card so it stays secondary to it). It bleeds past the screen-side content
    // padding (Spacing.xl) to hug both edges; the compensating start/end padding keeps the
    // summary text on the content edge (flush with the response) and the chevron in place.
    // Only the timeline stays indented.
    Row(
        modifier = Modifier
            .bleedHorizontally(Spacing.xl)
            .fillMaxWidth()
            .let { if (canExpand) it.clickable { expanded = !expanded } else it }
            .background(if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else Color.Transparent)
            .heightIn(min = 48.dp)
            .padding(start = Spacing.xl, top = Spacing.sm, end = Spacing.md + Spacing.xl, bottom = Spacing.sm),
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
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
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
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    AnimatedVisibility(
        visible = expanded && canExpand,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
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
private fun ThinkingIcon() = Icon(
    painter = painterResource(R.drawable.ic_thinking),
    contentDescription = null,
    tint = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.size(STEP_ICON_SIZE),
)

/** Line height of a step's first line, used to centre its icon on that line. */
@Composable
private fun stepFirstLineHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
}

@Composable
private fun ProcessTextRow(text: String, isFirst: Boolean, isLast: Boolean) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    val collapsible = text.length > REASONING_COLLAPSE_CHARS
    val bodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeightStyle = STEP_LINE_HEIGHT,
    )
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = { ThinkingIcon() },
    ) {
        Column {
            // The full markdown is always rendered (parsed once — never blanks on toggle); collapsing
            // just clips an animated height, so expand/collapse glides instead of jumping.
            if (collapsible) {
                ClippedReveal(expanded = expanded, collapsedHeight = reasoningCollapsedHeight()) {
                    MarkdownBlock(text = text, bodyStyle = bodyStyle, serif = false)
                }
                // Snug transparent pill — ripple only on press. minimumInteractiveComponentSize keeps
                // the touch target at 48dp while the visible pill stays compact.
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(Radius.full)
                        .clickable { expanded = !expanded }
                        .padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (expanded) "See less" else "See more",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                MarkdownBlock(text = text, bodyStyle = bodyStyle, serif = false)
            }
        }
    }
}

private const val REASONING_COLLAPSE_CHARS = 280
private const val REASONING_COLLAPSED_LINES = 6
// Taller fade band → a softer, more gradual dissolve into "See more" (was Spacing.xl, too abrupt).
private val REASONING_FADE_HEIGHT = 48.dp
private val REASONING_HEIGHT_SPEC = tween<Int>(durationMillis = 200, easing = FastOutSlowInEasing)

/** Collapsed clamp height for a long reasoning block — about [REASONING_COLLAPSED_LINES] lines. */
@Composable
private fun reasoningCollapsedHeight(): Dp {
    val density = LocalDensity.current
    val lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
    return with(density) { lineHeight.toDp() } * REASONING_COLLAPSED_LINES
}

/**
 * Renders [content] at full height but reports an animated, clamped height: expanding/collapsing
 * clips the bottom rather than swapping the text, so the markdown is parsed once and the motion is
 * smooth both ways. Top-anchored — the top stays put while the bottom reveals/clips — with a soft
 * [fadeBottom] edge while content remains below the fold.
 */
@Composable
private fun ClippedReveal(
    expanded: Boolean,
    collapsedHeight: Dp,
    content: @Composable () -> Unit,
) {
    val collapsedPx = with(LocalDensity.current) { collapsedHeight.roundToPx() }
    var fullPx by remember { mutableStateOf(0) }
    val target = if (expanded) fullPx else minOf(collapsedPx, fullPx)
    val animatedPx by animateIntAsState(target, REASONING_HEIGHT_SPEC, label = "reasoning-reveal")
    val fading = fullPx > 0 && animatedPx < fullPx
    SubcomposeLayout(
        modifier = Modifier
            .clipToBounds()
            .then(if (fading) Modifier.fadeBottom(REASONING_FADE_HEIGHT) else Modifier),
    ) { constraints ->
        val placeable = subcompose(Unit, content).first().measure(constraints.copy(minHeight = 0))
        if (placeable.height != fullPx) fullPx = placeable.height
        val h = if (fullPx == 0) placeable.height else animatedPx.coerceIn(0, placeable.height)
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
}

/**
 * Fades the bottom [height] of the content to transparent — a soft truncation edge. Renders to an
 * offscreen layer so the [BlendMode.DstIn] gradient erases only the destination's lower band; the
 * gradient is opaque above [height] from the bottom, so everything but that band is kept intact.
 */
private fun Modifier.fadeBottom(height: Dp): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startY = size.height - height.toPx(),
                endY = size.height,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Stacks timeline rows so their per-row [TimelineRow] rules join into one thread. */
@Composable
private fun TimelineColumn(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun TimelineRow(
    firstLineHeight: Dp,
    isFirst: Boolean,
    isLast: Boolean,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // Low-emphasis connector tone with enough contrast for a 2dp line against the page.
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val maskColor = MaterialTheme.colorScheme.background
    // Centre the icon disc on the content's FIRST line (not the whole row, which
    // drifts to the middle of multi-line reasoning): disc centre =
    // contentTopPad + firstLine/2, so its top inset is that minus half the disc.
    val discTop = (TIMELINE_CONTENT_VPAD + (firstLineHeight - ICON_MASK_SIZE) / 2)
        .coerceAtLeast(0.dp)
    val iconCenter = discTop + ICON_MASK_SIZE / 2
    Box(modifier = Modifier.fillMaxWidth()) {
        // Content drives the row height. No IntrinsicSize.Min here, so an animating-height child
        // (see ClippedReveal) can actually resize the row instead of being snapped to full.
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(GUTTER_WIDTH))
            Spacer(Modifier.width(Spacing.sm))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = TIMELINE_CONTENT_VPAD, bottom = TIMELINE_CONTENT_VPAD, end = Spacing.md),
            ) { content() }
        }
        // Gutter overlay: matchParentSize reads the content-driven height (it doesn't drive it), so
        // the connector rule and the masking icon disc track the content as it animates.
        Box(modifier = Modifier.matchParentSize()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(GUTTER_WIDTH)
                    .fillMaxHeight()
                    // Draw the rule centred in the gutter, capped at the first/last node's centre.
                    .drawBehind {
                        if (isFirst && isLast) return@drawBehind
                        val cx = size.width / 2f
                        val top = if (isFirst) iconCenter.toPx() else 0f
                        val bottom = if (isLast) iconCenter.toPx() else size.height
                        drawLine(
                            color = ruleColor,
                            start = Offset(cx, top),
                            end = Offset(cx, bottom),
                            strokeWidth = TIMELINE_RULE_WIDTH.toPx(),
                        )
                    },
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = discTop)
                            .size(ICON_MASK_SIZE)
                            .clip(CircleShape)
                            .background(maskColor),
                        contentAlignment = Alignment.Center,
                    ) { icon() }
                }
            }
        }
    }
}

/** Outlined icon for each tool's operation; wrench for anything unmapped. */
private fun toolIcon(name: String): ImageVector = when (name) {
    "read_calendar" -> Icons.Outlined.CalendarMonth
    "set_alarm" -> Icons.Outlined.Alarm
    "cancel_alarm" -> Icons.Outlined.AlarmOff
    "estimate_commute", "estimate_commute_multi_mode" -> Icons.Outlined.DirectionsCar
    "geocode_address" -> Icons.Outlined.LocationOn
    "notify_warning" -> Icons.Outlined.WarningAmber
    "send_brief" -> Icons.AutoMirrored.Outlined.Article
    "do_nothing" -> Icons.Outlined.Bedtime
    else -> Icons.Outlined.Build
}

@Composable
private fun ToolStepRow(step: ProcessItem.Tool, isFirst: Boolean, isLast: Boolean) {
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            Icon(
                imageVector = toolIcon(step.name),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            // "Calling" + name pill take their natural width (priority); the name pill never
            // char-wraps. The result takes the remaining space and ellipsizes (e.g. long addresses).
            Text(
                text = "Calling",
                style = MaterialTheme.typography.bodyMedium.copy(lineHeightStyle = STEP_LINE_HEIGHT),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(Radius.sm),
            ) {
                Text(
                    text = step.name,
                    style = CronTypography.labelMonoSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                when {
                    !step.isComplete -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    step.isError -> Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = "Failed",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(STEP_ICON_SIZE),
                    )
                    step.contextLabel != null -> Text(
                        text = step.contextLabel,
                        style = CronTypography.labelMonoSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                    )
                    else -> Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "Done",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(STEP_ICON_SIZE),
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneRow(isFirst: Boolean, isLast: Boolean) {
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
    }) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.bodyMedium.copy(lineHeightStyle = STEP_LINE_HEIGHT),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
 * Themed markdown renderer. The thinking area passes `serif = false` (sans
 * everything); the final response passes `serif = true` (serif body *and*
 * headers). Tables render through [CronMarkdownTable] — an editorial,
 * weighted-column table with horizontal rules only.
 */
@Composable
private fun MarkdownBlock(
    text: String,
    bodyStyle: TextStyle,
    serif: Boolean,
    modifier: Modifier = Modifier,
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outline = MaterialTheme.colorScheme.outlineVariant

    val colors = markdownColor(
        text = bodyStyle.color.takeIf { it.alpha > 0f } ?: onSurface,
        codeBackground = surfaceHigh,
        inlineCodeBackground = surfaceHigh,
        dividerColor = outline,
        tableBackground = Color.Transparent,
    )
    val serifize: (TextStyle) -> TextStyle =
        { if (serif) it.copy(fontFamily = SerifFontFamily) else it }
    // Martian Mono reads larger than the sans/serif at the same size, so drop code a notch.
    val codeStyle = bodyStyle.copy(
        fontFamily = CodeFontFamily,
        color = onSurface,
        fontSize = (bodyStyle.fontSize.value * 0.85f).sp,
    )
    val typography = markdownTypography(
        h1 = serifize(MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h2 = serifize(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h3 = serifize(MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = onSurface)),
        h4 = serifize(MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, color = onSurface)),
        h5 = serifize(MaterialTheme.typography.labelLarge.copy(color = onSurface)),
        h6 = serifize(MaterialTheme.typography.labelMedium.copy(color = onSurfaceVariant)),
        text = bodyStyle,
        paragraph = bodyStyle,
        bullet = bodyStyle,
        list = bodyStyle,
        ordered = bodyStyle,
        quote = bodyStyle.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
        code = codeStyle,
        inlineCode = codeStyle,
        textLink = androidx.compose.ui.text.TextLinkStyles(
            style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary),
        ),
    )
    Markdown(
        content = text,
        colors = colors,
        typography = typography,
        // The library drops a uniform `block` spacer after every block, so keep it
        // at the tight floor gap and let paragraphs/headers own their own rhythm:
        // paragraphs add a bottom gap; headers add a roomy break above and a
        // below-gap that shrinks with level so deep headers hug the text they head.
        padding = markdownPadding(
            block = MD_BLOCK_GAP,
            listItemTop = Spacing.xs,
            listItemBottom = Spacing.xs,
            listIndent = Spacing.lg,
        ),
        components = markdownComponents(
            paragraph = { model ->
                MarkdownParagraph(model.content, model.node, Modifier.padding(bottom = MD_PARA_BELOW), bodyStyle)
            },
            heading1 = { model -> SpacedHeader(model, typography.h1, HEADING_GAP[0]) },
            heading2 = { model -> SpacedHeader(model, typography.h2, HEADING_GAP[1]) },
            heading3 = { model -> SpacedHeader(model, typography.h3, HEADING_GAP[2]) },
            heading4 = { model -> SpacedHeader(model, typography.h4, HEADING_GAP[3]) },
            heading5 = { model -> SpacedHeader(model, typography.h5, HEADING_GAP[4]) },
            heading6 = { model -> SpacedHeader(model, typography.h6, HEADING_GAP[5]) },
            // Widen the gap between the bullet/number marker and the list text.
            unorderedList = { model ->
                MarkdownBulletList(
                    content = model.content,
                    node = model.node,
                    style = bodyStyle,
                    depth = model.listDepth,
                    markerModifier = { Modifier.padding(end = LIST_MARKER_GAP) },
                )
            },
            orderedList = { model ->
                MarkdownOrderedList(
                    content = model.content,
                    node = model.node,
                    style = bodyStyle,
                    depth = model.listDepth,
                    markerModifier = { Modifier.padding(end = LIST_MARKER_GAP) },
                )
            },
            table = { model -> CronMarkdownTable(model, bodyStyle) },
        ),
        modifier = modifier,
    )
}

/** Per-level header spacing: a section break above, a tighter gap below. */
private class HeadingGap(val top: Dp, val bottom: Dp)

// h1 roomiest, shrinking to h6 which nearly touches its following text. Below-gaps
// stack on top of MD_BLOCK_GAP; the top-gaps separate a header from prior content.
private val HEADING_GAP = listOf(
    HeadingGap(Spacing.md, Spacing.sm),                          // h1 — 12 / 8
    HeadingGap(Spacing.sm + Spacing.xxs, Spacing.xs + Spacing.xxs), // h2 — 10 / 6
    HeadingGap(Spacing.sm, Spacing.xs),                          // h3 — 8 / 4
    HeadingGap(Spacing.xs + Spacing.xxs, Spacing.xxs),           // h4 — 6 / 2
    HeadingGap(Spacing.xs, Spacing.xxs),                         // h5 — 4 / 2
    HeadingGap(Spacing.xs, 0.dp),                                // h6 — 4 / 0
)
private val MD_BLOCK_GAP = Spacing.xxs
private val MD_PARA_BELOW = Spacing.xxs
private val LIST_MARKER_GAP = Spacing.sm
// Bounds for content-proportional table columns, so no column dominates or collapses.
private val TABLE_COL_MIN = 64.dp
private val TABLE_COL_MAX = 200.dp

@Composable
private fun SpacedHeader(model: MarkdownComponentModel, style: TextStyle, gap: HeadingGap) {
    Box(modifier = Modifier.padding(top = gap.top, bottom = gap.bottom)) {
        MarkdownHeader(model.content, model.node, style)
    }
}

/**
 * Editorial table: bold header, a rule under the header and subtle rules between rows, no
 * vertical lines and no doubled borders. Columns are weighted by their widest cell so they
 * stay content-proportional and the text wraps to fit the available width.
 */
@Composable
private fun CronMarkdownTable(model: MarkdownComponentModel, cellStyle: TextStyle) {
    val start = model.node.startOffset.coerceIn(0, model.content.length)
    val end = model.node.endOffset.coerceIn(start, model.content.length)
    val source = model.content.substring(start, end)
    val rows = remember(source) { parseGfmTable(source) }
    if (rows.isEmpty()) return

    val headerLine = MaterialTheme.colorScheme.outlineVariant
    val rowLine = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val headerStyle = cellStyle.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numCols = rows.maxOf { it.size }
    // Weight columns by measured content width, but clamp into a band so a long-sentence
    // column can't dominate (squeezing its neighbours to per-character wrapping) and a short
    // one can't collapse.
    val minColPx = with(density) { TABLE_COL_MIN.toPx() }
    val maxColPx = with(density) { TABLE_COL_MAX.toPx() }
    val colWeights = remember(rows, headerStyle, minColPx, maxColPx) {
        (0 until numCols).map { c ->
            rows.maxOf { row ->
                measurer.measure(AnnotatedString(row.getOrElse(c) { "" }), headerStyle).size.width
            }.toFloat().coerceIn(minColPx, maxColPx)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { r, row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                (0 until numCols).forEach { c ->
                    Box(
                        modifier = Modifier
                            .weight(colWeights[c])
                            .padding(end = Spacing.md, top = Spacing.sm, bottom = Spacing.sm),
                    ) {
                        Text(
                            text = row.getOrElse(c) { "" },
                            style = if (r == 0) headerStyle else cellStyle,
                        )
                    }
                }
            }
            HorizontalDivider(color = if (r == 0) headerLine else rowLine)
        }
    }
}

// Hoisted so they compile once, not on every cell of every table render.
private val TABLE_DELIMITER_CELL = Regex(":?-+:?")
private val MD_BOLD = Regex("\\*\\*(.+?)\\*\\*")
private val MD_BOLD_ALT = Regex("__(.+?)__")
private val MD_ITALIC = Regex("\\*(.+?)\\*")
private val MD_INLINE_CODE = Regex("`(.+?)`")

/** Parse a GFM table block into rows of cell text, dropping the `---|---` delimiter row. */
private fun parseGfmTable(raw: String): List<List<String>> {
    val rows = raw.trim().lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            line.removePrefix("|").removeSuffix("|").split("|").map { stripInlineMarks(it.trim()) }
        }
    return rows.filterNot { row -> row.isNotEmpty() && row.all { it.matches(TABLE_DELIMITER_CELL) } }
}

/** Strip the common inline emphasis/code markers so cells don't show literal `**`/`` ` ``. */
private fun stripInlineMarks(s: String): String = s
    .replace(MD_BOLD, "$1")
    .replace(MD_BOLD_ALT, "$1")
    .replace(MD_ITALIC, "$1")
    .replace(MD_INLINE_CODE, "$1")
