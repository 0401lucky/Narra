package com.example.myapplication.roleplay

import com.example.myapplication.data.repository.ai.ChatStatusBlockParser
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.actionMetadataValue
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.model.isActionPart
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.onlineThoughtContent
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.toSpecialPlayCopyText
import com.example.myapplication.model.pokeSuffix
import com.example.myapplication.model.pokeTarget
import com.example.myapplication.model.voiceMessageContent

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
        val userName = RoleplayConversationSupport.resolveUserPersona(scenario, settings).displayName
        val characterName = scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
        val currentScenarioInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario)

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
                val loadingMessage = rawMessages.lastOrNull {
                    it.role == MessageRole.ASSISTANT && it.status == MessageStatus.LOADING
                }
                val streamingInteractionMode = loadingMessage?.let {
                    RoleplayMessageFormatSupport.resolveMessageInteractionMode(
                        message = it,
                        fallbackInteractionMode = currentScenarioInteractionMode,
                    )
                } ?: currentScenarioInteractionMode
                val streamingThoughtContent = streamingThoughtPreviewContent(
                    interactionMode = streamingInteractionMode,
                    rawContent = streamingContent,
                )
                add(
                    RoleplayMessageUiModel(
                        sourceMessageId = "streaming",
                        contentType = when {
                            streamingInteractionMode == RoleplayInteractionMode.OFFLINE_LONGFORM -> {
                                RoleplayContentType.LONGFORM
                            }
                            streamingThoughtContent != null -> RoleplayContentType.THOUGHT
                            else -> RoleplayContentType.DIALOGUE
                        },
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = streamingThoughtContent ?: streamingContent,
                        // 复用 loadingMessage 的 createdAt，保持流式期间 UI key 稳定，
                        // 避免 LazyColumn.animateItem 在每次 ContentDelta 重组时产生残留幽灵条目。
                        createdAt = loadingMessage?.createdAt ?: nowProvider(),
                        isStreaming = true,
                        messageStatus = MessageStatus.LOADING,
                        copyText = if (streamingInteractionMode == RoleplayInteractionMode.OFFLINE_LONGFORM) {
                            RoleplayLongformMarkupParser.stripMarkupForDisplay(streamingContent)
                        } else if (streamingThoughtContent != null) {
                            streamingThoughtContent
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
        if (message.isRecalled) {
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.NARRATION,
                speaker = RoleplaySpeaker.SYSTEM,
                speakerName = "系统",
                content = message.content.ifBlank { "你撤回了一条消息" },
                isRecalled = true,
                systemEventKind = message.systemEventKind,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = "",
            )
            return
        }
        if (normalizedParts.isEmpty()) {
            val content = message.content.trim().ifBlank { "（无文本内容）" }
            target += RoleplayMessageUiModel(
                sourceMessageId = message.id,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.USER,
                speakerName = userName,
                content = content,
                replyToMessageId = message.replyToMessageId,
                replyToPreview = message.replyToPreview,
                replyToSpeakerName = message.replyToSpeakerName,
                isRecalled = message.isRecalled,
                systemEventKind = message.systemEventKind,
                createdAt = message.createdAt,
                messageStatus = message.status,
                copyText = content,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.isActionPart() -> {
                    target += part.toRoleplayActionUiModel(
                        sourceMessageId = message.id,
                        speaker = RoleplaySpeaker.USER,
                        speakerName = userName,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                    )
                }

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
                        replyToMessageId = part.replyToMessageId.ifBlank { message.replyToMessageId },
                        replyToPreview = part.replyToPreview.ifBlank { message.replyToPreview },
                        replyToSpeakerName = part.replyToSpeakerName.ifBlank { message.replyToSpeakerName },
                        isRecalled = message.isRecalled,
                        systemEventKind = message.systemEventKind,
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
                replyToMessageId = message.replyToMessageId,
                replyToPreview = message.replyToPreview,
                replyToSpeakerName = message.replyToSpeakerName,
                isRecalled = message.isRecalled,
                systemEventKind = message.systemEventKind,
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
        val currentScenarioInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario)
        val normalizedParts = resolveAssistantDisplayParts(
            message = message,
            interactionMode = currentScenarioInteractionMode,
        )
        val initialSize = target.size
        val canRetry = !RoleplayConversationSupport.isOpeningNarrationMessageId(message.id, scenario.id) &&
            (message.status == MessageStatus.COMPLETED || message.status == MessageStatus.ERROR)
        val rawContent = RoleplayMessageFormatSupport.resolveAssistantRawContent(message)
        val outputFormat = RoleplayMessageFormatSupport.resolveAssistantMessageOutputFormat(message)
        val messageInteractionMode = RoleplayMessageFormatSupport.resolveMessageInteractionMode(
            message = message,
            fallbackInteractionMode = currentScenarioInteractionMode,
        )
        if (message.status == MessageStatus.ERROR) {
            appendAssistantErrorMessage(
                target = target,
                message = message,
                characterName = characterName,
                canRetry = canRetry,
                outputParser = outputParser,
            )
            return
        }
        if (outputFormat == RoleplayOutputFormat.LONGFORM &&
            messageInteractionMode == RoleplayInteractionMode.OFFLINE_LONGFORM
        ) {
            normalizedParts.filter { it.isActionPart() }.forEach { part ->
                target += part.toRoleplayActionUiModel(
                    sourceMessageId = message.id,
                    speaker = RoleplaySpeaker.CHARACTER,
                    speakerName = characterName,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    canRetry = canRetry,
                )
            }
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
            val longformSource = rawContent
            val longformParts = ChatStatusBlockParser.extract(longformSource, hideStatusBlocksInBubble = true)
            if (longformParts.any { it.type == ChatMessagePartType.STATUS }) {
                longformParts.forEach { part ->
                    when (part.type) {
                        ChatMessagePartType.STATUS -> {
                            target += part.toRoleplayStatusUiModel(
                                sourceMessageId = message.id,
                                createdAt = message.createdAt,
                                messageStatus = message.status,
                                canRetry = canRetry,
                            )
                        }

                        ChatMessagePartType.TEXT -> {
                            appendLongformTextUiModel(
                                target = target,
                                sourceMessageId = message.id,
                                characterName = characterName,
                                longformSource = part.text,
                                createdAt = message.createdAt,
                                messageStatus = message.status,
                                canRetry = canRetry,
                            )
                        }

                        else -> Unit
                    }
                }
            } else {
                appendLongformTextUiModel(
                    target = target,
                    sourceMessageId = message.id,
                    characterName = characterName,
                    longformSource = longformSource,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    canRetry = canRetry,
                )
            }
            return
        }
        if (normalizedParts.isEmpty()) {
            val content = rawContent
            if (message.status == MessageStatus.LOADING && content.isBlank()) {
                return
            }
            if (
                messageInteractionMode == RoleplayInteractionMode.ONLINE_PHONE &&
                message.systemEventKind == RoleplayOnlineEventKind.NONE &&
                appendOnlineProtocolAssistantMessages(
                    target = target,
                    sourceMessageId = message.id,
                    rawContent = content,
                    characterName = characterName,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    canRetry = canRetry,
                )
            ) {
                return
            }
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                scenarioId = scenario.id,
                rawContent = content,
                userName = userName,
                characterName = characterName,
                interactionMode = messageInteractionMode,
                allowNarration = scenario.enableNarration,
                isRecalled = message.isRecalled,
                systemEventKind = message.systemEventKind,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
                outputParser = outputParser,
            )
            return
        }

        normalizedParts.forEach { part ->
            when {
                part.type == ChatMessagePartType.STATUS -> {
                    target += part.toRoleplayStatusUiModel(
                        sourceMessageId = message.id,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        canRetry = canRetry,
                    )
                }

                part.isActionPart() -> {
                    target += part.toRoleplayActionUiModel(
                        sourceMessageId = message.id,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        canRetry = canRetry,
                    )
                }

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
                    if (part.isOnlineThoughtPart()) {
                        target += RoleplayMessageUiModel(
                            sourceMessageId = message.id,
                            contentType = RoleplayContentType.THOUGHT,
                            speaker = RoleplaySpeaker.CHARACTER,
                            speakerName = characterName,
                            content = part.onlineThoughtContent(),
                            createdAt = message.createdAt,
                            messageStatus = message.status,
                            copyText = part.onlineThoughtContent(),
                            canRetry = canRetry,
                        )
                        return@forEach
                    }
                    if (
                        messageInteractionMode == RoleplayInteractionMode.ONLINE_PHONE &&
                        message.systemEventKind == RoleplayOnlineEventKind.NONE &&
                        appendOnlineProtocolAssistantMessages(
                            target = target,
                            sourceMessageId = message.id,
                            rawContent = part.text,
                            characterName = characterName,
                            createdAt = message.createdAt,
                            messageStatus = message.status,
                            canRetry = canRetry,
                        )
                    ) {
                        return@forEach
                    }
                    appendAssistantTextSegments(
                        target = target,
                        sourceMessageId = message.id,
                        scenarioId = scenario.id,
                        rawContent = part.text,
                        userName = userName,
                        characterName = characterName,
                        interactionMode = messageInteractionMode,
                        allowNarration = scenario.enableNarration,
                        isRecalled = message.isRecalled,
                        systemEventKind = message.systemEventKind,
                        createdAt = message.createdAt,
                        messageStatus = message.status,
                        canRetry = canRetry,
                        outputParser = outputParser,
                    )
                }
            }
        }
        if (target.size == initialSize && message.content.isNotBlank()) {
            if (
                messageInteractionMode == RoleplayInteractionMode.ONLINE_PHONE &&
                message.systemEventKind == RoleplayOnlineEventKind.NONE &&
                appendOnlineProtocolAssistantMessages(
                    target = target,
                    sourceMessageId = message.id,
                    rawContent = message.content,
                    characterName = characterName,
                    createdAt = message.createdAt,
                    messageStatus = message.status,
                    canRetry = canRetry,
                )
            ) {
                return
            }
            appendAssistantTextSegments(
                target = target,
                sourceMessageId = message.id,
                scenarioId = scenario.id,
                rawContent = message.content,
                userName = userName,
                characterName = characterName,
                interactionMode = messageInteractionMode,
                allowNarration = scenario.enableNarration,
                isRecalled = message.isRecalled,
                systemEventKind = message.systemEventKind,
                createdAt = message.createdAt,
                messageStatus = message.status,
                canRetry = canRetry,
                outputParser = outputParser,
            )
        }
    }

    private fun appendOnlineProtocolAssistantMessages(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        rawContent: String,
        characterName: String,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
    ): Boolean {
        val statusSplitParts = ChatStatusBlockParser.extract(
            text = rawContent,
            hideStatusBlocksInBubble = true,
        )
        if (statusSplitParts.any { it.type == ChatMessagePartType.STATUS }) {
            statusSplitParts.forEach { part ->
                when (part.type) {
                    ChatMessagePartType.STATUS -> {
                        target += part.toRoleplayStatusUiModel(
                            sourceMessageId = sourceMessageId,
                            createdAt = createdAt,
                            messageStatus = messageStatus,
                            canRetry = canRetry,
                        )
                    }

                    ChatMessagePartType.TEXT -> {
                        if (
                            !appendOnlineProtocolAssistantMessages(
                                target = target,
                                sourceMessageId = sourceMessageId,
                                rawContent = part.text,
                                characterName = characterName,
                                createdAt = createdAt,
                                messageStatus = messageStatus,
                                canRetry = canRetry,
                            )
                        ) {
                            appendOnlineTextPartUiModel(
                                target = target,
                                sourceMessageId = sourceMessageId,
                                part = part,
                                characterName = characterName,
                                createdAt = createdAt,
                                messageStatus = messageStatus,
                                canRetry = canRetry,
                            )
                        }
                    }

                    else -> Unit
                }
            }
            return true
        }
        val protocolResult = OnlineActionProtocolParser.parseWithFallback(
            rawContent = rawContent,
            characterName = characterName,
        ) ?: return false
        protocolResult.parts.forEach { part ->
            when {
                part.isActionPart() -> {
                    target += part.toRoleplayActionUiModel(
                        sourceMessageId = sourceMessageId,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        createdAt = createdAt,
                        messageStatus = messageStatus,
                        canRetry = canRetry,
                    )
                }

                part.isSpecialPlayPart() -> {
                    target += RoleplayMessageUiModel(
                        sourceMessageId = sourceMessageId,
                        contentType = RoleplayContentType.SPECIAL_PLAY,
                        speaker = RoleplaySpeaker.CHARACTER,
                        speakerName = characterName,
                        content = "",
                        createdAt = createdAt,
                        messageStatus = messageStatus,
                        copyText = part.toSpecialPlayCopyText(),
                        canRetry = canRetry,
                        specialPart = part,
                    )
                }

                part.text.isNotBlank() -> {
                    appendOnlineTextPartUiModel(
                        target = target,
                        sourceMessageId = sourceMessageId,
                        part = part,
                        characterName = characterName,
                        createdAt = createdAt,
                        messageStatus = messageStatus,
                        canRetry = canRetry,
                    )
                }
            }
        }
        return true
    }

    private fun appendOnlineTextPartUiModel(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        part: ChatMessagePart,
        characterName: String,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
    ) {
        if (part.isOnlineThoughtPart()) {
            val thoughtContent = part.onlineThoughtContent()
            target += RoleplayMessageUiModel(
                sourceMessageId = sourceMessageId,
                contentType = RoleplayContentType.THOUGHT,
                speaker = RoleplaySpeaker.CHARACTER,
                speakerName = characterName,
                content = thoughtContent,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = thoughtContent,
                canRetry = canRetry,
            )
            return
        }
        val content = part.text.trim()
        if (content.isBlank()) {
            return
        }
        val narrationContent = extractOnlineNarrationContent(content)
        if (narrationContent != null) {
            target += RoleplayMessageUiModel(
                sourceMessageId = sourceMessageId,
                contentType = RoleplayContentType.NARRATION,
                speaker = RoleplaySpeaker.NARRATOR,
                speakerName = "旁白",
                content = narrationContent,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = narrationContent,
                canRetry = canRetry,
            )
        } else {
            target += RoleplayMessageUiModel(
                sourceMessageId = sourceMessageId,
                contentType = RoleplayContentType.DIALOGUE,
                speaker = RoleplaySpeaker.CHARACTER,
                speakerName = characterName,
                content = content,
                replyToMessageId = part.replyToMessageId,
                replyToPreview = part.replyToPreview,
                replyToSpeakerName = part.replyToSpeakerName,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = content,
                canRetry = canRetry,
            )
        }
    }

    private fun appendAssistantErrorMessage(
        target: MutableList<RoleplayMessageUiModel>,
        message: ChatMessage,
        characterName: String,
        canRetry: Boolean,
        outputParser: RoleplayOutputParser,
    ) {
        val content = buildString {
            val normalizedParts = normalizeChatMessageParts(message.parts)
            if (normalizedParts.isNotEmpty()) {
                append(
                    normalizedParts.joinToString(separator = "\n\n") { part ->
                        part.onlineThoughtContent().takeIf { part.isOnlineThoughtPart() }
                            ?: part.text.trim()
                    }.trim(),
                )
            } else {
                append(outputParser.stripMarkup(message.content))
            }
        }.trim().ifBlank {
            message.content.trim().ifBlank { "发送失败" }
        }
        target += RoleplayMessageUiModel(
            sourceMessageId = message.id,
            contentType = RoleplayContentType.DIALOGUE,
            speaker = RoleplaySpeaker.CHARACTER,
            speakerName = characterName,
            content = content,
            createdAt = message.createdAt,
            messageStatus = message.status,
            copyText = content,
            canRetry = canRetry,
        )
    }

    private fun resolveAssistantDisplayParts(
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
            OnlineInlineThoughtFallback.splitToParts(normalizedParts.single().text)?.let { fallbackParts ->
                return normalizeChatMessageParts(fallbackParts)
            }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(normalizedParts.single().text)?.let { fallbackParts ->
                return normalizeChatMessageParts(fallbackParts)
            }
        }
        if (normalizedParts.isEmpty()) {
            OnlineInlineThoughtFallback.splitToParts(message.content)?.let { fallbackParts ->
                return normalizeChatMessageParts(fallbackParts)
            }
            OnlineInlineThoughtFallback.splitDialogueOnlyToParts(message.content)?.let { fallbackParts ->
                return normalizeChatMessageParts(fallbackParts)
            }
        }
        return normalizedParts
    }

    private fun streamingThoughtPreviewContent(
        interactionMode: RoleplayInteractionMode,
        rawContent: String,
    ): String? {
        if (interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            return null
        }
        val lines = rawContent.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty() || lines.any { !it.startsWith("心声：") }) {
            return null
        }
        return lines.joinToString(separator = "\n\n") { line ->
            line.removePrefix("心声：").trim()
        }.trim().takeIf { it.isNotBlank() }
    }

    private fun appendAssistantTextSegments(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        scenarioId: String,
        rawContent: String,
        userName: String,
        characterName: String,
        interactionMode: RoleplayInteractionMode,
        allowNarration: Boolean,
        isRecalled: Boolean,
        systemEventKind: RoleplayOnlineEventKind,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
        outputParser: RoleplayOutputParser,
    ) {
        val normalizedContent = rawContent.trim()
        if (normalizedContent.isBlank()) {
            return
        }
        val statusSplitParts = ChatStatusBlockParser.extract(
            text = normalizedContent,
            hideStatusBlocksInBubble = true,
        )
        if (statusSplitParts.any { it.type == ChatMessagePartType.STATUS }) {
            statusSplitParts.forEach { part ->
                when (part.type) {
                    ChatMessagePartType.STATUS -> {
                        target += part.toRoleplayStatusUiModel(
                            sourceMessageId = sourceMessageId,
                            createdAt = createdAt,
                            messageStatus = messageStatus,
                            canRetry = canRetry,
                        )
                    }

                    ChatMessagePartType.TEXT -> {
                        appendAssistantTextSegments(
                            target = target,
                            sourceMessageId = sourceMessageId,
                            scenarioId = scenarioId,
                            rawContent = part.text,
                            userName = userName,
                            characterName = characterName,
                            interactionMode = interactionMode,
                            allowNarration = allowNarration,
                            isRecalled = isRecalled,
                            systemEventKind = systemEventKind,
                            createdAt = createdAt,
                            messageStatus = messageStatus,
                            canRetry = canRetry,
                            outputParser = outputParser,
                        )
                    }

                    else -> Unit
                }
            }
            return
        }
        outputParser.parseAssistantOutput(
            rawContent = normalizedContent,
            characterName = characterName,
            allowNarration = allowNarration,
        ).forEach { parsedSegment ->
            val segment = normalizeAssistantSegment(
                segment = parsedSegment,
                sourceMessageId = sourceMessageId,
                scenarioId = scenarioId,
                interactionMode = interactionMode,
                systemEventKind = systemEventKind,
                characterName = characterName,
            )
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
                replyToMessageId = segment.replyToMessageId,
                replyToPreview = segment.replyToPreview,
                replyToSpeakerName = segment.replyToSpeakerName,
                isRecalled = isRecalled,
                systemEventKind = systemEventKind,
                emotion = segment.emotion,
                createdAt = createdAt,
                messageStatus = messageStatus,
                copyText = segment.content,
                canRetry = canRetry,
            )
        }
    }

    private fun normalizeAssistantSegment(
        segment: RoleplayParsedSegment,
        sourceMessageId: String,
        scenarioId: String,
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
        if (RoleplayConversationSupport.isOpeningNarrationMessageId(sourceMessageId, scenarioId)) {
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

    private fun appendLongformTextUiModel(
        target: MutableList<RoleplayMessageUiModel>,
        sourceMessageId: String,
        characterName: String,
        longformSource: String,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
    ) {
        val displayLongformContent = RoleplayLongformMarkupParser.stripMarkupForDisplay(longformSource)
        if (messageStatus == MessageStatus.LOADING && displayLongformContent.isBlank()) {
            return
        }
        if (longformSource.isBlank()) {
            return
        }
        target += RoleplayMessageUiModel(
            sourceMessageId = sourceMessageId,
            contentType = RoleplayContentType.LONGFORM,
            speaker = RoleplaySpeaker.CHARACTER,
            speakerName = characterName,
            content = displayLongformContent,
            createdAt = createdAt,
            messageStatus = messageStatus,
            copyText = displayLongformContent,
            richTextSource = longformSource,
            canRetry = canRetry,
        )
    }

    private fun ChatMessagePart.toRoleplayStatusUiModel(
        sourceMessageId: String,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean,
    ): RoleplayMessageUiModel {
        val rawStatus = specialMetadataValue("raw").ifBlank { text }.trim()
        val title = specialMetadataValue("title").ifBlank { "状态" }
        return RoleplayMessageUiModel(
            sourceMessageId = sourceMessageId,
            contentType = RoleplayContentType.STATUS,
            speaker = RoleplaySpeaker.SYSTEM,
            speakerName = title,
            content = rawStatus,
            createdAt = createdAt,
            messageStatus = messageStatus,
            copyText = rawStatus,
            canRetry = canRetry,
            specialPart = this,
        )
    }

    private fun ChatMessagePart.toRoleplayActionUiModel(
        sourceMessageId: String,
        speaker: RoleplaySpeaker,
        speakerName: String,
        createdAt: Long,
        messageStatus: MessageStatus,
        canRetry: Boolean = false,
    ): RoleplayMessageUiModel {
        return RoleplayMessageUiModel(
            sourceMessageId = sourceMessageId,
            contentType = RoleplayContentType.ACTION,
            speaker = speaker,
            speakerName = speakerName,
            content = when (actionType) {
                ChatActionType.EMOJI -> actionMetadataValue("description")
                ChatActionType.VOICE_MESSAGE -> voiceMessageContent()
                ChatActionType.AI_PHOTO -> actionMetadataValue("description")
                ChatActionType.LOCATION -> actionMetadataValue("location_name")
                ChatActionType.POKE -> buildString {
                    val target = pokeTarget()
                    val suffix = pokeSuffix()
                    if (suffix.isBlank() && target.isBlank()) {
                        append("戳了戳你")
                    } else {
                        append("拍了拍")
                        when (target.lowercase()) {
                            "自己", "self" -> append("自己")
                            "用户", "user", "对方" -> append("你")
                            else -> if (target.isNotBlank()) append(target) else append("你")
                        }
                        if (suffix.isNotBlank()) append(suffix)
                    }
                }
                ChatActionType.VIDEO_CALL -> actionMetadataValue("reason")
                null -> ""
            },
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
            createdAt = createdAt,
            messageStatus = messageStatus,
            copyText = toActionCopyText(),
            canRetry = canRetry,
            actionPart = this,
        )
    }

    // 模型在线上模式输出叙述性旁白文本时，可能用【】、（）或 () 包裹。
    // 检测到此模式后返回剥离括号的内容，用于以旁白气泡（斜体）渲染。
    private val narrationBracketPairs = listOf(
        "【" to "】",
        "（" to "）",
        "(" to ")",
    )

    private fun extractOnlineNarrationContent(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.length < 3) return null
        // 排除心声前缀——这些已由 inlineThoughtPrefixes 处理
        if (trimmed.startsWith("【心声】")) return null
        val pair = narrationBracketPairs.firstOrNull { (open, close) ->
            trimmed.startsWith(open) && trimmed.endsWith(close)
        } ?: return null
        val inner = trimmed.removePrefix(pair.first).removeSuffix(pair.second).trim()
        return inner.takeIf { it.isNotBlank() }
    }
}
