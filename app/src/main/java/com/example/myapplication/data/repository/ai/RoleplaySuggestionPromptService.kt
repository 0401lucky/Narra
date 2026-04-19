package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionUiModel

/**
 * 负责生成剧情模式下玩家可选的建议回复。
 * 首次请求若被判定为"应重试"（例如返回都是泛泛文案），会携带 rejectedSuggestions 再来一次。
 *
 * T6.5 从 DefaultAiPromptExtrasService 抽离。使用 roleplay 采样 + 回退。
 */
internal class RoleplaySuggestionPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateRoleplaySuggestions(
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

    private suspend fun requestRoleplaySuggestions(
        requestMessages: List<ChatMessageDto>,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<RoleplaySuggestionUiModel> {
        val content = core.requestCompletionContent(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "RP 建议生成失败",
            request = core.buildRequestWithRoleplaySampling(
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
