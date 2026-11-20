package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.MemoryArchiveMessage
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MemoryOriginalUiState(
    val title: String = "历史原文",
    val messages: List<MemoryArchiveMessage> = emptyList(),
    val nextOffset: Int = 0,
    val totalCount: Int = 0,
    val hasMore: Boolean = false,
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val error: String? = null,
)

/** 只持有档案读取依赖；从这里无法进入场景、切换当前会话或启动后台整理。 */
class MemoryOriginalViewModel(
    private val repository: MemoryArchiveRepository,
    private val characterId: String,
    private val spaceId: String,
    private val personaKey: String,
    private val conversationId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MemoryOriginalUiState())
    val uiState = _uiState.asStateFlow()
    private var context: MemoryRuntimeContext? = null
    private var epoch = 0L
    private var reloadJob: Job? = null
    private var pageJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        val ticket = ++epoch
        reloadJob?.cancel()
        pageJob?.cancel()
        context = null
        _uiState.value = MemoryOriginalUiState()
        reloadJob = viewModelScope.launch {
            try {
                val resolved = repository.getManagementContext(characterId, spaceId, personaKey)
                val conversation = resolved?.let { candidate ->
                    if (repository.listArchivePersonas(candidate)
                            .none { it.key == personaKey }
                    ) null
                    else repository.listArchiveConversations(candidate)
                        .find { it.id == conversationId && it.exists }
                }
                if (ticket != epoch) return@launch
                if (resolved == null || conversation == null) {
                    _uiState.update { it.copy(isLoading = false, missing = true) }
                    return@launch
                }
                context = resolved
                _uiState.update {
                    it.copy(
                        title = conversation.title.ifBlank { "历史原文" },
                        isLoading = false
                    )
                }
                loadMore()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (ticket == epoch) failed(failure)
            }
        }
    }

    fun loadMore() {
        val resolved = context ?: return
        val state = _uiState.value
        val ticket = epoch
        if (state.isLoading || (state.messages.isNotEmpty() && !state.hasMore)) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        pageJob = viewModelScope.launch {
            try {
                val page =
                    repository.listArchiveMessages(resolved, conversationId, 100, state.nextOffset)
                if (ticket != epoch) return@launch
                _uiState.update {
                    it.copy(
                        messages = (it.messages + page.items).distinctBy { row -> row.message.id },
                        nextOffset = page.nextOffset,
                        totalCount = page.totalCount,
                        hasMore = page.hasMore,
                        isLoading = false
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                if (ticket == epoch) failed(failure)
            }
        }
    }

    private fun failed(failure: Throwable) = _uiState.update {
        it.copy(
            isLoading = false,
            error = SensitiveTextRedactor.throwableMessageForUi(failure, "读取原文失败，请重试。")
        )
    }

    companion object {
        fun factory(
            repository: MemoryArchiveRepository,
            characterId: String,
            spaceId: String,
            personaKey: String,
            conversationId: String
        ): ViewModelProvider.Factory =
            typedViewModelFactory {
                MemoryOriginalViewModel(
                    repository,
                    characterId,
                    spaceId,
                    personaKey,
                    conversationId
                )
            }
    }
}
