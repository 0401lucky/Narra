package com.example.myapplication.model

data class ConversationSummarySegment(
    val id: String,
    val conversationId: String,
    val assistantId: String = "",
    val startMessageId: String = "",
    val endMessageId: String = "",
    val startCreatedAt: Long = 0L,
    val endCreatedAt: Long = 0L,
    val messageCount: Int = 0,
    val summary: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val sourceSnapshot: List<ConversationSummarySource> = emptyList(),
    val completionStatus: PromptCompletionStatus = PromptCompletionStatus.UNKNOWN,
    val memoryContext: MemoryRuntimeContext? = null,
)
