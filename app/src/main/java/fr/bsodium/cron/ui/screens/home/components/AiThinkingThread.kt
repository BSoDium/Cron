package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ProcessItem
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MonoFontFamily
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

@Composable
private fun ThinkingDisclosure(
    summary: String?,
    process: List<ProcessItem>,
    isComplete: Boolean,
    inProgress: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = process.isNotEmpty()
    // Pill button: transparent when collapsed; a subtly lighter fill when open,
    // the same colour the timeline rule uses so the thread reads as emerging
    // from the pill. Bigger left padding gives the text button-like breathing room.
    Row(
        modifier = Modifier
            .clip(Radius.full)
            .let { if (canExpand) it.clickable { expanded = !expanded } else it }
            .background(
                if (expanded) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                Radius.full,
            )
            .heightIn(min = 48.dp)
            .padding(start = Spacing.lg, top = Spacing.sm, end = Spacing.md, bottom = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(
            text = summary ?: "Thinking…",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
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
    AnimatedVisibility(visible = expanded && canExpand) {
        TimelineColumn {
            process.forEach { item ->
                when (item) {
                    is ProcessItem.Reasoning -> ProcessTextRow(item.text)
                    is ProcessItem.Narration -> ProcessTextRow(item.text)
                    is ProcessItem.Tool -> ToolStepRow(item)
                }
            }
            if (isComplete) DoneRow()
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

@Composable
private fun ProcessTextRow(text: String) {
    TimelineRow(icon = { ThinkingIcon() }) {
        MarkdownBlock(
            text = text,
            bodyStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            serif = false,
        )
    }
}

/**
 * Wraps a vertical sequence of rows in a single timeline column — the children
 * inherit a left-side rule via [TimelineRow]. Groups the reasoning, tool-step
 * rows, and the "Done" marker under a single visual thread.
 */
@Composable
private fun TimelineColumn(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun TimelineRow(
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    // The rule shares the open-pill colour so the thread looks continuous with it.
    val ruleColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val maskColor = MaterialTheme.colorScheme.background
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
    ) {
        // Gutter: a full-height rule, with the step icon centered on the row in a
        // page-coloured disc that masks the rule, leaving a clean gap around it.
        Box(modifier = Modifier.width(GUTTER_WIDTH)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(ruleColor),
            )
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
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
                .padding(top = Spacing.xs, bottom = Spacing.xs, end = Spacing.md),
        ) { content() }
    }
}

@Composable
private fun ToolStepRow(step: ProcessItem.Tool) {
    TimelineRow(icon = {
        Icon(
            imageVector = Icons.Rounded.Build,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(STEP_ICON_SIZE),
        )
    }) {
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
                        style = CronTypography.labelMono.copy(fontSize = 12.sp, lineHeight = 16.sp),
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
                    style = CronTypography.labelMono.copy(fontSize = 12.sp, lineHeight = 16.sp),
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
private fun DoneRow() {
    TimelineRow(icon = {
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
 * headers). Tables render through [CronMarkdownTable] — a full-grid,
 * horizontally scrollable, borders-only table.
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
        code = bodyStyle.copy(fontFamily = MonoFontFamily, color = onSurface),
        inlineCode = bodyStyle.copy(fontFamily = MonoFontFamily, color = onSurface),
        textLink = androidx.compose.ui.text.TextLinkStyles(
            style = androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.primary),
        ),
    )
    Markdown(
        content = text,
        colors = colors,
        typography = typography,
        // Tighter than before — paragraphs/headers no longer feel too airy.
        padding = markdownPadding(block = Spacing.md),
        components = markdownComponents(
            table = { model -> CronMarkdownTable(model, bodyStyle) },
        ),
        modifier = modifier,
    )
}

/**
 * Full-grid, borders-only, horizontally scrollable table. Parses the GFM table
 * source from the AST node range, sizes each column to its widest cell (so wide
 * tables scroll rather than wrap), and outlines every cell on all four sides.
 */
@Composable
private fun CronMarkdownTable(model: MarkdownComponentModel, cellStyle: TextStyle) {
    val start = model.node.startOffset.coerceIn(0, model.content.length)
    val end = model.node.endOffset.coerceIn(start, model.content.length)
    val rows = parseGfmTable(model.content.substring(start, end))
    if (rows.isEmpty()) return

    val border = MaterialTheme.colorScheme.outlineVariant
    val headerStyle = cellStyle.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numCols = rows.maxOf { it.size }
    val colWidths = (0 until numCols).map { c ->
        val widest = rows.maxOf { row ->
            measurer.measure(AnnotatedString(row.getOrElse(c) { "" }), headerStyle).size.width
        }
        with(density) { widest.toDp() } + Spacing.sm * 2 + Spacing.xs
    }

    Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
        rows.forEachIndexed { r, row ->
            Row {
                (0 until numCols).forEach { c ->
                    Box(
                        modifier = Modifier
                            .width(colWidths[c])
                            .border(1.dp, border)
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                    ) {
                        Text(
                            text = row.getOrElse(c) { "" },
                            style = if (r == 0) headerStyle else cellStyle,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Parse a GFM table block into rows of cell text, dropping the `---|---` delimiter row. */
private fun parseGfmTable(raw: String): List<List<String>> {
    val rows = raw.trim().lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            line.removePrefix("|").removeSuffix("|").split("|").map { stripInlineMarks(it.trim()) }
        }
    return rows.filterNot { row -> row.isNotEmpty() && row.all { it.matches(Regex(":?-+:?")) } }
}

/** Strip the common inline emphasis/code markers so cells don't show literal `**`/`` ` ``. */
private fun stripInlineMarks(s: String): String = s
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("__(.+?)__"), "$1")
    .replace(Regex("\\*(.+?)\\*"), "$1")
    .replace(Regex("`(.+?)`"), "$1")
