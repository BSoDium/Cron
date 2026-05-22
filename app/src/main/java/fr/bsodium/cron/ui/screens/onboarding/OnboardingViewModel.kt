package fr.bsodium.cron.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.settings.SecureKeyStore
import fr.bsodium.cron.settings.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { Welcome, ApiKey, Permissions, Done }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val apiKeyInput: String = "",
    val apiKeySaved: Boolean = false,
    val permissionsGranted: Boolean = false,
    val isSavingKey: Boolean = false,
    val keyError: String? = null,
)

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val secureStore = SecureKeyStore(application)
    private val settings = SettingsRepository(application)

    private val _uiState = MutableStateFlow(
        OnboardingUiState(
            step = if (secureStore.hasAnthropicKey()) OnboardingStep.Permissions else OnboardingStep.Welcome,
            apiKeySaved = secureStore.hasAnthropicKey(),
        )
    )
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

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
            OnboardingStep.Welcome -> OnboardingStep.ApiKey
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
