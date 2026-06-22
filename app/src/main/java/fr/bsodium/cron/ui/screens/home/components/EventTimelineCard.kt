package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.CronTypography
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

private val EVENT_ICON_SIZE = 16.dp

@Composable
private fun eventFirstLineHeight(): Dp {
    val density = LocalDensity.current
    return with(density) { MaterialTheme.typography.bodyMedium.lineHeight.toDp() }
}

@Composable
internal fun EventTimelineCard(
    trigger: TriggerType,
    label: String,
    detail: String?,
    timestamp: Instant,
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tz = TimeZone.currentSystemDefault()
    val local = timestamp.toLocalDateTime(tz)
    val timeText = String.format(Locale.US, "%02d:%02d", local.hour, local.minute)

    SessionTimelineEventRow(
        firstLineHeight = eventFirstLineHeight(),
        isFirst = isFirst,
        isLast = isLast,
        icon = {
            Symbol(
                symbol = triggerSymbol(trigger),
                contentDescription = null,
                tint = contentColor,
                size = EVENT_ICON_SIZE,
            )
        },
    ) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (detail != null) {
                MonoPill(text = detail)
            }
            Text(
                text = timeText,
                style = CronTypography.labelMonoSmall,
                color = contentColor,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EventTimelineCardPreview() {
    CronTheme {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            EventTimelineCard(
                trigger = TriggerType.SleepOnset,
                label = "You fell asleep",
                detail = null,
                timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                isFirst = true,
                isLast = false,
            )
            EventTimelineCard(
                trigger = TriggerType.AlarmSnoozed,
                label = "Alarm snoozed",
                detail = "9 min",
                timestamp = Instant.fromEpochMilliseconds(System.currentTimeMillis()),
                isFirst = false,
                isLast = true,
            )
        }
    }
}
