package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.CronTheme
import fr.bsodium.cron.ui.theme.Radius
import fr.bsodium.cron.ui.theme.Spacing
import java.util.Locale

private val BUDGET_ROW_MIN_HEIGHT = 48.dp

/** Daily AI token-cap presets shown in the budget dialog; 0 means unlimited (cap disabled). */
private val BUDGET_PRESETS = listOf(100_000, 250_000, 500_000, 0)

/** Clickable row showing the current daily token cap + today's usage; opens a preset picker. */
@Composable
internal fun DailyBudgetRow(
    limit: Int,
    usedToday: Int,
    onSelect: (Int) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Daily AI budget",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = String.format(Locale.US, "%,d used today", usedToday),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatTokenLimit(limit),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Daily AI budget") },
            text = {
                Column {
                    BUDGET_PRESETS.forEach { preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable {
                                    onSelect(preset)
                                    showDialog = false
                                }
                                .heightIn(min = BUDGET_ROW_MIN_HEIGHT),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = preset == limit,
                                onClick = {
                                    onSelect(preset)
                                    showDialog = false
                                },
                            )
                            Text(
                                text = formatTokenLimit(preset),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** "250,000 tokens" for a finite cap, "Unlimited" when disabled (0 or less). */
private fun formatTokenLimit(tokens: Int): String =
    if (tokens <= 0) "Unlimited" else String.format(Locale.US, "%,d tokens", tokens)

@Preview(showBackground = true, widthDp = 412)
@Composable
private fun DailyBudgetRowPreview() {
    CronTheme {
        DailyBudgetRow(limit = 250_000, usedToday = 12_480, onSelect = {})
    }
}
