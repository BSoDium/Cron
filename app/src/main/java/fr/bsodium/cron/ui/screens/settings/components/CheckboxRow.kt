package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.bleedHorizontally
import fr.bsodium.cron.ui.components.rememberCronHaptics
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.MaterialSymbol
import fr.bsodium.cron.ui.theme.Spacing
import fr.bsodium.cron.ui.theme.Symbol

/** A title/subtitle row with a leading [Checkbox] and an optional [icon]; the whole row is clickable. */
@Composable
internal fun CheckboxRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: MaterialSymbol? = null,
    hapticsEnabled: Boolean = true,
) {
    val haptics = rememberCronHaptics(enabled = hapticsEnabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .bleedHorizontally(Spacing.xl)
            .clickable { haptics.contextClick(); onCheckedChange(!checked) }
            .padding(horizontal = Spacing.xl, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Symbol(
                symbol = icon,
                contentDescription = null,
                size = 32.dp,
                weight = 300,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.lg))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun CheckboxRowPreview() {
    CronTheme {
        Column {
            CheckboxRow(title = "Drive", subtitle = "Estimate by car", checked = true, icon = MaterialSymbol.DirectionsCar, onCheckedChange = {})
            CheckboxRow(title = "Transit", subtitle = "Estimate by bus, tram, or train", checked = false, icon = MaterialSymbol.DirectionsTransit, onCheckedChange = {})
            CheckboxRow(title = "No icon", subtitle = "Fallback without icon", checked = true, onCheckedChange = {})
        }
    }
}
