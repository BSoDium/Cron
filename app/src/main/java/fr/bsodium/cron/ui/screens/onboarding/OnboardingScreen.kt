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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
                .padding(horizontal = 32.dp),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (state.step != OnboardingStep.Done) {
                LinearProgressIndicator(
                    progress = { stepIndex.toFloat() / totalSteps },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(32.dp))
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
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Meet Cron",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Cron watches your calendar and sleep patterns overnight, then wakes you at the ideal moment before your first appointment — automatically planned by Claude.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(40.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Your name appears in the morning greeting. Stored on this device only.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = state.displayNameInput,
            onValueChange = viewModel::onDisplayNameChanged,
            label = { Text("Your name") },
            placeholder = { Text("Elliot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(16.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Cron uses Claude to plan your alarm. Paste your API key below — it's stored encrypted on this device and never leaves it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(16.dp))
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
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Cron needs the following to work:\n\n" +
                "• Calendar — to read tomorrow's appointments\n" +
                "• Location — to estimate your commute time once each evening\n" +
                "• Notifications — to show and dismiss the alarm\n" +
                "• Activity Recognition — to detect when you're out of bed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { launcher.launch(permissions.toTypedArray()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Grant permissions")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
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
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "You're all set",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Cron will plan your first alarm tonight. You'll see the session status on the home screen.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(40.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
            Text("Go to home")
        }
    }
}
