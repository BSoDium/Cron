package fr.bsodium.cron.ui.screens.memory.components

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
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.screens.home.components.rememberRelativeAgo
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Clock

@Composable
internal fun MemoryEntryRow(entry: MemoryEntry, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.text,
                style = CronTypography.timelineRowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            entry.category?.let { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
        }
        Text(
            text = rememberRelativeAgo(entry.createdAt.toEpochMilliseconds()),
            style = CronTypography.timelineRowTime,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoryEntryRowPreview() {
    val now = Clock.System.now()
    CronTheme {
        Column {
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 1,
                    text = "Prefers earlier wake-ups on gym days",
                    category = "schedule",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            MemoryEntryRow(
                entry = MemoryEntry(id = 2, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now),
            )
        }
    }
}
