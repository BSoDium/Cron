package fr.bsodium.cron.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.ActionType
import fr.bsodium.cron.session.model.SessionStatus
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toJavaInstant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class SessionDisplayState(
    val status: SessionStatus,
    val action: ActionType,
    val alarmTime: LocalTime?,
    val reason: String,
    val sessionDate: LocalDate,
    val snoozeCount: Int,
)

/**
 * Hero card for the Home screen. Shows the AI-decided alarm time,
 * session status, and the model's reasoning in plain text.
 */
@Composable
fun SessionStatusCard(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state == null) {
                NoSessionContent()
            } else {
                SessionContent(state)
            }
        }
    }
}

@Composable
private fun NoSessionContent() {
    Text(
        text = "--:--",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Light,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "No active session",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SessionContent(state: SessionDisplayState) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d")

    if (state.alarmTime != null) {
        val zone = ZoneId.systemDefault()
        val alarmInstant = state.sessionDate
            .atStartOfDayIn(TimeZone.of(zone.id))
            .toJavaInstant()
            .atZone(zone)
            .withHour(state.alarmTime.hour)
            .withMinute(state.alarmTime.minute)

        Text(
            text = alarmInstant.format(timeFormatter),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = alarmInstant.format(dateFormatter),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Text(
            text = "--:--",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Planning alarm…",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(state.status)
        if (state.snoozeCount > 0) {
            SuggestionChip(
                onClick = {},
                label = { Text("Snoozed ×${state.snoozeCount}") },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    labelColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            )
        }
    }

}

@Composable
private fun StatusChip(status: SessionStatus) {
    val label = when (status) {
        SessionStatus.Planning -> "Planning"
        SessionStatus.Monitoring -> "Monitoring sleep"
        SessionStatus.Awake -> "Awake"
        SessionStatus.ReMonitoring -> "Re-monitoring"
        SessionStatus.Complete -> "Complete"
    }
    SuggestionChip(onClick = {}, label = { Text(label) })
}

/**
 * Chat-style card showing the AI's latest decision reasoning.
 * Displayed below the hero alarm card on the Home screen.
 */
@Composable
fun AiPlanCard(
    state: SessionDisplayState?,
    modifier: Modifier = Modifier,
) {
    val reason = state?.reason?.takeIf { it.isNotBlank() }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (reason != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Alarm,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Cron",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val actionLabel = when (state.action) {
                    ActionType.SetAlarm -> "Alarm scheduled"
                    ActionType.CancelAlarm -> "Alarm cancelled"
                    ActionType.SendBrief -> "Morning brief queued"
                    ActionType.DoNothing -> "No change needed"
                    ActionType.NotifyWarning -> "Warning issued"
                }
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            } else {
                Text(
                    text = "Cron will plan tonight's alarm at your evening trigger time.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
