package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.context.ContextTransferCodec
import com.example.myapplication.viewmodel.ContextImportPayload
import com.example.myapplication.viewmodel.ContextTransferSection
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetSamplerConfig
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakePresetRepository
import com.example.myapplication.testutil.FakeSettingsStore
import com.example.myapplication.testutil.FakeWorldBookRepository
import com.example.myapplication.testutil.TestAiServices
import com.example.myapplication.testutil.createTestAiServices
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class ContextTransferViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun importBundleJson_mergesAssistantsWorldBookMemoryAndSummaries() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "assistant-existing",
                        name = "现有助手",
                    ),
                ),
                selectedAssistantId = "assistant-existing",
            ),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val worldBookRepository = FakeWorldBookRepository()
        val memoryRepository = FakeMemoryRepository()
        val summaryRepository = FakeConversationSummaryRepository()
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = worldBookRepository,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = summaryRepository,
        )

        advanceUntilIdle()

        val rawJson = ContextTransferCodec().encode(
            com.example.myapplication.model.ContextDataBundle(
                assistants = listOf(
                    Assistant(
                        id = "assistant-imported",
                        name = "导入助手",
                    ),
                ),
                worldBookEntries = listOf(
                    WorldBookEntry(
                        id = "world-1",
                        title = "白塔城",
                        content = "北境贸易都会",
                    ),
                ),
                memoryEntries = listOf(
                    MemoryEntry(
                        id = "memory-1",
                        scopeType = MemoryScopeType.GLOBAL,
                        content = "用户喜欢短句回复",
                    ),
                ),
                conversationSummaries = listOf(
                    ConversationSummary(
                        conversationId = "c1",
                        summary = "已经整理好前文摘要。",
                        coveredMessageCount = 10,
                    ),
                ),
            ),
        )

        viewModel.previewImportJson(rawJson, ContextTransferSection.ALL)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.importPreview?.assistantCount)
        assertEquals(1, viewModel.uiState.value.importPreview?.worldBookCount)
        assertEquals(1, viewModel.uiState.value.importPreview?.memoryCount)

        viewModel.confirmImport()
        advanceUntilIdle()

        assertTrue(services.settingsRepository.settingsFlow.first().assistants.any { it.id == "assistant-existing" })
        assertTrue(services.settingsRepository.settingsFlow.first().assistants.any { it.id == "assistant-imported" })
        assertEquals("白塔城", worldBookRepository.listEntries().single().title)
        assertEquals("用户喜欢短句回复", memoryRepository.currentEntries().single().content)
        assertEquals("已经整理好前文摘要。", summaryRepository.getSummary("c1")?.summary)
        assertEquals("上下文数据已合并导入", viewModel.uiState.value.message)
    }

    @Test
    fun exportBundleJson_assistantSectionIncludesCustomAssistants() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "assistant-export",
                        name = "导出助手",
                    ),
                ),
            ),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        var exportedJson = ""
        viewModel.exportBundleJson(ContextTransferSection.ASSISTANTS) { json, _ ->
            exportedJson = json
        }
        advanceUntilIdle()

        val decoded = ContextTransferCodec().decode(exportedJson)
        assertEquals(listOf("assistant-export"), decoded.assistants.map { it.id })
        assertEquals(0, decoded.worldBookEntries.size)
        assertEquals(0, decoded.memoryEntries.size)
        assertEquals(0, decoded.conversationSummaries.size)
    }

    @Test
    fun exportBundleJson_worldBookSectionOnlyIncludesWorldBookEntries() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val worldBookRepository = FakeWorldBookRepository(
            initialEntries = listOf(
                WorldBookEntry(
                    id = "world-1",
                    title = "白塔城",
                    content = "北境贸易都会",
                ),
            ),
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = worldBookRepository,
            memoryRepository = FakeMemoryRepository(
                initialEntries = listOf(
                    MemoryEntry(
                        id = "memory-1",
                        content = "不会被导出",
                    ),
                ),
            ),
            conversationSummaryRepository = FakeConversationSummaryRepository(
                initialSummaries = listOf(
                    ConversationSummary(
                        conversationId = "c1",
                        summary = "不会被导出",
                    ),
                ),
            ),
        )

        advanceUntilIdle()

        var exportedJson = ""
        viewModel.exportBundleJson(ContextTransferSection.WORLD_BOOK) { json, _ ->
            exportedJson = json
        }
        advanceUntilIdle()

        val decoded = ContextTransferCodec().decode(exportedJson)
        assertEquals(0, decoded.assistants.size)
        assertEquals(1, decoded.worldBookEntries.size)
        assertEquals(0, decoded.memoryEntries.size)
        assertEquals(0, decoded.conversationSummaries.size)
    }

    @Test
    fun exportAndImportPresetPack_roundTripsCustomPresetsOnly() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val presetRepository = FakePresetRepository(
            initialPresets = listOf(
                Preset(
                    id = "builtin-preset",
                    name = "内置预设",
                    builtIn = true,
                ),
                Preset(
                    id = "custom-preset",
                    name = "自定义预设",
                    sampler = PresetSamplerConfig(temperature = 0.66f),
                ),
            ),
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            presetRepository = presetRepository,
        )

        advanceUntilIdle()

        var exportedJson = ""
        viewModel.exportBundleJson(ContextTransferSection.PRESETS) { json, _ ->
            exportedJson = json
        }
        advanceUntilIdle()

        val decoded = ContextTransferCodec().decode(exportedJson)
        assertEquals(listOf("custom-preset"), decoded.presets.map { it.id })
        assertEquals(0.66f, decoded.presets.single().sampler.temperature ?: 0f, 0.0001f)

        val targetRepository = FakePresetRepository()
        val importViewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            presetRepository = targetRepository,
        )

        importViewModel.previewImportJson(exportedJson, ContextTransferSection.PRESETS)
        advanceUntilIdle()
        assertEquals(1, importViewModel.uiState.value.importPreview?.presetCount)

        importViewModel.confirmImport()
        advanceUntilIdle()

        val imported = targetRepository.currentPresets().single()
        assertEquals("custom-preset", imported.id)
        assertTrue(imported.userModified)
        assertEquals(false, imported.builtIn)
    }

    @Test
    fun previewImportJson_detectsConflictAndSupportsTavernSource() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "existing-id",
                        name = "现有助手",
                    ),
                ),
            ),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val conflictJson = ContextTransferCodec().encode(
            com.example.myapplication.model.ContextDataBundle(
                assistants = listOf(
                    Assistant(
                        id = "existing-id",
                        name = "冲突助手",
                    ),
                ),
            ),
        )

        viewModel.previewImportJson(conflictJson, ContextTransferSection.ASSISTANTS)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.importPreview?.conflicts?.size)
        assertEquals("角色卡", viewModel.uiState.value.importPreview?.conflicts?.single()?.typeLabel)

        val tavernJson = """
            {
              "name": "白塔侦探",
              "description": "擅长调查失窃案。",
              "scenario": "你正在白塔城破案。",
              "first_mes": "先把你知道的都告诉我。"
            }
        """.trimIndent()

        viewModel.previewImportJson(tavernJson, ContextTransferSection.ASSISTANTS)
        advanceUntilIdle()

        assertEquals("Tavern 角色卡", viewModel.uiState.value.importPreview?.sourceLabel)
        assertEquals(1, viewModel.uiState.value.importPreview?.assistantCount)
    }

    @Test
    fun previewImportPayload_supportsTavernImageCard() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val tavernJson = """
            {
              "name": "白塔侦探",
              "description": "擅长调查失窃案。",
              "scenario": "你正在白塔城破案。"
            }
        """.trimIndent()
        val pngPayload = ContextImportPayload(
            fileName = "detective.png",
            mimeType = "image/png",
            binaryContent = buildPngCharacterCard(tavernJson),
        )

        viewModel.previewImportPayload(pngPayload, ContextTransferSection.ASSISTANTS)
        advanceUntilIdle()

        assertEquals("Tavern 图片角色卡", viewModel.uiState.value.importPreview?.sourceLabel)
        assertEquals(1, viewModel.uiState.value.importPreview?.assistantCount)
    }

    @Test
    fun previewImportPayload_supportsBracketWrappedTavernImageCard() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val tavernJson = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "夜巡者",
                "description": "负责守夜的调查员。",
                "scenario": "你正在旧港追查失踪案。"
              }
            }
        """.trimIndent()
        val pngPayload = ContextImportPayload(
            fileName = "night-watch.png",
            mimeType = "image/png",
            binaryContent = buildWrappedPngCharacterCard(tavernJson),
        )

        viewModel.previewImportPayload(pngPayload, ContextTransferSection.ASSISTANTS)
        advanceUntilIdle()

        assertEquals("Tavern 图片角色卡", viewModel.uiState.value.importPreview?.sourceLabel)
        assertEquals(1, viewModel.uiState.value.importPreview?.assistantCount)
    }

    @Test
    fun confirmImport_tavernAssistantSectionAlsoImportsScopedWorldBookEntries() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val worldBookRepository = FakeWorldBookRepository()
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = worldBookRepository,
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val tavernJson = """
            {
              "spec": "chara_card_v3",
              "data": {
                "name": "夜巡者",
                "description": "负责守夜的调查员。",
                "extensions": {
                  "lore": {
                    "character_book": {
                      "name": "璃珠都市设定",
                      "entries": [
                        {
                          "name": "璃珠都市",
                          "keys": ["璃珠都市", "港城"],
                          "content": "璃珠都市是一座不夜港城。"
                        },
                        {
                          "name": "夜巡守则",
                          "keys": ["夜巡", "巡逻"],
                          "content": "午夜之后必须避开旧钟楼北侧。"
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val payload = ContextImportPayload(
            fileName = "night-watch.png",
            mimeType = "image/png",
            binaryContent = buildPngCharacterCard(tavernJson),
        )

        viewModel.previewImportPayload(payload, ContextTransferSection.ASSISTANTS)
        advanceUntilIdle()
        assertEquals("Tavern 图片角色卡", viewModel.uiState.value.importPreview?.sourceLabel)
        assertEquals(1, viewModel.uiState.value.importPreview?.assistantCount)
        assertEquals(2, viewModel.uiState.value.importPreview?.worldBookCount)

        viewModel.confirmImport()
        advanceUntilIdle()

        val importedAssistant = services.settingsRepository.settingsFlow.first().assistants.single()
        assertEquals(2, importedAssistant.linkedWorldBookIds.size)
        assertEquals(listOf("璃珠都市", "夜巡守则"), worldBookRepository.listEntries().map { it.title })
        assertTrue(worldBookRepository.listEntries().all { it.scopeId == importedAssistant.id })
    }

    @Test
    fun confirmImport_tavernImageCardImportsWorldBookAndAvatar() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val worldBookRepository = FakeWorldBookRepository()
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = worldBookRepository,
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            importedAssistantAvatarSaver = { "file:///avatars/${it.assistantId}.png" },
        )

        advanceUntilIdle()

        val tavernJson = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "夜巡者",
                "description": "负责守夜的调查员。",
                "character_book": {
                  "name": "璃珠都市设定",
                  "entries": [
                    {
                      "name": "璃珠都市",
                      "keys": ["/璃珠(都|城)市/i"],
                      "content": "璃珠都市是一座不夜港城。"
                    },
                    {
                      "name": "夜巡守则",
                      "keys": ["夜巡"],
                      "secondary_keys": ["午夜"],
                      "selective": true,
                      "content": "午夜的旧钟楼区域属于高风险地带。"
                    }
                  ]
                }
              }
            }
        """.trimIndent()
        val payload = ContextImportPayload(
            fileName = "night-watch.png",
            mimeType = "image/png",
            binaryContent = buildPngCharacterCard(tavernJson),
        )

        viewModel.previewImportPayload(payload, ContextTransferSection.ALL)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.importPreview?.worldBookCount)

        viewModel.confirmImport()
        advanceUntilIdle()

        val importedAssistant = services.settingsRepository.settingsFlow.first().assistants.single()
        assertEquals("file:///avatars/${importedAssistant.id}.png", importedAssistant.avatarUri)
        assertEquals(listOf("璃珠都市", "夜巡守则"), worldBookRepository.listEntries().map { it.title })
        assertTrue(worldBookRepository.listEntries().all { it.scopeId == importedAssistant.id })
        assertEquals(2, importedAssistant.linkedWorldBookIds.size)
    }

    @Test
    fun previewImportJson_supportsStandaloneLorebookJson() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val lorebookJson = """
            {
              "name": "璃珠都市设定",
              "entries": {
                "10": {
                  "uid": 10,
                  "comment": "璃珠都市",
                  "key": ["璃珠都市", "港城"],
                  "content": "璃珠都市是一座不夜港城。",
                  "order": 120
                },
                "11": {
                  "uid": 11,
                  "comment": "夜巡守则",
                  "key": ["夜巡"],
                  "keysecondary": ["午夜"],
                  "selective": true,
                  "content": "午夜之后必须避开旧钟楼北侧。"
                }
              }
            }
        """.trimIndent()

        viewModel.previewImportPayload(
            payload = ContextImportPayload(
                fileName = "lorebook.json",
                mimeType = "application/json",
                textContent = lorebookJson,
            ),
            section = ContextTransferSection.WORLD_BOOK,
        )
        advanceUntilIdle()

        assertEquals("独立世界书", viewModel.uiState.value.importPreview?.sourceLabel)
        assertEquals(2, viewModel.uiState.value.importPreview?.worldBookCount)
    }

    @Test
    fun confirmImport_standaloneLorebookCreatesAttachableEntries() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val worldBookRepository = FakeWorldBookRepository()
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = worldBookRepository,
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val lorebookJson = """
            {
              "name": "北境档案",
              "entries": [
                {
                  "uid": 1,
                  "comment": "白塔城",
                  "key": ["白塔城"],
                  "content": "白塔城是北境最大的贸易都会。"
                }
              ]
            }
        """.trimIndent()

        viewModel.previewImportPayload(
            payload = ContextImportPayload(
                fileName = "north-archive.json",
                mimeType = "application/json",
                textContent = lorebookJson,
            ),
            section = ContextTransferSection.WORLD_BOOK,
        )
        advanceUntilIdle()

        viewModel.confirmImport()
        advanceUntilIdle()

        val entry = worldBookRepository.listEntries().single()
        assertEquals(WorldBookScopeType.ATTACHABLE, entry.scopeType)
        assertEquals("", entry.scopeId)
        assertEquals("北境档案", entry.sourceBookName)
    }

    @Test
    fun previewImportPayload_worldBookSectionSupportsTavernPngExtraction() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val services = createTestAiServices(
            settings = AppSettings(),
            dispatcher = mainDispatcherRule.dispatcher,
        )
        val viewModel = createContextTransferViewModel(
            services = services,
            worldBookRepository = FakeWorldBookRepository(),
            memoryRepository = FakeMemoryRepository(),
            conversationSummaryRepository = FakeConversationSummaryRepository(),
        )

        advanceUntilIdle()

        val tavernJson = """
            {
              "spec": "chara_card_v2",
              "data": {
                "name": "夜巡者",
                "description": "负责守夜的调查员。",
                "character_book": {
                  "name": "璃珠都市设定",
                  "entries": [
                    {
                      "name": "璃珠都市",
                      "keys": ["璃珠都市"],
                      "content": "璃珠都市是一座不夜港城。"
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        viewModel.previewImportPayload(
            payload = ContextImportPayload(
                fileName = "night-watch.png",
                mimeType = "image/png",
                binaryContent = buildPngCharacterCard(tavernJson),
            ),
            section = ContextTransferSection.WORLD_BOOK,
        )
        advanceUntilIdle()

        val preview = viewModel.uiState.value.importPreview
        assertEquals(0, preview?.assistantCount)
        assertEquals(1, preview?.worldBookCount)
    }

    private fun createContextTransferViewModel(
        services: TestAiServices,
        worldBookRepository: FakeWorldBookRepository,
        memoryRepository: FakeMemoryRepository,
        conversationSummaryRepository: FakeConversationSummaryRepository,
        presetRepository: FakePresetRepository = FakePresetRepository(),
        importedAssistantAvatarSaver: suspend (AssistantAvatarImport) -> String? = { null },
    ): ContextTransferViewModel {
        return ContextTransferViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            worldBookRepository = worldBookRepository,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            presetRepository = presetRepository,
            importedAssistantAvatarSaver = importedAssistantAvatarSaver,
        )
    }

    private fun buildPngCharacterCard(json: String): ByteArray {
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()
        output.write(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            ),
        )
        writeChunk(output, "IHDR", ByteArray(13))
        writeChunk(output, "tEXt", "chara\u0000$base64".toByteArray(StandardCharsets.ISO_8859_1))
        writeChunk(output, "IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun buildWrappedPngCharacterCard(json: String): ByteArray {
        val base64 = Base64.getEncoder().encodeToString(json.toByteArray(StandardCharsets.UTF_8))
        val output = ByteArrayOutputStream()
        output.write(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A,
            ),
        )
        writeChunk(output, "IHDR", ByteArray(13))
        writeChunk(output, "tEXt", "Comment\u0000[chara:$base64]".toByteArray(StandardCharsets.ISO_8859_1))
        writeChunk(output, "IEND", ByteArray(0))
        return output.toByteArray()
    }

    private fun writeChunk(
        output: ByteArrayOutputStream,
        type: String,
        data: ByteArray,
    ) {
        output.write(ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(data.size).array())
        output.write(type.toByteArray(StandardCharsets.ISO_8859_1))
        output.write(data)
        output.write(ByteArray(4))
    }
}
