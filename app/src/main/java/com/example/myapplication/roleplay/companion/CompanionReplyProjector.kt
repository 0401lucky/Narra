package com.example.myapplication.roleplay.companion

import com.example.myapplication.model.*

/** 任意合法读取窗口都可投影；不依赖当前场次、不访问模型，也不写消息或来源修订。 */
object CompanionReplyProjector {
    private val parser = CompanionReplyParser()

    fun project(
        message: ChatMessage,
        characterName: String = message.speakerName.ifBlank { "角色" }
    ): ParsedCompanionReply? {
        if (message.role != MessageRole.ASSISTANT || message.companionTurnId.isNullOrBlank() ||
            message.roleplayInteractionMode != RoleplayInteractionMode.OFFLINE_DIALOGUE ||
            message.roleplayOutputFormat == RoleplayOutputFormat.LONGFORM
        ) return null
        val metadata = message.companionReplyMetadata?.takeIf { it.matches(message) }
        val source = CompanionReplySource(
            messageId = message.id,
            conversationId = message.conversationId,
            turnId = message.companionTurnId,
            revision = message.memoryRevision,
            protocolVersion = metadata?.protocolVersion ?: 0,
            parserVersion = CompanionReplyParser.PARSER_VERSION,
            rawHash = companionRawHash(message.content),
            attemptId = metadata?.selectedAttemptId,
            configurationRevision = metadata?.presentation?.sourceSettingsRevision,
            characterId = message.speakerId,
        )
        return parser.parse(
            message.content, source, metadata?.presentation, characterName,
            allowObservation = metadata != null && message.status == MessageStatus.COMPLETED,
            initialDiagnostics = if (metadata == null) setOf(CompanionReplyDiagnostic.MISSING_SOURCE_METADATA) else emptySet(),
        )
    }

    fun project(messages: List<ChatMessage>): Map<String, ParsedCompanionReply> =
        messages.mapNotNull { message -> project(message)?.let { message.id to it } }.toMap()

    fun preview(
        attempt: CompanionAttempt,
        raw: String,
        characterName: String,
        complete: Boolean
    ): ParsedCompanionReply =
        parser.parse(
            raw,
            CompanionReplySource(
                messageId = "preview:${attempt.id}",
                conversationId = attempt.context.conversationId,
                turnId = attempt.turnId,
                revision = attempt.expectedTurnRevision,
                protocolVersion = attempt.descriptor.protocolVersion,
                parserVersion = CompanionReplyParser.PARSER_VERSION,
                rawHash = companionRawHash(raw),
                attemptId = attempt.id,
                configurationRevision = attempt.descriptor.presentation?.sourceSettingsRevision,
                characterId = attempt.context.characterId,
            ),
            attempt.descriptor.presentation, characterName, allowObservation = complete,
        )
}
