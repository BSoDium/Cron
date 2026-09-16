package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.screens.home.components.rememberRelativeAgo
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.datetime.Clock

/** One memory entry, swipe-left-to-delete (Gmail-style) — never edited in place, only ever removed
 *  directly or superseded by the assistant via the composer above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryEntryRow(entry: MemoryEntry, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()

    Box(modifier = modifier.clip(RoundedCornerShape(Radius.lg))) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            onDismiss = { direction -> if (direction == SwipeToDismissBoxValue.EndToStart) onDelete() },
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = Spacing.lg),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Symbol(
                        symbol = MaterialSymbol.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CronColors.elementSurface)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.md),
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
    }
}

@Preview(showBackground = true)
@Composable
private fun MemoryEntryRowPreview() {
    val now = Clock.System.now()
    CronTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 1,
                    text = "Prefers earlier wake-ups on gym days",
                    category = "schedule",
                    createdAt = now,
                    updatedAt = now,
                ),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(id = 2, text = "Commutes by bike", category = null, createdAt = now, updatedAt = now),
                onDelete = {},
            )
        }
    }
}
