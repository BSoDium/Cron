package fr.bsodium.cron.ui.screens.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import fr.bsodium.cron.permissions.SystemPermissions
import fr.bsodium.cron.sensors.healthconnect.SleepStageReader
import fr.bsodium.cron.ui.theme.Spacing

private val WELCOME_ICON_SIZE = 72.dp
private val DONE_ICON_SIZE = 64.dp

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()

    val stepIndex = state.step.ordinal
    val totalSteps = OnboardingStep.entries.size - 1 // exclude Done

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = Spacing.xxxl),
        ) {
            Spacer(modifier = Modifier.height(Spacing.lg))

            if (state.step != OnboardingStep.Done) {
                LinearProgressIndicator(
                    progress = { stepIndex.toFloat() / totalSteps },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(Spacing.xxxl))
            }

            when (state.step) {
                OnboardingStep.Welcome -> WelcomeStep(onNext = viewModel::advance)
                OnboardingStep.Name -> NameStep(state = state, viewModel = viewModel)
                OnboardingStep.ApiKey -> ApiKeyStep(state = state, viewModel = viewModel)
                OnboardingStep.Permissions -> PermissionsStep(
                    onGranted = {
                        viewModel.onPermissionsResult(true)
                        viewModel.advance()
                    },
                    onSkip = {
                        viewModel.onPermissionsResult(false)
                        viewModel.advance()
                    },
                )
                OnboardingStep.Reliability -> ReliabilityStep(onContinue = viewModel::advance)
                OnboardingStep.Done -> DoneStep(onFinish = {
                    viewModel.complete()
                    onComplete()
                })
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Alarm,
            contentDescription = null,
            modifier = Modifier.size(WELCOME_ICON_SIZE),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        Text(
            text = "Meet Cron",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "Cron watches your calendar and sleep patterns overnight, then wakes you at the ideal moment before your first appointment — automatically planned by Claude.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxxl + Spacing.sm))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
            Text("Get started")
        }
    }
}

@Composable
private fun NameStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("What should Cron call you?", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Your name appears in the morning greeting. Stored on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))

        OutlinedTextField(
            value = state.displayNameInput,
            onValueChange = viewModel::onDisplayNameChanged,
            label = { Text("Your name") },
            placeholder = { Text("Elliot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = viewModel::saveDisplayName,
            enabled = state.displayNameInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ApiKeyStep(
    state: OnboardingUiState,
    viewModel: OnboardingViewModel,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Anthropic API key", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Cron uses Claude to plan your alarm. Paste your API key below — it's stored encrypted on this device and never leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))

        OutlinedTextField(
            value = state.apiKeyInput,
            onValueChange = viewModel::onApiKeyChanged,
            label = { Text("API key") },
            placeholder = { Text("sk-ant-…") },
            isError = state.keyError != null,
            supportingText = state.keyError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(Spacing.lg))
        Button(
            onClick = viewModel::saveApiKey,
            enabled = state.apiKeyInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save key & continue")
        }
    }
}

@Composable
private fun PermissionsStep(
    onGranted: () -> Unit,
    onSkip: () -> Unit,
) {
    val permissions = buildList {
        add(Manifest.permission.READ_CALENDAR)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACTIVITY_RECOGNITION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val calendarGranted = results[Manifest.permission.READ_CALENDAR] == true
        if (calendarGranted) onGranted() else onSkip()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Permissions", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Cron needs the following to work:\n\n" +
                "• Calendar — to read tomorrow's appointments\n" +
                "• Location — to estimate your commute time once each evening\n" +
                "• Notifications — to show and dismiss the alarm\n" +
                "• Activity Recognition — to detect when you're out of bed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        Button(
            onClick = { launcher.launch(permissions.toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant permissions")
        }
        Spacer(modifier = Modifier.height(Spacing.sm))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun ReliabilityStep(onContinue: () -> Unit) {
    val context = LocalContext.current
    val reader = remember { SleepStageReader(context) }

    var bgLocationGranted by remember { mutableStateOf(SystemPermissions.hasBackgroundLocation(context)) }
    var batteryExempt by remember { mutableStateOf(SystemPermissions.isIgnoringBatteryOptimizations(context)) }
    var hcConnected by remember { mutableStateOf(false) }

    val bgLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { bgLocationGranted = SystemPermissions.hasBackgroundLocation(context) }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { batteryExempt = SystemPermissions.isIgnoringBatteryOptimizations(context) }
    val hcLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted -> hcConnected = granted.containsAll(reader.requiredPermissions) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Reliable overnight", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "These let Cron refresh your location and run the plan while you sleep — without " +
                "opening the app. All optional, and you can change them later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))

        ReliabilityButton(
            title = if (bgLocationGranted) "Background location enabled" else "Allow location all the time",
            subtitle = "Refreshes your commute origin overnight. Tap, then pick \"Allow all the time\".",
            done = bgLocationGranted,
            enabled = SystemPermissions.hasForegroundLocation(context),
            onClick = { bgLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        ReliabilityButton(
            title = if (batteryExempt) "Battery optimization off" else "Ignore battery optimization",
            subtitle = "Stops the system from killing the overnight tracker.",
            done = batteryExempt,
            enabled = true,
            onClick = { batteryLauncher.launch(SystemPermissions.batteryOptimizationIntent(context)) },
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        ReliabilityButton(
            title = if (hcConnected) "Sleep data connected" else "Connect sleep data",
            subtitle = "Use wearable sleep stages (Health Connect) for smarter wake timing.",
            done = hcConnected,
            enabled = reader.availability() == SleepStageReader.Availability.Available,
            onClick = { hcLauncher.launch(reader.requiredPermissions) },
        )

        Spacer(modifier = Modifier.height(Spacing.xxl + Spacing.xs))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
    }
}

@Composable
private fun ReliabilityButton(
    title: String,
    subtitle: String,
    done: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !done,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done) {
            Spacer(modifier = Modifier.size(Spacing.sm))
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DoneStep(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Alarm,
            contentDescription = null,
            modifier = Modifier.size(DONE_ICON_SIZE),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(Spacing.md))
        Text(
            text = "Cron will plan your first alarm tonight. You'll see the session status on the home screen.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Spacing.xxxl + Spacing.sm))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Go to home")
        }
    }
}
