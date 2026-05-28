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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.CheckCircle
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.screens.home.ToolStep
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MonoFontFamily
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

/**
 * Latest-turn AI thread:
 *
 *   summary line                                ⌄    (sans, collapsible)
 *   │  thinking body (serif, markdown)
 *   ●  Calling [read_calendar]                  ✓    (sans label, sans pill)
 *   ●  Calling [set_alarm]                      ✓
 *   ✓  Done
 *   {serif response body, full markdown}
 */
@Composable
fun AiThinkingThread(thread: AiThreadUi, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        val showDisclosure = !thread.thinking.isNullOrBlank() ||
            (!thread.isComplete && thread.response.isNullOrBlank())
        if (showDisclosure) {
            ThinkingDisclosure(
                summary = thread.summary,
                thinking = thread.thinking,
                inProgress = !thread.isComplete && thread.response == null,
            )
        }
        if (thread.toolSteps.isNotEmpty() || thread.isComplete) {
            Spacer(Modifier.height(Spacing.sm))
            TimelineColumn {
                thread.toolSteps.forEach { step -> ToolStepRow(step) }
                if (thread.isComplete) DoneRow()
            }
        }
        if (!thread.response.isNullOrBlank()) {
            Spacer(Modifier.height(Spacing.md + Spacing.xs))
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
        TimelineRow(showBullet = false) {
            MarkdownBlock(
                text = thinking.orEmpty(),
                bodyStyle = CronTypography.bodySerif.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
}

/**
 * Wraps a vertical sequence of rows in a single timeline column — the children
 * inherit a left-side rule via [TimelineRow]. Useful for grouping the thinking
 * body, tool-step rows, and the "Done" marker under a single visual thread.
 */
@Composable
private fun TimelineColumn(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}

@Composable
private fun TimelineRow(
    showBullet: Boolean = true,
    content: @Composable () -> Unit,
) {
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    val dotColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        // Fixed-width gutter that hosts the vertical rule + (optionally) a bullet.
        Box(modifier = Modifier.width(20.dp)) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(ruleColor),
            )
            if (showBullet) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 7.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        Box(modifier = Modifier.padding(vertical = 4.dp)) { content() }
    }
}

@Composable
private fun ToolStepRow(step: ToolStep) {
    TimelineRow(showBullet = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
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
}

@Composable
private fun DoneRow() {
    TimelineRow(showBullet = false) {
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
}

@Composable
private fun ResponseBody(text: String) {
    MarkdownBlock(
        text = text,
        bodyStyle = CronTypography.bodySerif.copy(color = MaterialTheme.colorScheme.onSurface),
    )
}

/**
 * Themed markdown renderer used for both the assistant's thinking body and
 * the final response. Paragraphs and lists render in the serif body face;
 * headers stay in the sans display face for clear hierarchy; inline & block
 * code render in the mono face on a `surfaceContainerHigh` chip.
 */
@Composable
private fun MarkdownBlock(
    text: String,
    bodyStyle: androidx.compose.ui.text.TextStyle,
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
        tableBackground = surfaceHigh,
    )
    val typography = markdownTypography(
        h1 = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold, color = onSurface),
        h2 = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, color = onSurface),
        h3 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = onSurface),
        h4 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium, color = onSurface),
        h5 = MaterialTheme.typography.labelLarge.copy(color = onSurface),
        h6 = MaterialTheme.typography.labelMedium.copy(color = onSurfaceVariant),
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
        modifier = modifier,
    )
}
