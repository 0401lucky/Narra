package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart
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
    fun mapMessages_prefersProtocolTagsWhenStoredFormatWasLongform() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "切模式修复",
            characterDisplayNameOverride = "陆宴清",
            longformModeEnabled = false,
            enableNarration = true,
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
                    content = "<narration>夜色沉了下去。</narration><dialogue>先别走。</dialogue>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped[0].contentType)
        assertEquals("夜色沉了下去。", mapped[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
        assertEquals("先别走。", mapped[1].content)
    }

    @Test
    fun mapMessages_prefersLongformTagsWhenStoredFormatWasProtocol() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "切模式修复",
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
                    content = "雨声压了下来。<thought>不能后退。</thought>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.LONGFORM, mapped.single().contentType)
        assertEquals("雨声压了下来。不能后退。", mapped.single().content)
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
    fun mapMessages_onlineOpeningNarrationStaysNarrationInsteadOfThought() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上开场",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
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
                    content = "<narration>夜色渐深，聊天框还停在昨晚那句没回完的话上。</narration>",
                    createdAt = 1L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped.single().contentType)
        assertEquals("夜色渐深，聊天框还停在昨晚那句没回完的话上。", mapped.single().content)
    }

    @Test
    fun mapMessages_onlineModeDoesNotInsertBurstSystemMessage() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = false,
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
                    content = """
                        <dialogue>我看见了。</dialogue>
                        <dialogue>先别躲。</dialogue>
                        <dialogue>把话说完。</dialogue>
                    """.trimIndent(),
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertTrue(mapped.isNotEmpty())
        assertTrue(mapped.none { it.speakerName == "系统" })
        assertTrue(mapped.none { it.content.contains("连发了") })
    }

    @Test
    fun mapMessages_onlineModeConvertsNarrationToThought() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            userDisplayNameOverride = "林晚",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
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
                    content = "<narration>他盯着已读标记，指尖悬了一秒。</narration><dialogue>现在才回？</dialogue>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.THOUGHT, mapped[0].contentType)
        assertEquals("陆宴清", mapped[0].speakerName)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
    }

    @Test
    fun mapMessages_onlineModePreservesSystemNarrationHints() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "event-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>你截了一张聊天截图。</narration>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    systemEventKind = RoleplayOnlineEventKind.SCREENSHOT,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped.single().contentType)
        assertEquals("旁白", mapped.single().speakerName)
    }

    @Test
    fun mapMessages_onlineModeMapsPlainTextSystemEventToNarration() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "event-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "视频通话已结束，通话时长 00:12",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    systemEventKind = RoleplayOnlineEventKind.VIDEO_CALL_ENDED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    parts = listOf(textMessagePart("视频通话已结束，通话时长 00:12")),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped.single().contentType)
        assertEquals("旁白", mapped.single().speakerName)
    }

    @Test
    fun mapMessages_onlineModeMapsThoughtPartsToThoughtUiModel() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    parts = listOf(
                        thoughtMessagePart("其实已经在输入框里删了三次。"),
                        textMessagePart("你现在终于肯回我了？"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.THOUGHT, mapped[0].contentType)
        assertEquals("其实已经在输入框里删了三次。", mapped[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
        assertEquals("你现在终于肯回我了？", mapped[1].content)
    }

    @Test
    fun mapMessages_offlineModeStillDecodesStoredOnlineThoughtParts() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "切回线下",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    parts = listOf(
                        thoughtMessagePart("其实已经删删改改很多次了。"),
                        textMessagePart("你终于回我了。"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.THOUGHT, mapped[0].contentType)
        assertEquals("其实已经删删改改很多次了。", mapped[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
    }

    @Test
    fun mapMessages_prefersStoredInteractionModeOverCurrentScenario() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "切到线上",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "风把窗帘掀起一角。<char>“我没有忘。”</char>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
                    roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.LONGFORM, mapped.single().contentType)
        assertEquals("风把窗帘掀起一角。“我没有忘。”", mapped.single().content)
    }

    @Test
    fun mapMessages_onlineModeStreamingThoughtPreviewUsesThoughtBubbleType() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = emptyList(),
            streamingContent = "心声：其实已经想拨过去了。",
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.THOUGHT, mapped.single().contentType)
        assertEquals("其实已经想拨过去了。", mapped.single().content)
    }

    @Test
    fun mapMessages_onlineModeErrorMessageFallsBackToPlainDialogue() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "沈宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "沈宴清"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-error",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "type\nbad_response_status.code\nparam\n.",
                    createdAt = 2L,
                    status = MessageStatus.ERROR,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(1, mapped.size)
        assertEquals(RoleplayContentType.DIALOGUE, mapped.single().contentType)
        assertEquals(MessageStatus.ERROR, mapped.single().messageStatus)
        assertEquals("type\nbad_response_status.code\nparam\n.", mapped.single().content)
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
