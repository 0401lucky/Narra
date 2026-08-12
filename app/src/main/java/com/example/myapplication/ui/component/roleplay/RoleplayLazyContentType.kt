package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel

/**
 * RP 消息列表只使用有限且稳定的复用类别，避免正文或临时播放状态改变时失去槽位复用。
 */
internal enum class RoleplayLazyContentType {
    NARRATION,
    THOUGHT,
    DIALOGUE,
    LONGFORM,
    ACTION_VOICE,
    ACTION_IMAGE,
    ACTION_GENERIC,
    SPECIAL_PLAY,
    STATUS,
    SYSTEM,
}

internal fun roleplayLazyContentType(
    message: RoleplayMessageUiModel,
): RoleplayLazyContentType {
    return when (message.contentType) {
        RoleplayContentType.NARRATION -> RoleplayLazyContentType.NARRATION
        RoleplayContentType.THOUGHT -> RoleplayLazyContentType.THOUGHT
        RoleplayContentType.DIALOGUE -> RoleplayLazyContentType.DIALOGUE
        RoleplayContentType.LONGFORM -> RoleplayLazyContentType.LONGFORM
        RoleplayContentType.ACTION -> when (message.actionPart?.actionType) {
            ChatActionType.VOICE_MESSAGE -> RoleplayLazyContentType.ACTION_VOICE
            ChatActionType.AI_PHOTO -> RoleplayLazyContentType.ACTION_IMAGE
            else -> RoleplayLazyContentType.ACTION_GENERIC
        }

        RoleplayContentType.SPECIAL_PLAY -> RoleplayLazyContentType.SPECIAL_PLAY
        RoleplayContentType.STATUS -> RoleplayLazyContentType.STATUS
        RoleplayContentType.SYSTEM -> RoleplayLazyContentType.SYSTEM
    }
}
