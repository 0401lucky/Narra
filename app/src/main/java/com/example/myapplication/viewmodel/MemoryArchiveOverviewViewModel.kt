package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemoryArchiveOverviewUiState(
    val counts: Map<String, Int> = emptyMap(),
    val error: String? = null
)

class MemoryArchiveOverviewViewModel(private val repository: MemoryArchiveRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(MemoryArchiveOverviewUiState())
    val uiState = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var refreshEpoch = 0L

    fun refresh() {
        val ticket = ++refreshEpoch
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                val counts = repository.listArchiveCharacterIds()
                    .associateWith { repository.countArchiveItems(it) }
                if (ticket == refreshEpoch) _uiState.value = MemoryArchiveOverviewUiState(counts)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (ticket == refreshEpoch) _uiState.value = _uiState.value.copy(
                    error = SensitiveTextRedactor.throwableMessageForUi(
                        failure,
                        "读取档案角色失败，请重新进入此页。"
                    )
                )
            }
        }
    }

    companion object {
        fun factory(repository: MemoryArchiveRepository) =
            typedViewModelFactory { MemoryArchiveOverviewViewModel(repository) }
    }
}
