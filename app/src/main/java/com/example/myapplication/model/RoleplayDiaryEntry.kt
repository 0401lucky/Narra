package com.example.myapplication.model

import androidx.compose.runtime.Immutable

@Immutable
data class RoleplayDiaryEntry(
    val id: String,
    val conversationId: String,
    val scenarioId: String,
    val title: String,
    val content: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Immutable
data class RoleplayDiaryDraft(
    val title: String,
    val content: String,
)
