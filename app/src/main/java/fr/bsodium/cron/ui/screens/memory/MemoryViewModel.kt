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
        // Derive mutation state from WorkManager so it cannot drift from the real job.
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
            val placeholderId = repository.addPending(trimmed)
            repository.triggerMutation(trimmed, placeholderId)
        }
    }

    fun retryEntry(id: Long) {
        viewModelScope.launch {
            val instruction = repository.retry(id) ?: return@launch
            _isMutating.value = true
            repository.triggerMutation(instruction, id)
        }
    }

    fun addAnyway(id: Long) {
        viewModelScope.launch { repository.addAnyway(id) }
    }

    /** Swipe-to-delete: a direct removal, not routed through the assistant — see MemoryScreen's KDoc. */
    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}
