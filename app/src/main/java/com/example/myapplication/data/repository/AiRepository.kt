package com.example.myapplication.data.repository

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatCompletionChunk
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageUrlContentPartDto
import com.example.myapplication.model.ImageUrlDto
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.ScreenTextBlock
import com.example.myapplication.model.ScreenTranslationRequest
import com.example.myapplication.model.ScreenTranslationResult
import com.example.myapplication.model.ScreenTranslationSegmentResult
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TextContentPartDto
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.buildThinkingRequestConfig
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.inferredModelInfo
import com.example.myapplication.model.isTransferPart
import com.example.myapplication.model.isValidTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toMessageAttachmentOrNull
import com.example.myapplication.model.toPlainText
import com.google.gson.Gson
import com.google.gson.JsonParser
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

private const val DefaultChatFormattingPrompt = """你是一个注重可读性的中文助手。
默认使用清晰、克制的 Markdown 排版回答：
- 根据内容自然分段，段落之间留空行，避免大段文字堆叠。
- 有并列信息时优先使用短列表或编号列表。
- 有明确主题时使用简短小标题；内容很短时不要强行加标题。
- 代码、命令、JSON、SQL 等结构化内容使用 Markdown 代码块。
- 只要给出可复制执行的命令、终端输入、配置片段或脚本，必须用 fenced code block 包裹，并尽量标注语言，如 ```powershell```、```bash```、```json```。
- 用户明确要求纯文本、原样输出或指定格式时，优先严格遵循用户要求。
- 保持表达简洁准确，不为了排版额外添加空话。
"""

private const val DefaultSpecialPlayPrompt = """
你支持一个仅限聊天内展示的“转账”特殊玩法。
- 当你决定给用户转账时，必须输出一条 XML 自闭合标签：
  <transfer id="唯一ID" direction="assistant_to_user" amount="88.00" counterparty="用户" note="备注" />
- 当你确认已经收下用户之前发来的转账时，必须输出：
  <transfer-update ref="之前的转账ID" status="received" />
- 除了 transfer 和 transfer-update，不要输出任何其他玩法标签。
- 标签不要放进代码块，不要解释标签语法本身。
- 可以同时输出正常对话正文，界面会把标签渲染成玩法卡片。
"""

private val TransferTagRegex = Regex("""<transfer\s+([^>]+)/>""")
private val TransferUpdateTagRegex = Regex("""<transfer-update\s+([^>]+)/>""")
private val XmlAttributeRegex = Regex("(\\w+)=\"([^\"]*)\"")

data class ParsedAssistantSpecialOutput(
    val content: String,
    val parts: List<ChatMessagePart>,
    val transferUpdates: List<TransferUpdateDirective> = emptyList(),
)

data class TransferUpdateDirective(
    val refId: String,
    val status: TransferStatus,
)

data class StructuredMemoryExtractionResult(
    val persistentMemories: List<String> = emptyList(),
    val sceneStateMemories: List<String> = emptyList(),
)

