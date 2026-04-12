package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplaySpeaker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayVideoCallScreenTest {
    @Test
    fun buildVideoCallPresentationState_keepsCarryoverButOnlyShowsCallMessages() {
        val state = buildVideoCallPresentationState(
            messages = listOf(
                message(id = "before-user", createdAt = 100L, content = "通话前用户消息", speaker = RoleplaySpeaker.USER),
                message(id = "before-assistant", createdAt = 200L, content = "通话前角色消息", speaker = RoleplaySpeaker.CHARACTER),
                message(
                    id = "system-call",
                    createdAt = 300L,
                    content = "已接通视频通话",
                    contentType = RoleplayContentType.SYSTEM,
                    speaker = RoleplaySpeaker.SYSTEM,
                    eventKind = RoleplayOnlineEventKind.VIDEO_CALL_CONNECTED,
                ),
                message(id = "call-1", createdAt = 350L, content = "通话内消息1", speaker = RoleplaySpeaker.USER),
                message(id = "call-2", createdAt = 400L, content = "通话内消息2", speaker = RoleplaySpeaker.CHARACTER),
                message(id = "call-3", createdAt = 450L, content = "通话内消息3", speaker = RoleplaySpeaker.USER),
                message(id = "call-4", createdAt = 500L, content = "通话内消息4", speaker = RoleplaySpeaker.CHARACTER),
                message(id = "call-5", createdAt = 550L, content = "通话内消息5", speaker = RoleplaySpeaker.USER),
            ),
            activeVideoCallStartedAt = 300L,
        )

        assertEquals(2, state.carryoverCount)
        assertEquals(listOf("call-2", "call-3", "call-4", "call-5"), state.visibleMessages.map { it.sourceMessageId })
    }

    @Test
    fun buildVideoCallPresentationState_returnsEmptyWhenCallNotStarted() {
        val state = buildVideoCallPresentationState(
            messages = listOf(
                message(id = "m1", createdAt = 100L, content = "测试消息", speaker = RoleplaySpeaker.USER),
            ),
            activeVideoCallStartedAt = 0L,
        )

        assertEquals(0, state.carryoverCount)
        assertTrue(state.visibleMessages.isEmpty())
    }

    private fun message(
        id: String,
        createdAt: Long,
        content: String,
        speaker: RoleplaySpeaker,
        contentType: RoleplayContentType = RoleplayContentType.DIALOGUE,
        eventKind: RoleplayOnlineEventKind = RoleplayOnlineEventKind.NONE,
    ): RoleplayMessageUiModel {
        return RoleplayMessageUiModel(
            sourceMessageId = id,
            contentType = contentType,
            speaker = speaker,
            speakerName = "",
            content = content,
            systemEventKind = eventKind,
            createdAt = createdAt,
            messageStatus = MessageStatus.COMPLETED,
        )
    }
}
