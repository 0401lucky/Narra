package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.ChatConversationSupport
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.transferMessagePart

data class ChatOutgoingMessagePlan(
    val userParts: List<ChatMessagePart>,
    val nextInput: String,
    val nextPendingParts: List<ChatMessagePart>,
    val imageGenerationPrompt: String = "",
    val forceChatRoundTrip: Boolean,
)

sealed interface ChatOutgoingMessageResolution {
    data object NoOp : ChatOutgoingMessageResolution

    data class Error(
        val message: String,
    ) : ChatOutgoingMessageResolution

    data class Ready(
        val plan: ChatOutgoingMessagePlan,
    ) : ChatOutgoingMessageResolution
}

object ChatOutgoingMessageSupport {
    fun resolveTextMessage(state: ChatUiState): ChatOutgoingMessageResolution {
        val text = state.input.trim()
        val pendingParts = state.pendingParts
        if (text.isBlank() && pendingParts.isEmpty()) {
            return ChatOutgoingMessageResolution.Error("请输入消息内容或添加附件")
        }
        val userParts = ChatConversationSupport.buildUserMessageParts(
            text = text,
            pendingParts = pendingParts,
        )
        ChatConversationSupport.validateOutgoingParts(
            settings = state.settings,
            userParts = userParts,
        )?.let { errorMessage ->
            return ChatOutgoingMessageResolution.Error(errorMessage)
        }
        return ChatOutgoingMessageResolution.Ready(
            plan = ChatOutgoingMessagePlan(
                userParts = userParts,
                nextInput = "",
                nextPendingParts = emptyList(),
                imageGenerationPrompt = text,
                forceChatRoundTrip = false,
            ),
        )
    }

    fun resolveTransferPlay(
        state: ChatUiState,
        counterparty: String,
        amount: String,
        note: String,
    ): ChatOutgoingMessageResolution {
        if (state.isSending) {
            return ChatOutgoingMessageResolution.NoOp
        }
        ChatConversationSupport.validateTransferPlayAvailability(state.settings)?.let { errorMessage ->
            return ChatOutgoingMessageResolution.Error(errorMessage)
        }
        val normalizedAmount = amount.trim()
        if (normalizedAmount.isBlank()) {
            return ChatOutgoingMessageResolution.Error("请输入转账金额")
        }
        val normalizedCounterparty = counterparty.trim().ifBlank {
            state.currentAssistant?.name?.trim().orEmpty().ifBlank { "对方" }
        }
        return ChatOutgoingMessageResolution.Ready(
            plan = ChatOutgoingMessagePlan(
                userParts = listOf(
                    transferMessagePart(
                        direction = TransferDirection.USER_TO_ASSISTANT,
                        status = TransferStatus.PENDING,
                        counterparty = normalizedCounterparty,
                        amount = normalizedAmount,
                        note = note.trim(),
                    ),
                ),
                nextInput = state.input,
                nextPendingParts = state.pendingParts,
                forceChatRoundTrip = true,
            ),
        )
    }
}
