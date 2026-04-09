package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplaySuggestionCoordinatorTest {
    @Test
    fun generateSuggestions_buildsRoleplayRequestAndReturnsSuggestions() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "conv-1",
                    title = "雨夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                "conv-1" to listOf(
                    ChatMessage(id = "m1", conversationId = "conv-1", role = MessageRole.USER, content = "你今晚看起来不太对劲。", createdAt = 1L),
                    ChatMessage(id = "m2", conversationId = "conv-1", role = MessageRole.ASSISTANT, content = "我只是没想到你会来。", createdAt = 2L),
                    ChatMessage(id = "m3", conversationId = "conv-1", role = MessageRole.USER, content = "别再回避了。", createdAt = 3L),
                ),
            ),
        )
        val repository = ConversationRepository(store)
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            contextMessageSize = 2,
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "雨夜对峙",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            longformModeEnabled = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "chat-model",
            chatSuggestionModel = "suggest-model",
        )
        val captured = CapturedSuggestionCall()
        val coordinator = RoleplaySuggestionCoordinator(
            conversationRepository = repository,
            promptContextAssembler = object : PromptContextAssembler {
                override suspend fun assemble(
                    settings: AppSettings,
                    assistant: Assistant?,
                    conversation: Conversation,
                    userInputText: String,
                    recentMessages: List<ChatMessage>,
                    promptMode: com.example.myapplication.model.PromptMode,
                    includePhoneSnapshot: Boolean,
                ): PromptContextResult {
                    return PromptContextResult(systemPrompt = "【对话摘要】正在互相试探")
                }
            },
            aiPromptExtrasService = object : AiPromptExtrasService {
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
                ): List<RoleplaySuggestionUiModel> {
                    captured.conversationExcerpt = conversationExcerpt
                    captured.systemPrompt = systemPrompt
                    captured.playerStyleReference = playerStyleReference
                    captured.baseUrl = baseUrl
                    captured.apiKey = apiKey
                    captured.modelId = modelId
                    captured.longformMode = longformMode
                    return listOf(
                        RoleplaySuggestionUiModel(
                            id = "s1",
                            label = "逼近真相",
                            text = "你到底还瞒了我什么？",
                            axis = RoleplaySuggestionAxis.PLOT,
                        ),
                    )
                }

                override suspend fun condenseRoleplayMemories(
                    memoryItems: List<String>,
                    mode: com.example.myapplication.data.repository.RoleplayMemoryCondenseMode,
                    maxItems: Int,
                    baseUrl: String,
                    apiKey: String,
                    modelId: String,
                    apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
                    provider: com.example.myapplication.model.ProviderSettings?,
                ): List<String> = error("不应调用")
            },
            nowProvider = { 10L },
        )

        val conversationMessages = store.listMessages("conv-1")
        assertEquals(1, store.listMessagesCount)

        val result = coordinator.generateSuggestions(
            RoleplaySuggestionRequest(
                scenario = scenario,
                session = session,
                settings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
                currentInput = "继续追问",
                recentMessageWindow = 10,
                conversationMessages = conversationMessages,
                resolveAssistant = { _, _ -> assistant },
                resolveRoleplayNames = { _, _, _ -> "林晚" to "陆宴清" },
                resolveSuggestionModelId = { "suggest-model" },
                buildDynamicDirectorNote = { _, _, _, _ -> "导演提示" },
            ),
        )

        assertEquals(1, result.suggestions.size)
        assertEquals("逼近真相", result.suggestions.first().label)
        assertEquals("suggest-model", captured.modelId)
        assertEquals("https://example.com/v1/", captured.baseUrl)
        assertTrue(captured.longformMode)
        assertTrue(captured.systemPrompt.contains("导演提示"))
        assertTrue(captured.playerStyleReference.contains("别再回避了"))
        assertTrue(captured.conversationExcerpt.contains("别再回避了"))
        assertTrue(!captured.conversationExcerpt.contains("你今晚看起来不太对劲。"))
        assertEquals(1, store.listMessagesCount)
    }

    private class CapturedSuggestionCall {
        var conversationExcerpt: String = ""
        var systemPrompt: String = ""
        var playerStyleReference: String = ""
        var baseUrl: String = ""
        var apiKey: String = ""
        var modelId: String = ""
        var longformMode: Boolean = false
    }
}
