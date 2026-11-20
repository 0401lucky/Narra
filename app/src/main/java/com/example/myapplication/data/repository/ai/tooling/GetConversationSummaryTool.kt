package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson

class GetConversationSummaryTool(
    private val gson: Gson = AppJson.gson,
    private val memoryArchiveRepository: com.example.myapplication.data.repository.context.MemoryArchiveRepository? = null,
) : AppTool {
    override val name: String = NAME

    override val description: String = "读取当前会话已缓存的摘要，帮助模型快速回顾剧情或对话进展。"

    override val inputSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to emptyMap<String, Any>(),
        "additionalProperties" to false,
    )

    override suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult {
        val conversationId = context.runtimeContext?.conversation?.id.orEmpty()
        if (conversationId.isBlank()) {
            return errorResult("当前没有可用会话，无法读取摘要")
        }
        val memoryContext = context.runtimeContext?.memoryContext
        if (memoryContext != null && (memoryContext.characterId != context.runtimeContext?.assistant?.id ||
                    memoryContext.conversationId != conversationId)
        ) return errorResult("摘要归属与当前请求角色不符")
        if (memoryContext != null && memoryArchiveRepository != null &&
            memoryArchiveRepository.getRuntimeContext(
                conversationId,
                memoryContext.characterId
            ) != memoryContext
        ) return errorResult("当前摘要的角色或运行归属已改变")
        if (context.runtimeContext?.promptMode == com.example.myapplication.model.PromptMode.ROLEPLAY && memoryContext == null) return errorResult(
            "当前摘要缺少运行归属"
        )
        val stored = if (memoryContext == null) context.conversationSummaryRepository.getSummary(
            conversationId
        )
        else context.conversationSummaryRepository.getSummaryForContext(memoryContext)
        val messages =
            context.conversationSummaryRepository.getSummarySourceMessages(conversationId)
                ?: context.runtimeContext?.recentMessages.orEmpty()
        val summary =
            com.example.myapplication.conversation.ConversationSummaryCoverageSupport.usableSummary(
                stored,
                messages,
                memoryContext
            )
                ?: return errorResult("当前会话暂无缓存摘要或缓存已不适用于本次运行")
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "conversation_id" to summary.conversationId,
                    "assistant_id" to summary.assistantId,
                    "summary" to summary.summary,
                    "covered_message_count" to summary.coveredMessageCount,
                    "updated_at" to summary.updatedAt,
                ),
            ),
        )
    }

    private fun errorResult(
        message: String,
    ): ToolExecutionResult {
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "error" to message,
                ),
            ),
            isError = true,
        )
    }

    companion object {
        const val NAME = "get_conversation_summary"
    }
}
