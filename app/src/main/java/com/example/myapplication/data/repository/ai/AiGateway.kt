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
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.ChatToolDto
import com.example.myapplication.model.ImageEditInputImageDto
import com.example.myapplication.model.ImageEditRequest
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.model.buildThinkingRequestConfig
import com.example.myapplication.model.legacyReasoningStepsFromContent
import com.example.myapplication.model.normalizeKnownModelId
import com.example.myapplication.model.normalizeChatReasoningSteps
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.decodeBase64
import java.io.IOException
import java.util.Collections

// 采样配置和请求构建已提取到 GatewayRequestSupport

interface AiGateway {
    suspend fun generateImage(
        prompt: String,
        modelId: String = "",
    ): List<ImageGenerationResult>

    suspend fun generateImageWithProvider(
        prompt: String,
        provider: ProviderSettings,
        modelId: String = "",
    ): List<ImageGenerationResult> = generateImage(
        prompt = prompt,
        modelId = modelId,
    )

    suspend fun editImage(
        prompt: String,
        images: List<MessageAttachment>,
        modelId: String = "",
    ): List<ImageGenerationResult> = error("当前环境不支持图片改图")

    suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
        toolingOptions: GatewayToolingOptions = GatewayToolingOptions(),
    ): AssistantReply

    fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
        toolingOptions: GatewayToolingOptions = GatewayToolingOptions(),
    ): Flow<ChatStreamEvent>

    fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
        statusCardsEnabled: Boolean = true,
        hideStatusBlocksInBubble: Boolean = true,
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
    private val imageApiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        apiServiceFactory.createLongRunning(
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
    private val requestClientProvider: (String, String) -> OkHttpClient = { baseUrl, apiKey ->
        apiServiceFactory.createRequestClient(
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
    private companion object {
        val DEFAULT_BINARY_MEDIA_TYPE = "application/octet-stream".toMediaType()
        val IMAGE_EDIT_MULTIPART_FALLBACK_CODES = setOf(400, 415, 422, 500)
    }

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
                enableThinking = spec.enableThinking,
                thinkingBudget = spec.thinkingBudget,
                thinking = spec.thinking,
                promptMode = spec.promptMode,
                tools = spec.tools,
                toolChoice = spec.toolChoice,
                promptEnvelope = spec.promptEnvelope,
                googleSearchRetrieval = spec.googleSearchRetrieval,
            )
        },
    )
    private val roleplaySamplingDisabledBaseUrls = Collections.synchronizedSet(mutableSetOf<String>())

    private data class ModelBuiltInSearchRequestOptions(
        val tools: List<ChatToolDto> = emptyList(),
        val googleSearchRetrieval: Map<String, Any>? = null,
    )

    private data class GeminiNativeOutput(
        val content: String = "",
        val reasoningContent: String = "",
    )

    private fun modelBuiltInSearchRequestOptions(
        enabled: Boolean,
    ): ModelBuiltInSearchRequestOptions {
        if (!enabled) {
            return ModelBuiltInSearchRequestOptions()
        }
        return ModelBuiltInSearchRequestOptions(
            tools = listOf(
                ChatToolDto(
                    type = "google_search",
                    googleSearch = emptyMap(),
                ),
            ),
        )
    }

    override suspend fun generateImage(
        prompt: String,
        modelId: String,
    ): List<ImageGenerationResult> {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = normalizeKnownModelId(
            modelId.trim().ifBlank {
                activeProvider?.selectedModel ?: settings.selectedModel
            },
        )
        if (activeProvider?.resolvedApiProtocol() == ProviderApiProtocol.ANTHROPIC) {
            throw IllegalStateException("Anthropic /messages 协议当前不支持图片生成")
        }
        return requestImageGeneration(
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
            prompt = prompt,
        )
    }

    override suspend fun generateImageWithProvider(
        prompt: String,
        provider: ProviderSettings,
        modelId: String,
    ): List<ImageGenerationResult> {
        require(provider.hasBaseCredentials()) { "请先完成生图提供商的 Base URL 和 API Key" }
        if (provider.resolvedApiProtocol() == ProviderApiProtocol.ANTHROPIC) {
            throw IllegalStateException("Anthropic /messages 协议当前不支持图片生成")
        }
        val selectedModel = normalizeKnownModelId(
            modelId.trim().ifBlank { provider.selectedModel.trim() },
        )
        require(selectedModel.isNotBlank()) { "请先选择生图模型" }
        return requestImageGeneration(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = selectedModel,
            prompt = prompt,
        )
    }

    private suspend fun requestImageGeneration(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        prompt: String,
    ): List<ImageGenerationResult> {
        val response = imageApiServiceProvider(baseUrl, apiKey).generateImage(
            ImageGenerationRequest(
                model = selectedModel,
                prompt = prompt,
                n = 1,
                responseFormat = imageResponseFormatForModel(selectedModel),
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

    override suspend fun editImage(
        prompt: String,
        images: List<MessageAttachment>,
        modelId: String,
    ): List<ImageGenerationResult> {
        require(images.isNotEmpty()) { "请先选择参考图" }

        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = normalizeKnownModelId(
            modelId.trim().ifBlank {
                activeProvider?.selectedModel ?: settings.selectedModel
            },
        )
        if (activeProvider?.resolvedApiProtocol() == ProviderApiProtocol.ANTHROPIC) {
            throw IllegalStateException("Anthropic /messages 协议当前不支持图片编辑")
        }

        val encodedImages = buildList {
            images.forEach { attachment ->
                add(imagePayloadResolver(attachment))
            }
        }
        val api = imageApiServiceProvider(baseUrl, apiKey)
        val response = api.editImage(
            ImageEditRequest(
                model = selectedModel,
                prompt = prompt,
                images = encodedImages.map { imageUrl ->
                    ImageEditInputImageDto(imageUrl = imageUrl)
                },
                n = 1,
                responseFormat = imageResponseFormatForModel(selectedModel),
            ),
        )

        val finalResponse = if (!response.isSuccessful &&
            shouldFallbackToMultipartImageEdit(response.code())
        ) {
            buildMultipartImageEditParts(images, encodedImages)?.let { multipartImages ->
                executeMultipartImageEdit(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    model = selectedModel,
                    prompt = prompt,
                    images = multipartImages,
                )
            } ?: response
        } else {
            response
        }

        if (!finalResponse.isSuccessful) {
            throw IllegalStateException("图片编辑失败：${finalResponse.code()}")
        }

        val data = finalResponse.body()?.data.orEmpty()
        if (data.isEmpty()) {
            throw IllegalStateException("图片编辑接口未返回数据")
        }

        return data.map { item ->
            ImageGenerationResult(
                b64Data = item.b64Json.orEmpty(),
                url = item.url.orEmpty(),
                revisedPrompt = item.revisedPrompt.orEmpty(),
            )
        }
    }

    private fun shouldFallbackToMultipartImageEdit(code: Int): Boolean {
        return code in IMAGE_EDIT_MULTIPART_FALLBACK_CODES
    }

    private fun buildMultipartImageEditParts(
        attachments: List<MessageAttachment>,
        encodedImages: List<String>,
    ): List<MultipartBody.Part>? {
        if (attachments.size != encodedImages.size) {
            return null
        }
        return buildList {
            attachments.zip(encodedImages).forEachIndexed { index, (attachment, imageUrl) ->
                val parsedImage = parseDataUrlImage(imageUrl) ?: return null
                val fileName = attachment.fileName.takeUnless { it.isNullOrBlank() }
                    ?: "image-${index + 1}${parsedImage.defaultExtension}"
                add(
                    MultipartBody.Part.createFormData(
                        "image",
                        fileName,
                        parsedImage.bytes.toRequestBody(parsedImage.mediaType),
                    ),
                )
            }
        }
    }

    private fun executeMultipartImageEdit(
        baseUrl: String,
        apiKey: String,
        model: String,
        prompt: String,
        images: List<MultipartBody.Part>,
    ): retrofit2.Response<com.example.myapplication.model.ImageGenerationResponse> {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("prompt", prompt)
            .addFormDataPart("n", "1")
            .apply {
                imageResponseFormatForModel(model)?.let { responseFormat ->
                    addFormDataPart("response_format", responseFormat)
                }
            }
            .apply {
                images.forEach(::addPart)
            }
            .build()
        val request = Request.Builder()
            .url(apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE) + "images/edits")
            .post(requestBody)
            .build()
        requestClientProvider(baseUrl, apiKey).newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            return if (response.isSuccessful) {
                retrofit2.Response.success(
                    gson.fromJson(
                        responseBody,
                        com.example.myapplication.model.ImageGenerationResponse::class.java,
                    ),
                )
            } else {
                retrofit2.Response.error(
                    response.code,
                    responseBody.toResponseBody(response.body?.contentType()),
                )
            }
        }
    }

    private fun parseDataUrlImage(dataUrl: String): MultipartImagePart? {
        val separatorIndex = dataUrl.indexOf(',')
        if (separatorIndex <= 0) {
            return null
        }
        val header = dataUrl.substring(0, separatorIndex)
        if (!header.startsWith("data:", ignoreCase = true) || !header.contains(";base64", ignoreCase = true)) {
            return null
        }
        val mimeType = header.substringAfter("data:", "").substringBefore(';').trim()
        if (mimeType.isBlank()) {
            return null
        }
        val bytes = dataUrl.substring(separatorIndex + 1)
            .decodeBase64()
            ?.toByteArray()
            ?: return null
        val mediaType = mimeType.toMediaTypeOrNull() ?: DEFAULT_BINARY_MEDIA_TYPE
        return MultipartImagePart(
            bytes = bytes,
            mediaType = mediaType,
            defaultExtension = when (mimeType.lowercase()) {
                "image/jpeg" -> ".jpg"
                "image/webp" -> ".webp"
                "image/gif" -> ".gif"
                else -> ".png"
            },
        )
    }

    private data class MultipartImagePart(
        val bytes: ByteArray,
        val mediaType: okhttp3.MediaType,
        val defaultExtension: String,
    )

    private fun imageResponseFormatForModel(modelId: String): String? {
        val normalized = normalizeKnownModelId(modelId).lowercase()
        return if ("gpt-image" in normalized || Regex("""\bgpt(?:[-_.]?\d+(?:\.\d+)*)?[-_.]image\b""").containsMatchIn(normalized)) {
            null
        } else {
            "b64_json"
        }
    }

    override suspend fun sendMessage(
        messages: List<ChatMessage>,
        systemPrompt: String,
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): AssistantReply {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = normalizeKnownModelId(activeProvider?.selectedModel ?: settings.selectedModel)
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)
        val apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE

        val requestMessages = toRequestMessages(messages, systemPrompt, PromptMode.CHAT, promptEnvelope)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }
        val gatewayTooling = toolAvailabilityResolver.resolve(
            settings = settings,
            activeProvider = activeProvider,
            selectedModel = selectedModel,
            promptMode = PromptMode.CHAT,
            toolingOptions = toolingOptions,
        )
        val isModelBuiltInSearch = toolingOptions.searchEnabled &&
            settings.activeSearchSource(activeProvider)?.type == SearchSourceType.MODEL_BUILTIN

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
                    promptEnvelope = promptEnvelope,
                    isModelBuiltInSearch = isModelBuiltInSearch,
                )

                ProviderApiProtocol.ANTHROPIC -> sendAnthropicMessage(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    thinkingRequestConfig = thinkingRequestConfig,
                    gatewayTooling = gatewayTooling,
                    promptEnvelope = promptEnvelope,
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
        promptEnvelope: PromptEnvelope,
        toolingOptions: GatewayToolingOptions,
    ): Flow<ChatStreamEvent> = flow {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = normalizeKnownModelId(activeProvider?.selectedModel ?: settings.selectedModel)
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)
        val apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE

        val requestMessages = toRequestMessages(messages, systemPrompt, promptMode, promptEnvelope)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }
        val gatewayTooling = toolAvailabilityResolver.resolve(
            settings = settings,
            activeProvider = activeProvider,
            selectedModel = selectedModel,
            promptMode = promptMode,
            toolingOptions = toolingOptions,
        )
        val isModelBuiltInSearch = toolingOptions.searchEnabled &&
            settings.activeSearchSource(activeProvider)?.type == SearchSourceType.MODEL_BUILTIN

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
                        promptEnvelope = promptEnvelope,
                        activeProvider = activeProvider,
                        isModelBuiltInSearch = isModelBuiltInSearch,
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
                        promptEnvelope = promptEnvelope,
                        isModelBuiltInSearch = isModelBuiltInSearch,
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
                        promptEnvelope = promptEnvelope,
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
                        promptEnvelope = promptEnvelope,
                    )
                }
            }
        }

        normalizeReasoningStepEvents(rawEvents).collect { emit(it) }
    }.flowOn(ioDispatcher)

    override fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
        statusCardsEnabled: Boolean,
        hideStatusBlocksInBubble: Boolean,
    ): ParsedAssistantSpecialOutput {
        return GatewaySpecialPlaySupport.parseAssistantSpecialOutput(
            content = content,
            existingParts = existingParts,
            statusCardsEnabled = statusCardsEnabled,
            hideStatusBlocksInBubble = hideStatusBlocksInBubble,
        )
    }

    private fun assistantReplyFromOpenAiMessage(
        assistantMessage: com.example.myapplication.model.AssistantMessageDto?,
        citations: List<MessageCitation> = emptyList(),
        finishReason: String? = null,
    ): AssistantReply {
        val extractedOutput = GatewayAssistantOutputParser.extractAssistantOutput(assistantMessage)
        return ensureAssistantReplyHasContent(
            AssistantReply(
                content = extractedOutput.content,
                reasoningContent = extractedOutput.reasoning,
                parts = extractedOutput.parts,
                citations = citations,
            ),
            finishReason = finishReason,
        )
    }

    private fun ensureAssistantReplyHasContent(
        reply: AssistantReply,
        finishReason: String? = null,
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
            throw IllegalStateException(emptyAssistantContentMessage(finishReason))
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
        promptEnvelope: PromptEnvelope,
        isModelBuiltInSearch: Boolean = false,
    ): AssistantReply {
        val modelBuiltInSearchOptions = modelBuiltInSearchRequestOptions(isModelBuiltInSearch)
        return when (activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    var request = buildRequestWithRoleplaySampling(
                        model = selectedModel,
                        messages = requestMessages,
                        baseUrl = baseUrl,
                        apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                        promptMode = PromptMode.CHAT,
                        reasoningEffort = thinkingRequestConfig.reasoningEffort,
                        enableThinking = thinkingRequestConfig.enableThinking,
                        thinkingBudget = thinkingRequestConfig.thinkingBudget,
                        thinking = thinkingRequestConfig.thinking,
                        promptEnvelope = promptEnvelope,
                        tools = modelBuiltInSearchOptions.tools,
                        googleSearchRetrieval = modelBuiltInSearchOptions.googleSearchRetrieval,
                    )
                    val api = apiServiceProvider(
                        baseUrl,
                        apiKey,
                    )
                    var response = api.createChatCompletionAt(
                        buildOpenAiTextUrl(baseUrl, activeProvider),
                        request,
                    )
                    var latestErrorDetail = if (!response.isSuccessful) {
                        response.errorBody()?.string().orEmpty()
                    } else {
                        ""
                    }
                    if (response.code() == 400 && shouldRetryWithoutReasoningParameters(request, latestErrorDetail)) {
                        request = withoutReasoningParameters(request)
                        response = api.createChatCompletionAt(
                            buildOpenAiTextUrl(baseUrl, activeProvider),
                            request,
                        )
                        latestErrorDetail = if (!response.isSuccessful) {
                            response.errorBody()?.string().orEmpty()
                        } else {
                            ""
                        }
                    }
                    if (response.code() == 400 && shouldRetryWithoutRoleplaySampling(request, latestErrorDetail)) {
                        markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
                        request = withoutRoleplaySampling(request)
                        response = api.createChatCompletionAt(
                            buildOpenAiTextUrl(baseUrl, activeProvider),
                            request,
                        )
                        latestErrorDetail = if (!response.isSuccessful) {
                            response.errorBody()?.string().orEmpty()
                        } else {
                            ""
                        }
                    }
                    if (!response.isSuccessful) {
                        throw PromptExtrasResponseSupport.buildHttpFailure(
                            operation = "聊天请求失败",
                            code = response.code(),
                            errorDetail = latestErrorDetail,
                            headers = response.headers(),
                        )
                    }
                    val choice = response.body()
                        ?.choices
                        ?.firstOrNull()
                    assistantReplyFromOpenAiMessage(
                        assistantMessage = choice?.message,
                        finishReason = choice?.finishReason,
                    )
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
                        promptEnvelope = promptEnvelope,
                        additionalOpenAiTools = modelBuiltInSearchOptions.tools,
                        googleSearchRetrieval = modelBuiltInSearchOptions.googleSearchRetrieval,
                    ).finalReply ?: throw IllegalStateException("模型未返回有效内容")
                }
            }

            OpenAiTextApiMode.RESPONSES -> {
                if (gatewayTooling.enabledToolNames.isEmpty()) {
                    var request = buildRequestWithRoleplaySampling(
                        model = selectedModel,
                        messages = requestMessages,
                        baseUrl = baseUrl,
                        apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                        promptMode = PromptMode.CHAT,
                        reasoningEffort = thinkingRequestConfig.reasoningEffort,
                        enableThinking = thinkingRequestConfig.enableThinking,
                        thinkingBudget = thinkingRequestConfig.thinkingBudget,
                        thinking = thinkingRequestConfig.thinking,
                        promptEnvelope = promptEnvelope,
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
                        promptEnvelope = promptEnvelope,
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
        promptEnvelope: PromptEnvelope,
    ): AssistantReply {
        if (gatewayTooling.enabledToolNames.isEmpty()) {
            return sendAnthropicMessageWithoutTools(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = requestMessages,
                thinkingRequestConfig = thinkingRequestConfig,
                promptMode = PromptMode.CHAT,
                promptEnvelope = promptEnvelope,
            )
        }

        val outcome = try {
            toolEngine.runAnthropicToolLoop(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = requestMessages,
                thinkingRequestConfig = thinkingRequestConfig,
                enabledToolNames = gatewayTooling.enabledToolNames,
                toolContext = gatewayTooling.toolContext,
                promptMode = PromptMode.CHAT,
                promptEnvelope = promptEnvelope,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            if (!shouldRetryAnthropicWithoutTools(exception)) throw exception
            return sendAnthropicMessageWithoutTools(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = requestMessages,
                thinkingRequestConfig = thinkingRequestConfig,
                promptMode = PromptMode.CHAT,
                promptEnvelope = promptEnvelope,
            )
        }
        return outcome.finalReply ?: throw IllegalStateException("模型未返回有效内容")
    }

    private suspend fun sendAnthropicMessageWithoutTools(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        promptMode: PromptMode,
        promptEnvelope: PromptEnvelope,
    ): AssistantReply {
        var request = buildRequestWithRoleplaySampling(
            model = selectedModel,
            messages = requestMessages,
            baseUrl = baseUrl,
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
            promptMode = promptMode,
            thinking = thinkingRequestConfig.thinking,
            promptEnvelope = promptEnvelope,
        )
        var response = anthropicApiProvider(baseUrl, apiKey).createMessage(
            AnthropicProtocolSupport.buildMessageRequest(
                request,
            ),
        )
        if (!response.isSuccessful) {
            var errorDetail = response.errorBody()?.string().orEmpty()
            if (response.code() == 400 && shouldRetryWithoutRoleplaySampling(request, errorDetail)) {
                markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.ANTHROPIC)
                request = withoutRoleplaySampling(request)
                response = anthropicApiProvider(baseUrl, apiKey).createMessage(
                    AnthropicProtocolSupport.buildMessageRequest(
                        request,
                    ),
                )
                errorDetail = response.errorBody()?.string().orEmpty()
            }
            if (response.isSuccessful) {
                val body = response.body() ?: throw IllegalStateException("响应体为空")
                return ensureAssistantReplyHasContent(AnthropicProtocolSupport.toAssistantReply(body))
            }
            throw PromptExtrasResponseSupport.buildHttpFailure(
                operation = "聊天请求失败",
                code = response.code(),
                errorDetail = errorDetail,
                headers = response.headers(),
            )
        }
        val body = response.body() ?: throw IllegalStateException("响应体为空")
        return ensureAssistantReplyHasContent(AnthropicProtocolSupport.toAssistantReply(body))
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
        promptEnvelope: PromptEnvelope,
        isModelBuiltInSearch: Boolean = false,
    ): Flow<ChatStreamEvent> = flow {
        val modelBuiltInSearchOptions = modelBuiltInSearchRequestOptions(isModelBuiltInSearch)
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
                    promptEnvelope = promptEnvelope,
                    additionalOpenAiTools = modelBuiltInSearchOptions.tools,
                    googleSearchRetrieval = modelBuiltInSearchOptions.googleSearchRetrieval,
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
                        promptEnvelope = promptEnvelope,
                        isModelBuiltInSearch = isModelBuiltInSearch,
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
                    promptEnvelope = promptEnvelope,
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
                            maxOutputTokens = continuation.maxOutputTokens,
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
        promptEnvelope: PromptEnvelope,
    ): Flow<ChatStreamEvent> = flow {
        val toolLoopOutcome = try {
            toolEngine.runAnthropicToolLoop(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = requestMessages,
                thinkingRequestConfig = thinkingRequestConfig,
                enabledToolNames = gatewayTooling.enabledToolNames,
                toolContext = gatewayTooling.toolContext,
                promptMode = promptMode,
                promptEnvelope = promptEnvelope,
            )
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            if (!shouldRetryAnthropicWithoutTools(exception)) throw exception
            streamAnthropicMessage(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
                requestMessages = requestMessages,
                thinkingRequestConfig = thinkingRequestConfig,
                promptMode = promptMode,
                promptEnvelope = promptEnvelope,
            ).collect { emit(it) }
            return@flow
        }
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
                promptEnvelope = promptEnvelope,
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
            if (!response.isEventStreamResponse()) {
                val rawBody = response.body?.string()
                    ?: throw IllegalStateException("响应体为空")
                streamBufferedOpenAiResponse(
                    rawBody = rawBody,
                    apiMode = OpenAiTextApiMode.RESPONSES,
                ).collect { emit(it) }
                return@flow
            }
            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")
            val thinkTagParser = ThinkTagStreamParser()
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val data = extractSseData(line) ?: continue
                if (emitOpenAiStreamData(data, OpenAiTextApiMode.RESPONSES, thinkTagParser)) break
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
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
        isModelBuiltInSearch: Boolean = false,
    ): Flow<ChatStreamEvent> = flow {
        val apiMode = activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        val modelBuiltInSearchOptions = modelBuiltInSearchRequestOptions(
            enabled = isModelBuiltInSearch && apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS,
        )
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
                    promptEnvelope = promptEnvelope,
                    isModelBuiltInSearch = isModelBuiltInSearch,
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
            enableThinking = thinkingRequestConfig.enableThinking,
            thinkingBudget = thinkingRequestConfig.thinkingBudget,
            thinking = thinkingRequestConfig.thinking,
            promptMode = promptMode,
            promptEnvelope = promptEnvelope,
            tools = modelBuiltInSearchOptions.tools,
            googleSearchRetrieval = modelBuiltInSearchOptions.googleSearchRetrieval,
        )
        var call = client.newCall(
            buildStreamingRequest(
                fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider),
                requestBody = serializeOpenAiStreamingRequestBody(apiMode, requestBody),
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
            if (response.code == 400 && shouldRetryWithoutReasoningParameters(requestBody, streamErrorDetail)) {
                response.close()
                requestBody = withoutReasoningParameters(requestBody)
                call = client.newCall(
                    buildStreamingRequest(
                        fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider),
                        requestBody = serializeOpenAiStreamingRequestBody(apiMode, requestBody),
                    ),
                )
                response = call.execute()
            }
            val retryWithoutReasoningErrorDetail = if (!response.isSuccessful) {
                response.peekBody(64L * 1024L).string()
            } else {
                ""
            }
            if (response.code == 400 && shouldRetryWithoutRoleplaySampling(requestBody, retryWithoutReasoningErrorDetail)) {
                response.close()
                markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
                requestBody = withoutRoleplaySampling(requestBody)
                call = client.newCall(
                    buildStreamingRequest(
                        fullUrl = buildOpenAiTextUrl(baseUrl, activeProvider),
                        requestBody = serializeOpenAiStreamingRequestBody(apiMode, requestBody),
                    ),
                )
                response = call.execute()
            }
            if (!response.isSuccessful) {
                throw GatewayNetworkSupport.okhttpFailure("聊天请求失败", response)
            }

            if (!response.isEventStreamResponse()) {
                val rawBody = response.body?.string()
                    ?: throw IllegalStateException("响应体为空")
                streamBufferedOpenAiResponse(
                    rawBody = rawBody,
                    apiMode = apiMode,
                ).collect { emit(it) }
                return@flow
            }

            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")

            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank()) continue
                val data = extractSseData(line) ?: continue
                if (emitOpenAiStreamData(data, apiMode, thinkTagParser)) break
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

    private fun streamBufferedOpenAiResponse(
        rawBody: String,
        apiMode: OpenAiTextApiMode,
    ): Flow<ChatStreamEvent> = flow {
        val body = rawBody.trim()
        if (body.isBlank()) {
            throw IllegalStateException("响应体为空")
        }

        if (body.lineSequence().any { extractSseData(it) != null }) {
            val thinkTagParser = ThinkTagStreamParser()
            for (line in body.lineSequence()) {
                if (line.isBlank()) continue
                val data = extractSseData(line) ?: continue
                if (emitOpenAiStreamData(data, apiMode, thinkTagParser)) break
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
            return@flow
        }

        when (apiMode) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> emitAssistantReply(
                parseChatCompletionJsonResponse(body),
            ).collect { emit(it) }

            OpenAiTextApiMode.RESPONSES -> emitAssistantReply(
                parseResponsesJsonResponse(body),
            ).collect { emit(it) }
        }
    }

    private fun parseChatCompletionJsonResponse(
        rawBody: String,
    ): AssistantReply {
        // 仅解析一次 JSON：先得到树，再从树反序列化为 OpenAI 结构，Gemini 原生兜底复用同一棵树
        val root = runCatching { JsonParser.parseString(rawBody).asJsonObject }.getOrNull()
        val response = root?.let {
            runCatching { gson.fromJson(it, ChatCompletionResponse::class.java) }.getOrNull()
        }
        val choice = response?.choices?.firstOrNull()
        val assistantMessage = choice?.message
        if (assistantMessage != null) {
            return assistantReplyFromOpenAiMessage(
                assistantMessage = assistantMessage,
                finishReason = choice.finishReason,
            )
        }
        return root?.let { parseGeminiNativeJsonResponse(it) }
            ?: throw IllegalStateException("模型未返回有效内容")
    }

    private fun parseResponsesJsonResponse(
        rawBody: String,
    ): AssistantReply {
        val response = runCatching {
            gson.fromJson(rawBody, ResponseApiResponse::class.java)
        }.getOrNull()
        val parsed = response?.let(ResponseApiSupport::parseResponse)
            ?: ResponseApiParsedOutput()
        return ensureAssistantReplyHasContent(
            AssistantReply(
                content = parsed.content,
                reasoningContent = parsed.reasoning,
            ),
        )
    }

    private fun parseGeminiNativeJsonResponse(
        root: JsonObject,
    ): AssistantReply? {
        val candidates = root.getAsJsonArrayOrNull("candidates") ?: return null
        if (candidates.size() == 0) {
            throw geminiNativeEmptyContentException(root)
        }
        val output = extractGeminiNativeOutput(root)
        if (output.content.isBlank() && output.reasoningContent.isBlank()) {
            throw geminiNativeEmptyContentException(root)
        }
        return ensureAssistantReplyHasContent(
            AssistantReply(
                content = output.content,
                reasoningContent = output.reasoningContent,
            ),
        )
    }

    private suspend fun FlowCollector<ChatStreamEvent>.emitOpenAiStreamData(
        data: String,
        apiMode: OpenAiTextApiMode,
        thinkTagParser: ThinkTagStreamParser,
    ): Boolean {
        if (data == "[DONE]") {
            return true
        }

        openAiStreamErrorException(data)?.let { throw it }

        if (apiMode == OpenAiTextApiMode.RESPONSES) {
            when (val event = ResponseApiSupport.parseStreamEvent(data)) {
                is ResponseApiStreamEvent.ContentDelta -> emit(ChatStreamEvent.ContentDelta(event.value))
                is ResponseApiStreamEvent.ReasoningDelta -> emit(ChatStreamEvent.ReasoningDelta(event.value))
                ResponseApiStreamEvent.Completed -> return true
                null -> Unit
            }
            return false
        }

        if (emitGeminiNativeStreamData(data, thinkTagParser)) {
            return false
        }

        val chunk = runCatching {
            gson.fromJson(data, ChatCompletionChunk::class.java)
        }.getOrNull() ?: return false

        val delta = chunk.choices.firstOrNull()?.delta
        val reasoningDelta = GatewayAssistantOutputParser.extractReasoning(delta)
        if (reasoningDelta.isNotBlank()) {
            emit(ChatStreamEvent.ReasoningDelta(reasoningDelta))
        }
        val contentDelta = GatewayAssistantOutputParser.extractContent(delta)
        if (contentDelta.isNotBlank()) {
            emitContentDelta(contentDelta, thinkTagParser)
        }
        GatewayAssistantOutputParser.extractAssistantImageParts(delta?.images).forEach { imagePart ->
            emit(ChatStreamEvent.ImageDelta(imagePart))
        }
        return false
    }

    private fun openAiStreamErrorException(data: String): IllegalStateException? {
        val detail = extractOpenAiStreamErrorDetail(data) ?: return null
        val guidance = PromptExtrasResponseSupport.contentSafetyGuidance(detail).orEmpty()
        val redacted = AiErrorRedaction.redact(detail)
        return IllegalStateException(
            buildString {
                append("聊天请求失败")
                if (guidance.isNotBlank()) {
                    append('（')
                    append(guidance)
                    append('）')
                }
                if (redacted.isNotBlank()) {
                    append('：')
                    append(redacted)
                }
            },
        )
    }

    private fun extractOpenAiStreamErrorDetail(data: String): String? {
        val root = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull()
            ?: return null
        val error = root.get("error")
        val message = when {
            error == null || error.isJsonNull -> ""
            error.isJsonObject -> {
                val obj = error.asJsonObject
                listOfNotNull(
                    obj.jsonString("message"),
                    obj.jsonString("detail"),
                    obj.jsonString("details"),
                    obj.jsonString("code"),
                    obj.jsonString("type"),
                ).distinct().joinToString(separator = "\n")
            }
            error.isJsonPrimitive -> runCatching { error.asString }.getOrDefault("")
            else -> error.toString()
        }.ifBlank {
            val eventType = root.jsonString("type")
            root.jsonString("detail")
                ?: root.jsonString("message")
                ?: eventType?.takeIf { it.contains("error", ignoreCase = true) }
                ?: ""
        }
        if (message.isBlank()) {
            return null
        }
        val requestId = root.jsonString("request_id")
            ?: root.jsonString("request-id")
            ?: root.jsonString("id")
        return buildString {
            append(message.trim())
            if (!requestId.isNullOrBlank()) {
                append("\nrequest-id: ")
                append(requestId)
            }
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun JsonObject.jsonString(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) {
            return null
        }
        return runCatching { element.asString.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private suspend fun FlowCollector<ChatStreamEvent>.emitContentDelta(
        contentDelta: String,
        thinkTagParser: ThinkTagStreamParser,
    ) {
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

    private suspend fun FlowCollector<ChatStreamEvent>.emitGeminiNativeStreamData(
        data: String,
        thinkTagParser: ThinkTagStreamParser,
    ): Boolean {
        val root = runCatching { JsonParser.parseString(data).asJsonObject }
            .getOrNull()
            ?: return false
        if (!root.has("candidates")) {
            return false
        }
        val output = extractGeminiNativeOutput(root)
        if (output.reasoningContent.isNotBlank()) {
            emit(ChatStreamEvent.ReasoningDelta(output.reasoningContent))
        }
        if (output.content.isNotBlank()) {
            emitContentDelta(output.content, thinkTagParser)
        }
        if (output.content.isBlank() && output.reasoningContent.isBlank()) {
            val failure = geminiNativeEmptyContentMessage(root)
            if (failure != null) {
                throw IllegalStateException(AiErrorRedaction.redact(failure))
            }
        }
        return true
    }

    private fun extractGeminiNativeOutput(
        root: JsonObject,
    ): GeminiNativeOutput {
        val candidates = root.getAsJsonArrayOrNull("candidates") ?: return GeminiNativeOutput()
        var fallbackReasoning = ""
        for (candidateElement in candidates) {
            val candidate = candidateElement.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val parts = candidate
                .get("content")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
                ?.getAsJsonArrayOrNull("parts")
                ?: continue
            val output = extractGeminiNativeParts(parts)
            if (output.content.isNotBlank()) {
                return output
            }
            if (fallbackReasoning.isBlank() && output.reasoningContent.isNotBlank()) {
                fallbackReasoning = output.reasoningContent
            }
        }
        return GeminiNativeOutput(reasoningContent = fallbackReasoning)
    }

    private fun extractGeminiNativeParts(
        parts: Iterable<com.google.gson.JsonElement>,
    ): GeminiNativeOutput {
        val content = mutableListOf<String>()
        val reasoning = mutableListOf<String>()
        for (partElement in parts) {
            val part = partElement.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val text = part.get("text")
                ?.takeIf { !it.isJsonNull }
                ?.asString
                .orEmpty()
            if (text.isBlank()) {
                continue
            }
            if (part.booleanValue("thought")) {
                reasoning += text
            } else {
                content += text
            }
        }
        return GeminiNativeOutput(
            content = content.joinToString(separator = "\n\n"),
            reasoningContent = reasoning.joinToString(separator = "\n\n"),
        )
    }

    private fun geminiNativeEmptyContentException(
        root: JsonObject,
    ): IllegalStateException {
        return IllegalStateException(
            AiErrorRedaction.redact(geminiNativeEmptyContentMessage(root) ?: "模型未返回有效内容"),
        )
    }

    private fun geminiNativeEmptyContentMessage(
        root: JsonObject,
    ): String? {
        val blockReason = root
            .get("promptFeedback")
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.stringValue("blockReason")
            .orEmpty()
        if (blockReason.isNotBlank()) {
            return "Gemini 请求被安全策略拦截：$blockReason"
        }
        val finishReason = root.getAsJsonArrayOrNull("candidates")
            ?.asSequence()
            ?.mapNotNull { candidate ->
                candidate.takeIf { it.isJsonObject }
                    ?.asJsonObject
                    ?.stringValue("finishReason")
                    ?.takeIf { it.isNotBlank() }
            }
            ?.firstOrNull { it != "STOP" }
            .orEmpty()
        if (finishReason.isNotBlank()) {
            return "Gemini 未返回可见正文，结束原因：$finishReason"
        }
        return null
    }

    private fun okhttp3.Response.isEventStreamResponse(): Boolean {
        return header("Content-Type")
            .orEmpty()
            .lowercase()
            .contains("text/event-stream")
    }

    private fun extractSseData(line: String): String? {
        if (!line.startsWith("data:")) {
            return null
        }
        return line.removePrefix("data:").trim()
    }

    private fun serializeOpenAiStreamingRequestBody(
        apiMode: OpenAiTextApiMode,
        request: ChatCompletionRequest,
    ): String {
        return if (apiMode == OpenAiTextApiMode.RESPONSES) {
            gson.toJson(ResponseApiSupport.buildRequest(request))
        } else {
            gson.toJson(request)
        }
    }

    private suspend fun sendOpenAiMessageWithoutStreaming(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: com.example.myapplication.model.ThinkingRequestConfig,
        activeProvider: ProviderSettings?,
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
        isModelBuiltInSearch: Boolean = false,
    ): AssistantReply {
        val apiMode = activeProvider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        val modelBuiltInSearchOptions = modelBuiltInSearchRequestOptions(
            enabled = isModelBuiltInSearch && apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS,
        )
        return when (apiMode) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> {
                var request = buildRequestWithRoleplaySampling(
                    model = selectedModel,
                    messages = requestMessages,
                    baseUrl = baseUrl,
                    apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                    promptMode = PromptMode.CHAT,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    enableThinking = thinkingRequestConfig.enableThinking,
                    thinkingBudget = thinkingRequestConfig.thinkingBudget,
                    thinking = thinkingRequestConfig.thinking,
                    promptEnvelope = promptEnvelope,
                    tools = modelBuiltInSearchOptions.tools,
                    googleSearchRetrieval = modelBuiltInSearchOptions.googleSearchRetrieval,
                )
                val api = apiServiceProvider(baseUrl, apiKey)
                var response = api.createChatCompletionAt(
                    buildOpenAiTextUrl(baseUrl, activeProvider),
                    request,
                )
                var latestErrorDetail = if (!response.isSuccessful) {
                    response.errorBody()?.string().orEmpty()
                } else {
                    ""
                }
                if (response.code() == 400 && shouldRetryWithoutReasoningParameters(request, latestErrorDetail)) {
                    request = withoutReasoningParameters(request)
                    response = api.createChatCompletionAt(
                        buildOpenAiTextUrl(baseUrl, activeProvider),
                        request,
                    )
                    latestErrorDetail = if (!response.isSuccessful) {
                        response.errorBody()?.string().orEmpty()
                    } else {
                        ""
                    }
                }
                if (response.code() == 400 && shouldRetryWithoutRoleplaySampling(request, latestErrorDetail)) {
                    markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
                    request = withoutRoleplaySampling(request)
                    response = api.createChatCompletionAt(
                        buildOpenAiTextUrl(baseUrl, activeProvider),
                        request,
                    )
                    latestErrorDetail = if (!response.isSuccessful) {
                        response.errorBody()?.string().orEmpty()
                    } else {
                        ""
                    }
                }
                if (!response.isSuccessful) {
                    throw PromptExtrasResponseSupport.buildHttpFailure(
                        operation = "聊天请求失败",
                        code = response.code(),
                        errorDetail = latestErrorDetail,
                        headers = response.headers(),
                    )
                }
                val choice = response.body()?.choices?.firstOrNull()
                assistantReplyFromOpenAiMessage(
                    assistantMessage = choice?.message,
                    finishReason = choice?.finishReason,
                )
            }

            OpenAiTextApiMode.RESPONSES -> {
                val request = buildRequestWithRoleplaySampling(
                    model = selectedModel,
                    messages = requestMessages,
                    baseUrl = baseUrl,
                    apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                    promptMode = PromptMode.CHAT,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    enableThinking = thinkingRequestConfig.enableThinking,
                    thinkingBudget = thinkingRequestConfig.thinkingBudget,
                    thinking = thinkingRequestConfig.thinking,
                    promptEnvelope = promptEnvelope,
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
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
    ): Flow<ChatStreamEvent> = flow {
        var requestBody = buildRequestWithRoleplaySampling(
            model = selectedModel,
            messages = requestMessages,
            baseUrl = baseUrl,
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
            stream = true,
            thinking = thinkingRequestConfig.thinking,
            promptMode = promptMode,
            promptEnvelope = promptEnvelope,
        )
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.ANTHROPIC)
        val client = anthropicStreamClientProvider(baseUrl, apiKey)
        var call = client.newCall(buildAnthropicStreamingRequest(normalizedBaseUrl, requestBody))
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
            val streamErrorDetail = if (!response.isSuccessful) {
                response.peekBody(64L * 1024L).string()
            } else {
                ""
            }
            if (response.code == 400 && shouldRetryWithoutRoleplaySampling(requestBody, streamErrorDetail)) {
                response.close()
                markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.ANTHROPIC)
                requestBody = withoutRoleplaySampling(requestBody)
                call = client.newCall(buildAnthropicStreamingRequest(normalizedBaseUrl, requestBody))
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
                val data = extractSseData(line) ?: continue
                if (data == "[DONE]") break
                val delta = AnthropicProtocolSupport.parseStreamData(data)
                if (!delta.errorMessage.isNullOrBlank()) {
                    throw IllegalStateException(AiErrorRedaction.redact(delta.errorMessage))
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

    private fun buildAnthropicStreamingRequest(
        normalizedBaseUrl: String,
        requestBody: ChatCompletionRequest,
    ): Request {
        return Request.Builder()
            .url("${normalizedBaseUrl}messages")
            .post(
                gson.toJson(AnthropicProtocolSupport.buildMessageRequest(requestBody))
                    .toRequestBody("application/json".toMediaType()),
            )
            .build()
    }

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
        enableThinking: Boolean? = null,
        thinkingBudget: Int? = null,
        thinking: com.example.myapplication.model.ThinkingConfigDto? = null,
        promptMode: PromptMode = PromptMode.ROLEPLAY,
        tools: List<com.example.myapplication.model.ChatToolDto> = emptyList(),
        toolChoice: String? = null,
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
        googleSearchRetrieval: Map<String, Any>? = null,
    ): ChatCompletionRequest = GatewayRequestSupport.buildRequestWithRoleplaySampling(
        model = model,
        messages = messages,
        baseUrl = baseUrl,
        apiProtocol = apiProtocol,
        apiServiceFactory = apiServiceFactory,
        disabledBaseUrls = roleplaySamplingDisabledBaseUrls,
        stream = stream,
        reasoningEffort = reasoningEffort,
        enableThinking = enableThinking,
        thinkingBudget = thinkingBudget,
        thinking = thinking,
        promptMode = promptMode,
        samplerOverride = promptEnvelope.sampler,
        stopSequences = promptEnvelope.stopSequences,
        tools = tools,
        toolChoice = toolChoice,
        googleSearchRetrieval = googleSearchRetrieval,
    )

    private fun shouldRetryWithoutRoleplaySampling(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean = GatewayRequestSupport.shouldRetryWithoutRoleplaySampling(request, errorDetail)

    private fun shouldRetryWithoutReasoningParameters(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean = GatewayRequestSupport.shouldRetryWithoutReasoningParameters(request, errorDetail)

    private fun withoutRoleplaySampling(
        request: ChatCompletionRequest,
    ): ChatCompletionRequest = GatewayRequestSupport.withoutRoleplaySampling(request)

    private fun withoutReasoningParameters(
        request: ChatCompletionRequest,
    ): ChatCompletionRequest = GatewayRequestSupport.withoutReasoningParameters(request)

    private fun shouldRetryAnthropicWithoutTools(
        exception: Exception,
    ): Boolean {
        val message = exception.message.orEmpty().lowercase()
        if (!message.contains("聊天请求失败：400")) {
            return false
        }
        return listOf(
            "tool",
            "tools",
            "tool_use",
            "temperature",
            "top_p",
            "top p",
            "top_k",
            "stop_sequences",
            "unknown field",
            "unknown parameter",
            "unsupported",
            "not supported",
            "not allowed",
            "not permitted",
        ).any(message::contains)
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
        promptEnvelope: PromptEnvelope = PromptEnvelope(),
    ): List<ChatMessageDto> {
        return GatewayRequestMessageBuilder.build(
            messages = messages,
            systemPrompt = systemPrompt,
            promptMode = promptMode,
            promptEnvelope = promptEnvelope,
            imagePayloadResolver = imagePayloadResolver,
            filePromptResolver = filePromptResolver,
        )
    }

}
