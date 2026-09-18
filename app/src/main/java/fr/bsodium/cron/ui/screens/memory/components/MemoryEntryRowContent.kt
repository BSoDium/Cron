package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.textShimmer
import fr.bsodium.cron.ui.screens.home.components.rememberRelativeAgo
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun PendingMemoryEntryContent(
    instruction: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.sm
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Text(
            text = instruction?.takeIf { it.isNotBlank() } ?: "Updating memory…",
            style = CronTypography.timelineRowTitle,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs)
                .textShimmer(),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier
                    .clip(Radius.full)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(
                        start = Spacing.xxs,
                        end = Spacing.sm,
                        top = Spacing.xxs,
                        bottom = Spacing.xxs
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = "Processing memory",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FailedMemoryEntryContent(
    instruction: String?,
    failureReason: String,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onAddAnyway: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSystemError = isSystemFailure(failureReason)

    Column(
        modifier = modifier.padding(
            start = Spacing.md,
            end = Spacing.md,
            top = Spacing.md,
            bottom = Spacing.sm
        ),
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
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "\"${instruction.orEmpty()}\"",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontStyle = FontStyle.Italic,
                    fontFamily = CronTypography.bodySerif.fontFamily,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = failureMessage(failureReason),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 10,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSystemError) {
                TextButton(
                    onClick = onDelete,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
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
                TextButton(
                    onClick = onAddAnyway,
                    modifier = Modifier.testTag("add-anyway-button"),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 8.dp,
                    ),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.secondary
                    ),
                ) {
                    Symbol(
                        symbol = MaterialSymbol.ArrowInsert,
                        contentDescription = null,
                        size = 18.dp,
                        weight = 400,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add anyway")
                }

                FilledTonalButton(
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
                        weight = 500,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
internal fun SuccessMemoryEntryContent(
    displayedText: String,
    createdAt: Instant,
    updatedAt: Instant,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = displayedText,
            style = CronTypography.timelineRowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 10,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val elapsedMillis by produceState(
                initialValue = Clock.System.now().toEpochMilliseconds() - updatedAt.toEpochMilliseconds(),
                key1 = updatedAt
            ) {
                while (true) {
                    delay(1000.milliseconds)
                    value = Clock.System.now().toEpochMilliseconds() - updatedAt.toEpochMilliseconds()
                }
            }

            val isRecent = elapsedMillis < 300_000L
            val animatedFadeFraction by animateFloatAsState(
                targetValue = if (isRecent) 1f else 0f,
                label = "recent-fade-fraction",
                animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            )

            Box(
                modifier = Modifier
                    .clip(Radius.full)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = animatedFadeFraction))
                    .padding(
                        start = androidx.compose.ui.unit.lerp(Spacing.sm, Spacing.xxs, animatedFadeFraction),
                        end = Spacing.sm,
                        top = Spacing.xxs,
                        bottom = Spacing.xxs
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    if (animatedFadeFraction > 0f) {
                        Symbol(
                            symbol = MaterialSymbol.Update,
                            contentDescription = null,
                            size = 14.dp,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = animatedFadeFraction),
                        )
                    }
                    val label = if (createdAt == updatedAt) "Created" else "Updated"
                    Text(
                        text = "$label ${rememberRelativeAgo(updatedAt.toEpochMilliseconds())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.onSecondaryContainer,
                            animatedFadeFraction
                        ),
                    )
                }
            }
        }
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
