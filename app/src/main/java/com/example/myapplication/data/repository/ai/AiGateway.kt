package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.ai.tooling.GetConversationSummaryTool
import com.example.myapplication.data.repository.ai.tooling.MemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.NoOpMemoryWriteService
import com.example.myapplication.data.repository.ai.tooling.ReadMemoryTool
import com.example.myapplication.data.repository.ai.tooling.ResolvedGatewayTooling
import com.example.myapplication.data.repository.ai.tooling.SaveMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SearchWebTool
import com.example.myapplication.data.repository.ai.tooling.SearchWorldBookTool
import com.example.myapplication.data.repository.ai.tooling.ToolAvailabilityResolver
import com.example.myapplication.data.repository.ai.tooling.ToolContinuation
import com.example.myapplication.data.repository.ai.tooling.ToolEngine
import com.example.myapplication.data.repository.ai.tooling.ToolRegistry
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatCompletionChunk
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.buildThinkingRequestConfig
import com.example.myapplication.model.legacyReasoningStepsFromContent
import com.example.myapplication.model.normalizeChatReasoningSteps
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Collections

// 采样配置和请求构建已提取到 GatewayRequestSupport

interface AiGateway {
    suspend fun generateImage(
        prompt: String,
        modelId: String = "",
    ): List<ImageGenerationResult>

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        toolingOptions: GatewayToolingOptions = GatewayToolingOptions(),
    ): AssistantReply

    fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
        toolingOptions: GatewayToolingOptions = GatewayToolingOptions(),
    ): Flow<ChatStreamEvent>

    fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
    ): ParsedAssistantSpecialOutput
}

