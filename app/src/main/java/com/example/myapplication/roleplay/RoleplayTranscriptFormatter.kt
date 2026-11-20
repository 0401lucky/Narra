package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.companionSourceEligibility
import com.example.myapplication.model.CompanionSourceEligibility
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.onlineThoughtContent
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.MemorySemanticKind
import com.example.myapplication.model.MemorySemanticText
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toSpecialPlayCopyText

object RoleplayTranscriptFormatter {
    private val parser = RoleplayOutputParser()

    /** 完整来源专用接口：不取最近窗口、不截尾，也不将附件地址当成语义。 */
    fun memorySemanticSegments(message: ChatMessage): List<MemorySemanticText> {
        if (message.companionSourceEligibility() != CompanionSourceEligibility.TEXT_ALLOWED) return emptyList()
        val companionSegments =
            com.example.myapplication.roleplay.companion.CompanionReplyProjector.project(message)?.beats?.map { beat ->
                MemorySemanticText(
                    beat.content,
                    when (beat.contentType) {
                        RoleplayContentType.NARRATION -> MemorySemanticKind.NARRATION
                        RoleplayContentType.THOUGHT -> MemorySemanticKind.THOUGHT
                        else -> MemorySemanticKind.SPEECH
                    },
                    beat.contentType == RoleplayContentType.THOUGHT,
                )
            }
        val parts = normalizeChatMessageParts(message.parts)
        val partSegments = parts.mapNotNull { part ->
            when (part.type) {
                ChatMessagePartType.TEXT -> {
                    val thought = part.isOnlineThoughtPart()
                    val text = if (thought) part.onlineThoughtContent() else part.text
                    text.takeIf { it.isNotBlank() }?.let {
                        MemorySemanticText(
                            it,
                            if (thought) MemorySemanticKind.THOUGHT else MemorySemanticKind.SPEECH,
                            thought
                        )
                    }
                }

                ChatMessagePartType.ACTION -> when (part.actionType) {
                    ChatActionType.VOICE_MESSAGE -> part.actionMetadata["content"].orEmpty()
                        .takeIf { it.isNotBlank() }
                        ?.let { MemorySemanticText(it, MemorySemanticKind.TRANSCRIPT) }

                    ChatActionType.AI_PHOTO -> part.actionMetadata["description"].orEmpty()
                        .takeIf { it.isNotBlank() }
                        ?.let { MemorySemanticText(it, MemorySemanticKind.DESCRIPTION) }

                    null -> null
                    else -> part.toActionCopyText().takeIf { it.isNotBlank() }
                        ?.let { MemorySemanticText(it, MemorySemanticKind.SYSTEM_EVENT) }
                }

                ChatMessagePartType.SPECIAL -> part.toSpecialPlayCopyText()
                    .takeIf { it.isNotBlank() }
                    ?.let { MemorySemanticText(it, MemorySemanticKind.SYSTEM_EVENT) }

                else -> null
            }
        }.flatMap { segment ->
            if (segment.kind == MemorySemanticKind.SPEECH && message.role == MessageRole.ASSISTANT) {
                memoryAssistantSegments(message, segment.text)
            } else listOf(segment)
        }
        val segments = when {
            companionSegments != null -> companionSegments
            partSegments.isNotEmpty() -> partSegments
            parts.isNotEmpty() || message.attachments.isNotEmpty() && message.content.isBlank() -> listOf(
                MemorySemanticText(
                    "（无可用语义内容）",
                    MemorySemanticKind.UNKNOWN,
                    skipReason = "MEDIA_WITHOUT_SEMANTICS"
                ),
            )

            message.content.isBlank() -> emptyList()
            message.role == MessageRole.USER -> listOf(
                MemorySemanticText(
                    message.content,
                    MemorySemanticKind.SPEECH
                )
            )

            message.systemEventKind != RoleplayOnlineEventKind.NONE ->
                listOf(MemorySemanticText(message.content, MemorySemanticKind.SYSTEM_EVENT))

            message.id.startsWith("rp-opening-") ->
                listOf(MemorySemanticText(message.content, MemorySemanticKind.NARRATION))

            else -> memoryAssistantSegments(message, message.content)
        }
        return segments.flatMap { segment ->
            // 只移除明确的二进制内联负载；正文中的网址本身可能是重要事实。
            val matches = inlineMediaLocations(segment.text)
            if (matches.isEmpty()) listOf(segment) else buildList {
                var start = 0
                matches.forEach { match ->
                    if (match.first > start) add(
                        segment.copy(
                            text = segment.text.substring(
                                start,
                                match.first
                            )
                        )
                    )
                    add(
                        MemorySemanticText(
                            "（媒体地址已省略）",
                            MemorySemanticKind.UNKNOWN,
                            skipReason = "MEDIA_LOCATION"
                        )
                    )
                    start = match.last + 1
                }
                if (start < segment.text.length) add(
                    segment.copy(
                        text = segment.text.substring(
                            start
                        )
                    )
                )
            }
        }.filter { it.text.isNotBlank() }
    }

