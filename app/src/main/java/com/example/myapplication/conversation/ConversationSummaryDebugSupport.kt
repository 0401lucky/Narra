package com.example.myapplication.conversation

object ConversationSummaryDebugSupport {
    fun appendStatusLine(
        debugDump: String,
        hasSummary: Boolean,
        coveredMessageCount: Int,
        completedMessageCount: Int,
        triggerMessageCount: Int,
    ): String {
        val reason = when {
            hasSummary && coveredMessageCount > 0 -> {
                "已注入（覆盖 $coveredMessageCount 条消息）"
            }

            completedMessageCount <= triggerMessageCount -> {
                "暂无缓存摘要；当前完成消息数 $completedMessageCount，未达到自动摘要阈值 > $triggerMessageCount"
            }

            else -> "暂无缓存摘要"
        }
        return buildString {
            append(debugDump)
            append("\n- 摘要状态说明：")
            append(reason)
        }
    }
}
