package com.example.myapplication.roleplay

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.toSpecialPlayCopyText

object RoleplayMessageUiMapper {
    fun mapMessages(
        scenario: RoleplayScenario?,
        assistant: Assistant?,
        settings: AppSettings,
        rawMessages: List<ChatMessage>,
        streamingContent: String?,
        outputParser: RoleplayOutputParser,
        nowProvider: () -> Long,
    ): List<RoleplayMessageUiModel> {
        if (scenario == null) {
            return emptyList()
        }
        val userName = scenario.userDisplayNameOverride.trim()
            .ifBlank { settings.resolvedUserDisplayName() }
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }

        return buildList {
            rawMessages.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> {
                        appendRoleplayUserMessages(
                            target = this,
                            message = message,
                            userName = userName,
                        )
                    }

                    MessageRole.ASSISTANT -> {
                        appendRoleplayAssistantMessages(
                            target = this,
                            message = message,
                            scenario = scenario,
                            userName = userName,
                            characterName = characterName,
                            outputParser = outputParser,
                        )
                    }
                }
            }
            if (!streamingContent.isNullOrBlank()) {
                val streamingFormat = rawMessages
                    .lastOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.LOADING }
                    ?.let(RoleplayMessageFormatSupport::resolveAssistantMessageOutputFormat)
                    ?: RoleplayMessageFormatSupport.resolveScenarioOutputFormat(scenario)
                add(
                    RoleplayMessageUiModel(
                        sourceMessageId = "streaming",
                        contentType = if (streamingFormat == RoleplayOutputFormat.LONGFORM) {
                            RoleplayContentType.LONGFORM
                        } else {
                            RoleplayContentType.DIALOGUE
                        },
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = streamingContent,
                        createdAt = nowProvider(),
                        isStreaming = true,
                        messageStatus = MessageStatus.LOADING,
                        copyText = if (streamingFormat == RoleplayOutputFormat.LONGFORM) {
                            RoleplayLongformMarkupParser.stripMarkupForDisplay(streamingContent)
                        } else {
                            outputParser.stripMarkup(streamingContent)
                        },
                    ),
                )
            }
        }
    }

    private fun appendRoleplayUserMessages(
        target: MutableList<RoleplayMessageUiModel>,
        message: ChatMessage,
        userName: String,
    ) {
        val normalizedParts = normalizeChatMessageParts(message.parts)
        val initialSize = target.size
        if (normalizedParts.isEmpty()) {
            val content = message.content.trim().ifBlank { "（无文本内容）" }
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = userName,
                content = content,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = content,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.isSpecialPlayPart() -> {
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.SPECIAL_PLAY,
                        speaker = RoleplaySpeaker.USER,
                        speakerName = userName,
                        content = "",
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = part.toSpecialPlayCopyText(),
                        specialPart = part,
                    )
                }

                part.text.isNotBlank() -> {
                    val content = part.text.trim()
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.DIALOGUE,
                        speaker = RoleplaySpeaker.USER,
                        speakerName = userName,
                        content = content,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = content,
                    )
                }
            }
        }
        if (target.size == initialSize && message.content.isNotBlank()) {
            val content = message.content.trim()
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = userName,
                content = content,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = content,
            )
        }
    }

    private fun appendRoleplayAssistantMessages(
        target: MutableList<RoleplayMessageUiModel>,
        message: ChatMessage,
        scenario: RoleplayScenario,
        userName: String,
        characterName: String,
        outputParser: RoleplayOutputParser,
    ) {
        val normalizedParts = normalizeChatMessageParts(message.parts)
        val initialSize = target.size
        val canRetry = !RoleplayConversationSupport.isOpeningNarrationMessageId(message.id, scenario.id) &&
            (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.ERROR)
        val outputFormat = RoleplayMessageFormatSupport.resolveAssistantMessageOutputFormat(message)
        if (outputFormat == RoleplayOutputFormat.LONGFORM) {
            val specialParts = normalizedParts.filter { it.isSpecialPlayPart() }
            specialParts.forEach { part ->
                target += RoleplayMessageUiModel(
                    sourceMessageId = message.id,
                    contentType = RoleplayContentType.SPECIAL_PLAY,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = "",
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    copyText = part.toSpecialPlayCopyText(),
                    canRetry = canRetry,
                    specialPart = part,
                )
            }
            val longformSource = RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
            val displayLongformContent = RoleplayLongformMarkupParser.stripMarkupForDisplay(longformSource)
            if (message.status == MessageStatus.LOADING && displayLongformContent.isBlank()) {
                return
            }
            if (longformSource.isNotBlank()) {
                target += RoleplayMessageUiModel(
                    sourceMessageId = message.id,
                    contentType = RoleplayContentType.LONGFORM,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    content = displayLongformContent,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    copyText = displayLongformContent,
                    richTextSource = longformSource,
                    canRetry = canRetry,
                )
            }
            return
        }
        if (normalizedParts.isEmpty()) {
            val content = message.content.trim()
            if (message.status == MessageStatus.LOADING && content.isBlank()) {
                return
            }
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                rawContent = content,
                userName = userName,
                characterName = characterName,
                allowNarration = scenario.enableNarration,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
                outputParser = outputParser,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.isSpecialPlayPart() -> {
                    target += RoleplayMessageUiModel(
                        sourceMessageId = message.id,
                        contentType = RoleplayContentType.SPECIAL_PLAY,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = "",
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        copyText = part.toSpecialPlayCopyText(),
                        canRetry = canRetry,
                        specialPart = part,
                    )
                }

                part.text.isNotBlank() -> {
                    appendAssistantTextSegments(
                        target = target,
                        sourceMessageId = message.id,
                        rawContent = part.text,
                        userName = userName,
                        characterName = characterName,
                        allowNarration = scenario.enableNarration,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        canRetry = canRetry,
                        outputParser = outputParser,
                    )
                }
            }
        }
        if (target.size == initialSize && message.content.isNotBlank()) {
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                rawContent = message.content,
                userName = userName,
                characterName = characterName,
                allowNarration = scenario.enableNarration,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
                outputParser = outputParser,
            )
        }
    }

    private fun appendAssistantTextSegments(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        rawContent: String,
        userName: String,
        characterName: String,
        allowNarration: Boolean,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
        outputParser: RoleplayOutputParser,
    ) {
        val normalizedContent = rawContent.trim()
        if (normalizedContent.isBlank()) {
            return
        }
        outputParser.parseAssistantOutput(
            rawContent = normalizedContent,
            characterName = characterName,
            allowNarration = allowNarration,
        ).forEach { segment ->
            target += RoleplayMessageUiModel(
                sourceMessageId = sourceMessageId,
                contentType = segment.contentType,
                speaker = segment.speaker,
                speakerName = when (segment.speaker) {
                    RoleplaySpeaker.USER -> userName
                    RoleplaySpeaker.CHARACTER -> characterName
                    RoleplaySpeaker.NARRATOR -> "旁白"
                    RoleplaySpeaker.SYSTEM -> segment.speakerName
                },
                content = segment.content,
                emotion = segment.emotion,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = segment.content,
                canRetry = canRetry,
            )
        }
    }
}
