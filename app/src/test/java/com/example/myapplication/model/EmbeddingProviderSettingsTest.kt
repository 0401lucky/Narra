package com.example.myapplication.model

import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddingProviderSettingsTest {
    private val chat = ProviderSettings(
        id = "chat",
        baseUrl = "https://chat.example.test/v1/",
        apiKey = "fixture-chat-key",
        selectedModel = "fixture-chat-model",
        memoryModel = "fixture-extraction-model",
    )
    private val embedding = ProviderSettings(
        id = "embedding",
        baseUrl = "https://embedding.example.test/v1/",
        apiKey = "fixture-embedding-key",
        embeddingModel = "fixture-vector-model",
        embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
    )

    @Test
    fun defaultEmbeddingAndUnassignedProvider_neverUseChatModel() {
        val defaults = chat.copy(embeddingModel = "fixture-vector-model")
        assertEquals(
            ProviderFunctionModelMode.DISABLED,
            defaults.resolveFunctionModelMode(ProviderFunction.EMBEDDING)
        )
        assertEquals("", defaults.resolveFunctionModel(ProviderFunction.EMBEDDING))
        assertDisabled(settings(defaults))
        assertDisabled(settings(embedding).copy(functionModelProviderIds = FunctionModelProviderIds()))
        assertDisabled(
            AppSettings(
                baseUrl = chat.baseUrl,
                apiKey = chat.apiKey,
                selectedModel = chat.selectedModel
            )
        )
    }

    @Test
    fun explicitEmbedding_usesIndependentProviderAndModelWithoutChatSelection() {
        val settings = settings(embedding.copy(embeddingModel = "  fixture-vector-model  "))
        assertEquals(embedding.id, settings.resolveEmbeddingProvider()?.id)
        assertEquals(embedding.baseUrl, settings.resolveEmbeddingProvider()?.baseUrl)
        assertEquals(embedding.apiKey, settings.resolveEmbeddingProvider()?.apiKey)
        assertEquals("", settings.resolveEmbeddingProvider()?.selectedModel)
        assertEquals(
            "fixture-vector-model",
            settings.resolveFunctionModel(ProviderFunction.EMBEDDING)
        )
        assertEquals(
            ProviderFunctionModelMode.CUSTOM,
            settings.resolveFunctionModelMode(ProviderFunction.EMBEDDING)
        )
        assertEquals(chat, settings.activeProvider())
        assertEquals(chat.memoryModel, settings.resolveFunctionModel(ProviderFunction.MEMORY))
    }

    @Test
    fun invalidOrUnavailableEmbedding_doesNotFallBackToAnyOtherProvider() {
        val invalidProviders = listOf(
            embedding.copy(enabled = false),
            embedding.copy(apiKey = ""),
            embedding.copy(baseUrl = ""),
            embedding.copy(apiProtocol = ProviderApiProtocol.ANTHROPIC),
            embedding.copy(embeddingModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT),
            embedding.copy(embeddingModelMode = ProviderFunctionModelMode.DISABLED),
            embedding.copy(embeddingModel = " "),
        )
        invalidProviders.forEach { assertDisabled(settings(it)) }
        assertDisabled(settings(embedding).copy(providers = listOf(chat)))
        assertDisabled(
            settings(embedding).copy(
                functionModelProviderIds = FunctionModelProviderIds(embeddingProviderId = "deleted-provider"),
            )
        )
    }

    @Test
    fun legacyOrUnknownEmbeddingMode_isDisabledEvenWithExplicitModelText() {
        val raw = AppJson.gson.toJsonTree(embedding).asJsonObject
        for (mode in listOf(
            null,
            "null",
            "\"UNKNOWN_MODE\"",
            "\"FOLLOW_DEFAULT\"",
            "\"DISABLED\""
        )) {
            val providerJson = raw.deepCopy()
            if (mode == null) providerJson.remove("embeddingModelMode")
            else providerJson.add("embeddingModelMode", JsonParser.parseString(mode))
            val provider = AppJson.gson.fromJson(providerJson, ProviderSettings::class.java)
            assertDisabled(settings(provider))
            assertEquals("", provider.resolveFunctionModel(ProviderFunction.EMBEDDING))
        }
        val missingModel =
            raw.deepCopy().apply { add("embeddingModel", JsonParser.parseString("null")) }
        assertDisabled(settings(AppJson.gson.fromJson(missingModel, ProviderSettings::class.java)))
    }

    @Test
    fun legacyNullAssignments_normalizeWithoutChangingOtherUses() {
        val assignments = AppJson.gson.fromJson(
            """{"memoryProviderId":"chat","embeddingProviderId":null}""",
            FunctionModelProviderIds::class.java,
        ).normalized(setOf(chat.id, embedding.id))
        assertEquals(chat.id, assignments.memoryProviderId)
        assertEquals("", assignments.providerIdFor(ProviderFunction.EMBEDDING))
        val selected = assignments.withProviderId(ProviderFunction.EMBEDDING, "  embedding  ")
        assertEquals(embedding.id, selected.embeddingProviderId)
        assertEquals(chat.id, selected.memoryProviderId)
        assertEquals("", selected.normalized(setOf(chat.id)).embeddingProviderId)
    }

    private fun settings(provider: ProviderSettings): AppSettings = AppSettings(
        providers = listOf(chat, provider),
        selectedProviderId = chat.id,
        functionModelProviderIds = FunctionModelProviderIds(embeddingProviderId = embedding.id),
    )

    private fun assertDisabled(settings: AppSettings) {
        assertNull(settings.resolveEmbeddingProvider())
        assertNull(settings.resolveFunctionProvider(ProviderFunction.EMBEDDING))
        assertEquals("", settings.resolveFunctionModel(ProviderFunction.EMBEDDING))
        assertEquals(
            ProviderFunctionModelMode.DISABLED,
            settings.resolveFunctionModelMode(ProviderFunction.EMBEDDING)
        )
    }
}
