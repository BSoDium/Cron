package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.session.model.TriggerType
import fr.bsodium.cron.ui.screens.home.RunKind
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/** Tab glyph for a run: the clock for the nightly base, play for a user-started base, else its trigger icon. */
internal fun runSymbol(kind: RunKind): MaterialSymbol = when (kind) {
    RunKind.ScheduledBase -> MaterialSymbol.Schedule
    RunKind.ManualBase -> MaterialSymbol.PlayArrow
    is RunKind.Replan -> triggerSymbol(kind.trigger)
}

/** The Material Symbol for a replan trigger. `null` is the original scheduled plan; `EveningPlan` is a
 *  user-run manual replan. The exhaustive `when` makes a new [TriggerType] a compile error. */
internal fun triggerSymbol(trigger: TriggerType?): MaterialSymbol = when (trigger) {
    null -> MaterialSymbol.Schedule
    TriggerType.EveningPlan -> MaterialSymbol.Autoplay
    TriggerType.CalendarChange -> MaterialSymbol.EventUpcoming
    TriggerType.SleepOnset -> MaterialSymbol.Bedtime
    TriggerType.HcStageUpdate -> MaterialSymbol.VitalSigns
    TriggerType.MidSleepActivity -> MaterialSymbol.Vibration
    TriggerType.OutOfBedConfirmed -> MaterialSymbol.DirectionsWalk
    TriggerType.WakeWindowOpportunity -> MaterialSymbol.LightMode
    TriggerType.AlarmSnoozed -> MaterialSymbol.Snooze
    TriggerType.AlarmDismissed -> MaterialSymbol.AlarmOff
    TriggerType.HardLatestFired -> MaterialSymbol.NotificationImportant
}

private fun triggerLabel(trigger: TriggerType?): String = when (trigger) {
    null -> "Planned (scheduled)"
    TriggerType.EveningPlan -> "Re-planned (manual)"
    TriggerType.CalendarChange -> "Your schedule changed"
    TriggerType.SleepOnset -> "You fell asleep"
    TriggerType.HcStageUpdate -> "Sleep stage update"
    TriggerType.MidSleepActivity -> "Movement mid-sleep"
    TriggerType.OutOfBedConfirmed -> "You got out of bed"
    TriggerType.WakeWindowOpportunity -> "A good moment to wake"
    TriggerType.AlarmSnoozed -> "You snoozed the alarm"
    TriggerType.AlarmDismissed -> "You dismissed the alarm"
    TriggerType.HardLatestFired -> "Hard-latest backstop fired"
}

@Preview(showBackground = true)
@Composable
private fun TriggerIconsPreview() {
    val triggers: List<TriggerType?> = listOf(null) + TriggerType.entries
    CronTheme {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            triggers.forEach { trigger ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Symbol(
                        symbol = triggerSymbol(trigger),
                        contentDescription = null,
                        size = 24.dp,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = triggerLabel(trigger),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
