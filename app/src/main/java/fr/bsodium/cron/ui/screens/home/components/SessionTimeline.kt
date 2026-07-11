@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

private val ARROW_ICON_SIZE = 18.dp

internal fun LazyListScope.sessionTimelineItems(
    timeline: List<TimelineItem>,
    hasMore: Boolean,
    onOpenAiRun: (turnIndex: Int, sessionId: String) -> Unit,
    onNavigateToHistory: () -> Unit,
) {
    item(key = "timeline-top-spacer") {
        Spacer(Modifier.height(Spacing.xxxl))
    }

    items(
        count = timeline.size,
        key = { timeline[it].id },
        contentType = { timeline[it]::class },
    ) { index ->
        val item = timeline[index]
        // A day header renders no connector of its own, so the node on either side is a fresh segment boundary, same as the list's true first/last row.
        val isFirst = index == 0 || timeline[index - 1] is TimelineItem.DayHeader
        val isLast = index == timeline.lastIndex || timeline[index + 1] is TimelineItem.DayHeader
        val placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
        val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

        when (item) {
            is TimelineItem.AiRun -> AiRunNode(
                item = item,
                isFirst = isFirst,
                isLast = isLast,
                onClick = { onOpenAiRun(item.iteration.turnIndex, item.sessionId) },
                modifier = Modifier.animateItem(fadeInSpec = fadeSpec, placementSpec = placementSpec, fadeOutSpec = fadeSpec),
            )
            is TimelineItem.Event -> EventNode(
                item = item,
                isFirst = isFirst,
                isLast = isLast,
                modifier = Modifier.animateItem(fadeInSpec = fadeSpec, placementSpec = placementSpec, fadeOutSpec = fadeSpec),
            )
            is TimelineItem.DayHeader -> DayHeaderRow(
                item = item,
                modifier = Modifier.animateItem(fadeInSpec = fadeSpec, placementSpec = placementSpec, fadeOutSpec = fadeSpec),
            )
        }
    }

    if (hasMore) {
        item(key = "view-history") {
            FilledTonalButton(
                onClick = onNavigateToHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.lg),
                shape = Radius.full,
            ) {
                Text("View full history")
            }
        }
    }
}

@Composable
internal fun AiRunNode(
    item: TimelineItem.AiRun,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val iter = item.iteration
    val symbol = if (iter.thread.isMocked) MaterialSymbol.Code else runSymbol(iter.kind)
    // Every AI run shares the primary family (docs/expressive.md "AI runs → primary"); the latest one steps up to its own morphing hero anchor.
    val anchor = when {
        item.isStreaming -> TimelineAnchor.Loader
        item.isLatest -> TimelineAnchor.Latest(symbol)
        else -> TimelineAnchor.Icon(symbol = symbol, tint = scheme.onPrimaryContainer, containerColor = scheme.primaryContainer)
    }
    val contentColor = scheme.onSurfaceVariant
    // The resolved alarm time is the "key fact" headline; a cancel/do_nothing turn has none, so it falls back to the AI's prose outcome instead of a time pair.
    val newTime = iter.thread.newAlarmTime.takeIf { item.isLatest }
    val prevTime = iter.previousAlarmTime.takeIf { item.isLatest }
    val heroHeadline = iter.thread.summary?.takeIf { item.isLatest && it.isNotBlank() }

    TimelineNode(
        anchor = anchor,
        isFirst = isFirst,
        isLast = isLast,
        onClick = onClick,
        modifier = modifier,
        verticalPadding = if (item.isLatest) Spacing.lg else Spacing.md,
        title = {
            if (item.isLatest) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    // Shares the countdown card's `primary` fill so the newest run visually rhymes with it; a plain on-role pairing, not a nested-container case (docs/color-roles.md).
                    Text(
                        text = iter.systemMessage.uppercase(Locale.US),
                        style = CronTypography.timelineHeroKicker,
                        color = scheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    when {
                        // A real change: show it as a PREV › NEW pair, NEW bolder — the key fact at a glance, not a prose sentence.
                        newTime != null && prevTime != null && prevTime != newTime -> Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(text = prevTime.asClock(), style = CronTypography.timelineHeroTimePrev)
                            Text(text = "›", style = CronTypography.timelineHeroTimePrev, color = contentColor)
                            Text(text = newTime.asClock(), style = CronTypography.timelineHeroTitle)
                        }
                        // No prior time to compare against, or it didn't change — the pair would just read as a redundant "7:30 › 7:30".
                        newTime != null -> Text(text = newTime.asClock(), style = CronTypography.timelineHeroTitle)
                        // No new time at all (cancel/do_nothing) — falls back to the AI's own outcome sentence.
                        else -> Text(
                            text = heroHeadline ?: iter.systemMessage,
                            style = CronTypography.timelineHeroTitle,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = iter.systemMessage,
                    style = CronTypography.timelineRowTitle,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        status = {
            if (item.isLatest) {
                // The tap-through affordance belongs in this trailing, vertically-centered slot, not inline with the headline.
                Symbol(symbol = MaterialSymbol.ArrowForward, contentDescription = null, tint = contentColor, size = ARROW_ICON_SIZE)
            } else {
                Text(
                    text = iter.timeLabel,
                    style = CronTypography.labelMonoSmall,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        },
        content = if (item.isLatest) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Text(
                        text = "Latest · ${iter.timeLabel}",
                        style = CronTypography.labelMonoSmall,
                        color = contentColor,
                    )
                    // Only when the headline above is a time — the prose-fallback headline already IS this summary, so showing it again here would duplicate it.
                    if (newTime != null && heroHeadline != null) {
                        Text(
                            text = heroHeadline,
                            style = MaterialTheme.typography.bodyMedium,
                            color = contentColor,
                        )
                    }
                }
            }
        } else {
            null
        },
    )
}

private fun LocalTime.asClock(): String = String.format(Locale.US, "%02d:%02d", hour, minute)

@Composable
internal fun EventNode(
    item: TimelineItem.Event,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = item.trigger.timelineAccent()
    val accentContainer = accent.containerColor()
    val onAccentContainer = accent.onContainerColor()
    val tz = TimeZone.currentSystemDefault()
    val local = item.timestamp.toLocalDateTime(tz)
    val timeText = String.format(Locale.US, "%02d:%02d", local.hour, local.minute)

    TimelineNode(
        anchor = TimelineAnchor.Icon(
            symbol = triggerSymbol(item.trigger),
            tint = onAccentContainer,
            containerColor = accentContainer,
        ),
        isFirst = isFirst,
        isLast = isLast,
        verticalPadding = Spacing.md,
        modifier = modifier,
        title = {
            Text(
                text = item.label,
                style = CronTypography.timelineRowTitle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        status = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (item.detail != null) {
                    MonoPill(text = item.detail, containerColor = accentContainer, contentColor = onAccentContainer)
                }
                Text(
                    text = timeText,
                    style = CronTypography.labelMonoSmall,
                    color = contentColor,
                )
            }
        },
    )
}

