package fr.bsodium.cron.ui.screens.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.bsodium.cron.engine.model.CalendarEvent
import fr.bsodium.cron.ui.components.AlarmCard
import fr.bsodium.cron.ui.components.EventListItem
import fr.bsodium.cron.ui.components.PermissionGate
import fr.bsodium.cron.ui.components.StatusToggle
import java.time.LocalDate
import java.time.ZoneId

/**
 * Main screen of the Cron app.
 *
 * Shows the next alarm, a toggle to enable/disable, and tomorrow's events.
 * Gates everything behind a permission check via [PermissionGate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cron",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        PermissionGate(
            hasCalendarPermission = uiState.hasCalendarPermission,
            hasNotificationPermission = uiState.hasNotificationPermission,
            hasLocationPermission = uiState.hasLocationPermission,
            onPermissionsResult = { calendarGranted, notificationGranted, locationGranted ->
                viewModel.updatePermissionState(calendarGranted, notificationGranted, locationGranted)
                if (calendarGranted) {
                    viewModel.startObserving()
                    viewModel.refresh()
                }
            }
        ) {
            // Register/unregister the calendar ContentObserver with the lifecycle
            DisposableEffect(Unit) {
                viewModel.startObserving()
                viewModel.refresh()
                onDispose {
                    viewModel.stopObserving()
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Hero alarm card
                AlarmCard(
                    alarm = uiState.nextAlarm,
                    status = uiState.status,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    travelInfo = uiState.travelInfo
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Enable/disable toggle
                StatusToggle(
                    isEnabled = uiState.isEnabled,
                    onToggle = { viewModel.setEnabled(it) }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Tomorrow's events section
                val tomorrowEvents = filterTomorrowEvents(uiState.events)

                Text(
                    text = "Tomorrow's schedule" + if (tomorrowEvents.isNotEmpty()) {
                        " (${tomorrowEvents.size})"
                    } else "",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (tomorrowEvents.isEmpty()) {
                    Text(
                        text = "No events scheduled",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                } else {
                    tomorrowEvents.forEach { event ->
                        EventListItem(event = event)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

/**
 * Filters events to only those starting tomorrow (in the device timezone).
 */
private fun filterTomorrowEvents(events: List<CalendarEvent>): List<CalendarEvent> {
    val zone = ZoneId.systemDefault()
    val tomorrow = LocalDate.now(zone).plusDays(1)
    val tomorrowStart = tomorrow.atStartOfDay(zone).toInstant()
    val tomorrowEnd = tomorrow.plusDays(1).atStartOfDay(zone).toInstant()

    return events.filter { it.startTime in tomorrowStart..<tomorrowEnd }
}
