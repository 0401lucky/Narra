package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorldBookUiState(
    val entries: List<WorldBookEntry> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WorldBookViewModel(
    private val repository: WorldBookRepository,
) : ViewModel() {
    val entries: StateFlow<List<WorldBookEntry>> = repository.observeEntries().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _uiState = MutableStateFlow(WorldBookUiState())
    val uiState: StateFlow<WorldBookUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            entries.collect { worldBookEntries ->
                _uiState.update { current ->
                    current.copy(entries = worldBookEntries)
                }
            }
        }
    }

    fun saveEntry(entry: WorldBookEntry) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            repository.upsertEntry(entry.copy(updatedAt = System.currentTimeMillis()))
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = "世界书已保存",
                )
            }
        }
    }

    fun deleteEntry(entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            repository.deleteEntry(entryId)
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = "世界书已删除",
                )
            }
        }
    }

    fun renameBook(
        originalBookName: String,
        newBookName: String,
    ) {
        val oldName = originalBookName.trim()
        val renamedBook = newBookName.trim()
        if (oldName.isBlank() || renamedBook.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            val now = System.currentTimeMillis()
            repository.listEntries()
                .filter { it.sourceBookName == oldName }
                .forEach { entry ->
                    repository.upsertEntry(
                        entry.copy(
                            sourceBookName = renamedBook,
                            updatedAt = now,
                        ),
                    )
                }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = "世界书已重命名",
                )
            }
        }
    }

    fun deleteBook(bookName: String) {
        val normalizedBookName = bookName.trim()
        if (normalizedBookName.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            repository.listEntries()
                .filter { it.sourceBookName == normalizedBookName }
                .forEach { entry ->
                    repository.deleteEntry(entry.id)
                }
            _uiState.update {
                it.copy(
                    isSaving = false,
                    message = "整本世界书已删除",
                )
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            repository: WorldBookRepository,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorldBookViewModel(repository) as T
                }
            }
        }
    }
}
