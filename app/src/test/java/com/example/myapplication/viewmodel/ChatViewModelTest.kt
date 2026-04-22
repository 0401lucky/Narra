package com.example.myapplication.viewmodel

import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.SavedImageFile
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.GiftImageStatus
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.ImageEditRequest
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationDataDto
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.PunishIntensity
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftImageStatus
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.testutil.FakeConversationStore
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.createTestAiServices
import com.google.gson.JsonParser
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
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
    fun init_createsDefaultConversationWhenStoreIsEmpty() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore()
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(),
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.conversations.size)
        assertTrue(state.currentConversationId.isNotBlank())
        assertEquals(DEFAULT_CONVERSATION_TITLE, state.currentConversationTitle)
        assertEquals(state.currentConversationId, store.listConversations().single().id)
        assertTrue(state.isConversationReady)
    }

    @Test
    fun selectConversation_clearsImmediatelyThenLoadsTargetMessages() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话一",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 20L,
                ),
                Conversation(
                    id = "c2",
                    title = "会话二",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 10L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "会话一消息",
                        createdAt = 1L,
                    ),
                ),
                "c2" to listOf(
                    ChatMessage(
                        id = "m2",
                        conversationId = "c2",
                        role = MessageRole.USER,
                        content = "会话二消息",
                        createdAt = 2L,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(),
        )

        advanceUntilIdle()
        assertEquals(listOf("会话一消息"), viewModel.uiState.value.messages.map { it.content })

        viewModel.selectConversation("c2")

        val intermediateState = viewModel.uiState.value
        assertEquals("c2", intermediateState.currentConversationId)
        assertEquals("c2", intermediateState.displayedConversationId)
        assertTrue(intermediateState.messages.isEmpty())
        assertFalse(intermediateState.isConversationReady)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("c2", state.currentConversationId)
        assertEquals("会话二", state.currentConversationTitle)
        assertEquals(listOf("会话二消息"), state.messages.map { it.content })
        assertTrue(state.isConversationReady)
    }

    @Test
    fun sendMessage_rejectsSendingWhileConversationIsSwitching() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话一",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 20L,
                ),
                Conversation(
                    id = "c2",
                    title = "会话二",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 10L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "旧上下文",
                        createdAt = 1L,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
        )

        advanceUntilIdle()
        viewModel.selectConversation("c2")
        viewModel.updateInput("现在发")
        viewModel.sendMessage()

        val state = viewModel.uiState.value
        assertEquals("会话切换中，请稍后再发送", state.errorMessage)
        assertTrue(state.messages.isEmpty())
        assertTrue(store.listMessages("c2").isEmpty())
    }

    @Test
    fun sendMessage_streamsReplyAndUpdatesConversationTitle() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse(content = "你好，我是助手")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(100L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("你好")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.errorMessage == null)
        assertEquals(listOf(MessageRole.USER, MessageRole.ASSISTANT), state.messages.map { it.role })
        assertEquals(listOf(MessageStatus.COMPLETED, MessageStatus.COMPLETED), state.messages.map { it.status })
        assertEquals(listOf("你好", "你好，我是助手"), state.messages.map { it.content })
        assertEquals("你好", state.currentConversationTitle)
        assertEquals("deepseek-chat", state.messages.last().modelName)
        assertEquals(listOf("你好", "你好，我是助手"), store.listMessages("c1").map { it.content })
    }

    @Test
    fun sendMessage_injectsScenarioIntoSystemPrompt() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
        )
        enqueueStreamResponse(content = "收到")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "侦探助手",
                        systemPrompt = "你是一名冷静的侦探顾问。",
                        scenario = "当前场景是一间维多利亚风格的事务所。",
                        greeting = "晚上好，先把线索整理一下。",
                        exampleDialogues = listOf(
                            "用户：先看证词还是账本？\n助手：先看账本，能更快锁定资金流向。",
                        ),
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
        )

        advanceUntilIdle()
        viewModel.updateInput("帮我分析")
        viewModel.sendMessage()
        advanceUntilIdle()

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val systemPrompt = requestBody
            .getAsJsonArray("messages")[0]
            .asJsonObject["content"]
            .asString

        assertTrue(systemPrompt.contains("你是一名冷静的侦探顾问。"))
        assertTrue(systemPrompt.contains("【场景设定】"))
        assertTrue(systemPrompt.contains("当前场景是一间维多利亚风格的事务所。"))
        assertTrue(systemPrompt.contains("【开场白参考】"))
        assertTrue(systemPrompt.contains("晚上好，先把线索整理一下。"))
        assertTrue(systemPrompt.contains("【示例对话】"))
        assertTrue(systemPrompt.contains("先看账本，能更快锁定资金流向。"))
        assertTrue(viewModel.uiState.value.latestPromptDebugDump.contains("【上下文调试】"))
        assertEquals(1, viewModel.uiState.value.contextGovernance?.sentMessageCount)
    }

    @Test
    fun editUserMessage_rewindsConversationAndRestoresInput() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val conversationSummaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "c1",
                    assistantId = "",
                    summary = "旧摘要",
                    coveredMessageCount = 2,
                    updatedAt = 20L,
                ),
            ),
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "第一句",
                        status = MessageStatus.COMPLETED,
                        createdAt = 1L,
                    ),
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "旧回复",
                        status = MessageStatus.COMPLETED,
                        createdAt = 2L,
                    ),
                    ChatMessage(
                        id = "user-2",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "补充说明",
                        status = MessageStatus.COMPLETED,
                        createdAt = 3L,
                        parts = listOf(
                            imageMessagePart(
                                uri = "content://image-1",
                                fileName = "picked.png",
                            ),
                        ),
                    ),
                    ChatMessage(
                        id = "assistant-2",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "后续回复",
                        status = MessageStatus.COMPLETED,
                        createdAt = 4L,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(),
            conversationSummaryRepository = conversationSummaryRepository,
        )

        advanceUntilIdle()
        viewModel.editUserMessage("user-2")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("补充说明", state.input)
        assertEquals(1, state.pendingParts.size)
        assertEquals("user-1", state.messages[0].id)
        assertEquals("assistant-1", state.messages[1].id)
        assertEquals(2, state.messages.size)
        assertEquals(1, store.replaceConversationSnapshotCount)
        assertEquals(2, store.listMessages("c1").size)
        assertEquals(null, conversationSummaryRepository.getSummary("c1"))
        assertEquals("已回退到这条用户消息，可修改后重新发送", state.noticeMessage)
    }

    @Test
    fun toggleMessageMemory_addsAndRemovesAssistantScopedMemory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "message-1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "请记住我喜欢短句回复。",
                        createdAt = 1L,
                    ),
                ),
            ),
        )
        val memoryRepository = FakeMemoryRepository()
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "记忆助手",
                        memoryEnabled = true,
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            memoryRepository = memoryRepository,
        )

        advanceUntilIdle()
        viewModel.toggleMessageMemory("message-1")
        advanceUntilIdle()

        val createdEntry = memoryRepository.currentEntries().single()
        assertEquals(MemoryScopeType.ASSISTANT, createdEntry.scopeType)
        assertEquals("assistant-1", createdEntry.scopeId)
        assertEquals("message-1", createdEntry.sourceMessageId)
        assertEquals("请记住我喜欢短句回复。", createdEntry.content)
        assertTrue(viewModel.uiState.value.rememberedMessageIds.contains("message-1"))

        viewModel.toggleMessageMemory("message-1")
        advanceUntilIdle()

        assertTrue(memoryRepository.currentEntries().isEmpty())
        assertTrue(viewModel.uiState.value.rememberedMessageIds.isEmpty())
    }

    @Test
    fun toggleMessageMemory_usesGlobalScopeWhenAssistantEnablesSharedMemory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "message-1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "请记住我常用中文和短句。",
                        createdAt = 1L,
                    ),
                ),
            ),
        )
        val memoryRepository = FakeMemoryRepository()
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "共享记忆助手",
                        memoryEnabled = true,
                        useGlobalMemory = true,
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            memoryRepository = memoryRepository,
        )

        advanceUntilIdle()
        viewModel.toggleMessageMemory("message-1")
        advanceUntilIdle()

        val createdEntry = memoryRepository.currentEntries().single()
        assertEquals(MemoryScopeType.GLOBAL, createdEntry.scopeType)
        assertEquals("", createdEntry.scopeId)
        assertEquals("message-1", createdEntry.sourceMessageId)
    }

    @Test
    fun sendMessage_withConversationSummaryTrimsRequestMessages() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val existingMessages = (1..8).flatMap { index ->
            listOf(
                ChatMessage(
                    id = "user-$index",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "用户消息 $index",
                    createdAt = index.toLong(),
                ),
                ChatMessage(
                    id = "assistant-$index",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "助手回复 $index",
                    createdAt = (index + 100).toLong(),
                ),
            )
        }
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to existingMessages,
            ),
        )
        enqueueStreamResponse(content = "继续调查")
        val summaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                com.example.myapplication.model.ConversationSummary(
                    conversationId = "c1",
                    assistantId = "assistant-1",
                    summary = "前文已经总结完成。",
                    coveredMessageCount = 12,
                    updatedAt = 10L,
                ),
            ),
        )
        val promptAssembler = DefaultPromptContextAssembler(
            conversationSummaryRepository = summaryRepository,
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "摘要助手",
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            conversationSummaryRepository = summaryRepository,
            promptContextAssembler = promptAssembler,
        )

        advanceUntilIdle()
        viewModel.updateInput("现在继续")
        viewModel.sendMessage()
        advanceUntilIdle()

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val messages = requestBody.getAsJsonArray("messages")
        val systemPrompt = messages[0].asJsonObject["content"].asString
        assertTrue(systemPrompt.contains("【对话摘要】"))
        assertTrue(messages.size() < existingMessages.size + 2)
    }

    @Test
    fun sendMessage_withStaleConversationSummary_keepsIntermediateMessages() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val existingMessages = (1..8).flatMap { index ->
            listOf(
                ChatMessage(
                    id = "user-$index",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "用户消息 $index",
                    createdAt = index.toLong(),
                ),
                ChatMessage(
                    id = "assistant-$index",
                    conversationId = "c1",
                    role = MessageRole.ASSISTANT,
                    content = "助手回复 $index",
                    createdAt = (index + 100).toLong(),
                ),
            )
        }
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to existingMessages,
            ),
        )
        enqueueStreamResponse(content = "继续调查")
        val summaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                com.example.myapplication.model.ConversationSummary(
                    conversationId = "c1",
                    assistantId = "assistant-1",
                    summary = "只覆盖了更早的一小段。",
                    coveredMessageCount = 4,
                    updatedAt = 10L,
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "摘要助手",
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            conversationSummaryRepository = summaryRepository,
            promptContextAssembler = DefaultPromptContextAssembler(
                conversationSummaryRepository = summaryRepository,
            ),
        )

        advanceUntilIdle()
        viewModel.updateInput("现在继续")
        viewModel.sendMessage()
        advanceUntilIdle()

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val messages = requestBody.getAsJsonArray("messages")
        assertEquals(existingMessages.size + 2, messages.size())
        assertEquals(
            ContextSummaryState.STALE,
            viewModel.uiState.value.contextGovernance?.summaryState,
        )
    }

    @Test
    fun sendMessage_whenAssistantMemoryEnabled_generatesAutomaticMemory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
        )
        enqueueStreamResponse(content = "好的，我之后会尽量用短句回复。")
        val memoryRepository = FakeMemoryRepository()
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "deepseek-chat",
            titleSummaryModel = "title-model",
            chatSuggestionModel = "suggestion-model",
            memoryModel = "memory-model",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "记忆助手",
                        memoryEnabled = true,
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            memoryRepository = memoryRepository,
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        val prompt = request.messages.firstOrNull()?.content?.toString().orEmpty()
                        val content = when {
                            prompt.contains("长期记忆提取器") -> "[\"用户喜欢短句回复\"]"
                            prompt.contains("总结以下对话的主题") -> "短句偏好"
                            prompt.contains("生成3个简短的后续问题建议") -> "继续聊\n换个话题\n总结一下"
                            prompt.contains("压缩成一段简洁摘要") -> "用户希望后续回复尽量简洁。"
                            else -> "[]"
                        }
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        message = AssistantMessageDto(
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

        advanceUntilIdle()
        viewModel.updateInput("请记住我更喜欢短句回复。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val memories = memoryRepository.currentEntries()
        assertEquals(1, memories.size)
        assertEquals(MemoryScopeType.ASSISTANT, memories.single().scopeType)
        assertEquals("assistant-1", memories.single().scopeId)
        assertEquals("用户喜欢短句回复", memories.single().content)
    }

    @Test
    fun sendMessage_whenSharedMemoryEnabled_writesCondensedGlobalMemory() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
        )
        enqueueStreamResponse(content = "好的，我会记住你的偏好。")
        val memoryRepository = FakeMemoryRepository(
            initialEntries = listOf(
                MemoryEntry(
                    id = "old-1",
                    scopeType = MemoryScopeType.GLOBAL,
                    scopeId = "",
                    content = "用户喜欢中文回复。",
                    importance = 60,
                ),
                MemoryEntry(
                    id = "old-2",
                    scopeType = MemoryScopeType.GLOBAL,
                    scopeId = "",
                    content = "用户偏好短句。",
                    importance = 60,
                ),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "deepseek-chat",
            titleSummaryModel = "title-model",
            chatSuggestionModel = "suggestion-model",
            memoryModel = "memory-model",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(
                    Assistant(
                        id = "assistant-1",
                        name = "共享记忆助手",
                        memoryEnabled = true,
                        useGlobalMemory = true,
                    ),
                ),
                selectedAssistantId = "assistant-1",
            ),
            memoryRepository = memoryRepository,
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        val prompt = request.messages.firstOrNull()?.content?.toString().orEmpty()
                        val content = when {
                            prompt.contains("长期记忆提取器") -> "[\"用户喜欢中文和短句回复\", \"用户更希望我别写太长\"]"
                            prompt.contains("角色长期记忆整理器") -> "[\"用户长期偏好中文和短句回复。\", \"用户不喜欢过长输出。\"]"
                            prompt.contains("总结以下对话的主题") -> "偏好总结"
                            prompt.contains("生成3个简短的后续问题建议") -> "继续聊\n换个话题\n总结一下"
                            prompt.contains("压缩成一段简洁摘要") -> "用户偏好中文短句。"
                            else -> "[]"
                        }
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        message = AssistantMessageDto(
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

        advanceUntilIdle()
        viewModel.updateInput("请记住我常用中文和短句回复，别写太长。")
        viewModel.sendMessage()
        advanceUntilIdle()

        val memories = memoryRepository.currentEntries()
        assertEquals(4, memories.size)
        assertTrue(memories.all { it.scopeType == MemoryScopeType.GLOBAL })
        assertTrue(memories.all { it.scopeId.isBlank() })
        assertTrue(memories.any { it.content.contains("中文和短句") })
        assertTrue(memories.any { it.content.contains("别写太长") })
    }

    @Test
    fun sendMessage_streamsReasoningIntoAssistantMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse(
            content = "最终答案",
            reasoning = "先分析问题",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(150L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("解释一下")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantMessage = viewModel.uiState.value.messages.last()
        assertEquals("最终答案", assistantMessage.content)
        assertEquals("先分析问题", assistantMessage.reasoningContent)
    }

    @Test
    fun sendMessage_withImageGenerationModelCallsImagesEndpoint() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        var generateImageCalled = false
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "grok-imagine-1.0",
            ),
            nowProvider = incrementingNowProvider(175L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        error("不应调用聊天接口")
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        generateImageCalled = true
                        return Response.success(
                            ImageGenerationResponse(
                                data = listOf(
                                    ImageGenerationDataDto(
                                        url = "https://cdn.example.com/generated/no-extension-signed",
                                        revisedPrompt = "修订后的提示词",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            },
        )

        advanceUntilIdle()
        viewModel.updateInput("画一只猫")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantMessage = viewModel.uiState.value.messages.last()
        assertEquals(MessageStatus.COMPLETED, assistantMessage.status)
        assertEquals("修订后的提示词", assistantMessage.content)
        assertEquals(
            listOf("https://cdn.example.com/generated/no-extension-signed"),
            assistantMessage.attachments.map { it.uri },
        )
        assertEquals(2, assistantMessage.parts.size)
        assertTrue(generateImageCalled)
    }

    @Test
    fun sendMessage_withProviderImageAbilityOverrideCallsImagesEndpoint() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        var generateImageCalled = false
        val provider = ProviderSettings(
            id = "provider-1",
            name = "自定义提供商",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "custom-image-model",
            models = listOf(
                ModelInfo(
                    modelId = "custom-image-model",
                    abilities = setOf(ModelAbility.IMAGE_GENERATION),
                    abilitiesCustomized = true,
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            nowProvider = incrementingNowProvider(180L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        error("不应调用聊天接口")
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        generateImageCalled = true
                        return Response.success(
                            ImageGenerationResponse(
                                data = listOf(
                                    ImageGenerationDataDto(
                                        url = "https://cdn.example.com/generated/provider-override",
                                        revisedPrompt = "覆盖后的生图提示词",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            },
        )

        advanceUntilIdle()
        viewModel.updateInput("画一张海报")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantMessage = viewModel.uiState.value.messages.last()
        assertEquals(MessageStatus.COMPLETED, assistantMessage.status)
        assertEquals("覆盖后的生图提示词", assistantMessage.content)
        assertEquals("custom-image-model", assistantMessage.modelName)
        assertTrue(generateImageCalled)
    }

    @Test
    fun sendMessage_withReferenceImageAndImageGenerationModelCallsEditImage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        var generateImageCalled = false
        var editImageCalled = false
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = "https://example.com/v1/",
                apiKey = "key",
                selectedModel = "gpt-image-1",
            ),
            nowProvider = incrementingNowProvider(185L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            imagePayloadResolver = { attachment ->
                assertEquals("ref.png", attachment.fileName)
                "data:image/png;base64,ref-image"
            },
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        generateImageCalled = true
                        return Response.success(
                            ImageGenerationResponse(
                                data = listOf(
                                    ImageGenerationDataDto(
                                        url = "https://cdn.example.com/generated/unexpected",
                                        revisedPrompt = "不应走纯生图",
                                    ),
                                ),
                            ),
                        )
                    }

                    override suspend fun editImage(request: ImageEditRequest): Response<ImageGenerationResponse> {
                        editImageCalled = true
                        assertEquals("gpt-image-1", request.model)
                        assertEquals("把这张图改成海报风", request.prompt)
                        assertEquals(
                            listOf("data:image/png;base64,ref-image"),
                            request.images.map { it.imageUrl },
                        )
                        return Response.success(
                            ImageGenerationResponse(
                                data = listOf(
                                    ImageGenerationDataDto(
                                        url = "https://cdn.example.com/generated/edited",
                                        revisedPrompt = "改图后的提示词",
                                    ),
                                ),
                            ),
                        )
                    }
                }
            },
        )

        advanceUntilIdle()
        viewModel.addPendingParts(
            listOf(
                imageMessagePart(
                    uri = "content://image/1",
                    mimeType = "image/png",
                    fileName = "ref.png",
                ),
            ),
        )
        viewModel.updateInput("把这张图改成海报风")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantMessage = viewModel.uiState.value.messages.last()
        assertTrue(editImageCalled)
        assertFalse(generateImageCalled)
        assertEquals(MessageStatus.COMPLETED, assistantMessage.status)
        assertEquals("改图后的提示词", assistantMessage.content)
        assertEquals(
            listOf("https://cdn.example.com/generated/edited"),
            assistantMessage.attachments.map { it.uri },
        )
    }

    @Test
    fun sendMessage_streamsImagePartsIntoAssistantMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueRawStreamResponse(
            buildString {
                append(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"这是生成结果\",\"images\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://cdn.example.com/generated/from-stream\"}}]},\"index\":0}]}\n\n",
                )
                append("data: [DONE]\n\n")
            },
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(220L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("发张图")
        viewModel.sendMessage()
        advanceUntilIdle()

        val assistantMessage = viewModel.uiState.value.messages.last()
        assertEquals("这是生成结果", assistantMessage.content)
        assertEquals(2, assistantMessage.parts.size)
        assertEquals("https://cdn.example.com/generated/from-stream", assistantMessage.parts.last().uri)
    }

    @Test
    fun sendMessage_withPendingImagePartStoresUserParts() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse(content = "我看到了")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "gpt-4o",
            ),
            nowProvider = incrementingNowProvider(230L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("看看这张图")
        viewModel.addPendingParts(
            listOf(
                com.example.myapplication.model.imageMessagePart(
                    uri = "content://picked/image",
                    mimeType = "image/png",
                    fileName = "cat.png",
                ),
            ),
        )
        viewModel.sendMessage()
        advanceUntilIdle()

        val userMessage = viewModel.uiState.value.messages.first()
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals("看看这张图", userMessage.content)
        assertEquals(2, userMessage.parts.size)
        assertEquals("content://picked/image", userMessage.parts.last().uri)
        assertEquals(1, userMessage.attachments.size)
    }

    @Test
    fun sendMessage_failureMarksAssistantMessageAsError() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(200L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("你好")
        viewModel.sendMessage()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.errorMessage != null)
        assertEquals(listOf(MessageStatus.COMPLETED, MessageStatus.ERROR), state.messages.map { it.status })
    }

    @Test
    fun retryMessage_retriesFailedMessageViaStream() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        server.enqueue(MockResponse().setResponseCode(500))
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(300L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.updateInput("你好")
        viewModel.sendMessage()
        advanceUntilIdle()
        val failedMessageId = viewModel.uiState.value.messages.last().id

        enqueueStreamResponse(content = "重试成功")
        viewModel.retryMessage(failedMessageId)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.errorMessage == null)
        assertEquals(listOf(MessageStatus.COMPLETED, MessageStatus.COMPLETED), state.messages.map { it.status })
        assertEquals("重试成功", state.messages.last().content)
    }

    @Test
    fun retryMessage_regeneratesCompletedAssistantMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "user-1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "你好",
                        createdAt = 1L,
                    ),
                    ChatMessage(
                        id = "assistant-1",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "旧回答",
                        status = MessageStatus.COMPLETED,
                        createdAt = 2L,
                        modelName = "deepseek-chat",
                    ),
                ),
            ),
        )
        enqueueStreamResponse(content = "新回答")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(400L),
            messageIdProvider = idProviderOf("unused"),
        )

        advanceUntilIdle()
        viewModel.retryMessage("assistant-1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.errorMessage == null)
        assertEquals(listOf("你好", "新回答"), state.messages.map { it.content })
        assertEquals(listOf(MessageStatus.COMPLETED, MessageStatus.COMPLETED), state.messages.map { it.status })
    }

    @Test
    fun createConversation_switchesToNewConversation() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "原始会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "原始消息",
                        createdAt = 1L,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(),
        )
        advanceUntilIdle()

        assertEquals("c1", viewModel.uiState.value.currentConversationId)
        assertEquals(1, viewModel.uiState.value.messages.size)

        viewModel.createConversation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentConversationId != "c1")
        assertTrue(state.currentConversationId.isNotBlank())
        assertEquals(DEFAULT_CONVERSATION_TITLE, state.currentConversationTitle)
        assertTrue(state.messages.isEmpty())
        assertEquals(2, state.conversations.size)
    }

    @Test
    fun deleteCurrentConversation_switchesToRemainingConversation() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话一",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 20L,
                ),
                Conversation(
                    id = "c2",
                    title = "会话二",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 10L,
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(),
        )
        advanceUntilIdle()

        assertEquals("c1", viewModel.uiState.value.currentConversationId)

        viewModel.deleteCurrentConversation()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.currentConversationId.isNotBlank())
        assertTrue(state.currentConversationId != "c1")
        assertTrue(state.isConversationReady)
    }

    @Test
    fun translateDraftInput_updatesTranslationState() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "deepseek-chat",
            translationModel = "gpt-4o-mini",
        )
        val viewModel = createViewModel(
            store = FakeConversationStore(),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    com.example.myapplication.model.ChatChoiceDto(
                                        message = com.example.myapplication.model.AssistantMessageDto(
                                            content = "Hello",
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

        advanceUntilIdle()
        viewModel.updateInput("你好")
        viewModel.translateDraftInput()
        advanceUntilIdle()

        val translation = viewModel.uiState.value.translation
        assertTrue(translation.isVisible)
        assertEquals("Hello", translation.translatedText)
        assertEquals("gpt-4o-mini", translation.modelName)
    }

    @Test
    fun sendTransferPlay_createsTransferCardUserMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse(content = "收到这笔钱。")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
            nowProvider = incrementingNowProvider(100L),
            messageIdProvider = idProviderOf("user-transfer", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.sendTransferPlay(
            counterparty = "陆宴清",
            amount = "88.00",
            note = "买奶茶",
        )
        advanceUntilIdle()

        val userMessage = viewModel.uiState.value.messages.first()
        val transferPart = userMessage.parts.first()
        assertEquals(MessageRole.USER, userMessage.role)
        assertEquals(com.example.myapplication.model.ChatMessagePartType.SPECIAL, transferPart.type)
        assertEquals(TransferDirection.USER_TO_ASSISTANT, transferPart.specialDirection)
        assertEquals(TransferStatus.PENDING, transferPart.specialStatus)
        assertEquals("陆宴清", transferPart.specialCounterparty)
        assertEquals("88.00", transferPart.specialAmount)
        assertEquals("买奶茶", transferPart.specialNote)
    }

    @Test
    fun confirmTransferReceipt_updatesAssistantTransferCardStatus() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val transferPart = transferMessagePart(
            id = "transfer-1",
            direction = TransferDirection.ASSISTANT_TO_USER,
            status = TransferStatus.PENDING,
            counterparty = "陆宴清",
            amount = "188.00",
            note = "给你的零花钱",
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "assistant-transfer",
                        conversationId = "c1",
                        role = MessageRole.ASSISTANT,
                        content = "转账 188.00",
                        parts = listOf(transferPart),
                        createdAt = 1L,
                    ),
                ),
            ),
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "key",
                selectedModel = "deepseek-chat",
            ),
        )

        advanceUntilIdle()
        viewModel.confirmTransferReceipt("transfer-1")
        advanceUntilIdle()

        val updatedPart = viewModel.uiState.value.messages.first().parts.first()
        assertEquals(TransferStatus.RECEIVED, updatedPart.specialStatus)
    }

    private fun enqueueStreamResponse(
        content: String,
        reasoning: String = "",
    ) {
        val sseBody = buildString {
            if (reasoning.isNotBlank()) {
                append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"$reasoning\"},\"index\":0}]}\n\n")
            }
            append("data: {\"choices\":[{\"delta\":{\"content\":\"$content\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
    }

    private fun enqueueRawStreamResponse(
        sseBody: String,
    ) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
    }

    @Test
    fun rapidAssistantSwitching_doesNotCrashOrLoseState() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore()
        val assistantA = Assistant(id = "assistant-a", name = "A")
        val assistantB = Assistant(id = "assistant-b", name = "B")
        val settings = AppSettings(
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "test-model",
            assistants = listOf(assistantA, assistantB),
        )
        val viewModel = createViewModel(
            store = store,
            settings = settings,
            messageIdProvider = {
                java.util.UUID.randomUUID().toString()
            },
        )
        advanceUntilIdle()

        viewModel.selectAssistant("assistant-a")
        viewModel.selectAssistant("assistant-b")
        viewModel.selectAssistant("assistant-a")
        viewModel.selectAssistant("assistant-b")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isSending)
        assertTrue(state.currentConversationId.isNotBlank())
    }

    @Test
    fun rapidConversationSwitching_maintainsConsistentState() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val store = FakeConversationStore()
        val settings = AppSettings(
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            selectedModel = "test-model",
        )
        val viewModel = createViewModel(
            store = store,
            settings = settings,
            messageIdProvider = {
                java.util.UUID.randomUUID().toString()
            },
        )
        advanceUntilIdle()

        viewModel.createConversation()
        advanceUntilIdle()
        viewModel.createConversation()
        advanceUntilIdle()

        val conversations = viewModel.uiState.value.conversations
        assertTrue(conversations.size >= 2)

        viewModel.selectConversation(conversations[0].id)
        viewModel.selectConversation(conversations[1].id)
        viewModel.selectConversation(conversations[0].id)
        advanceUntilIdle()

        val finalState = viewModel.uiState.value
        assertEquals(conversations[0].id, finalState.currentConversationId)
        assertFalse(finalState.isSending)
    }

    @Test
    fun refreshConversationSummary_rebuildsContextGovernanceImmediately() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "新的聊天摘要"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val conversationId = "c1"
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = conversationId,
                    title = "旧对话",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
            messagesByConversation = mapOf(
                conversationId to (1..21).map { index ->
                    ChatMessage(
                        id = "m$index",
                        conversationId = conversationId,
                        role = if (index % 2 == 0) MessageRole.ASSISTANT else MessageRole.USER,
                        content = "消息$index",
                        createdAt = index.toLong(),
                    )
                },
            ),
        )
        val summaryRepository = FakeConversationSummaryRepository()
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "chat-model",
            titleSummaryModel = "summary-model",
        )
        val assistant = Assistant(id = "assistant-1", name = "助手")
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(assistant),
                selectedAssistantId = assistant.id,
            ),
            conversationSummaryRepository = summaryRepository,
            promptContextAssembler = DefaultPromptContextAssembler(
                conversationSummaryRepository = summaryRepository,
            ),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> = error("不应调用")

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        return Response.success(
                            ChatCompletionResponse(
                                choices = listOf(
                                    ChatChoiceDto(
                                        index = 0,
                                        message = AssistantMessageDto(
                                            role = "assistant",
                                            content = "新的聊天摘要",
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

        advanceUntilIdle()
        viewModel.refreshConversationSummary()
        advanceUntilIdle()

        assertEquals("新的聊天摘要", summaryRepository.getSummary(conversationId)?.summary)
        assertEquals(9, viewModel.uiState.value.contextGovernance?.summaryCoveredMessageCount)
        assertEquals(ContextSummaryState.APPLIED, viewModel.uiState.value.contextGovernance?.summaryState)
    }

    @Test
    fun sendSpecialPlay_generatesGiftImageAndKeepsSingleGiftMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val conversationId = "c1"
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = conversationId,
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                    assistantId = "assistant-1",
                ),
            ),
        )
        enqueueStreamResponse("礼物我收下了。")
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "deepseek-chat",
            giftImageModel = "gpt-image-1",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
                assistants = listOf(Assistant(id = "assistant-1", name = "陆宴清")),
                selectedAssistantId = "assistant-1",
            ),
            nowProvider = incrementingNowProvider(100L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
            imageSaver = {
                SavedImageFile(
                    path = "/tmp/gift.png",
                    mimeType = "image/png",
                    fileName = "gift.png",
                )
            },
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
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
                            prompt.contains("礼物生图提示词优化器") -> "一张黑胶唱片礼物特写，柔和光影，电影感构图。"
                            prompt.contains("请用不超过15个字总结以下对话的主题") -> "礼物互动"
                            else -> "通用结果"
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
                                    ImageGenerationDataDto(
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

        advanceUntilIdle()
        viewModel.sendSpecialPlay(
            GiftPlayDraft(
                target = "陆宴清",
                item = "黑胶唱片",
                note = "收好",
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        val giftPart = state.messages.first().parts.single()
        assertEquals(GiftImageStatus.SUCCEEDED, giftPart.giftImageStatus())
        assertEquals("/tmp/gift.png", giftPart.specialMetadataValue("gift_image_uri"))
        assertEquals("礼物我收下了。", state.messages.last().content)
    }

    @Test
    fun sendSpecialPlay_withoutGiftImageModelShowsNoticeAndKeepsPlainGiftCard() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val conversationId = "c1"
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = conversationId,
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse("收到礼物")
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "deepseek-chat",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            nowProvider = incrementingNowProvider(200L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.sendSpecialPlay(
            GiftPlayDraft(
                target = "陆宴清",
                item = "黑胶唱片",
            ),
        )
        advanceUntilIdle()

        val giftPart = viewModel.uiState.value.messages.first().parts.single()
        assertEquals(null, giftPart.giftImageStatus())
        assertEquals("未配置礼物生图模型，已按普通礼物卡发送", viewModel.uiState.value.noticeMessage)
    }

    @Test
    fun sendSpecialPlay_sendsPunishCardAndReceivesAssistantReply() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val conversationId = "c1"
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = conversationId,
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        enqueueStreamResponse("……知道了。")
        val provider = ProviderSettings(
            id = "provider-1",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "key",
            selectedModel = "deepseek-chat",
        )
        val viewModel = createViewModel(
            store = store,
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            nowProvider = incrementingNowProvider(300L),
            messageIdProvider = idProviderOf("user-1", "assistant-1"),
        )

        advanceUntilIdle()
        viewModel.sendSpecialPlay(
            PunishPlayDraft(
                method = "戒尺",
                count = "三下",
                intensity = PunishIntensity.HEAVY,
                reason = "撒谎",
                note = "边抽边认错",
            ),
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        val punishPart = state.messages.first().parts.single()
        assertEquals("punish", punishPart.specialType?.protocolValue)
        assertEquals("戒尺", punishPart.specialMetadataValue("method"))
        assertEquals("三下", punishPart.specialMetadataValue("count"))
        assertEquals("heavy", punishPart.specialMetadataValue("intensity"))
        assertEquals("……知道了。", state.messages.last().content)
    }

    private fun createViewModel(
        store: FakeConversationStore,
        settings: AppSettings,
        memoryRepository: com.example.myapplication.data.repository.context.MemoryRepository = EmptyMemoryRepository,
        conversationSummaryRepository: com.example.myapplication.data.repository.context.ConversationSummaryRepository = EmptyConversationSummaryRepository,
        promptContextAssembler: PromptContextAssembler = DefaultPromptContextAssembler(),
        nowProvider: () -> Long = incrementingNowProvider(1L),
        messageIdProvider: () -> String = idProviderOf("m1", "m2", "m3", "m4"),
        imageSaver: suspend (String) -> SavedImageFile = { error("测试不应保存图片") },
        imagePayloadResolver: suspend (com.example.myapplication.model.MessageAttachment) -> String = { error("测试不应解析图片") },
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
    ): ChatViewModel {
        val resolvedApiServiceProvider = apiServiceProvider ?: { _, _ ->
            object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                override suspend fun listModels(): Response<ModelsResponse> {
                    error("默认测试桩不应调用模型接口")
                }

                override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                    return Response.error(
                        500,
                        """{"error":"disabled in unit test"}"""
                            .toResponseBody("application/json".toMediaType()),
                    )
                }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                    error("默认测试桩不应调用生图接口")
                }
            }
        }
        val services = createTestAiServices(
            settings = settings,
            dispatcher = mainDispatcherRule.dispatcher,
            apiServiceProvider = resolvedApiServiceProvider,
            streamClientProvider = { _, _ ->
                OkHttpClient.Builder().build()
            },
            imagePayloadResolver = imagePayloadResolver,
        )
        val conversationRepository = ConversationRepository(
            conversationStore = store,
            nowProvider = nowProvider,
        )
        return ChatViewModel(
            settingsRepository = services.settingsRepository,
            aiGateway = services.aiGateway,
            aiPromptExtrasService = services.aiPromptExtrasService,
            aiTranslationService = services.aiTranslationService,
            conversationRepository = conversationRepository,
            memoryRepository = memoryRepository,
            conversationSummaryRepository = conversationSummaryRepository,
            promptContextAssembler = promptContextAssembler,
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
class MainDispatcherRule(
    val dispatcher: TestDispatcher = kotlinx.coroutines.test.StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        kotlinx.coroutines.Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
