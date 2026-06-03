package fr.bsodium.cron.ui.screens.settings.categories

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import fr.bsodium.cron.permissions.SystemPermissions
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.ui.screens.settings.components.ActionRow
import fr.bsodium.cron.ui.screens.settings.components.SettingsDetailScaffold

// No @Preview: this screen reads live system-permission state and wires Activity-result launchers,
// which the preview renderer can't supply. The individual ActionRows are previewed in ActionRow.kt.
@Composable
fun ReliabilitySettingsScreen(onBack: () -> Unit) {
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

    SettingsDetailScaffold(title = "Reliability", onBack = onBack) {
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
}
