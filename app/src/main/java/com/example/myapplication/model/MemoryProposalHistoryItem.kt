package com.example.myapplication.model

enum class MemoryProposalStatus(
    val label: String,
) {
    PENDING("待确认"),
    APPROVED("已记住"),
    REJECTED("已忽略"),
}

data class MemoryProposalHistoryItem(
    val id: String,
    val conversationId: String,
    val assistantId: String = "",
    val scopeType: MemoryScopeType,
    val content: String,
    val reason: String = "",
    val importance: Int = 0,
    val status: MemoryProposalStatus = MemoryProposalStatus.PENDING,
    val createdAt: Long = 0L,
    val decidedAt: Long = 0L,
)
