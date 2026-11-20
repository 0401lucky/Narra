package com.example.myapplication.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.DEFAULT_MEMORY_RECALL_TOKEN_BUDGET
import com.example.myapplication.model.FunctionModelProviderIds
import com.example.myapplication.model.MEMORY_RECALL_TOKEN_BUDGET_MAX
import com.example.myapplication.model.MEMORY_RECALL_TOKEN_BUDGET_MIN
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.system.json.AppJson
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
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun settingsFlow_legacyAndInvalidEmbeddingFields_disableOnlyEmbedding() = runTest {
        val invalidFields = listOf(
            "embeddingModelMode" to null,
            "embeddingModelMode" to "null",
            "embeddingModelMode" to "\"FOLLOW_DEFAULT\"",
            "embeddingModelMode" to "\"FUTURE_MODE\"",
            "embeddingModelMode" to "\"DISABLED\"",
            "embeddingModelMode" to "false",
            "embeddingModelMode" to "0",
            "embeddingModelMode" to "[]",
            "embeddingModelMode" to "{}",
            "embeddingModel" to null,
            "embeddingModel" to "null",
            "embeddingModel" to "[]",
            "embeddingModel" to "true",
        )
        for ((field, rawValue) in invalidFields) {
            val providerJson = AppJson.gson.toJsonTree(
                ProviderSettings(
                    id = "provider",
                    baseUrl = "https://fixture.example.test/v1/",
                    selectedModel = "fixture-chat",
                    titleSummaryModel = "fixture-summary",
                    memoryModel = "fixture-extraction",
                    embeddingModel = "fixture-vector",
                    embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
                ),
            ).asJsonObject
            if (rawValue == null) providerJson.remove(field)
            else providerJson.add(field, JsonParser.parseString(rawValue))
            withStoreFixture(
                mutablePreferencesOf(
                    stringPreferencesKey("providers_json") to "[$providerJson]",
                    stringPreferencesKey("function_model_provider_ids_json") to
                            """{"memoryProviderId":"provider","embeddingProviderId":"provider"}""",
                ),
            ) { fixture ->
                fixture.secureStore.putStringMap(
                    "provider_api_keys",
                    mapOf("provider" to "fixture-key")
                )
                val settings = fixture.openStore().settingsFlow.first()
                assertEquals("$field=$rawValue", 1, settings.providers.size)
                assertEquals("fixture-chat", settings.activeProvider()?.selectedModel)
                assertEquals(
                    "fixture-summary",
                    settings.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
                )
                assertEquals(
                    "fixture-extraction",
                    settings.resolveFunctionModel(ProviderFunction.MEMORY)
                )
                assertNull(settings.resolveEmbeddingProvider())
                assertEquals("", settings.resolveFunctionModel(ProviderFunction.EMBEDDING))
                assertEquals(
                    ProviderFunctionModelMode.DISABLED,
                    settings.providers.single().embeddingModelMode
                )
            }
        }
    }

    @Test
    fun settingsFlow_invalidEmbeddingAssignment_preservesOtherFunctionAssignments() = runTest {
        for (rawValue in listOf("null", "true", "23", "[]", "{}")) {
            withStoreFixture(
                mutablePreferencesOf(
                    stringPreferencesKey("providers_json") to AppJson.gson.toJson(
                        listOf(
                            ProviderSettings(id = "provider")
                        )
                    ),
                    stringPreferencesKey("function_model_provider_ids_json") to
                            """{"memoryProviderId":"provider","embeddingProviderId":$rawValue}""",
                ),
            ) { fixture ->
                val settings = fixture.openStore().settingsFlow.first()
                assertEquals("provider", settings.functionModelProviderIds.memoryProviderId)
                assertEquals("", settings.functionModelProviderIds.embeddingProviderId)
                assertNull(settings.resolveEmbeddingProvider())
            }
        }
    }

    @Test
    fun saveProviderSettings_embeddingRoundTripsWithSecureKeysAndIndependentSelection() = runTest {
        withStoreFixture { fixture ->
            val store = fixture.openStore()
            val chat = ProviderSettings(
                id = "chat",
                baseUrl = "https://chat.example.test/v1/",
                apiKey = "sk-fixture-chat-secret",
                selectedModel = "fixture-chat",
                memoryModel = "fixture-extraction",
            )
            val embedding = ProviderSettings(
                id = "embedding",
                baseUrl = "https://embedding.example.test/v1/",
                apiKey = "sk-fixture-embedding-secret",
                embeddingModel = "  fixture-vector  ",
                embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
            )
            store.saveMemorySettings(autoSummaryEvery = 7, capacity = 1350)
            store.saveMemoryRecallTokenBudget(2345)
            store.saveMemoryPromptSettings("合成提取模板", "合成注入模板")
            store.saveProviderSettings(listOf(chat, embedding), chat.id)
            store.saveFunctionModelProviderIds(FunctionModelProviderIds(embeddingProviderId = embedding.id))
            advanceUntilIdle()

            val preferences = fixture.dataStore.data.first()
            val ordinaryValues = preferences.asMap().values.joinToString()
            assertFalse(ordinaryValues.contains(chat.apiKey))
            assertFalse(ordinaryValues.contains(embedding.apiKey))
            assertEquals(
                embedding.apiKey,
                fixture.secureStore.getStringMap("provider_api_keys")[embedding.id]
            )

            val restored = fixture.openStore().settingsFlow.first()
            assertEquals(chat.id, restored.activeProvider()?.id)
            assertEquals(chat.selectedModel, restored.activeProvider()?.selectedModel)
            assertEquals(chat.memoryModel, restored.resolveFunctionModel(ProviderFunction.MEMORY))
            assertEquals(embedding.id, restored.resolveEmbeddingProvider()?.id)
            assertEquals(embedding.apiKey, restored.resolveEmbeddingProvider()?.apiKey)
            assertEquals(
                "fixture-vector",
                restored.resolveFunctionModel(ProviderFunction.EMBEDDING)
            )
            assertEquals(7, restored.memoryAutoSummaryEvery)
            assertEquals(1350, restored.memoryCapacity)
            assertEquals(2345, restored.memoryRecallTokenBudget)
            assertEquals("合成提取模板", restored.memoryExtractionPrompt)
            assertEquals("合成注入模板", restored.memoryInjectionPrompt)

            store.saveProviderSettings(
                listOf(
                    chat,
                    embedding.copy(embeddingModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT)
                ),
                chat.id,
            )
            advanceUntilIdle()
            assertNull(store.settingsFlow.first().resolveEmbeddingProvider())
            val storedProviders = JsonParser.parseString(
                fixture.dataStore.data.first()[stringPreferencesKey("providers_json")],
            ).asJsonArray
            assertEquals("DISABLED", storedProviders[1].asJsonObject["embeddingModelMode"].asString)

            store.saveProviderSettings(listOf(chat), chat.id)
            advanceUntilIdle()
            val afterDelete = fixture.openStore().settingsFlow.first()
            assertNull(afterDelete.resolveEmbeddingProvider())
            assertEquals("", afterDelete.functionModelProviderIds.embeddingProviderId)
            assertEquals(
                chat.selectedModel,
                afterDelete.resolveFunctionModel(ProviderFunction.CHAT)
            )
        }
    }

    @Test
    fun migrateSensitiveData_legacyProviderKeyMovesToSecureStoreWithoutEnablingEmbedding() =
        runTest {
            withStoreFixture(
                mutablePreferencesOf(
                    stringPreferencesKey("providers_json") to
                            """[{"id":"legacy","baseUrl":"https://fixture.example.test/v1/","apiKey":"sk-fixture-legacy-secret","selectedModel":"fixture-chat","memoryModel":"fixture-extraction"}]""",
                ),
            ) { fixture ->
                val store = fixture.openStore()
                store.migrateSensitiveData()
                advanceUntilIdle()
                assertEquals(
                    "sk-fixture-legacy-secret",
                    fixture.secureStore.getStringMap("provider_api_keys")["legacy"]
                )
                assertFalse(
                    fixture.dataStore.data.first().asMap().values.joinToString()
                        .contains("sk-fixture-legacy-secret")
                )
                val restored = fixture.openStore().settingsFlow.first()
                assertEquals(
                    "fixture-extraction",
                    restored.resolveFunctionModel(ProviderFunction.MEMORY)
                )
                assertNull(restored.resolveEmbeddingProvider())
                assertEquals(DEFAULT_MEMORY_RECALL_TOKEN_BUDGET, restored.memoryRecallTokenBudget)
            }
        }

    @Test
    fun saveMemoryRecallTokenBudget_clampsAndPreservesOtherMemorySettings() = runTest {
        withStoreFixture { fixture ->
            val store = fixture.openStore()
            assertEquals(
                DEFAULT_MEMORY_RECALL_TOKEN_BUDGET,
                store.settingsFlow.first().memoryRecallTokenBudget
            )
            store.saveMemorySettings(autoSummaryEvery = 13, capacity = 2750)
            store.saveMemoryPromptSettings("现有提取模板", "现有注入模板")
            for ((raw, expected) in listOf(
                -1 to MEMORY_RECALL_TOKEN_BUDGET_MIN,
                Int.MAX_VALUE to MEMORY_RECALL_TOKEN_BUDGET_MAX,
                3456 to 3456
            )) {
                store.saveMemoryRecallTokenBudget(raw)
                advanceUntilIdle()
                val restored = fixture.openStore().settingsFlow.first()
                assertEquals(expected, restored.memoryRecallTokenBudget)
                assertEquals(13, restored.memoryAutoSummaryEvery)
                assertEquals(2750, restored.memoryCapacity)
                assertEquals("现有提取模板", restored.memoryExtractionPrompt)
                assertEquals("现有注入模板", restored.memoryInjectionPrompt)
            }
        }
    }

    @Test
    fun residentLimits_roundTripDefaultsClampingAndOtherSettings() = runTest {
        withStoreFixture { fixture ->
            val store = fixture.openStore()
            assertEquals(
                com.example.myapplication.model.MemoryResidentLimits(12, 10, 10, 8),
                store.settingsFlow.first().memoryResidentLimits
            )
            store.saveMemorySettings(13, 2750)
            store.saveMemoryRecallTokenBudget(3456)
            store.saveMemoryPromptSettings("保留用户模板", "保留注入模板")
            store.saveMemoryResidentLimits(
                com.example.myapplication.model.MemoryResidentLimits(
                    -1,
                    200,
                    4,
                    0
                )
            )
            advanceUntilIdle()
            val restored = fixture.openStore().settingsFlow.first()
            assertEquals(
                com.example.myapplication.model.MemoryResidentLimits(0, 100, 4, 0),
                restored.memoryResidentLimits
            )
            assertEquals(13, restored.memoryAutoSummaryEvery)
            assertEquals(2750, restored.memoryCapacity)
            assertEquals(3456, restored.memoryRecallTokenBudget)
            assertEquals("保留用户模板", restored.memoryExtractionPrompt)
            assertEquals("保留注入模板", restored.memoryInjectionPrompt)
        }
    }

    private suspend fun TestScope.withStoreFixture(
        initial: Preferences = emptyPreferences(),
        block: suspend (StoreFixture) -> Unit,
    ) {
        val fixture = StoreFixture(testScheduler)
        fixture.dataStore.emit(initial)
        try {
            block(fixture)
        } finally {
            fixture.sharingScope.cancel()
        }
    }

    private class StoreFixture(scheduler: TestCoroutineScheduler) {
        val dataStore = MutablePreferencesDataStore(replay = 1)
        val secureStore = RecordingSecureValueStore()
        private val dispatcher = StandardTestDispatcher(scheduler)
        val sharingScope = CoroutineScope(SupervisorJob() + dispatcher)

        fun openStore() = AppSettingsStore(
            dataStore = dataStore,
            secureValueStore = secureStore,
            sharingScope = sharingScope,
            settingsDispatcher = dispatcher,
        )
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
    private val strings = mutableMapOf<String, String>()
    private val maps = mutableMapOf<String, Map<String, String>>()

    override fun getString(key: String): String {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return strings[key].orEmpty()
    }

    override fun putString(key: String, value: String) {
        strings[key] = value
    }

    override fun getStringMap(key: String): Map<String, String> {
        readCount += 1
        readThreadNames += Thread.currentThread().name
        return maps[key].orEmpty()
    }

    override fun putStringMap(key: String, values: Map<String, String>) {
        maps[key] = values.toMap()
    }
}
