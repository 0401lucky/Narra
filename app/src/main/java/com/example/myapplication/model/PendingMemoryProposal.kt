package com.example.myapplication.model

import java.util.UUID

data class PendingMemoryProposal(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val assistantId: String = "",
    val scopeType: MemoryScopeType,
    val content: String,
    val reason: String = "",
    val importance: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
