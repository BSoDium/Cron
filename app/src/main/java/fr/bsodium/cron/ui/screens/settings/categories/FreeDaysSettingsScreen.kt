package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.TimePickerRow
import fr.bsodium.cron.ui.theme.CronTheme
import kotlinx.datetime.LocalTime

@Composable
fun FreeDaysSettingsScreen(
    wakeStart: LocalTime,
    wakeEnd: LocalTime,
    onWakeStart: (LocalTime) -> Unit,
    onWakeEnd: (LocalTime) -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetailScaffold(title = "Free days", onBack = onBack) {
        TimePickerRow(
            label = "Wake window start",
            description = "Earliest to wake on days with no events",
            time = wakeStart,
            onTimeSelected = onWakeStart,
        )
        TimePickerRow(
            label = "Wake window end",
            description = "Latest to wake on days with no events",
            time = wakeEnd,
            onTimeSelected = onWakeEnd,
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 300)
@Composable
private fun FreeDaysSettingsScreenPreview() {
    CronTheme {
        FreeDaysSettingsScreen(
            wakeStart = LocalTime(8, 0),
            wakeEnd = LocalTime(9, 30),
            onWakeStart = {},
            onWakeEnd = {},
            onBack = {},
        )
    }
}
