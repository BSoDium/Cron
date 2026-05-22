package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.BuildConfig
import fr.bsodium.cron.ui.components.AiDebugCard
import fr.bsodium.cron.ui.components.SensorDebugCard
import fr.bsodium.cron.ui.components.SessionStatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Cron", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SessionStatusCard(
                state = uiState.sessionDisplay,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (BuildConfig.DEBUG) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                AiDebugCard(
                    hasKey = uiState.hasAnthropicKey,
                    smokeState = uiState.smokeState,
                    onSaveKey = viewModel::saveAnthropicKey,
                    onRunSmoke = viewModel::runSmokeTest,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    routesApiKey = BuildConfig.GOOGLE_ROUTES_API_KEY.takeIf { it.isNotBlank() },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                val sensorEvents by viewModel.recentSensorEvents.collectAsState()
                SensorDebugCard(
                    recentEvents = sensorEvents,
                    onStart = viewModel::startSensorService,
                    onStop = viewModel::stopSensorService,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
