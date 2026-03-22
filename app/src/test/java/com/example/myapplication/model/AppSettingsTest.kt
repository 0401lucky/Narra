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
        )

        assertEquals("translation-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("title-model", provider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
        assertEquals("suggestion-model", provider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION))
    }

    @Test
    fun resolveFunctionModel_fallsBackToChatModelWhenDedicatedModelMissing() {
        val provider = ProviderSettings(
            selectedModel = "chat-model",
        )

        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.TRANSLATION))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY))
        assertEquals("chat-model", provider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION))
    }
}