    private fun memoryAssistantSegments(
        message: ChatMessage,
        text: String
    ): List<MemorySemanticText> {
        if (message.systemEventKind != RoleplayOnlineEventKind.NONE) return listOf(
            MemorySemanticText(text, MemorySemanticKind.SYSTEM_EVENT)
        )
        if (message.id.startsWith("rp-opening-")) {
            return listOf(MemorySemanticText(text, MemorySemanticKind.NARRATION))
        }
        return parser.parseAssistantOutput(text, message.speakerName.ifBlank { "角色" }, true)
            .map { segment ->
                val thought = segment.contentType == RoleplayContentType.THOUGHT ||
                        message.roleplayInteractionMode == RoleplayInteractionMode.ONLINE_PHONE && segment.contentType == RoleplayContentType.NARRATION
                MemorySemanticText(
                    segment.content, when {
                        thought -> MemorySemanticKind.THOUGHT
                        segment.contentType == RoleplayContentType.NARRATION -> MemorySemanticKind.NARRATION
                        else -> MemorySemanticKind.SPEECH
                    }, privateToSpeaker = thought
                )
            }.ifEmpty {
                listOf(
                    MemorySemanticText(
                        "（没有可用的语义正文）",
                        MemorySemanticKind.UNKNOWN,
                        skipReason = "PROTOCOL_WITHOUT_SEMANTICS"
                    )
                )
            }
    }

    private val MEDIA_LOCATION =
        Regex("data:[^\\s,]+;base64,[a-z0-9+/=_-]+", RegexOption.IGNORE_CASE)

    internal fun inlineMediaLocations(text: String): List<IntRange> =
        MEDIA_LOCATION.findAll(text).map { it.range }.toList()

    /** 请求投影不改写原文；群聊其他角色只能取得公开部分。 */
    fun visibleMessageForCharacter(
        message: ChatMessage,
        characterId: String,
        group: Boolean
    ): ChatMessage? {
        if (!group || message.role != MessageRole.ASSISTANT || message.speakerId.isNotBlank() && message.speakerId == characterId) return message
        val segments = memorySemanticSegments(
            message.copy(
                status = MessageStatus.COMPLETED,
                isRecalled = false
            )
        )
        if (segments.none { it.privateToSpeaker }) return message
        val visible = segments.filter { !it.privateToSpeaker && it.skipReason.isBlank() }
        if (visible.isEmpty()) return null
        return message.copy(
            content = visible.joinToString("\n\n") { it.text },
            parts = visible.map { com.example.myapplication.model.textMessagePart(it.text) },
            reasoningContent = "", reasoningSteps = emptyList(), attachments = emptyList(),
            replyToMessageId = "", replyToPreview = "", replyToSpeakerName = "",
        )
    }

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
        if ((message.companionTurnId.isNotBlank() || message.companionMemoryState != com.example.myapplication.model.CompanionMemoryState.LEGACY) &&
            message.companionSourceEligibility() != CompanionSourceEligibility.TEXT_ALLOWED
        ) return emptyList()
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
                com.example.myapplication.roleplay.companion.CompanionReplyProjector.project(
                    message,
                    resolvedCharacterName
                )?.let { reply ->
                    return reply.beats.map { beat ->
                        val prefix = when (beat.contentType) {
                            RoleplayContentType.NARRATION -> "旁白"
                            RoleplayContentType.THOUGHT -> "${resolvedCharacterName.ifBlank { "角色" }}心声"
                            else -> resolvedCharacterName.ifBlank { "角色" }
                        }
                        "$prefix：${beat.content}"
                    }
                }
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
                                segment.content.trim().takeIf { it.isNotBlank() }
                                    ?.let { readableContent ->
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
            .filterNot { part ->
                part.text.isNotBlank() && OnlineActionProtocolParser.isProtocolResidualText(part.text)
            }
        if (interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return normalizedParts
        }
        if (normalizedParts.any { it.isOnlineThoughtPart() }) {
            return normalizedParts
        }
        if (normalizedParts.size == 1 && normalizedParts.single().text.isNotBlank()) {
            OnlineInlineThoughtFallback.splitToParts(normalizedParts.single().text)
                ?.let { return it }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(normalizedParts.single().text)
                ?.let { return it }
        }
        if (normalizedParts.isEmpty()) {
            OnlineInlineThoughtFallback.splitToParts(message.content)?.let { return it }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(message.content)?.let { return it }
        }
        return normalizedParts
    }
}
