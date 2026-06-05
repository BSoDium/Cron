package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.ui.screens.settings.components.CustomInstructionsRow
import fr.bsodium.cron.ui.screens.settings.components.DailyBudgetRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.theme.CronTheme

@Composable
fun AssistantSettingsScreen(
    userInstructions: String?,
    dailyTokenLimit: Int,
    tokensUsedToday: Int,
    hapticsEnabled: Boolean,
    onUserInstructions: (String) -> Unit,
    onDailyTokenLimit: (Int) -> Unit,
    onHapticsEnabled: (Boolean) -> Unit,
    onRefreshUsage: () -> Unit,
    onBack: () -> Unit,
) {
    // Token spend is read from SharedPreferences, not observed — refresh whenever the screen resumes
    // (e.g. after a turn ran while the app was backgrounded) so "used today" stays current.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshUsage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDetailScaffold(title = "Assistant", onBack = onBack) {
        CustomInstructionsRow(
            instructions = userInstructions,
            onSave = onUserInstructions,
        )
        DailyBudgetRow(
            limit = dailyTokenLimit,
            usedToday = tokensUsedToday,
            onSelect = onDailyTokenLimit,
        )
        SwitchRow(
            title = "Haptic feedback",
            subtitle = "Subtle ticks while the assistant writes",
            checked = hapticsEnabled,
            onCheckedChange = onHapticsEnabled,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AssistantSettingsScreenPreview() {
    CronTheme {
        AssistantSettingsScreen(
            userInstructions = "Prefer earlier wake-ups on gym days.",
            dailyTokenLimit = BudgetStore.DEFAULT_DAILY_TOKEN_LIMIT,
            tokensUsedToday = 12_400,
            hapticsEnabled = true,
            onUserInstructions = {},
            onDailyTokenLimit = {},
            onHapticsEnabled = {},
            onRefreshUsage = {},
            onBack = {},
        )
    }
}
