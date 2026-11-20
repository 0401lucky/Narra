package com.example.myapplication.model

data class ConversationSummary(
    val conversationId: String,
    val assistantId: String = "",
    val summary: String = "",
    val coveredMessageCount: Int = 0,
    val updatedAt: Long = 0L,
    val sourceSnapshot: List<ConversationSummarySource> = emptyList(),
    val completionStatus: PromptCompletionStatus = PromptCompletionStatus.UNKNOWN,
    val memoryContext: MemoryRuntimeContext? = null,
)

/** 来源使用 UTF-16 半开区间；当前会话摘要只接受完整消息，不把摘录认作整条覆盖。 */
data class ConversationSummarySource(
    val messageId: String,
    val messageRevision: Long,
    val createdAt: Long,
    val fingerprint: String,
    val startOffset: Int,
    val endOffset: Int,
    val kind: String? = null,
)
