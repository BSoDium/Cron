package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Spacing

/** A title/subtitle row with a trailing [Switch] for a boolean preference. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hapticsEnabled: Boolean = true,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(checked = checked, onCheckedChange = { v -> haptics.contextClick(); onCheckedChange(v) })
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun SwitchRowPreview() {
    CronTheme {
        SwitchRow(
            title = "Haptic feedback",
            subtitle = "Subtle ticks while the assistant writes",
            checked = true,
            onCheckedChange = {},
        )
    }
}
