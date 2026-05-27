package fr.bsodium.cron.ui.screens.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fr.bsodium.cron.FabRegistry
import fr.bsodium.cron.ui.components.FabAction
import fr.bsodium.cron.ui.screens.home.components.AiThinkingThread
import fr.bsodium.cron.ui.screens.home.components.GreetingHeader
import fr.bsodium.cron.ui.screens.home.components.NextAlarmCard

@Composable
fun HomeScreen(viewModel: HomeViewModel, fabRegistry: FabRegistry) {
    val uiState by viewModel.uiState.collectAsState()
    DisposableEffect(viewModel, uiState.isRetrying) {
        fabRegistry.set(
            FabAction(
                onClick = viewModel::retryAiPlan,
                spinning = uiState.isRetrying,
            )
        )
        onDispose { fabRegistry.clear() }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasNotificationPermission by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        GreetingHeader(
            prefix = uiState.greetingPrefix,
            name = uiState.greetingName,
            photoUrl = uiState.greetingPhotoUrl,
        )
        Spacer(Modifier.height(20.dp))
        NextAlarmCard(
            dateLabel = uiState.dateLabel,
            alarmTime = uiState.sessionDisplay?.alarmTime,
            sleepDurationLabel = uiState.sleepStats?.durationLabel,
            sleepSegments = uiState.sleepStats?.segments.orEmpty(),
        )
        Spacer(Modifier.height(24.dp))
        val thread = uiState.aiThread
        if (thread != null) {
            AiThinkingThread(thread)
        } else {
            AiThreadEmptyPlaceholder()
        }
        Spacer(Modifier.height(16.dp))

        if (!hasNotificationPermission) {
            NotificationPermissionRow(
                onEnable = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                    )
                },
            )
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AiThreadEmptyPlaceholder(modifier: Modifier = Modifier) {
    Text(
        text = "Tap ▶ to plan tomorrow's alarm from your calendar.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun NotificationPermissionRow(onEnable: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Notifications are off — Cron can't ring your alarm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEnable) { Text("Enable") }
        }
    }
}
