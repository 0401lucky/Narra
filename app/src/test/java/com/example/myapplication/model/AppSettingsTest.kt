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
            searchModel = "search-model",
        )

        assertEquals("translation-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("title-model", provider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
        assertEquals("suggestion-model", provider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION))
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
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.SEARCH))
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
}
