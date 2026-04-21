package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorldBookUiState(
    val entries: List<WorldBookEntry> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

private data class WorldBookInternalState(
    val isSaving: Boolean = false,
    val message: String? = null,
)

class WorldBookViewModel(
    private val repository: WorldBookRepository,
) : ViewModel() {
    private val internal = MutableStateFlow(WorldBookInternalState())

    val uiState: StateFlow<WorldBookUiState> = combine(
        repository.observeEntries(),
        internal,
    ) { entries, state ->
        WorldBookUiState(
            entries = entries,
            isSaving = state.isSaving,
            message = state.message,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = WorldBookUiState(),
    )

    fun saveEntry(entry: WorldBookEntry) {
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching {
                repository.upsertEntry(entry.copy(updatedAt = System.currentTimeMillis()))
            }
            internal.update {
                it.copy(
                    isSaving = false,
                    message = if (outcome.isSuccess) "世界书已保存" else "保存失败，请重试",
                )
            }
        }
    }

    fun deleteEntry(entryId: String) {
        if (entryId.isBlank()) return
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching { repository.deleteEntry(entryId) }
            internal.update {
                it.copy(
                    isSaving = false,
                    message = if (outcome.isSuccess) "世界书已删除" else "删除失败，请重试",
                )
            }
        }
    }

    fun renameBook(
        bookId: String,
        newBookName: String,
    ) {
        val normalizedBookId = bookId.trim()
        val renamedBook = newBookName.trim()
        if (normalizedBookId.isBlank() || renamedBook.isBlank()) return
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching {
                repository.renameBook(normalizedBookId, renamedBook)
            }
            internal.update {
                it.copy(
                    isSaving = false,
                    message = if (outcome.isSuccess) "世界书已重命名" else "重命名失败，请重试",
                )
            }
        }
    }

    fun deleteBook(bookId: String) {
        val normalizedBookId = bookId.trim()
        if (normalizedBookId.isBlank()) return
        viewModelScope.launch {
            internal.update { it.copy(isSaving = true, message = null) }
            val outcome = runCatching {
                repository.deleteBook(normalizedBookId)
            }
            internal.update {
                it.copy(
                    isSaving = false,
                    message = if (outcome.isSuccess) "整本世界书已删除" else "删除失败，请重试",
                )
            }
        }
    }

    fun consumeMessage() {
        internal.update { it.copy(message = null) }
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
