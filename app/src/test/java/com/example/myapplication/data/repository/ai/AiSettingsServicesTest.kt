package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_TAGS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_SCAN_DEPTH
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinPreset
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
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
    fun settingsEditor_rejectsGeminiNativeGenerateContentBaseUrl() {
        val settingsStore = FakeSettingsStore(AppSettings())
        val editor = DefaultAiSettingsEditor(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                editor.saveProviderSettings(
                    providers = listOf(
                        ProviderSettings(
                            id = "provider-gemini",
                            name = "Gemini",
                            baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent",
                            apiKey = "key",
                            selectedModel = "gemini-3.5-flash",
                        ),
                    ),
                    selectedProviderId = "provider-gemini",
                )
            }
        }

        assertEquals(
            "Google Gemini 的 Base URL 请填写 https://generativelanguage.googleapis.com/v1beta/ 或 https://generativelanguage.googleapis.com/v1beta/openai/，不要填写 /models/...:generateContent 这类原生接口地址",
            error.message,
        )
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
            showOnlineRoleplayNarration = false,
            enableRoleplayNetMeme = false,
            roleplayImmersiveMode = RoleplayImmersiveMode.HIDE_SYSTEM_BARS,
            roleplayHighContrast = true,
            roleplayLineHeightScale = RoleplayLineHeightScale.RELAXED,
            roleplayNoBackgroundSkin = RoleplayNoBackgroundSkinSettings(
                preset = RoleplayNoBackgroundSkinPreset.IMESSAGE,
                maxWidthPercent = 76,
            ),
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
            personaPrompt = "测试用户的人设",
            avatarUri = "content://avatar/test",
            avatarUrl = "https://cdn.example.com/avatar.png",
        )

        val saved = settingsStore.settingsFlow.first()
        assertEquals(ThemeMode.DARK, saved.themeMode)
        assertEquals(1.1f, saved.messageTextScale)
        assertEquals(RoleplayImmersiveMode.HIDE_SYSTEM_BARS, saved.roleplayImmersiveMode)
        assertTrue(saved.roleplayHighContrast)
        assertTrue(!saved.showOnlineRoleplayNarration)
        assertEquals(RoleplayLineHeightScale.RELAXED, saved.roleplayLineHeightScale)
        assertEquals(RoleplayNoBackgroundSkinPreset.IMESSAGE, saved.roleplayNoBackgroundSkin.preset)
        assertEquals(76, saved.roleplayNoBackgroundSkin.maxWidthPercent)
        assertTrue(saved.screenTranslationSettings.serviceEnabled)
        assertEquals("英语", saved.screenTranslationSettings.targetLanguage)
        assertEquals("Hello", saved.translationHistory.first().translatedText)
        assertEquals("assistant-1", saved.selectedAssistantId)
        assertEquals("陆宴清", saved.assistants.first().name)
        assertEquals("测试用户", saved.userDisplayName)
        assertEquals("测试用户的人设", saved.userPersonaPrompt)
        assertEquals("content://avatar/test", saved.userAvatarUri)
        assertEquals("https://cdn.example.com/avatar.png", saved.userAvatarUrl)
    }

    @Test
    fun settingsEditor_normalizesAssistantImportFieldsBeforeSaving() = runBlocking {
        val settingsStore = FakeSettingsStore(AppSettings())
        val editor = DefaultAiSettingsEditor(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
        )

        editor.saveAssistants(
            assistants = listOf(
                Assistant(
                    id = "assistant-1",
                    name = "名".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS + 20),
                    exampleDialogues = List(CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES + 2) { index ->
                        "示例$index" + "长".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS)
                    },
                    tags = List(CONTEXT_IMPORT_MAX_ASSISTANT_TAGS + 2) { index ->
                        "标签$index" + "长".repeat(CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS)
                    },
                    worldBookMaxEntries = 999,
                    worldBookScanDepth = 999,
                ),
            ),
            selectedAssistantId = "assistant-1",
        )

        val saved = settingsStore.settingsFlow.first().assistants.single()
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_NAME_CHARS, saved.name.length)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUES, saved.exampleDialogues.size)
        assertTrue(saved.exampleDialogues.all { it.length <= CONTEXT_IMPORT_MAX_ASSISTANT_EXAMPLE_DIALOGUE_CHARS })
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_TAGS, saved.tags.size)
        assertTrue(saved.tags.all { it.length <= CONTEXT_IMPORT_MAX_ASSISTANT_TAG_CHARS })
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_MAX_ENTRIES, saved.worldBookMaxEntries)
        assertEquals(CONTEXT_IMPORT_MAX_ASSISTANT_WORLD_BOOK_SCAN_DEPTH, saved.worldBookScanDepth)
    }
}
