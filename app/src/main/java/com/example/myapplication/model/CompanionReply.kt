package com.example.myapplication.model

import java.security.MessageDigest

fun companionRawHash(raw: String): String = MessageDigest.getInstance("SHA-256")
    .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

/** 读取时由真实关联图提供，JSON 导入不会恢复该瞬时字段。它不授予机器来源资格。 */
data class CompanionReplyMetadata(
    val messageId: String,
    val conversationId: String,
    val turnId: String,
    val sourceRevision: Long,
    val rawHash: String,
    val speakerId: String,
    val outputFormat: RoleplayOutputFormat,
    val interactionMode: RoleplayInteractionMode?,
    val selectedAttemptId: String,
    val protocolVersion: Int,
    val presentation: CompanionPresentationSnapshot?,
) {
    fun matches(message: ChatMessage): Boolean = message.role == MessageRole.ASSISTANT &&
            message.id == messageId && message.conversationId == conversationId &&
            message.companionTurnId == turnId && message.memoryRevision == sourceRevision &&
            message.speakerId == speakerId && message.roleplayOutputFormat == outputFormat &&
            message.roleplayInteractionMode == interactionMode &&
            message.companionMemoryState == CompanionMemoryState.READY &&
            message.status == MessageStatus.COMPLETED && !message.isRecalled &&
            companionRawHash(message.content) == rawHash
}

data class CompanionReplySource(
    val messageId: String,
    val conversationId: String,
    val turnId: String,
    val revision: Long,
    val protocolVersion: Int,
    val parserVersion: Int,
    val rawHash: String,
    val attemptId: String? = null,
    val configurationRevision: Long? = null,
    val characterId: String = "",
)

/** 坐标始终指向未经规范化的原文；一个旧式混写节点可派生多个共享此节点范围的拍。 */
data class CompanionRawSpan(val start: Int, val endExclusive: Int)

data class CompanionBeat(
    val id: String,
    val contentType: RoleplayContentType,
    val speaker: RoleplaySpeaker,
    val speakerName: String,
    val content: String,
    val rawSpan: CompanionRawSpan,
    val spriteKey: String? = null,
    val voiceEmotion: CompanionVoiceEmotion? = null,
    val emotion: String = "",
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val replyToSpeakerName: String = "",
)

data class CompanionObservedField(val id: String, val label: String, val value: String)

data class CompanionObservation(
    val fields: List<CompanionObservedField>,
    val style: CompanionObservationStyle,
)

enum class CompanionReplyDiagnostic {
    MISSING_SOURCE_METADATA, MISSING_PRESENTATION, UNSUPPORTED_PROTOCOL,
    UNSUPPORTED_PRESENTATION, MALFORMED_OBSERVATION, UNKNOWN_OBSERVATION_FIELD,
    OBSERVATION_LIMIT_EXCEEDED, INVALID_SPRITE, INVALID_VOICE_EMOTION,
}

data class ParsedCompanionReply(
    val source: CompanionReplySource,
    val raw: String,
    val beats: List<CompanionBeat>,
    val observation: CompanionObservation?,
    val diagnostics: Set<CompanionReplyDiagnostic> = emptySet(),
    val presentation: CompanionPresentationSnapshot? = null,
) {
    val displayText: String get() = beats.joinToString("\n\n") { it.content }
}
