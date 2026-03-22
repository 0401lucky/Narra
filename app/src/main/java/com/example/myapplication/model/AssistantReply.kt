package com.example.myapplication.model

data class AssistantReply(
    val content: String,
    val reasoningContent: String = "",
    val parts: List<ChatMessagePart> = emptyList(),
)

sealed interface ChatStreamEvent {
    data class ContentDelta(val value: String) : ChatStreamEvent

    data class ReasoningDelta(val value: String) : ChatStreamEvent

    data class ImageDelta(val part: ChatMessagePart) : ChatStreamEvent

    data object Completed : ChatStreamEvent
}
