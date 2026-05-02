package com.example.myapplication.data.repository

import com.example.myapplication.data.local.AppUpdateStateStore
import com.example.myapplication.model.AppUpdateAvailability
import com.example.myapplication.model.AppUpdateEnvironment
import com.example.myapplication.model.AppUpdateLocalState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateRepositoryTest {
    @Test
    fun checkForUpdates_returnsOptionalUpdateWhenRemoteVersionIsNewer() = runTest {
        val store = FakeAppUpdateStateStore()
        val repository = AppUpdateRepository(
            stateStore = store,
            metadataFetcher = { optionalUpdateJson() },
            nowProvider = { 1_000L },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(),
            manual = true,
        )

        assertEquals(AppUpdateAvailability.OPTIONAL, outcome?.availability)
        assertEquals(1_000L, outcome?.checkedAt)
        assertEquals(1_000L, store.currentState().lastCheckAt)
        assertTrue(store.currentState().cachedMetadataJson.isNotBlank())
    }

    @Test
    fun checkForUpdates_stillReturnsOptionalUpdateWhenMinimumSupportedVersionIsHigherThanCurrentVersion() = runTest {
        val repository = AppUpdateRepository(
            stateStore = FakeAppUpdateStateStore(),
            metadataFetcher = { requiredUpdateJson() },
            nowProvider = { 2_000L },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(versionCode = 10000),
            manual = true,
        )

        assertEquals(AppUpdateAvailability.OPTIONAL, outcome?.availability)
        assertEquals(10100, outcome?.metadata?.latestVersionCode)
    }

    @Test
    fun checkForUpdates_returnsCachedOptionalUpdateOnNetworkFailure() = runTest {
        val store = FakeAppUpdateStateStore(
            AppUpdateLocalState(
                lastCheckAt = 5_000L,
                cachedMetadataJson = requiredUpdateJson(),
            ),
        )
        val repository = AppUpdateRepository(
            stateStore = store,
            metadataFetcher = { error("network down") },
            nowProvider = { 10_000L },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(versionCode = 10000),
            manual = true,
        )

        assertEquals(AppUpdateAvailability.OPTIONAL, outcome?.availability)
        assertTrue(outcome?.fromCache == true)
        assertNotNull(outcome?.errorMessage)
    }

    @Test
    fun checkForUpdates_skipsAutoRequestWithinThrottleWindowAndUsesCachedOutcome() = runTest {
        val store = FakeAppUpdateStateStore(
            AppUpdateLocalState(
                lastCheckAt = 100L,
                cachedMetadataJson = optionalUpdateJson(),
            ),
        )
        var fetchCount = 0
        val repository = AppUpdateRepository(
            stateStore = store,
            metadataFetcher = {
                fetchCount++
                optionalUpdateJson()
            },
            nowProvider = { 200L },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(),
            manual = false,
        )

        assertEquals(0, fetchCount)
        assertEquals(AppUpdateAvailability.OPTIONAL, outcome?.availability)
        assertTrue(outcome?.fromCache == true)
    }

    @Test
    fun checkForUpdates_returnsDisabledWhenBaseUrlMissing() = runTest {
        val repository = AppUpdateRepository(
            stateStore = FakeAppUpdateStateStore(),
            metadataFetcher = { optionalUpdateJson() },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(metadataBaseUrl = ""),
            manual = true,
        )

        assertEquals(AppUpdateAvailability.DISABLED, outcome?.availability)
    }

    @Test
    fun checkForUpdates_rejectsNewVersionWithoutApkSha256() = runTest {
        val repository = AppUpdateRepository(
            stateStore = FakeAppUpdateStateStore(),
            metadataFetcher = { optionalUpdateJson(apkSha256 = "") },
            nowProvider = { 1_000L },
        )

        val outcome = repository.checkForUpdates(
            environment = testEnvironment(),
            manual = true,
        )

        assertEquals(AppUpdateAvailability.UNKNOWN, outcome?.availability)
        assertEquals("更新元数据缺少 APK 校验信息", outcome?.errorMessage)
    }


    @Test
    fun checkForUpdates_cachesUpToDateMetadataAndReusesItWithinThrottleWindow() = runTest {
        val store = FakeAppUpdateStateStore()
        var fetchCount = 0
        val repository = AppUpdateRepository(
            stateStore = store,
            metadataFetcher = {
                fetchCount++
                currentVersionJson()
            },
            nowProvider = { if (fetchCount == 0) 1_000L else 2_000L },
        )

        val firstOutcome = repository.checkForUpdates(
            environment = testEnvironment(),
            manual = true,
        )
        val secondOutcome = repository.checkForUpdates(
            environment = testEnvironment(),
            manual = false,
        )

        assertEquals(AppUpdateAvailability.UP_TO_DATE, firstOutcome?.availability)
        assertEquals(AppUpdateAvailability.UP_TO_DATE, secondOutcome?.availability)
        assertTrue(secondOutcome?.fromCache == true)
        assertEquals(1, fetchCount)
        assertTrue(store.currentState().cachedMetadataJson.isNotBlank())
    }

    private fun testEnvironment(
        versionCode: Int = 10000,
        metadataBaseUrl: String = "https://updates.example.com",
    ): AppUpdateEnvironment {
        return AppUpdateEnvironment(
            appId = "com.narra.app",
            channel = "release",
            versionName = "1.0.0",
            versionCode = versionCode,
            metadataBaseUrl = metadataBaseUrl,
        )
    }

    private fun optionalUpdateJson(apkSha256: String = "abcdef"): String {
        return """
            {
              "app_id": "com.narra.app",
              "channel": "release",
              "latest_version_name": "1.1.0",
              "latest_version_code": 10100,
              "minimum_supported_version_code": 10000,
              "apk_url": "https://downloads.example.com/Narra-v1.1.0-10100-release.apk",
              "apk_sha256": "$apkSha256",
              "published_at": "2026-03-22T12:00:00+08:00",
              "release_notes": ["新增功能 A", "修复问题 B"]
            }
        """.trimIndent()
    }

    private fun requiredUpdateJson(): String {
        return """
            {
              "app_id": "com.narra.app",
              "channel": "release",
              "latest_version_name": "1.1.0",
              "latest_version_code": 10100,
              "minimum_supported_version_code": 10001,
              "apk_url": "https://downloads.example.com/Narra-v1.1.0-10100-release.apk",
              "apk_sha256": "abcdef",
              "published_at": "2026-03-22T12:00:00+08:00",
              "release_notes": ["新增功能 A", "修复问题 B"]
            }
        """.trimIndent()
    }

    private fun currentVersionJson(): String {
        return """
            {
              "app_id": "com.narra.app",
              "channel": "release",
              "latest_version_name": "1.0.0",
              "latest_version_code": 10000,
              "minimum_supported_version_code": 0,
              "apk_url": "",
              "apk_sha256": "",
              "published_at": "2026-04-11T12:00:00+08:00",
              "release_notes": ["当前已是最新版本"]
            }
        """.trimIndent()
    }
}

private class FakeAppUpdateStateStore(
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
