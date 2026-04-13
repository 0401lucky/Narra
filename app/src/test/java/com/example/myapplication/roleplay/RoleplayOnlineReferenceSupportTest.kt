package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayOnlineReferenceSupportTest {
    private val scenario = RoleplayScenario(
        id = "scene-1",
        userDisplayNameOverride = "林晚",
        characterDisplayNameOverride = "陆宴清",
        interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        enableNarration = true,
    )

    private val assistant = Assistant(
        id = "assistant-1",
        name = "陆宴清",
    )

    @Test
    fun buildCandidates_usesFlattenedDialogueItemsFromWindow() {
        val candidates = RoleplayOnlineReferenceSupport.buildCandidates(
            messages = listOf(
                ChatMessage(
                    id = "user-1",
                    conversationId = "conv-1",
                    role = MessageRole.USER,
                    content = "你刚才想说什么？",
                    createdAt = 1L,
                    status = MessageStatus.COMPLETED,
                ),
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(
                        textMessagePart("我刚才已经看见了。"),
                        textMessagePart("别装没事。"),
                    ),
                ),
            ),
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(showOnlineRoleplayNarration = true),
            outputParser = RoleplayOutputParser(),
        )

        assertEquals(3, candidates.size)
        assertEquals("1001", candidates[0].shortId)
        assertEquals("1003", candidates[2].shortId)
        assertEquals("别装没事。", candidates[2].preview)
    }

    @Test
    fun sanitizeRequestMessages_removesThoughtPartsWhenGlobalSwitchIsOff() {
        val sanitized = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
            messages = listOf(
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(
                        thoughtMessagePart("其实已经后悔了。"),
                        textMessagePart("你先说。"),
                    ),
                ),
            ),
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(showOnlineRoleplayNarration = false),
            outputParser = RoleplayOutputParser(),
        )

        assertEquals(1, sanitized.single().parts.size)
        assertEquals("你先说。", sanitized.single().parts.single().text)
    }

    @Test
    fun sanitizeRequestMessages_dropsAssistantSystemEventsFromModelHistory() {
        val sanitized = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
            messages = listOf(
                ChatMessage(
                    id = "event-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>视频通话已结束，通话时长 00:12</narration>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    systemEventKind = RoleplayOnlineEventKind.VIDEO_CALL_ENDED,
                ),
                ChatMessage(
                    id = "assistant-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "",
                    createdAt = 3L,
                    status = MessageStatus.COMPLETED,
                    parts = listOf(textMessagePart("回去路上给我发一句。")),
                ),
            ),
            scenario = scenario,
            assistant = assistant,
            settings = AppSettings(showOnlineRoleplayNarration = true),
            outputParser = RoleplayOutputParser(),
        )

        assertEquals(1, sanitized.size)
        assertEquals("assistant-1", sanitized.single().id)
    }

    @Test
    fun buildSystemEventPromptContext_rewritesVideoEndAndScreenshotAsPlainContext() {
        val context = RoleplayOnlineReferenceSupport.buildSystemEventPromptContext(
            messages = listOf(
                ChatMessage(
                    id = "event-1",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>视频通话已结束，通话时长 00:12</narration>",
                    createdAt = 2L,
                    status = MessageStatus.COMPLETED,
                    systemEventKind = RoleplayOnlineEventKind.VIDEO_CALL_ENDED,
                ),
                ChatMessage(
                    id = "event-2",
                    conversationId = "conv-1",
                    role = MessageRole.ASSISTANT,
                    content = "<narration>你截了一张聊天截图。</narration>",
                    createdAt = 3L,
                    status = MessageStatus.COMPLETED,
                    systemEventKind = RoleplayOnlineEventKind.SCREENSHOT,
                ),
            ),
            outputParser = RoleplayOutputParser(),
        )

        assertTrue(context.contains("当前已回到普通线上聊天"))
        assertTrue(context.contains("用户刚截了一张聊天截图"))
        assertTrue(!context.contains("<narration>"))
    }

    @Test
    fun resolveReplyTargets_rewritesShortIdToRealMessageId() {
        val resolved = RoleplayOnlineReferenceSupport.resolveReplyTargets(
            parts = listOf(
                textMessagePart(
                    text = "我不是不回你。",
                    replyToMessageId = "1002",
                ),
            ),
            candidates = listOf(
                OnlineMessageReferenceCandidate(
                    shortId = "1002",
                    sourceMessageId = "assistant-1",
                    speakerName = "陆宴清",
                    preview = "别装没事。",
                ),
            ),
        )

        assertEquals("assistant-1", resolved.single().replyToMessageId)
        assertEquals("陆宴清", resolved.single().replyToSpeakerName)
        assertEquals("别装没事。", resolved.single().replyToPreview)
    }

    @Test
    fun formatCandidatesForPrompt_usesChatLikeIdShape() {
        val formatted = RoleplayOnlineReferenceSupport.formatCandidatesForPrompt(
            listOf(
                OnlineMessageReferenceCandidate(
                    shortId = "1007",
                    sourceMessageId = "assistant-1",
                    speakerName = "陆宴清",
                    preview = "你终于回我了。",
                ),
            ),
        )

        assertTrue(formatted.contains("[ID:1007]"))
        assertTrue(formatted.contains("陆宴清：你终于回我了。"))
    }
}