class AiRepository(
    private val settingsStore: SettingsStore,
    private val apiServiceFactory: ApiServiceFactory,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        apiServiceFactory.create(
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
    private val imagePayloadResolver: suspend (MessageAttachment) -> String = {
        throw IllegalStateException("当前环境不支持图片发送")
    },
    private val filePromptResolver: suspend (MessageAttachment) -> String = {
        throw IllegalStateException("当前环境不支持文件发送")
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val gson = Gson()

    val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow

    suspend fun fetchModels(
        baseUrl: String,
        apiKey: String,
    ): List<String> {
        require(baseUrl.isNotBlank()) { "请先填写 Base URL" }
        require(apiKey.isNotBlank()) { "请先填写 API Key" }

        val response = runNetworkCall {
            apiServiceProvider(
                baseUrl,
                apiKey.trim(),
            ).listModels()
        }
        if (!response.isSuccessful) {
            val errorDetail = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(
                "模型拉取失败：${response.code()}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
            )
        }

        val models = response.body()?.data.orEmpty().map { it.id }.filter { it.isNotBlank() }
        if (models.isEmpty()) {
            throw IllegalStateException("未获取到可用模型")
        }
        return models
    }

    /** 拉取模型列表并返回带能力推断的 ModelInfo 列表。 */
    suspend fun fetchModelInfos(
        baseUrl: String,
        apiKey: String,
    ): List<ModelInfo> {
        val modelIds = fetchModels(baseUrl, apiKey)
        return modelIds.map(::inferredModelInfo)
    }

    suspend fun saveSettings(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
    ) {
        settingsStore.saveSettings(
            baseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl),
            apiKey = apiKey.trim(),
            selectedModel = selectedModel.trim(),
        )
    }

    suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) {
        val normalizedProviders = providers.map { provider ->
            if (provider.baseUrl.isNotBlank()) {
                provider.copy(baseUrl = apiServiceFactory.normalizeBaseUrl(provider.baseUrl))
            } else {
                provider
            }
        }
        settingsStore.saveProviderSettings(
            providers = normalizedProviders,
            selectedProviderId = selectedProviderId,
        )
    }

    suspend fun saveDisplaySettings(
        themeMode: ThemeMode,
        messageTextScale: Float,
        reasoningExpandedByDefault: Boolean,
        showThinkingContent: Boolean,
        autoCollapseThinking: Boolean,
        autoPreviewImages: Boolean,
        codeBlockAutoWrap: Boolean,
        codeBlockAutoCollapse: Boolean,
    ) {
        settingsStore.saveDisplaySettings(
            themeMode = themeMode,
            messageTextScale = messageTextScale,
            reasoningExpandedByDefault = reasoningExpandedByDefault,
            showThinkingContent = showThinkingContent,
            autoCollapseThinking = autoCollapseThinking,
            autoPreviewImages = autoPreviewImages,
            codeBlockAutoWrap = codeBlockAutoWrap,
            codeBlockAutoCollapse = codeBlockAutoCollapse,
        )
    }

    suspend fun saveScreenTranslationSettings(
        settings: com.example.myapplication.model.ScreenTranslationSettings,
    ) {
        settingsStore.saveScreenTranslationSettings(settings)
    }

    suspend fun saveUserProfile(
        displayName: String,
        avatarUri: String,
        avatarUrl: String,
    ) {
        settingsStore.saveUserProfile(
            displayName = displayName.trim(),
            avatarUri = avatarUri.trim(),
            avatarUrl = avatarUrl.trim(),
        )
    }

    suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    ) {
        settingsStore.saveAssistants(
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
        )
    }

    suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) {
        settingsStore.saveTranslationHistory(history)
    }

    /** 调用 /images/generations 端点生成图片，返回 base64 数据列表。 */
    suspend fun generateImage(prompt: String): List<ImageGenerationResult> {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel

        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey).generateImage(
                ImageGenerationRequest(
                    model = selectedModel,
                    prompt = prompt,
                    n = 1,
                    responseFormat = "b64_json",
                ),
            )
        }

        if (!response.isSuccessful) {
            val errorDetail = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(
                "图片生成失败：${response.code()}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
            )
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

    /** 使用指定模型生成对话标题摘要。 */
    suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): String {
        val prompt = "请用不超过15个字总结以下对话的主题，只输出标题文字，不要带引号或标点：\n$firstUserMessage"
        val requestMessages = listOf(
            ChatMessageDto(
                role = "user",
                content = prompt,
            ),
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("标题生成失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("标题模型未返回有效内容")
        }
        return content.take(20)
    }

    /** 使用指定模型生成聊天建议。 */
    suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): List<String> {
        val prompt = "基于以下对话，生成3个简短的后续问题建议，每个建议不超过20个字，用换行分隔，只输出建议文字：\n$conversationSummary"
        val requestMessages = listOf(
            ChatMessageDto(
                role = "user",
                content = prompt,
            ),
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("建议生成失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            return emptyList()
        }
        return content.lines()
            .map { it.trim().removePrefix("-").removePrefix("·").trim() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(3)
    }

    suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): String {
        val prompt = buildString {
            append("请把下面的对话压缩成一段简洁摘要，保留关键事实、人物关系、目标、进度和未完成事项。")
            append("输出使用简体中文，控制在 300 字以内，不要添加标题：\n")
            append(conversationText)
        }
        val requestMessages = listOf(
            ChatMessageDto(
                role = "user",
                content = prompt,
            ),
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("摘要生成失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("摘要模型未返回有效内容")
        }
        return content.take(500)
    }

    suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): List<String> {
        val prompt = buildString {
            append("你是对话长期记忆提取器。")
            append("请从下面的最近对话中提取适合长期保存的内容，例如：用户偏好、稳定设定、人物关系、长期目标、持续约束。")
            append("忽略寒暄、一次性任务、临时情绪和重复信息。")
            append("如果没有值得记忆的内容，返回 []。")
            append("只输出 JSON 数组，每项都是一条简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
        val requestMessages = listOf(
            ChatMessageDto(
                role = "user",
                content = prompt,
            ),
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("记忆提取失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            return emptyList()
        }
        val parsedArray = runCatching {
            JsonParser.parseString(content).asJsonArray.mapNotNull { element ->
                runCatching { element.asString.trim() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
        if (parsedArray != null) {
            return parsedArray.distinct().take(3)
        }
        return content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(3)
    }

    suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): StructuredMemoryExtractionResult {
        val prompt = buildString {
            append("你是沉浸式剧情记忆提取器。")
            append("请从下面的剧情记录中提取两类记忆。")
            append("persistent_memories 用来保存长期稳定事实、关系、偏好和设定。")
            append("scene_state_memories 用来保存当前剧情线的地点、任务进度、关键事件、关系阶段和线索。")
            append("忽略寒暄、重复信息、一次性情绪和无长期价值的细节。")
            append("若某一类没有内容，返回空数组。")
            append("只输出 JSON 对象：")
            append("{\"persistent_memories\":[...],\"scene_state_memories\":[...]}。")
            append("每项都必须是简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
        val requestMessages = listOf(
            ChatMessageDto(
                role = "user",
                content = prompt,
            ),
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("RP 记忆提取失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            return StructuredMemoryExtractionResult()
        }

        val parsedJson = runCatching { JsonParser.parseString(content).asJsonObject }.getOrNull()
        if (parsedJson != null) {
            return StructuredMemoryExtractionResult(
                persistentMemories = parsedJson["persistent_memories"]
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { element -> runCatching { element.asString.trim() }.getOrNull() }
                    .orEmpty()
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(4),
                sceneStateMemories = parsedJson["scene_state_memories"]
                    ?.takeIf { it.isJsonArray }
                    ?.asJsonArray
                    ?.mapNotNull { element -> runCatching { element.asString.trim() }.getOrNull() }
                    .orEmpty()
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .take(6),
            )
        }

        val fallbackItems = content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .distinct()
        return StructuredMemoryExtractionResult(
            persistentMemories = fallbackItems.take(2),
            sceneStateMemories = fallbackItems.drop(2).take(4),
        )
    }

    suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
    ): List<RoleplaySuggestionUiModel> {
        val requestMessages = buildRoleplaySuggestionMessages(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = systemPrompt,
        )
        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            throw IllegalStateException("RP 建议生成失败：${response.code()}")
        }
        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            return emptyList()
        }
        return parseRoleplaySuggestionsContent(content)
    }

    suspend fun translateText(
        text: String,
        targetLanguage: String = "简体中文",
        sourceLanguage: String = "自动检测",
    ): String {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = activeProvider?.resolveFunctionModel(ProviderFunction.TRANSLATION)
            ?: settings.selectedModel

        val requestMessages = buildTranslationRequestMessages(
            text = text,
            targetLanguage = targetLanguage,
            sourceLanguage = sourceLanguage,
        )

        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = requestMessages,
                ),
            )
        }
        if (!response.isSuccessful) {
            val errorDetail = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(
                "翻译失败：${response.code()}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
            )
        }

        val content = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("翻译模型未返回有效内容")
        }
        return content
    }

    fun translateTextStream(
        text: String,
        targetLanguage: String = "简体中文",
        sourceLanguage: String = "自动检测",
    ): Flow<String> = flow {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = activeProvider?.resolveFunctionModel(ProviderFunction.TRANSLATION)
            ?: settings.selectedModel

        val requestBody = ChatCompletionRequest(
            model = modelId,
            messages = buildTranslationRequestMessages(
                text = text,
                targetLanguage = targetLanguage,
                sourceLanguage = sourceLanguage,
            ),
            stream = true,
        )
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl)
        val httpRequest = Request.Builder()
            .url("${normalizedBaseUrl}chat/completions")
            .post(gson.toJson(requestBody).toRequestBody("application/json".toMediaType()))
            .build()

        val client = streamClientProvider(baseUrl, apiKey)
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
                val errorDetail = response.body?.string().orEmpty()
                throw IllegalStateException(
                    "翻译失败：${response.code}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
                )
            }

            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")
            val builder = StringBuilder()
            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val chunk = runCatching {
                    gson.fromJson(data, ChatCompletionChunk::class.java)
                }.getOrNull() ?: continue
                val delta = chunk.choices.firstOrNull()?.delta?.content.orEmpty()
                if (delta.isNotBlank()) {
                    builder.append(delta)
                    emit(builder.toString())
                }
            }

            if (builder.isBlank()) {
                throw IllegalStateException("翻译模型未返回有效内容")
            }
        } catch (e: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("翻译请求已取消").apply { initCause(e) }
            }
            throw e.toReadableNetworkException()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e.toReadableNetworkException()
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }.flowOn(ioDispatcher)

    suspend fun translateStructuredSegments(
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

        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val modelId = activeProvider?.resolveFunctionModel(ProviderFunction.TRANSLATION)
            ?: settings.selectedModel

        val response = runNetworkCall {
            apiServiceProvider(baseUrl, apiKey.trim()).createChatCompletion(
                ChatCompletionRequest(
                    model = modelId,
                    messages = buildStructuredTranslationMessages(
                        targetLanguage = request.targetLanguage,
                        segments = normalizedSegments,
                    ),
                ),
            )
        }
        if (!response.isSuccessful) {
            val errorDetail = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(
                "翻译失败：${response.code()}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
            )
        }

        val rawContent = extractContentText(
            response.body()?.choices?.firstOrNull()?.message?.content,
        ).trim()
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

    suspend fun sendMessage(messages: List<ChatMessage>, systemPrompt: String = ""): AssistantReply {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)

        val requestMessages = toRequestMessages(messages, systemPrompt)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }

        val response = runNetworkCall {
            apiServiceProvider(
                baseUrl,
                apiKey,
            ).createChatCompletion(
                ChatCompletionRequest(
                    model = selectedModel,
                    messages = requestMessages,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    thinking = thinkingRequestConfig.thinking,
                ),
            )
        }

        if (!response.isSuccessful) {
            val errorDetail = response.errorBody()?.string().orEmpty()
            throw IllegalStateException(
                "聊天请求失败：${response.code()}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
            )
        }

        val assistantMessage = response.body()
            ?.choices
            ?.firstOrNull()
            ?.message
        val extractedOutput = extractAssistantOutput(assistantMessage)
        val content = extractedOutput.content
        val reasoning = extractedOutput.reasoning

        if (content.isBlank() && extractedOutput.parts.isEmpty()) {
            throw IllegalStateException("模型未返回有效内容")
        }

        return AssistantReply(
            content = content,
            reasoningContent = reasoning,
            parts = extractedOutput.parts,
        )
    }

    fun sendMessageStream(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
    ): Flow<ChatStreamEvent> = flow {
        val settings = settingsStore.settingsFlow.first()
        require(settings.hasRequiredConfig()) { "请先完成设置并选择模型" }
        val activeProvider = settings.activeProvider()
        val baseUrl = activeProvider?.baseUrl ?: settings.baseUrl
        val apiKey = activeProvider?.apiKey ?: settings.apiKey
        val selectedModel = activeProvider?.selectedModel ?: settings.selectedModel
        val thinkingRequestConfig = buildThinkingRequestConfig(activeProvider, selectedModel)

        val requestMessages = toRequestMessages(messages, systemPrompt, promptMode)
        require(requestMessages.isNotEmpty()) { "消息不能为空" }

        val requestBody = ChatCompletionRequest(
            model = selectedModel,
            messages = requestMessages,
            stream = true,
            reasoningEffort = thinkingRequestConfig.reasoningEffort,
            thinking = thinkingRequestConfig.thinking,
        )
        val jsonBody = gson.toJson(requestBody)
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl)

        val httpRequest = Request.Builder()
            .url("${normalizedBaseUrl}chat/completions")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .build()

        val client = streamClientProvider(baseUrl, apiKey)
        val call = client.newCall(httpRequest)
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
            if (!response.isSuccessful) {
                val errorDetail = response.body?.string().orEmpty()
                throw IllegalStateException(
                    "聊天请求失败：${response.code}${if (errorDetail.isNotBlank()) "\n$errorDetail" else ""}",
                )
            }

            val source = response.body?.source()
                ?: throw IllegalStateException("响应体为空")

            while (!source.exhausted()) {
                coroutineContext.ensureActive()
                val line = source.readUtf8Line() ?: break
                if (line.isBlank() || !line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break

                val chunk = try {
                    gson.fromJson(data, ChatCompletionChunk::class.java)
                } catch (_: Exception) {
                    null
                } ?: continue

                val delta = chunk.choices.firstOrNull()?.delta
                val reasoningDelta = delta.extractReasoning()
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
                extractAssistantImageParts(delta?.images).forEach { imagePart ->
                    emit(ChatStreamEvent.ImageDelta(imagePart))
                }
            }
            if (thinkTagParser.hasPending()) {
                val trailingOutput = thinkTagParser.flush()
                if (trailingOutput.reasoning.isNotBlank()) {
                    emit(ChatStreamEvent.ReasoningDelta(trailingOutput.reasoning))
                }
                if (trailingOutput.content.isNotBlank()) {
                    emit(ChatStreamEvent.ContentDelta(trailingOutput.content))
                }
            }
            emit(ChatStreamEvent.Completed)
        } catch (e: IOException) {
            if (call.isCanceled() || coroutineJob?.isActive == false) {
                throw CancellationException("聊天请求已取消").apply { initCause(e) }
            }
            throw e.toReadableNetworkException()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e.toReadableNetworkException()
        } finally {
            cancelHandle?.dispose()
            call.cancel()
            response?.close()
        }
    }.flowOn(ioDispatcher)

    private suspend fun <T> runNetworkCall(block: suspend () -> T): T {
        return try {
            block()
        } catch (exception: Exception) {
            throw exception.toReadableNetworkException()
        }
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

    private suspend fun toRequestMessages(
        messages: List<ChatMessage>,
        systemPrompt: String = "",
        promptMode: PromptMode = PromptMode.CHAT,
    ): List<ChatMessageDto> {
        val effectiveSystemPrompt = buildSystemPrompt(systemPrompt, promptMode)
        return buildList {
            if (effectiveSystemPrompt.isNotBlank()) {
                add(ChatMessageDto(role = "system", content = effectiveSystemPrompt))
            }
            messages
                .filter { it.status == MessageStatus.COMPLETED && it.hasSendableContent() }
                .forEach { message ->
                    val requestContent = buildRequestContent(message) ?: return@forEach
                    add(
                        ChatMessageDto(
                            role = if (message.role == MessageRole.USER) "user" else "assistant",
                            content = requestContent,
                        ),
                    )
                }
        }
    }

    private fun buildSystemPrompt(
        systemPrompt: String,
        promptMode: PromptMode,
    ): String {
        val customPrompt = systemPrompt.trim()
        return when (promptMode) {
            PromptMode.ROLEPLAY -> customPrompt
            PromptMode.CHAT -> {
                if (customPrompt.isBlank()) {
                    "$DefaultChatFormattingPrompt\n\n$DefaultSpecialPlayPrompt"
                } else {
                    "$customPrompt\n\n$DefaultChatFormattingPrompt\n\n$DefaultSpecialPlayPrompt"
                }
            }
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

    private fun buildRoleplaySuggestionMessages(
        conversationExcerpt: String,
        systemPrompt: String,
    ): List<ChatMessageDto> {
        return listOf(
            ChatMessageDto(
                role = "system",
                content = buildString {
                    append("你是沉浸式剧情输入建议生成器。")
                    append("你的任务不是继续扮演角色，而是基于当前剧情上下文，为玩家/用户这一侧生成 3 条可直接发送的下一步输入建议。")
                    append("所有建议必须站在用户视角书写，不能替角色发言，不能输出剧情总结或解释。")
                    append("必须尽量保持玩家最近几轮的说话口吻、人设和情绪状态一致。")
                    append("建议风格要有差异，至少覆盖：推进剧情、探索信息、推动情绪/关系。")
                    append("建议内容可以同时包含动作描写和对话；如果包含动作描写，优先使用 *动作* 这种写法。")
                    append("每条建议控制在 1~3 句。")
                    append("不要输出 Markdown，不要输出代码块，不要输出额外解释。")
                    append("只输出 JSON 数组，格式固定为：")
                    append("[{\"label\":\"试探推进\",\"text\":\"...\"},{\"label\":\"信息探索\",\"text\":\"...\"},{\"label\":\"情绪拉扯\",\"text\":\"...\"}]")
                },
            ),
            ChatMessageDto(
                role = "user",
                content = buildString {
                    append("【剧情设定与上下文】\n")
                    append(systemPrompt.trim().ifBlank { "无" })
                    append("\n\n【最近剧情】\n")
                    append(conversationExcerpt.trim().ifBlank { "无" })
                    append("\n\n请基于以上内容生成 3 条“用户下一步输入建议”。")
                },
            ),
        )
    }

    private fun buildSpecialPlayPrompt(
        part: ChatMessagePart,
    ): String? {
        if (!part.isValidTransferPart()) {
            return null
        }
        val direction = when (part.specialDirection) {
            TransferDirection.USER_TO_ASSISTANT -> "user_to_assistant"
            TransferDirection.ASSISTANT_TO_USER -> "assistant_to_user"
            null -> return null
        }
        val status = when (part.specialStatus) {
            TransferStatus.PENDING -> "pending"
            TransferStatus.RECEIVED -> "received"
            null -> return null
        }
        val escapedCounterparty = part.specialCounterparty.escapeXmlAttribute()
        val escapedAmount = part.specialAmount.escapeXmlAttribute()
        val escapedNote = part.specialNote.escapeXmlAttribute()
        return buildString {
            append("<transfer")
            append(" id=\"").append(part.specialId.escapeXmlAttribute()).append('"')
            append(" direction=\"").append(direction).append('"')
            append(" amount=\"").append(escapedAmount).append('"')
            append(" counterparty=\"").append(escapedCounterparty).append('"')
            append(" note=\"").append(escapedNote).append('"')
            append(" status=\"").append(status).append("\" />")
        }
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

    private fun parseRoleplaySuggestionsContent(
        rawContent: String,
    ): List<RoleplaySuggestionUiModel> {
        val normalized = unwrapJsonCodeFence(rawContent)
        parseRoleplaySuggestionArray(normalized)?.let { parsed ->
            if (parsed.isNotEmpty()) {
                return parsed
            }
        }

        val blockCandidates = normalized
            .split(Regex("""\n\s*\n"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (blockCandidates.size >= 2) {
            val parsedBlocks = blockCandidates.mapIndexedNotNull { index, block ->
                parseFallbackRoleplaySuggestion(block, index)
            }
            if (parsedBlocks.isNotEmpty()) {
                return parsedBlocks.distinctBy { it.text }.take(3)
            }
        }

        return normalized.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .replace(Regex("""^\d+[\.\)、]\s*"""), "")
                    .trim()
            }
            .filter { it.isNotEmpty() }
            .mapIndexedNotNull { index, item ->
                parseFallbackRoleplaySuggestion(
                    rawItem = item,
                    index = index,
                )
            }
            .distinctBy { it.text }
            .take(3)
    }

    private fun parseRoleplaySuggestionArray(
        rawContent: String,
    ): List<RoleplaySuggestionUiModel>? {
        val parsedJson = runCatching { JsonParser.parseString(rawContent.trim()) }.getOrNull() ?: return null
        val suggestionArray = when {
            parsedJson.isJsonArray -> parsedJson.asJsonArray
            parsedJson.isJsonObject -> parsedJson.asJsonObject["suggestions"]
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
            else -> null
        } ?: return null

        return suggestionArray.mapIndexedNotNull { index, element ->
            val suggestionObject = runCatching { element.asJsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val text = suggestionObject["text"]
                ?.let { value -> runCatching { value.asString }.getOrNull() }
                .orEmpty()
                .trim()
            if (text.isBlank()) {
                return@mapIndexedNotNull null
            }
            val label = suggestionObject["label"]
                ?.let { value -> runCatching { value.asString }.getOrNull() }
                .orEmpty()
                .trim()
                .ifBlank { defaultRoleplaySuggestionLabel(index) }
            RoleplaySuggestionUiModel(
                id = buildRoleplaySuggestionId(index),
                label = label,
                text = text,
            )
        }.distinctBy { it.text }.take(3)
    }

    private fun parseFallbackRoleplaySuggestion(
        rawItem: String,
        index: Int,
    ): RoleplaySuggestionUiModel? {
        val normalizedItem = rawItem
            .replace("\r\n", "\n")
            .trim()
            .removePrefix("-")
            .removePrefix("•")
            .replace(Regex("""^\d+[\.\)、]\s*"""), "")
            .trim()
        if (normalizedItem.isBlank()) {
            return null
        }

        val labelMatch = Regex("""^\s*([^：:\n]{2,12})[：:]\s*(.+)$""", RegexOption.DOT_MATCHES_ALL)
            .find(normalizedItem)
        val label = labelMatch?.groupValues?.get(1)?.trim().orEmpty()
        val text = (labelMatch?.groupValues?.get(2) ?: normalizedItem)
            .replace("\n", " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (text.isBlank()) {
            return null
        }
        return RoleplaySuggestionUiModel(
            id = buildRoleplaySuggestionId(index),
            label = label.ifBlank { defaultRoleplaySuggestionLabel(index) },
            text = text,
        )
    }

    private fun unwrapJsonCodeFence(rawContent: String): String {
        val trimmed = rawContent.trim()
        val match = Regex("""^```(?:json)?\s*([\s\S]*?)\s*```$""")
            .find(trimmed)
        return match?.groupValues?.get(1)?.trim() ?: trimmed
    }

    private fun buildRoleplaySuggestionId(index: Int): String {
        return "roleplay-suggestion-${index + 1}"
    }

    private fun defaultRoleplaySuggestionLabel(index: Int): String {
        return when (index) {
            0 -> "试探推进"
            1 -> "信息探索"
            else -> "情绪拉扯"
        }
    }

    private fun String.parseXmlAttributes(): Map<String, String> {
        return XmlAttributeRegex.findAll(this)
            .associate { match ->
                match.groupValues[1] to match.groupValues[2]
            }
    }

    private fun String.escapeXmlAttribute(): String {
        return this
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private suspend fun buildRequestContent(message: ChatMessage): Any? {
        if (message.role != MessageRole.USER) {
            val assistantParts = normalizeChatMessageParts(message.parts)
            if (assistantParts.isNotEmpty()) {
                val assistantContext = assistantParts.mapNotNull { part ->
                    when (part.type) {
                        ChatMessagePartType.TEXT -> part.text.takeIf { it.isNotBlank() }
                        ChatMessagePartType.SPECIAL -> buildSpecialPlayPrompt(part)
                        ChatMessagePartType.IMAGE,
                        ChatMessagePartType.FILE,
                        -> null
                    }
                }.joinToString(separator = "\n\n").trim()
                if (assistantContext.isNotBlank()) {
                    return assistantContext
                }
            }
            return message.content
                .ifBlank { message.parts.toPlainText() }
                .takeIf { it.isNotBlank() }
        }

        val userParts = buildRequestMessageParts(message)
        if (userParts.isEmpty()) {
            return message.content.takeIf { it.isNotBlank() }
        }

        val hasNonTextPart = userParts.any { it.type != ChatMessagePartType.TEXT }
        if (!hasNonTextPart) {
            return userParts.toPlainText().takeIf { it.isNotBlank() }
        }

        return buildList {
            userParts.forEach { part ->
                when (part.type) {
                    ChatMessagePartType.TEXT -> {
                        if (part.text.isNotBlank()) {
                            add(TextContentPartDto(text = part.text))
                        }
                    }

                    ChatMessagePartType.IMAGE -> {
                        val attachment = part.toMessageAttachmentOrNull()
                            ?: return@forEach
                        val url = imagePayloadResolver(attachment)
                        add(
                            ImageUrlContentPartDto(
                                imageUrl = ImageUrlDto(url = url),
                            ),
                        )
                    }

                    ChatMessagePartType.FILE -> {
                        val attachment = part.toMessageAttachmentOrNull()
                            ?: return@forEach
                        add(TextContentPartDto(text = filePromptResolver(attachment)))
                    }

                    ChatMessagePartType.SPECIAL -> {
                        buildSpecialPlayPrompt(part)?.let { prompt ->
                            add(TextContentPartDto(text = prompt))
                        }
                    }
                }
            }
        }.takeIf { it.isNotEmpty() }
    }

    fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
    ): ParsedAssistantSpecialOutput {
        val preservedNonTextParts = normalizeChatMessageParts(existingParts)
            .filter { part -> part.type != ChatMessagePartType.TEXT }
        if (content.isBlank()) {
            return ParsedAssistantSpecialOutput(
                content = "",
                parts = preservedNonTextParts,
            )
        }

        val renderedParts = mutableListOf<ChatMessagePart>()
        val transferUpdates = mutableListOf<TransferUpdateDirective>()
        var cursor = 0
        val matches = buildList {
            addAll(TransferTagRegex.findAll(content))
            addAll(TransferUpdateTagRegex.findAll(content))
        }.sortedBy { it.range.first }

        matches.forEach { match ->
            val range = match.range
            if (range.first > cursor) {
                val prefix = content.substring(cursor, range.first)
                if (prefix.isNotBlank()) {
                    renderedParts += textMessagePart(prefix.trim())
                }
            }

            val rawAttributes = match.groupValues.getOrNull(1).orEmpty()
            val attributes = rawAttributes.parseXmlAttributes()
            when {
                match.value.startsWith("<transfer-update") -> {
                    val refId = attributes["ref"].orEmpty().trim()
                    val status = attributes["status"].orEmpty().trim()
                    if (refId.isNotBlank() && status == "received") {
                        transferUpdates += TransferUpdateDirective(
                            refId = refId,
                            status = TransferStatus.RECEIVED,
                        )
                    }
                }

                match.value.startsWith("<transfer") -> {
                    val direction = when (attributes["direction"].orEmpty().trim()) {
                        "assistant_to_user" -> TransferDirection.ASSISTANT_TO_USER
                        "user_to_assistant" -> TransferDirection.USER_TO_ASSISTANT
                        else -> null
                    }
                    if (direction != null) {
                        transferMessagePart(
                            id = attributes["id"].orEmpty().ifBlank { java.util.UUID.randomUUID().toString() },
                            direction = direction,
                            status = when (attributes["status"].orEmpty().trim()) {
                                "received" -> TransferStatus.RECEIVED
                                else -> TransferStatus.PENDING
                            },
                            counterparty = attributes["counterparty"].orEmpty().ifBlank { "对方" },
                            amount = attributes["amount"].orEmpty(),
                            note = attributes["note"].orEmpty(),
                        ).takeIf { it.isValidTransferPart() }?.let(renderedParts::add)
                    }
                }
            }

            cursor = range.last + 1
        }

        if (cursor < content.length) {
            val suffix = content.substring(cursor)
            if (suffix.isNotBlank()) {
                renderedParts += textMessagePart(suffix.trim())
            }
        }

        val normalizedVisibleParts = normalizeChatMessageParts(renderedParts)
        val visibleContent = normalizedVisibleParts.toPlainText()
        return ParsedAssistantSpecialOutput(
            content = visibleContent,
            parts = normalizeChatMessageParts(normalizedVisibleParts + preservedNonTextParts),
            transferUpdates = transferUpdates,
        )
    }

    private fun buildRequestMessageParts(message: ChatMessage): List<ChatMessagePart> {
        if (message.parts.isNotEmpty()) {
            return normalizeChatMessageParts(message.parts)
        }

        if (message.attachments.isEmpty()) {
            return if (message.content.isBlank()) {
                emptyList()
            } else {
                listOf(textMessagePart(message.content))
            }
        }

        return normalizeChatMessageParts(
            buildList {
                if (message.content.isNotBlank()) {
                    add(textMessagePart(message.content))
                }
                addAll(message.attachments.map { attachment -> attachment.toChatMessagePart() })
            },
        )
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

    private fun extractAssistantImagePart(contentPart: Map<*, *>): ChatMessagePart? {
        val nestedImageUrl = contentPart["image_url"] as? Map<*, *>
        val nestedFile = contentPart["file"] as? Map<*, *>
        val url = listOfNotNull(
            nestedImageUrl?.get("url") as? String,
            nestedFile?.get("url") as? String,
            contentPart["url"] as? String,
        ).firstOrNull { it.isNotBlank() }
        if (!url.isNullOrBlank()) {
            return imageMessagePart(uri = url)
        }

        val inlineDataUrl = contentPart["data"] as? String
        if (!inlineDataUrl.isNullOrBlank() && inlineDataUrl.startsWith("data:image", ignoreCase = true)) {
            return imageMessagePart(uri = inlineDataUrl)
        }

        val base64 = contentPart["b64_json"] as? String
        if (!base64.isNullOrBlank()) {
            val mimeType = (contentPart["mime_type"] as? String).orEmpty().ifBlank { "image/png" }
            return imageMessagePart(
                uri = "data:$mimeType;base64,$base64",
                mimeType = mimeType,
            )
        }

        return null
    }

    private fun extractAssistantOutput(
        assistantMessage: com.example.myapplication.model.AssistantMessageDto?,
    ): ParsedAssistantOutput {
        val reasoning = assistantMessage.extractReasoning().trim()
        val contentParts = extractAssistantContentParts(assistantMessage?.content)
        val mergedParts = mergeAssistantParts(
            contentParts = contentParts,
            topLevelImages = extractAssistantImageParts(assistantMessage?.images),
        )
        if (reasoning.isNotBlank()) {
            return ParsedAssistantOutput(
                content = mergedParts.toContentMirror(),
                reasoning = reasoning,
                parts = mergedParts,
            )
        }
        return splitThinkTaggedParts(mergedParts)
    }

    private fun com.example.myapplication.model.AssistantMessageDto?.extractReasoning(): String {
        return this?.reasoningContent
            ?: this?.reasoning
            ?: this?.thinking
            ?: ""
    }

    private fun com.example.myapplication.model.ChatDeltaDto?.extractReasoning(): String {
        return this?.reasoningContent
            ?: this?.reasoning
            ?: this?.thinking
            ?: ""
    }

    private fun extractAssistantContentParts(content: Any?): List<ChatMessagePart> {
        val parts = when (content) {
            null -> emptyList()
            is String -> listOf(textMessagePart(content))
            is List<*> -> content.flatMap(::extractAssistantContentPart)
            is Map<*, *> -> extractAssistantContentPart(content)
            else -> emptyList()
        }
        return normalizeChatMessageParts(parts)
    }

    private fun extractAssistantContentPart(contentPart: Any?): List<ChatMessagePart> {
        return when (contentPart) {
            is String -> listOf(textMessagePart(contentPart))
            is Map<*, *> -> buildList {
                val text = (contentPart["text"] as? String).orEmpty()
                if (text.isNotBlank()) {
                    add(textMessagePart(text))
                }
                extractAssistantImagePart(contentPart)?.let(::add)
            }

            else -> emptyList()
        }
    }

    private fun extractAssistantImageParts(
        imageParts: List<com.example.myapplication.model.AssistantImagePartDto>?,
    ): List<ChatMessagePart> {
        return imageParts.orEmpty()
            .mapNotNull(::extractAssistantImagePart)
            .distinctBy { "${it.type}:${it.uri}" }
    }

    private fun extractAssistantImagePart(
        imagePart: com.example.myapplication.model.AssistantImagePartDto,
    ): ChatMessagePart? {
        val directUrl = imagePart.imageUrl?.url
            ?: imagePart.url
        if (!directUrl.isNullOrBlank()) {
            return imageMessagePart(
                uri = directUrl,
                mimeType = imagePart.mimeType.orEmpty(),
            )
        }

        val base64 = imagePart.b64Json.orEmpty()
        if (base64.isBlank()) {
            return null
        }

        val mimeType = imagePart.mimeType.orEmpty().ifBlank { "image/png" }
        return imageMessagePart(
            uri = "data:$mimeType;base64,$base64",
            mimeType = mimeType,
        )
    }

    private fun mergeAssistantParts(
        contentParts: List<ChatMessagePart>,
        topLevelImages: List<ChatMessagePart>,
    ): List<ChatMessagePart> {
        if (topLevelImages.isEmpty()) {
            return normalizeChatMessageParts(contentParts)
        }

        val existingImageUris = contentParts
            .filter { it.type == ChatMessagePartType.IMAGE }
            .mapTo(mutableSetOf()) { it.uri }

        return normalizeChatMessageParts(
            contentParts + topLevelImages.filter { image ->
                image.uri.isNotBlank() && image.uri !in existingImageUris
            },
        )
    }
}

private data class ParsedAssistantOutput(
    val content: String,
    val reasoning: String,
    val parts: List<ChatMessagePart> = emptyList(),
)

private val ThinkTagRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)
private val ClosingThinkTagRegex = Regex("</think>")

private fun splitThinkTaggedContent(content: String): ParsedAssistantOutput {
    if (!content.contains("<think>")) {
        return ParsedAssistantOutput(
            content = content.trim(),
            reasoning = "",
        )
    }

    val reasoning = ThinkTagRegex.findAll(content)
        .mapNotNull { match ->
            match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
        }
        .joinToString(separator = "\n\n")

    val visibleContent = content
        .replace(ThinkTagRegex, "")
        .replace(ClosingThinkTagRegex, "")
        .trim()

    return ParsedAssistantOutput(
        content = visibleContent,
        reasoning = reasoning,
    )
}

private fun splitThinkTaggedParts(parts: List<ChatMessagePart>): ParsedAssistantOutput {
    val normalizedParts = normalizeChatMessageParts(parts)
    val splitText = splitThinkTaggedContent(normalizedParts.toPlainText())
    if (splitText.reasoning.isBlank()) {
        return ParsedAssistantOutput(
            content = normalizedParts.toContentMirror(),
            reasoning = "",
            parts = normalizedParts,
        )
    }

    val visibleParts = normalizeChatMessageParts(
        buildList {
            if (splitText.content.isNotBlank()) {
                add(textMessagePart(splitText.content))
            }
            addAll(normalizedParts.filter { it.type == ChatMessagePartType.IMAGE })
        },
    )
    return ParsedAssistantOutput(
        content = visibleParts.toContentMirror(),
        reasoning = splitText.reasoning,
        parts = visibleParts,
    )
}

private class ThinkTagStreamParser {
    private val pending = StringBuilder()
    private var insideThinking = false

    fun shouldConsume(delta: String): Boolean {
        return insideThinking ||
            pending.isNotEmpty() ||
            '<' in delta ||
            '>' in delta
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun consume(delta: String): ParsedAssistantOutput {
        if (delta.isEmpty()) {
            return ParsedAssistantOutput(content = "", reasoning = "")
        }

        pending.append(delta)
        val content = StringBuilder()
        val reasoning = StringBuilder()

        while (pending.isNotEmpty()) {
            if (insideThinking) {
                val closingIndex = pending.indexOf("</think>")
                if (closingIndex >= 0) {
                    reasoning.append(pending.substring(0, closingIndex))
                    pending.delete(0, closingIndex + "</think>".length)
                    insideThinking = false
                } else {
                    val safeLength = pending.length - ("</think>".length - 1)
                    if (safeLength > 0) {
                        reasoning.append(pending.substring(0, safeLength))
                        pending.delete(0, safeLength)
                    }
                    break
                }
            } else {
                val openingIndex = pending.indexOf("<think>")
                if (openingIndex >= 0) {
                    if (openingIndex > 0) {
                        content.append(pending.substring(0, openingIndex))
                    }
                    pending.delete(0, openingIndex + "<think>".length)
                    insideThinking = true
                } else {
                    val safeLength = pending.length - ("<think>".length - 1)
                    if (safeLength > 0) {
                        content.append(pending.substring(0, safeLength))
                        pending.delete(0, safeLength)
                    }
                    break
                }
            }
        }

        return ParsedAssistantOutput(
            content = content.toString(),
            reasoning = reasoning.toString(),
        )
    }

    fun flush(): ParsedAssistantOutput {
        if (pending.isEmpty()) {
            return ParsedAssistantOutput(content = "", reasoning = "")
        }

        val trailing = pending.toString()
        pending.clear()
        return if (insideThinking) {
            insideThinking = false
            ParsedAssistantOutput(
                content = "",
                reasoning = trailing,
            )
        } else {
            ParsedAssistantOutput(
                content = trailing,
                reasoning = "",
            )
        }
    }
}

data class ImageGenerationResult(
    val b64Data: String,
    val url: String,
    val revisedPrompt: String,
)
