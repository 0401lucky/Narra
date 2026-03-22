package com.example.myapplication.viewmodel

import com.example.myapplication.data.local.AppUpdateStateStore
import com.example.myapplication.data.repository.AppUpdateRepository
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateDownloadSnapshot
import com.example.myapplication.model.AppUpdateDownloadStatus
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.AppUpdateInstallResult
import com.example.myapplication.model.AppUpdateInstallResultType
import com.example.myapplication.model.AppUpdateLocalState
import com.example.myapplication.model.AppUpdateMetadata
import com.example.myapplication.system.update.AppUpdateDownloadController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppUpdateViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun checkForUpdates_updatesUiStateAndShowsOptionalDialog() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = createRepository(
            metadataJson = optionalUpdateJson(),
        )
        val controller = FakeAppUpdateDownloadController()
        val viewModel = AppUpdateViewModel(
            repository = repository,
            downloadController = controller,
            environment = testEnvironment(),
        )

        advanceUntilIdle()
        viewModel.checkForUpdates()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AppUpdateAvailability.OPTIONAL, state.availability)
        assertTrue(state.isDialogVisible)
        assertEquals(10100, state.latestMetadata?.latestVersionCode)
    }

    @Test
    fun startUpdateDownload_tracksDownloadUntilDownloaded() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = createRepository(
            metadataJson = optionalUpdateJson(),
        )
        val controller = FakeAppUpdateDownloadController().apply {
            queueSnapshots(
                downloadId = 41L,
                snapshots = listOf(
                    AppUpdateDownloadSnapshot(
                        status = AppUpdateDownloadStatus.PENDING,
                        downloadId = 41L,
                    ),
                    AppUpdateDownloadSnapshot(
                        status = AppUpdateDownloadStatus.DOWNLOADED,
                        downloadId = 41L,
                    ),
                ),
            )
        }
        val viewModel = AppUpdateViewModel(
            repository = repository,
            downloadController = controller,
            environment = testEnvironment(),
        )

        advanceUntilIdle()
        viewModel.checkForUpdates()
        advanceUntilIdle()
        viewModel.startUpdateDownload()
        advanceUntilIdle()
        advanceTimeBy(1_000L)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(AppUpdateDownloadStatus.DOWNLOADED, state.downloadSnapshot.status)
        assertEquals(41L, state.downloadSnapshot.downloadId)
    }

    @Test
    fun installDownloadedUpdate_clearsTrackedDownloadWhenHashMismatch() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeViewModelAppUpdateStateStore(
            AppUpdateLocalState(
                activeDownloadId = 41L,
                cachedMetadataJson = optionalUpdateJson(),
            ),
        )
        val repository = AppUpdateRepository(
            stateStore = store,
            metadataFetcher = { optionalUpdateJson() },
            nowProvider = { 1_000L },
        )
        val controller = FakeAppUpdateDownloadController().apply {
            queueSnapshots(
                downloadId = 41L,
                snapshots = listOf(
                    AppUpdateDownloadSnapshot(
                        status = AppUpdateDownloadStatus.DOWNLOADED,
                        downloadId = 41L,
                    ),
                ),
            )
            installResult = AppUpdateInstallResult(
                type = AppUpdateInstallResultType.HASH_MISMATCH,
                message = "安装包校验失败，请重新下载",
            )
        }
        val viewModel = AppUpdateViewModel(
            repository = repository,
            downloadController = controller,
            environment = testEnvironment(),
        )

        advanceUntilIdle()
        viewModel.installDownloadedUpdate()
        advanceUntilIdle()

        assertEquals(0L, store.currentState().activeDownloadId)
        assertEquals(AppUpdateDownloadStatus.IDLE, viewModel.uiState.value.downloadSnapshot.status)
        assertEquals("安装包校验失败，请重新下载", viewModel.uiState.value.message)
    }

    private fun createRepository(metadataJson: String): AppUpdateRepository {
        return AppUpdateRepository(
            stateStore = FakeViewModelAppUpdateStateStore(),
            metadataFetcher = { metadataJson },
            nowProvider = { 1_000L },
        )
    }

    private fun testEnvironment(): AppUpdateEnvironment {
        return AppUpdateEnvironment(
            appId = "com.narra.app",
            channel = "release",
            versionName = "1.0.0",
            versionCode = 10000,
            metadataBaseUrl = "https://updates.example.com",
        )
    }

    private fun optionalUpdateJson(): String {
        return """
            {
              "app_id": "com.narra.app",
              "channel": "release",
              "latest_version_name": "1.1.0",
              "latest_version_code": 10100,
              "minimum_supported_version_code": 10000,
              "apk_url": "https://downloads.example.com/Narra-v1.1.0-10100-release.apk",
              "apk_sha256": "abcdef",
              "published_at": "2026-03-22T12:00:00+08:00",
              "release_notes": ["新增功能 A"]
            }
        """.trimIndent()
    }
}

private class FakeViewModelAppUpdateStateStore(
    initialState: AppUpdateLocalState = AppUpdateLocalState(),
) : AppUpdateStateStore {
    private val state = MutableStateFlow(initialState)

    override val stateFlow: Flow<AppUpdateLocalState> = state

    override suspend fun saveLastCheckAt(value: Long) {
        state.value = state.value.copy(lastCheckAt = value)
    }

    override suspend fun saveCachedMetadataJson(value: String) {
        state.value = state.value.copy(cachedMetadataJson = value)
    }

    override suspend fun clearCachedMetadata() {
        state.value = state.value.copy(cachedMetadataJson = "")
    }

    override suspend fun saveActiveDownloadId(value: Long) {
        state.value = state.value.copy(activeDownloadId = value)
    }

    override suspend fun clearActiveDownloadId() {
        state.value = state.value.copy(activeDownloadId = 0L)
    }

    fun currentState(): AppUpdateLocalState = state.value
}

private class FakeAppUpdateDownloadController : AppUpdateDownloadController {
    private val queuedSnapshots = linkedMapOf<Long, ArrayDeque<AppUpdateDownloadSnapshot>>()
    private val fallbackSnapshots = mutableMapOf<Long, AppUpdateDownloadSnapshot>()

    var nextDownloadId: Long = 41L
    var installResult: AppUpdateInstallResult = AppUpdateInstallResult(
        type = AppUpdateInstallResultType.STARTED,
        message = "已打开系统安装器",
    )

    override fun enqueueDownload(metadata: AppUpdateMetadata): Long {
        return nextDownloadId
    }

    override fun queryDownload(downloadId: Long): AppUpdateDownloadSnapshot {
        val queue = queuedSnapshots[downloadId]
        if (queue != null && queue.isNotEmpty()) {
            val next = queue.removeFirst()
            fallbackSnapshots[downloadId] = next
            return next
        }
        return fallbackSnapshots[downloadId]
            ?: AppUpdateDownloadSnapshot(
                status = AppUpdateDownloadStatus.IDLE,
                downloadId = downloadId,
            )
    }

    override suspend fun installDownloadedPackage(
        downloadId: Long,
        metadata: AppUpdateMetadata,
    ): AppUpdateInstallResult {
        return installResult
    }

    fun queueSnapshots(
        downloadId: Long,
        snapshots: List<AppUpdateDownloadSnapshot>,
    ) {
        queuedSnapshots[downloadId] = ArrayDeque(snapshots)
        fallbackSnapshots[downloadId] = snapshots.lastOrNull()
            ?: AppUpdateDownloadSnapshot(
                status = AppUpdateDownloadStatus.IDLE,
                downloadId = downloadId,
            )
    }
}
