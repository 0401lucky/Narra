package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayMessageUiMapperTest {
    @Test
    fun mapMessages_mapsDialogueTransferAndStreamingContent() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "雨夜对峙",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            enableNarration = true,
        )
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "你还想瞒我到什么时候？",
                    createdAt = 1L,
                ),
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>雨声更急了。</narration><dialogue>我没有想瞒你。</dialogue>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                ),
                ChatMessage(
                    id = "transfer-1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "转账",
                    createdAt = 3L,
                    parts = listOf(
                        transferMessagePart(
                            id = "t1",
                            direction = TransferDirection.USER_TO_ASSISTANT,
                            status = TransferStatus.PENDING,
                            counterparty = "陆宴清",
                            amount = "66.00",
                            note = "先垫付",
                        ),
                    ),
                ),
            ),
            streamingContent = "……你先听我解释。",
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        )

        assertEquals(5, mapped.size)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[0].contentType)
        assertEquals("林晚", mapped[0].speakerName)
        assertEquals(RoleplayContentType.NARRATION, mapped[1].contentType)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[2].contentType)
        assertEquals(RoleplayContentType.SPECIAL_TRANSFER, mapped[3].contentType)
        assertTrue(mapped[4].isStreaming)
        assertEquals("陆宴清", mapped[4].speakerName)
    }

    @Test
    fun mapMessages_inLongformModePreservesRichTextSource() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "长文模式",
            characterDisplayNameOverride = "陆宴清",
            longformModeEnabled = true,
        )
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(
                        textMessagePart("<char>我垂下眼，声音很轻。</char>"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.LONGFORM, mapped.single().contentType)
        assertEquals("<char>我垂下眼，声音很轻。</char>", mapped.single().richTextSource)
        assertEquals("我垂下眼，声音很轻。", mapped.single().content)
    }
}
