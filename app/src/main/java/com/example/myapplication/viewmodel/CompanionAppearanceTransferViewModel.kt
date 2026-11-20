package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.context.CompanionAppearanceTransferRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompanionAppearanceTransferUiState(
    val isBusy: Boolean = false,
    val preview: CompanionAppearanceImportPreview? = null,
    val pendingOperations: List<ContextTransferOperationSummary> = emptyList(),
    val message: String? = null,
)

class CompanionAppearanceTransferViewModel(
    private val characterId: String,
    private val repository: CompanionAppearanceTransferRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CompanionAppearanceTransferUiState())
    val uiState = _uiState.asStateFlow()

    init {
        act("待恢复装扮暂不可读取") { refreshPending() }
    }

    fun exportPackage(onReady: (String, String) -> Unit) = act("装扮导出失败") {
        onReady(repository.exportPackage(characterId), "narra-appearance.json")
    }

    fun previewImport(rawJson: String) = act("装扮预览失败") {
        _uiState.value.preview?.let { old ->
            val pending =
                repository.listPendingOperations(ContextTransferOperationKind.PUBLIC_APPEARANCE)
                    .firstOrNull { it.operationId == old.operationId }
            if (pending?.stage == ContextTransferStage.PLANNED) repository.cancelPreview(old.operationId)
            _uiState.update { it.copy(preview = null) }
        }
        val preview = repository.prepareAppearanceImport(rawJson, characterId)
        _uiState.update { it.copy(preview = preview) }
        refreshPending()
    }

    fun confirmImport() {
        val operationId = _uiState.value.preview?.operationId ?: return
        resumeOperation(operationId)
    }

    fun resumeOperation(operationId: String) = act("装扮导入未完成，已完成阶段保留") {
        require(_uiState.value.preview?.operationId == operationId || _uiState.value.pendingOperations.any { it.operationId == operationId && characterId in it.targetAssistantIds }) { "该装扮计划不属于当前角色" }
        val result = repository.resumeOperation(operationId)
        if (result.stage == ContextTransferStage.COMPLETE) _uiState.update {
            it.copy(
                preview = it.preview?.takeUnless { preview -> preview.operationId == operationId },
                message = "装扮已导入"
            )
        }
        refreshPending()
    }

    fun dismissPreview() {
        val id = _uiState.value.preview?.operationId ?: return
        act("已完成阶段保留，可从待恢复操作继续") {
            val pending =
                repository.listPendingOperations(ContextTransferOperationKind.PUBLIC_APPEARANCE)
                    .firstOrNull { it.operationId == id }
            if (pending?.stage == ContextTransferStage.PLANNED) repository.cancelPreview(id)
            _uiState.update { it.copy(preview = null) }
            refreshPending()
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun act(fallback: String, block: suspend () -> Unit) {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, message = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                _uiState.update {
                    it.copy(
                        message = SensitiveTextRedactor.throwableMessageForUi(
                            failure,
                            fallback
                        )
                    )
                }
                try {
                    refreshPending()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                }
            } finally {
                _uiState.update { it.copy(isBusy = false) }
            }
        }
    }

    private suspend fun refreshPending() {
        val operations =
            repository.listPendingOperations(ContextTransferOperationKind.PUBLIC_APPEARANCE)
        _uiState.update { it.copy(pendingOperations = operations.filter { operation -> characterId in operation.targetAssistantIds }) }
    }

    companion object {
        fun factory(
            characterId: String,
            repository: CompanionAppearanceTransferRepository
        ): ViewModelProvider.Factory =
            typedViewModelFactory { CompanionAppearanceTransferViewModel(characterId, repository) }
    }
}
