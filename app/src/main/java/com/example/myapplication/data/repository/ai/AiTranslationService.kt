package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatCompletionChunk
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ScreenTextBlock
import com.example.myapplication.model.ScreenTranslationRequest
import com.example.myapplication.model.ScreenTranslationResult
import com.example.myapplication.model.ScreenTranslationSegmentResult
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
import java.net.UnknownHostException

interface AiTranslationService {
    suspend fun translateText(
        text: String,
        targetLanguage: String = "简体中文",
        sourceLanguage: String = "自动检测",
    ): String

    suspend fun translateStructuredSegments(
        request: ScreenTranslationRequest,
    ): ScreenTranslationResult

    fun translateTextStream(
        text: String,
        targetLanguage: String = "简体中文",
        sourceLanguage: String = "自动检测",
    ): Flow<String>
}

class DefaultAiTranslationService(
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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AiTranslationService {
    private val gson = Gson()

    override suspend fun translateText(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): String {
        val settings = requireConfiguredSettings()
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = resolveTranslationModelId(settings)

        val content = runNetworkCall {
            requestCompletionText(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId,
                requestMessages = buildTranslationRequestMessages(
                    text = text,
                    targetLanguage = targetLanguage,
                    sourceLanguage = sourceLanguage,
                ),
                apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE,
                operation = "翻译失败",
            )
        }.trim()
        if (content.isBlank()) {
            throw IllegalStateException("翻译模型未返回有效内容")
        }
        return content
    }

    override fun translateTextStream(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): Flow<String> = flow {
        val settings = requireConfiguredSettings()
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = resolveTranslationModelId(settings)

        val apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE
        val requestBody = ChatCompletionRequest(
            model = modelId,
            messages = buildTranslationRequestMessages(
                text = text,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
            ),
            stream = true,
        )
        val builder = StringBuilder()
        streamCompletionText(
            baseUrl = baseUrl,
            apiKey = apiKey,
            request = requestBody,
            apiProtocol = apiProtocol,
            operation = "翻译失败",
        ).collect { delta ->
            builder.append(delta)
            emit(builder.toString())
        }

        if (builder.isBlank()) {
            throw IllegalStateException("翻译模型未返回有效内容")
        }
    }.flowOn(ioDispatcher)

    override suspend fun translateStructuredSegments(
        request: ScreenTranslationRequest,
    ): ScreenTranslationResult {
        val normalizedSegments = request.segments
            .filter { it.text.isNotBlank() }
            .sortedBy { it.orderIndex }
        if (normalizedSegments.isEmpty()) {
            return ScreenTranslationResult(
                sourceType = request.sourceType,
                sourceAppPackage = request.appPackage,
                sourceAppLabel = request.appLabel,
                targetLanguage = request.targetLanguage,
                createdAt = System.currentTimeMillis(),
            )
        }

        val settings = requireConfiguredSettings()
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = resolveTranslationModelId(settings)

        val rawContent = runNetworkCall {
            requestCompletionText(
                baseUrl = baseUrl,
                apiKey = apiKey,
                modelId = modelId,
                requestMessages = buildStructuredTranslationMessages(
                    targetLanguage = request.targetLanguage,
                    segments = normalizedSegments,
                ),
                apiProtocol = activeProvider?.resolvedApiProtocol() ?: ProviderApiProtocol.OPENAI_COMPATIBLE,
                operation = "翻译失败",
            )
        }.trim()
        if (rawContent.isBlank()) {
            throw IllegalStateException("翻译模型未返回有效内容")
        }

        val parsedSegments = parseStructuredTranslationContent(
            rawContent = rawContent,
            originalSegments = normalizedSegments,
        )
        if (parsedSegments.isNotEmpty()) {
            return ScreenTranslationResult(
                sourceType = request.sourceType,
                sourceAppPackage = request.appPackage,
                sourceAppLabel = request.appLabel,
                targetLanguage = request.targetLanguage,
                originalSegments = normalizedSegments,
                translatedSegments = parsedSegments,
                fullTranslation = parsedSegments.joinToString(separator = "\n") { it.translatedText },
                createdAt = System.currentTimeMillis(),
            )
        }

        val fallbackTranslation = translateText(
            text = normalizedSegments.joinToString(separator = "\n") { it.text },
            targetLanguage = request.targetLanguage,
            sourceLanguage = "自动检测",
        )
        return ScreenTranslationResult(
            sourceType = request.sourceType,
            sourceAppPackage = request.appPackage,
            sourceAppLabel = request.appLabel,
            targetLanguage = request.targetLanguage,
            originalSegments = normalizedSegments,
            translatedSegments = emptyList(),
            fullTranslation = fallbackTranslation,
            createdAt = System.currentTimeMillis(),
        )
    }

    private suspend fun requireConfiguredSettings(): AppSettings {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        return settings
    }

    private fun resolveTranslationModelId(settings: AppSettings): String {
        val modelId = settings.activeProvider()
            ?.resolveFunctionModel(ProviderFunction.TRANSLATION)
            .orEmpty()
        if (modelId.isBlank()) {
            throw IllegalStateException("请先在模型页开启翻译模型")
        }
        return modelId
    }

    private suspend fun <T> runNetworkCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (exception: Exception) {
            throw exception.toReadableNetworkException()
        }
    }

    private suspend fun requestCompletionText(
        baseUrl: String,
        apiKey: String,
        modelId: String,
        requestMessages: List<ChatMessageDto>,
        apiProtocol: ProviderApiProtocol,
        operation: String,
    ): String {
        return when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                val provider = settingsStore.settingsFlow.first().activeProvider()
                val mode = provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
                if (mode == OpenAiTextApiMode.RESPONSES) {
                    val response = apiServiceProvider(baseUrl, apiKey.trim()).createResponseAt(
                        buildOpenAiTextUrl(baseUrl, provider),
                        ResponseApiSupport.buildRequest(
                            ChatCompletionRequest(
                                model = modelId,
                                messages = requestMessages,
                            ),
                        ),
                    )
                    if (!response.isSuccessful) {
                        throw PromptExtrasResponseSupport.buildHttpFailure(
                            operation = operation,
                            code = response.code(),
                            errorDetail = response.errorBody()?.string().orEmpty(),
                            headers = response.headers(),
                        )
                    }
                    val body = response.body() ?: throw IllegalStateException("响应体为空")
                    ResponseApiSupport.parseResponse(body).content
                } else {
                    val response = apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletionAt(
                        buildOpenAiTextUrl(baseUrl, provider),
                        ChatCompletionRequest(
                            model = modelId,
                            messages = requestMessages,
                        ),
                    )
                    if (!response.isSuccessful) {
                        throw retrofitFailure(operation, response)
                    }
                    extractContentText(
                        response.body()?.choices?.firstOrNull()?.message?.content,
                    )
                }
            }
            ProviderApiProtocol.ANTHROPIC -> {
                val response = anthropicApiProvider(baseUrl, apiKey.trim()).createMessage(
                    AnthropicProtocolSupport.buildMessageRequest(
                        ChatCompletionRequest(
                            model = modelId,
                            messages = requestMessages,
                        ),
                    ),
                )
                if (!response.isSuccessful) {
                    throw PromptExtrasResponseSupport.buildHttpFailure(
                        operation = operation,
                        code = response.code(),
                        errorDetail = response.errorBody()?.string().orEmpty(),
                        headers = response.headers(),
                    )
                }
                val body = response.body() ?: throw IllegalStateException("响应体为空")
                AnthropicProtocolSupport.extractContentText(body)
            }
        }
    }

    private fun streamCompletionText(
        baseUrl: String,
        apiKey: String,
        request: ChatCompletionRequest,
        apiProtocol: ProviderApiProtocol,
        operation: String,
    ): Flow<String> = flow {
        when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> {
                val provider = settingsStore.settingsFlow.first().activeProvider()
                val mode = provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
                val httpRequest = Request.Builder()
                    .url(buildOpenAiTextUrl(baseUrl, provider))
                    .post(
                        if (mode == OpenAiTextApiMode.RESPONSES) {
                            gson.toJson(ResponseApiSupport.buildRequest(request))
                        } else {
                            gson.toJson(request)
                        }.toRequestBody("application/json".toMediaType()),
                    )
                    .build()
                streamWithOkHttp(
                    client = streamClientProvider(baseUrl, apiKey),
                    request = httpRequest,
                    operation = operation,
                    onData = { data ->
                        if (mode == OpenAiTextApiMode.RESPONSES) {
                            when (val event = ResponseApiSupport.parseStreamEvent(data)) {
                                is ResponseApiStreamEvent.ContentDelta -> event.value
                                is ResponseApiStreamEvent.ReasoningDelta -> event.value
                                ResponseApiStreamEvent.Completed, null -> null
                            }
                        } else if (data == "[DONE]") {
                            null
                        } else {
                            runCatching {
                                gson.fromJson(data, ChatCompletionChunk::class.java)
                            }.getOrNull()?.choices?.firstOrNull()?.delta?.content.orEmpty()
                        }
                    },
                ).collect { emit(it) }
            }
            ProviderApiProtocol.ANTHROPIC -> {
                val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
                val anthropicRequest = AnthropicProtocolSupport.buildMessageRequest(request)
                val httpRequest = Request.Builder()
                    .url("${normalizedBaseUrl}messages")
                    .post(gson.toJson(anthropicRequest).toRequestBody("application/json".toMediaType()))
                    .build()
                streamWithOkHttp(
                    client = anthropicStreamClientProvider(baseUrl, apiKey),
                    request = httpRequest,
                    operation = operation,
                    onData = { data ->
                        val delta = AnthropicProtocolSupport.parseStreamData(data)
                        if (!delta.errorMessage.isNullOrBlank()) {
                            throw IllegalStateException(delta.errorMessage)
                        }
                        if (delta.stop) {
                            null
                        } else {
                            delta.content
                        }
                    },
                ).collect { emit(it) }
            }
        }
    }

    private fun streamWithOkHttp(
        client: OkHttpClient,
        request: Request,
        operation: String,
        onData: (String) -> String?,
    ): Flow<String> = flow {
        val call = client.newCall(request)
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
                throw okhttpFailure(operation, response)
            }
            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val delta = onData(line.removePrefix("data: ").trim()).orEmpty()
                if (delta.isNotBlank()) {
                    emit(delta)
                }
            }
        } catch (exception: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("翻译请求已取消").apply { initCause(exception) }
            }
            throw exception.toReadableNetworkException()
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            throw exception.toReadableNetworkException()
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }

    private fun buildTranslationRequestMessages(
        text: String,
        targetLanguage: String,
        sourceLanguage: String,
    ): List<ChatMessageDto> {
        return listOf(
            ChatMessageDto(
                role = "system",
                content = buildString {
                    append("你是一个专业翻译助手。")
                    if (sourceLanguage.isNotBlank() && sourceLanguage != "自动检测") {
                        append("请将用户提供的内容从")
                        append(sourceLanguage)
                        append("准确翻译为")
                        append(targetLanguage)
                    } else {
                        append("请将用户提供的内容准确翻译为")
                        append(targetLanguage)
                    }
                    append("，只输出译文，不要解释，不要加引号。")
                },
            ),
            ChatMessageDto(
                role = "user",
                content = text.trim(),
            ),
        )
    }

    private fun buildStructuredTranslationMessages(
        targetLanguage: String,
        segments: List<ScreenTextBlock>,
    ): List<ChatMessageDto> {
        val numberedSegments = segments.joinToString(separator = "\n") { segment ->
            "${segment.orderIndex + 1}\t${segment.text.trim()}"
        }
        return listOf(
            ChatMessageDto(
                role = "system",
                content = buildString {
                    append("你是一个屏幕文本翻译助手。")
                    append("请将用户提供的每一行文本逐条翻译为")
                    append(targetLanguage)
                    append("。")
                    append("必须保持原有顺序，且每行只输出一条结果。")
                    append("输出格式固定为：编号、一个制表符、译文。")
                    append("不要输出额外解释、标题或代码块。")
                },
            ),
            ChatMessageDto(
                role = "user",
                content = numberedSegments,
            ),
        )
    }

    private fun parseStructuredTranslationContent(
        rawContent: String,
        originalSegments: List<ScreenTextBlock>,
    ): List<ScreenTranslationSegmentResult> {
        val linePattern = Regex("""^\s*(\d+)\s*[\t:：\.\)\]-]+\s*(.+?)\s*$""")
        val parsedByIndex = rawContent.lines()
            .mapNotNull { line ->
                val match = linePattern.find(line.trim()) ?: return@mapNotNull null
                val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: return@mapNotNull null
                val translation = match.groupValues[2].trim()
                if (translation.isBlank()) return@mapNotNull null
                index to translation
            }
            .toMap()

        if (parsedByIndex.size != originalSegments.size) {
            return emptyList()
        }

        return originalSegments.mapNotNull { segment ->
            val translatedText = parsedByIndex[segment.orderIndex] ?: return@mapNotNull null
            ScreenTranslationSegmentResult(
                sourceText = segment.text,
                translatedText = translatedText,
                bounds = segment.bounds,
                orderIndex = segment.orderIndex,
            )
        }.takeIf { it.size == originalSegments.size }.orEmpty()
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

    private fun extractContentText(content: Any?): String {
        return when (content) {
            null -> ""
            is String -> content
            is List<*> -> content.mapNotNull(::extractContentPartText)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n")
            is Map<*, *> -> extractContentPartText(content)
            else -> ""
        }
    }

    private fun extractContentPartText(contentPart: Any?): String {
        return when (contentPart) {
            is String -> contentPart
            is Map<*, *> -> (contentPart["text"] as? String).orEmpty()
            else -> ""
        }
    }

    private fun <T> retrofitFailure(
        operation: String,
        response: retrofit2.Response<T>,
    ): IllegalStateException {
        val errorDetail = response.errorBody()?.string().orEmpty()
        return IllegalStateException(
            buildString {
                append(operation)
                append('：')
                append(response.code())
                val normalizedErrorDetail = errorDetail.trim()
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    private fun okhttpFailure(
        operation: String,
        response: okhttp3.Response,
    ): IllegalStateException {
        val errorDetail = response.body?.string().orEmpty()
        return IllegalStateException(
            buildString {
                append(operation)
                append('：')
                append(response.code)
                val normalizedErrorDetail = errorDetail.trim()
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    private fun Exception.toReadableNetworkException(): Exception {
        if (this is CancellationException) {
            return this
        }
        if (findUnknownHostException() != null) {
            return IllegalStateException(
                "无法解析服务地址，请检查设备网络、DNS 设置，并确认 Base URL 是否可访问",
                this,
            )
        }
        return this
    }

    private fun Throwable.findUnknownHostException(): UnknownHostException? {
        var current: Throwable? = this
        while (current != null) {
            if (current is UnknownHostException) {
                return current
            }
            current = current.cause
        }
        return null
    }
}
