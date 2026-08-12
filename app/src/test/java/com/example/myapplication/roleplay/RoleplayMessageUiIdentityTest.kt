package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.emojiMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayMessageUiIdentityTest {
    @Test
    fun mapMessages_streamingPreviewContentGrowth_keepsUiItemId() {
        val loadingMessage = assistantMessage(
            id = "assistant-loading",
            content = "",
            status = MessageStatus.LOADING,
        )

        val first = mapMessages(
            rawMessages = listOf(loadingMessage),
            streamingContent = "我",
            now = 100L,
        ).single()
        val grown = mapMessages(
            rawMessages = listOf(loadingMessage),
            streamingContent = "我在楼下。",
            now = 999L,
        ).single()

        assertEquals(first.uiItemId, grown.uiItemId)
        assertTrue(first.uiItemId.contains("assistant-loading"))
    }

    @Test
    fun mapMessages_prependOtherMessage_keepsExistingUiItemIds() {
        val target = userMessage(
            id = "target",
            content = "第一段\n第二段",
            parts = listOf(textMessagePart("第一段"), textMessagePart("第二段")),
        )
        val beforePrepend = mapMessages(rawMessages = listOf(target))
        val afterPrepend = mapMessages(
            rawMessages = listOf(userMessage(id = "prefix", content = "前置消息"), target),
        )

        assertEquals(
            beforePrepend.filter { it.sourceMessageId == target.id }.map { it.uiItemId },
            afterPrepend.filter { it.sourceMessageId == target.id }.map { it.uiItemId },
        )
    }

    @Test
    fun mapMessages_deleteOtherMessage_keepsRemainingUiItemIds() {
        val removed = userMessage(id = "removed", content = "稍后删除")
        val target = userMessage(
            id = "target",
            content = "第一段\n第二段",
            parts = listOf(textMessagePart("第一段"), textMessagePart("第二段")),
        )
        val tail = assistantMessage(id = "tail", content = "还在。")
        val beforeDelete = mapMessages(rawMessages = listOf(removed, target, tail))
        val afterDelete = mapMessages(rawMessages = listOf(target, tail))

        listOf(target.id, tail.id).forEach { sourceMessageId ->
            assertEquals(
                beforeDelete.filter { it.sourceMessageId == sourceMessageId }.map { it.uiItemId },
                afterDelete.filter { it.sourceMessageId == sourceMessageId }.map { it.uiItemId },
            )
        }
    }

    @Test
    fun mapMessages_sameSourceMultipleBubbles_producesUniqueUiItemIds() {
        val message = userMessage(
            id = "multi-part",
            content = "两段文本和两个卡片",
            parts = listOf(
                textMessagePart("第一段"),
                textMessagePart("第二段"),
                emojiMessagePart(description = "轻轻挑眉", id = "action-1"),
                inviteMessagePart(
                    id = "special-1",
                    target = "用户",
                    place = "江边",
                    time = "今晚",
                ),
            ),
        )

        val mapped = mapMessages(rawMessages = listOf(message))

        assertEquals(4, mapped.size)
        assertEquals(mapped.size, mapped.map { it.uiItemId }.toSet().size)
    }

    @Test
    fun mapMessages_persistedActionAndSpecialIds_surviveEarlierPartInsertion() {
        val keptAction = emojiMessagePart(description = "微笑", id = "action-kept")
        val keptSpecial = inviteMessagePart(
            id = "special-kept",
            target = "用户",
            place = "书店",
            time = "周末",
        )
        val original = userMessage(
            id = "cards",
            content = "卡片",
            parts = listOf(keptAction, keptSpecial),
        )
        val withEarlierAction = original.copy(
            parts = listOf(
                emojiMessagePart(description = "挥手", id = "action-new"),
                keptAction,
                keptSpecial,
            ),
        )

        val originalMapped = mapMessages(rawMessages = listOf(original))
        val insertedMapped = mapMessages(rawMessages = listOf(withEarlierAction))
        val originalActionId = originalMapped.single { it.actionPart?.actionId == "action-kept" }.uiItemId
        val insertedActionId = insertedMapped.single { it.actionPart?.actionId == "action-kept" }.uiItemId
        val originalSpecialId = originalMapped.single { it.specialPart?.specialId == "special-kept" }.uiItemId
        val insertedSpecialId = insertedMapped.single { it.specialPart?.specialId == "special-kept" }.uiItemId

        assertEquals(originalActionId, insertedActionId)
        assertEquals(originalSpecialId, insertedSpecialId)
        assertTrue(originalActionId.contains("action-kept"))
        assertTrue(originalSpecialId.contains("special-kept"))
    }

    @Test
    fun mapMessages_duplicatePersistedActionIds_fallBackToDeterministicLocalOrdinals() {
        val message = userMessage(
            id = "duplicate-actions",
            content = "重复动作卡",
            parts = listOf(
                emojiMessagePart(description = "挥手", id = "duplicate-action"),
                emojiMessagePart(description = "微笑", id = "duplicate-action"),
            ),
        )

        val first = mapMessages(rawMessages = listOf(message))
        val second = mapMessages(rawMessages = listOf(message))

        assertEquals(first.map { it.uiItemId }, second.map { it.uiItemId })
        assertEquals(first.size, first.map { it.uiItemId }.toSet().size)
        assertTrue(first.none { it.uiItemId.contains("action-id") })
    }

    @Test
    fun mapMessages_duplicatePersistedSpecialIds_fallBackToDeterministicLocalOrdinals() {
        val message = userMessage(
            id = "duplicate-specials",
            content = "重复特殊卡",
            parts = listOf(
                inviteMessagePart(
                    id = "duplicate-special",
                    target = "用户",
                    place = "书店",
                    time = "周末",
                ),
                inviteMessagePart(
                    id = "duplicate-special",
                    target = "用户",
                    place = "江边",
                    time = "今晚",
                ),
            ),
        )

        val first = mapMessages(rawMessages = listOf(message))
        val second = mapMessages(rawMessages = listOf(message))

        assertEquals(first.map { it.uiItemId }, second.map { it.uiItemId })
        assertEquals(first.size, first.map { it.uiItemId }.toSet().size)
        assertTrue(first.none { it.uiItemId.contains("special-id") })
    }

    @Test
    fun mapMessages_protocolFallbackActionsAndSpecials_ignoreTransientPartIds() {
        val protocolMessage = assistantMessage(
            id = "protocol-cards",
            content = """
                [
                  {"type":"emoji","description":"轻轻挑眉"},
                  {"type":"invite","target":"用户","place":"书店","time":"周末"}
                ]
            """.trimIndent(),
        )

        val first = mapMessages(rawMessages = listOf(protocolMessage))
        val second = mapMessages(rawMessages = listOf(protocolMessage))

        assertEquals(first.map { it.uiItemId }, second.map { it.uiItemId })
        assertEquals(first.size, first.map { it.uiItemId }.toSet().size)
    }

    private fun mapMessages(
        rawMessages: List<ChatMessage>,
        streamingContent: String? = null,
        now: Long = 10L,
    ) = RoleplayMessageUiMapper.mapMessages(
        scenario = RoleplayScenario(
            id = "scene-identity",
            characterDisplayNameOverride = "陆宴清",
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            enableNarration = true,
        ),
        assistant = Assistant(id = "assistant-1", name = "陆宴清"),
        settings = AppSettings(),
        rawMessages = rawMessages,
        streamingContent = streamingContent,
        outputParser = RoleplayOutputParser(),
        nowProvider = { now },
    )

    private fun userMessage(
        id: String,
        content: String,
        parts: List<com.example.myapplication.model.ChatMessagePart> = emptyList(),
    ) = ChatMessage(
        id = id,
        conversationId = "conversation-1",
        role = MessageRole.USER,
        content = content,
        createdAt = 1L,
        parts = parts,
    )

    private fun assistantMessage(
        id: String,
        content: String,
        status: MessageStatus = MessageStatus.COMPLETED,
    ) = ChatMessage(
        id = id,
        conversationId = "conversation-1",
        role = MessageRole.ASSISTANT,
        content = content,
        status = status,
        createdAt = 2L,
        roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
        roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
    )
}
