package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.data.repository.StructuredMemoryExtractionResult
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.google.gson.JsonParser
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

private data class PromptExtrasRoleplaySamplingConfig(
    val temperature: Float,
    val topP: Float,
)

interface AiPromptExtrasService {
    suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>

    suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): String

    suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>

    suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): StructuredMemoryExtractionResult

    suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
        longformMode: Boolean = false,
    ): List<RoleplaySuggestionUiModel>

    suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        provider: ProviderSettings? = null,
    ): List<String>
}

class DefaultAiPromptExtrasService(
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
) : AiPromptExtrasService {
    private val roleplaySamplingDisabledBaseUrls = Collections.synchronizedSet(mutableSetOf<String>())

    override suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = "请用不超过15个字总结以下对话的主题，只输出标题文字，不要带引号或标点：\n$firstUserMessage"
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "标题生成失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        )
        if (content.isBlank()) {
            throw IllegalStateException("标题模型未返回有效内容")
        }
        return content.take(20)
    }

    override suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val prompt = "基于以下对话，生成3个简短的后续问题建议，每个建议不超过20个字，用换行分隔，只输出建议文字：\n$conversationSummary"
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "建议生成失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        )
        return content.lines()
            .map { it.trim().removePrefix("-").removePrefix("·").trim() }
            .filter { it.isNotBlank() && it.length <= 50 }
            .take(3)
    }

    override suspend fun generateConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = buildString {
            append("请把下面的对话压缩成一段简洁摘要，保留关键事实、人物关系、目标、进度和未完成事项。")
            append("输出使用简体中文，控制在 300 字以内，不要添加标题：\n")
            append(conversationText)
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "摘要生成失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("摘要模型未返回有效内容")
        }
        return content.take(500)
    }

    override suspend fun generateRoleplayConversationSummary(
        conversationText: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = buildString {
            append("你是沉浸式剧情摘要整理器。")
            append("请根据下面的剧情记录输出结构化摘要，帮助下一轮扮演保持连续性。")
            append("严格使用以下 5 个小节，每节 1 到 3 句：")
            append("【剧情进展】、【当前状态】、【关系变化】、【未解问题】、【近期触发点】。")
            append("保留明确人物关系、地点、任务进度、情绪转折和悬念。")
            append("不要输出 XML、不要解释规则、不要省略小节标题：\n")
            append(conversationText)
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 摘要生成失败",
            request = buildRequestWithRoleplaySampling(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        ).trim()
        if (content.isBlank()) {
            throw IllegalStateException("RP 摘要模型未返回有效内容")
        }
        return content.take(800)
    }

    override suspend fun generateMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val prompt = buildString {
            append("你是对话长期记忆提取器。")
            append("请从下面的最近对话中提取适合长期保存的内容，例如：用户偏好、稳定设定、人物关系、长期目标、持续约束。")
            append("忽略寒暄、一次性任务、临时情绪和重复信息。")
            append("如果没有值得记忆的内容，返回 []。")
            append("只输出 JSON 数组，每项都是一条简体中文短句，不要输出额外解释：\n")
            append(conversationExcerpt)
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "记忆提取失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
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

    override suspend fun generateRoleplayMemoryEntries(
        conversationExcerpt: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
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
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 记忆提取失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
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

    override suspend fun generateRoleplaySuggestions(
        conversationExcerpt: String,
        systemPrompt: String,
        playerStyleReference: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        longformMode: Boolean,
    ): List<RoleplaySuggestionUiModel> {
        val requestMessages = RoleplaySuggestionSupport.buildRequestMessages(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = systemPrompt,
            playerStyleReference = playerStyleReference,
            longformMode = longformMode,
        )
        val initialSuggestions = requestRoleplaySuggestions(
            requestMessages = requestMessages,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            provider = provider,
        )
        if (!RoleplaySuggestionSupport.shouldRetry(initialSuggestions)) {
            return initialSuggestions
        }
        val retryMessages = RoleplaySuggestionSupport.buildRequestMessages(
            conversationExcerpt = conversationExcerpt,
            systemPrompt = systemPrompt,
            playerStyleReference = playerStyleReference,
            longformMode = longformMode,
            rejectedSuggestions = initialSuggestions,
        )
        val retriedSuggestions = requestRoleplaySuggestions(
            requestMessages = retryMessages,
            baseUrl = baseUrl,
            apiKey = apiKey,
            modelId = modelId,
            apiProtocol = apiProtocol,
            provider = provider,
        )
        return retriedSuggestions.ifEmpty { initialSuggestions }
    }

    override suspend fun condenseRoleplayMemories(
        memoryItems: List<String>,
        mode: RoleplayMemoryCondenseMode,
        maxItems: Int,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val normalizedItems = memoryItems
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (normalizedItems.size <= maxItems.coerceAtLeast(1)) {
            return normalizedItems
        }
        val prompt = buildString {
            append(
                when (mode) {
                    RoleplayMemoryCondenseMode.CHARACTER -> {
                        "你是角色长期记忆整理器。请把下面多条零散记忆整理为更稳定、更少量的角色事实。"
                    }
                    RoleplayMemoryCondenseMode.SCENE -> {
                        "你是剧情状态记忆整理器。请把下面多条零散剧情状态整理为更稳定、更少量的场景事实。"
                    }
                },
            )
            append("去掉重复、近义改写和一次性废话，保留必须被后续对话遵守的信息。")
            append("输出不超过 ")
            append(maxItems.coerceAtLeast(1))
            append(" 条简体中文短句。只输出 JSON 数组，不要解释：\n")
            normalizedItems.forEach { item ->
                append("- ")
                append(item)
                append('\n')
            }
        }
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "记忆汇总失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "user",
                        content = prompt,
                    ),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        ).trim()
        if (content.isBlank()) {
            return normalizedItems.take(maxItems.coerceAtLeast(1))
        }
        val parsedArray = runCatching {
            JsonParser.parseString(content).asJsonArray.mapNotNull { element ->
                runCatching { element.asString.trim() }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }
            }
        }.getOrNull()
        return (parsedArray ?: content.lines()
            .map { line ->
                line.trim()
                    .removePrefix("-")
                    .removePrefix("•")
                    .trim()
            }
            .filter { it.isNotEmpty() })
            .distinct()
            .take(maxItems.coerceAtLeast(1))
    }

    private suspend fun requestCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
        allowRoleplaySamplingFallback: Boolean = false,
    ): String {
        return when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> requestOpenAiCompletionContent(
                baseUrl = baseUrl,
                apiKey = apiKey,
                operation = operation,
                request = request,
                provider = provider,
                allowRoleplaySamplingFallback = allowRoleplaySamplingFallback,
            )
            ProviderApiProtocol.ANTHROPIC -> requestAnthropicCompletionContent(
                baseUrl = baseUrl,
                apiKey = apiKey,
                operation = operation,
                request = request,
            )
        }
    }

    private suspend fun requestOpenAiCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
        provider: ProviderSettings?,
        allowRoleplaySamplingFallback: Boolean,
    ): String {
        val normalizedApiKey = apiKey.trim()
        var currentRequest = request
        val apiMode = provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        var response = if (apiMode == OpenAiTextApiMode.RESPONSES) {
            apiServiceProvider(baseUrl, normalizedApiKey).createResponseAt(
                buildOpenAiTextUrl(baseUrl, provider),
                ResponseApiSupport.buildRequest(currentRequest),
            )
        } else {
            apiServiceProvider(baseUrl, normalizedApiKey).createChatCompletionAt(
                buildOpenAiTextUrl(baseUrl, provider),
                currentRequest,
            )
        }
        var latestErrorDetail = if (!response.isSuccessful) {
            response.errorBody()?.string().orEmpty()
        } else {
            ""
        }
        if (apiMode == OpenAiTextApiMode.CHAT_COMPLETIONS &&
            allowRoleplaySamplingFallback &&
            response.code() == 400 &&
            shouldRetryWithoutRoleplaySampling(
                request = currentRequest,
                errorDetail = latestErrorDetail,
            )
        ) {
            markRoleplaySamplingUnsupported(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
            currentRequest = currentRequest.copy(
                temperature = null,
                topP = null,
            )
            response = apiServiceProvider(baseUrl, normalizedApiKey).createChatCompletionAt(
                buildOpenAiTextUrl(baseUrl, provider),
                currentRequest,
            )
            latestErrorDetail = if (!response.isSuccessful) {
                response.errorBody()?.string().orEmpty()
            } else {
                ""
            }
        }
        if (!response.isSuccessful) {
            throw PromptExtrasResponseSupport.buildHttpFailure(
                operation = operation,
                code = response.code(),
                errorDetail = latestErrorDetail,
                headers = response.headers(),
            )
        }
        return if (apiMode == OpenAiTextApiMode.RESPONSES) {
            val body = response.body() as? com.example.myapplication.model.ResponseApiResponse
                ?: throw IllegalStateException("$operation：响应体为空")
            ResponseApiSupport.parseResponse(body).content.trim()
        } else {
            val body = response.body() as? com.example.myapplication.model.ChatCompletionResponse
                ?: throw IllegalStateException("$operation：响应体为空")
            PromptExtrasResponseSupport.extractContentText(
                body.choices.firstOrNull()?.message?.content,
            ).trim()
        }
    }

    private suspend fun requestAnthropicCompletionContent(
        baseUrl: String,
        apiKey: String,
        operation: String,
        request: ChatCompletionRequest,
    ): String {
        val response = anthropicApiProvider(baseUrl, apiKey.trim()).createMessage(
            AnthropicProtocolSupport.buildMessageRequest(request),
        )
        if (!response.isSuccessful) {
            throw PromptExtrasResponseSupport.buildHttpFailure(
                operation = operation,
                code = response.code(),
                errorDetail = response.errorBody()?.string().orEmpty(),
                headers = response.headers(),
            )
        }
        val body = response.body() ?: throw IllegalStateException("$operation：响应体为空")
        return AnthropicProtocolSupport.extractContentText(body).trim()
    }

    private fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(baseUrl, apiProtocol)
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
        )
    }

    private fun resolveRoleplaySampling(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
    ): PromptExtrasRoleplaySamplingConfig? {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
        if (roleplaySamplingDisabledBaseUrls.contains(normalizedBaseUrl)) {
            return null
        }
        return PromptExtrasRoleplaySamplingConfig(
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

    private suspend fun requestRoleplaySuggestions(
        requestMessages: List<ChatMessageDto>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<RoleplaySuggestionUiModel> {
        val content = requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 建议生成失败",
            request = buildRequestWithRoleplaySampling(
                model = modelId,
                messages = requestMessages,
                baseUrl = baseUrl,
                apiProtocol = apiProtocol,
            ),
            apiProtocol = apiProtocol,
            provider = provider,
            allowRoleplaySamplingFallback = true,
        )
        return RoleplaySuggestionParser.parse(content)
    }

}
