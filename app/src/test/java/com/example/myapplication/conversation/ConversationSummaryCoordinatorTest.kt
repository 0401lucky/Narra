package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationSummaryCoordinatorTest {
    @Test
    fun updateConversationSummary_persistsGeneratedSummary() = runBlocking {
        val repository = FakeConversationSummaryRepository()
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 123L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "你好", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "你好呀", createdAt = 2L),
                ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "继续总结", createdAt = 3L),
                ChatMessage(id = "m4", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "好的", createdAt = 4L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            buildSummaryInput = { messages ->
                messages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, modelId, _ ->
                assertTrue(conversationText.contains("你好"))
                assertEquals("summary-model", modelId)
                "这是摘要"
            },
        )

        assertTrue(result.updated)
        assertEquals("这是摘要", result.summaryText)
        assertEquals(3, result.coveredMessageCount)
        assertEquals(
            ConversationSummary(
                conversationId = "conv-1",
                assistantId = "assistant-1",
                summary = "这是摘要",
                coveredMessageCount = 3,
                updatedAt = 123L,
            ),
            repository.getSummary("conv-1"),
        )
    }

    @Test
    fun updateConversationSummary_skipsWhenExistingCoverageIsEnough() = runBlocking {
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "已有摘要",
                    coveredMessageCount = 5,
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "a", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "b", createdAt = 2L),
                ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "c", createdAt = 3L),
                ChatMessage(id = "m4", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "d", createdAt = 4L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            buildSummaryInput = { error("不应再生成摘要") },
            generateSummary = { _, _, _, _, _ -> error("不应调用生成") },
        )

        assertFalse(result.updated)
        assertEquals("已有摘要", repository.getSummary("conv-1")?.summary)
    }

    @Test
    fun updateConversationSummary_followsDefaultChatModelWhenTitleSummaryModeUsesDefault() = runBlocking {
        val repository = FakeConversationSummaryRepository()
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 123L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "a", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "b", createdAt = 2L),
                ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "c", createdAt = 3L),
                ChatMessage(id = "m4", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "d", createdAt = 4L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            buildSummaryInput = { messages ->
                messages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { _, _, _, modelId, _ ->
                assertEquals("chat-model", modelId)
                "这是摘要"
            },
        )

        assertTrue(result.updated)
    }

    @Test
    fun updateConversationSummary_forceRefreshRegeneratesSameCoverageSummary() = runBlocking {
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "旧摘要",
                    coveredMessageCount = 3,
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 456L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "a", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "b", createdAt = 2L),
                ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "c", createdAt = 3L),
                ChatMessage(id = "m4", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "d", createdAt = 4L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
            ),
            forceRefresh = true,
            buildSummaryInput = { messages ->
                messages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { _, _, _, _, _ -> "新摘要" },
        )

        assertTrue(result.updated)
        assertEquals("新摘要", repository.getSummary("conv-1")?.summary)
        assertEquals(456L, repository.getSummary("conv-1")?.updatedAt)
    }

    private fun unusedPromptExtrasService(): AiPromptExtrasService {
        return object : AiPromptExtrasService {
            override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateChatSuggestions(conversationSummary: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> = error("不应调用")
            override suspend fun generateConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateRoleplayConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> = error("不应调用")
            override suspend fun generateRoleplayMemoryEntries(
                conversationExcerpt: String,
                baseUrl: String,
                apiKey: String,
                modelId: String,
                apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                provider: com.example.myapplication.model.ProviderSettings?,
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
        }
    }
}
