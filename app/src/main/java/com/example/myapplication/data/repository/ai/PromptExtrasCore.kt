package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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

/**
 * 共享的 Prompt Extras 底座：封装网络请求、roleplay 采样参数兜底、markdown / JSON 提取等
 * 所有 prompt 子服务都复用的基础设施。
 *
 * T6 把原 [DefaultAiPromptExtrasService] 中的 ~200 行私有助手搬到这里，具体 prompt 构建留在
 * 各自领域的 `*PromptService`（Title / Summary / Memory / Suggestion / Diary / Phone）。
 *
 * 线程安全：[roleplaySamplingDisabledBaseUrls] 用同步 Set 记录哪些 baseUrl 已拒绝过采样参数；
 * 同一个实例被多个子服务共享，保证"拒绝记忆"跨服务生效。
 */
internal class PromptExtrasCore(
    private val apiServiceFactory: ApiServiceFactory,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi = { baseUrl, apiKey ->
        // Extras 走长超时客户端：非流式且一次性返回，思考模型 / 长上下文容易首字节 >120s。
        apiServiceFactory.createLongRunning(baseUrl = baseUrl, apiKey = apiKey)
    },
    private val anthropicApiProvider: (String, String) -> AnthropicApi = { baseUrl, apiKey ->
        apiServiceFactory.createLongRunningAnthropic(baseUrl = baseUrl, apiKey = apiKey)
    },
) {
    private val roleplaySamplingDisabledBaseUrls =
        Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun requestCompletionContent(
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

    fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        promptMode: PromptMode = PromptMode.ROLEPLAY,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(baseUrl, apiProtocol, promptMode)
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
        )
    }

    fun parseRequiredStructuredJsonObject(
        content: String,
        operation: String,
    ): JsonObject {
        if (content.isBlank()) {
            error("$operation：模型未返回任何内容")
        }
        return extractStructuredJsonObject(content)
            ?: error("$operation：模型返回格式不符合要求，未提取到合法 JSON 对象")
    }

    fun extractStructuredJsonObject(
        rawContent: String,
    ): JsonObject? {
        val candidate = extractFirstCompleteJsonObject(
            stripMarkdownCodeFence(rawContent),
        ) ?: return null
        return runCatching { JsonParser.parseString(candidate).asJsonObject }.getOrNull()
    }

    fun stripMarkdownCodeFence(rawContent: String): String {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("```")) {
            return trimmed
        }
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    fun extractFirstCompleteJsonObject(rawContent: String): String? {
        val startIndex = rawContent.indexOf('{')
        if (startIndex == -1) {
            return null
        }
        var inString = false
        var escaped = false
        var depth = 0
        for (index in startIndex until rawContent.length) {
            val char = rawContent[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return rawContent.substring(startIndex, index + 1)
                    }
                }
            }
        }
        return null
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

    private fun resolveRoleplaySampling(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        promptMode: PromptMode,
    ): PromptExtrasRoleplaySamplingConfig? {
        if (promptMode != PromptMode.ROLEPLAY) {
            return null
        }
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
}

// -- JsonObject 助手扩展（所有 prompt 子服务共享）-------------------------------------------------

internal fun JsonObject?.stringValue(key: String): String {
    if (this == null) {
        return ""
    }
    return this.get(key)
        ?.takeIf { !it.isJsonNull }
        ?.asString
        ?.trim()
        .orEmpty()
}

internal fun JsonObject?.booleanValue(key: String): Boolean {
    if (this == null) {
        return false
    }
    return this.get(key)
        ?.takeIf { !it.isJsonNull }
        ?.asBoolean
        ?: false
}

internal fun JsonObject.getAsJsonArrayOrNull(key: String): JsonArray? {
    val value = get(key) ?: return null
    return if (value.isJsonArray) value.asJsonArray else null
}

internal fun com.google.gson.JsonElement.asJsonObjectOrNull(): JsonObject? {
    return runCatching { asJsonObject }.getOrNull()
}
