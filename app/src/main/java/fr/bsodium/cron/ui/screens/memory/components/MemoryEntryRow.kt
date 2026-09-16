package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import kotlin.math.abs

private val TIMESTAMP_COLUMN_WIDTH = 88.dp
private val DELETE_ICON_SIZE = 22.dp
private val REVEAL_THRESHOLD = 32.dp

/** One memory entry, swipe-left-to-delete (Gmail-style) — never edited in place, only ever removed
 *  directly or superseded by the assistant via the composer above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryEntryRow(entry: MemoryEntry, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    val density = LocalDensity.current

    Box(modifier = modifier.testTag("memory-entry-${entry.id}")) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            onDismiss = { direction -> if (direction == SwipeToDismissBoxValue.EndToStart) onDelete() },
            backgroundContent = {
                // Below REVEAL_THRESHOLD, nothing is shown at all — the delete card stays fully
                // hidden rather than peeking in. Past it, the card grows from the row's revealed
                // edge as its own independently-rounded shape (not clipped to the row's own
                // bounds), so it reads as a separate card next to the row, Gmail-style — the
                // trash icon centers itself for free since it just sits at the card's own
                // Alignment.Center as the card's width grows.
                val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                val revealPx = abs(offsetPx)
                val thresholdPx = with(density) { REVEAL_THRESHOLD.toPx() }
                val cardWidthPx = (revealPx - thresholdPx).coerceAtLeast(0f)
                val cardWidth = with(density) { cardWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = Spacing.xxs),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = Spacing.xxs)
                            .width(cardWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(Radius.lg))
                            .background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center,
                    ) {
                        Symbol(
                            symbol = MaterialSymbol.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onError,
                            size = DELETE_ICON_SIZE,
                        )
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.lg))
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
                Box(
                    modifier = Modifier.width(TIMESTAMP_COLUMN_WIDTH),
                    contentAlignment = Alignment.TopEnd,
                ) {
                    Text(
                        text = rememberRelativeAgo(entry.createdAt.toEpochMilliseconds()),
                        style = CronTypography.timelineRowTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
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
