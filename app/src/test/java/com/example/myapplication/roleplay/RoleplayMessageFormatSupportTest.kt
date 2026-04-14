package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart
import com.example.myapplication.model.RoleplayOutputFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayMessageFormatSupportTest {
    @Test
    fun resolveContentOutputFormat_protocolThoughtDoesNotMisclassifyAsLongform() {
        val format = RoleplayMessageFormatSupport.resolveContentOutputFormat(
            preferredFormat = RoleplayOutputFormat.PROTOCOL,
            rawContent = """
                <narrative>他指节轻敲了两下杯壁。</narrative>
                <thought>不能再让她绕开这个问题。</thought>
                <dialogue speaker="character">这次别再含糊过去。</dialogue>
            """.trimIndent(),
        )

        assertEquals(RoleplayOutputFormat.PROTOCOL, format)
    }

    @Test
    fun resolveContentOutputFormat_unspecifiedThoughtOnlyStillFallsBackToLongform() {
        val format = RoleplayMessageFormatSupport.resolveContentOutputFormat(
            preferredFormat = RoleplayOutputFormat.UNSPECIFIED,
            rawContent = "<thought>（不能再退了。）</thought>",
        )

        assertEquals(RoleplayOutputFormat.LONGFORM, format)
    }

    @Test
    fun resolveMessageInteractionMode_prefersStoredModeOverCurrentScene() {
        val message = ChatMessage(
            id = "assistant-1",
            role = MessageRole.ASSISTANT,
            content = "风从窗缝里挤进来。<char>“我没有忘。”</char>",
            roleplayOutputFormat = RoleplayOutputFormat.LONGFORM,
            roleplayInteractionMode = RoleplayInteractionMode.OFFLINE_LONGFORM,
        )

        val interactionMode = RoleplayMessageFormatSupport.resolveMessageInteractionMode(
            message = message,
            fallbackInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(RoleplayInteractionMode.OFFLINE_LONGFORM, interactionMode)
    }

    @Test
    fun resolveMessageInteractionMode_legacyOnlinePartsStillResolveToOnlinePhone() {
        val message = ChatMessage(
            id = "assistant-1",
            role = MessageRole.ASSISTANT,
            content = "",
            parts = listOf(
                thoughtMessagePart("刚才差点把对话框关掉。"),
                textMessagePart("你终于回了。"),
            ),
            roleplayOutputFormat = RoleplayOutputFormat.PROTOCOL,
        )

        val interactionMode = RoleplayMessageFormatSupport.resolveMessageInteractionMode(
            message = message,
            fallbackInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
        )

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, interactionMode)
    }

    @Test
    fun resolveMessageInteractionMode_onlineFallbackKeepsLegacyThoughtMarkupInOnlineMode() {
        val message = ChatMessage(
            id = "assistant-1",
            role = MessageRole.ASSISTANT,
            content = "<thought>刚才差点就直接拨过去了。</thought>\nend_of_sentence|>",
            roleplayOutputFormat = RoleplayOutputFormat.UNSPECIFIED,
        )

        val interactionMode = RoleplayMessageFormatSupport.resolveMessageInteractionMode(
            message = message,
            fallbackInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
        )

        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, interactionMode)
    }
}
