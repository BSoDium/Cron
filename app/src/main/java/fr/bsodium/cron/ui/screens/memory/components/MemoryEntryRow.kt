package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
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
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val TIMESTAMP_COLUMN_WIDTH = 88.dp
private val DELETE_ICON_SIZE = 22.dp
private val CARD_GAP = Spacing.xs
private val ICON_EDGE_PADDING = Spacing.lg

/** One memory entry, swipe-left-to-delete (Gmail-style) — never edited in place, only ever removed
 *  directly or superseded by the assistant via the composer above. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryEntryRow(entry: MemoryEntry, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = modifier.testTag("memory-entry-${entry.id}")) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            // A completed swipe only asks for confirmation — deleting is irreversible and this is
            // the one action in Memory that isn't routed through the assistant, so there's no
            // second chance to notice a mistake later. Declining resets the row rather than leaving
            // it dismissed, since the swipe gesture itself never actually deletes anything.
            onDismiss = { direction -> if (direction == SwipeToDismissBoxValue.EndToStart) showDeleteConfirm = true },
            backgroundContent = {
                // The card is sized to exactly the reveal amount (matches the row's own edge, no
                // clipping needed) — but that size must be read inside Modifier.layout{}, the LAYOUT
                // phase, not the composable body (composition phase). A composition-time read of
                // dismissState.requireOffset() only updates this card on its own, slower
                // recomposition-and-remeasure cadence, which visibly lagged a frame behind the row's
                // own layout-phase-deferred offset during a fast fling or snap-back and let the
                // (still-wide, stale) card overlap the settling row — confirmed live, not theoretical.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = Spacing.xxs),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .padding(end = CARD_GAP)
                            .fillMaxHeight()
                            .layout { measurable, constraints ->
                                val revealPx = abs(runCatching { dismissState.requireOffset() }.getOrDefault(0f)).roundToInt()
                                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = revealPx))
                                layout(revealPx, placeable.height) { placeable.placeRelative(0, 0) }
                            }
                            .clip(RoundedCornerShape(Radius.xl))
                            .background(MaterialTheme.colorScheme.error),
                    ) {
                        Symbol(
                            symbol = MaterialSymbol.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onError,
                            size = DELETE_ICON_SIZE,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset {
                                    val revealPx = abs(runCatching { dismissState.requireOffset() }.getOrDefault(0f))
                                    val iconHalfPx = (DELETE_ICON_SIZE / 2).toPx()
                                    val fixedOffsetFromEdgePx = (ICON_EDGE_PADDING + DELETE_ICON_SIZE / 2).toPx()
                                    val offsetFromEdgePx = max(fixedOffsetFromEdgePx, revealPx / 2f)
                                    val iconCenterXPx = revealPx - offsetFromEdgePx
                                    IntOffset((iconCenterXPx - iconHalfPx).roundToInt(), 0)
                                },
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

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirm = false
                scope.launch { dismissState.reset() }
            },
            title = { Text("Delete this memory?") },
            text = { Text(entry.text) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch { dismissState.reset() }
                }) { Text("Cancel") }
            },
        )
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
