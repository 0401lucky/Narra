package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.ChatConversationSupport
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.punishMessagePart
import com.example.myapplication.model.taskMessagePart
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

    fun resolveSpecialPlay(
        state: ChatUiState,
        draft: ChatSpecialPlayDraft,
    ): ChatOutgoingMessageResolution {
        if (state.isSending) {
            return ChatOutgoingMessageResolution.NoOp
        }
        ChatConversationSupport.validateSpecialPlayAvailability(state.settings)?.let { errorMessage ->
            return ChatOutgoingMessageResolution.Error(errorMessage)
        }
        val specialPart = when (draft) {
            is TransferPlayDraft -> {
                val normalizedAmount = draft.amount.trim()
                if (normalizedAmount.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入转账金额")
                }
                transferMessagePart(
                    direction = TransferDirection.USER_TO_ASSISTANT,
                    status = TransferStatus.PENDING,
                    counterparty = draft.counterparty.trim().ifBlank {
                        state.currentAssistant?.name?.trim().orEmpty().ifBlank { "对方" }
                    },
                    amount = normalizedAmount,
                    note = draft.note.trim(),
                )
            }

            is InvitePlayDraft -> {
                val normalizedPlace = draft.place.trim()
                if (normalizedPlace.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入邀约地点")
                }
                val normalizedTime = draft.time.trim()
                if (normalizedTime.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入邀约时间")
                }
                inviteMessagePart(
                    target = draft.target.trim().ifBlank {
                        state.currentAssistant?.name?.trim().orEmpty().ifBlank { "对方" }
                    },
                    place = normalizedPlace,
                    time = normalizedTime,
                    note = draft.note.trim(),
                )
            }

            is GiftPlayDraft -> {
                val normalizedItem = draft.item.trim()
                if (normalizedItem.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入礼物内容")
                }
                giftMessagePart(
                    target = draft.target.trim().ifBlank {
                        state.currentAssistant?.name?.trim().orEmpty().ifBlank { "对方" }
                    },
                    item = normalizedItem,
                    note = draft.note.trim(),
                )
            }

            is TaskPlayDraft -> {
                val normalizedTitle = draft.title.trim()
                if (normalizedTitle.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入委托标题")
                }
                val normalizedObjective = draft.objective.trim()
                if (normalizedObjective.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入委托目标")
                }
                taskMessagePart(
                    title = normalizedTitle,
                    objective = normalizedObjective,
                    reward = draft.reward.trim(),
                    deadline = draft.deadline.trim(),
                )
            }

            is PunishPlayDraft -> {
                val normalizedMethod = draft.method.trim()
                if (normalizedMethod.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入惩罚方式")
                }
                val normalizedCount = draft.count.trim()
                if (normalizedCount.isBlank()) {
                    return ChatOutgoingMessageResolution.Error("请输入惩罚次数")
                }
                punishMessagePart(
                    method = normalizedMethod,
                    count = normalizedCount,
                    intensity = draft.intensity,
                    reason = draft.reason.trim(),
                    note = draft.note.trim(),
                )
            }
        }
        return ChatOutgoingMessageResolution.Ready(
            plan = ChatOutgoingMessagePlan(
                userParts = listOf(specialPart),
                nextInput = state.input,
                nextPendingParts = state.pendingParts,
                forceChatRoundTrip = true,
            ),
        )
    }

    fun resolveTransferPlay(
        state: ChatUiState,
        counterparty: String,
        amount: String,
        note: String,
    ): ChatOutgoingMessageResolution {
        return resolveSpecialPlay(
            state = state,
            draft = TransferPlayDraft(
                counterparty = counterparty,
                amount = amount,
                note = note,
            ),
        )
    }
}
