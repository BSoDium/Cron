package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.engine.model.ScheduledAlarm
import fr.bsodium.cron.engine.model.SyncResult
import fr.bsodium.cron.engine.model.TravelInfo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hero card showing the next scheduled alarm prominently.
 * Shows the alarm time, the target event, and a countdown.
 */
@Composable
fun AlarmCard(
    alarm: ScheduledAlarm?,
    status: SyncResult.Status,
    modifier: Modifier = Modifier,
    travelInfo: TravelInfo? = null
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (alarm != null) {
                val zone = ZoneId.systemDefault()
                val alarmTime = alarm.triggerTime.atZone(zone)
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

                // Large alarm time
                Text(
                    text = alarmTime.format(timeFormatter),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Date
                Text(
                    text = alarmTime.format(dateFormatter),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Event label
                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Travel info section
                TravelInfoSection(travelInfo)

                Spacer(modifier = Modifier.height(8.dp))

                // Countdown
                val now = Instant.now()
                val duration = Duration.between(now, alarm.triggerTime)
                if (!duration.isNegative) {
                    val hours = duration.toHours()
                    val minutes = duration.toMinutes() % 60
                    val countdownText = when {
                        hours > 0 -> "in ${hours}h ${minutes}min"
                        else -> "in ${minutes}min"
                    }
                    Text(
                        text = countdownText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // No alarm state
                Text(
                    text = "--:--",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                val message = when (status) {
                    SyncResult.Status.NO_EVENTS -> "No events found for tomorrow"
                    SyncResult.Status.ALARM_TOO_LATE -> "First event is too late for an alarm"
                    SyncResult.Status.ALARM_IN_PAST -> "Alarm time has already passed"
                    SyncResult.Status.DISABLED -> "Automatic alarms are disabled"
                    else -> "No alarm scheduled"
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Shows travel time status below the alarm info.
 *
 * - Success: "X min drive" with the destination
 * - Skipped: reason why (no API key, no GPS, no event location)
 * - Error: API error message
 */
@Composable
private fun TravelInfoSection(travelInfo: TravelInfo?) {
    if (travelInfo == null) return

    Spacer(modifier = Modifier.height(6.dp))

    if (travelInfo.isSuccess) {
        val minutes = travelInfo.travelTime!!.toMinutes()
        Text(
            text = "\uD83D\uDE97 $minutes min drive",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (travelInfo.eventLocation != null) {
            Text(
                text = "to ${travelInfo.eventLocation}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    } else if (!travelInfo.wasAttempted) {
        // Preconditions not met — show why
        val reason = when {
            !travelInfo.hasApiKey -> "No routing API key configured"
            !travelInfo.hasDeviceLocation -> "Device location unavailable"
            !travelInfo.hasEventLocation -> "Event has no location"
            else -> "Routing skipped"
        }
        Text(
            text = "Prep time only \u2014 $reason",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    } else {
        // API was called but failed
        Text(
            text = "Prep time only \u2014 route lookup failed",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
        )
        if (travelInfo.error != null) {
            Text(
                text = travelInfo.error,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.5f),
                maxLines = 2
            )
        }
    }
}
