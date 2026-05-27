package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ToolStep
import fr.bsodium.cron.ui.theme.BrandOrange
import fr.bsodium.cron.ui.theme.MonoFontFamily
import fr.bsodium.cron.ui.theme.SerifFontFamily

/**
 * Renders the latest AI turn as a thread:
 *
 *   ▸ {summary}                       (collapsible header)
 *   │ ◷ {thinking content}
 *   │
 *   ● Calling {read_calendar}              ✓
 *   ● Calling {set_alarm}                  ✓
 *   ✓ Done
 *   {serif response prose with `chips`}
 */
@Composable
fun AiThinkingThread(thread: AiThreadUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        ThinkingDisclosure(
            summary = thread.summary,
            thinking = thread.thinking,
            inProgress = !thread.isComplete && thread.response == null,
        )
        if (thread.toolSteps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            thread.toolSteps.forEach { step -> ToolStepRow(step) }
        }
        if (thread.isComplete) {
            Spacer(Modifier.height(14.dp))
            DoneRow()
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            ResponseBody(thread.response)
        }
    }
}

@Composable
private fun ThinkingDisclosure(summary: String?, thinking: String?, inProgress: Boolean) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val canExpand = !thinking.isNullOrBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (canExpand) it.clickable { expanded = !expanded } else it }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    AnimatedVisibility(visible = expanded && canExpand) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
            // Clock icon at top
            Icon(
                imageVector = Icons.Outlined.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .padding(top = 2.dp),
            )
            Spacer(Modifier.width(8.dp))
            // Vertical divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = thinking.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ToolStepRow(step: ToolStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(50)),
        )
        Text(
            text = "Calling",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Mono inline pill for the tool name.
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = step.name,
                fontFamily = MonoFontFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (step.isComplete) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = "Done",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DoneRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = "Done",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResponseBody(text: String) {
    val accent = BrandOrange
    val chipBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val onSurface = MaterialTheme.colorScheme.onSurface
    val annotated = remember(text, accent, chipBg) { parseMarkdownLite(text, accent, chipBg, onSurface) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(
            fontFamily = SerifFontFamily,
            fontSize = 16.sp,
            lineHeight = 22.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Renders backtick-delimited tokens as monospaced orange chips and
 * `**bold**` tokens as bold spans. No other markdown is interpreted.
 */
private fun parseMarkdownLite(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    chipBg: androidx.compose.ui.graphics.Color,
    bodyColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    val codeRe = Regex("`([^`]+)`")
    val boldRe = Regex("\\*\\*([^*]+)\\*\\*")

    // Tokenize into a flat list of (range, kind, content) by scanning both patterns.
    data class Token(val start: Int, val end: Int, val kind: String, val content: String)
    val tokens = mutableListOf<Token>()
    codeRe.findAll(text).forEach { m -> tokens.add(Token(m.range.first, m.range.last + 1, "code", m.groupValues[1])) }
    boldRe.findAll(text).forEach { m -> tokens.add(Token(m.range.first, m.range.last + 1, "bold", m.groupValues[1])) }
    tokens.sortBy { it.start }

    var cursor = 0
    for (t in tokens) {
        if (t.start < cursor) continue // overlapping; skip
        if (t.start > cursor) append(text.substring(cursor, t.start))
        when (t.kind) {
            "code" -> withStyle(SpanStyle(fontFamily = MonoFontFamily, color = accent, background = chipBg)) {
                append(" ${t.content} ")
            }
            "bold" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bodyColor)) {
                append(t.content)
            }
        }
        cursor = t.end
    }
    if (cursor < text.length) append(text.substring(cursor))
}
