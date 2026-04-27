package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsModelLoadCoordinatorTest {
    @Test
    fun loadModelsForProvider_returnsPendingFetchedModelsWhenNotPersisting() = runBlocking {
        val coordinator = SettingsModelLoadCoordinator(
            modelCatalogRepository = object : AiModelCatalogRepository {
                override suspend fun fetchModels(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<String> = error("不应调用")
                override suspend fun fetchModelInfos(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<ModelInfo> {
                    return listOf(ModelInfo(modelId = "model-a"), ModelInfo(modelId = "model-b"))
                }
            },
            settingsEditor = RecordingSettingsEditor(),
        )

        val result = coordinator.loadModelsForProvider(
            providerId = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "",
            apiProtocol = com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE,
            persistResult = false,
        )

        assertEquals("已获取 2 个模型，请选择要添加的", result.message)
        assertEquals(listOf("model-a", "model-b"), result.pendingFetchedModels.map { it.modelId })
        assertEquals("provider-1", result.pendingFetchProviderId)
    }

    @Test
    fun loadModelsForProvider_persistsMergedModelsWhenRequested() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsModelLoadCoordinator(
            modelCatalogRepository = object : AiModelCatalogRepository {
                override suspend fun fetchModels(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<String> = error("不应调用")
                override suspend fun fetchModelInfos(baseUrl: String, apiKey: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol): List<ModelInfo> {
                    return listOf(
                        ModelInfo(modelId = "model-a"),
                        ModelInfo(modelId = "model-b"),
                    )
                }
            },
            settingsEditor = editor,
        )
        val persistedProviders = listOf(
            ProviderSettings(
                id = "provider-1",
                name = "Provider",
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "model-a",
                availableModels = listOf("model-a"),
                models = listOf(
                    ModelInfo(
                        modelId = "model-a",
                        abilitiesCustomized = true,
                    ),
                ),
            ),
        )

        val result = coordinator.loadModelsForProvider(
            providerId = "provider-1",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "model-a",
            apiProtocol = com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE,
            persistResult = true,
            persistedProviders = persistedProviders,
            persistedSelectedProviderId = "provider-1",
        )

        assertEquals("模型已同步", result.message)
        assertEquals(listOf("model-a", "model-b"), editor.savedProviders.single().availableModels)
        assertEquals("provider-1", editor.savedSelectedProviderId)
        assertTrue(editor.savedProviders.single().models?.first { it.modelId == "model-a" }?.abilitiesCustomized == true)
    }

    private class RecordingSettingsEditor : AiSettingsEditor {
        var savedProviders: List<ProviderSettings> = emptyList()
        var savedSelectedProviderId: String = ""

        override suspend fun saveProviderSettings(providers: List<ProviderSettings>, selectedProviderId: String) {
            savedProviders = providers
            savedSelectedProviderId = selectedProviderId
        }

        override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) = Unit
        override suspend fun saveAssistants(assistants: List<Assistant>, selectedAssistantId: String) = Unit

        override suspend fun saveDisplaySettings(
            themeMode: ThemeMode,
            messageTextScale: Float,
            reasoningExpandedByDefault: Boolean,
            showThinkingContent: Boolean,
            autoCollapseThinking: Boolean,
            autoPreviewImages: Boolean,
            codeBlockAutoWrap: Boolean,
            codeBlockAutoCollapse: Boolean,
            showRoleplayAiHelper: Boolean,
            roleplayLongformTargetChars: Int,
            showRoleplayPresenceStrip: Boolean,
            showRoleplayStatusStrip: Boolean,
            showOnlineRoleplayNarration: Boolean,
            enableRoleplayNetMeme: Boolean,
            roleplayImmersiveMode: RoleplayImmersiveMode,
            roleplayHighContrast: Boolean,
            roleplayLineHeightScale: RoleplayLineHeightScale,
        ) = Unit

        override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) = Unit

        override suspend fun saveSearchSettings(settings: SearchSettings) = Unit

        override suspend fun saveUserProfile(displayName: String, personaPrompt: String, avatarUri: String, avatarUrl: String) = Unit
        override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) = Unit
        override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = Unit
        override suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String) = Unit
        override suspend fun saveMemoryInjectionPosition(position: com.example.myapplication.model.MemoryInjectionPosition) = Unit
        override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = Unit
    }
}
