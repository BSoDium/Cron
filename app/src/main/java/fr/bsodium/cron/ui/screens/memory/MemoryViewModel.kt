package fr.bsodium.cron.ui.screens.memory

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.bsodium.cron.memory.MemoryEntry
import fr.bsodium.cron.memory.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MemoryRepository(application)

    val entries: StateFlow<List<MemoryEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isMutating = MutableStateFlow(false)
    val isMutating: StateFlow<Boolean> = _isMutating

    init {
        // Drives isMutating off the real WorkManager job, not a client-side flag, so it can never
        // disagree with whether a mutation is actually in flight (same idiom as HomeViewModel.isRetrying).
        viewModelScope.launch {
            repository.observeMutationWork().collect { infos ->
                _isMutating.value = infos.any { !it.state.isFinished }
            }
        }
    }

    fun sendInstruction(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        _isMutating.value = true
        viewModelScope.launch {
            val placeholderId = repository.addPending()
            repository.triggerMutation(trimmed, placeholderId)
        }
    }

    /** Swipe-to-delete: a direct removal, not routed through the assistant — see MemoryScreen's KDoc. */
    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
