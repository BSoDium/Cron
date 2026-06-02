package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.components.MarkdownComponentModel
import fr.bsodium.cron.ui.theme.Spacing

// Bounds for content-proportional table columns, so no column dominates or collapses.
private val TABLE_COL_MIN = 64.dp
private val TABLE_COL_MAX = 200.dp

/**
 * Editorial table: bold header, a rule under the header and subtle rules between rows, no
 * vertical lines and no doubled borders. Columns are weighted by their widest cell so they
 * stay content-proportional and the text wraps to fit the available width.
 */
@Composable
internal fun CronMarkdownTable(model: MarkdownComponentModel, cellStyle: TextStyle) {
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
    // Weight columns by measured width, clamped into a band so a long column can't dominate
    // (squeezing neighbours to per-character wrapping) and a short one can't collapse.
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
