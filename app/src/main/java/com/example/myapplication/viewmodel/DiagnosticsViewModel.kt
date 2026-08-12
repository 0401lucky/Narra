package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.system.diagnostics.DiagnosticsExportResult
import com.example.myapplication.system.diagnostics.ExitDiagnosticsOperations
import com.example.myapplication.system.diagnostics.HistoricalExitQuerySupport
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DiagnosticsUiState(
    val isLoading: Boolean = true,
    val supportStatus: HistoricalExitQuerySupport = HistoricalExitQuerySupport.UNSUPPORTED,
    val recordCount: Int = 0,
    val latestExitTimestampEpochMillis: Long? = null,
    val isExporting: Boolean = false,
    val message: String? = null,
)

class DiagnosticsViewModel(
    private val diagnostics: ExitDiagnosticsOperations,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        DiagnosticsUiState(supportStatus = diagnostics.supportStatus),
    )
    val uiState: StateFlow<DiagnosticsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val snapshot = diagnostics.snapshot()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        supportStatus = snapshot.supportStatus,
                        recordCount = snapshot.events.size,
                        latestExitTimestampEpochMillis = snapshot.events
                            .maxOfOrNull { event -> event.exitTimestampEpochMillis },
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        message = "本地诊断信息读取失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun onExportDocumentSelected(
        openOutputStream: (() -> OutputStream?)?,
    ) {
        if (openOutputStream == null || _uiState.value.isExporting) return
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            try {
                val result = diagnostics.export(openOutputStream)
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        message = when (result) {
                            is DiagnosticsExportResult.Success -> "诊断信息已导出"
                            DiagnosticsExportResult.Failure -> "诊断信息导出失败，请稍后重试"
                        },
                    )
                }
            } catch (cancellation: CancellationException) {
                _uiState.update { it.copy(isExporting = false) }
                throw cancellation
            } catch (_: Throwable) {
                _uiState.update {
                    it.copy(
                        isExporting = false,
                        message = "诊断信息导出失败，请稍后重试",
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    companion object {
        fun factory(
            diagnostics: ExitDiagnosticsOperations,
        ): ViewModelProvider.Factory = typedViewModelFactory {
            DiagnosticsViewModel(diagnostics)
        }
    }
}
