package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderTypeTest {

    @Test
    fun fromBaseUrl_detectsOpenAI() {
        assertEquals(ProviderType.OPENAI, ProviderType.fromBaseUrl("https://api.openai.com/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsDeepSeek() {
        assertEquals(ProviderType.DEEPSEEK, ProviderType.fromBaseUrl("https://api.deepseek.com/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsGoogle() {
        assertEquals(
            ProviderType.GOOGLE,
            ProviderType.fromBaseUrl("https://generativelanguage.googleapis.com/v1beta/"),
        )
    }

    @Test
    fun fromBaseUrl_detectsAnthropic() {
        assertEquals(ProviderType.ANTHROPIC, ProviderType.fromBaseUrl("https://api.anthropic.com/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsMistral() {
        assertEquals(ProviderType.MISTRAL, ProviderType.fromBaseUrl("https://api.mistral.ai/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsGrok() {
        assertEquals(ProviderType.GROK, ProviderType.fromBaseUrl("https://api.x.ai/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsMoonshot() {
        assertEquals(ProviderType.MOONSHOT, ProviderType.fromBaseUrl("https://api.moonshot.cn/v1/"))
    }

    @Test
    fun fromBaseUrl_detectsZhipu() {
        assertEquals(ProviderType.ZHIPU, ProviderType.fromBaseUrl("https://open.bigmodel.cn/api/paas/v4/"))
    }

    @Test
    fun fromBaseUrl_detectsQwen() {
        assertEquals(
            ProviderType.QWEN,
            ProviderType.fromBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/"),
        )
    }

    @Test
    fun fromBaseUrl_returnsCustomForUnknownUrl() {
        assertEquals(ProviderType.CUSTOM, ProviderType.fromBaseUrl("https://my-proxy.example.com/v1/"))
    }

    @Test
    fun fromBaseUrl_ignoresCaseWhenDetecting() {
        assertEquals(ProviderType.OPENAI, ProviderType.fromBaseUrl("https://API.OPENAI.COM/v1/"))
        assertEquals(ProviderType.DEEPSEEK, ProviderType.fromBaseUrl("https://Api.DeepSeek.Com/V1/"))
    }

    @Test
    fun fromBaseUrl_returnsCustomForBlankUrl() {
        assertEquals(ProviderType.CUSTOM, ProviderType.fromBaseUrl(""))
    }

    @Test
    fun anthropicDefaultBaseUrl_pointsToOfficialMessagesEndpointBase() {
        assertEquals("https://api.anthropic.com/v1/", ProviderType.ANTHROPIC.defaultBaseUrl)
    }

    @Test
    fun providerSettings_resolvedApiProtocol_infersAnthropicFromOfficialBaseUrl() {
        val settings = ProviderSettings(
            baseUrl = "https://api.anthropic.com/v1/",
        )

        assertEquals(ProviderApiProtocol.ANTHROPIC, settings.resolvedApiProtocol())
    }

    @Test
    fun providerSettings_resolvedApiProtocol_defaultsToOpenAiCompatibleForCustomProxy() {
        val settings = ProviderSettings(
            baseUrl = "https://proxy.example.com/v1/",
            type = ProviderType.ANTHROPIC,
        )

        assertEquals(ProviderApiProtocol.OPENAI_COMPATIBLE, settings.resolvedApiProtocol())
    }

    @Test
    fun providerSettings_resolvedOpenAiTextApiMode_defaultsToChatCompletions() {
        val settings = ProviderSettings(
            baseUrl = "https://api.openai.com/v1/",
        )

        assertEquals(OpenAiTextApiMode.CHAT_COMPLETIONS, settings.resolvedOpenAiTextApiMode())
    }

    @Test
    fun providerSettings_resolvedChatCompletionsPath_normalizesCustomPath() {
        val settings = ProviderSettings(
            chatCompletionsPath = "custom/chat",
        )

        assertEquals("/custom/chat", settings.resolvedChatCompletionsPath())
    }

    @Test
    fun providerSettings_resolvedType_usesExplicitType() {
        val settings = ProviderSettings(
            baseUrl = "https://api.openai.com/v1/",
            type = ProviderType.DEEPSEEK,
        )
        assertEquals(ProviderType.DEEPSEEK, settings.resolvedType())
    }

    @Test
    fun providerSettings_resolvedType_infersFromBaseUrlWhenTypeIsNull() {
        val settings = ProviderSettings(
            baseUrl = "https://api.openai.com/v1/",
            type = null,
        )
        assertEquals(ProviderType.OPENAI, settings.resolvedType())
    }
}
