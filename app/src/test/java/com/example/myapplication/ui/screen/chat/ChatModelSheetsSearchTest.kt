package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatModelSheetsSearchTest {
    @Test
    fun buildProviderSearchKeywords_containsProviderNameIdBaseUrlAndSelectedModel() {
        val provider = ProviderSettings(
            id = "provider-lucky",
            name = "lucky",
            baseUrl = "https://api.deepseek.com/v1/",
            selectedModel = "deepseek-chat",
        )

        val keywords = buildProviderSearchKeywords(provider)

        assertTrue(keywords.contains("provider-lucky"))
        assertTrue(keywords.contains("lucky"))
        assertTrue(keywords.contains("https://api.deepseek.com/v1/"))
        assertTrue(keywords.contains("deepseek-chat"))
    }

    @Test
    fun filterModelInfosForQuery_returnsAllModelsWhenProviderMatchesQuery() {
        val modelInfos = listOf(
            ModelInfo(modelId = "deepseek-chat", displayName = "deepseek-chat"),
            ModelInfo(modelId = "deepseek-reasoner", displayName = "deepseek-reasoner"),
        )

        val filtered = filterModelInfosForQuery(
            modelInfos = modelInfos,
            normalizedQuery = "lucky",
            providerMatchesQuery = true,
        )

        assertEquals(modelInfos, filtered)
    }
}
