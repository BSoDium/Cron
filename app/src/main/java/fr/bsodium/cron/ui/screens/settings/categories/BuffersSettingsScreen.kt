package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.BufferSlider
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronTheme

@Composable
fun BuffersSettingsScreen(
    commuteBufferMinutes: Int,
    preparationBufferMinutes: Int,
    onCommuteBuffer: (Int) -> Unit,
    onPreparationBuffer: (Int) -> Unit,
    onBack: () -> Unit,
    hapticsEnabled: Boolean = true,
) {
    SettingsDetailScaffold(title = "Buffers", onBack = onBack) {
        BufferSlider(
            label = "Travel buffer",
            description = "Minimum commute time before the first event",
            value = commuteBufferMinutes,
            onChange = onCommuteBuffer,
            hapticsEnabled = hapticsEnabled,
        )
        BufferSlider(
            label = "Preparation time",
            description = "Shower, breakfast, getting dressed",
            value = preparationBufferMinutes,
            onChange = onPreparationBuffer,
            hapticsEnabled = hapticsEnabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BuffersSettingsScreenPreview() {
    CronTheme {
        BuffersSettingsScreen(
            commuteBufferMinutes = 15,
            preparationBufferMinutes = 30,
            onCommuteBuffer = {},
            onPreparationBuffer = {},
            onBack = {},
        )
    }
}
