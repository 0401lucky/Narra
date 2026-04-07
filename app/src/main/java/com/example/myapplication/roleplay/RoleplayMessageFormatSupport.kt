package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.toPlainText

object RoleplayMessageFormatSupport {
    private val longformTagPattern = Regex("""(?is)<(/?)(char|thought)>""")
    private val protocolTagPattern = Regex("""(?is)<(/?)(dialogue|narration)\b""")

    fun resolveScenarioOutputFormat(
        scenario: RoleplayScenario,
    ): RoleplayOutputFormat {
        return when {
            scenario.longformModeEnabled -> RoleplayOutputFormat.LONGFORM
            scenario.enableRoleplayProtocol -> RoleplayOutputFormat.PROTOCOL
            else -> RoleplayOutputFormat.PLAIN
        }
    }

    fun resolveAssistantMessageOutputFormat(
        message: ChatMessage,
    ): RoleplayOutputFormat {
        if (message.role != MessageRole.ASSISTANT) {
            return RoleplayOutputFormat.UNSPECIFIED
        }
        return message.roleplayOutputFormat.takeIf {
            it != RoleplayOutputFormat.UNSPECIFIED
        } ?: inferLegacyAssistantOutputFormat(resolveAssistantRawContent(message))
    }

    fun resolveAssistantRawContent(
        message: ChatMessage,
    ): String {
        return message.parts.toPlainText()
            .ifBlank { message.content }
            .trim()
    }

    private fun inferLegacyAssistantOutputFormat(
        rawContent: String,
    ): RoleplayOutputFormat {
        if (rawContent.isBlank()) {
            return RoleplayOutputFormat.PLAIN
        }
        return when {
            longformTagPattern.containsMatchIn(rawContent) -> RoleplayOutputFormat.LONGFORM
            protocolTagPattern.containsMatchIn(rawContent) -> RoleplayOutputFormat.PROTOCOL
            else -> RoleplayOutputFormat.PLAIN
        }
    }
}
