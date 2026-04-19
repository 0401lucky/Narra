package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayFormatReminderSupportTest {

    @Test
    fun buildReminderText_onlinePhoneReturnsJsonArrayReminder() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        assertNotNull(reminder)
        assertTrue(reminder!!.contains("JSON 数组"))
        assertTrue(reminder.contains("[]"))
    }

    @Test
    fun buildReminderText_longformModeEnabledReturnsCharThoughtReminder() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                longformModeEnabled = true,
            ),
        )
        assertNotNull(reminder)
        assertTrue(reminder!!.contains("<char>"))
        assertTrue(reminder.contains("<thought>"))
    }

    @Test
    fun buildReminderText_offlineLongformModeReturnsCharThoughtReminder() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
                longformModeEnabled = false,
            ),
        )
        assertNotNull(reminder)
        assertTrue(reminder!!.contains("<char>"))
        assertTrue(reminder.contains("<thought>"))
    }

    @Test
    fun buildReminderText_roleplayProtocolWithNarrationIncludesNarrationTag() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                longformModeEnabled = false,
                enableRoleplayProtocol = true,
                enableNarration = true,
            ),
        )
        assertNotNull(reminder)
        assertTrue(reminder!!.contains("<dialogue speaker=\"character\""))
        assertTrue(reminder.contains("<narration>"))
    }

    @Test
    fun buildReminderText_roleplayProtocolWithoutNarrationForbidsNarrationTag() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                longformModeEnabled = false,
                enableRoleplayProtocol = true,
                enableNarration = false,
            ),
        )
        assertNotNull(reminder)
        assertTrue(reminder!!.contains("<dialogue speaker=\"character\""))
        assertTrue(reminder.contains("禁用 <narration>"))
    }

    @Test
    fun buildReminderText_defaultPlainTextModeReturnsNull() {
        val reminder = RoleplayFormatReminderSupport.buildReminderText(
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                longformModeEnabled = false,
                enableRoleplayProtocol = false,
            ),
        )
        assertNull(reminder)
    }

    @Test
    fun injectIntoLatestUser_prefixesLatestUserContentWhenPartsEmpty() {
        val messages = listOf(
            ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "风过无声"),
            ChatMessage(id = "u1", role = MessageRole.USER, content = "你在想什么？"),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
            ),
        )
        assertEquals(2, result.size)
        assertEquals(messages[0], result[0])
        val injected = result[1]
        assertTrue(injected.content.startsWith("<format_reminder>"))
        assertTrue(injected.content.contains("</format_reminder>"))
        assertTrue(injected.content.endsWith("你在想什么？"))
    }

    @Test
    fun injectIntoLatestUser_prefixesFirstTextPartWhenPartsPresent() {
        val messages = listOf(
            ChatMessage(
                id = "u1",
                role = MessageRole.USER,
                content = "忽略此 content",
                parts = listOf(
                    textMessagePart("第一段文本"),
                    textMessagePart("第二段文本"),
                ),
            ),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        val injected = result.single()
        assertEquals(2, injected.parts.size)
        assertTrue(injected.parts[0].text.startsWith("<format_reminder>"))
        assertTrue(injected.parts[0].text.endsWith("第一段文本"))
        assertEquals("第二段文本", injected.parts[1].text)
        // content 不应被改（因为 parts 分支生效）
        assertEquals("忽略此 content", injected.content)
    }

    @Test
    fun injectIntoLatestUser_prependsTextPartWhenOnlyNonTextParts() {
        val imagePart = imageMessagePart(
            uri = "data:image/png;base64,AAAA",
            mimeType = "image/png",
        )
        val messages = listOf(
            ChatMessage(
                id = "u1",
                role = MessageRole.USER,
                content = "",
                parts = listOf(imagePart),
            ),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        val injected = result.single()
        assertEquals(2, injected.parts.size)
        assertEquals(ChatMessagePartType.TEXT, injected.parts[0].type)
        assertTrue(injected.parts[0].text.startsWith("<format_reminder>"))
        assertEquals(imagePart, injected.parts[1])
    }

    @Test
    fun injectIntoLatestUser_onlyTouchesLatestUserMessage() {
        val messages = listOf(
            ChatMessage(id = "u1", role = MessageRole.USER, content = "早一些的用户消息"),
            ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "助手回复"),
            ChatMessage(id = "u2", role = MessageRole.USER, content = "最新的用户消息"),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        assertEquals(messages[0], result[0])
        assertEquals(messages[1], result[1])
        assertNotEquals(messages[2], result[2])
        assertTrue(result[2].content.endsWith("最新的用户消息"))
    }

    @Test
    fun injectIntoLatestUser_returnsOriginalWhenReminderIsNull() {
        val messages = listOf(
            ChatMessage(id = "u1", role = MessageRole.USER, content = "你好"),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
                longformModeEnabled = false,
                enableRoleplayProtocol = false,
            ),
        )
        assertSame(messages, result)
    }

    @Test
    fun injectIntoLatestUser_returnsOriginalWhenNoUserMessage() {
        val messages = listOf(
            ChatMessage(id = "a1", role = MessageRole.ASSISTANT, content = "仅助手消息"),
        )
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = messages,
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        assertSame(messages, result)
    }

    @Test
    fun injectIntoLatestUser_returnsOriginalForEmptyList() {
        val result = RoleplayFormatReminderSupport.injectIntoLatestUser(
            messages = emptyList(),
            scenario = RoleplayScenario(
                id = "s",
                interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
            ),
        )
        assertTrue(result.isEmpty())
    }
}
