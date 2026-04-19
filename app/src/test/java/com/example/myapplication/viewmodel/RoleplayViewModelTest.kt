package com.example.myapplication.viewmodel

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.tooling.DefaultMemoryWriteService
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.InMemoryPendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.PendingMemoryProposalRepository
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.PunishIntensity
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.model.giftImageStatus
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.testutil.FakeConversationStore
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeWorldBookRepository
import com.example.myapplication.testutil.createTestAiServices
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun generateSuggestions_updatesUiStateAndUsesSuggestionModel() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            systemPrompt = "保持冷静克制。",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "雨夜对峙",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val conversation = Conversation(
            id = session.conversationId,
            title = "雨夜",
            model = "chat-model",
            createdAt = 1L,
            updatedAt = 2L,
            assistantId = assistant.id,
        )
        val store = FakeConversationStore(
            conversations = listOf(conversation),
            messagesByConversation = mapOf(
                conversation.id to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = conversation.id,
                        role = MessageRole.USER,
                        content = "你今晚看起来不太对劲。",
                        createdAt = 10L,
                    ),
                    ChatMessage(
                        id = "m2",
                        conversationId = conversation.id,
                        role = MessageRole.ASSISTANT,
                        content = "我只是没想到你会现在来。",
                        createdAt = 11L,
                        status = MessageStatus.COMPLETED,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        var lastSuggestionRequest: ChatCompletionRequest? = null
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("【对话摘要】两人正处于互相试探阶段。"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        lastSuggestionRequest = request
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = """
                                            [
                                              {"axis":"plot","label":"逼近真相","text":"*我抬眼看向他* 你刚才那句话，到底是什么意思？"},
                                              {"axis":"info","label":"追问细节","text":"先告诉我，这里之前到底发生过什么。"},
                                              {"axis":"emotion","label":"压住退路","text":"*我没有退开* 既然你知道，就别再瞒着我。"}
                                            ]
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        val listMessagesCountBeforeSuggestion = store.listMessagesCount

        viewModel.generateSuggestions()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingSuggestions)
        assertEquals(3, state.suggestions.size)
        assertEquals("逼近真相", state.suggestions[0].label)
        assertTrue(state.suggestions[0].text.contains("你刚才那句话"))
        assertEquals(RoleplaySuggestionAxis.PLOT, state.suggestions[0].axis)
        assertEquals(listMessagesCountBeforeSuggestion, store.listMessagesCount)

        val request = lastSuggestionRequest ?: error("未记录到建议请求")
        assertEquals("rp-suggestion-model", request.model)
        assertTrue(request.messages[1].content.toString().contains("【剧情设定与上下文】"))
        assertTrue(request.messages[1].content.toString().contains("【玩家口吻参考】"))
        assertTrue(request.messages[1].content.toString().contains("【最近剧情】"))
        assertTrue(request.messages[1].content.toString().contains("林晚"))
        assertTrue(request.messages[1].content.toString().contains("陆宴清"))
        assertEquals(0.9f, request.temperature)
        assertEquals(0.92f, request.topP)
    }

    @Test
    fun updateInput_cancelsGeneratingSuggestions() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "你到底想说什么？",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        val delayedApiProvider: (String, String) -> OpenAiCompatibleApi = { _, _ ->
            object : OpenAiCompatibleApi {
                override suspend fun listModels(): Response<ModelsResponse> {
                    error("不应调用模型列表")
                }

                override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                    delay(1_000)
                    return Response.success(
                        ChatCompletionResponse(
                            choices = listOf(
                                ChatChoiceDto(
                                    index = 0,
                                    message = AssistantMessageDto(
                                        role = "assistant",
                                        content = """[{"label":"试探推进","text":"你先回答我。"}]""",
                                    ),
                                ),
                            ),
                        ),
                    )
                }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                    error("不应调用生图接口")
                }
            }
        }
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            apiServiceProvider = delayedApiProvider,
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        viewModel.generateSuggestions()
        runCurrent()
        assertTrue(viewModel.uiState.value.isGeneratingSuggestions)

        advanceTimeBy(100)
        viewModel.updateInput("我自己来写")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("我自己来写", state.input)
        assertFalse(state.isGeneratingSuggestions)
        assertTrue(state.suggestions.isEmpty())
        assertEquals(null, state.suggestionErrorMessage)
    }

    @Test
    fun generateSuggestions_showsClearMessageWhenSuggestionModelDisabled() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModelMode = ProviderFunctionModelMode.DISABLED,
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.generateSuggestions()

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingSuggestions)
        assertEquals("请先在模型页开启聊天建议模型", state.suggestionErrorMessage)
    }

    @Test
    fun generateDraftInput_fillsInputWithoutWritingConversationHistory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            systemPrompt = "保持冷静克制。",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "雨夜对峙",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val conversation = Conversation(
            id = session.conversationId,
            title = "雨夜",
            model = "chat-model",
            createdAt = 1L,
            updatedAt = 2L,
            assistantId = assistant.id,
        )
        val store = FakeConversationStore(
            conversations = listOf(conversation),
            messagesByConversation = mapOf(
                conversation.id to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = conversation.id,
                        role = MessageRole.USER,
                        content = "你今晚看起来不太对劲。",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("【对话摘要】两人正处于互相试探阶段。"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = """
                                            [
                                              {"axis":"plot","label":"逼近真相","text":"我警惕地打量着四周。'这是哪里？'"},
                                              {"axis":"info","label":"追问细节","text":"先告诉我，你为什么会突然出现在这里。"}
                                            ]
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        val originalMessages = store.listMessages(session.conversationId)
        viewModel.generateDraftInput()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("我警惕地打量着四周。'这是哪里？'", state.input)
        assertFalse(state.isGeneratingSuggestions)
        assertTrue(state.suggestions.isEmpty())
        assertEquals(originalMessages, store.listMessages(session.conversationId))
        assertEquals(1, store.listMessages(session.conversationId).size)
    }

    @Test
    fun generateDraftInput_doesNotFillMalformedStructuredPayload() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            systemPrompt = "保持冷静克制。",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "雨夜对峙",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val conversation = Conversation(
            id = session.conversationId,
            title = "雨夜",
            model = "chat-model",
            createdAt = 1L,
            updatedAt = 2L,
            assistantId = assistant.id,
        )
        val store = FakeConversationStore(
            conversations = listOf(conversation),
            messagesByConversation = mapOf(
                conversation.id to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = conversation.id,
                        role = MessageRole.USER,
                        content = "你今晚看起来不太对劲。",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("【对话摘要】两人正处于互相试探阶段。"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = """
                                            plot: {label: "逼近真相", text: "你刚才那句到底是什么意思？"}
                                            info: {label: "追问细节", text: "先告诉我，这里之前到底发生了什么。"}
                                            emotion: {label: "逼近关系", text: "你还想把我推开到什么时候？"}
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.updateInput("我自己先写了一句")
        advanceUntilIdle()

        val originalMessages = store.listMessages(session.conversationId)
        viewModel.generateDraftInput()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("我自己先写了一句", state.input)
        assertEquals("AI 没有生成可用草稿", state.suggestionErrorMessage)
        assertFalse(state.isGeneratingSuggestions)
        assertTrue(state.suggestions.isEmpty())
        assertEquals(originalMessages, store.listMessages(session.conversationId))
    }

    @Test
    fun sendMessage_clearsSuggestionsBeforeSending() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("我沉默了几秒，终于开口：那你想知道到什么程度？")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "你今晚为什么会在这里？",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("m-user", "m-assistant"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = """
                                            [
                                              {"label":"试探推进","text":"你最好把话说清楚。"},
                                              {"label":"信息探索","text":"这里之前到底发生过什么？"},
                                              {"label":"情绪拉扯","text":"我不信你什么都不知道。"}
                                            ]
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.generateSuggestions()
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.suggestions.size)

        viewModel.applySuggestion("你最好把话说清楚。")
        assertEquals("你最好把话说清楚。", viewModel.uiState.value.input)
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())
        val listMessagesCountBeforeSend = store.listMessagesCount
        viewModel.sendMessage()
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.suggestions.isEmpty())
        assertEquals(listMessagesCountBeforeSend, store.listMessagesCount)
        assertTrue(store.listMessages(session.conversationId).any { it.content.contains("你最好把话说清楚") })
        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(0.9f, requestBody["temperature"].asFloat)
        assertEquals(0.92f, requestBody["top_p"].asFloat)
        assertTrue(requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString.contains("【本轮导演提示】"))
    }

    @Test
    fun retryTurn_regeneratesAssistantReplyFromSelectedTurn() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("新的剧情回复")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "你今晚为什么会在这里？",
                        createdAt = 10L,
                    ),
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = session.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "旧回复",
                        status = MessageStatus.COMPLETED,
                        createdAt = 11L,
                        modelName = "chat-model",
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        val listMessagesCountBeforeRetry = store.listMessagesCount
        viewModel.retryTurn("assistant-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertEquals(listMessagesCountBeforeRetry, store.listMessagesCount)
        assertEquals(listOf("你今晚为什么会在这里？", "新的剧情回复"), store.listMessages(session.conversationId).map { it.content })
        assertTrue(state.messages.any { it.sourceMessageId == "assistant-1" && it.content == "新的剧情回复" })
    }

    @Test
    fun editUserMessage_rewindsConversationAndRestoresInput() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "第一句",
                        createdAt = 10L,
                    ),
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = session.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "第一句回复",
                        status = MessageStatus.COMPLETED,
                        createdAt = 11L,
                        modelName = "chat-model",
                    ),
                    ChatMessage(
                        id = "user-2",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "卡住的那句",
                        createdAt = 12L,
                    ),
                    ChatMessage(
                        id = "assistant-2",
                        conversationId = session.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "发送失败",
                        status = MessageStatus.ERROR,
                        createdAt = 13L,
                        modelName = "chat-model",
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        val listMessagesCountBeforeEdit = store.listMessagesCount
        viewModel.editUserMessage("user-2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("卡住的那句", state.input)
        assertEquals(listMessagesCountBeforeEdit, store.listMessagesCount)
        assertEquals(listOf("第一句", "第一句回复"), store.listMessages(session.conversationId).map { it.content })
        assertTrue(state.messages.none { it.sourceMessageId == "user-2" })
        assertTrue(state.messages.none { it.sourceMessageId == "assistant-2" })
    }

    @Test
    fun enterScenario_ignoresStaleStartResultFromPreviousScenario() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistantA = Assistant(id = "assistant-a", name = "甲")
        val assistantB = Assistant(id = "assistant-b", name = "乙")
        val scenarioA = RoleplayScenario(id = "scene-a", assistantId = assistantA.id, title = "A 场景")
        val scenarioB = RoleplayScenario(id = "scene-b", assistantId = assistantB.id, title = "B 场景")
        val sessionA = RoleplaySession(
            id = "session-a",
            scenarioId = scenarioA.id,
            conversationId = "conv-a",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val sessionB = RoleplaySession(
            id = "session-b",
            scenarioId = scenarioB.id,
            conversationId = "conv-b",
            createdAt = 3L,
            updatedAt = 4L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = sessionA.conversationId,
                    title = "A",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistantA.id,
                ),
                Conversation(
                    id = sessionB.conversationId,
                    title = "B",
                    model = "chat-model",
                    createdAt = 3L,
                    updatedAt = 4L,
                    assistantId = assistantB.id,
                ),
            ),
            messagesByConversation = mapOf(
                sessionA.conversationId to listOf(
                    ChatMessage(
                        id = "assistant-a-1",
                        conversationId = sessionA.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "A 会话内容",
                        status = MessageStatus.COMPLETED,
                        createdAt = 10L,
                    ),
                ),
                sessionB.conversationId to listOf(
                    ChatMessage(
                        id = "assistant-b-1",
                        conversationId = sessionB.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "B 会话内容",
                        status = MessageStatus.COMPLETED,
                        createdAt = 11L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = DelayedStartRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenarioA, scenarioB),
                sessions = listOf(sessionA, sessionB),
                startDelayByScenarioId = mapOf(
                    scenarioA.id to 200L,
                    scenarioB.id to 20L,
                ),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistantA, assistantB),
                selectedAssistantId = assistantA.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenarioA.id)
        runCurrent()
        viewModel.enterScenario(scenarioB.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(scenarioB.id, state.currentScenario?.id)
        assertEquals(sessionB.id, state.currentSession?.id)
        assertTrue(state.messages.any { it.content.contains("B 会话内容") })
        assertTrue(state.messages.none { it.content.contains("A 会话内容") })
    }

    @Test
    fun switchingScenario_clearsQuotedReplyBeforeSending() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("新的回复")

        val assistantA = Assistant(id = "assistant-a", name = "甲")
        val assistantB = Assistant(id = "assistant-b", name = "乙")
        val scenarioA = RoleplayScenario(id = "scene-a", assistantId = assistantA.id, title = "A 场景")
        val scenarioB = RoleplayScenario(id = "scene-b", assistantId = assistantB.id, title = "B 场景")
        val sessionA = RoleplaySession(
            id = "session-a",
            scenarioId = scenarioA.id,
            conversationId = "conv-a",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val sessionB = RoleplaySession(
            id = "session-b",
            scenarioId = scenarioB.id,
            conversationId = "conv-b",
            createdAt = 3L,
            updatedAt = 4L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = sessionA.conversationId,
                    title = "A",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistantA.id,
                ),
                Conversation(
                    id = sessionB.conversationId,
                    title = "B",
                    model = "chat-model",
                    createdAt = 3L,
                    updatedAt = 4L,
                    assistantId = assistantB.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenarioA, scenarioB),
                sessions = listOf(sessionA, sessionB),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistantA, assistantB),
                selectedAssistantId = assistantA.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-b", "assistant-b"),
        )

        viewModel.enterScenario(scenarioA.id)
        advanceUntilIdle()
        viewModel.quoteMessage("quoted-1", "旧角色", "旧预览")
        assertEquals("quoted-1", viewModel.uiState.value.replyToMessageId)

        viewModel.leaveScenario()
        viewModel.enterScenario(scenarioB.id)
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.replyToMessageId)
        viewModel.updateInput("现在说正事")
        viewModel.sendMessage()
        advanceUntilIdle()

        val savedUserMessage = store.listMessages(sessionB.conversationId)
            .first { it.role == MessageRole.USER }
        assertEquals("", savedUserMessage.replyToMessageId)
        assertEquals("", savedUserMessage.replyToPreview)
        assertEquals("", savedUserMessage.replyToSpeakerName)
    }

    @Test
    fun cancelSending_removesLoadingAssistantMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(id = "assistant-1", name = "陆宴清")
        val scenario = RoleplayScenario(id = "scene-1", assistantId = assistant.id)
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val settings = AppSettings(
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            assistants = listOf(assistant),
            selectedAssistantId = assistant.id,
        )
        val roleplayRepository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(scenario),
            sessions = listOf(session),
        )
        val conversationRepository = ConversationRepository(
            conversationStore = store,
            nowProvider = incrementingNowProvider(1L),
        )
        val promptContextAssembler = fixedPromptAssembler("提示词上下文")
        val hangingGateway = object : AiGateway {
            override suspend fun generateImage(prompt: String, modelId: String): List<ImageGenerationResult> {
                return emptyList()
            }

            override suspend fun sendMessage(
                messages: List<ChatMessage>,
                systemPrompt: String,
                toolingOptions: GatewayToolingOptions,
            ): AssistantReply {
                error("不应调用非流式发送")
            }

            override fun sendMessageStream(
                messages: List<ChatMessage>,
                systemPrompt: String,
                promptMode: com.example.myapplication.model.PromptMode,
                toolingOptions: GatewayToolingOptions,
            ) = kotlinx.coroutines.flow.flow<com.example.myapplication.model.ChatStreamEvent> {
                delay(Long.MAX_VALUE)
            }

            override fun parseAssistantSpecialOutput(
                content: String,
                existingParts: List<ChatMessagePart>,
            ): ParsedAssistantSpecialOutput {
                return ParsedAssistantSpecialOutput(
                    content = content,
                    parts = existingParts,
                )
            }
        }
        val assistantRoundTripRunner = ConversationAssistantRoundTripRunner(
            conversationRepository = conversationRepository,
            aiGateway = hangingGateway,
        )
        var latestState = RoleplayUiState(
            settings = settings,
            currentScenario = scenario,
            currentSession = session,
            currentAssistant = assistant,
            isSending = true,
        )
        var latestRawMessages: List<ChatMessage> = emptyList()
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = session.conversationId,
            role = MessageRole.USER,
            content = "先停一下",
            createdAt = 10L,
            parts = listOf(textMessagePart("先停一下")),
        )
        val loadingMessage = ChatMessage(
            id = "assistant-loading",
            conversationId = session.conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            createdAt = 11L,
            modelName = "chat-model",
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
        )
        val executor = RoleplayRoundTripExecutor(
            aiGateway = hangingGateway,
            conversationRepository = conversationRepository,
            conversationSummaryRepository = FakeConversationSummaryRepository(),
            phoneSnapshotRepository = EmptyPhoneSnapshotRepository,
            roleplayRepository = roleplayRepository,
            promptContextAssembler = promptContextAssembler,
            assistantRoundTripRunner = assistantRoundTripRunner,
            outputParser = com.example.myapplication.roleplay.RoleplayOutputParser(),
            nowProvider = incrementingNowProvider(100L),
            currentUiState = { latestState },
            updateUiState = { reducer -> latestState = reducer(latestState) },
            updateRawMessages = { messages -> latestRawMessages = messages },
            launchGiftImageGeneration = { _, _ -> },
            launchConversationSummaryGeneration = { _, _, _, _, _ -> },
            launchAutomaticMemoryExtraction = { _, _, _, _, _ -> },
        )

        val executeJob = launch {
            executor.execute(
                state = latestState,
                scenario = scenario,
                session = session,
                selectedModel = "chat-model",
                assistant = assistant,
                requestMessages = listOf(userMessage),
                cancelledMessages = listOf(userMessage),
                initialPersistence = RoundTripInitialPersistence.Append(
                    messages = listOf(userMessage, loadingMessage),
                ),
                loadingMessage = loadingMessage,
                buildFinalMessages = { completedAssistant ->
                    listOf(userMessage, completedAssistant)
                },
            )
        }
        runCurrent()
        assertTrue(store.listMessages(session.conversationId).any { it.status == MessageStatus.LOADING })
        executeJob.cancelAndJoin()
        advanceUntilIdle()

        val savedMessages = store.listMessages(session.conversationId)
        assertFalse(latestState.isSending)
        assertEquals(listOf(userMessage), latestRawMessages)
        assertTrue(savedMessages.none { it.status == MessageStatus.LOADING })
        assertEquals(1, savedMessages.size)
        assertEquals("先停一下", savedMessages.single().content)
    }

    @Test
    fun sendTransferPlay_persistsTransferPartAndMapsTransferCard() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("我收下了。")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.sendTransferPlay("陆宴清", "88.00", "晚饭钱")
        advanceUntilIdle()

        val savedMessages = store.listMessages(session.conversationId)
        assertTrue(savedMessages.first { it.role == MessageRole.USER }.parts.first().isTransferPart())
        assertTrue(viewModel.uiState.value.messages.any { it.contentType == RoleplayContentType.SPECIAL_PLAY })
    }

    @Test
    fun sendTransferPlay_inOnlineModeAcceptsPendingTransferViaDirective() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("""["收下了。",{"type":"transfer_action","action":"accept"}]""")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
                showOnlineRoleplayNarration = true,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.sendTransferPlay("陆宴清", "88.00", "晚饭钱")
        advanceUntilIdle()

        val transferPart = store.listMessages(session.conversationId)
            .first { it.role == MessageRole.USER }
            .parts
            .first()
        assertEquals(TransferStatus.RECEIVED, transferPart.specialStatus)
    }

    @Test
    fun confirmTransferReceipt_updatesPendingTransferStatus() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val transferPart = transferMessagePart(
            id = "transfer-1",
            direction = TransferDirection.ASSISTANT_TO_USER,
            status = TransferStatus.PENDING,
            counterparty = "陆宴清",
            amount = "66.00",
            note = "路费",
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = session.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "转账 66.00",
                        createdAt = 10L,
                        parts = listOf(transferPart),
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.confirmTransferReceipt("transfer-1")
        advanceUntilIdle()

        val updatedPart = store.listMessages(session.conversationId).first().parts.first()
        assertEquals(TransferStatus.RECEIVED, updatedPart.specialStatus)
    }

    @Test
    fun sendMessage_condensesExtractedMemoriesIntoCompactScopeEntries() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("<dialogue speaker=\"character\" emotion=\"克制\">钟楼响过之后，密门就会开。</dialogue>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            memoryEnabled = true,
            memoryMaxItems = 6,
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "钟楼刚才已经响过了，对吗？",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
            memoryModel = "memory-model",
        )
        val memoryRepository = FakeMemoryRepository(
            initialEntries = listOf(
                MemoryEntry(
                    id = "old-a1",
                    scopeType = com.example.myapplication.model.MemoryScopeType.ASSISTANT,
                    scopeId = assistant.id,
                    content = "角色会先试探再给答案。",
                    importance = 60,
                ),
                MemoryEntry(
                    id = "old-a2",
                    scopeType = com.example.myapplication.model.MemoryScopeType.ASSISTANT,
                    scopeId = assistant.id,
                    content = "角色已经知道钟楼密门的位置。",
                    importance = 60,
                ),
                MemoryEntry(
                    id = "old-c1",
                    scopeType = com.example.myapplication.model.MemoryScopeType.CONVERSATION,
                    scopeId = session.conversationId,
                    content = "当前剧情在追问钟楼密门。",
                    importance = 70,
                ),
                MemoryEntry(
                    id = "old-c2",
                    scopeType = com.example.myapplication.model.MemoryScopeType.CONVERSATION,
                    scopeId = session.conversationId,
                    content = "用户已经听见钟楼响过。",
                    importance = 70,
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            memoryRepository = memoryRepository,
            messageIdProvider = idProviderOf("m-user", "m-assistant"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        val prompt = request.messages.firstOrNull()?.content.toString()
                        val content = when {
                            prompt.contains("沉浸式剧情记忆提取器") -> """
                                {"persistent_memories":["角色会先试探再给答案。","角色已经承认自己知道密门位置。","角色不愿正面解释动机。"],"scene_state_memories":["当前剧情焦点是钟楼密门与钥匙。","用户已经听见钟楼响过。","角色刚承认钟楼响后密门会开。","双方还在相互试探。"]}
                            """.trimIndent()
                            prompt.contains("角色长期记忆整理器") -> """
                                ["角色习惯先试探再给答案，并避免直接交底。","角色已经承认自己知道钟楼密门位置，但仍在回避动机。","角色在关键问题上仍保持克制和防备。"]
                            """.trimIndent()
                            prompt.contains("剧情状态记忆整理器") -> """
                                ["当前剧情焦点是追问钟楼密门与钥匙去向。","钟楼已经响过，角色也承认密门会因此开启。","双方仍处于逼问与试探并存的僵持状态。","用户正在继续逼近真相。"]
                            """.trimIndent()
                            else -> "[]"
                        }
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = content,
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.updateInput("那你就把钟楼密门和钥匙的事一次说清楚。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val currentEntries = memoryRepository.currentEntries()
        val assistantMemories = currentEntries.filter {
            it.scopeType == com.example.myapplication.model.MemoryScopeType.ASSISTANT &&
                it.scopeId == assistant.id
        }
        val sceneMemories = currentEntries.filter {
            it.scopeType == com.example.myapplication.model.MemoryScopeType.CONVERSATION &&
                it.scopeId == session.conversationId
        }
        assertEquals(4, assistantMemories.size)
        assertEquals(5, sceneMemories.size)
        assertTrue(assistantMemories.any { it.content.contains("密门位置") })
        assertTrue(sceneMemories.any { it.content.contains("钟楼响过") })
        assertTrue(sceneMemories.any { it.content.contains("双方还在相互试探") })
    }

    @Test
    fun enterScenario_withResidualLoadingMessage_doesNotTriggerOnlineCompensation() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = session.conversationId,
                        role = MessageRole.USER,
                        content = "你还在吗？",
                        createdAt = 10L,
                    ),
                    ChatMessage(
                        id = "assistant-loading",
                        conversationId = session.conversationId,
                        role = MessageRole.ASSISTANT,
                        content = "",
                        status = MessageStatus.LOADING,
                        createdAt = 20L,
                        modelName = "chat-model",
                        roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                        roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            nowProvider = incrementingNowProvider(8 * 60 * 60 * 1000L + 100L),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        store.upsertMessages(
            listOf(
                ChatMessage(
                    id = "user-2",
                    conversationId = session.conversationId,
                    role = MessageRole.USER,
                    content = "还是没有消息。",
                    createdAt = 30L,
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals(0, server.requestCount)
        assertTrue(
            store.listMessages(session.conversationId).none { message ->
                message.systemEventKind == RoleplayOnlineEventKind.COMPENSATION_OPENING
            },
        )
    }

    @Test
    fun sendMessage_updatesWorldBookHitCountFromLatestInputAndThenResetsNextTurn() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("<dialogue speaker=\"character\">我当然带着，怎么了？</dialogue>")
        enqueueStreamResponse("<dialogue speaker=\"character\">少岔开话题，说正事。</dialogue>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
            worldBookMaxEntries = 8,
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "调教室",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val worldBookRepository = FakeWorldBookRepository(
            initialEntries = listOf(
                com.example.myapplication.model.WorldBookEntry(
                    id = "wb-weapon",
                    title = "余罪的配枪",
                    content = "他随身带着一把黑星手枪。",
                    keywords = listOf("枪, 武器, 掏出"),
                    scopeType = com.example.myapplication.model.WorldBookScopeType.ASSISTANT,
                    scopeId = assistant.id,
                ),
            ),
        )
        val promptContextAssembler = DefaultPromptContextAssembler(
            worldBookRepository = worldBookRepository,
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = promptContextAssembler,
            messageIdProvider = idProviderOf("user-1", "assistant-1", "user-2", "assistant-2"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        viewModel.updateInput("你身上带武器了吗？")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.contextStatus.worldBookHitCount)
        assertTrue(viewModel.uiState.value.latestPromptDebugDump.contains("他随身带着一把黑星手枪。"))
        assertEquals(1, viewModel.uiState.value.contextGovernance?.worldBookHitCount)

        viewModel.updateInput("今天天气不错。")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.contextStatus.worldBookHitCount)
    }

    @Test
    fun sendMessage_longformModeMapsAssistantReplyToLongformMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("他推开半掩的窗子，夜风一下子灌了进来。 “别逼我现在回答。”（可他其实已经有些动摇了。）")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "夜风",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            longformModeEnabled = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "夜风",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.updateInput("你到底在隐瞒什么？")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.messages.any { it.speaker == RoleplaySpeaker.USER && it.contentType == RoleplayContentType.DIALOGUE })
        val longformMessage = state.messages.firstOrNull {
            it.speaker == RoleplaySpeaker.CHARACTER && it.contentType == RoleplayContentType.LONGFORM
        }
        assertTrue(longformMessage != null)
        assertEquals(
            "他推开半掩的窗子，夜风一下子灌了进来。 “别逼我现在回答。”（可他其实已经有些动摇了。）",
            longformMessage?.content,
        )
        assertEquals(longformMessage?.content, longformMessage?.copyText)
        assertEquals(longformMessage?.content, longformMessage?.richTextSource)
        assertTrue(state.latestPromptDebugDump.contains("【长文小说模式】"))
        assertEquals(1, state.contextGovernance?.sentMessageCount)

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val systemPrompt = requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString
        assertTrue(systemPrompt.contains("【长文小说模式】"))
        assertTrue(systemPrompt.contains("<char>“……”</char>"))
        assertTrue(systemPrompt.contains("<thought>（……）</thought>"))
        assertTrue(!systemPrompt.contains("<dialogue speaker=\"character\""))
    }

    @Test
    fun sendMessage_longformModeStripsInternalRenderTagsFromVisibleContent() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("她望着你，呼吸轻得几乎听不见。<char>“别再躲我了。”</char><thought>（如果他现在还退开，我会更难受。）</thought>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "回廊",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            longformModeEnabled = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "回廊",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.updateInput("看着我。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val longformMessage = viewModel.uiState.value.messages.firstOrNull {
            it.speaker == RoleplaySpeaker.CHARACTER && it.contentType == RoleplayContentType.LONGFORM
        }
        assertEquals(
            "她望着你，呼吸轻得几乎听不见。“别再躲我了。”（如果他现在还退开，我会更难受。）",
            longformMessage?.content,
        )
        assertEquals(longformMessage?.content, longformMessage?.copyText)
        assertEquals(
            "她望着你，呼吸轻得几乎听不见。<char>“别再躲我了。”</char><thought>（如果他现在还退开，我会更难受。）</thought>",
            longformMessage?.richTextSource,
        )
    }

    @Test
    fun sendMessage_withStaleConversationSummary_keepsIntermediateMessages() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("继续推进剧情")

        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "对白模式",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val existingMessages = (1..10).map { index ->
            ChatMessage(
                id = "m$index",
                conversationId = session.conversationId,
                role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                content = "剧情消息$index",
                createdAt = index.toLong(),
            )
        }
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to existingMessages,
            ),
        )
        val summaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                com.example.myapplication.model.ConversationSummary(
                    conversationId = session.conversationId,
                    assistantId = assistant.id,
                    summary = "只覆盖了开头的一小段。",
                    coveredMessageCount = 2,
                    updatedAt = 3L,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = DefaultPromptContextAssembler(
                conversationSummaryRepository = summaryRepository,
            ),
            conversationSummaryRepository = summaryRepository,
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.updateInput("把话说清楚。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val messages = requestBody.getAsJsonArray("messages")
        assertEquals(existingMessages.size + 2, messages.size())
        assertEquals(ContextSummaryState.STALE, viewModel.uiState.value.contextGovernance?.summaryState)
    }

    @Test
    fun sendMessage_switchingBackFromLongformKeepsHistoryFormatAndRequestContext() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("风从窗缝里挤进来，吹得他的声音更轻。<char>“我没有忘。”</char>")
        enqueueStreamResponse("<dialogue speaker=\"character\" emotion=\"低沉\">我只是还没准备好现在说。</dialogue>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val longformScenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            longformModeEnabled = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = longformScenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(longformScenario),
            sessions = listOf(session),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1", "user-2", "assistant-2"),
        )

        viewModel.enterScenario(longformScenario.id)
        advanceUntilIdle()

        viewModel.updateInput("你还记得那一晚吗？")
        viewModel.sendMessage()
        advanceUntilIdle()

        repository.upsertScenario(
            longformScenario.copy(
                longformModeEnabled = false,
                updatedAt = 99L,
            ),
        )
        advanceUntilIdle()

        val firstLongformMessage = viewModel.uiState.value.messages.firstOrNull {
            it.sourceMessageId == "assistant-1" && it.contentType == RoleplayContentType.LONGFORM
        }
        assertTrue(firstLongformMessage != null)
        assertEquals(
            "风从窗缝里挤进来，吹得他的声音更轻。“我没有忘。”",
            firstLongformMessage?.content,
        )

        viewModel.updateInput("那你现在说。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val storedMessages = store.listMessages(session.conversationId)
        val assistantMessages = storedMessages.filter { it.role == MessageRole.ASSISTANT }
        assertEquals(RoleplayOutputFormat.LONGFORM, assistantMessages[0].roleplayOutputFormat)
        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, assistantMessages[0].roleplayInteractionMode)
        assertEquals(RoleplayOutputFormat.PROTOCOL, assistantMessages[1].roleplayOutputFormat)
        assertEquals(RoleplayInteractionMode.OFFLINE_DIALOGUE, assistantMessages[1].roleplayInteractionMode)

        val finalState = viewModel.uiState.value
        assertTrue(
            finalState.messages.any {
                it.sourceMessageId == "assistant-1" && it.contentType == RoleplayContentType.LONGFORM
            },
        )
        assertTrue(finalState.latestPromptDebugDump.contains("【输出协议】"))
        assertTrue(!finalState.latestPromptDebugDump.contains("【长文小说模式】"))

        server.takeRequest()
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val sentContext = secondRequest.getAsJsonArray("messages")
            .filter { element -> element.asJsonObject["role"].asString == "assistant" }
            .map { element -> element.asJsonObject["content"].asString }
            .joinToString(separator = "\n")
        assertTrue(sentContext.contains("我没有忘。"))
        assertFalse(sentContext.contains("<char>"))
        assertFalse(sentContext.contains("<thought>"))
    }

    @Test
    fun sendMessage_switchingFromOnlineToDialogueKeepsProtocolFormatting() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("<dialogue speaker=\"character\">你终于回了。</dialogue><thought>刚才差点把对话框关掉。</thought>")
        enqueueStreamResponse("<narrative>手指抵着杯壁磨了磨。</narrative><thought>不能再让她绕过去。</thought><dialogue speaker=\"character\">这次别再含糊过去。</dialogue>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val onlineScenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = onlineScenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(onlineScenario),
            sessions = listOf(session),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1", "user-2", "assistant-2"),
        )

        viewModel.enterScenario(onlineScenario.id)
        advanceUntilIdle()

        viewModel.updateInput("你刚刚想说什么？")
        viewModel.sendMessage()
        advanceUntilIdle()

        repository.upsertScenario(
            onlineScenario.copy(
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                updatedAt = 99L,
            ),
        )
        advanceUntilIdle()

        viewModel.updateInput("那你现在就说清楚。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val secondAssistantMessages = viewModel.uiState.value.messages.filter { it.sourceMessageId == "assistant-2" }
        assertTrue(secondAssistantMessages.isNotEmpty())
        assertTrue(secondAssistantMessages.none { it.contentType == RoleplayContentType.LONGFORM })
        assertTrue(secondAssistantMessages.none { it.content.contains("<") || it.content.contains("speaker=") })
        assertTrue(secondAssistantMessages.any { it.contentType == RoleplayContentType.THOUGHT })
        assertTrue(secondAssistantMessages.any { it.contentType == RoleplayContentType.DIALOGUE && it.content.contains("这次别再含糊过去") })
    }

    @Test
    fun sendMessage_switchingFromLongformToOnlineUsesOnlinePromptAndSanitizedHistory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("风从窗缝里挤进来，吹得他的声音更轻。<char>“我没有忘。”</char>")
        enqueueStreamResponse("""["我没躲着你。","刚才那句我看到了。"]""")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            longformModeEnabled = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(scenario),
            sessions = listOf(session),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1", "user-2", "assistant-2"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        viewModel.updateInput("你还记得那一晚吗？")
        viewModel.sendMessage()
        advanceUntilIdle()

        repository.upsertScenario(
            scenario.copy(
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
                longformModeEnabled = false,
                updatedAt = 99L,
            ),
        )
        advanceUntilIdle()

        viewModel.updateInput("那你现在直接发给我。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val storedMessages = store.listMessages(session.conversationId)
        val assistantMessages = storedMessages.filter { it.role == MessageRole.ASSISTANT }
        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, assistantMessages[0].roleplayInteractionMode)
        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, assistantMessages[1].roleplayInteractionMode)
        assertEquals(RoleplayOutputFormat.PROTOCOL, assistantMessages[1].roleplayOutputFormat)

        val secondAssistantMessages = viewModel.uiState.value.messages.filter { it.sourceMessageId == "assistant-2" }
        assertTrue(secondAssistantMessages.isNotEmpty())
        assertTrue(secondAssistantMessages.none { it.contentType == RoleplayContentType.LONGFORM })
        assertTrue(secondAssistantMessages.any { it.contentType == RoleplayContentType.DIALOGUE && it.content.contains("我没躲着你") })
        assertTrue(viewModel.uiState.value.latestPromptDebugDump.contains("合法 JSON 数组"))
        assertFalse(viewModel.uiState.value.latestPromptDebugDump.contains("【长文小说模式】"))

        server.takeRequest()
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val sentContext = secondRequest.getAsJsonArray("messages")
            .filter { element -> element.asJsonObject["role"].asString == "assistant" }
            .map { element -> element.asJsonObject["content"].asString }
            .joinToString(separator = "\n")
        assertTrue(sentContext.contains("我没有忘。"))
        assertFalse(sentContext.contains("<char>"))
        assertFalse(sentContext.contains("<thought>"))
    }

    @Test
    fun sendMessage_switchingFromOnlineToLongformUsesLongformPromptAndStripsOnlineProtocolFromHistory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("""["你终于回了。",{"type":"thought","content":"刚才差点把对话框关掉。"}]""")
        enqueueStreamResponse("他把杯子放回桌面。<char>“这次我会说清楚。”</char>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(scenario),
            sessions = listOf(session),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1", "user-2", "assistant-2"),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        viewModel.updateInput("你刚刚想说什么？")
        viewModel.sendMessage()
        advanceUntilIdle()

        repository.upsertScenario(
            scenario.copy(
                interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
                longformModeEnabled = true,
                enableRoleplayProtocol = false,
                updatedAt = 99L,
            ),
        )
        advanceUntilIdle()

        viewModel.updateInput("那你现在好好说。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val storedMessages = store.listMessages(session.conversationId)
        val assistantMessages = storedMessages.filter { it.role == MessageRole.ASSISTANT }
        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, assistantMessages[0].roleplayInteractionMode)
        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, assistantMessages[1].roleplayInteractionMode)
        assertEquals(RoleplayOutputFormat.LONGFORM, assistantMessages[1].roleplayOutputFormat)

        val secondAssistantMessages = viewModel.uiState.value.messages.filter { it.sourceMessageId == "assistant-2" }
        assertTrue(secondAssistantMessages.any { it.contentType == RoleplayContentType.LONGFORM })
        assertTrue(viewModel.uiState.value.latestPromptDebugDump.contains("【长文小说模式】"))
        assertFalse(viewModel.uiState.value.latestPromptDebugDump.contains("合法 JSON 数组"))

        server.takeRequest()
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val sentContext = secondRequest.getAsJsonArray("messages")
            .filter { element -> element.asJsonObject["role"].asString != "system" }
            .map { element -> element.asJsonObject["content"].asString }
            .joinToString(separator = "\n")
        assertTrue(sentContext.contains("刚才差点把对话框关掉"))
        assertFalse(sentContext.contains("\"type\":\"thought\""))
        assertFalse(sentContext.contains("[[rp_online_thought]]"))
    }

    @Test
    fun startVideoCall_recordsConnectedEventWithoutAutoReply() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(scenario),
            sessions = listOf(session),
        )
        var now = 1_000L
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            nowProvider = {
                val value = now
                now += 1_000L
                value
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.startVideoCall()
        advanceUntilIdle()

        val latestState = viewModel.uiState.value
        val conversationMessages = store.listMessages(session.conversationId)

        assertTrue(latestState.isVideoCallActive)
        assertFalse(latestState.isSending)
        assertEquals(1, conversationMessages.size)
        assertEquals(RoleplayOnlineEventKind.VIDEO_CALL_CONNECTED, conversationMessages.single().systemEventKind)
        assertEquals("已接通视频通话", conversationMessages.single().content)
    }

    @Test
    fun sendMessage_duringVideoCallUsesVideoCallPrompt() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("<dialogue speaker=\"character\">看着我。</dialogue>")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        var now = 1_000L
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            nowProvider = {
                val value = now
                now += 1_000L
                value
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.startVideoCall()
        advanceUntilIdle()
        viewModel.updateInput("你现在能看清我吗？")
        viewModel.sendMessage()
        advanceUntilIdle()

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val systemPrompt = requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString

        assertTrue(systemPrompt.contains("【线上视频通话模式】"))
        assertTrue(systemPrompt.contains("实时视频通话"))
        assertTrue(systemPrompt.contains("不要再输出 video_call 动作"))
        assertTrue(systemPrompt.contains("合法 JSON 数组"))
    }

    @Test
    fun hangupVideoCall_recordsEndedEventAndClearsState() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val repository = FakeRoleplayRepository(
            conversationStore = store,
            scenarios = listOf(scenario),
            sessions = listOf(session),
        )
        var now = 1_000L
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = repository,
            settings = AppSettings(
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            nowProvider = {
                val value = now
                now += 1_000L
                value
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.startVideoCall()
        advanceUntilIdle()
        viewModel.hangupVideoCall()
        advanceUntilIdle()

        val latestState = viewModel.uiState.value
        val conversationMessages = store.listMessages(session.conversationId)

        assertFalse(latestState.isVideoCallActive)
        assertEquals(2, conversationMessages.size)
        assertEquals(RoleplayOnlineEventKind.VIDEO_CALL_ENDED, conversationMessages.last().systemEventKind)
        assertTrue(conversationMessages.last().content.contains("视频通话已结束，通话时长 00:02"))
    }

    @Test
    fun hangupVideoCall_thenImmediateSend_usesOnlineChatPromptAndNoTaggedVideoEvents() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        enqueueStreamResponse("""["下班了吗？"]""")

        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧夜",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
            enableRoleplayProtocol = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧夜",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        var now = 1_000L
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            nowProvider = {
                val value = now
                now += 1_000L
                value
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.startVideoCall()
        advanceUntilIdle()
        viewModel.hangupVideoCall()
        viewModel.updateInput("下班了吗？")
        viewModel.sendMessage()
        advanceUntilIdle()

        val request = server.takeRequest(5, TimeUnit.SECONDS)
        assertTrue("预期应发送一条线上聊天请求", request != null)
        val requestBodyText = request!!.body.readUtf8()
        val requestBody = JsonParser.parseString(requestBodyText).asJsonObject
        val systemPrompt = requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString

        assertTrue(systemPrompt.contains("【线上手机聊天模式】"))
        assertTrue(!systemPrompt.contains("【线上视频通话模式】"))
        assertTrue(!requestBodyText.contains("<narration>已接通视频通话</narration>"))
        assertTrue(!requestBodyText.contains("<narration>视频通话已结束"))
    }

    @Test
    fun dismissAssistantMismatchDialog_withSuppression_persistsPreference() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
                suppressRoleplayAssistantMismatchDialog = false,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
        )

        viewModel.dismissAssistantMismatchDialog(suppressFuturePrompt = true)
        advanceUntilIdle()

        assertTrue(viewModel.settings.value.suppressRoleplayAssistantMismatchDialog)
        assertEquals("继续沿用旧剧情，后续不再提示", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun generateDraftInput_longformModeRequestsNovelStyleDraft() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
            systemPrompt = "保持克制。",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "夜谈",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "余罪",
            longformModeEnabled = true,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val conversation = Conversation(
            id = session.conversationId,
            title = "夜谈",
            model = "chat-model",
            createdAt = 1L,
            updatedAt = 2L,
            assistantId = assistant.id,
        )
        val store = FakeConversationStore(
            conversations = listOf(conversation),
            messagesByConversation = mapOf(
                conversation.id to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = conversation.id,
                        role = MessageRole.USER,
                        content = "你今晚看起来有点不对劲。",
                        createdAt = 10L,
                    ),
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            chatSuggestionModel = "rp-suggestion-model",
        )
        var lastSuggestionRequest: ChatCompletionRequest? = null
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("【对话摘要】两人仍在互相试探。"),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型列表")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        lastSuggestionRequest = request
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = """
                                            [
                                              {"axis":"plot","label":"继续试探","text":"我没有立刻逼问，只是慢慢走近一步，盯着他被夜风吹乱的领口。 “你可以现在不说，但我会一直看着你，直到你自己露出破绽。”"},
                                              {"axis":"info","label":"追问细节","text":"我顺着他刚才回避的视线看向窗边，压低声音问道：“你刚才到底想遮住什么？是伤，还是别的痕迹？”"},
                                              {"axis":"emotion","label":"逼近关系","text":"我轻轻拽住他的袖口，没有再用命令的语气，只是低声说：“余罪，你要是真撑不住，就别再一个人硬扛了。”"}
                                            ]
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.generateDraftInput()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.input.contains("我没有立刻逼问"))
        val request = lastSuggestionRequest ?: error("未记录到建议请求")
        val systemPrompt = request.messages.first().content.toString()
        assertTrue(systemPrompt.contains("当前场景处于长文小说模式"))
    }

    @Test
    fun approvePendingMemoryProposal_savesMemoryAndClearsProposal() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
            memoryEnabled = true,
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "test-key",
            selectedModel = "chat-model",
            memoryModel = "memory-model",
        )
        val memoryRepository = FakeMemoryRepository()
        val proposalRepository = InMemoryPendingMemoryProposalRepository()
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = fixedPromptAssembler("提示词上下文"),
            memoryRepository = memoryRepository,
            pendingMemoryProposalRepository = proposalRepository,
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()

        proposalRepository.upsertProposal(
            PendingMemoryProposal(
                id = "proposal-1",
                conversationId = session.conversationId,
                assistantId = assistant.id,
                scopeType = com.example.myapplication.model.MemoryScopeType.ASSISTANT,
                content = "她不喜欢被突然逼问。",
                reason = "这是稳定偏好",
                importance = 60,
            ),
        )
        advanceUntilIdle()

        assertEquals("她不喜欢被突然逼问。", viewModel.uiState.value.pendingMemoryProposal?.content)

        viewModel.approvePendingMemoryProposal()
        advanceUntilIdle()

        val entry = memoryRepository.currentEntries().single()
        assertEquals("她不喜欢被突然逼问。", entry.content)
        assertTrue(viewModel.uiState.value.pendingMemoryProposal == null)
    }

    private fun enqueueStreamResponse(content: String) {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"")
            append(content.replace("\"", "\\\""))
            append("\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
    }

    private fun fixedPromptAssembler(
        systemPrompt: String,
    ): PromptContextAssembler {
        return object : PromptContextAssembler {
            override suspend fun assemble(
                settings: AppSettings,
                assistant: Assistant?,
                conversation: Conversation,
                userInputText: String,
                recentMessages: List<ChatMessage>,
                promptMode: com.example.myapplication.model.PromptMode,
                includePhoneSnapshot: Boolean,
            ): PromptContextResult {
                return PromptContextResult(
                    systemPrompt = systemPrompt,
                    debugDump = "debug",
                )
            }
        }
    }

    @Test
    fun refreshCurrentConversationSummary_rebuildsContextGovernanceImmediately() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "新的剧情摘要"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "余罪",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧巷",
            assistantId = assistant.id,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧巷",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
            messagesByConversation = mapOf(
                session.conversationId to (1..13).map { index ->
                    ChatMessage(
                        id = "m$index",
                        conversationId = session.conversationId,
                        role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                        content = "剧情消息$index",
                        createdAt = index.toLong(),
                    )
                },
            ),
        )
        val summaryRepository = FakeConversationSummaryRepository()
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = DefaultPromptContextAssembler(
                conversationSummaryRepository = summaryRepository,
            ),
            conversationSummaryRepository = summaryRepository,
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> = error("不应调用")

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = "新的剧情摘要",
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createChatCompletionAt(
                        url: String,
                        request: ChatCompletionRequest,
                    ): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(
                        url: String,
                        request: com.example.myapplication.model.ResponseApiRequest,
                    ): Response<com.example.myapplication.model.ResponseApiResponse> = error("不应调用")

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> = error("不应调用")
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.refreshCurrentConversationSummary()
        advanceUntilIdle()

        assertEquals("新的剧情摘要", summaryRepository.getSummary(session.conversationId)?.summary)
        assertEquals(5, viewModel.uiState.value.contextGovernance?.summaryCoveredMessageCount)
        assertEquals(ContextSummaryState.APPLIED, viewModel.uiState.value.contextGovernance?.summaryState)
    }

    @Test
    fun sendSpecialPlay_generatesGiftImageForRoleplayGiftCard() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧巷",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧巷",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        enqueueStreamResponse("<dialogue speaker=\"character\">我会好好收着。</dialogue>")
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
            giftImageModel = "gpt-image-1",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = DefaultPromptContextAssembler(),
            imageSaver = {
                SavedImageFile(
                    path = "/tmp/roleplay-gift.png",
                    mimeType = "image/png",
                    fileName = "roleplay-gift.png",
                )
            },
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> = error("不应调用")

                    override suspend fun createChatCompletion(
                        request: ChatCompletionRequest,
                    ): Response<ChatCompletionResponse> = createChatCompletionAt("", request)

                    override suspend fun createChatCompletionAt(
                        url: String,
                        request: ChatCompletionRequest,
                    ): Response<ChatCompletionResponse> {
                        val prompt = request.messages.firstOrNull()?.content?.toString().orEmpty()
                        val content = when {
                            prompt.contains("礼物生图提示词优化器") -> "黑胶唱片礼物特写，安静旧巷氛围，电影感。"
                            prompt.contains("persistent_memories") -> "{\"persistent_memories\":[],\"scene_state_memories\":[]}"
                            else -> "辅助结果"
                        }
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = content,
                                        ),
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun createResponseAt(
                        url: String,
                        request: com.example.myapplication.model.ResponseApiRequest,
                    ): Response<com.example.myapplication.model.ResponseApiResponse> = error("不应调用")

                    override suspend fun generateImage(
                        request: ImageGenerationRequest,
                    ): Response<ImageGenerationResponse> {
                        return Response.success(
                            ImageGenerationResponse(
                                data = listOf(
                                    com.example.myapplication.model.ImageGenerationDataDto(
                                        b64Json = "ZmFrZQ==",
                                        revisedPrompt = request.prompt,
                                    ),
                                ),
                            ),
                        )
                    }
                }
            },
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.sendSpecialPlay(
            GiftPlayDraft(
                target = "陆宴清",
                item = "黑胶唱片",
                note = "收好",
            ),
        )
        advanceUntilIdle()

        val specialMessage = viewModel.uiState.value.messages.first { it.contentType == RoleplayContentType.SPECIAL_PLAY }
        assertEquals(GiftImageStatus.SUCCEEDED, specialMessage.specialPart?.giftImageStatus())
        assertEquals("/tmp/roleplay-gift.png", specialMessage.specialPart?.specialMetadataValue("gift_image_uri"))
        assertTrue(viewModel.uiState.value.messages.any { it.contentType == RoleplayContentType.DIALOGUE })
    }

    @Test
    fun sendSpecialPlay_sendsPunishCardForRoleplay() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "旧巷",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conv-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "旧巷",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        enqueueStreamResponse("<dialogue speaker=\"character\">……知道了。</dialogue>")
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = DefaultPromptContextAssembler(),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.sendSpecialPlay(
            PunishPlayDraft(
                method = "戒尺",
                count = "三下",
                intensity = PunishIntensity.MEDIUM,
                reason = "撒谎",
                note = "边抽边认错",
            ),
        )
        advanceUntilIdle()

        val specialMessage = viewModel.uiState.value.messages.first { it.contentType == RoleplayContentType.SPECIAL_PLAY }
        assertEquals("punish", specialMessage.specialPart?.specialType?.protocolValue)
        assertEquals("戒尺", specialMessage.specialPart?.specialMetadataValue("method"))
        assertEquals("三下", specialMessage.specialPart?.specialMetadataValue("count"))
        assertEquals("medium", specialMessage.specialPart?.specialMetadataValue("intensity"))
        assertTrue(viewModel.uiState.value.messages.any {
            it.contentType == RoleplayContentType.DIALOGUE && it.content.contains("知道了")
        })
    }

    @Test
    fun sendVoiceMessage_sendsVoiceActionForOnlineRoleplay() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上夜聊",
            assistantId = assistant.id,
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )
        val session = RoleplaySession(
            id = "session-1",
            scenarioId = scenario.id,
            conversationId = "conversation-1",
            createdAt = 1L,
            updatedAt = 2L,
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "测试 Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "chat-model",
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = session.conversationId,
                    title = "线上夜聊",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                    assistantId = assistant.id,
                ),
            ),
        )
        enqueueStreamResponse("""["收到你的语音了。"]""")
        val viewModel = createViewModel(
            store = store,
            roleplayRepository = FakeRoleplayRepository(
                conversationStore = store,
                scenarios = listOf(scenario),
                sessions = listOf(session),
            ),
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            promptContextAssembler = DefaultPromptContextAssembler(),
        )

        viewModel.enterScenario(scenario.id)
        advanceUntilIdle()
        viewModel.sendVoiceMessage(
            VoiceMessageDraft(
                content = "我一会儿回你电话。",
                durationSeconds = 7,
            ),
        )
        advanceUntilIdle()

        val voiceMessage = viewModel.uiState.value.messages.first { it.contentType == RoleplayContentType.ACTION }
        assertEquals(ChatActionType.VOICE_MESSAGE, voiceMessage.actionPart?.actionType)
        assertEquals("我一会儿回你电话。", voiceMessage.actionPart?.actionMetadata?.get("content"))
        assertEquals("7", voiceMessage.actionPart?.actionMetadata?.get("duration_seconds"))
    }

    private fun createViewModel(
        store: FakeConversationStore,
        roleplayRepository: RoleplayRepository,
        settings: AppSettings,
        promptContextAssembler: PromptContextAssembler,
        memoryRepository: MemoryRepository = FakeMemoryRepository(),
        conversationSummaryRepository: ConversationSummaryRepository = FakeConversationSummaryRepository(),
        pendingMemoryProposalRepository: PendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
        nowProvider: () -> Long = incrementingNowProvider(1L),
        messageIdProvider: () -> String = idProviderOf("m1", "m2", "m3", "m4"),
        imageSaver: suspend (String) -> SavedImageFile = { error("测试不应保存图片") },
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
        streamClientProvider: ((String, String) -> OkHttpClient)? = null,
    ): RoleplayViewModel {
        val resolvedApiServiceProvider = apiServiceProvider ?: { baseUrl, apiKey ->
            ApiServiceFactory().create(
                baseUrl = baseUrl,
                apiKey = apiKey,
            )
        }
        val services = createTestAiServices(
            settings = settings,
            dispatcher = mainDispatcherRule.dispatcher,
            apiServiceProvider = resolvedApiServiceProvider,
            streamClientProvider = streamClientProvider ?: { _, _ ->
                OkHttpClient.Builder().build()
            },
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            pendingMemoryProposalRepository = pendingMemoryProposalRepository,
        )
        val conversationRepository = ConversationRepository(
            conversationStore = store,
            nowProvider = nowProvider,
        )
        return RoleplayViewModel(
            settingsRepository = services.settingsRepository,
            settingsEditor = services.settingsEditor,
            aiGateway = services.aiGateway,
            aiPromptExtrasService = services.aiPromptExtrasService,
            conversationRepository = conversationRepository,
            roleplayRepository = roleplayRepository,
            promptContextAssembler = promptContextAssembler,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            pendingMemoryProposalRepository = pendingMemoryProposalRepository,
            phoneSnapshotRepository = EmptyPhoneSnapshotRepository,
            memoryWriteService = DefaultMemoryWriteService(
                settingsStore = services.settingsStore,
                memoryRepository = memoryRepository,
                pendingMemoryProposalRepository = pendingMemoryProposalRepository,
                aiPromptExtrasService = services.aiPromptExtrasService,
            ),
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
            imageSaver = imageSaver,
        )
    }

    private fun incrementingNowProvider(start: Long): () -> Long {
        var current = start
        return {
            current++
            current - 1
        }
    }

    private fun idProviderOf(vararg ids: String): () -> String {
        val values = ArrayDeque(ids.toList())
        return {
            values.removeFirstOrNull() ?: error("测试消息 ID 不足")
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class FakeRoleplayRepository(
    private val conversationStore: FakeConversationStore,
    scenarios: List<RoleplayScenario>,
    sessions: List<RoleplaySession>,
    onlineMetaByConversation: Map<String, com.example.myapplication.model.RoleplayOnlineMeta> = emptyMap(),
) : RoleplayRepository {
    private val scenariosState = MutableStateFlow(scenarios)
    private val sessionsState = MutableStateFlow(sessions)
    private val onlineMetaState = MutableStateFlow(onlineMetaByConversation)

    override fun observeScenarios(): Flow<List<RoleplayScenario>> = scenariosState

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> {
        return scenariosState.map { scenarios ->
            scenarios.firstOrNull { it.id == scenarioId }
        }
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> {
        return sessionsState.map { sessions ->
            sessions.firstOrNull { it.scenarioId == scenarioId }
        }
    }

    override fun observeSessions(): Flow<List<RoleplaySession>> = sessionsState

    override fun observeDiaryEntries(conversationId: String): Flow<List<com.example.myapplication.model.RoleplayDiaryEntry>> {
        return flowOf(emptyList())
    }

    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> {
        return observeSessionByScenario(scenarioId).flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                conversationStore.observeMessages(session.conversationId)
            }
        }
    }

    override suspend fun listScenarios(): List<RoleplayScenario> = scenariosState.value

    override suspend fun getScenario(scenarioId: String): RoleplayScenario? {
        return scenariosState.value.firstOrNull { it.id == scenarioId }
    }

    override suspend fun upsertScenario(scenario: RoleplayScenario) {
        scenariosState.value = scenariosState.value.filterNot { it.id == scenario.id } + scenario
    }

    override suspend fun deleteScenario(scenarioId: String) {
        scenariosState.value = scenariosState.value.filterNot { it.id == scenarioId }
        sessionsState.value = sessionsState.value.filterNot { it.scenarioId == scenarioId }
    }

    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult {
        val scenario = getScenario(scenarioId) ?: error("场景不存在")
        val session = sessionsState.value.firstOrNull { it.scenarioId == scenarioId }
            ?: error("测试未预置会话")
        val conversationMessages = conversationStore.listMessages(session.conversationId)
        return RoleplaySessionStartResult(
            session = session,
            reusedExistingSession = true,
            hasHistory = conversationMessages.isNotEmpty(),
            assistantMismatch = false,
            conversationAssistantId = scenario.assistantId,
            conversationMessages = conversationMessages,
        )
    }

    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult {
        return startScenario(scenarioId)
    }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? {
        return sessionsState.value.firstOrNull { it.scenarioId == scenarioId }
    }

    override suspend fun getSession(sessionId: String): RoleplaySession? {
        return sessionsState.value.firstOrNull { it.id == sessionId }
    }

    override suspend fun listDiaryEntries(conversationId: String): List<com.example.myapplication.model.RoleplayDiaryEntry> {
        return emptyList()
    }

    override suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<com.example.myapplication.model.RoleplayDiaryDraft>,
    ): List<com.example.myapplication.model.RoleplayDiaryEntry> {
        return emptyList()
    }

    override suspend fun getOnlineMeta(conversationId: String): com.example.myapplication.model.RoleplayOnlineMeta? {
        return onlineMetaState.value[conversationId]
    }

    override suspend fun upsertOnlineMeta(meta: com.example.myapplication.model.RoleplayOnlineMeta) {
        onlineMetaState.value = onlineMetaState.value.toMutableMap().apply {
            this[meta.conversationId] = meta
        }
    }

    override suspend fun deleteOnlineMeta(conversationId: String) {
        onlineMetaState.value = onlineMetaState.value.toMutableMap().apply {
            remove(conversationId)
        }
    }

    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
private class DelayedStartRoleplayRepository(
    private val conversationStore: FakeConversationStore,
    scenarios: List<RoleplayScenario>,
    sessions: List<RoleplaySession>,
    private val startDelayByScenarioId: Map<String, Long>,
    onlineMetaByConversation: Map<String, com.example.myapplication.model.RoleplayOnlineMeta> = emptyMap(),
) : RoleplayRepository {
    private val scenariosState = MutableStateFlow(scenarios)
    private val sessionsState = MutableStateFlow(sessions)
    private val onlineMetaState = MutableStateFlow(onlineMetaByConversation)

    override fun observeScenarios(): Flow<List<RoleplayScenario>> = scenariosState

    override fun observeScenario(scenarioId: String): Flow<RoleplayScenario?> {
        return scenariosState.map { scenarios ->
            scenarios.firstOrNull { it.id == scenarioId }
        }
    }

    override fun observeSessionByScenario(scenarioId: String): Flow<RoleplaySession?> {
        return sessionsState.map { sessions ->
            sessions.firstOrNull { it.scenarioId == scenarioId }
        }
    }

    override fun observeSessions(): Flow<List<RoleplaySession>> = sessionsState

    override fun observeDiaryEntries(conversationId: String): Flow<List<com.example.myapplication.model.RoleplayDiaryEntry>> {
        return flowOf(emptyList())
    }

    override fun observeConversationMessages(scenarioId: String): Flow<List<ChatMessage>> {
        return observeSessionByScenario(scenarioId).flatMapLatest { session ->
            if (session == null) {
                flowOf(emptyList())
            } else {
                conversationStore.observeMessages(session.conversationId)
            }
        }
    }

    override suspend fun listScenarios(): List<RoleplayScenario> = scenariosState.value

    override suspend fun getScenario(scenarioId: String): RoleplayScenario? {
        return scenariosState.value.firstOrNull { it.id == scenarioId }
    }

    override suspend fun upsertScenario(scenario: RoleplayScenario) {
        scenariosState.value = scenariosState.value.filterNot { it.id == scenario.id } + scenario
    }

    override suspend fun deleteScenario(scenarioId: String) {
        scenariosState.value = scenariosState.value.filterNot { it.id == scenarioId }
        sessionsState.value = sessionsState.value.filterNot { it.scenarioId == scenarioId }
    }

    override suspend fun startScenario(scenarioId: String): RoleplaySessionStartResult {
        delay(startDelayByScenarioId[scenarioId] ?: 0L)
        val scenario = getScenario(scenarioId) ?: error("场景不存在")
        val session = sessionsState.value.firstOrNull { it.scenarioId == scenarioId }
            ?: error("测试未预置会话")
        val conversationMessages = conversationStore.listMessages(session.conversationId)
        return RoleplaySessionStartResult(
            session = session,
            reusedExistingSession = true,
            hasHistory = conversationMessages.isNotEmpty(),
            assistantMismatch = false,
            conversationAssistantId = scenario.assistantId,
            conversationMessages = conversationMessages,
        )
    }

    override suspend fun restartScenario(scenarioId: String): RoleplaySessionStartResult {
        return startScenario(scenarioId)
    }

    override suspend fun getSessionByScenario(scenarioId: String): RoleplaySession? {
        return sessionsState.value.firstOrNull { it.scenarioId == scenarioId }
    }

    override suspend fun getSession(sessionId: String): RoleplaySession? {
        return sessionsState.value.firstOrNull { it.id == sessionId }
    }

    override suspend fun listDiaryEntries(conversationId: String): List<com.example.myapplication.model.RoleplayDiaryEntry> {
        return emptyList()
    }

    override suspend fun replaceDiaryEntries(
        conversationId: String,
        scenarioId: String,
        entries: List<com.example.myapplication.model.RoleplayDiaryDraft>,
    ): List<com.example.myapplication.model.RoleplayDiaryEntry> {
        return emptyList()
    }

    override suspend fun getOnlineMeta(conversationId: String): com.example.myapplication.model.RoleplayOnlineMeta? {
        return onlineMetaState.value[conversationId]
    }

    override suspend fun upsertOnlineMeta(meta: com.example.myapplication.model.RoleplayOnlineMeta) {
        onlineMetaState.value = onlineMetaState.value.toMutableMap().apply {
            this[meta.conversationId] = meta
        }
    }

    override suspend fun deleteOnlineMeta(conversationId: String) {
        onlineMetaState.value = onlineMetaState.value.toMutableMap().apply {
            remove(conversationId)
        }
    }

    override suspend fun deleteDiaryEntriesForConversation(conversationId: String) = Unit
}
