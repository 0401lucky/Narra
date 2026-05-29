package com.example.myapplication.ui.screen.roleplay

import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RoleplayOnlinePhoneMessageListKeyTest {

    @Test
    fun buildOnlinePhoneMessageListKey_sameMessageDifferentIndex_producesDistinctKeys() {
        // 同一条 assistant 消息拆出的多个气泡：sourceMessageId / contentType / createdAt /
        // content / partId(="") 完全相同，仅在列表中的位置不同。
        val message = RoleplayMessageUiModel(
            sourceMessageId = "assistant-1",
            contentType = RoleplayContentType.DIALOGUE,
            speaker = RoleplaySpeaker.CHARACTER,
            speakerName = "角色",
            content = "嗯。",
            createdAt = 1_000L,
        )

        val keyAtZero = buildOnlinePhoneMessageListKey(message, 0)
        val keyAtOne = buildOnlinePhoneMessageListKey(message, 1)

        assertNotEquals(keyAtZero, keyAtOne)
    }
}
