package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateCheckOutcome
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.AppUpdateInstallResultType
import com.example.myapplication.model.AppUpdateMetadata
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val currentVersionName: String,
    val currentVersionCode: Int,
    val channel: String,
    val metadataBaseUrl: String,
    val lastCheckedAt: Long = 0L,
    val isChecking: Boolean = false,
    val availability: AppUpdateAvailability = AppUpdateAvailability.UNKNOWN,
    val latestMetadata: AppUpdateMetadata? = null,
    val downloadSnapshot: AppUpdateDownloadSnapshot = AppUpdateDownloadSnapshot(),
    val isDialogVisible: Boolean = false,
    val message: String? = null,
) {
    val hasConfiguredSource: Boolean
        get() = metadataBaseUrl.isNotBlank()

    val isForceUpdate: Boolean
        get() = false

    val hasAvailableUpdate: Boolean
        get() = availability == AppUpdateAvailability.OPTIONAL
}

class AppUpdateViewModel(
    private val repository: AppUpdateRepository,
    private val downloadController: AppUpdateDownloadController,
    private val environment: AppUpdateEnvironment,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        AppUpdateUiState(
            currentVersionName = environment.versionName,
            currentVersionCode = environment.versionCode,
            channel = environment.channel,
            metadataBaseUrl = environment.metadataBaseUrl,
            availability = if (environment.metadataBaseUrl.isBlank()) {
                AppUpdateAvailability.DISABLED
            } else {
                AppUpdateAvailability.UNKNOWN
            },
        ),
    )
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private var hasStarted = false
    private var downloadMonitorJob: Job? = null
    private var observedDownloadId: Long? = null

    init {
        viewModelScope.launch {
            repository.localStateFlow.collect { localState ->
                _uiState.update {
                    it.copy(lastCheckedAt = localState.lastCheckAt)
                }
                reconcileActiveDownload(localState.activeDownloadId)
            }
        }
        viewModelScope.launch {
            val cachedOutcome = repository.evaluateCachedOutcome(environment)
            applyOutcome(
                outcome = cachedOutcome,
                revealDialog = true,
                surfaceSuccessMessage = false,
                surfaceFailureMessage = false,
            )
            if (cachedOutcome?.availability == AppUpdateAvailability.UP_TO_DATE) {
                repository.clearActiveDownloadId()
            }
        }
    }

    fun onAppStarted() {
        if (hasStarted) {
            return
        }
        hasStarted = true
        if (!uiState.value.hasConfiguredSource) {
            _uiState.update { it.copy(availability = AppUpdateAvailability.DISABLED) }
            return
        }
        performCheck(
            manual = false,
            revealDialog = true,
            surfaceSuccessMessage = false,
            surfaceFailureMessage = false,
        )
    }

    fun checkForUpdates() {
        performCheck(
            manual = true,
            revealDialog = true,
            surfaceSuccessMessage = true,
            surfaceFailureMessage = true,
        )
    }

    fun dismissDialog() {
        _uiState.update { current -> current.copy(isDialogVisible = false) }
    }

    fun startUpdateDownload() {
        val metadata = uiState.value.latestMetadata ?: return
        val downloadStatus = uiState.value.downloadSnapshot.status
        if (downloadStatus == AppUpdateDownloadStatus.RUNNING ||
            downloadStatus == AppUpdateDownloadStatus.PENDING ||
            downloadStatus == AppUpdateDownloadStatus.PAUSED
        ) {
            return
        }

        viewModelScope.launch {
            runCatching {
                downloadController.enqueueDownload(metadata)
            }.onSuccess { downloadId ->
                repository.saveActiveDownloadId(downloadId)
                _uiState.update {
                    it.copy(
                        downloadSnapshot = AppUpdateDownloadSnapshot(
                            status = AppUpdateDownloadStatus.PENDING,
                            downloadId = downloadId,
                        ),
                        isDialogVisible = true,
                        message = "已开始下载更新包",
                    )
                }
                reconcileActiveDownload(downloadId)
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "创建下载任务失败")
                }
            }
        }
    }

    fun installDownloadedUpdate() {
        val metadata = uiState.value.latestMetadata ?: return
        val downloadId = uiState.value.downloadSnapshot.downloadId ?: return

        viewModelScope.launch {
            val result = downloadController.installDownloadedPackage(downloadId, metadata)
            when (result.type) {
                AppUpdateInstallResultType.STARTED,
                AppUpdateInstallResultType.REQUIRE_UNKNOWN_SOURCES_PERMISSION,
                -> _uiState.update { it.copy(message = result.message) }

                AppUpdateInstallResultType.HASH_MISMATCH,
                AppUpdateInstallResultType.FILE_MISSING,
                -> {
                    repository.clearActiveDownloadId()
                    _uiState.update {
                        it.copy(
                            downloadSnapshot = AppUpdateDownloadSnapshot(),
                            message = result.message,
                        )
                    }
                }

                AppUpdateInstallResultType.ERROR -> {
                    _uiState.update { it.copy(message = result.message ?: "安装启动失败") }
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun performCheck(
        manual: Boolean,
        revealDialog: Boolean,
        surfaceSuccessMessage: Boolean,
        surfaceFailureMessage: Boolean,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isChecking = true, message = null) }
            val outcome = repository.checkForUpdates(
                environment = environment,
                manual = manual,
            )
            applyOutcome(
                outcome = outcome,
                revealDialog = revealDialog,
                surfaceSuccessMessage = surfaceSuccessMessage,
                surfaceFailureMessage = surfaceFailureMessage,
            )
        }
    }

    private suspend fun reconcileActiveDownload(downloadId: Long) {
        if (downloadId <= 0L) {
            observedDownloadId = null
            downloadMonitorJob?.cancel()
            _uiState.update { current ->
                if (current.downloadSnapshot.status == AppUpdateDownloadStatus.DOWNLOADED) {
                    current
                } else {
                    current.copy(downloadSnapshot = AppUpdateDownloadSnapshot())
                }
            }
            return
        }

        if (observedDownloadId == downloadId && downloadMonitorJob?.isActive == true) {
            return
        }

        observedDownloadId = downloadId
        downloadMonitorJob?.cancel()
        downloadMonitorJob = viewModelScope.launch {
            while (true) {
                val snapshot = downloadController.queryDownload(downloadId)
                _uiState.update { current ->
                    current.copy(downloadSnapshot = snapshot)
                }
                when (snapshot.status) {
                    AppUpdateDownloadStatus.DOWNLOADED -> break
                    AppUpdateDownloadStatus.FAILED,
                    AppUpdateDownloadStatus.MISSING,
                    -> {
                        repository.clearActiveDownloadId()
                        _uiState.update {
                            it.copy(
                                downloadSnapshot = AppUpdateDownloadSnapshot(),
                                message = snapshot.reason ?: "更新下载已中断",
                            )
                        }
                        break
                    }

                    else -> delay(1_000L)
                }
            }
        }
    }

    private suspend fun applyOutcome(
        outcome: AppUpdateCheckOutcome?,
        revealDialog: Boolean,
        surfaceSuccessMessage: Boolean,
        surfaceFailureMessage: Boolean,
    ) {
        if (outcome == null) {
            _uiState.update { it.copy(isChecking = false) }
            return
        }

        _uiState.update { current ->
            current.copy(
                isChecking = false,
                availability = outcome.availability,
                latestMetadata = outcome.metadata,
                downloadSnapshot = if (outcome.availability == AppUpdateAvailability.UP_TO_DATE) {
                    AppUpdateDownloadSnapshot()
                } else {
                    current.downloadSnapshot
                },
                isDialogVisible = when {
                    revealDialog && outcome.availability == AppUpdateAvailability.OPTIONAL -> true
                    outcome.availability == AppUpdateAvailability.UP_TO_DATE -> false
                    else -> current.isDialogVisible
                },
                message = when {
                    surfaceFailureMessage && !outcome.errorMessage.isNullOrBlank() &&
                        outcome.availability == AppUpdateAvailability.UNKNOWN
                    -> outcome.errorMessage

                    surfaceSuccessMessage && outcome.availability == AppUpdateAvailability.DISABLED ->
                        "当前未配置更新地址"

                    surfaceSuccessMessage && outcome.availability == AppUpdateAvailability.UP_TO_DATE ->
                        "当前已是最新版本"

                    else -> current.message
                },
            )
        }

        if (outcome.availability == AppUpdateAvailability.UP_TO_DATE) {
            repository.clearActiveDownloadId()
        }
    }

    companion object {
        fun factory(
            repository: AppUpdateRepository,
            downloadController: AppUpdateDownloadController,
            environment: AppUpdateEnvironment,
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppUpdateViewModel(
                        repository = repository,
                        downloadController = downloadController,
                        environment = environment,
                    ) as T
                }
            }
        }
    }
}
