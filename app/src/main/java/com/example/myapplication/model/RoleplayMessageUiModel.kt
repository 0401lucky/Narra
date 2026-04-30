package com.example.myapplication.model

import androidx.compose.runtime.Immutable

enum class RoleplayContentType {
    NARRATION,
    THOUGHT,
    DIALOGUE,
    LONGFORM,
    ACTION,
    SPECIAL_PLAY,
    STATUS,
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
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val replyToSpeakerName: String = "",
    val isRecalled: Boolean = false,
    val systemEventKind: RoleplayOnlineEventKind = RoleplayOnlineEventKind.NONE,
    val emotion: String = "",
    val createdAt: Long = 0L,
    val isStreaming: Boolean = false,
    val messageStatus: MessageStatus = MessageStatus.COMPLETED,
    val copyText: String = content,
    val richTextSource: String = content,
    val canRetry: Boolean = false,
    val actionPart: ChatMessagePart? = null,
    val specialPart: ChatMessagePart? = null,
)
