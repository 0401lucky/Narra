package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings

/**
 * 负责生成会话标题与聊天建议，无 roleplay 采样。
 * T6.2 从 DefaultAiPromptExtrasService 抽离；对外通过 facade 暴露。
 */
internal class TitleAndChatSuggestionPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generateTitle(
        firstUserMessage: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): String {
        val prompt = "请用不超过15个字总结以下对话的主题，只输出标题文字，不要带引号或标点：\n$firstUserMessage"
        val content = core.requestCompletionContent(
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

    suspend fun generateChatSuggestions(
        conversationSummary: String,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): List<String> {
        val prompt = "基于以下对话，生成3个简短的后续问题建议，每个建议不超过20个字，用换行分隔，只输出建议文字：\n$conversationSummary"
        val content = core.requestCompletionContent(
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
}
