package fr.bsodium.cron.ui.screens.memory.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

private val SEE_MORE_ICON_SIZE = 16.dp

@Composable
private fun justificationCollapsedHeight(): Dp {
    val density = LocalDensity.current
    val lineHeight = MaterialTheme.typography.bodySmall.lineHeight
    return with(density) { lineHeight.toDp() } * 3
}

/**
 * Smoothly expands and collapses content by animating layout height using defaultSpatialSpec,
 * measuring the full height via SubcomposeLayout so text isn't reflowed during motion.
 */
@Composable
private fun ClippedReveal(
    expanded: Boolean,
    collapsedHeight: Dp,
    onHasOverflowChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val collapsedPx = with(LocalDensity.current) { collapsedHeight.roundToPx() }
    var fullPx by remember { mutableIntStateOf(0) }
    val target = if (expanded) (if (fullPx > 0) fullPx else collapsedPx) else collapsedPx
    val animatedPx by animateIntAsState(
        targetValue = target,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "justification-reveal",
    )
    SubcomposeLayout(
        modifier = Modifier.clipToBounds(),
    ) { constraints ->
        val placeable = subcompose(Unit, content).first().measure(constraints.copy(minHeight = 0))
        if (placeable.height != fullPx) {
            fullPx = placeable.height
            onHasOverflowChanged(fullPx > collapsedPx + 2)
        }
        val h = if (fullPx == 0) placeable.height else animatedPx.coerceIn(0, placeable.height)
        layout(placeable.width, h) { placeable.place(0, 0) }
    }
}

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
                    .padding(
                        start = Spacing.xs,
                        end = Spacing.xs,
                        top = Spacing.xs,
                        bottom = Spacing.xs
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
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
    var expanded by rememberSaveable(failureReason) { mutableStateOf(false) }
    var canExpand by remember(failureReason) { mutableStateOf(false) }

    val affordance by animateFloatAsState(
        targetValue = if (canExpand || expanded) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "collapse-affordance",
    )

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
            Column {
                ClippedReveal(
                    expanded = expanded,
                    collapsedHeight = justificationCollapsedHeight(),
                    onHasOverflowChanged = { overflow -> canExpand = overflow },
                ) {
                    Text(
                        text = failureMessage(failureReason),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (canExpand || expanded) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer { alpha = affordance }
                            .offset(x = -Spacing.xs, y = Spacing.xxs)
                            .minimumInteractiveComponentSize()
                            .clip(Radius.full)
                            .clickable { expanded = !expanded }
                            .padding(
                                start = Spacing.xs,
                                end = Spacing.sm,
                                top = Spacing.xs,
                                bottom = Spacing.xs
                            ),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Symbol(
                                symbol = if (expanded) MaterialSymbol.ExpandLess else MaterialSymbol.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                size = SEE_MORE_ICON_SIZE,
                            )
                            Text(
                                text = if (expanded) "See less" else "See more",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
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
    text: String,
    createdAt: Instant,
    updatedAt: Instant,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .animateContentSize()
            .padding(start = Spacing.lg, end = Spacing.md, top = Spacing.md, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
        horizontalAlignment = Alignment.Start,
    ) {
        val effectsSpec = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
        val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()
        Box(modifier = Modifier.fillMaxWidth()) {
            AnimatedContent(
                targetState = text,
                transitionSpec = {
                    ContentTransform(
                        initialContentExit = fadeOut(animationSpec = effectsSpec),
                        targetContentEnter = fadeIn(animationSpec = effectsSpec),
                        sizeTransform = SizeTransform(clip = false) { _, _ -> spatialSpec }
                    )
                },
                label = "memory-text-fade-blur",
                modifier = Modifier.fillMaxWidth()
            ) { targetText ->
                val blurRadius by transition.animateFloat(
                    transitionSpec = { effectsSpec },
                    label = "memory-text-blur"
                ) { state ->
                    if (state == EnterExitState.Visible) 0f else 16f
                }

                Text(
                    text = targetText,
                    style = CronTypography.timelineRowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (blurRadius > 0f) Modifier.blur(blurRadius.dp) else Modifier)
                )
            }
        }

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
                        start = androidx.compose.ui.unit.lerp(Spacing.sm, Spacing.xs, animatedFadeFraction),
                        end = Spacing.sm,
                        top = Spacing.xs,
                        bottom = Spacing.xs
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
                    val timeDiff = abs(createdAt.toEpochMilliseconds() - updatedAt.toEpochMilliseconds())
                    val label = if (timeDiff < 1000L) "Created" else "Updated"

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
