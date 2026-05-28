package fr.bsodium.cron.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.components.ScreenTitle
import fr.bsodium.cron.ui.components.SectionLabel
import fr.bsodium.cron.ui.theme.Spacing
import kotlinx.datetime.LocalTime

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl)
            .padding(top = statusInsetTop + Spacing.xxl, bottom = navBottomInset + 96.dp),
    ) {
        ScreenTitle("Settings")
        Section(label = "Schedule") {
                TimePickerRow(
                    label = "Evening trigger",
                    description = "When Cron plans tonight's alarm",
                    time = state.eveningTrigger,
                    onTimeSelected = { viewModel.setEveningTrigger(it) },
                )
                TimePickerRow(
                    label = "Hard latest",
                    description = "Absolute latest the alarm can fire",
                    time = state.hardLatest,
                    onTimeSelected = { viewModel.setHardLatest(it) },
                )
            }

            Section(label = "Free days") {
                TimePickerRow(
                    label = "Wake window start",
                    description = "Earliest to wake on days with no events",
                    time = state.freeDayWakeStart,
                    onTimeSelected = { viewModel.setFreeDayWakeWindow(it, state.freeDayWakeEnd) },
                )
                TimePickerRow(
                    label = "Wake window end",
                    description = "Latest to wake on days with no events",
                    time = state.freeDayWakeEnd,
                    onTimeSelected = { viewModel.setFreeDayWakeWindow(state.freeDayWakeStart, it) },
                )
            }

            Section(label = "Buffers") {
                BufferSlider(
                    label = "Travel buffer",
                    description = "Minimum commute time before the first event",
                    value = state.commuteBufferMinutes,
                    onChange = viewModel::setCommuteBuffer,
                )
                BufferSlider(
                    label = "Preparation time",
                    description = "Shower, breakfast, getting dressed",
                    value = state.preparationBufferMinutes,
                    onChange = viewModel::setPreparationBuffer,
                )
            }

            Section(label = "Account") {
                val context = LocalContext.current
                GoogleSignInRow(
                    photoUrl = state.displayPhotoUrl,
                    displayName = state.displayName,
                    isSigningIn = state.isSigningIn,
                    error = state.signInError,
                    onSignIn = { viewModel.signInWithGoogle(context) },
                    onSignOut = viewModel::signOut,
                )
                DisplayNameRow(
                    name = state.displayName,
                    onSave = viewModel::setDisplayName,
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
                            text = if (state.hasApiKey) "Stored locally" else "Not configured",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.hasApiKey) {
                        TextButton(onClick = viewModel::clearApiKey) {
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

            Spacer(modifier = Modifier.height(40.dp))
        }
}

@Composable
private fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    Spacer(modifier = Modifier.height(28.dp))
    SectionLabel(text = label)
    Spacer(modifier = Modifier.height(14.dp))
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerRow(
    label: String,
    description: String,
    time: LocalTime,
    onTimeSelected: (LocalTime) -> Unit,
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
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "%02d:%02d".format(time.hour, time.minute),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (showDialog) {
        TimePickerDialog(
            initial = time,
            onDismiss = { showDialog = false },
            onConfirm = { newTime ->
                onTimeSelected(newTime)
                showDialog = false
            },
        )
    }
}

@Composable
private fun BufferSlider(
    label: String,
    description: String,
    value: Int,
    onChange: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "$value min",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..60f,
            steps = 11,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun GoogleSignInRow(
    photoUrl: String?,
    displayName: String?,
    isSigningIn: Boolean,
    error: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
) {
    val signedIn = !photoUrl.isNullOrBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Google profile",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = when {
                    signedIn && !displayName.isNullOrBlank() -> "Signed in as $displayName"
                    signedIn -> "Signed in"
                    else -> "Use your Google account for name + avatar"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        when {
            isSigningIn -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            signedIn -> TextButton(onClick = onSignOut) {
                Text("Sign out", color = MaterialTheme.colorScheme.primary)
            }
            else -> TextButton(onClick = onSignIn) {
                Text("Sign in", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DisplayNameRow(
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
            // Sign in / Clear buttons in the rows above and below.
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime(pickerState.hour, pickerState.minute)) }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
