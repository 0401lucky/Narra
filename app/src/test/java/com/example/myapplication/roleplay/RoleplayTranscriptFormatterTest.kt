package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayTranscriptFormatterTest {
    @Test
    fun formatMessages_preservesStoredLongformHistoryAfterModeSwitch() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "你还记得吗？",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    parts = listOf(
                        textMessagePart("风把窗帘掀起一角。\n<char>“我没有忘。”</char>"),
                    ),
                    roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
        )

        assertEquals(
            "林晚：你还记得吗？\n陆宴清：风把窗帘掀起一角。\n陆宴清：“我没有忘。”",
            transcript,
        )
    }

    @Test
    fun formatMessages_keepsProtocolHistoryReadable() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>雨声更急了。</narration><dialogue>先别逼我。</dialogue>",
                    createdAt = 2L,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
        )

        assertEquals(
            "旁白：雨声更急了。\n陆宴清：先别逼我。",
            transcript,
        )
    }

    @Test
    fun formatMessages_onlineModeFormatsLegacyNarrationAsThought() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>他把删到一半的话又咽了回去。</narration><dialogue>行，那你先忙。</dialogue>",
                    createdAt = 2L,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(
            "陆宴清心声：他把删到一半的话又咽了回去。\n陆宴清：行，那你先忙。",
            transcript,
        )
    }

    @Test
    fun formatMessages_offlineModeStillDecodesStoredOnlineThoughtPart() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    parts = listOf(
                        thoughtMessagePart("其实已经删掉好几次了。"),
                        textMessagePart("你终于肯回我了。"),
                    ),
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        )

        assertEquals(
            "陆宴清心声：其实已经删掉好几次了。\n陆宴清：你终于肯回我了。",
            transcript,
        )
    }

    @Test
    fun formatMessages_onlineOpeningNarrationStaysNarration() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = RoleplayConversationSupport.openingNarrationMessageId(
                        scenarioId = "scene-1",
                        conversationId = "conv-1",
                    ),
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>夜色渐深，聊天框还停在昨晚那句没回完的话上。</narration>",
                    createdAt = 2L,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(
            "旁白：夜色渐深，聊天框还停在昨晚那句没回完的话上。",
            transcript,
        )
    }

    @Test
    fun formatMessages_prefersStoredMessageInteractionModeAfterSceneSwitch() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "风把窗帘掀起一角。<char>“我没有忘。”</char>",
                    createdAt = 2L,
                    roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
                    roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
                ),
            ),
            userName = "林晚",
            characterName = "陆宴清",
            allowNarration = true,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(
            "陆宴清：风把窗帘掀起一角。“我没有忘。”",
            transcript,
        )
    }

    @Test
    fun formatMessages_onlineInlineThoughtFallbackSplitsTranscript() {
        val transcript = RoleplayTranscriptFormatter.formatMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-inline",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "【心声】看到这几个字，嘴角不自觉地动了动，想压下去没压住。 ...... 在家就好好休息。 又没干正事。 晚上想吃什么，下课带过去。",
                    createdAt = 2L,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                    parts = listOf(
                        textMessagePart("【心声】看到这几个字，嘴角不自觉地动了动，想压下去没压住。 ...... 在家就好好休息。 又没干正事。 晚上想吃什么，下课带过去。"),
                    ),
                ),
            ),
            userName = "林晚",
            characterName = "沈宴清",
            allowNarration = true,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(
            "沈宴清心声：看到这几个字，嘴角不自觉地动了动，想压下去没压住。\n沈宴清：在家就好好休息。\n沈宴清：又没干正事。\n沈宴清：晚上想吃什么，下课带过去。",
            transcript,
        )
    }
}
