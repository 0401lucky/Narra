package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayChatType
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayMessageUiMapperTest {
    @Test
    fun mapMessages_userDialogueUsesResolvedPersonaAvatar() {
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = RoleplayScenario(
                id = "scene-avatar",
                userDisplayNameOverride = "lucky",
                userPortraitUri = "file://scene-user.png",
                userPortraitUrl = "https://cdn.example.com/scene-user.png",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(
                userAvatarUri = "file://global-user.png",
                userAvatarUrl = "https://cdn.example.com/global-user.png",
            ),
            rawMessages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "晚上好",
                    createdAt = 1L,
                    parts = listOf(textMessagePart("晚上好")),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        )

        assertEquals("file://scene-user.png", mapped.single().speakerAvatarUri)
        assertEquals("https://cdn.example.com/scene-user.png", mapped.single().speakerAvatarUrl)
    }

    @Test
    fun mapMessages_userDialogueKeepsMessageLevelQuoteOnTextPart() {
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = RoleplayScenario(
                id = "scene-quote",
                userDisplayNameOverride = "lucky",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "user-quote",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "我知道了",
                    createdAt = 1L,
                    parts = listOf(textMessagePart("我知道了")),
                    replyToMessageId = "quoted-1",
                    replyToSpeakerName = "陆宴清",
                    replyToPreview = "别急，先把话说完。",
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        ).single()

        assertEquals("quoted-1", mapped.replyToMessageId)
        assertEquals("陆宴清", mapped.replyToSpeakerName)
        assertEquals("别急，先把话说完。", mapped.replyToPreview)
    }

    @Test
    fun mapMessages_singleChatCharacterAvatarPrefersSnapshotThenScenarioThenAssistant() {
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            iconName = "psychology",
            avatarUri = "file://assistant-avatar.png",
        )
        val scenario = RoleplayScenario(
            id = "scene-avatar",
            characterDisplayNameOverride = "陆宴清",
            characterPortraitUri = "file://scenario-avatar.png",
            characterPortraitUrl = "https://cdn.example.com/scenario-avatar.png",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        val withSnapshot = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "在",
                    createdAt = 1L,
                    speakerAvatarUri = "file://snapshot-avatar.png",
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        ).single()

        val withScenario = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-2",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "刚看到",
                    createdAt = 2L,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        ).single()

        val withAssistant = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario.copy(characterPortraitUri = "", characterPortraitUrl = ""),
            assistant = assistant,
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-3",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "嗯",
                    createdAt = 3L,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        ).single()

        assertEquals("file://snapshot-avatar.png", withSnapshot.speakerAvatarUri)
        assertEquals("", withSnapshot.speakerAvatarUrl)
        assertEquals("file://scenario-avatar.png", withScenario.speakerAvatarUri)
        assertEquals("https://cdn.example.com/scenario-avatar.png", withScenario.speakerAvatarUrl)
        assertEquals("file://assistant-avatar.png", withAssistant.speakerAvatarUri)
        assertEquals("psychology", withAssistant.speakerIconName)
    }

    @Test
    fun mapMessages_groupCharacterAvatarUsesSpeakerIdAssistantFallback() {
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = RoleplayScenario(
                id = "group-avatar",
                chatType = RoleplayChatType.GROUP,
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
            assistant = null,
            settings = AppSettings(
                assistants = listOf(
                    Assistant(
                        id = "role-a",
                        name = "沈宴清",
                        iconName = "auto_stories",
                        avatarUri = "file://shen.png",
                    ),
                ),
            ),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "刚看到",
                    createdAt = 1L,
                    speakerId = "role-a",
                    speakerName = "沈宴清",
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        )

        assertEquals("file://shen.png", mapped.single().speakerAvatarUri)
        assertEquals("auto_stories", mapped.single().speakerIconName)
    }

    @Test
    fun mapMessages_streamingMessageInheritsLoadingAvatarSnapshot() {
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = RoleplayScenario(
                id = "scene-streaming-avatar",
                characterDisplayNameOverride = "陆宴清",
                characterPortraitUri = "file://scenario-avatar.png",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
            assistant = Assistant(id = "assistant-1", name = "陆宴清"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-loading",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    status = MessageStatus.LOADING,
                    createdAt = 1L,
                    speakerName = "陆宴清",
                    speakerAvatarUri = "file://loading-avatar.png",
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = "刚到",
            outputParser = RoleplayOutputParser(),
            nowProvider = { 10L },
        )

        val streaming = mapped.single { it.isStreaming }
        assertEquals("file://loading-avatar.png", streaming.speakerAvatarUri)
    }

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
    fun mapMessages_inLongformModeExtractsLeadingStatusBlock() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "长文模式",
            characterDisplayNameOverride = "陆承渊",
            longformModeEnabled = true,
        )
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "陆承渊"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = """
                        > 时间：23:03 | 日期：2026年4月29日
                        地点：静安区公寓卧室 | 天气：雨停后的深夜
                        陆承渊·状态 | 阶段：破线 | 外在：仰躺

                        <p style="text-align:center; color:gray; font-size:0.9em;">—— 外滩夜色 ——</p>
                        拇指停在后腰窝的位置。
                    """.trimIndent(),
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
        assertEquals(RoleplayContentType.STATUS, mapped[0].contentType)
        assertTrue(mapped[0].content.contains("时间：23:03"))
        assertEquals(RoleplayContentType.LONGFORM, mapped[1].contentType)
        assertTrue(mapped[1].content.contains("—— 外滩夜色 ——"))
        assertTrue(mapped[1].content.contains("拇指停在后腰窝的位置。"))
        assertTrue(!mapped[1].content.contains("时间：23:03"))
    }

    @Test
    fun mapMessages_extractsLeadingStatusBlockFromDialogueContent() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "普通模式",
            characterDisplayNameOverride = "君泽",
        )
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "君泽"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = """
                        『时间：10:07 | 日期：2026年4月21日星期二 | 地点：申江新区管委会主任办公室 | 天气：百叶窗的缝隙切碎了日光

                        他把录音笔轻轻放在桌面上。
                    """.trimIndent(),
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.STATUS, mapped[0].contentType)
        assertTrue(mapped[0].content.contains("时间：10:07"))
        assertTrue(!mapped[0].content.contains("『时间"))
        assertTrue(mapped[1].content.contains("录音笔轻轻放在桌面上"))
        assertTrue(!mapped[1].content.contains("时间：10:07"))
    }

    @Test
    fun mapMessages_extractsLeadingStatusBlockBeforeOnlineProtocolFallback() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "君泽",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )
        val content = """
            『时间：10:07 ｜ 日期：2026年4月21日星期二 ｜ 地点：申江新区管委会主任办公室 ｜ 天气：百叶窗的缝隙切碎了日光』

            —— 卷页翻开 · 正文起 ——

            君泽垂下眼睑，视线在那支录音笔上停留了极短的一瞬。
        """.trimIndent()
        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "君泽"),
            settings = AppSettings(),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = content,
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(textMessagePart(content)),
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                    roleplayOutputFormat = RoleplayOutputFormat.PLAIN,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertTrue(mapped.size >= 2)
        assertEquals(RoleplayContentType.STATUS, mapped[0].contentType)
        assertTrue(mapped[0].content.contains("时间：10:07"))
        assertTrue(!mapped[0].content.contains("『时间"))
        val visibleBody = mapped.drop(1).joinToString(separator = "\n") { it.content }
        assertTrue(visibleBody.contains("卷页翻开"))
        assertTrue(visibleBody.contains("录音笔"))
        assertTrue(!visibleBody.contains("时间：10:07"))
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
    fun mapMessages_onlineModeSplitsInlineThoughtAndDialogueFallback() {
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
                    id = "assistant-inline",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "【心声】看到这几个字，嘴角不自觉地动了动，想压下去没压住。 ...... 在家就好好休息。 又没干正事。 晚上想吃什么，下课带过去。",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                    parts = listOf(
                        textMessagePart("【心声】看到这几个字，嘴角不自觉地动了动，想压下去没压住。 ...... 在家就好好休息。 又没干正事。 晚上想吃什么，下课带过去。"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(4, mapped.size)
        assertEquals(RoleplayContentType.THOUGHT, mapped[0].contentType)
        assertEquals("看到这几个字，嘴角不自觉地动了动，想压下去没压住。", mapped[0].content)
        assertEquals("在家就好好休息。", mapped[1].content)
        assertEquals("又没干正事。", mapped[2].content)
        assertEquals("晚上想吃什么，下课带过去。", mapped[3].content)
    }

    @Test
    fun mapMessages_onlineModeSplitsShortPlainTextFallbackIntoMultipleBubbles() {
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
                    id = "assistant-short",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "嗯，刚从教室回来。 还在整理这周的论文批改。 你呢，在家？",
                    createdAt = 3L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                    parts = listOf(
                        textMessagePart("嗯，刚从教室回来。 还在整理这周的论文批改。 你呢，在家？"),
                    ),
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(3, mapped.size)
        assertEquals("嗯，刚从教室回来。", mapped[0].content)
        assertEquals("还在整理这周的论文批改。", mapped[1].content)
        assertEquals("你呢，在家？", mapped[2].content)
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
    fun mapMessages_streamingMessageReusesLoadingMessageCreatedAt() {
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
                    id = "assistant-loading",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 1234L,
                    status = MessageStatus.LOADING,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = "心声：其实已经想拨过去了。",
            outputParser = RoleplayOutputParser(),
            nowProvider = { 9999L },
        )

        assertEquals(1, mapped.size)
        assertTrue(mapped.single().isStreaming)
        assertEquals(1234L, mapped.single().createdAt)
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

    @Test
    fun mapMessages_onlineProtocolRendersActionsAndSpecialPlays() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上玩法",
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
                    id = "assistant-actions",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = """
                        [
                          {"type":"thought","content":"想见。"},
                          {"type":"reply_to","message_id":"m-old","content":"这句我回你。"},
                          {"type":"emoji","description":"轻轻挑眉"},
                          {"type":"voice_message","content":"我在楼下。","duration_seconds":4},
                          {"type":"ai-photo","description":"楼下路灯照着湿漉漉的台阶。"},
                          {"type":"location","locationName":"南滨路老仓库","address":"江边旧仓库"},
                          {"type":"transfer","amount":66,"note":"打车"},
                          {"type":"poke","target":"用户","suffix":"的肩"},
                          {"type":"video_call","reason":"想看看你"},
                          {"type":"invite","target":"用户","place":"家里","time":"现在","note":"菜要凉了"},
                          {"type":"gift","target":"用户","item":"围巾","note":"外面冷"},
                          {"type":"task","title":"带伞","objective":"出门前把伞拿上","reward":"少淋雨","deadline":"出门前"},
                          {"type":"punish","method":"今晚早点睡","count":"1","intensity":"low","reason":"昨天熬夜了"}
                        ]
                    """.trimIndent(),
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertTrue(mapped.any { it.contentType == RoleplayContentType.THOUGHT && it.content == "想见。" })
        assertTrue(mapped.any {
            it.contentType == RoleplayContentType.DIALOGUE &&
                it.replyToMessageId == "m-old" &&
                it.content == "这句我回你。"
        })
        val actionTypes = mapped.mapNotNull { it.actionPart?.actionType }
        assertTrue(actionTypes.containsAll(
            listOf(
                ChatActionType.EMOJI,
                ChatActionType.VOICE_MESSAGE,
                ChatActionType.AI_PHOTO,
                ChatActionType.LOCATION,
                ChatActionType.POKE,
                ChatActionType.VIDEO_CALL,
            ),
        ))
        val specialTypes = mapped.mapNotNull { it.specialPart?.specialType }
        assertTrue(specialTypes.containsAll(
            listOf(
                ChatSpecialType.TRANSFER,
                ChatSpecialType.INVITE,
                ChatSpecialType.GIFT,
                ChatSpecialType.TASK,
                ChatSpecialType.PUNISH,
            ),
        ))
        assertFalse(mapped.any { ui ->
            listOf("\"type\"", "ai-photo", "voice_message", "play id").any { marker ->
                ui.content.contains(marker, ignoreCase = true) ||
                    ui.copyText.contains(marker, ignoreCase = true)
            }
        })
    }

    @Test
    fun mapMessages_onlineModeChineseParenNarrationRecognized() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "闰青",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "闰青"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-narration",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = """["（闰青盯着递到眼前的手机，并没伸手去接，反而发出一声意味不明的冷哼。）","聊幽落，你是不是觉得，只要把手机递给我，我就能大度到当什么都没发生？"]""",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped[0].contentType)
        assertEquals("旁白", mapped[0].speakerName)
        assertEquals("闰青盯着递到眼前的手机，并没伸手去接，反而发出一声意味不明的冷哼。", mapped[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
    }

    @Test
    fun mapMessages_onlineModeAsciiParenNarrationRecognized() {
        val scenario = RoleplayScenario(
            id = "scene-1",
            title = "线上模式",
            characterDisplayNameOverride = "闰青",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        )

        val mapped = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = Assistant(id = "assistant-1", name = "闰青"),
            settings = AppSettings(showOnlineRoleplayNarration = true),
            rawMessages = listOf(
                ChatMessage(
                    id = "assistant-ascii-narration",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = """["(他最终还是接了过去，指尖恼怒地在屏幕上划拉了几下。)","你倒是大方。"]""",
                    createdAt = 3L,
                    status = MessageStatus.COMPLETED,
                    roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
                    roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
                ),
            ),
            streamingContent = null,
            outputParser = RoleplayOutputParser(),
            nowProvider = { 20L },
        )

        assertEquals(2, mapped.size)
        assertEquals(RoleplayContentType.NARRATION, mapped[0].contentType)
        assertEquals("旁白", mapped[0].speakerName)
        assertEquals("他最终还是接了过去，指尖恼怒地在屏幕上划拉了几下。", mapped[0].content)
        assertEquals(RoleplayContentType.DIALOGUE, mapped[1].contentType)
        assertEquals("你倒是大方。", mapped[1].content)
    }
}
