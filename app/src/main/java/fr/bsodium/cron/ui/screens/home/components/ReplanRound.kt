package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.home.AiEditUi
import fr.bsodium.cron.ui.screens.home.AiThreadUi
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/**
 * A replan rendered ChatGPT-style: a muted system-message line naming the event that triggered the
 * rerun, then the assistant's thinking + answer inline (no outer collapse). The trailing status shape
 * shows only on the latest round ([isLast]).
 */
@Composable
internal fun ReplanRound(edit: AiEditUi, isLast: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = edit.systemMessage,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (edit.timeLabel.isNotBlank()) {
                Text(
                    text = "· ${edit.timeLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AiThinkingThread(
            thread = edit.thread,
            isRunning = edit.thread.isStreaming,
            showShape = isLast,
        )
    }
}

@Preview(showBackground = true, name = "Replan round")
@Composable
private fun ReplanRoundPreview() {
    CronTheme {
        ReplanRound(
            edit = AiEditUi(
                turnIndex = 1,
                timeLabel = "02:10",
                systemMessage = "Your schedule changed",
                thread = AiThreadUi(turnIndex = 1, summary = "Adjusted", process = emptyList(), response = "The 10:00 stand-up moved to 10:30, so I nudged the alarm to 08:35.", durationSeconds = 4),
            ),
            isLast = true,
            modifier = Modifier.padding(Spacing.xl),
        )
    }
}
