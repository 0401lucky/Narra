package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsServicesTest {
    @Test
    fun settingsRepository_exposesSettingsFlow() = runBlocking {
        val settingsStore = FakeSettingsStore(
            AppSettings(
                selectedModel = "gpt-4o-mini",
            ),
        )
        val repository = DefaultAiSettingsRepository(settingsStore)

        val settings = repository.settingsFlow.first()

        assertEquals("gpt-4o-mini", settings.selectedModel)
    }

    @Test
    fun settingsEditor_normalizesProviderBaseUrlBeforeSaving() = runBlocking {
        val settingsStore = FakeSettingsStore(AppSettings())
        val editor = DefaultAiSettingsEditor(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
        )

        editor.saveProviderSettings(
            providers = listOf(
                ProviderSettings(
                    id = "provider-1",
                    name = "Provider",
                    baseUrl = "https://example.com/v1",
                    apiKey = "key",
                    selectedModel = "chat-model",
                ),
            ),
            selectedProviderId = "provider-1",
        )

        val saved = settingsStore.settingsFlow.first()
        assertEquals("https://example.com/v1/", saved.baseUrl)
        assertEquals("https://example.com/v1/", saved.providers.first().baseUrl)
    }

    @Test
    fun settingsEditor_savesDisplayTranslationHistoryAndAssistants() = runBlocking {
        val settingsStore = FakeSettingsStore(AppSettings())
        val editor = DefaultAiSettingsEditor(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
        )

        editor.saveDisplaySettings(
            themeMode = ThemeMode.DARK,
            messageTextScale = 1.1f,
            reasoningExpandedByDefault = false,
            showThinkingContent = false,
            autoCollapseThinking = false,
            autoPreviewImages = false,
            codeBlockAutoWrap = true,
            codeBlockAutoCollapse = true,
            showRoleplayAiHelper = false,
            roleplayLongformTargetChars = 900,
            showRoleplayPresenceStrip = false,
            showRoleplayStatusStrip = true,
            roleplayImmersiveMode = RoleplayImmersiveMode.HIDE_SYSTEM_BARS,
            roleplayHighContrast = true,
            roleplayLineHeightScale = RoleplayLineHeightScale.RELAXED,
        )
        editor.saveScreenTranslationSettings(
            ScreenTranslationSettings(
                serviceEnabled = true,
                targetLanguage = "英语",
            ),
        )
        editor.saveTranslationHistory(
            listOf(
                TranslationHistoryEntry(
                    id = "history-1",
                    sourceText = "你好",
                    translatedText = "Hello",
                    sourceLanguage = "中文",
                    targetLanguage = "英语",
                    modelName = "translate-model",
                    createdAt = 1L,
                ),
            ),
        )
        editor.saveAssistants(
            assistants = listOf(
                Assistant(
                    id = "assistant-1",
                    name = "陆宴清",
                ),
            ),
            selectedAssistantId = "assistant-1",
        )
        editor.saveUserProfile(
            displayName = "测试用户",
            avatarUri = "content://avatar/test",
            avatarUrl = "https://cdn.example.com/avatar.png",
        )

        val saved = settingsStore.settingsFlow.first()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(1.1f, saved.messageTextScale)
        assertEquals(RoleplayImmersiveMode.HIDE_SYSTEM_BARS, saved.roleplayImmersiveMode)
        assertTrue(saved.roleplayHighContrast)
        assertEquals(RoleplayLineHeightScale.RELAXED, saved.roleplayLineHeightScale)
        assertTrue(saved.screenTranslationSettings.serviceEnabled)
        assertEquals("英语", saved.screenTranslationSettings.targetLanguage)
        assertEquals("Hello", saved.translationHistory.first().translatedText)
        assertEquals("assistant-1", saved.selectedAssistantId)
        assertEquals("陆宴清", saved.assistants.first().name)
        assertEquals("测试用户", saved.userDisplayName)
        assertEquals("content://avatar/test", saved.userAvatarUri)
        assertEquals("https://cdn.example.com/avatar.png", saved.userAvatarUrl)
    }
}
