package fr.bsodium.cron.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import fr.bsodium.cron.permissions.SystemPermissions
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.ui.components.ScreenTitle
import fr.bsodium.cron.ui.components.SectionLabel
import fr.bsodium.cron.ui.theme.Spacing
import java.util.Locale
import kotlinx.datetime.LocalTime

private val DIALOG_FIELD_MIN_HEIGHT = 120.dp

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
            .padding(top = statusInsetTop + Spacing.xxl, bottom = navBottomInset + Spacing.navBarClearance),
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

            Section(label = "Assistant") {
                CustomInstructionsRow(
                    instructions = state.userInstructions,
                    onSave = viewModel::setUserInstructions,
                )
            }

            Section(label = "Reliability") {
                ReliabilitySettings()
            }

            Section(label = "Account") {
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

            Section(label = "About") {
                val uriHandler = LocalUriHandler.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { uriHandler.openUri("https://storyset.com/food") },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Credits",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "Food illustrations by Storyset",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.xxxl + Spacing.sm))
        }
}

@Composable
private fun ReliabilitySettings() {
    val context = LocalContext.current
    val reader = remember { SleepStageReader(context) }
    val hcAvailable = remember { reader.availability() == SleepStageReader.Availability.Available }

    var bgLocation by remember { mutableStateOf(SystemPermissions.hasBackgroundLocation(context)) }
    var battery by remember { mutableStateOf(SystemPermissions.isIgnoringBatteryOptimizations(context)) }
    var exactAlarms by remember { mutableStateOf(SystemPermissions.canScheduleExactAlarms(context)) }
    var hcConnected by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { hcConnected = reader.hasSleepPermission() }

    val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        bgLocation = SystemPermissions.hasBackgroundLocation(context)
    }
    // Returning from a system settings screen re-checks the two that can't report a result directly.
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        battery = SystemPermissions.isIgnoringBatteryOptimizations(context)
        exactAlarms = SystemPermissions.canScheduleExactAlarms(context)
    }
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> hcConnected = granted.containsAll(reader.requiredPermissions) }

    ActionRow(
        title = "Background location",
        subtitle = "Refresh your location for the nightly plan while the app is closed",
        done = bgLocation,
        actionLabel = "Allow",
        enabled = SystemPermissions.hasForegroundLocation(context),
        onClick = { bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
    )
    ActionRow(
        title = "Battery optimization",
        subtitle = "Let Cron run overnight without being killed",
        done = battery,
        actionLabel = "Disable",
        enabled = true,
        onClick = { settingsLauncher.launch(SystemPermissions.batteryOptimizationIntent(context)) },
    )
    ActionRow(
        title = "Exact alarms",
        subtitle = "Fire the alarm at the precise planned time",
        done = exactAlarms,
        actionLabel = "Allow",
        enabled = true,
        onClick = { settingsLauncher.launch(SystemPermissions.exactAlarmSettingsIntent(context)) },
    )
    ActionRow(
        title = "Health Connect",
        subtitle = if (hcAvailable) "Use wearable sleep stages for smarter wake timing"
            else "Install Health Connect to use wearable sleep data",
        done = hcConnected,
        actionLabel = "Connect",
        enabled = hcAvailable,
        onClick = { hcLauncher.launch(reader.requiredPermissions) },
    )
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String,
    done: Boolean,
    actionLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
        if (done) {
            Text(
                text = "Enabled",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        } else {
            TextButton(onClick = onClick, enabled = enabled) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Section(
    label: String,
    content: @Composable () -> Unit,
) {
    Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.xs))
    SectionLabel(text = label)
    Spacer(modifier = Modifier.height(Spacing.md + Spacing.xxs))
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
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
            text = String.format(Locale.US, "%02d:%02d", time.hour, time.minute),
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
        Spacer(modifier = Modifier.height(Spacing.sm))
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

@Composable
private fun CustomInstructionsRow(
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
