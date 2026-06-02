package fr.bsodium.cron.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.ai.BudgetStore
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
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
    val preparationBufferMinutes: Int = 15,
    val hasApiKey: Boolean = false,
    val displayName: String? = null,
    val userInstructions: String? = null,
    val dailyTokenLimit: Int = BudgetStore.DEFAULT_DAILY_TOKEN_LIMIT,
    val tokensUsedToday: Int = 0,
)

class SettingsViewModel @JvmOverloads constructor(
    application: Application,
    private val secureStore: SecureKeyStore = SecureKeyStore(application),
) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val budget = BudgetStore(application)

    /** Today's token spend. SharedPreferences-backed (not a Flow); refreshed on screen resume. */
    private val _tokensUsedToday = MutableStateFlow(0)

    init {
        refreshUsage()
    }

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
    }.combine(repo.preparationBufferMinutes) { state, prepBuffer ->
        state.copy(preparationBufferMinutes = prepBuffer)
    }.combine(repo.displayName) { state, name ->
        state.copy(displayName = name)
    }.combine(repo.userInstructions) { state, instructions ->
        state.copy(userInstructions = instructions)
    }.combine(repo.dailyTokenLimit) { state, limit ->
        state.copy(dailyTokenLimit = limit)
    }.combine(_tokensUsedToday) { state, used ->
        state.copy(tokensUsedToday = used)
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

    fun setPreparationBuffer(minutes: Int) {
        viewModelScope.launch { repo.setPreparationBufferMinutes(minutes) }
    }

    fun clearApiKey() {
        secureStore.anthropicApiKey = null
    }

    fun setDisplayName(name: String) {
        viewModelScope.launch { repo.setDisplayName(name) }
    }

    fun setUserInstructions(text: String) {
        viewModelScope.launch { repo.setUserInstructions(text) }
    }

    fun setDailyTokenLimit(tokens: Int) {
        viewModelScope.launch { repo.setDailyTokenLimit(tokens) }
    }

    /** Re-reads today's token spend; called when the Settings screen resumes. */
    fun refreshUsage() {
        _tokensUsedToday.value = budget.usedToday()
    }
}
