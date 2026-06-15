package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.theme.CronTheme

@Composable
fun AppSettingsScreen(
    hapticsEnabled: Boolean,
    onHapticsEnabled: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetailScaffold(title = "Preferences", onBack = onBack) {
        SwitchRow(
            title = "Haptic feedback",
            subtitle = "Ticks, clicks, and confirmations throughout the app",
            checked = hapticsEnabled,
            onCheckedChange = onHapticsEnabled,
            hapticsEnabled = hapticsEnabled,
        )
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 300)
@Composable
private fun AppSettingsScreenPreview() {
    CronTheme {
        AppSettingsScreen(hapticsEnabled = true, onHapticsEnabled = {}, onBack = {})
    }
}
