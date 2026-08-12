package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoleplayLazyContentTypeTest {
    @Test
    fun roleplayLazyContentType_textStateChanges_keepStableCategory() {
        val base = message(contentType = RoleplayContentType.DIALOGUE)

        val changed = base.copy(
            content = "增长后的正文",
            isStreaming = true,
            messageStatus = MessageStatus.LOADING,
        )

        assertEquals(roleplayLazyContentType(base), roleplayLazyContentType(changed))
    }

    @Test
    fun roleplayLazyContentType_actionStructures_useFiniteSubtypes() {
        val voice = message(
            contentType = RoleplayContentType.ACTION,
            actionType = ChatActionType.VOICE_MESSAGE,
        )
        val image = message(
            contentType = RoleplayContentType.ACTION,
            actionType = ChatActionType.AI_PHOTO,
        )
        val generic = message(
            contentType = RoleplayContentType.ACTION,
            actionType = ChatActionType.LOCATION,
        )

        assertEquals(RoleplayLazyContentType.ACTION_VOICE, roleplayLazyContentType(voice))
        assertEquals(RoleplayLazyContentType.ACTION_IMAGE, roleplayLazyContentType(image))
        assertEquals(RoleplayLazyContentType.ACTION_GENERIC, roleplayLazyContentType(generic))
        assertNotEquals(roleplayLazyContentType(voice), roleplayLazyContentType(image))
    }

    private fun message(
        contentType: RoleplayContentType,
        actionType: ChatActionType? = null,
    ): RoleplayMessageUiModel {
        return RoleplayMessageUiModel(
            uiItemId = "message:dialogue:0",
            sourceMessageId = "message",
            contentType = contentType,
            speaker = RoleplaySpeaker.CHARACTER,
            speakerName = "角色",
            content = "正文",
            actionPart = actionType?.let { type ->
                ChatMessagePart(
                    type = ChatMessagePartType.ACTION,
                    actionType = type,
                    actionId = "action",
                )
            },
        )
    }
}
