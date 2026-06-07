package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.AiEditUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing

private val ROW_MIN_HEIGHT = 48.dp     // touch-target floor for the clickable row
private val DOT_SIZE = 6.dp

/**
 * Replans rendered as edits to the original plan: a compact, expandable row per turn (time + a short
 * summary like "No change · …" or "Moved to 08:35"), tapping reveals that replan's own reasoning.
 */
@Composable
internal fun AiEditsList(edits: List<AiEditUi>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        edits.forEach { edit -> EditRow(edit) }
    }
}

@Composable
private fun EditRow(edit: AiEditUi) {
    var expanded by rememberSaveable(edit.turnIndex) { mutableStateOf(edit.thread.isStreaming) }
    // Keep an in-progress edit open as it streams.
    LaunchedEffect(edit.thread.isStreaming) { if (edit.thread.isStreaming) expanded = true }
    val chevronRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "edit-chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .clickable { expanded = !expanded }
                .heightIn(min = ROW_MIN_HEIGHT)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Box(
                modifier = Modifier
                    .size(DOT_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline),
            )
            Text(
                text = edit.timeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = edit.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Box(modifier = Modifier.padding(start = Spacing.lg, end = Spacing.sm, bottom = Spacing.sm)) {
                AiThinkingThread(
                    thread = edit.thread,
                    isRunning = edit.thread.isStreaming,
                    expanded = true,
                )
            }
        }
    }
}

private val PREVIEW_EDITS = listOf(
    AiEditUi(
        turnIndex = 1,
        timeLabel = "02:10",
        summary = "No change · still on track for 08:22",
        thread = AiThreadUi(turnIndex = 1, summary = "No change", process = emptyList(), response = "Sensors look normal — keeping the 08:22 alarm.", durationSeconds = 2),
        changedAlarm = false,
    ),
    AiEditUi(
        turnIndex = 2,
        timeLabel = "03:40",
        summary = "Moved to 08:35",
        thread = AiThreadUi(turnIndex = 2, summary = "Adjusted", process = emptyList(), response = "Deep sleep ran long; nudging the alarm to 08:35.", durationSeconds = 3),
        changedAlarm = true,
    ),
)

@Preview(showBackground = true, name = "Edits list")
@Composable
private fun AiEditsListPreview() {
    CronTheme {
        AiEditsList(edits = PREVIEW_EDITS, modifier = Modifier.padding(Spacing.xl))
    }
}
