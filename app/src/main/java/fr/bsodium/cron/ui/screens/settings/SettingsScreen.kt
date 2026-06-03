package fr.bsodium.cron.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.permissions.SystemPermissions
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.ui.components.ScreenTitle
import fr.bsodium.cron.ui.screens.settings.components.ActionRow
import fr.bsodium.cron.ui.screens.settings.components.BufferSlider
import fr.bsodium.cron.ui.screens.settings.components.CustomInstructionsRow
import fr.bsodium.cron.ui.screens.settings.components.DailyBudgetRow
import fr.bsodium.cron.ui.screens.settings.components.DisplayNameRow
import fr.bsodium.cron.ui.screens.settings.components.Section
import fr.bsodium.cron.ui.screens.settings.components.SwitchRow
import fr.bsodium.cron.ui.screens.settings.components.TimePickerRow
import fr.bsodium.cron.ui.theme.Spacing

@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.uiState.collectAsState()
    val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusInsetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Token spend is read from SharedPreferences, not observed — refresh it whenever the screen resumes
    // (e.g. after a turn ran while the app was backgrounded) so the "used today" figure stays current.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshUsage()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
            DailyBudgetRow(
                limit = state.dailyTokenLimit,
                usedToday = state.tokensUsedToday,
                onSelect = viewModel::setDailyTokenLimit,
            )
            SwitchRow(
                title = "Haptic feedback",
                subtitle = "Subtle ticks while the assistant writes",
                checked = state.hapticsEnabled,
                onCheckedChange = viewModel::setHapticsEnabled,
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
