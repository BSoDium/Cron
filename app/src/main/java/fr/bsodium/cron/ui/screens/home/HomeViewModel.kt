package fr.bsodium.cron.ui.screens.home

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.engine.calendar.CalendarReaderImpl
import fr.bsodium.cron.engine.config.CronConfig
import fr.bsodium.cron.engine.model.CalendarEvent
import fr.bsodium.cron.engine.model.ScheduledAlarm
import fr.bsodium.cron.engine.model.SyncResult
import fr.bsodium.cron.engine.orchestrator.CronOrchestrator
import fr.bsodium.cron.engine.scheduler.AlarmSchedulerImpl
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the Home screen.
 *
 * Bridges the Cron engine to the Compose UI by exposing a reactive
 * [HomeUiState] and managing the lifecycle of a [ContentObserver]
 * that watches for calendar changes while the app is in the foreground.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val config = CronConfig.DEFAULT
    private val calendarReader = CalendarReaderImpl(application.contentResolver, config)
    private val alarmScheduler = AlarmSchedulerImpl(application)
    private val orchestrator = CronOrchestrator(calendarReader, alarmScheduler, config)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var debounceJob: Job? = null

    /**
     * ContentObserver that watches for calendar changes and triggers a
     * debounced refresh. Calendar apps often fire multiple onChange events
     * in rapid succession, so we debounce to avoid unnecessary work.
     */
    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refreshDebounced()
        }
    }

    /**
     * Register the ContentObserver. Call from the UI layer when the
     * composable enters the composition (e.g., via DisposableEffect).
     */
    fun startObserving() {
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true, // notifyForDescendants
                calendarObserver
            )
        } catch (_: SecurityException) {
            // Calendar permission not granted — observer won't work,
            // but that's fine; the UI will prompt for permission.
        }
    }

    /**
     * Unregister the ContentObserver. Call from the UI layer when the
     * composable leaves the composition.
     */
    fun stopObserving() {
        getApplication<Application>().contentResolver.unregisterContentObserver(calendarObserver)
    }

    /**
     * Run a full sync and update the UI state.
     */
    fun refresh() {
        viewModelScope.launch {
            val result = orchestrator.synchronize()
            _uiState.update { current ->
                current.copy(
                    nextAlarm = result.alarm,
                    events = result.events,
                    status = result.status
                )
            }
        }
    }

    /**
     * Enable or disable the automatic alarm engine.
     */
    fun setEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isEnabled = enabled) }
        // In a future version, this would persist to DataStore
        // and reconstruct the config. For now, we just re-sync
        // with the engine's hardcoded config.
        if (!enabled) {
            // Cancel any existing alarm
            viewModelScope.launch {
                val disabledConfig = config.copy(enabled = false)
                val tempOrchestrator = CronOrchestrator(calendarReader, alarmScheduler, disabledConfig)
                tempOrchestrator.synchronize()
                _uiState.update { it.copy(nextAlarm = null, status = SyncResult.Status.DISABLED) }
            }
        } else {
            refresh()
        }
    }

    fun updatePermissionState(hasCalendar: Boolean, hasNotification: Boolean) {
        _uiState.update {
            it.copy(
                hasCalendarPermission = hasCalendar,
                hasNotificationPermission = hasNotification
            )
        }
    }

    private fun refreshDebounced() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(2000) // 2-second debounce
            refresh()
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopObserving()
    }
}

/**
 * UI state for the Home screen.
 */
data class HomeUiState(
    val nextAlarm: ScheduledAlarm? = null,
    val events: List<CalendarEvent> = emptyList(),
    val status: SyncResult.Status = SyncResult.Status.NO_EVENTS,
    val isEnabled: Boolean = true,
    val hasCalendarPermission: Boolean = false,
    val hasNotificationPermission: Boolean = false
)
