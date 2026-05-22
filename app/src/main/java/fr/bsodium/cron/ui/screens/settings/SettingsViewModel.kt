package fr.bsodium.cron.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

data class SettingsUiState(
    val eveningTrigger: LocalTime = LocalTime(22, 0),
    val hardLatest: LocalTime = LocalTime(10, 0),
    val freeDayWakeStart: LocalTime = LocalTime(8, 0),
    val freeDayWakeEnd: LocalTime = LocalTime(9, 30),
    val commuteBufferMinutes: Int = 15,
    val hasApiKey: Boolean = false,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val secureStore = SecureKeyStore(application)

    val uiState: StateFlow<SettingsUiState> = combine(
        repo.eveningTriggerLocalTime,
        repo.hardLatestDefault,
        repo.freeDayWakeStart,
        repo.freeDayWakeEnd,
        repo.commuteBufferMinutes,
    ) { evening, hardLatest, wakeStart, wakeEnd, buffer ->
        SettingsUiState(
            eveningTrigger = evening,
            hardLatest = hardLatest,
            freeDayWakeStart = wakeStart,
            freeDayWakeEnd = wakeEnd,
            commuteBufferMinutes = buffer,
            hasApiKey = secureStore.hasAnthropicKey(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setEveningTrigger(time: LocalTime) {
        viewModelScope.launch {
            repo.setEveningTriggerLocalTime(time)
            EveningPlanScheduler(getApplication()).armNext()
        }
    }

    fun setHardLatest(time: LocalTime) {
        viewModelScope.launch { repo.setHardLatestDefault(time) }
    }

    fun setFreeDayWakeWindow(start: LocalTime, end: LocalTime) {
        viewModelScope.launch { repo.setFreeDayWakeWindow(start, end) }
    }

    fun setCommuteBuffer(minutes: Int) {
        viewModelScope.launch { repo.setCommuteBufferMinutes(minutes) }
    }

    fun clearApiKey() {
        secureStore.anthropicApiKey = null
    }
}
