package com.example.myapplication.model

data class ConversationSummary(
    val conversationId: String,
    val assistantId: String = "",
    val summary: String = "",
    val coveredMessageCount: Int = 0,
    val updatedAt: Long = 0L,
)
