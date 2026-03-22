package com.example.myapplication.model

import java.util.UUID

data class RoleplaySession(
    val id: String = UUID.randomUUID().toString(),
    val scenarioId: String,
    val conversationId: String,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)
