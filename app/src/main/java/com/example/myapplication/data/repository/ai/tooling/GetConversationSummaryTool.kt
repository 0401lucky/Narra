package com.example.myapplication.data.repository.ai.tooling

import com.google.gson.Gson

class GetConversationSummaryTool(
    private val gson: Gson = Gson(),
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
        val summary = context.conversationSummaryRepository.getSummary(conversationId)
            ?: return errorResult("当前会话暂无缓存摘要")
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
