package com.example.myapplication.viewmodel

import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.testutil.FakeConversationStore
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeSettingsStore
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

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
                                              {"label":"试探推进","text":"*我抬眼看向他* 你刚才那句话，到底是什么意思？"},
                                              {"label":"信息探索","text":"先告诉我，这里之前到底发生过什么。"},
                                              {"label":"情绪拉扯","text":"*我没有退开* 既然你知道，就别再瞒着我。"}
                                            ]
                                            """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        )
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

        val state = viewModel.uiState.value
        assertFalse(state.isGeneratingSuggestions)
        assertEquals(3, state.suggestions.size)
        assertEquals("试探推进", state.suggestions[0].label)
        assertTrue(state.suggestions[0].text.contains("你刚才那句话"))

        val request = lastSuggestionRequest ?: error("未记录到建议请求")
        assertEquals("rp-suggestion-model", request.model)
        assertTrue(request.messages[1].content.toString().contains("【剧情设定与上下文】"))
        assertTrue(request.messages[1].content.toString().contains("【最近剧情】"))
        assertTrue(request.messages[1].content.toString().contains("林晚"))
        assertTrue(request.messages[1].content.toString().contains("陆宴清"))
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
        viewModel.sendMessage()
        assertTrue(viewModel.uiState.value.suggestions.isEmpty())

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.suggestions.isEmpty())
        assertTrue(store.listMessages(session.conversationId).any { it.content.contains("你最好把话说清楚") })
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
            ): PromptContextResult {
                return PromptContextResult(
                    systemPrompt = systemPrompt,
                    debugDump = "debug",
                )
            }
        }
    }

    private fun createViewModel(
        store: FakeConversationStore,
        roleplayRepository: RoleplayRepository,
        settings: AppSettings,
        promptContextAssembler: PromptContextAssembler,
        memoryRepository: MemoryRepository = FakeMemoryRepository(),
        conversationSummaryRepository: ConversationSummaryRepository = FakeConversationSummaryRepository(),
        nowProvider: () -> Long = incrementingNowProvider(1L),
        messageIdProvider: () -> String = idProviderOf("m1", "m2", "m3", "m4"),
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
        streamClientProvider: ((String, String) -> OkHttpClient)? = null,
    ): RoleplayViewModel {
        val repository = AiRepository(
            settingsStore = FakeSettingsStore(settings),
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = apiServiceProvider ?: { baseUrl, apiKey ->
                ApiServiceFactory().create(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                )
            },
            streamClientProvider = streamClientProvider ?: { _, _ ->
                OkHttpClient.Builder().build()
            },
            ioDispatcher = mainDispatcherRule.dispatcher,
        )
        val conversationRepository = ConversationRepository(
            conversationStore = store,
            nowProvider = nowProvider,
        )
        return RoleplayViewModel(
            repository = repository,
            conversationRepository = conversationRepository,
            roleplayRepository = roleplayRepository,
            promptContextAssembler = promptContextAssembler,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
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
) : RoleplayRepository {
    private val scenariosState = MutableStateFlow(scenarios)
    private val sessionsState = MutableStateFlow(sessions)

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
        return RoleplaySessionStartResult(
            session = session,
            reusedExistingSession = true,
            hasHistory = conversationStore.listMessages(session.conversationId).isNotEmpty(),
            assistantMismatch = false,
            conversationAssistantId = scenario.assistantId,
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
}
