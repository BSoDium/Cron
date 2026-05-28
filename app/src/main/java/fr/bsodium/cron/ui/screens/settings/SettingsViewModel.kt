package fr.bsodium.cron.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.alarm.EveningPlanScheduler
import fr.bsodium.cron.auth.GoogleAuthClient
import fr.bsodium.cron.auth.GoogleAuthResult
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
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
    val displayPhotoUrl: String? = null,
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val secureStore = SecureKeyStore(application)
    private val googleAuth = GoogleAuthClient(application)
    private val _signInState = MutableStateFlow(SignInState())

    private data class SignInState(
        val isSigningIn: Boolean = false,
        val error: String? = null,
    )

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
    }.combine(repo.displayPhotoUrl) { state, photoUrl ->
        state.copy(displayPhotoUrl = photoUrl)
    }.combine(_signInState) { state, signIn ->
        state.copy(isSigningIn = signIn.isSigningIn, signInError = signIn.error)
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

    fun signInWithGoogle(activityContext: Context) {
        if (_signInState.value.isSigningIn) return
        _signInState.update { it.copy(isSigningIn = true, error = null) }
        viewModelScope.launch {
            when (val result = googleAuth.signIn(activityContext)) {
                is GoogleAuthResult.Success -> {
                    val name = result.profile.givenName ?: result.profile.displayName
                    if (!name.isNullOrBlank()) repo.setDisplayName(name)
                    repo.setDisplayPhotoUrl(result.profile.photoUrl)
                    _signInState.update { it.copy(isSigningIn = false, error = null) }
                }
                is GoogleAuthResult.Cancelled ->
                    _signInState.update { it.copy(isSigningIn = false, error = null) }
                is GoogleAuthResult.Misconfigured ->
                    _signInState.update { it.copy(isSigningIn = false, error = "Sign-in not configured.") }
                is GoogleAuthResult.Failure ->
                    _signInState.update { it.copy(isSigningIn = false, error = "Sign-in failed.") }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch { repo.clearDisplayProfile() }
    }
}
