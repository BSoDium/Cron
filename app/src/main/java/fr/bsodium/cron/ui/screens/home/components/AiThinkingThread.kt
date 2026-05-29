package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
fun AiThinkingThread(thread: AiThreadUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        val inProgress = !thread.isComplete && thread.response == null
        if (thread.process.isNotEmpty() || inProgress) {
            ThinkingDisclosure(
                summary = thread.summary,
                process = thread.process,
                isComplete = thread.isComplete,
                inProgress = inProgress,
            )
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.md + Spacing.xs))
            ResponseBody(thread.response)
        }
    }
}

private val GUTTER_WIDTH = 28.dp
private val STEP_ICON_SIZE = 16.dp
private val ICON_MASK_SIZE = 24.dp
// Vertical padding around each row's content. Doubles as the gap between steps —
// kept at sm so a clear segment of the timeline rule shows between icon discs.
private val TIMELINE_CONTENT_VPAD = Spacing.sm

@Composable
private fun ThinkingDisclosure(
    summary: String?,
    process: List<ProcessItem>,
    isComplete: Boolean,
    inProgress: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = process.isNotEmpty()
    // Full-width square bar: transparent when collapsed, a subtly lighter fill when open
    // (the timeline rule colour, so the thread reads as emerging from it). No corner radius
    // and no start padding, so the summary text sits on the thread's content edge — flush
    // with the response below. Only the timeline stays indented.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (canExpand) it.clickable { expanded = !expanded } else it }
            .background(if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .heightIn(min = 48.dp)
            .padding(top = Spacing.sm, end = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = summary ?: "Thinking…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (inProgress) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (canExpand) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
                val isLast = !isComplete && i == process.lastIndex
                when (item) {
                    is ProcessItem.Reasoning -> ProcessTextRow(item.text, isFirst, isLast)
                    is ProcessItem.Narration -> ProcessTextRow(item.text, isFirst, isLast)
                    is ProcessItem.Tool -> ToolStepRow(item, isFirst, isLast)
                }
            }
            if (isComplete) DoneRow(isFirst = process.isEmpty(), isLast = true)
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
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = { ThinkingIcon() },
    ) {
        MarkdownBlock(
            text = text,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            serif = false,
        )
    }
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
    // The rule shares the open-pill colour so the thread looks continuous with it.
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val maskColor = MaterialTheme.colorScheme.background
    // Centre the icon disc on the content's FIRST line (not the whole row, which
    // drifts to the middle of multi-line reasoning): disc centre =
    // contentTopPad + firstLine/2, so its top inset is that minus half the disc.
    val discTop = (TIMELINE_CONTENT_VPAD + (firstLineHeight - ICON_MASK_SIZE) / 2)
        .coerceAtLeast(0.dp)
    val iconCenter = discTop + ICON_MASK_SIZE / 2
    // Cap the connector at the endpoints so it runs from the first node's centre to
    // the last node's centre rather than leaking past them.
    val ruleExtent = when {
        isFirst && isLast -> Modifier.height(0.dp)
        isFirst -> Modifier.fillMaxHeight().padding(top = iconCenter)
        isLast -> Modifier.height(iconCenter)
        else -> Modifier.fillMaxHeight()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Gutter: the connector rule, with the step icon in a page-coloured disc
        // that masks the rule, leaving a clean gap around it.
        Box(modifier = Modifier.width(GUTTER_WIDTH)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .then(ruleExtent)
                    .background(ruleColor),
            )
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
        Spacer(Modifier.width(Spacing.sm))
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(top = TIMELINE_CONTENT_VPAD, bottom = TIMELINE_CONTENT_VPAD, end = Spacing.md),
        ) { content() }
    }
}

@Composable
private fun ToolStepRow(step: ProcessItem.Tool, isFirst: Boolean, isLast: Boolean) {
    TimelineRow(
        firstLineHeight = stepFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(
                    text = "Calling",
                    style = MaterialTheme.typography.bodyMedium,
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
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            when {
                !step.isComplete -> CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                step.contextLabel != null -> Text(
                    text = step.contextLabel,
                    style = CronTypography.labelMonoSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(STEP_ICON_SIZE),
                )
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
            imageVector = Icons.Rounded.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
    }) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.bodyMedium,
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
private val MD_BLOCK_GAP = Spacing.xs
private val MD_PARA_BELOW = Spacing.xs
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
