package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.model.ChatCompletionChunk
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.buildThinkingRequestConfig
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

private const val ROLEPLAY_TEMPERATURE = 0.9f
private const val ROLEPLAY_TOP_P = 0.92f

private val UnsupportedSamplingMessageHints = listOf(
    "temperature",
    "top_p",
    "top p",
    "unknown parameter",
    "unknown field",
    "unrecognized field",
    "not permitted",
    "not allowed",
)

private data class GatewayRoleplaySamplingConfig(
    val temperature: Float,
    val topP: Float,
)

interface AiGateway {
    suspend fun generateImage(prompt: String): List<ImageGenerationResult>

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
    ): AssistantReply

    fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiGateway {
    private val gson = Gson()
    private val roleplaySamplingDisabledBaseUrls = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun generateImage(prompt: String): List<ImageGenerationResult> {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel
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

        return try {
            when (apiProtocol) {
                ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                    val request = ChatCompletionRequest(
                        model = selectedModel,
                        messages = requestMessages,
                        reasoningEffort = thinkingRequestConfig.reasoningEffort,
                        thinking = thinkingRequestConfig.thinking,
                    )
                    when (activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
                        OpenAiTextApiMode.CHAT_COMPLETIONS -> {
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
                            val extractedOutput = GatewayAssistantOutputParser.extractAssistantOutput(assistantMessage)
                            if (extractedOutput.content.isBlank() && extractedOutput.parts.isEmpty()) {
                                throw IllegalStateException("模型未返回有效内容")
                            }
                            AssistantReply(
                                content = extractedOutput.content,
                                reasoningContent = extractedOutput.reasoning,
                                parts = extractedOutput.parts,
                            )
                        }
                        OpenAiTextApiMode.RESPONSES -> {
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
                            AssistantReply(
                                content = parsed.content,
                                reasoningContent = parsed.reasoning,
                            )
                        }
                    }
                }
                ProviderApiProtocol.ANTHROPIC -> {
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
                    val reply = AnthropicProtocolSupport.toAssistantReply(body)
                    if (reply.content.isBlank() && reply.parts.isEmpty()) {
                        throw IllegalStateException("模型未返回有效内容")
                    }
                    reply
                }
            }
        } catch (exception: Exception) {
            throw GatewayNetworkSupport.toReadableNetworkException(exception)
        }
    }

    override fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptMode: PromptMode,
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

        when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                streamOpenAiMessage(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    promptMode = promptMode,
                    activeProvider = activeProvider,
                ).collect { emit(it) }
            }
            ProviderApiProtocol.ANTHROPIC -> {
                streamAnthropicMessage(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    promptMode = promptMode,
                ).collect { emit(it) }
            }
        }
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

    private fun streamOpenAiMessage(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        promptMode: PromptMode,
        activeProvider: ProviderSettings? = null,
    ): Flow<ChatStreamEvent> = flow {
        val client = streamClientProvider(baseUrl, apiKey)
        val apiMode = activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
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
    ): Request {
        return Request.Builder()
            .url(fullUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    private fun buildOpenAiTextUrl(
        baseUrl: String,
        provider: ProviderSettings?,
    ): String {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val path = when (provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> provider?.resolvedChatCompletionsPath() ?: DEFAULT_CHAT_COMPLETIONS_PATH
            OpenAiTextApiMode.RESPONSES -> "/responses"
        }
        return normalizedBaseUrl.removeSuffix("/") + path
    }

    private fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        stream: Boolean = false,
        reasoningEffort: String? = null,
        thinking: com.example.myapplication.model.ThinkingConfigDto? = null,
        promptMode: PromptMode = PromptMode.ROLEPLAY,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(baseUrl, apiProtocol, promptMode)
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = stream,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
            reasoningEffort = reasoningEffort,
            thinking = thinking,
        )
    }

    private fun resolveRoleplaySampling(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        promptMode: PromptMode,
    ): GatewayRoleplaySamplingConfig? {
        if (promptMode != PromptMode.ROLEPLAY) {
            return null
        }
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
        if (roleplaySamplingDisabledBaseUrls.contains(normalizedBaseUrl)) {
            return null
        }
        return GatewayRoleplaySamplingConfig(
            temperature = ROLEPLAY_TEMPERATURE,
            topP = ROLEPLAY_TOP_P,
        )
    }

    private fun shouldRetryWithoutRoleplaySampling(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean {
        if (request.temperature == null && request.topP == null) {
            return false
        }
        val normalizedError = errorDetail.trim().lowercase()
        if (normalizedError.isBlank()) {
            return false
        }
        return UnsupportedSamplingMessageHints.any { hint ->
            normalizedError.contains(hint)
        }
    }

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
