package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
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

    @Test
    fun updateConversationSummary_splitsLongHistoryIntoSegmentsAndRefreshesTotalSummary() = runBlocking {
        val repository = FakeConversationSummaryRepository()
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 789L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val generatedInputs = mutableListOf<String>()
        val messages = (1..8).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = "conv-1",
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "第 $index 条剧情内容，包含较长描述用于触发分段。" + "细节".repeat(180),
                createdAt = index.toLong(),
            )
        }

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
                segmentTargetCharacterCount = 70,
                maxSegmentsPerRun = 10,
            ),
            buildSummaryInput = { summaryMessages ->
                summaryMessages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, _, _ ->
                generatedInputs += conversationText
                "摘要 ${generatedInputs.size}"
            },
        )

        val segments = repository.listSummarySegments("conv-1")
        assertTrue(result.updated)
        assertTrue(segments.size > 1)
        assertEquals(7, result.coveredMessageCount)
        assertEquals(7, segments.sumOf { it.messageCount })
        assertEquals("摘要 ${generatedInputs.size}", repository.getSummary("conv-1")?.summary)
        assertTrue(generatedInputs.last().contains("【分段摘要档案】"))
    }

    @Test
    fun updateConversationSummary_skipsMessagesAlreadyCoveredBySegments() = runBlocking {
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "已有总摘要",
                    coveredMessageCount = 2,
                ),
            ),
            initialSegments = listOf(
                ConversationSummarySegment(
                    id = "seg-1",
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    startMessageId = "m1",
                    endMessageId = "m2",
                    startCreatedAt = 1L,
                    endCreatedAt = 2L,
                    messageCount = 2,
                    summary = "前两条已经压缩",
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
        val generatedInputs = mutableListOf<String>()
        val messages = (1..5).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "剧情 $index",
                createdAt = index.toLong(),
            )
        }

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            config = SummaryGenerationConfig(
                triggerMessageCount = 2,
                recentMessageWindow = 1,
                minCoveredMessageCount = 2,
                segmentTargetCharacterCount = 1_000,
                maxSegmentsPerRun = 10,
            ),
            buildSummaryInput = { summaryMessages ->
                summaryMessages.joinToString(separator = "\n") { it.content }
            },
            generateSummary = { conversationText, _, _, _, _ ->
                generatedInputs += conversationText
                "新摘要 ${generatedInputs.size}"
            },
        )

        assertTrue(result.updated)
        assertFalse(generatedInputs.first().contains("剧情 1"))
        assertFalse(generatedInputs.first().contains("剧情 2"))
        assertTrue(generatedInputs.first().contains("剧情 3"))
        assertTrue(generatedInputs.first().contains("剧情 4"))
        assertEquals(4, repository.getSummary("conv-1")?.coveredMessageCount)
    }

    @Test
    fun updateConversationSummary_forceRefreshKeepsExistingSegmentsWhenCoverageIsComplete() = runBlocking {
        val repository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "旧总摘要",
                    coveredMessageCount = 4,
                ),
            ),
            initialSegments = listOf(
                ConversationSummarySegment(
                    id = "seg-1",
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    startMessageId = "m1",
                    endMessageId = "m4",
                    startCreatedAt = 1L,
                    endCreatedAt = 4L,
                    messageCount = 4,
                    summary = "已有分段摘要",
                ),
            ),
        )
        val coordinator = ConversationSummaryCoordinator(
            aiPromptExtrasService = unusedPromptExtrasService(),
            conversationSummaryRepository = repository,
            nowProvider = { 999L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val messages = (1..5).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = "conv-1",
                role = MessageRole.USER,
                content = "剧情 $index",
                createdAt = index.toLong(),
            )
        }

        val result = coordinator.updateConversationSummary(
            conversationId = "conv-1",
            assistantId = "assistant-1",
            completedMessages = messages,
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
            buildSummaryInput = { error("已全覆盖时不应重新读取原始消息") },
            generateSummary = { conversationText, _, _, _, _ ->
                assertTrue(conversationText.contains("已有分段摘要"))
                "新总摘要"
            },
        )

        assertTrue(result.updated)
        assertEquals("新总摘要", repository.getSummary("conv-1")?.summary)
        assertEquals(4, repository.getSummary("conv-1")?.coveredMessageCount)
        assertEquals(1, repository.listSummarySegments("conv-1").size)
    }

    private fun unusedPromptExtrasService(): AiPromptExtrasService {
        return object : AiPromptExtrasService {
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
        }
    }
}
