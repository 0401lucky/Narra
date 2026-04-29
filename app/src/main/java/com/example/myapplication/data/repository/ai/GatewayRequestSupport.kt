package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PresetSamplerConfig
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
    "top_k",
    "min_p",
    "repetition_penalty",
    "frequency_penalty",
    "presence_penalty",
    "max_tokens",
    "max_output_tokens",
    "stop",
    "stop_sequences",
    "unknown parameter",
    "unknown field",
    "unrecognized field",
    "not permitted",
    "not allowed",
)

internal data class GatewayRoleplaySamplingConfig(
    val temperature: Float? = null,
    val topP: Float? = null,
    val topK: Int? = null,
    val minP: Float? = null,
    val repetitionPenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val presencePenalty: Float? = null,
    val maxOutputTokens: Int? = null,
    val stopSequences: List<String> = emptyList(),
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
        samplerOverride: PresetSamplerConfig = PresetSamplerConfig(),
        stopSequences: List<String> = emptyList(),
        tools: List<com.example.myapplication.model.ChatToolDto> = emptyList(),
        toolChoice: String? = null,
    ): ChatCompletionRequest {
        val sampling = resolveRoleplaySampling(
            baseUrl = baseUrl,
            apiProtocol = apiProtocol,
            apiServiceFactory = apiServiceFactory,
            disabledBaseUrls = disabledBaseUrls,
            promptMode = promptMode,
            samplerOverride = samplerOverride,
            stopSequences = stopSequences,
        )
        return ChatCompletionRequest(
            model = model,
            messages = messages,
            stream = stream,
            temperature = sampling?.temperature,
            topP = sampling?.topP,
            topK = sampling?.topK,
            minP = sampling?.minP,
            repetitionPenalty = sampling?.repetitionPenalty,
            frequencyPenalty = sampling?.frequencyPenalty,
            presencePenalty = sampling?.presencePenalty,
            maxTokens = sampling?.maxOutputTokens,
            stop = sampling?.stopSequences.orEmpty(),
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
        samplerOverride: PresetSamplerConfig = PresetSamplerConfig(),
        stopSequences: List<String> = emptyList(),
    ): GatewayRoleplaySamplingConfig? {
        val normalizedBaseUrl = apiServiceFactory.normalizeBaseUrl(baseUrl, apiProtocol)
        if (disabledBaseUrls.contains(normalizedBaseUrl)) {
            return null
        }
        val hasPresetSampler = samplerOverride.temperature != null ||
            samplerOverride.topP != null ||
            samplerOverride.topK != null ||
            samplerOverride.minP != null ||
            samplerOverride.repetitionPenalty != null ||
            samplerOverride.frequencyPenalty != null ||
            samplerOverride.presencePenalty != null ||
            samplerOverride.maxOutputTokens != null ||
            stopSequences.isNotEmpty()
        if (promptMode != PromptMode.ROLEPLAY && !hasPresetSampler) {
            return null
        }
        return GatewayRoleplaySamplingConfig(
            temperature = samplerOverride.temperature ?: if (promptMode == PromptMode.ROLEPLAY) ROLEPLAY_TEMPERATURE else null,
            topP = samplerOverride.topP ?: if (promptMode == PromptMode.ROLEPLAY) ROLEPLAY_TOP_P else null,
            topK = samplerOverride.topK,
            minP = samplerOverride.minP,
            repetitionPenalty = samplerOverride.repetitionPenalty,
            frequencyPenalty = samplerOverride.frequencyPenalty,
            presencePenalty = samplerOverride.presencePenalty,
            maxOutputTokens = samplerOverride.maxOutputTokens,
            stopSequences = stopSequences.map(String::trim).filter(String::isNotBlank).distinct(),
        )
    }

    fun shouldRetryWithoutRoleplaySampling(
        request: ChatCompletionRequest,
        errorDetail: String,
    ): Boolean {
        if (
            request.temperature == null &&
            request.topP == null &&
            request.topK == null &&
            request.minP == null &&
            request.repetitionPenalty == null &&
            request.frequencyPenalty == null &&
            request.presencePenalty == null &&
            request.maxTokens == null &&
            request.stop.isEmpty()
        ) {
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
