package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.unit.IntOffset
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
import kotlin.math.roundToInt

private val TIMESTAMP_COLUMN_WIDTH = 88.dp
private val DELETE_ICON_SIZE = 22.dp

/** One memory entry, swipe-left-to-delete (Gmail-style) — never edited in place, only ever removed
 *  directly or superseded by the assistant via the composer above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryEntryRow(entry: MemoryEntry, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .testTag("memory-entry-${entry.id}")
            .clip(RoundedCornerShape(Radius.lg)),
    ) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            onDismiss = { direction -> if (direction == SwipeToDismissBoxValue.EndToStart) onDelete() },
            backgroundContent = {
                // Icon stays pinned near the right edge (clipped, effectively hidden) while the
                // reveal is narrower than the icon itself, then tracks the center of the growing
                // red strip as the swipe continues — Gmail's reveal physics, not a static icon.
                val offsetPx = runCatching { dismissState.requireOffset() }.getOrDefault(0f)
                val revealPx = abs(offsetPx).coerceAtMost(with(density) { DELETE_ICON_SIZE.toPx() } * 3f)
                val iconPx = with(density) { DELETE_ICON_SIZE.toPx() }
                val shiftPx = (iconPx - revealPx) / 2f
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Symbol(
                        symbol = MaterialSymbol.Close,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                        size = DELETE_ICON_SIZE,
                        modifier = Modifier
                            .padding(end = Spacing.lg)
                            .offset { IntOffset(x = shiftPx.roundToInt(), y = 0) },
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
