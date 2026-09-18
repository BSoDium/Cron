package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.components.aiPulse
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
 *  directly or superseded by the assistant via the composer above. While [MemoryEntry.pending] is
 *  true (the assistant's mutation turn hasn't finalized this row yet — see
 *  `MemoryRepository.addPending`), it renders as a spinner placeholder in the same shape and
 *  position instead of the entry's not-yet-real text, so the row flips to real content in place
 *  rather than a separate placeholder being swapped for a second, real one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MemoryEntryRow(
    entry: MemoryEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onAddAnyway: () -> Unit = {},
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val haptics = rememberCronHaptics()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(dismissState.targetValue) {
        if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) haptics.tick()
    }

    Box(modifier = modifier.testTag("memory-entry-${entry.id}")) {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = false,
            onDismiss = { direction ->
                if (direction == SwipeToDismissBoxValue.EndToStart) {
                    if (entry.failureReason != null) {
                        onDelete()
                    } else {
                        showDeleteConfirm = true
                    }
                }
            },
            backgroundContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = Spacing.xxs),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .layout { measurable, constraints ->
                                val revealPx = abs(runCatching { dismissState.requireOffset() }.getOrDefault(0f)).roundToInt()
                                val cardWidthPx = (revealPx - CARD_GAP.roundToPx()).coerceAtLeast(0)
                                val placeable = measurable.measure(constraints.copy(minWidth = cardWidthPx, maxWidth = cardWidthPx))
                                layout(cardWidthPx, placeable.height) { placeable.placeRelative(0, 0) }
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
                                    val cardWidthPx = (revealPx - CARD_GAP.toPx()).coerceAtLeast(0f)
                                    val iconHalfPx = (DELETE_ICON_SIZE / 2).toPx()
                                    val fixedOffsetFromEdgePx = (ICON_EDGE_PADDING + DELETE_ICON_SIZE / 2).toPx()
                                    val offsetFromEdgePx = max(fixedOffsetFromEdgePx, cardWidthPx / 2f)
                                    val iconCenterXPx = cardWidthPx - offsetFromEdgePx
                                    IntOffset((iconCenterXPx - iconHalfPx).roundToInt(), 0)
                                },
                        )
                    }
                }
            },
        ) {
            val rowShape = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.lg))
                .background(CronColors.elementSurface)
            if (entry.pending) {
                Row(
                    modifier = rowShape
                        .aiPulse(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.primaryContainer,
                            ),
                            cornerRadius = Radius.lg,
                        )
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = entry.instruction?.takeIf { it.isNotBlank() } ?: "Updating memory…",
                        style = CronTypography.timelineRowTitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            } else if (entry.failureReason != null) {
                val isSystemError = isSystemFailure(entry.failureReason)

                Column(
                    modifier = rowShape.padding(start = Spacing.md, end = Spacing.md, top = Spacing.md, bottom = Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.xs, vertical = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    ) {
                        Text(
                            text = "Couldn't save this memory",
                            style = CronTypography.timelineRowTitle,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(
                            text = "\"${entry.instruction.orEmpty()}\"",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = FontStyle.Italic,
                                fontFamily = CronTypography.bodySerif.fontFamily,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = failureMessage(entry.failureReason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TextButton(
                            onClick = onDelete,
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 8.dp,
                            ),
                        ) {
                            Symbol(
                                symbol = MaterialSymbol.Delete,
                                contentDescription = null,
                                size = 18.dp,
                                weight = 400,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete")
                        }

                        if (isSystemError) {
                            FilledTonalButton(
                                onClick = onRetry,
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            ) {
                                Symbol(
                                    symbol = MaterialSymbol.Update,
                                    contentDescription = null,
                                    size = 18.dp,
                                    weight = 500,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Retry")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = onAddAnyway,
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 16.dp,
                                    top = 8.dp,
                                    bottom = 8.dp,
                                ),
                            ) {
                                Symbol(
                                    symbol = MaterialSymbol.DoneAll,
                                    contentDescription = null,
                                    size = 18.dp,
                                    weight = 500,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Add anyway")
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = rowShape.padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.text,
                            style = CronTypography.timelineRowTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Text(
                        text = rememberRelativeAgo(entry.createdAt.toEpochMilliseconds()),
                        style = CronTypography.timelineRowTime,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(TIMESTAMP_COLUMN_WIDTH),
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
            text = {
                Text(entry.text.ifBlank { entry.instruction ?: "This memory is still being processed." })
            },
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

private fun isSystemFailure(reason: String): Boolean = when (reason) {
    "no_api_key", "budget_exhausted", "http_error", "max_retries_exceeded", "technical_error" -> true
    else -> false
}


private fun failureMessage(reason: String): String = when (reason) {
    "no_api_key" -> "Add an API key in Settings and try again."
    "budget_exhausted" -> "Today's AI token budget is exhausted."
    "no_memory_added" -> "The assistant did not create a memory from that instruction."
    "http_error" -> "The AI service couldn't be reached."
    "max_retries_exceeded", "technical_error" -> "A technical error prevented the assistant from processing this instruction."
    else -> reason
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
            MemoryEntryRow(
                entry = MemoryEntry(id = 3, text = "", category = null, createdAt = now, updatedAt = now, pending = true),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(id = 4, text = "", instruction = "I am a moderate fan of vegetables. Especially fried ones.", category = null, createdAt = now, updatedAt = now, pending = false, failureReason = "I'm not storing that because it's a food preference unrelated to sleep planning. The sleep-planning assistant needs facts about your schedule, commute, wake times, and other constraints that affect when you should go to bed —not general food likes or dislikes."),
                onDelete = {},
            )
        }
    }
}
