package com.example.myapplication.conversation

import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationMemoryExtractionCoordinatorTest {
    @Test
    fun updateChatMemories_persistsAssistantScopedMemories() = runBlocking {
        val repository = FakeMemoryRepository()
        val coordinator = ConversationMemoryExtractionCoordinator(
            aiPromptExtrasService = fakePromptExtrasService(
                memoryEntries = listOf("用户喜欢短句", "用户正在调查钟楼"),
            ),
            memoryRepository = repository,
            nowProvider = { 50L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            memoryModel = "memory-model",
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            memoryEnabled = true,
            useGlobalMemory = false,
            memoryMaxItems = 4,
        )

        val updated = coordinator.updateChatMemories(
            assistant = assistant,
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "你好", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "在呢", createdAt = 2L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            recentMessageWindow = 6,
            buildMemoryInput = { "用户：你好\n助手：在呢" },
        )

        assertTrue(updated)
        val entries = repository.currentEntries()
        assertEquals(2, entries.size)
        assertTrue(entries.all { it.scopeType == MemoryScopeType.ASSISTANT })
        assertTrue(entries.all { it.scopeId == "assistant-1" })
        assertTrue(entries.all { it.sourceMessageId == "m2" })
    }

    @Test
    fun updateRoleplayMemories_persistsAssistantAndConversationScopes() = runBlocking {
        val repository = FakeMemoryRepository()
        val coordinator = ConversationMemoryExtractionCoordinator(
            aiPromptExtrasService = fakePromptExtrasService(
                roleplayMemoryEntries = StructuredMemoryExtractionResult(
                    persistentMemories = listOf("角色长期隐瞒钟楼线索"),
                    sceneStateMemories = listOf("当前地点在钟楼平台", "双方正在对峙"),
                ),
            ),
            memoryRepository = repository,
            nowProvider = { 80L },
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            memoryModel = "memory-model",
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            memoryEnabled = true,
            useGlobalMemory = false,
            memoryMaxItems = 4,
        )

        val updated = coordinator.updateRoleplayMemories(
            conversationId = "conv-1",
            assistant = assistant,
            completedMessages = listOf(
                ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "你一直知道钥匙在哪", createdAt = 1L),
                ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "我只是不能说", createdAt = 2L),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            recentMessageWindow = 8,
            sceneMemoryMaxItems = 12,
            buildMemoryInput = { "用户：你一直知道钥匙在哪\n角色：我只是不能说" },
        )

        assertTrue(updated)
        val entries = repository.currentEntries()
        assertEquals(3, entries.size)
        assertTrue(entries.any { it.scopeType == MemoryScopeType.ASSISTANT && it.scopeId == "assistant-1" })
        assertTrue(entries.any { it.scopeType == MemoryScopeType.CONVERSATION && it.scopeId == "conv-1" })
    }

    private fun fakePromptExtrasService(
        memoryEntries: List<String> = emptyList(),
        roleplayMemoryEntries: StructuredMemoryExtractionResult = StructuredMemoryExtractionResult(),
    ): AiPromptExtrasService {
        return object : AiPromptExtrasService {
            override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateChatSuggestions(conversationSummary: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> = error("不应调用")
            override suspend fun generateConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateRoleplayConversationSummary(conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = error("不应调用")
            override suspend fun generateMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): List<String> = memoryEntries
            override suspend fun generateRoleplayMemoryEntries(conversationExcerpt: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): StructuredMemoryExtractionResult = roleplayMemoryEntries
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
            ): List<String> = memoryEntries.take(maxItems)
        }
    }
}
