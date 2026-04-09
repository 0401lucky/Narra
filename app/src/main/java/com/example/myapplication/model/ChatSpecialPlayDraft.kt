package com.example.myapplication.model

sealed interface ChatSpecialPlayDraft {
    val type: ChatSpecialType
}

enum class PunishIntensity(val storageValue: String, val displayName: String) {
    LIGHT("light", "轻"),
    MEDIUM("medium", "中"),
    HEAVY("heavy", "重");

    companion object {
        fun fromStorageValue(value: String): PunishIntensity? {
            return entries.firstOrNull { it.storageValue == value.trim().lowercase() }
        }
    }
}

data class TransferPlayDraft(
    val counterparty: String = "",
    val amount: String = "",
    val note: String = "",
) : ChatSpecialPlayDraft {
    override val type: ChatSpecialType = ChatSpecialType.TRANSFER
}

data class InvitePlayDraft(
    val target: String = "",
    val place: String = "",
    val time: String = "",
    val note: String = "",
) : ChatSpecialPlayDraft {
    override val type: ChatSpecialType = ChatSpecialType.INVITE
}

data class GiftPlayDraft(
    val target: String = "",
    val item: String = "",
    val note: String = "",
) : ChatSpecialPlayDraft {
    override val type: ChatSpecialType = ChatSpecialType.GIFT
}

data class TaskPlayDraft(
    val title: String = "",
    val objective: String = "",
    val reward: String = "",
    val deadline: String = "",
) : ChatSpecialPlayDraft {
    override val type: ChatSpecialType = ChatSpecialType.TASK
}

data class PunishPlayDraft(
    val method: String = "",
    val count: String = "",
    val intensity: PunishIntensity = PunishIntensity.MEDIUM,
    val reason: String = "",
    val note: String = "",
) : ChatSpecialPlayDraft {
    override val type: ChatSpecialType = ChatSpecialType.PUNISH
}
