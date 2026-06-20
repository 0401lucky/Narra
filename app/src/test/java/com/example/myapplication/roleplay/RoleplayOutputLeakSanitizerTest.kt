package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.textMessagePart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayOutputLeakSanitizerTest {
    @Test
    fun sanitize_removesLongformFormatReminderLeak() {
        val raw = """
            【避坑】严格遵守格式要求：台词必须用 <char>“...”</char> 包裹
            `包裹，心声必须用 <thought>（...）</thought> 包裹，叙述裸写，绝对禁止使用 Markdown、JSON 等格式`

            窗外的雨停在玻璃上。
            <char>“别怕，我在。”</char>

            【推进点】展现她跳蛋被关掉瞬间的身体反应
        """.trimIndent()

        val sanitized = RoleplayOutputLeakSanitizer.sanitize(
            rawContent = raw,
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        assertFalse(sanitized.contains("避坑"))
        assertFalse(sanitized.contains("台词必须用"))
        assertFalse(sanitized.contains("Markdown"))
        assertFalse(sanitized.contains("推进点"))
        assertTrue(sanitized.contains("窗外的雨停在玻璃上。"))
        assertTrue(sanitized.contains("<char>“别怕，我在。”</char>"))
    }

    @Test
    fun sanitize_keepsNormalLongformMarkup() {
        val raw = "玄关灯亮起。\n\n<char>“回来了。”</char>\n\n<thought>（还是等到了。）</thought>"

        val sanitized = RoleplayOutputLeakSanitizer.sanitize(
            rawContent = raw,
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        assertEquals(raw, sanitized)
    }

    @Test
    fun sanitize_removesReasoningThinkBlocksWithoutTouchingThoughtMarkup() {
        val raw = """
            <think>
            先分析用户意图，再决定回复格式。
            </think>
            玄关灯亮起。

            <thought>（还是等到了。）</thought>
        """.trimIndent()

        val sanitized = RoleplayOutputLeakSanitizer.sanitize(
            rawContent = raw,
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        assertFalse(sanitized.contains("<think>"))
        assertFalse(sanitized.contains("先分析用户意图"))
        assertTrue(sanitized.contains("玄关灯亮起。"))
        assertTrue(sanitized.contains("<thought>（还是等到了。）</thought>"))
    }

    @Test
    fun sanitize_removesDanglingReasoningThinkBlockDuringStreaming() {
        val raw = "雨声压低。\n<thinking>我需要先分析人物关系"

        val sanitized = RoleplayOutputLeakSanitizer.sanitize(
            rawContent = raw,
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        assertEquals("雨声压低。", sanitized)
    }

    @Test
    fun sanitizeParts_removesBlankTextPartAfterLeakCleanup() {
        val parts = RoleplayOutputLeakSanitizer.sanitizeParts(
            parts = listOf(
                textMessagePart("【格式保持】每句台词必须用 <char> 包裹"),
                textMessagePart("<char>“回来了。”</char>"),
            ),
            interactionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        assertEquals(1, parts.size)
        assertEquals(ChatMessagePartType.TEXT, parts.single().type)
        assertEquals("<char>“回来了。”</char>", parts.single().text)
    }

    @Test
    fun sanitize_doesNotTouchOnlinePhoneJson() {
        val raw = """["别催", {"type":"voice_message","content":"马上到","duration_seconds":3}]"""

        val sanitized = RoleplayOutputLeakSanitizer.sanitize(
            rawContent = raw,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(raw, sanitized)
    }
}
