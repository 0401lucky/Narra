package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.MessageCitation

data class ToolLoopOutcome(
    val finalReply: AssistantReply? = null,
    val continuation: ToolContinuation? = null,
    val citations: List<MessageCitation> = emptyList(),
    val toolRoundCount: Int = 0,
)

sealed interface ToolContinuation {
    data class Transcript(
        val messages: List<ChatMessageDto>,
    ) : ToolContinuation

    data class Responses(
        val input: List<Any>,
        val previousResponseId: String?,
        val temperature: Float? = null,
        val topP: Float? = null,
    ) : ToolContinuation
}
