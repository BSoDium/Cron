package fr.bsodium.cron.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.ui.theme.Spacing

private val GATE_ICON_SIZE = 64.dp

/**
 * Gates the main content behind required permissions.
 *
 * If READ_CALENDAR is not granted, shows a centered prompt
 * explaining why the permission is needed and a button to request it.
 * Also requests POST_NOTIFICATIONS on Android 13+ and ACCESS_COARSE_LOCATION
 * for travel time estimation.
 *
 * @param hasCalendarPermission Whether READ_CALENDAR is currently granted.
 * @param hasNotificationPermission Whether POST_NOTIFICATIONS is currently granted.
 * @param hasLocationPermission Whether ACCESS_COARSE_LOCATION is currently granted.
 * @param onPermissionsResult Callback when permissions are granted/denied.
 * @param content The main UI to show when permissions are granted.
 */
@Composable
fun PermissionGate(
    hasCalendarPermission: Boolean,
    hasNotificationPermission: Boolean,
    hasLocationPermission: Boolean = false,
    onPermissionsResult: (calendarGranted: Boolean, notificationGranted: Boolean, locationGranted: Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    if (hasCalendarPermission) {
        content()
        return
    }

    var hasRequestedOnce by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasRequestedOnce = true
        val calendarGranted = permissions[Manifest.permission.READ_CALENDAR] ?: false
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }
        val locationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        onPermissionsResult(calendarGranted, notificationGranted, locationGranted)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxxl),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Outlined.CalendarMonth,
            contentDescription = null,
            modifier = Modifier.size(GATE_ICON_SIZE),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))

        Text(
            text = "Calendar access needed",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(Spacing.md))

        Text(
            text = "Cron needs to read your calendar to automatically schedule wake-up alarms before your first event each day.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(Spacing.xxxl))

        if (!hasRequestedOnce) {
            Button(
                onClick = {
                    val permissions = buildList {
                        add(Manifest.permission.READ_CALENDAR)
                        add(Manifest.permission.ACCESS_COARSE_LOCATION)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    permissionLauncher.launch(permissions.toTypedArray())
                }
            ) {
                Text("Grant Permission")
            }
        } else {
            // Permission was denied — show "Open Settings" button
            Text(
                text = "Permission was denied. You can grant it in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(Spacing.lg))

            OutlinedButton(
                onClick = {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                    context.startActivity(intent)
                }
            ) {
                Text("Open Settings")
            }
        }
    }
}
