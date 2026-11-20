package com.example.myapplication.data.repository.ai

import com.example.myapplication.conversation.MemoryEventSummaryFormatter
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.MemoryEventSummarySnapshot
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings

/** 只构造事件摘要请求并保留协议结果；请求恢复、发布和成员归档由事件协调器负责。 */
internal class MemoryEventPromptService(
    private val core: PromptExtrasCore,
) {
    suspend fun generate(
        snapshot: MemoryEventSummarySnapshot,
        baseUrl: String,
        apiKey: String,
        modelId: String,
        apiProtocol: ProviderApiProtocol,
        provider: ProviderSettings?,
    ): PromptCompletionResult {
        val materials = MemoryEventSummaryFormatter.format(snapshot)
        return core.requestCompletionResult(
            baseUrl = baseUrl,
            apiKey = apiKey,
            operation = "事件摘要失败",
            request = ChatCompletionRequest(
                model = modelId,
                messages = listOf(
                    ChatMessageDto(
                        role = "system",
                        content = MemoryEventSummaryFormatter.systemPrompt
                    ),
                    ChatMessageDto(role = "user", content = materials),
                ),
            ),
            apiProtocol = apiProtocol,
            provider = provider,
        )
    }
}
