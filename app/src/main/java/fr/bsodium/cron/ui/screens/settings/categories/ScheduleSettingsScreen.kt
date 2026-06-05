package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.TimePickerRow
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.datetime.LocalTime

@Composable
fun ScheduleSettingsScreen(
    eveningTrigger: LocalTime,
    hardLatest: LocalTime,
    onEveningTrigger: (LocalTime) -> Unit,
    onHardLatest: (LocalTime) -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetailScaffold(title = "Schedule", onBack = onBack) {
        TimePickerRow(
            label = "Evening trigger",
            description = "When Cron plans tonight's alarm",
            time = eveningTrigger,
            onTimeSelected = onEveningTrigger,
        )
        TimePickerRow(
            label = "Hard latest",
            description = "Absolute latest the alarm can fire",
            time = hardLatest,
            onTimeSelected = onHardLatest,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ScheduleSettingsScreenPreview() {
    CronTheme {
        ScheduleSettingsScreen(
            eveningTrigger = LocalTime(22, 0),
            hardLatest = LocalTime(10, 0),
            onEveningTrigger = {},
            onHardLatest = {},
            onBack = {},
        )
    }
}
