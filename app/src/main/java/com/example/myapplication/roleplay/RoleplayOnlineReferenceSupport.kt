package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.toContentMirror

data class OnlineMessageReferenceCandidate(
    val shortId: String,
    val sourceMessageId: String,
    val speakerName: String,
    val preview: String,
)

data class ResolvedOnlineReplyTarget(
    val sourceMessageId: String,
    val speakerName: String,
    val preview: String,
)

internal object RoleplayOnlineReferenceSupport {
    private const val REFERENCE_ID_BASE = 1001
    private const val DEFAULT_MAX_CANDIDATES = 12

    fun buildCandidates(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        outputParser: RoleplayOutputParser,
        maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    ): List<OnlineMessageReferenceCandidate> {
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return emptyList()
        }
        val mappedMessages = RoleplayMessageUiMapper.mapMessages(
            scenario = scenario,
            assistant = assistant,
            settings = settings,
            rawMessages = messages,
            streamingContent = null,
            outputParser = outputParser,
            nowProvider = { 0L },
        )
        return mappedMessages
            .filter { message ->
                message.sourceMessageId.isNotBlank() &&
                    message.messageStatus == MessageStatus.COMPLETED &&
                    !message.isRecalled &&
                    message.systemEventKind == RoleplayOnlineEventKind.NONE &&
                    message.contentType == RoleplayContentType.DIALOGUE &&
                    message.copyText.isNotBlank()
            }
            .takeLast(maxCandidates)
            .mapIndexed { index, message ->
                OnlineMessageReferenceCandidate(
                    shortId = (REFERENCE_ID_BASE + index).toString(),
                    sourceMessageId = message.sourceMessageId,
                    speakerName = message.speakerName.trim().ifBlank { defaultSpeakerName(message, scenario, assistant, settings) },
                    preview = message.copyText
                        .replace('\n', ' ')
                        .trim()
                        .take(60),
                )
            }
    }

    fun formatCandidatesForPrompt(
        candidates: List<OnlineMessageReferenceCandidate>,
    ): String {
        if (candidates.isEmpty()) {
            return ""
        }
        return buildString {
            append("可引用消息候选：\n")
            candidates.forEach { candidate ->
                append("- [ID:")
                append(candidate.shortId)
                append("] ")
                append(candidate.speakerName.ifBlank { "对方" })
                append("：")
                append(candidate.preview)
                append('\n')
            }
        }.trim()
    }

    fun resolveReplyTargets(
        parts: List<ChatMessagePart>,
        candidates: List<OnlineMessageReferenceCandidate>,
    ): List<ChatMessagePart> {
        if (parts.isEmpty() || candidates.isEmpty()) {
            return parts
        }
        val candidateMap = candidates.associateBy { it.shortId }
        return parts.map { part ->
            val rawReplyId = part.replyToMessageId.trim()
            if (rawReplyId.isBlank()) {
                return@map part
            }
            val target = candidateMap[rawReplyId] ?: return@map part.copy(
                replyToMessageId = "",
                replyToPreview = "",
                replyToSpeakerName = "",
            )
            part.copy(
                replyToMessageId = target.sourceMessageId,
                replyToPreview = target.preview,
                replyToSpeakerName = target.speakerName,
            )
        }
    }

    fun sanitizeRequestMessages(
        messages: List<ChatMessage>,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
        outputParser: RoleplayOutputParser,
    ): List<ChatMessage> {
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return messages
        }
        val allowThought = scenario.enableNarration && settings.showOnlineRoleplayNarration
        if (allowThought) {
            return messages
        }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        return messages.map { message ->
            if (message.role != MessageRole.ASSISTANT || message.systemEventKind != RoleplayOnlineEventKind.NONE) {
                return@map message
            }
            val normalizedParts = message.parts.filterNot { it.isOnlineThoughtPart() }
            if (normalizedParts.size != message.parts.size) {
                return@map message.copy(
                    content = normalizedParts.toContentMirror(
                        imageFallback = "",
                        fileFallback = "",
                        specialFallback = "",
                    ),
                    parts = normalizedParts,
                )
            }
            if (message.parts.isNotEmpty()) {
                return@map message
            }
            val cleanedContent = outputParser.parseAssistantOutput(
                rawContent = RoleplayMessageFormatSupport.resolveAssistantRawContent(message),
                characterName = characterName,
                allowNarration = scenario.enableNarration,
            ).filter { segment ->
                segment.contentType != com.example.myapplication.model.RoleplayContentType.THOUGHT &&
                    segment.contentType != com.example.myapplication.model.RoleplayContentType.NARRATION
            }.joinToString(separator = "\n\n") { it.content.trim() }
                .trim()
            if (cleanedContent == message.content.trim()) {
                message
            } else {
                message.copy(content = cleanedContent, parts = emptyList())
            }
        }
    }

    private fun defaultSpeakerName(
        message: com.example.myapplication.model.RoleplayMessageUiModel,
        scenario: RoleplayScenario,
        assistant: Assistant?,
        settings: AppSettings,
    ): String {
        return when (message.speaker) {
            com.example.myapplication.model.RoleplaySpeaker.USER -> scenario.userDisplayNameOverride.trim()
                .ifBlank { settings.resolvedUserDisplayName() }
                .ifBlank { "你" }
            com.example.myapplication.model.RoleplaySpeaker.CHARACTER -> scenario.characterDisplayNameOverride.trim()
                .ifBlank { assistant?.name?.trim().orEmpty() }
                .ifBlank { "对方" }
            else -> message.speakerName.ifBlank { "对方" }
        }
    }
}
