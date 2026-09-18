package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.components.textShimmer
import fr.bsodium.cron.ui.screens.home.components.rememberRelativeAgo
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.days

private val DELETE_ICON_SIZE = 22.dp
private val CARD_GAP = Spacing.xs
private val ICON_EDGE_PADDING = Spacing.lg

/** One memory entry, swipe-left-to-delete (Gmail-style) — never edited in place, only ever removed
 *  directly or superseded by the assistant via the composer above. While [MemoryEntry.pending] is
 *  true (the assistant's mutation turn hasn't finalized this row yet — see
 *  `MemoryRepository.addPending`), it renders with a shimmer placeholder in the same shape and
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

    var displayedText by remember(entry.id) { mutableStateOf(entry.text) }
    var lastText by remember(entry.id) { mutableStateOf(entry.text) }

    LaunchedEffect(entry.text, entry.id) {
        if (entry.text != lastText) {
            for (len in 0..entry.text.length) {
                displayedText = entry.text.substring(0, len)
                delay(20.milliseconds)
            }
            lastText = entry.text
        } else {
            displayedText = entry.text
        }
    }

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
                                val revealPx =
                                    abs(runCatching { dismissState.requireOffset() }.getOrDefault(0f)).roundToInt()
                                val cardWidthPx = (revealPx - CARD_GAP.roundToPx()).coerceAtLeast(0)
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minWidth = cardWidthPx,
                                        maxWidth = cardWidthPx
                                    )
                                )
                                layout(cardWidthPx, placeable.height) {
                                    placeable.placeRelative(
                                        0,
                                        0
                                    )
                                }
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
                                    val revealPx = abs(
                                        runCatching { dismissState.requireOffset() }.getOrDefault(
                                            0f
                                        )
                                    )
                                    val cardWidthPx = (revealPx - CARD_GAP.toPx()).coerceAtLeast(0f)
                                    val iconHalfPx = (DELETE_ICON_SIZE / 2).toPx()
                                    val fixedOffsetFromEdgePx =
                                        (ICON_EDGE_PADDING + DELETE_ICON_SIZE / 2).toPx()
                                    val offsetFromEdgePx =
                                        max(fixedOffsetFromEdgePx, cardWidthPx / 2f)
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
                .animateContentSize()

            if (entry.pending) {
                PendingMemoryEntryContent(
                    instruction = entry.instruction,
                    onDelete = onDelete,
                    modifier = rowShape
                )
            } else if (entry.failureReason != null) {
                FailedMemoryEntryContent(
                    instruction = entry.instruction,
                    failureReason = entry.failureReason,
                    onDelete = onDelete,
                    onRetry = onRetry,
                    onAddAnyway = onAddAnyway,
                    modifier = rowShape
                )
            } else {
                SuccessMemoryEntryContent(
                    displayedText = displayedText,
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt,
                    modifier = rowShape
                )
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
                Text(
                    text = entry.text.ifBlank {
                        entry.instruction ?: "This memory is still being processed."
                    },
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val yesterday = now.minus(1.days)
    CronTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 1,
                    text = "Prefers earlier wake-ups on gym days",
                    category = "schedule",
                    createdAt = yesterday,
                    updatedAt = yesterday,
                ),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 2,
                    text = "Commutes by bike",
                    category = null,
                    createdAt = yesterday,
                    updatedAt = now
                ),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 4,
                    text = "",
                    instruction = "I am a moderate fan of vegetables. Especially fried ones.",
                    category = null,
                    createdAt = now,
                    updatedAt = now,
                    pending = false,
                    failureReason = "I'm not storing that because it's a food preference unrelated to sleep planning. The sleep-planning assistant needs facts about your schedule, commute, wake times, and other constraints that affect when you should go to bed —not general food likes or dislikes."
                ),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 4,
                    text = "",
                    instruction = "When I take the plane, I need a 2 to 3 hour buffer to clear security.",
                    category = null,
                    createdAt = now,
                    updatedAt = now,
                    pending = false,
                    failureReason = "budget_exhausted"
                ),
                onDelete = {},
            )
            MemoryEntryRow(
                entry = MemoryEntry(
                    id = 3,
                    text = "",
                    instruction = "I only wake up late on weekends.",
                    category = null,
                    createdAt = now,
                    updatedAt = now,
                    pending = true
                ),
                onDelete = {},
            )
        }
    }
}
