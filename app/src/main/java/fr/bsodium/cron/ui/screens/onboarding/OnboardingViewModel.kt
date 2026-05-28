package fr.bsodium.cron.ui.screens.onboarding

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.auth.GoogleAuthClient
import fr.bsodium.cron.auth.GoogleAuthResult
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, Name, ApiKey, Permissions, Done }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val displayNameInput: String = "",
    val photoUrl: String? = null,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
    val permissionsGranted: Boolean = false,
    val isSavingKey: Boolean = false,
    val keyError: String? = null,
    val isSigningIn: Boolean = false,
    val signInError: String? = null,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStore = SecureKeyStore(application)
    private val settings = SettingsRepository(application)
    private val googleAuth = GoogleAuthClient(application)

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = if (secureStore.hasAnthropicKey()) OnboardingStep.Permissions else OnboardingStep.Welcome,
            apiKeySaved = secureStore.hasAnthropicKey(),
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun onDisplayNameChanged(name: String) {
        _uiState.update { it.copy(displayNameInput = name) }
    }

    fun saveDisplayName() {
        val name = _uiState.value.displayNameInput.trim()
        if (name.isBlank()) return
        val photoUrl = _uiState.value.photoUrl
        viewModelScope.launch {
            settings.setDisplayName(name)
            settings.setDisplayPhotoUrl(photoUrl)
        }
        advance()
    }

    fun signInWithGoogle(activityContext: Context) {
        if (_uiState.value.isSigningIn) return
        _uiState.update { it.copy(isSigningIn = true, signInError = null) }
        viewModelScope.launch {
            when (val result = googleAuth.signIn(activityContext)) {
                is GoogleAuthResult.Success -> {
                    val name = result.profile.givenName ?: result.profile.displayName
                    _uiState.update {
                        it.copy(
                            isSigningIn = false,
                            signInError = null,
                            displayNameInput = name.orEmpty().ifBlank { it.displayNameInput },
                            photoUrl = result.profile.photoUrl,
                        )
                    }
                }
                is GoogleAuthResult.Cancelled -> {
                    _uiState.update { it.copy(isSigningIn = false, signInError = null) }
                }
                is GoogleAuthResult.Misconfigured -> {
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = "Sign-in not configured. Enter your name manually.")
                    }
                }
                is GoogleAuthResult.Failure -> {
                    _uiState.update {
                        it.copy(isSigningIn = false, signInError = "Sign-in failed. Enter your name manually.")
                    }
                }
            }
        }
    }

    fun onApiKeyChanged(key: String) {
        _uiState.update { it.copy(apiKeyInput = key.trim(), keyError = null) }
    }

    fun saveApiKey() {
        val key = _uiState.value.apiKeyInput
        if (!key.startsWith("sk-ant-") && !key.startsWith("sk-")) {
            _uiState.update { it.copy(keyError = "Key must start with sk-ant- or sk-") }
            return
        }
        if (key.length < 20) {
            _uiState.update { it.copy(keyError = "Key is too short") }
            return
        }
        secureStore.anthropicApiKey = key
        _uiState.update { it.copy(apiKeySaved = true, keyError = null) }
        advance()
    }

    fun advance() {
        val current = _uiState.value.step
        val next = when (current) {
            OnboardingStep.Welcome -> OnboardingStep.Name
            OnboardingStep.Name -> OnboardingStep.ApiKey
            OnboardingStep.ApiKey -> OnboardingStep.Permissions
            OnboardingStep.Permissions -> OnboardingStep.Done
            OnboardingStep.Done -> OnboardingStep.Done
        }
        _uiState.update { it.copy(step = next) }
    }

    fun onPermissionsResult(allRequiredGranted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = allRequiredGranted) }
    }

    fun complete() {
        viewModelScope.launch {
            settings.setOnboardingComplete()
        }
    }
}
