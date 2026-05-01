package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.onlineThoughtContent
import com.example.myapplication.model.toPlainText

object RoleplayTranscriptFormatter {
    private val parser = RoleplayOutputParser()

    fun formatMessageSegments(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
        interactionMode: RoleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
    ): List<String> {
        return messages
            .flatMap { message ->
                formatMessage(
                    message = message,
                    userName = userName,
                    characterName = characterName,
                    allowNarration = allowNarration,
                    interactionMode = interactionMode,
                )
            }
    }

    fun formatMessages(
        messages: List<ChatMessage>,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
        interactionMode: RoleplayInteractionMode = RoleplayInteractionMode.OFFLINE_DIALOGUE,
    ): String {
        return formatMessageSegments(
            messages = messages,
            userName = userName,
            characterName = characterName,
            allowNarration = allowNarration,
            interactionMode = interactionMode,
        )
            .joinToString(separator = "\n")
            .trim()
    }

    private fun formatMessage(
        message: ChatMessage,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
        interactionMode: RoleplayInteractionMode,
    ): List<String> {
        val resolvedInteractionMode = RoleplayMessageFormatSupport.resolveMessageInteractionMode(
            message = message,
            fallbackInteractionMode = interactionMode,
        )
        return when (message.role) {
            MessageRole.USER -> {
                val content = message.parts.toPlainText()
                    .ifBlank { message.content }
                    .trim()
                if (content.isBlank()) {
                    emptyList()
                } else {
                    listOf("${userName.ifBlank { "用户" }}：$content")
                }
            }

            MessageRole.ASSISTANT -> {
                val resolvedCharacterName = message.speakerName.trim()
                    .ifBlank { characterName }
                val normalizedParts = resolveAssistantTranscriptParts(
                    message = message,
                    interactionMode = interactionMode,
                )
                if (normalizedParts.any { it.isOnlineThoughtPart() }) {
                    return normalizedParts.mapNotNull { part ->
                        when {
                            part.isOnlineThoughtPart() -> {
                                part.onlineThoughtContent()
                                    .takeIf { it.isNotBlank() }
                                    ?.let { thought -> "${resolvedCharacterName.ifBlank { "角色" }}心声：$thought" }
                            }
                            part.text.isNotBlank() -> {
                                part.text.trim()
                                    .takeIf { it.isNotBlank() }
                                    ?.let { text -> "${resolvedCharacterName.ifBlank { "角色" }}：$text" }
                            }
                            else -> null
                        }
                    }
                }
                val content = RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
                if (content.isBlank()) {
                    emptyList()
                } else {
                    when (RoleplayMessageFormatSupport.resolveAssistantMessageOutputFormat(message)) {
                        com.example.myapplication.model.RoleplayOutputFormat.LONGFORM -> {
                            formatLongformMessage(
                                rawContent = content,
                                characterName = resolvedCharacterName,
                            )
                        }

                        else -> {
                            parser.parseAssistantOutput(
                                rawContent = content,
                                characterName = resolvedCharacterName.ifBlank { "角色" },
                                allowNarration = allowNarration,
                            ).mapNotNull { parsedSegment ->
                                val segment = normalizeAssistantSegmentForTranscript(
                                    segment = parsedSegment,
                                    message = message,
                                    interactionMode = resolvedInteractionMode,
                                    systemEventKind = message.systemEventKind,
                                    characterName = resolvedCharacterName,
                                )
                                val prefix = when {
                                    segment.contentType == RoleplayContentType.THOUGHT -> {
                                        "${resolvedCharacterName.ifBlank { "角色" }}心声"
                                    }
                                    else -> when (segment.speaker) {
                                        RoleplaySpeaker.USER -> userName.ifBlank { "用户" }
                                        RoleplaySpeaker.NARRATOR -> "旁白"
                                        RoleplaySpeaker.SYSTEM -> segment.speakerName.ifBlank { "系统" }
                                        RoleplaySpeaker.CHARACTER -> characterName.ifBlank { "角色" }
                                    }
                                }
                                segment.content.trim().takeIf { it.isNotBlank() }?.let { readableContent ->
                                    "$prefix：$readableContent"
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun formatLongformMessage(
        rawContent: String,
        characterName: String,
    ): List<String> {
        val normalizedCharacterName = characterName.ifBlank { "角色" }
        return RoleplayLongformMarkupParser.stripMarkupForDisplay(rawContent)
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty {
                listOf(RoleplayLongformMarkupParser.stripMarkupForDisplay(rawContent))
            }
            .mapNotNull { paragraph ->
                paragraph.takeIf { it.isNotBlank() }?.let {
                    "$normalizedCharacterName：$it"
                }
            }
    }

    private fun normalizeAssistantSegmentForTranscript(
        segment: RoleplayParsedSegment,
        message: ChatMessage,
        interactionMode: RoleplayInteractionMode,
        systemEventKind: RoleplayOnlineEventKind,
        characterName: String,
    ): RoleplayParsedSegment {
        if (interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return segment
        }
        if (systemEventKind != RoleplayOnlineEventKind.NONE) {
            return segment.copy(
                contentType = RoleplayContentType.NARRATION,
                speaker = RoleplaySpeaker.NARRATOR,
                speakerName = "旁白",
            )
        }
        if (message.id.startsWith("rp-opening-")) {
            return segment.copy(
                contentType = RoleplayContentType.NARRATION,
                speaker = RoleplaySpeaker.NARRATOR,
                speakerName = "旁白",
            )
        }
        return when (segment.contentType) {
            RoleplayContentType.NARRATION -> segment.copy(
                contentType = RoleplayContentType.THOUGHT,
                speaker = RoleplaySpeaker.CHARACTER,
                speakerName = characterName,
            )
            else -> segment
        }
    }

    private fun resolveAssistantTranscriptParts(
        message: ChatMessage,
        interactionMode: RoleplayInteractionMode,
    ): List<com.example.myapplication.model.ChatMessagePart> {
        val normalizedParts = normalizeChatMessageParts(message.parts)
        if (interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return normalizedParts
        }
        if (normalizedParts.any { it.isOnlineThoughtPart() }) {
            return normalizedParts
        }
        if (normalizedParts.size == 1 && normalizedParts.single().text.isNotBlank()) {
            OnlineInlineThoughtFallback.splitToParts(normalizedParts.single().text)?.let { return it }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(normalizedParts.single().text)?.let { return it }
        }
        if (normalizedParts.isEmpty()) {
            OnlineInlineThoughtFallback.splitToParts(message.content)?.let { return it }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(message.content)?.let { return it }
        }
        return normalizedParts
    }
}
