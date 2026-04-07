package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ThinkingConfigDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

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

internal data class GatewayRoleplaySamplingConfig(
    val temperature: Float,
    val topP: Float,
)

/**
 * 无状态工具方法：构建请求对象、URL 拼接、角色扮演采样参数处理。
 */
internal object GatewayRequestSupport {

    fun buildStreamingRequest(
        fullUrl: String,
        requestBody: String,
    ): Request {
        return Request.Builder()
            .url(fullUrl)
            .post(requestBody.toRequestBody("application/json".toMediaType()))
            .build()
    }

    fun buildOpenAiTextUrl(
        baseUrl: String,
        provider: ProviderSettings?,
        apiServiceFactory: ApiServiceFactory,
    ): String {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, ProviderApiProtocol.OPENAI_COMPATIBLE)
        val path = when (provider?.resolvedOpenAiTextApiMode() ?: OpenAiTextApiMode.CHAT_COMPLETIONS) {
            OpenAiTextApiMode.CHAT_COMPLETIONS -> provider?.resolvedChatCompletionsPath() ?: DEFAULT_CHAT_COMPLETIONS_PATH
            OpenAiTextApiMode.RESPONSES -> "/responses"
        }
        return normalizedBaseUrl.removeSuffix("/") + path
    }

    fun buildRequestWithRoleplaySampling(
        model: String,
        messages: List<ChatMessageDto>,
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        apiServiceFactory: ApiServiceFactory,
        disabledBaseUrls: Set<String>,
        stream: Boolean = false,
        reasoningEffort: String? = null,
        thinking: ThinkingConfigDto? = null,
        promptMode: PromptMode = PromptMode.ROLEPLAY,
        tools: List<com.example.myapplication.model.ChatToolDto> = emptyList(),
        toolChoice: String? = null,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(baseUrl, apiProtocol, apiServiceFactory, disabledBaseUrls, promptMode)
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = stream,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
            reasoningEffort = reasoningEffort,
            thinking = thinking,
            tools = tools,
            toolChoice = toolChoice,
        )
    }

    fun resolveRoleplaySampling(
        baseUrl: String,
        apiProtocol: ProviderApiProtocol,
        apiServiceFactory: ApiServiceFactory,
        disabledBaseUrls: Set<String>,
        promptMode: PromptMode,
    ): GatewayRoleplaySamplingConfig? {
        if (promptMode != PromptMode.ROLEPLAY) {
            return null
        }
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
        if (disabledBaseUrls.contains(normalizedBaseUrl)) {
            return null
        }
        return GatewayRoleplaySamplingConfig(
            temperature = ROLEPLAY_TEMPERATURE,
            topP = ROLEPLAY_TOP_P,
        )
    }

    fun shouldRetryWithoutRoleplaySampling(
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
}
