package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTitleCoordinatorTest {
    @Test
    fun updateConversationTitle_generatesAndPersistsTitle() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "conv-1",
                    title = "新对话",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(store, nowProvider = { 20L })
        val coordinator = ConversationTitleCoordinator(
            aiPromptExtrasService = fakePromptExtrasService(
                generatedTitle = "钟楼疑云",
            ),
            conversationRepository = repository,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "title-model",
        )

        val modelName = coordinator.updateConversationTitle(
            conversationId = "conv-1",
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "帮我整理钟楼案线索",
                    createdAt = 1L,
                ),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        assertEquals("title-model", modelName)
        assertEquals("钟楼疑云", repository.getConversation("conv-1")?.title)
    }

    @Test
    fun updateConversationTitle_skipsWhenNoActiveProviderModel() = runBlocking {
        val repository = ConversationRepository(FakeConversationStore())
        val coordinator = ConversationTitleCoordinator(
            aiPromptExtrasService = fakePromptExtrasService(),
            conversationRepository = repository,
        )

        val modelName = coordinator.updateConversationTitle(
            conversationId = "conv-1",
            messages = emptyList(),
            settings = AppSettings(),
        )

        assertNull(modelName)
    }

    private fun fakePromptExtrasService(
        generatedTitle: String = "",
    ): AiPromptExtrasService {
        return object : AiPromptExtrasService {
            override suspend fun generateTitle(firstUserMessage: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol, provider: com.example.myapplication.model.ProviderSettings?): String = generatedTitle
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
