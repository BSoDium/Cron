@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.R
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronColors
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** `wght`/`wdth` for [DayHeaderLabel]'s big day-of-month digit — Roboto Flex is a genuine variable
 *  font (the same file [fr.bsodium.cron.ui.theme.CountdownFontFamily] already draws on), pinned to a
 *  bold, clearly-condensed-but-not-crushed pair. Every header renders at this one fixed pair — no
 *  active/inactive distinction to interpolate between. */
private const val NUMBER_WEIGHT = 900
private const val NUMBER_WIDTH = 42f

/** Vertical squash applied to the big day-of-month digit (Round 26 — "flatten it vertically") via
 *  `graphicsLayer`, the same paint-time-only scale technique `CollapsibleAlarmCard`'s clock digits
 *  already use elsewhere in this app. Purely visual — [CronTypography.timelineDayNumber]'s own
 *  `lineHeight` is separately tightened so the row's layout height keeps pace with the now
 *  shorter-*looking* glyph instead of leaving dead space beneath it. */
private const val NUMBER_VERTICAL_SQUASH = 0.8f

/** Height of the wavy divider's own container — enough room for its amplitude to read as a visible
 *  wiggle without ballooning into a thick decorative band. */
private val DIVIDER_HEIGHT = 6.dp

/** Distance between wave peaks for the divider below — shorter than
 *  [WavyProgressIndicatorDefaults.LinearDeterminateWavelength] (40dp, tuned for a full-width progress
 *  bar) so a few full waves still read across this narrower, block-width divider. */
private val DIVIDER_WAVELENGTH = 20.dp

private val numberFontFamily: FontFamily = FontFamily(
    Font(
        R.font.roboto_flex,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(NUMBER_WEIGHT),
            FontVariation.Setting("wdth", NUMBER_WIDTH),
        ),
    ),
)

/** The day-boundary label itself — a calendar-tear-off pairing: a colossal bold day-of-month digit,
 *  vertically flattened, with the full month name and a relative-day label sharing the line
 *  underneath it — month right-aligned under the number, the relative label opposite it on the far
 *  left — closed off by a thin wavy divider matching the block's own width, "an underline under the
 *  month text," not a full-width rule (that would read as splitting the timeline itself;
 *  [DayHeaderRow] confines this whole block to the gutter's right). Every header renders identically,
 *  no per-frame scroll tracking. */
@Composable
internal fun DayHeaderLabel(date: LocalDate, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Column(modifier = modifier) {
        Text(
            text = dayOfMonthLabel(date),
            style = CronTypography.timelineDayNumber.copy(fontFamily = numberFontFamily),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .align(Alignment.End)
                // Bottom-anchored (not center) so the squash doesn't leave symmetric dead space above and below the glyph; CronTypography.timelineDayNumber's tightened lineHeight controls the remaining gap below.
                .graphicsLayer { scaleY = NUMBER_VERTICAL_SQUASH; transformOrigin = TransformOrigin(0.5f, 1f) },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = relativeDayLabel(date),
                style = CronTypography.timelineDayActiveLabel,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = monthLabel(date),
                style = CronTypography.timelineDayMonth,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A real Material 3 Expressive component, not a hand-rolled wave (see docs/color-roles.md) — pinned at progress=1 draws one continuous wavy line across its own width; amplitude is forced constant (the default fades near 100% progress) and waveSpeed=0 keeps it static, avoiding a second perpetually-running animation.
        LinearWavyProgressIndicator(
            progress = { 1f },
            modifier = Modifier
                .padding(top = Spacing.xs)
                .fillMaxWidth()
                .height(DIVIDER_HEIGHT),
            color = MaterialTheme.colorScheme.outlineVariant,
            trackColor = MaterialTheme.colorScheme.outlineVariant,
            stroke = Stroke(width = with(density) { 1.dp.toPx() }, cap = StrokeCap.Round),
            stopSize = 0.dp,
            amplitude = { 1f },
            wavelength = DIVIDER_WAVELENGTH,
            waveSpeed = 0.dp,
        )
    }
}

/** Day-boundary heading, rendered as a plain in-flow `sessionTimelineItems` row — no anchor of its
 *  own (unlike an `Event`/`AiRun` row), no special pin behavior, no backdrop. Reserves [NODE_GUTTER]
 *  on the left — without it, [DayHeaderLabel]'s divider/content could extend far enough left to
 *  visually overlap the timeline track itself, since this row otherwise has nothing else forcing
 *  that reservation the way every anchor row's own gutter `Box` does — so the whole label block,
 *  divider included, stays confined to the content area right of the track. Every header renders
 *  identically, with no live "which section am I in" tracking — an earlier pinned-overlay/active-day
 *  design was dropped after live testing found it glitchy and a genuine on-device performance cost,
 *  with no cheaper way to keep the same tracking. */
@Composable
internal fun DayHeaderRow(item: TimelineItem.DayHeader, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // end = Spacing.md matches TimelineNode's own row inset — without it, the right-aligned number/month would sit closer to the screen edge than every anchor row's own trailing time label.
            .padding(top = Spacing.xs, bottom = Spacing.sm, end = Spacing.md),
    ) {
        // NODE_GUTTER alone would start the label flush with the gutter's own right edge; clearing titleSpacer (Spacing.md, TimelineNode.kt) past that lines this label's text up with the rest of the timeline's text indent.
        Spacer(Modifier.width(NODE_GUTTER + Spacing.md))
        DayHeaderLabel(date = item.date, modifier = Modifier.weight(1f))
    }
}

// locale-default full month name is intentional here (human-language)
private fun monthLabel(date: LocalDate): String =
    date.toJavaLocalDate().format(DateTimeFormatter.ofPattern("MMMM", Locale.getDefault())).uppercase(Locale.getDefault())

private fun dayOfMonthLabel(date: LocalDate): String = date.dayOfMonth.toString()

/** "Today"/"Tomorrow"/"Two days ago" for a date near now, falling back to the locale-default weekday
 *  name beyond that window — shared with [DayHeaderLabel]'s month line. Also looks *forward*
 *  (`-1L -> "Tomorrow"`) since a session can span into a future-looking plan entry. Every branch
 *  (including the locale weekday name) is already naturally capitalized, so this never uppercases
 *  the result. */
private fun relativeDayLabel(date: LocalDate): String {
    val today = java.time.LocalDate.now()
    val javaDate = date.toJavaLocalDate()
    return when (ChronoUnit.DAYS.between(javaDate, today)) {
        0L -> "Today"
        1L -> "Yesterday"
        2L -> "Two days ago"
        -1L -> "Tomorrow"
        // locale-default weekday name is intentional here (human-language); any other day gap (unbounded)
        else -> javaDate.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
    }
}

@PreviewLightDark
@Composable
private fun DayHeaderRowPreview() {
    CronTheme {
        Column(modifier = Modifier.fillMaxSize().background(CronColors.pageBackground)) {
            DayHeaderRow(
                item = TimelineItem.DayHeader(date = LocalDate(2026, 7, 3), timestamp = Instant.fromEpochMilliseconds(0)),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            DayHeaderRow(
                item = TimelineItem.DayHeader(date = LocalDate(2026, 6, 30), timestamp = Instant.fromEpochMilliseconds(0)),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}
