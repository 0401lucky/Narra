package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSuggestionCoordinatorTest {
    @Test
    fun generateSuggestions_buildsRecentSummaryAndReturnsModelName() = runBlocking {
        var capturedSummary = ""
        val coordinator = ChatSuggestionCoordinator(
            aiPromptExtrasService = object : AiPromptExtrasService {
                override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateChatSuggestions(conversationSummary: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> {
                    capturedSummary = conversationSummary
                    assertEquals("suggest-model", modelId)
                    return listOf("继续追问", "确认钥匙下落")
                }

                override suspend fun generateConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateRoleplayConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?, existingMemories: List<String>, userName: String, characterName: String, extractionPromptOverride: String): List<String> = error("不应调用")
                override suspend fun generateRoleplayMemoryEntries(
                    conversationExcerpt: String,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                    existingMemories: List<String>,
                ): StructuredMemoryExtractionResult = error("不应调用")

                override suspend fun generateRoleplaySuggestions(
                    conversationExcerpt: String,
                    systemPrompt: String,
                    playerStyleReference: String,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                    longformMode: Boolean,
                ): List<RoleplaySuggestionUiModel> = error("不应调用")

                override suspend fun condenseRoleplayMemories(
                    memoryItems: List<String>,
                    mode: RoleplayMemoryCondenseMode,
                    maxItems: Int,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                ): List<String> = error("不应调用")
            },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            chatSuggestionModel = "suggest-model",
        )

        val result = coordinator.generateSuggestions(
            messages = listOf(
                ChatMessage(id = "m1", role = MessageRole.USER, content = "第一句", createdAt = 1L),
                ChatMessage(id = "m2", role = MessageRole.ASSISTANT, content = "第二句", createdAt = 2L),
                ChatMessage(id = "m3", role = MessageRole.USER, content = "第三句", createdAt = 3L),
                ChatMessage(id = "m4", role = MessageRole.ASSISTANT, content = "第四句", createdAt = 4L),
                ChatMessage(id = "m5", role = MessageRole.USER, content = "第五句", createdAt = 5L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        assertEquals("suggest-model", result?.modelName)
        assertEquals(listOf("继续追问", "确认钥匙下落"), result?.suggestions)
        assertTrue(capturedSummary.contains("用户: 第三句"))
        assertTrue(capturedSummary.contains("助手: 第四句"))
        assertTrue(capturedSummary.contains("用户: 第五句"))
        assertTrue(!capturedSummary.contains("第一句"))
    }

    @Test
    fun generateSuggestions_returnsNullWithoutProviderModel() = runBlocking {
        val coordinator = ChatSuggestionCoordinator(
            aiPromptExtrasService = object : AiPromptExtrasService {
                override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateChatSuggestions(conversationSummary: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> = error("不应调用")
                override suspend fun generateConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateRoleplayConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
                override suspend fun generateMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?, existingMemories: List<String>, userName: String, characterName: String, extractionPromptOverride: String): List<String> = error("不应调用")
                override suspend fun generateRoleplayMemoryEntries(
                    conversationExcerpt: String,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                    existingMemories: List<String>,
                ): StructuredMemoryExtractionResult = error("不应调用")

                override suspend fun generateRoleplaySuggestions(
                    conversationExcerpt: String,
                    systemPrompt: String,
                    playerStyleReference: String,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                    longformMode: Boolean,
                ): List<RoleplaySuggestionUiModel> = error("不应调用")

                override suspend fun condenseRoleplayMemories(
                    memoryItems: List<String>,
                    mode: RoleplayMemoryCondenseMode,
                    maxItems: Int,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                ): List<String> = error("不应调用")
            },
        )

        assertNull(
            coordinator.generateSuggestions(
                messages = emptyList(),
                settings = AppSettings(),
            ),
        )
    }
}
