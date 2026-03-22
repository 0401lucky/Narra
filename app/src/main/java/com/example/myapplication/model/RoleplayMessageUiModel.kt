package com.example.myapplication.model

import androidx.compose.runtime.Immutable

enum class RoleplayContentType {
    NARRATION,
    DIALOGUE,
    SYSTEM,
}

enum class RoleplaySpeaker {
    USER,
    CHARACTER,
    NARRATOR,
    SYSTEM,
}

@Immutable
data class RoleplayMessageUiModel(
    val sourceMessageId: String,
    val contentType: RoleplayContentType,
    val speaker: RoleplaySpeaker,
    val speakerName: String,
    val content: String,
    val emotion: String = "",
    val createdAt: Long = 0L,
    val isStreaming: Boolean = false,
)
