package fr.bsodium.cron.ui.screens.settings.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.Spacing

private val DIALOG_FIELD_MIN_HEIGHT = 120.dp

/** Clickable row showing the current display name; opens a single-line editor dialog. */
@Composable
internal fun DisplayNameRow(
    name: String?,
    onSave: (String) -> Unit,
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
                text = "Display name",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Shown in the morning greeting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = name ?: "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (name != null) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            // Match TextButton content padding so the value aligns with the
            // Sign in / Clear buttons in the rows below.
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }

    if (showDialog) {
        var draft by remember { mutableStateOf(name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Display name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    label = { Text("Your name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(draft.trim())
                        showDialog = false
                    },
                    enabled = draft.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}

/** Clickable row showing whether custom instructions are set; opens a multi-line editor dialog. */
@Composable
internal fun CustomInstructionsRow(
    instructions: String?,
    onSave: (String) -> Unit,
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
                text = "Custom instructions",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Extra guidance sent to the planner every time",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (!instructions.isNullOrBlank()) "Set" else "—",
            style = MaterialTheme.typography.titleMedium,
            color = if (!instructions.isNullOrBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
        )
    }

    if (showDialog) {
        var draft by remember { mutableStateOf(instructions.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Custom instructions") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    minLines = 3,
                    maxLines = 8,
                    label = { Text("Tell Cron how to plan your wake-ups") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = DIALOG_FIELD_MIN_HEIGHT),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(draft.trim())
                        showDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancel") }
            },
        )
    }
}
