package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {
    @Test
    fun activeProvider_skipsDisabledSelectedProvider() {
        val disabledProvider = ProviderSettings(
            id = "provider-disabled",
            name = "Disabled",
            baseUrl = "https://disabled.example.com/v1/",
            apiKey = "disabled-key",
            selectedModel = "disabled-model",
            enabled = false,
        )
        val enabledProvider = ProviderSettings(
            id = "provider-enabled",
            name = "Enabled",
            baseUrl = "https://enabled.example.com/v1/",
            apiKey = "enabled-key",
            selectedModel = "enabled-model",
        )
        val settings = AppSettings(
            providers = listOf(disabledProvider, enabledProvider),
            selectedProviderId = disabledProvider.id,
        )

        assertEquals(enabledProvider.id, settings.activeProvider()?.id)
        assertTrue(settings.hasRequiredConfig())
    }

    @Test
    fun activeProvider_returnsNullWhenAllProvidersDisabled() {
        val disabledProvider = ProviderSettings(
            id = "provider-disabled",
            name = "Disabled",
            baseUrl = "https://disabled.example.com/v1/",
            apiKey = "disabled-key",
            selectedModel = "disabled-model",
            enabled = false,
        )
        val settings = AppSettings(
            providers = listOf(disabledProvider),
            selectedProviderId = disabledProvider.id,
        )

        assertNull(settings.activeProvider())
        assertFalse(settings.hasBaseCredentials())
        assertFalse(settings.hasRequiredConfig())
    }

    @Test
    fun resolveFunctionModel_prefersDedicatedModelWhenConfigured() {
        val provider = ProviderSettings(
            selectedModel = "chat-model",
            translationModel = "translation-model",
            titleSummaryModel = "title-model",
            chatSuggestionModel = "suggestion-model",
            phoneSnapshotModel = "phone-model",
            searchModel = "search-model",
        )

        assertEquals("translation-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("title-model", provider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
        assertEquals("suggestion-model", provider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION))
        assertEquals("phone-model", provider.resolveFunctionModel(ProviderFunction.PHONE_SNAPSHOT))
        assertEquals("search-model", provider.resolveFunctionModel(ProviderFunction.SEARCH))
    }

    @Test
    fun resolveFunctionModel_fallsBackToChatModelWhenDedicatedModelMissing() {
        val provider = ProviderSettings(
            selectedModel = "chat-model",
        )

        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.PHONE_SNAPSHOT))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.SEARCH))
    }

    @Test
    fun resolveFunctionModel_returnsBlankWhenFunctionModeDisabled() {
        val provider = ProviderSettings(
            selectedModel = "chat-model",
            translationModelMode = ProviderFunctionModelMode.DISABLED,
            searchModelMode = ProviderFunctionModelMode.DISABLED,
        )

        assertEquals("", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("", provider.resolveFunctionModel(ProviderFunction.SEARCH))
    }

    @Test
    fun resolveFunctionModel_treatsExplicitModelAsCustomForCompatibility() {
        val provider = ProviderSettings(
            selectedModel = "chat-model",
            translationModel = "translation-model",
        )

        assertEquals(ProviderFunctionModelMode.CUSTOM, provider.resolveFunctionModelMode(ProviderFunction.TRANSLATION))
        assertEquals("translation-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
    }

    @Test
    fun resolveFunctionProvider_usesAssignedProviderWithoutChangingChatProvider() {
        val chatProvider = ProviderSettings(
            id = "provider-chat",
            name = "Chat",
            selectedModel = "chat-model",
        )
        val titleProvider = ProviderSettings(
            id = "provider-title",
            name = "Title",
            selectedModel = "title-default",
            titleSummaryModel = "title-model",
        )
        val settings = AppSettings(
            providers = listOf(chatProvider, titleProvider),
            selectedProviderId = chatProvider.id,
            functionModelProviderIds = FunctionModelProviderIds(
                titleSummaryProviderId = titleProvider.id,
            ),
        )

        assertEquals(chatProvider.id, settings.activeProvider()?.id)
        assertEquals(titleProvider.id, settings.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY)?.id)
        assertEquals("title-model", settings.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
    }

    @Test
    fun hasConfiguredSearchSource_acceptsLlmSearchWhenProviderHasSearchModelAndResponsesMode() {
        val provider = ProviderSettings(
            id = "provider-search",
            name = "Search Provider",
            baseUrl = "https://api.x.ai/v1/",
            apiKey = "search-key",
            selectedModel = "grok-4-fast",
            searchModel = "grok-4.20-reasoning",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val settings = AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.LLM_SEARCH,
                        type = SearchSourceType.LLM_SEARCH,
                        name = "LLM 搜索",
                        enabled = true,
                        providerId = provider.id,
                    ),
                ),
                selectedSourceId = SearchSourceIds.LLM_SEARCH,
            ),
        )

        assertTrue(settings.hasConfiguredSearchSource(provider))
    }

    @Test
    fun hasConfiguredSearchSource_rejectsLlmSearchWithoutResponsesOrAnthropicSupport() {
        val provider = ProviderSettings(
            id = "provider-search",
            name = "Search Provider",
            baseUrl = "https://api.x.ai/v1/",
            apiKey = "search-key",
            selectedModel = "grok-4-fast",
            searchModel = "grok-4.20-reasoning",
            openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
        )
        val settings = AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.LLM_SEARCH,
                        type = SearchSourceType.LLM_SEARCH,
                        name = "LLM 搜索",
                        enabled = true,
                        providerId = provider.id,
                    ),
                ),
                selectedSourceId = SearchSourceIds.LLM_SEARCH,
            ),
        )

        assertFalse(settings.hasConfiguredSearchSource(provider))
    }

    @Test
    fun hasConfiguredSearchSource_rejectsLlmSearchWhenSearchModelDisabled() {
        val provider = ProviderSettings(
            id = "provider-search",
            name = "Search Provider",
            baseUrl = "https://api.x.ai/v1/",
            apiKey = "search-key",
            selectedModel = "grok-4-fast",
            searchModelMode = ProviderFunctionModelMode.DISABLED,
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val settings = AppSettings(
            providers = listOf(provider),
            selectedProviderId = provider.id,
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.LLM_SEARCH,
                        type = SearchSourceType.LLM_SEARCH,
                        name = "LLM 搜索",
                        enabled = true,
                        providerId = provider.id,
                    ),
                ),
                selectedSourceId = SearchSourceIds.LLM_SEARCH,
            ),
        )

        assertFalse(settings.hasConfiguredSearchSource(provider))
    }

    @Test
    fun resolvedAssistants_prefersSavedOverrideForBuiltinAssistant() {
        val overrideAssistant = Assistant(
            id = DEFAULT_ASSISTANT_ID,
            name = "默认助手",
            systemPrompt = "新的系统提示词",
            creatorNotes = "新的备注",
            isBuiltin = false,
        )
        val settings = AppSettings(
            assistants = listOf(overrideAssistant),
            selectedAssistantId = DEFAULT_ASSISTANT_ID,
        )

        val resolved = settings.resolvedAssistants().first { it.id == DEFAULT_ASSISTANT_ID }

        assertEquals("新的系统提示词", resolved.systemPrompt)
        assertEquals("新的备注", resolved.creatorNotes)
        assertTrue(resolved.isBuiltin)
    }

    @Test
    fun normalizedUserPersonaMasks_wrapsExistingProfileAsBaseMask() {
        val settings = AppSettings(
            userDisplayName = "lucky",
            userPersonaPrompt = "【lucky的人设】\n姓名：lucky",
            userAvatarUri = "content://avatar/lucky",
        )

        val mask = settings.normalizedUserPersonaMasks().single()

        assertEquals(USER_PROFILE_PERSONA_MASK_ID, mask.id)
        assertEquals("lucky", mask.name)
        assertEquals("【lucky的人设】\n姓名：lucky", mask.personaPrompt)
        assertEquals("content://avatar/lucky", mask.avatarUri)
        assertEquals(USER_PROFILE_PERSONA_MASK_ID, settings.resolvedDefaultUserPersonaMask()?.id)
    }

    @Test
    fun normalizedUserPersonaMasks_keepsProfileMaskBeforeCustomMasks() {
        val customMask = UserPersonaMask(
            id = "mask-custom",
            name = "自定义面具",
        )
        val settings = AppSettings(
            userDisplayName = "lucky",
            userPersonaPrompt = "现有用户人设",
            userPersonaMasks = listOf(customMask),
        )

        val masks = settings.normalizedUserPersonaMasks()

        assertEquals(listOf(USER_PROFILE_PERSONA_MASK_ID, "mask-custom"), masks.map { it.id })
    }
}
