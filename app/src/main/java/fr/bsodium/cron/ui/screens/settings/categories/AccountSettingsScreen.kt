package fr.bsodium.cron.ui.screens.settings.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.screens.settings.components.DisplayNameRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

@Composable
fun AccountSettingsScreen(
    displayName: String?,
    hasApiKey: Boolean,
    onDisplayName: (String) -> Unit,
    onClearApiKey: () -> Unit,
    onBack: () -> Unit,
) {
    SettingsDetailScaffold(title = "Account", onBack = onBack) {
        DisplayNameRow(
            name = displayName,
            onSave = onDisplayName,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Anthropic API key",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = if (hasApiKey) "Stored locally" else "Not configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (hasApiKey) {
                TextButton(onClick = onClearApiKey) {
                    Text(
                        text = "Clear",
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Text(
                    text = "—",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 300)
@Composable
private fun AccountSettingsScreenPreview() {
    CronTheme {
        AccountSettingsScreen(
            displayName = "Elliot",
            hasApiKey = true,
            onDisplayName = {},
            onClearApiKey = {},
            onBack = {},
        )
    }
}
