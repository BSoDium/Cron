package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.TightTextStyle
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

@Composable
internal fun EventNode(
    item: TimelineItem.Event,
    registry: TimelineTrackRegistry,
    isSegmentTop: Boolean,
    isSegmentBottom: Boolean,
    isAsleepAbove: Boolean,
    isAsleepBelow: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = item.trigger.timelineAccent()
    /** `isAsleepAbove || isAsleepBelow`, not `isAsleepAbove` alone: [TimelineTrackOverlay]'s
     *  `buildSleepPills` nests a transition cap inside the sleep pill's rounded end on EITHER
     *  edge — a sleep-onset cap (asleepAbove=true) *and* a wake-up cap (asleepBelow=true) both get
     *  the pill's cap extended past their own center, but `isAsleepAbove` alone is only true for
     *  the onset direction. A wake-up cap like `OutOfBedConfirmed` has `asleepAbove=false` even
     *  though it sits visually inside the dark pill from the sleep side below it — the OR catches
     *  both. */
    val isCapNestedInSleepPill = isAsleepAbove || isAsleepBelow
    val anchorContainer = accent.capContainerColor(isAsleep = isCapNestedInSleepPill)
    val onAnchorContainer = accent.capOnContainerColor(isAsleep = isCapNestedInSleepPill)
    val tz = TimeZone.currentSystemDefault()
    val local = item.timestamp.toLocalDateTime(tz)
    val timeText = String.format(Locale.US, "%02d:%02d", local.hour, local.minute)
    val detail = item.detail
    val emphasis = item.detailEmphasis

    TimelineNode(
        id = item.id,
        registry = registry,
        anchor = TimelineAnchor.Icon(
            symbol = triggerSymbol(item.trigger),
            tint = onAnchorContainer,
            containerColor = anchorContainer,
            valence = item.trigger.timelineValence(),
        ),
        isSegmentTop = isSegmentTop,
        isSegmentBottom = isSegmentBottom,
        isAsleepAbove = isAsleepAbove,
        isAsleepBelow = isAsleepBelow,
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
            Text(
                text = timelineTimeLabel(item.timestamp.toEpochMilliseconds(), timeText),
                style = CronTypography.timelineRowTime,
                color = contentColor,
            )
        },
        // A subtext line with the one fact that matters bolded, styled `outline` (secondary detail, not competing with the title/time color); `.merge(TightTextStyle)` removes Android's default font-padding leading, since passing an explicit `style` bypasses `LocalTextStyle` entirely.
        content = detail?.let { text ->
            emphasis?.let {
                {
                    Text(
                        text = emphasized(text, it),
                        style = MaterialTheme.typography.bodyMedium.merge(TightTextStyle),
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
    )
}

/** [text] with the [emphasis] substring rendered bold — falls back to plain [text] if [emphasis]
 *  isn't actually found in it (defensive: the two always come from the same call site in
 *  `TimelineMapper.kt`'s `eventDetail`, but nothing enforces that at the type level). */
private fun emphasized(text: String, emphasis: String): AnnotatedString = buildAnnotatedString {
    val start = text.indexOf(emphasis)
    if (start < 0) {
        append(text)
        return@buildAnnotatedString
    }
    append(text.substring(0, start))
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(emphasis) }
    append(text.substring(start + emphasis.length))
}
