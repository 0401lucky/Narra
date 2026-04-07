package com.example.myapplication.roleplay

import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplaySceneMoodTest {
    @Test
    fun resolveRoleplaySceneMood_mapsWarmEmotion() {
        val moodState = resolveRoleplaySceneMood(
            listOf(
                RoleplayMessageUiModel(
                    sourceMessageId = "a1",
                    contentType = RoleplayContentType.DIALOGUE,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = "陆宴清",
                    content = "我陪你去。",
                    emotion = "温柔",
                    messageStatus = MessageStatus.COMPLETED,
                ),
            ),
        )

        assertEquals(RoleplaySceneMood.WARM, moodState.mood)
        assertEquals("温柔", moodState.label)
    }

    @Test
    fun resolveRoleplaySceneMood_defaultsToNeutralWithoutEmotion() {
        val moodState = resolveRoleplaySceneMood(
            listOf(
                RoleplayMessageUiModel(
                    sourceMessageId = "a1",
                    contentType = RoleplayContentType.DIALOGUE,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = "陆宴清",
                    content = "先说重点。",
                ),
            ),
        )

        assertEquals(RoleplaySceneMood.NEUTRAL, moodState.mood)
        assertEquals("平稳", moodState.label)
    }
}
