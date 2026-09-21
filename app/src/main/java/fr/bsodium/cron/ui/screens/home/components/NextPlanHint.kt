package fr.bsodium.cron.ui.screens.home.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.alarm.nextEveningPlanInstant
import fr.bsodium.cron.ui.components.CronIllustratedMessage
import fr.bsodium.cron.ui.components.CronIllustrationType
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

private val IllustrationSize = 180.dp

/**
 * The resting state shown once the current alarm is done (passed or dismissed) and no plan exists for
 * the next one yet: a themed illustration, a quiet note that no plan has run, plus when the nightly run
 * will fire. The play FAB remains the manual-run CTA. (First run shows [OnboardingHint] instead.)
 */
@Composable
internal fun NextPlanHint(
    autoAlarmsEnabled: Boolean,
    eveningTriggerTime: LocalTime,
    modifier: Modifier = Modifier,
) {
    CronIllustratedMessage(
        type = CronIllustrationType.Landscape,
        title = "No plan available yet",
        subtitle = nextPlanSubline(autoAlarmsEnabled, eveningTriggerTime),
        illustrationSize = IllustrationSize,
        modifier = modifier,
    )
}

@Composable
private fun nextPlanSubline(autoAlarmsEnabled: Boolean, eveningTriggerTime: LocalTime): String {
    if (!autoAlarmsEnabled) return "No plan available for your next alarm. Automatic planning is off — run one yourself."
    val tz = TimeZone.currentSystemDefault()
    // Tick each minute so "tonight" rolls to "tomorrow" once the trigger time passes.
    val now by produceState(Clock.System.now(), eveningTriggerTime) {
        while (true) {
            value = Clock.System.now()
            delay(60_000)
        }
    }
    val nextDate = nextEveningPlanInstant(eveningTriggerTime, now, tz).toLocalDateTime(tz).date
    val today = now.toLocalDateTime(tz).date
    val whenWord = if (nextDate == today) "tonight" else "tomorrow"
    // Locale.US for the clock readout (ASCII digits) per the LCD/clock formatting rule.
    val hhmm = String.format(Locale.US, "%02d:%02d", eveningTriggerTime.hour, eveningTriggerTime.minute)
    return "No plan available for your next alarm. The next plan will run $whenWord at $hhmm."
}

@Preview(showBackground = true, name = "Next plan — auto on")
@Composable
private fun NextPlanHintAutoOnPreview() {
    CronTheme {
        NextPlanHint(autoAlarmsEnabled = true, eveningTriggerTime = LocalTime(20, 0), modifier = Modifier.padding(Spacing.xl))
    }
}

@Preview(showBackground = true, name = "Next plan — auto off")
@Composable
private fun NextPlanHintAutoOffPreview() {
    CronTheme {
        NextPlanHint(autoAlarmsEnabled = false, eveningTriggerTime = LocalTime(20, 0), modifier = Modifier.padding(Spacing.xl))
    }
}
