package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryManagementUiState(
    val memories: List<MemoryEntry> = emptyList(),
    val summaries: List<ConversationSummary> = emptyList(),
    val isBusy: Boolean = false,
    val message: String? = null,
)

class MemoryManagementViewModel(
    private val memoryRepository: MemoryRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
) : ViewModel() {
    val memories = memoryRepository.observeEntries().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _uiState = MutableStateFlow(MemoryManagementUiState())
    val uiState: StateFlow<MemoryManagementUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            memories.collect { memoryEntries ->
                _uiState.update { it.copy(memories = memoryEntries) }
            }
        }
        viewModelScope.launch {
            conversationSummaryRepository.observeSummaries().collect { summaries ->
                _uiState.update { current ->
                    current.copy(summaries = summaries)
                }
            }
        }
    }

    fun togglePinned(entryId: String) {
        val target = _uiState.value.memories.firstOrNull { it.id == entryId } ?: return
        viewModelScope.launch {
            memoryRepository.upsertEntry(
                target.copy(
                    pinned = !target.pinned,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            _uiState.update {
                it.copy(message = if (target.pinned) "已取消置顶记忆" else "已置顶记忆")
            }
        }
    }

    fun deleteMemory(entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            memoryRepository.deleteEntry(entryId)
            _uiState.update { it.copy(message = "记忆已删除") }
        }
    }

    fun deleteSummary(conversationId: String) {
        if (conversationId.isBlank()) return
        viewModelScope.launch {
            conversationSummaryRepository.deleteSummary(conversationId)
            _uiState.update { it.copy(message = "摘要已删除") }
        }
    }

    fun upsertMemory(entry: MemoryEntry) {
        viewModelScope.launch {
            memoryRepository.upsertEntry(
                entry.copy(updatedAt = System.currentTimeMillis()),
            )
            _uiState.update { it.copy(message = "记忆已保存") }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            memoryRepository: MemoryRepository,
            conversationSummaryRepository: ConversationSummaryRepository,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                MemoryManagementViewModel(
                    memoryRepository = memoryRepository,
                    conversationSummaryRepository = conversationSummaryRepository,
                )
            }
        }
    }
}
