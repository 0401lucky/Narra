package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.specialMetadataValue
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
        assertEquals(RoleplayContentType.SPECIAL_PLAY, mapped[3].contentType)
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

    @Test
    fun mapMessages_keepsStoredLongformHistoryAfterSwitchingBackToDialogueMode() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "普通模式",
            characterDisplayNameOverride = "陆宴清",
            longformModeEnabled = false,
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
                    content = "雨声贴着窗框往下坠。<char>“别这样看我。”</char>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.LONGFORM, mapped.single().contentType)
        assertEquals("雨声贴着窗框往下坠。“别这样看我。”", mapped.single().content)
    }

    @Test
    fun mapMessages_infersLegacyLongformTagsWhenMessageFormatIsMissing() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "对白模式",
            characterDisplayNameOverride = "陆宴清",
            longformModeEnabled = false,
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
                        textMessagePart("她垂下眼睫。<thought>（不能再退了。）</thought>"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.LONGFORM, mapped.single().contentType)
        assertEquals("她垂下眼睫。（不能再退了。）", mapped.single().content)
    }

    @Test
    fun mapMessages_marksOpeningNarrationAsNonRetryable() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "开场",
            characterDisplayNameOverride = "陆宴清",
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = RoleplayConversationSupport.openingNarrationMessageId(
                        scenarioId = scenario.id,
                        conversationId = "conv-1",
                    ),
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>夜色渐深。</narration>",
                    createdAt = 1L,
                    status = MessageStatus.COMPLETED,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertTrue(!mapped.single().canRetry)
    }

    @Test
    fun mapMessages_mapsInviteCardToSpecialPlay() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "邀约",
            characterDisplayNameOverride = "陆宴清",
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "invite-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "今晚见我。",
                    createdAt = 3L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(
                        inviteMessagePart(
                            id = "i1",
                            target = "林晚",
                            place = "江边步道",
                            time = "今晚九点",
                            note = "别迟到",
                        ),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(RoleplayContentType.SPECIAL_PLAY, mapped.single().contentType)
        assertEquals("江边步道", mapped.single().specialPart?.specialMetadataValue("place"))
    }
}
