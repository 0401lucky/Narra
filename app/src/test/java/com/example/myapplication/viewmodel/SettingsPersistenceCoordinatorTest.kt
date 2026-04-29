package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.FunctionModelProviderIds
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceIds
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.UserPersonaMask
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPersistenceCoordinatorTest {
    @Test
    fun saveSettings_persistsProvidersDisplayAndScreenTranslation() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)
        val provider = ProviderSettings(
            id = "provider-1",
            name = "  Provider A  ",
            baseUrl = " https://example.com/v1/ ",
            apiKey = " key ",
            selectedModel = " model-a ",
        )
        val state = SettingsUiState(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            functionModelProviderIds = FunctionModelProviderIds(
                titleSummaryProviderId = provider.id,
            ),
            themeMode = ThemeMode.DARK,
            messageTextScale = 1.1f,
            reasoningExpandedByDefault = false,
            showThinkingContent = false,
            autoCollapseThinking = false,
            autoPreviewImages = false,
            codeBlockAutoWrap = true,
            codeBlockAutoCollapse = true,
            showRoleplayAiHelper = false,
            roleplayLongformTargetChars = 1200,
            showRoleplayPresenceStrip = false,
            showRoleplayStatusStrip = true,
            showOnlineRoleplayNarration = false,
            roleplayImmersiveMode = RoleplayImmersiveMode.HIDE_SYSTEM_BARS,
            roleplayHighContrast = true,
            roleplayLineHeightScale = RoleplayLineHeightScale.RELAXED,
            roleplayNoBackgroundSkin = RoleplayNoBackgroundSkinSettings(
                preset = RoleplayNoBackgroundSkinPreset.IMESSAGE,
                maxWidthPercent = 76,
            ),
            screenTranslationSettings = ScreenTranslationSettings(
                serviceEnabled = true,
                targetLanguage = "日语",
            ),
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.BRAVE,
                        type = SearchSourceType.BRAVE,
                        name = "Brave 搜索",
                        enabled = true,
                        apiKey = "search-key",
                    ),
                ),
                selectedSourceId = SearchSourceIds.BRAVE,
                defaultResultCount = 6,
            ),
        )

        val result = coordinator.saveSettings(state)

        assertEquals("设置已保存", result.message)
        assertEquals("Provider A", editor.savedProviders.single().name)
        assertEquals("https://example.com/v1/", editor.savedProviders.single().baseUrl)
        assertEquals("model-a", editor.savedProviders.single().selectedModel)
        assertEquals(provider.id, editor.savedSelectedProviderId)
        assertEquals(provider.id, editor.savedFunctionModelProviderIds.providerIdFor(ProviderFunction.TITLE_SUMMARY))
        assertEquals(provider.id, result.functionModelProviderIds?.providerIdFor(ProviderFunction.TITLE_SUMMARY))
        assertEquals(ThemeMode.DARK, editor.savedThemeMode)
        assertFalse(editor.savedReasoningExpandedByDefault)
        assertTrue(editor.savedCodeBlockAutoWrap)
        assertTrue(editor.savedCodeBlockAutoCollapse)
        assertEquals(RoleplayImmersiveMode.HIDE_SYSTEM_BARS, editor.savedRoleplayImmersiveMode)
        assertTrue(editor.savedRoleplayHighContrast)
        assertFalse(editor.savedShowOnlineRoleplayNarration)
        assertEquals(RoleplayLineHeightScale.RELAXED, editor.savedRoleplayLineHeightScale)
        assertEquals(RoleplayNoBackgroundSkinPreset.IMESSAGE, editor.savedRoleplayNoBackgroundSkin.preset)
        assertEquals(76, editor.savedRoleplayNoBackgroundSkin.maxWidthPercent)
        assertEquals("日语", editor.savedScreenTranslationSettings.targetLanguage)
        assertTrue(editor.savedScreenTranslationSettings.serviceEnabled)
        assertEquals(6, editor.savedSearchSettings.defaultResultCount)
        assertEquals("Brave 搜索", editor.savedSearchSettings.sources.single().name)
    }

    @Test
    fun saveSelectedModel_updatesDraftProvidersForCurrentSelection() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider A",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "old-model",
            availableModels = listOf("old-model"),
        )

        val result = coordinator.saveSelectedModel(
            savedSettings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            currentProviders = listOf(provider),
            currentSelectedProviderId = provider.id,
            selectedModel = "new-model",
        )

        assertEquals("模型已切换为 new-model", result.message)
        assertEquals("new-model", editor.savedProviders.single().selectedModel)
        assertTrue(editor.savedProviders.single().availableModels.contains("new-model"))
        assertEquals("new-model", result.providers.single().selectedModel)
    }

    @Test
    fun saveSelectedProvider_prefersDraftProvidersWhenSavedSettingsStillStale() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
        )
        val draftProvider = ProviderSettings(
            id = "provider-draft",
            name = "Draft Provider",
            baseUrl = "https://draft.example.com/v1/",
            apiKey = "draft-key",
        )

        val result = coordinator.saveSelectedProvider(
            savedSettings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
            draftProviders = listOf(providerA, draftProvider),
            providerId = draftProvider.id,
        )

        assertEquals("已切换到 Draft Provider", result?.message)
        assertEquals(draftProvider.id, editor.savedSelectedProviderId)
        assertEquals(2, editor.savedProviders.size)
        assertEquals(2, result?.providers?.size)
        assertEquals(draftProvider.id, result?.selectedProviderId)
    }

    @Test
    fun saveSelectedModel_prefersCurrentSelectedDraftProviderWhenSavedSettingsStillStale() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)
        val providerA = ProviderSettings(
            id = "provider-a",
            name = "Provider A",
            baseUrl = "https://a.example.com/v1/",
            apiKey = "key-a",
            selectedModel = "model-a",
            availableModels = listOf("model-a"),
        )
        val draftProvider = ProviderSettings(
            id = "provider-draft",
            name = "Draft Provider",
            baseUrl = "https://draft.example.com/v1/",
            apiKey = "draft-key",
        )

        val result = coordinator.saveSelectedModel(
            savedSettings = AppSettings(
                providers = listOf(providerA),
                selectedProviderId = providerA.id,
            ),
            currentProviders = listOf(providerA, draftProvider),
            currentSelectedProviderId = draftProvider.id,
            selectedModel = "gpt-4o-mini",
        )

        val persistedDraftProvider = editor.savedProviders.first { it.id == draftProvider.id }
        assertEquals(draftProvider.id, editor.savedSelectedProviderId)
        assertEquals("gpt-4o-mini", persistedDraftProvider.selectedModel)
        assertTrue(persistedDraftProvider.availableModels.contains("gpt-4o-mini"))
        assertEquals(draftProvider.id, result.selectedProviderId)
        assertEquals("gpt-4o-mini", result.providers.first { it.id == draftProvider.id }.selectedModel)
        assertEquals("model-a", result.providers.first { it.id == providerA.id }.selectedModel)
    }

    @Test
    fun saveUserProfile_delegatesToEditor() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)

        val result = coordinator.saveUserProfile(
            displayName = "测试用户",
            personaPrompt = "一个会主动试探边界的角色扮演用户",
            avatarUri = "content://avatar/test",
            avatarUrl = "https://cdn.example.com/avatar.png",
        )

        assertEquals("个人资料已更新", result.message)
        assertEquals("测试用户", editor.savedDisplayName)
        assertEquals("一个会主动试探边界的角色扮演用户", editor.savedPersonaPrompt)
        assertEquals("content://avatar/test", editor.savedAvatarUri)
        assertEquals("https://cdn.example.com/avatar.png", editor.savedAvatarUrl)
    }

    @Test
    fun saveUserPersonaMasks_delegatesToEditor() = runBlocking {
        val editor = RecordingSettingsEditor()
        val coordinator = SettingsPersistenceCoordinator(editor)
        val mask = UserPersonaMask(
            id = "mask-1",
            name = "测试面具",
            personaPrompt = "测试面具人设",
        )

        val result = coordinator.saveUserPersonaMasks(
            masks = listOf(mask),
            defaultMaskId = mask.id,
        )

        assertEquals("面具已更新", result.message)
        assertEquals(listOf(mask), editor.savedUserPersonaMasks)
        assertEquals("mask-1", editor.savedDefaultUserPersonaMaskId)
    }

    private class RecordingSettingsEditor : AiSettingsEditor {
        var savedProviders: List<ProviderSettings> = emptyList()
        var savedSelectedProviderId: String = ""
        var savedThemeMode: ThemeMode = ThemeMode.SYSTEM
        var savedMessageTextScale: Float = 1f
        var savedReasoningExpandedByDefault: Boolean = true
        var savedShowThinkingContent: Boolean = true
        var savedAutoCollapseThinking: Boolean = true
        var savedAutoPreviewImages: Boolean = true
        var savedCodeBlockAutoWrap: Boolean = false
        var savedCodeBlockAutoCollapse: Boolean = false
        var savedShowRoleplayAiHelper: Boolean = true
        var savedRoleplayLongformTargetChars: Int = 0
        var savedShowRoleplayPresenceStrip: Boolean = true
        var savedShowRoleplayStatusStrip: Boolean = false
        var savedShowOnlineRoleplayNarration: Boolean = true
        var savedRoleplayImmersiveMode: RoleplayImmersiveMode = RoleplayImmersiveMode.EDGE_TO_EDGE
        var savedRoleplayHighContrast: Boolean = false
        var savedRoleplayLineHeightScale: RoleplayLineHeightScale = RoleplayLineHeightScale.NORMAL
        var savedRoleplayNoBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings()
        var savedScreenTranslationSettings: ScreenTranslationSettings = ScreenTranslationSettings()
        var savedSearchSettings: SearchSettings = SearchSettings()
        var savedDisplayName: String = ""
        var savedPersonaPrompt: String = ""
        var savedAvatarUri: String = ""
        var savedAvatarUrl: String = ""
        var savedUserPersonaMasks: List<UserPersonaMask> = emptyList()
        var savedDefaultUserPersonaMaskId: String = ""
        var savedFunctionModelProviderIds: com.example.myapplication.model.FunctionModelProviderIds =
            com.example.myapplication.model.FunctionModelProviderIds()

        override suspend fun saveProviderSettings(providers: List<ProviderSettings>, selectedProviderId: String) {
            savedProviders = providers
            savedSelectedProviderId = selectedProviderId
        }

        override suspend fun saveFunctionModelProviderIds(
            functionModelProviderIds: com.example.myapplication.model.FunctionModelProviderIds,
        ) {
            savedFunctionModelProviderIds = functionModelProviderIds
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
            roleplayNoBackgroundSkin: RoleplayNoBackgroundSkinSettings,
        ) {
            savedThemeMode = themeMode
            savedMessageTextScale = messageTextScale
            savedReasoningExpandedByDefault = reasoningExpandedByDefault
            savedShowThinkingContent = showThinkingContent
            savedAutoCollapseThinking = autoCollapseThinking
            savedAutoPreviewImages = autoPreviewImages
            savedCodeBlockAutoWrap = codeBlockAutoWrap
            savedCodeBlockAutoCollapse = codeBlockAutoCollapse
            savedShowRoleplayAiHelper = showRoleplayAiHelper
            savedRoleplayLongformTargetChars = roleplayLongformTargetChars
            savedShowRoleplayPresenceStrip = showRoleplayPresenceStrip
            savedShowRoleplayStatusStrip = showRoleplayStatusStrip
            savedShowOnlineRoleplayNarration = showOnlineRoleplayNarration
            savedRoleplayImmersiveMode = roleplayImmersiveMode
            savedRoleplayHighContrast = roleplayHighContrast
            savedRoleplayLineHeightScale = roleplayLineHeightScale
            savedRoleplayNoBackgroundSkin = roleplayNoBackgroundSkin
        }

        override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) {
            savedScreenTranslationSettings = settings
        }

        override suspend fun saveSearchSettings(settings: SearchSettings) {
            savedSearchSettings = settings
        }

        override suspend fun saveUserProfile(displayName: String, personaPrompt: String, avatarUri: String, avatarUrl: String) {
            savedDisplayName = displayName
            savedPersonaPrompt = personaPrompt
            savedAvatarUri = avatarUri
            savedAvatarUrl = avatarUrl
        }

        override suspend fun saveUserPersonaMasks(masks: List<UserPersonaMask>, defaultMaskId: String) {
            savedUserPersonaMasks = masks
            savedDefaultUserPersonaMaskId = defaultMaskId
        }

        override suspend fun saveDefaultPresetId(presetId: String) = Unit

        override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) = Unit
        override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = Unit
        override suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String) = Unit
        override suspend fun saveMemoryInjectionPosition(position: com.example.myapplication.model.MemoryInjectionPosition) = Unit
        override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = Unit
    }
}
