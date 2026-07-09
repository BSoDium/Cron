package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.home.TimelineItem
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

/** Day-boundary heading between timeline sections — an oversized wide word ("TODAY") outdented past
 *  the node gutter, paired with a small mono date figure, so it reads as its own display beat rather
 *  than another row in the gutter-aligned list below it. */
@Composable
internal fun DayHeaderRow(item: TimelineItem.DayHeader, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = Spacing.xxl, bottom = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        // alignByBaseline, not verticalAlignment = Bottom — the two faces have different descender depths, so box-bottom alignment leaves their baselines visibly offset.
        Text(
            text = item.weekdayLabel,
            style = CronTypography.timelineDayHeader,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline(),
        )
        Text(
            text = item.dateLabel,
            style = CronTypography.labelMonoSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline(),
        )
    }
}

@Preview(showBackground = true, name = "Day header")
@Composable
private fun DayHeaderRowPreview() {
    CronTheme {
        Column {
            DayHeaderRow(
                item = TimelineItem.DayHeader(
                    date = LocalDate(2026, 7, 3),
                    timestamp = Instant.fromEpochMilliseconds(0),
                    weekdayLabel = "TODAY",
                    dateLabel = "3 JUL",
                ),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
            DayHeaderRow(
                item = TimelineItem.DayHeader(
                    date = LocalDate(2026, 6, 30),
                    timestamp = Instant.fromEpochMilliseconds(0),
                    weekdayLabel = "YESTERDAY",
                    dateLabel = "30 JUN",
                ),
                modifier = Modifier.padding(horizontal = Spacing.md),
            )
        }
    }
}
