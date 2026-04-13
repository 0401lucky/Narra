package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isActionPart
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toPlainText

private val roleplayProtocolAliasPattern = Regex("""(?is)<(/?)narrative\b""")

internal fun normalizeRoleplayProtocolAliases(rawContent: String): String {
    return roleplayProtocolAliasPattern.replace(rawContent) { match ->
        "<${match.groupValues[1]}narration"
    }
}

object RoleplayMessageFormatSupport {
    private val longformSpeechTagPattern = Regex("""(?is)<(/?)char>""")
    private val sharedThoughtTagPattern = Regex("""(?is)<(/?)thought>""")
    private val sharedThoughtOnlyTagPattern = Regex("""(?is)</?thought>""")
    private val protocolStructuralTagPattern = Regex("""(?is)<(/?)(dialogue|narration)\b""")
    private val genericTagPattern = Regex("""(?is)<[^>]+>""")

    fun resolveScenarioInteractionMode(
        scenario: RoleplayScenario,
    ): RoleplayInteractionMode {
        return when {
            scenario.interactionMode == RoleplayInteractionMode.ONLINE_PHONE -> RoleplayInteractionMode.ONLINE_PHONE
            scenario.interactionMode == RoleplayInteractionMode.OFFLINE_LONGFORM || scenario.longformModeEnabled -> {
                RoleplayInteractionMode.OFFLINE_LONGFORM
            }
            else -> RoleplayInteractionMode.OFFLINE_DIALOGUE
        }
    }

    fun resolveScenarioOutputFormat(
        scenario: RoleplayScenario,
    ): RoleplayOutputFormat {
        return when (resolveScenarioInteractionMode(scenario)) {
            RoleplayInteractionMode.OFFLINE_LONGFORM -> RoleplayOutputFormat.LONGFORM
            RoleplayInteractionMode.ONLINE_PHONE -> RoleplayOutputFormat.PROTOCOL
            RoleplayInteractionMode.OFFLINE_DIALOGUE -> when {
            scenario.enableRoleplayProtocol -> RoleplayOutputFormat.PROTOCOL
                else -> RoleplayOutputFormat.PLAIN
            }
        }
    }

    fun resolveAssistantMessageOutputFormat(
        message: ChatMessage,
    ): RoleplayOutputFormat {
        if (message.role != MessageRole.ASSISTANT) {
            return RoleplayOutputFormat.UNSPECIFIED
        }
        return resolveContentOutputFormat(
            preferredFormat = message.roleplayOutputFormat,
            rawContent = resolveAssistantRawContent(message),
        )
    }

    fun resolveMessageInteractionMode(
        message: ChatMessage,
        fallbackInteractionMode: RoleplayInteractionMode,
    ): RoleplayInteractionMode {
        message.roleplayInteractionMode?.let { return it }
        if (message.role != MessageRole.ASSISTANT) {
            return fallbackInteractionMode
        }
        val outputFormat = resolveAssistantMessageOutputFormat(message)
        if (outputFormat == RoleplayOutputFormat.LONGFORM) {
            return RoleplayInteractionMode.OFFLINE_LONGFORM
        }
        if (message.systemEventKind != RoleplayOnlineEventKind.NONE) {
            return RoleplayInteractionMode.ONLINE_PHONE
        }
        val normalizedParts = normalizeChatMessageParts(message.parts)
        if (normalizedParts.any { part -> part.isOnlineThoughtPart() || part.isActionPart() }) {
            return RoleplayInteractionMode.ONLINE_PHONE
        }
        val rawContent = resolveAssistantRawContent(message)
        if (OnlineActionProtocolParser.parse(rawContent = rawContent, characterName = "角色") != null) {
            return RoleplayInteractionMode.ONLINE_PHONE
        }
        return when {
            outputFormat == RoleplayOutputFormat.PROTOCOL && fallbackInteractionMode == RoleplayInteractionMode.ONLINE_PHONE -> {
                RoleplayInteractionMode.ONLINE_PHONE
            }
            outputFormat == RoleplayOutputFormat.PROTOCOL -> RoleplayInteractionMode.OFFLINE_DIALOGUE
            else -> fallbackInteractionMode
        }
    }

    fun resolveAssistantRawContent(
        message: ChatMessage,
    ): String {
        return message.parts.toPlainText()
            .ifBlank { message.content }
            .trim()
    }

    fun resolveContentOutputFormat(
        preferredFormat: RoleplayOutputFormat,
        rawContent: String,
    ): RoleplayOutputFormat {
        val normalizedContent = normalizeRoleplayProtocolAliases(rawContent)
        if (normalizedContent.isBlank()) {
            return RoleplayOutputFormat.PLAIN
        }
        val hasProtocolStructure = protocolStructuralTagPattern.containsMatchIn(normalizedContent)
        val hasLongformSpeech = longformSpeechTagPattern.containsMatchIn(normalizedContent)
        val hasThought = sharedThoughtTagPattern.containsMatchIn(normalizedContent)
        return when {
            hasProtocolStructure -> RoleplayOutputFormat.PROTOCOL
            hasLongformSpeech -> RoleplayOutputFormat.LONGFORM
            hasThought && preferredFormat == RoleplayOutputFormat.PROTOCOL && hasVisibleTextOutsideThoughtBlocks(normalizedContent) -> {
                RoleplayOutputFormat.LONGFORM
            }
            hasThought && preferredFormat == RoleplayOutputFormat.PROTOCOL -> RoleplayOutputFormat.PROTOCOL
            hasThought && preferredFormat == RoleplayOutputFormat.LONGFORM -> RoleplayOutputFormat.LONGFORM
            hasThought -> RoleplayOutputFormat.LONGFORM
            preferredFormat != RoleplayOutputFormat.UNSPECIFIED -> preferredFormat
            else -> RoleplayOutputFormat.PLAIN
        }
    }

    private fun hasVisibleTextOutsideThoughtBlocks(
        rawContent: String,
    ): Boolean {
        return genericTagPattern
            .replace(sharedThoughtOnlyTagPattern.replace(rawContent, " "), " ")
            .any { !it.isWhitespace() }
    }
}
