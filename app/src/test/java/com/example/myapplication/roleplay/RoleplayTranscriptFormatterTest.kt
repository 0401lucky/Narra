package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.textMessagePart
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
}
