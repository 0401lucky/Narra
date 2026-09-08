package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsStoreTest {
    private companion object {
        /** 单次 Preferences emission 触发的安全存储读取次数；新增加密字段时同步更新。 */
        const val SECURE_READS_PER_EMISSION = 5
    }

    @Test
    fun settingsFlow_backgroundGlassFlag_preservesThemeForLegacyAndInvalidFields() = runTest {
        val cases = listOf(
            null to true,
            "true" to true,
            "false" to false,
            "null" to true,
            "\"false\"" to true,
            "0" to true,
            "[]" to true,
            "{}" to true,
        )
        for ((rawFlag, expectedEnabled) in cases) {
            val skinJson = JsonParser.parseString(
                """{
                    "preset": "qq",
                    "maxWidthPercent": 84,
                    "bubbleRadiusDp": 12,
                    "bubblePaddingHorizontalDp": 16,
                    "bubblePaddingVerticalDp": 8,
                    "showBubbleTail": true
                }""",
            ).asJsonObject
            if (rawFlag != null) {
                skinJson.add("onlineBackgroundGlassEnabled", JsonParser.parseString(rawFlag))
            }
            val dataStore = MutablePreferencesDataStore(replay = 1)
            dataStore.emit(
                mutablePreferencesOf(
                    stringPreferencesKey("roleplay_no_background_skin_json") to skinJson.toString(),
                ),
            )
            val testDispatcher = StandardTestDispatcher(testScheduler)
            val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
            val store = AppSettingsStore(
                dataStore = dataStore,
                secureValueStore = RecordingSecureValueStore(),
                sharingScope = sharingScope,
                settingsDispatcher = testDispatcher,
            )

            try {
                val restored = store.settingsFlow.first().roleplayNoBackgroundSkin

                assertEquals(
                    "onlineBackgroundGlassEnabled=$rawFlag",
                    RoleplayNoBackgroundSkinSettings(
                        preset = RoleplayNoBackgroundSkinPreset.QQ,
                        maxWidthPercent = 84,
                        bubbleRadiusDp = 12,
                        bubblePaddingHorizontalDp = 16,
                        bubblePaddingVerticalDp = 8,
                        showBubbleTail = true,
                        onlineBackgroundGlassEnabled = expectedEnabled,
                    ),
                    restored,
                )
            } finally {
                sharingScope.cancel()
            }
        }
    }

    @Test
    fun saveDisplaySettings_disabledBackgroundGlass_roundTripsWithOtherBubbleValues() = runTest {
        val dataStore = MutablePreferencesDataStore(replay = 1)
        dataStore.emit(emptyPreferences())
        val secureStore = RecordingSecureValueStore()
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
        )
        val defaults = AppSettings()
        val expectedSkin = RoleplayNoBackgroundSkinSettings(
            preset = RoleplayNoBackgroundSkinPreset.NOIR,
            maxWidthPercent = 88,
            bubbleRadiusDp = 9,
            bubblePaddingHorizontalDp = 17,
            bubblePaddingVerticalDp = 7,
            showBubbleTail = true,
            onlineBackgroundGlassEnabled = false,
        )

        try {
            store.saveDisplaySettings(
                themeMode = defaults.themeMode,
                appColorTheme = defaults.appColorTheme,
                messageTextScale = defaults.messageTextScale,
                reasoningExpandedByDefault = defaults.reasoningExpandedByDefault,
                showThinkingContent = defaults.showThinkingContent,
                autoCollapseThinking = defaults.autoCollapseThinking,
                autoPreviewImages = defaults.autoPreviewImages,
                codeBlockAutoWrap = defaults.codeBlockAutoWrap,
                codeBlockAutoCollapse = defaults.codeBlockAutoCollapse,
                showRoleplayAiHelper = defaults.showRoleplayAiHelper,
                roleplayLongformTargetChars = defaults.roleplayLongformTargetChars,
                showRoleplayPresenceStrip = defaults.showRoleplayPresenceStrip,
                showRoleplayStatusStrip = defaults.showRoleplayStatusStrip,
                showOnlineRoleplayNarration = defaults.showOnlineRoleplayNarration,
                enableRoleplayNetMeme = defaults.enableRoleplayNetMeme,
                roleplayImmersiveMode = defaults.roleplayImmersiveMode,
                roleplayHighContrast = defaults.roleplayHighContrast,
                roleplayLineHeightScale = defaults.roleplayLineHeightScale,
                roleplayNoBackgroundSkin = expectedSkin,
            )
            advanceUntilIdle()

            val storedJson = dataStore.data.first()[
                stringPreferencesKey("roleplay_no_background_skin_json"),
            ].orEmpty()
            assertFalse(
                JsonParser.parseString(storedJson).asJsonObject["onlineBackgroundGlassEnabled"].asBoolean,
            )
            val reopenedStore = AppSettingsStore(
                dataStore = dataStore,
                secureValueStore = secureStore,
                sharingScope = sharingScope,
                settingsDispatcher = testDispatcher,
            )
            assertEquals(expectedSkin, reopenedStore.settingsFlow.first().roleplayNoBackgroundSkin)
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_multipleCollectorsParseEachEmissionOnceAndReplayLatest() = runTest {
        val dataStore = MutablePreferencesDataStore(replay = 1)
        val secureStore = RecordingSecureValueStore()
        var parseCount = 0
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            parseObserver = { parseCount += 1 },
            readRetryDelayMillis = 0L,
        )

        try {
            advanceUntilIdle()
            val firstEmissions = mutableListOf<AppSettings>()
            val secondEmissions = mutableListOf<AppSettings>()
            val firstCollector = launch {
                store.settingsFlow.take(2).toList(firstEmissions)
            }
            val secondCollector = launch {
                store.settingsFlow.take(2).toList(secondEmissions)
            }
            advanceUntilIdle()
            dataStore.emit(emptyPreferences())
            val themeKey = stringPreferencesKey("theme_mode")
            dataStore.emit(mutablePreferencesOf(themeKey to "dark"))
            advanceUntilIdle()

            assertEquals(2, firstEmissions.size)
            assertEquals(2, secondEmissions.size)
            assertEquals(2, parseCount)
            // 安全读次数只随 emission 增长，不随 collector 数量倍增。
            // 每次 emission 读 5 处：legacy key、provider keys、search keys、语音合成 key、语音识别 key。
            val expectedSecureReads = 2 * SECURE_READS_PER_EMISSION
            assertEquals(expectedSecureReads, secureStore.readCount)
            val replayReads = List(3) {
                store.settingsFlow.first().themeMode.storageValue
            }
            assertEquals(listOf("dark", "dark", "dark"), replayReads)
            assertEquals(2, parseCount)
            assertEquals(expectedSecureReads, secureStore.readCount)
            firstCollector.join()
            secondCollector.join()
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_ioFailureEmitsDefaultThenRecoversFromLaterValue() = runTest {
        val expectedFailure = IOException("read failed")
        val preferences = mutablePreferencesOf(stringPreferencesKey("theme_mode") to "dark")
        val dataStore = SequencedPreferencesDataStore(
            sequence = listOf(
                { flow { throw expectedFailure } },
                { flow { emit(preferences) } },
            ),
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = RecordingSecureValueStore(),
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            readRetryDelayMillis = 0L,
        )

        try {
            val emissions = store.settingsFlow.take(2).toList()

            assertEquals("system", emissions.first().themeMode.storageValue)
            assertEquals("dark", emissions.last().themeMode.storageValue)
            assertEquals(2, dataStore.subscriptionCount)
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_nonIoFailureReportsUnavailableWithoutDefaultSnapshot() = runTest {
        val expectedFailure = IllegalStateException("programming failure")
        var unavailable: Throwable? = null
        val dataStore = SequencedPreferencesDataStore(
            sequence = listOf({ flow { throw expectedFailure } }),
        )
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val store = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = RecordingSecureValueStore(),
            sharingScope = sharingScope,
            settingsDispatcher = testDispatcher,
            unavailableObserver = { unavailable = it },
            readRetryDelayMillis = 0L,
        )

        try {
            advanceUntilIdle()

            assertSame(expectedFailure, unavailable)
            assertTrue((store.settingsFlow as SharedFlow).replayCache.isEmpty())
        } finally {
            sharingScope.cancel()
        }
    }

    @Test
    fun settingsFlow_parsesOnInjectedBackgroundDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "settings-parser-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        var parserThread = ""
        val secureStore = RecordingSecureValueStore()
        val sharingScope = CoroutineScope(SupervisorJob() + dispatcher)
        try {
            val dataStore = MutablePreferencesDataStore(replay = 1)
            val store = AppSettingsStore(
                dataStore = dataStore,
                secureValueStore = secureStore,
                sharingScope = sharingScope,
                settingsDispatcher = dispatcher,
                parseObserver = { parserThread = it },
                readRetryDelayMillis = 0L,
            )

            dataStore.emit(emptyPreferences())
            store.settingsFlow.take(1).toList()

            assertTrue(parserThread.startsWith("settings-parser-test"))
            assertFalse(parserThread.contains("main", ignoreCase = true))
            assertEquals(1, secureStore.readThreadNames.size)
            assertTrue(
                secureStore.readThreadNames.all { threadName ->
                    threadName.startsWith("settings-parser-test") &&
                        !threadName.contains("main", ignoreCase = true)
                },
            )
        } finally {
            sharingScope.cancel()
            dispatcher.close()
            executor.shutdownNow()
        }
    }
}

private class MutablePreferencesDataStore(
    replay: Int = 0,
) : DataStore<Preferences> {
    private val upstream = MutableSharedFlow<Preferences>(replay = replay)
    private var current: Preferences = emptyPreferences()
    override val data: Flow<Preferences> = upstream

    suspend fun emit(preferences: Preferences) {
        current = preferences
        upstream.emit(preferences)
    }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(current)
        emit(updated)
        return updated
    }
}

private class SequencedPreferencesDataStore(
    private val sequence: List<() -> Flow<Preferences>>,
) : DataStore<Preferences> {
    var subscriptionCount: Int = 0
        private set

    override val data: Flow<Preferences>
        get() {
            val index = subscriptionCount.coerceAtMost(sequence.lastIndex)
            subscriptionCount += 1
            return sequence[index]()
        }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        error("本测试不应写入 DataStore")
    }
}

private class RecordingSecureValueStore : SettingsSecureValueStore {
    var readCount: Int = 0
        private set
    val readThreadNames = linkedSetOf<String>()

    override fun getString(key: String): String {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return ""
    }

    override fun putString(key: String, value: String) = Unit

    override fun getStringMap(key: String): Map<String, String> {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return emptyMap()
    }

    override fun putStringMap(key: String, values: Map<String, String>) = Unit
}