class DefaultAiGateway(
    private val settingsStore: SettingsStore,
    private val apiServiceFactory: ApiServiceFactory,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        apiServiceFactory.create(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val anthropicApiProvider: (String, String) -> AnthropicApi = { baseUrl, apiKey ->
        apiServiceFactory.createAnthropic(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val streamClientProvider: (String, String) -> OkHttpClient = { baseUrl, apiKey ->
        apiServiceFactory.createStreamingClient(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val anthropicStreamClientProvider: (String, String) -> OkHttpClient = { baseUrl, apiKey ->
        apiServiceFactory.createAnthropicStreamingClient(
            baseUrl = baseUrl,
            apiKey = apiKey,
        )
    },
    private val imagePayloadResolver: suspend (MessageAttachment) -> String = {
        throw IllegalStateException("当前环境不支持图片发送")
    },
    private val filePromptResolver: suspend (MessageAttachment) -> String = {
        throw IllegalStateException("当前环境不支持文件发送")
    },
    private val searchRepository: SearchRepository,
    private val memoryRepository: MemoryRepository = EmptyMemoryRepository,
    private val worldBookRepository: WorldBookRepository = EmptyWorldBookRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository = EmptyConversationSummaryRepository,
    private val memoryWriteService: MemoryWriteService = NoOpMemoryWriteService,
    private val toolAvailabilityResolver: ToolAvailabilityResolver = ToolAvailabilityResolver(
        searchRepository = searchRepository,
        memoryRepository = memoryRepository,
        worldBookRepository = worldBookRepository,
        conversationSummaryRepository = conversationSummaryRepository,
    ),
    private val toolRegistry: ToolRegistry = ToolRegistry(
        listOf(
            ReadMemoryTool(),
            GetConversationSummaryTool(),
            SearchWorldBookTool(),
            SaveMemoryTool(),
            SearchWebTool(),
        ),
    ),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiGateway {
    private val gson = AppJson.gson
    private val toolEngine = ToolEngine(
        toolRegistry = toolRegistry,
        apiServiceProvider = apiServiceProvider,
        anthropicApiProvider = anthropicApiProvider,
        openAiTextUrlBuilder = ::buildOpenAiTextUrl,
        requestBuilder = { spec ->
            buildRequestWithRoleplaySampling(
                model = spec.model,
                messages = spec.messages,
                baseUrl = spec.baseUrl,
                apiProtocol = spec.apiProtocol,
                stream = spec.stream,
                reasoningEffort = spec.reasoningEffort,
                thinking = spec.thinking,
                promptMode = spec.promptMode,
                tools = spec.tools,
                toolChoice = spec.toolChoice,
            )
        },
    )
    private val roleplaySamplingDisabledBaseUrls = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun generateImage(
        prompt: String,
        modelId: String,
    ): List<ImageGenerationResult> {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = modelId.trim().ifBlank {
            activeProvider?.selectedModel ?: settings.selectedModel
        }
        if (activeProvider?.resolvedApiProtocol() == ProviderApiProtocol.ANTHROPIC) {
            throw IllegalStateException("Anthropic /messages 协议当前不支持图片生成")
        }

        val response = apiServiceProvider(baseUrl, apiKey).generateImage(
            ImageGenerationRequest(
                model = selectedModel,
                prompt = prompt,
                n = 1,
                responseFormat = "b64_json",
            ),
        )

        if (!response.isSuccessful) {
            throw IllegalStateException("图片生成失败：${response.code()}")
        }

        val data = response.body()?.data.orEmpty()
        if (data.isEmpty()) {
            throw IllegalStateException("图片生成接口未返回数据")
        }

        return data.map { item ->
            ImageGenerationResult(
                b64Data = item.b64Json.orEmpty(),
                url = item.url.orEmpty(),
                revisedPrompt = item.revisedPrompt.orEmpty(),
            )
        }
    }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        toolingOptions: GatewayToolingOptions,
    ): AssistantReply {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)
        val apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE

        val requestMessages = toRequestMessages(messages, systemPrompt)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }
        val gatewayTooling = toolAvailabilityResolver.resolve(
            settings = settings,
            activeProvider = activeProvider,
            selectedModel = selectedModel,
            promptMode = PromptMode.CHAT,
            toolingOptions = toolingOptions,
        )

        return try {
            when (apiProtocol) {
                ProviderApiProtocol.OPENAI_COMPATIBLE -> sendOpenAiCompatibleMessage(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    activeProvider = activeProvider,
                    gatewayTooling = gatewayTooling,
                )

                ProviderApiProtocol.ANTHROPIC -> sendAnthropicMessage(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    gatewayTooling = gatewayTooling,
                )
            }
        } catch (exception: Exception) {
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        }
    }

    override fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptMode: PromptMode,
        toolingOptions: GatewayToolingOptions,
    ): Flow<ChatStreamEvent> = flow {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)
        val apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE

        val requestMessages = toRequestMessages(messages, systemPrompt, promptMode)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }
        val gatewayTooling = toolAvailabilityResolver.resolve(
            settings = settings,
            activeProvider = activeProvider,
            selectedModel = selectedModel,
                        promptMode = promptMode,
                        toolingOptions = toolingOptions,
                    )

        val rawEvents = when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    streamOpenAiMessage(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        thinkingRequestConfig = thinkingRequestConfig,
                        promptMode = promptMode,
                        activeProvider = activeProvider,
                    )
                } else {
                    streamOpenAiMessageWithTools(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        thinkingRequestConfig = thinkingRequestConfig,
                        activeProvider = activeProvider,
                        gatewayTooling = gatewayTooling,
                        promptMode = promptMode,
                    )
                }
            }

            ProviderApiProtocol.ANTHROPIC -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    streamAnthropicMessage(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        thinkingRequestConfig = thinkingRequestConfig,
                        promptMode = promptMode,
                    )
                } else {
                    streamAnthropicMessageWithTools(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        thinkingRequestConfig = thinkingRequestConfig,
                        gatewayTooling = gatewayTooling,
                        promptMode = promptMode,
                    )
                }
            }
        }

        normalizeReasoningStepEvents(rawEvents).collect { emit(it) }
    }.flowOn(ioDispatcher)

    override fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
    ): ParsedAssistantSpecialOutput {
        return GatewaySpecialPlaySupport.parseAssistantSpecialOutput(
            content = content,
            existingParts = existingParts,
        )
    }

    private fun assistantReplyFromOpenAiMessage(
        assistantMessage: com.example.myapplication.model.AssistantMessageDto?,
        citations: List<MessageCitation> = emptyList(),
    ): AssistantReply {
        val extractedOutput = GatewayAssistantOutputParser.extractAssistantOutput(assistantMessage)
        return ensureAssistantReplyHasContent(
            AssistantReply(
                content = extractedOutput.content,
                reasoningContent = extractedOutput.reasoning,
                parts = extractedOutput.parts,
                citations = citations,
            ),
        )
    }

    private fun ensureAssistantReplyHasContent(
        reply: AssistantReply,
    ): AssistantReply {
        val normalizedReply = reply.copy(
            reasoningSteps = normalizeChatReasoningSteps(
                reply.reasoningSteps.ifEmpty {
                    legacyReasoningStepsFromContent(
                        reasoningContent = reply.reasoningContent,
                        createdAt = System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(),
                        idPrefix = "assistant-reply",
                    )
                },
            ),
        ).let { normalized ->
            normalized.copy(
                reasoningContent = normalized.reasoningContent.ifBlank {
                    reasoningStepsToContent(normalized.reasoningSteps)
                },
            )
        }
        if (normalizedReply.content.isBlank() && normalizedReply.parts.isEmpty()) {
            throw IllegalStateException("模型未返回有效内容")
        }
        return normalizedReply
    }

    private fun emitAssistantReply(
        reply: AssistantReply?,
    ): Flow<ChatStreamEvent> = flow {
        val resolvedReply = reply ?: throw IllegalStateException("模型未返回有效内容")
        resolvedReply.reasoningSteps.forEach { step ->
            emit(
                ChatStreamEvent.ReasoningStepStarted(
                    stepId = step.id,
                    createdAt = step.createdAt,
                ),
            )
            if (step.text.isNotBlank()) {
                emit(
                    ChatStreamEvent.ReasoningStepDelta(
                        stepId = step.id,
                        value = step.text,
                    ),
                )
            }
            emit(
                ChatStreamEvent.ReasoningStepCompleted(
                    stepId = step.id,
                    finishedAt = step.finishedAt ?: System.currentTimeMillis(),
                ),
            )
        }
        if (resolvedReply.content.isNotBlank()) {
            emit(ChatStreamEvent.ContentDelta(resolvedReply.content))
        }
        resolvedReply.parts
            .filter { it.type == com.example.myapplication.model.ChatMessagePartType.IMAGE }
            .forEach { emit(ChatStreamEvent.ImageDelta(it)) }
        if (resolvedReply.citations.isNotEmpty()) {
            emit(ChatStreamEvent.Citations(resolvedReply.citations))
        }
        emit(ChatStreamEvent.Completed)
    }

    private fun normalizeReasoningStepEvents(
        rawEvents: Flow<ChatStreamEvent>,
    ): Flow<ChatStreamEvent> = flow {
        var activeReasoningStepId: String? = null
        var reasoningStepIndex = 0

        suspend fun closeActiveReasoningStep() {
            val stepId = activeReasoningStepId ?: return
            emit(
                ChatStreamEvent.ReasoningStepCompleted(
                    stepId = stepId,
                    finishedAt = System.currentTimeMillis(),
                ),
            )
            activeReasoningStepId = null
        }

        rawEvents.collect { event ->
            when (event) {
                is ChatStreamEvent.ReasoningDelta -> {
                    val stepId = activeReasoningStepId ?: "reasoning-step-${reasoningStepIndex++}-${System.currentTimeMillis()}".also { generatedId ->
                        activeReasoningStepId = generatedId
                        emit(
                            ChatStreamEvent.ReasoningStepStarted(
                                stepId = generatedId,
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }
                    emit(
                        ChatStreamEvent.ReasoningStepDelta(
                            stepId = stepId,
                            value = event.value,
                        ),
                    )
                }

                is ChatStreamEvent.ContentDelta -> {
                    closeActiveReasoningStep()
                    emit(event)
                }

                is ChatStreamEvent.ImageDelta -> {
                    closeActiveReasoningStep()
                    emit(event)
                }

                is ChatStreamEvent.ReasoningStepStarted -> {
                    closeActiveReasoningStep()
                    activeReasoningStepId = event.stepId
                    emit(event)
                }

                is ChatStreamEvent.ReasoningStepDelta -> {
                    activeReasoningStepId = event.stepId
                    emit(event)
                }

                is ChatStreamEvent.ReasoningStepCompleted -> {
                    if (activeReasoningStepId == event.stepId) {
                        activeReasoningStepId = null
                    }
                    emit(event)
                }

                is ChatStreamEvent.Citations -> emit(event)

                ChatStreamEvent.Completed -> {
                    closeActiveReasoningStep()
                    emit(event)
                }
            }
        }
    }

    private suspend fun sendOpenAiCompatibleMessage(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        activeProvider: ProviderSettings?,
        gatewayTooling: ResolvedGatewayTooling,
    ): AssistantReply {
        return when (activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    val request = ChatCompletionRequest(
                        model = selectedModel,
                        messages = requestMessages,
                        reasoningEffort = thinkingRequestConfig.reasoningEffort,
                        thinking = thinkingRequestConfig.thinking,
                    )
                    val response = apiServiceProvider(
                        baseUrl,
                        apiKey,
                    ).createChatCompletionAt(
                        buildOpenAiTextUrl(baseUrl, activeProvider),
                        request,
                    )
                    if (!response.isSuccessful) {
                        throw GatewayNetworkSupport.retrofitFailure("聊天请求失败", response)
                    }
                    val assistantMessage = response.body()
                        ?.choices
                        ?.firstOrNull()
                        ?.message
                    assistantReplyFromOpenAiMessage(assistantMessage)
                } else {
                    toolEngine.runOpenAiChatCompletionToolLoop(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        activeProvider = activeProvider,
                        thinkingRequestConfig = thinkingRequestConfig,
                        enabledToolNames = gatewayTooling.enabledToolNames,
                    toolContext = gatewayTooling.toolContext,
                    promptMode = PromptMode.CHAT,
                ).finalReply ?: throw IllegalStateException("模型未返回有效内容")
                }
            }

            OpenAiTextApiMode.RESPONSES -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    val request = ChatCompletionRequest(
                        model = selectedModel,
                        messages = requestMessages,
                        reasoningEffort = thinkingRequestConfig.reasoningEffort,
                        thinking = thinkingRequestConfig.thinking,
                    )
                    val response = apiServiceProvider(
                        baseUrl,
                        apiKey,
                    ).createResponseAt(
                        buildOpenAiTextUrl(baseUrl, activeProvider),
                        ResponseApiSupport.buildRequest(request),
                    )
                    if (!response.isSuccessful) {
                        throw PromptExtrasResponseSupport.buildHttpFailure(
                            operation = "聊天请求失败",
                            code = response.code(),
                            errorDetail = response.errorBody()?.string().orEmpty(),
                            headers = response.headers(),
                        )
                    }
                    val body = response.body() ?: throw IllegalStateException("响应体为空")
                    val parsed = ResponseApiSupport.parseResponse(body)
                    ensureAssistantReplyHasContent(
                        AssistantReply(
                            content = parsed.content,
                            reasoningContent = parsed.reasoning,
                        ),
                    )
                } else {
                    toolEngine.runResponsesToolLoop(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        activeProvider = activeProvider,
                        thinkingRequestConfig = thinkingRequestConfig,
                        enabledToolNames = gatewayTooling.enabledToolNames,
                    toolContext = gatewayTooling.toolContext,
                    promptMode = PromptMode.CHAT,
                ).finalReply ?: throw IllegalStateException("模型未返回有效内容")
                }
            }
        }
    }

    private suspend fun sendAnthropicMessage(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        gatewayTooling: ResolvedGatewayTooling,
    ): AssistantReply {
        if (gatewayTooling.enabledToolNames.isEmpty()) {
            val response = anthropicApiProvider(baseUrl, apiKey).createMessage(
                AnthropicProtocolSupport.buildMessageRequest(
                    ChatCompletionRequest(
                        model = selectedModel,
                        messages = requestMessages,
                        temperature = null,
                        topP = null,
                        thinking = thinkingRequestConfig.thinking,
                    ),
                ),
            )
            if (!response.isSuccessful) {
                throw PromptExtrasResponseSupport.buildHttpFailure(
                    operation = "聊天请求失败",
                    code = response.code(),
                    errorDetail = response.errorBody()?.string().orEmpty(),
                    headers = response.headers(),
                )
            }
            val body = response.body() ?: throw IllegalStateException("响应体为空")
            return ensureAssistantReplyHasContent(AnthropicProtocolSupport.toAssistantReply(body))
        }

        return toolEngine.runAnthropicToolLoop(
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
            requestMessages = requestMessages,
            thinkingRequestConfig = thinkingRequestConfig,
            enabledToolNames = gatewayTooling.enabledToolNames,
            toolContext = gatewayTooling.toolContext,
            promptMode = PromptMode.CHAT,
        ).finalReply ?: throw IllegalStateException("模型未返回有效内容")
    }

    private fun streamOpenAiMessageWithTools(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        activeProvider: ProviderSettings?,
        gatewayTooling: ResolvedGatewayTooling,
        promptMode: PromptMode,
    ): Flow<ChatStreamEvent> = flow {
        when (activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> {
                val toolLoopOutcome = toolEngine.runOpenAiChatCompletionToolLoop(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    activeProvider = activeProvider,
                    thinkingRequestConfig = thinkingRequestConfig,
                    enabledToolNames = gatewayTooling.enabledToolNames,
                    toolContext = gatewayTooling.toolContext,
                    promptMode = promptMode,
                )
                if (toolLoopOutcome.toolRoundCount == 0) {
                    emitAssistantReply(toolLoopOutcome.finalReply).collect { emit(it) }
                } else {
                    val continuation = toolLoopOutcome.continuation as? ToolContinuation.Transcript
                        ?: throw IllegalStateException("工具续写上下文缺失")
                    streamOpenAiMessage(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        selectedModel = selectedModel,
                        requestMessages = continuation.messages,
                        thinkingRequestConfig = thinkingRequestConfig,
                        promptMode = promptMode,
                        activeProvider = activeProvider?.copy(openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS),
                    ).collect { event ->
                        if (event is ChatStreamEvent.Completed && toolLoopOutcome.citations.isNotEmpty()) {
                            emit(ChatStreamEvent.Citations(toolLoopOutcome.citations))
                        }
                        emit(event)
                    }
                }
            }

            OpenAiTextApiMode.RESPONSES -> {
                val toolLoopOutcome = toolEngine.runResponsesToolLoop(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    activeProvider = activeProvider,
                    thinkingRequestConfig = thinkingRequestConfig,
                    enabledToolNames = gatewayTooling.enabledToolNames,
                    toolContext = gatewayTooling.toolContext,
                    promptMode = promptMode,
                )
                if (toolLoopOutcome.toolRoundCount == 0) {
                    emitAssistantReply(toolLoopOutcome.finalReply).collect { emit(it) }
                } else {
                    val continuation = toolLoopOutcome.continuation as? ToolContinuation.Responses
                        ?: throw IllegalStateException("工具续写上下文缺失")
                    streamOpenAiResponseRequest(
                        baseUrl = baseUrl,
                        apiKey = apiKey,
                        activeProvider = activeProvider,
                        request = ResponseApiRequest(
                            model = selectedModel,
                            input = continuation.input,
                            stream = true,
                            temperature = continuation.temperature,
                            topP = continuation.topP,
                            reasoning = thinkingRequestConfig.reasoningEffort?.let { effort ->
                                com.example.myapplication.model.ResponseApiReasoningDto(
                                    effort = effort,
                                    summary = "auto",
                                )
                            },
                            previousResponseId = continuation.previousResponseId,
                        ),
                    ).collect { event ->
                        if (event is ChatStreamEvent.Completed && toolLoopOutcome.citations.isNotEmpty()) {
                            emit(ChatStreamEvent.Citations(toolLoopOutcome.citations))
                        }
                        emit(event)
                    }
                }
            }
        }
    }

    private fun streamAnthropicMessageWithTools(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        gatewayTooling: ResolvedGatewayTooling,
        promptMode: PromptMode,
    ): Flow<ChatStreamEvent> = flow {
        val toolLoopOutcome = toolEngine.runAnthropicToolLoop(
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
            requestMessages = requestMessages,
            thinkingRequestConfig = thinkingRequestConfig,
            enabledToolNames = gatewayTooling.enabledToolNames,
            toolContext = gatewayTooling.toolContext,
            promptMode = promptMode,
        )
        if (toolLoopOutcome.toolRoundCount == 0) {
            emitAssistantReply(toolLoopOutcome.finalReply).collect { emit(it) }
        } else {
            val continuation = toolLoopOutcome.continuation as? ToolContinuation.Transcript
                ?: throw IllegalStateException("工具续写上下文缺失")
            streamAnthropicMessage(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = continuation.messages,
                thinkingRequestConfig = thinkingRequestConfig,
                promptMode = promptMode,
            ).collect { event ->
                if (event is ChatStreamEvent.Completed && toolLoopOutcome.citations.isNotEmpty()) {
                    emit(ChatStreamEvent.Citations(toolLoopOutcome.citations))
                }
                emit(event)
            }
        }
    }

    private fun streamOpenAiResponseRequest(
        baseUrl: String,
        apiKey: String,
        activeProvider: ProviderSettings?,
        request: ResponseApiRequest,
    ): Flow<ChatStreamEvent> = flow {
        val client = streamClientProvider(baseUrl, apiKey)
        val call = client.newCall(
            buildStreamingRequest(
                fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider?.copy(openAiTextApiMode = OpenAiTextApiMode.RESPONSES)),
                requestBody = gson.toJson(request),
            ),
        )
        var response: okhttp3.Response? = null
        val coroutineContext = currentCoroutineContext()
        val coroutineJob = coroutineContext[Job]
        val cancelHandle = coroutineJob?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        try {
            response = call.execute()
            if (!response.isSuccessful) {
                throw GatewayNetworkSupport.okhttpFailure("聊天请求失败", response)
            }
            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                when (val event = ResponseApiSupport.parseStreamEvent(data)) {
                    is ResponseApiStreamEvent.ContentDelta -> emit(ChatStreamEvent.ContentDelta(event.value))
                    is ResponseApiStreamEvent.ReasoningDelta -> emit(ChatStreamEvent.ReasoningDelta(event.value))
                    ResponseApiStreamEvent.Completed -> break
                    null -> Unit
                }
            }
            emit(ChatStreamEvent.Completed)
        } catch (exception: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("聊天请求已取消").apply { initCause(exception) }
            }
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }

    private fun streamOpenAiMessage(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        promptMode: PromptMode,
        activeProvider: ProviderSettings? = null,
    ): Flow<ChatStreamEvent> = flow {
        val apiMode = activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        val client = runCatching {
            streamClientProvider(baseUrl, apiKey)
        }.getOrElse {
            emitAssistantReply(
                sendOpenAiMessageWithoutStreaming(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    activeProvider = activeProvider,
                ),
            ).collect { emit(it) }
            return@flow
        }
        var requestBody = buildRequestWithRoleplaySampling(
            model = selectedModel,
            messages = requestMessages,
            baseUrl = baseUrl,
            apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
            stream = true,
            reasoningEffort = thinkingRequestConfig.reasoningEffort,
            thinking = thinkingRequestConfig.thinking,
            promptMode = promptMode,
        )
        var call = client.newCall(
            buildStreamingRequest(
                fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider),
                requestBody = if (apiMode == OpenAiTextApiMode.RESPONSES) {
                    gson.toJson(ResponseApiSupport.buildRequest(requestBody))
                } else {
                    gson.toJson(requestBody)
                },
            ),
        )
        var response: okhttp3.Response? = null
        val thinkTagParser = ThinkTagStreamParser()
        val coroutineContext = currentCoroutineContext()
        val coroutineJob = coroutineContext[Job]
        val cancelHandle = coroutineJob?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }

        try {
            response = call.execute()
            val streamErrorDetail = if (!response.isSuccessful) {
                response.peekBody(64L * 1024L).string()
            } else {
                ""
            }
            if (apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS &&
                response.code == 400 &&
                shouldRetryWithoutRoleplaySampling(requestBody, streamErrorDetail)
            ) {
                response.close()
                markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
                requestBody = requestBody.copy(
                    temperature = null,
                    topP = null,
                )
                call = client.newCall(
                    buildStreamingRequest(
                        fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider),
                        requestBody = gson.toJson(requestBody),
                    ),
                )
                response = call.execute()
            }
            if (!response.isSuccessful) {
                throw GatewayNetworkSupport.okhttpFailure("聊天请求失败", response)
            }

            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")

            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                if (apiMode == OpenAiTextApiMode.RESPONSES) {
                    when (val event = ResponseApiSupport.parseStreamEvent(data)) {
                        is ResponseApiStreamEvent.ContentDelta -> emit(ChatStreamEvent.ContentDelta(event.value))
                        is ResponseApiStreamEvent.ReasoningDelta -> emit(ChatStreamEvent.ReasoningDelta(event.value))
                        ResponseApiStreamEvent.Completed -> break
                        null -> Unit
                    }
                    continue
                }

                val chunk = try {
                    gson.fromJson(data, ChatCompletionChunk::class.java)
                } catch (_: Exception) {
                    null
                } ?: continue

                val delta = chunk.choices.firstOrNull()?.delta
                val reasoningDelta = GatewayAssistantOutputParser.extractReasoning(delta)
                if (reasoningDelta.isNotBlank()) {
                    emit(ChatStreamEvent.ReasoningDelta(reasoningDelta))
                }
                val contentDelta = delta?.content.orEmpty()
                if (contentDelta.isNotBlank()) {
                    if (thinkTagParser.shouldConsume(contentDelta)) {
                        val parsedContent = thinkTagParser.consume(contentDelta)
                        if (parsedContent.reasoning.isNotBlank()) {
                            emit(ChatStreamEvent.ReasoningDelta(parsedContent.reasoning))
                        }
                        if (parsedContent.content.isNotBlank()) {
                            emit(ChatStreamEvent.ContentDelta(parsedContent.content))
                        }
                    } else {
                        emit(ChatStreamEvent.ContentDelta(contentDelta))
                    }
                }
                GatewayAssistantOutputParser.extractAssistantImageParts(delta?.images).forEach { imagePart ->
                    emit(ChatStreamEvent.ImageDelta(imagePart))
                }
            }
            if (apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS && thinkTagParser.hasPending()) {
                val trailingOutput = thinkTagParser.flush()
                if (trailingOutput.reasoning.isNotBlank()) {
                    emit(ChatStreamEvent.ReasoningDelta(trailingOutput.reasoning))
                }
                if (trailingOutput.content.isNotBlank()) {
                    emit(ChatStreamEvent.ContentDelta(trailingOutput.content))
                }
            }
            emit(ChatStreamEvent.Completed)
        } catch (exception: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("聊天请求已取消").apply { initCause(exception) }
            }
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }

    private suspend fun sendOpenAiMessageWithoutStreaming(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        activeProvider: ProviderSettings?,
    ): AssistantReply {
        return when (activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> {
                val request = ChatCompletionRequest(
                    model = selectedModel,
                    messages = requestMessages,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    thinking = thinkingRequestConfig.thinking,
                )
                val response = apiServiceProvider(baseUrl, apiKey).createChatCompletionAt(
                    buildOpenAiTextUrl(baseUrl, activeProvider),
                    request,
                )
                if (!response.isSuccessful) {
                    throw GatewayNetworkSupport.retrofitFailure("聊天请求失败", response)
                }
                assistantReplyFromOpenAiMessage(
                    response.body()?.choices?.firstOrNull()?.message,
                )
            }

            OpenAiTextApiMode.RESPONSES -> {
                val request = ChatCompletionRequest(
                    model = selectedModel,
                    messages = requestMessages,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    thinking = thinkingRequestConfig.thinking,
                )
                val response = apiServiceProvider(baseUrl, apiKey).createResponseAt(
                    buildOpenAiTextUrl(baseUrl, activeProvider),
                    ResponseApiSupport.buildRequest(request),
                )
                if (!response.isSuccessful) {
                    throw PromptExtrasResponseSupport.buildHttpFailure(
                        operation = "聊天请求失败",
                        code = response.code(),
                        errorDetail = response.errorBody()?.string().orEmpty(),
                        headers = response.headers(),
                    )
                }
                val body = response.body() ?: throw IllegalStateException("响应体为空")
                val parsed = ResponseApiSupport.parseResponse(body)
                ensureAssistantReplyHasContent(
                    AssistantReply(
                        content = parsed.content,
                        reasoningContent = parsed.reasoning,
                    ),
                )
            }
        }
    }

    private fun streamAnthropicMessage(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        promptMode: PromptMode,
    ): Flow<ChatStreamEvent> = flow {
        val requestBody = buildRequestWithRoleplaySampling(
            model = selectedModel,
            messages = requestMessages,
            baseUrl = baseUrl,
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
            stream = true,
            thinking = thinkingRequestConfig.thinking,
            promptMode = promptMode,
        )
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.ANTHROPIC)
        val httpRequest = Request.Builder()
            .url("${normalizedBaseUrl}messages")
            .post(gson.toJson(AnthropicProtocolSupport.buildMessageRequest(requestBody)).toRequestBody("application/json".toMediaType()))
            .build()
        val client = anthropicStreamClientProvider(baseUrl, apiKey)
        val call = client.newCall(httpRequest)
        var response: okhttp3.Response? = null
        val coroutineContext = currentCoroutineContext()
        val coroutineJob = coroutineContext[Job]
        val cancelHandle = coroutineJob?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                call.cancel()
            }
        }
        try {
            response = call.execute()
            if (!response.isSuccessful) {
                throw GatewayNetworkSupport.okhttpFailure("聊天请求失败", response)
            }
            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val delta = AnthropicProtocolSupport.parseStreamData(line.removePrefix("data: ").trim())
                if (!delta.errorMessage.isNullOrBlank()) {
                    throw IllegalStateException(delta.errorMessage)
                }
                if (delta.reasoning.isNotBlank()) {
                    emit(ChatStreamEvent.ReasoningDelta(delta.reasoning))
                }
                if (delta.content.isNotBlank()) {
                    emit(ChatStreamEvent.ContentDelta(delta.content))
                }
                if (delta.stop) {
                    break
                }
            }
            emit(ChatStreamEvent.Completed)
        } catch (exception: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("聊天请求已取消").apply { initCause(exception) }
            }
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }

    private fun buildStreamingRequest(
        fullUrl: String,
        requestBody: String,
    ): Request = GatewayRequestSupport.buildStreamingRequest(fullUrl, requestBody)

    private fun buildOpenAiTextUrl(
        baseUrl: String,
        provider: ProviderSettings?,
    ): String = GatewayRequestSupport.buildOpenAiTextUrl(baseUrl, provider, apiServiceFactory)

    private fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        stream: Boolean = false,
        reasoningEffort: String? = null,
        thinking: com.example.myapplication.model.ThinkingConfigDto? = null,
        promptMode: PromptMode = PromptMode.ROLEPLAY,
        tools: List<com.example.myapplication.model.ChatToolDto> = emptyList(),
        toolChoice: String? = null,
    ): ChatCompletionRequest = GatewayRequestSupport.buildRequestWithRoleplaySampling(
        model = model,
        messages = messages,
        baseUrl = baseUrl,
        apiProtocol = apiProtocol,
        apiServiceFactory = apiServiceFactory,
        disabledBaseUrls = roleplaySamplingDisabledBaseUrls,
        stream = stream,
        reasoningEffort = reasoningEffort,
        thinking = thinking,
        promptMode = promptMode,
        tools = tools,
        toolChoice = toolChoice,
    )

    private fun shouldRetryWithoutRoleplaySampling(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean = GatewayRequestSupport.shouldRetryWithoutRoleplaySampling(request, errorDetail)

    private fun markRoleplaySamplingUnsupported(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ) {
        roleplaySamplingDisabledBaseUrls += apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
    }

    private suspend fun toRequestMessages(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
    ): List<ChatMessageDto> {
        return GatewayRequestMessageBuilder.build(
            messages = messages,
            systemPrompt = systemPrompt,
            promptMode = promptMode,
            imagePayloadResolver = imagePayloadResolver,
            filePromptResolver = filePromptResolver,
        )
    }

}
