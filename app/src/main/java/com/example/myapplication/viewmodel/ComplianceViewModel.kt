package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.compliance.CompliancePolicy
import com.example.myapplication.data.local.ComplianceConsentRepository
import com.example.myapplication.model.ComplianceConsent
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ComplianceUiState(
    val isLoading: Boolean = true,
    val isAccepted: Boolean = false,
    val acceptedPolicyVersion: String = "",
    val acceptedAtEpochMillis: Long = 0L,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class ComplianceViewModel(
    private val consentRepository: ComplianceConsentRepository,
    private val currentPolicyVersion: String = CompliancePolicy.CURRENT_VERSION,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ComplianceUiState())
    val uiState: StateFlow<ComplianceUiState> = _uiState.asStateFlow()

    private var observationJob: Job? = null

    init {
        observeConsent()
    }

    fun acceptCurrentPolicy() {
        if (_uiState.value.isSaving || !_uiState.value.isLoading && _uiState.value.isAccepted) {
            return
        }
        val acceptedAt = clock()
        if (acceptedAt <= 0L) {
            _uiState.update { it.copy(errorMessage = "无法记录确认时间，请检查设备时间后重试") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                consentRepository.accept(
                    policyVersion = currentPolicyVersion,
                    acceptedAtEpochMillis = acceptedAt,
                )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isAccepted = true,
                        acceptedPolicyVersion = currentPolicyVersion,
                        acceptedAtEpochMillis = acceptedAt,
                        isSaving = false,
                        errorMessage = null,
                    )
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = SensitiveTextRedactor.throwableMessageForUi(
                            throwable = throwable,
                            fallback = "确认状态保存失败，请重试",
                        ),
                    )
                }
            }
        }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun retryObservation() {
        observeConsent()
    }

    private fun observeConsent() {
        observationJob?.cancel()
        observationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            consentRepository.consentFlow
                .catch { throwable ->
                    if (throwable is CancellationException) {
                        throw throwable
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isAccepted = false,
                            errorMessage = SensitiveTextRedactor.throwableMessageForUi(
                                throwable = throwable,
                                fallback = "读取确认状态失败，请重试",
                            ),
                        )
                    }
                }
                .collect { consent ->
                    applyConsent(consent)
                }
        }
    }

    private fun applyConsent(consent: ComplianceConsent) {
        _uiState.update {
            it.copy(
                isLoading = false,
                isAccepted = consent.isAcceptedFor(currentPolicyVersion),
                acceptedPolicyVersion = consent.acceptedPolicyVersion,
                acceptedAtEpochMillis = consent.acceptedAtEpochMillis,
                isSaving = false,
                errorMessage = null,
            )
        }
    }

    override fun onCleared() {
        observationJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(
            consentRepository: ComplianceConsentRepository,
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                ComplianceViewModel(consentRepository = consentRepository)
            }
        }
    }
}
