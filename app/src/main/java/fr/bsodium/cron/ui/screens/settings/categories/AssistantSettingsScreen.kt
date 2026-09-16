package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.ui.screens.settings.components.DailyBudgetRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronTheme

@Composable
fun AssistantSettingsScreen(
    dailyTokenLimit: Int,
    tokensUsedToday: Int,
    onDailyTokenLimit: (Int) -> Unit,
    onRefreshUsage: () -> Unit,
    onBack: () -> Unit,
) {
    // Token spend is read from SharedPreferences, not observed — refresh on resume (e.g. after a backgrounded turn) so "used today" stays current.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshUsage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsDetailScaffold(title = "Assistant", onBack = onBack) {
        DailyBudgetRow(
            limit = dailyTokenLimit,
            usedToday = tokensUsedToday,
            onSelect = onDailyTokenLimit,
        )
        DebugSettingsSection()
    }
}

@Preview(showBackground = true, widthDp = 480, heightDp = 300, fontScale = 1.0f)
@Composable
private fun AssistantSettingsScreenPreview() {
    CronTheme {
        AssistantSettingsScreen(
            dailyTokenLimit = BudgetStore.DEFAULT_DAILY_TOKEN_LIMIT,
            tokensUsedToday = 12_400,
            onDailyTokenLimit = {},
            onRefreshUsage = {},
            onBack = {},
        )
    }
}
