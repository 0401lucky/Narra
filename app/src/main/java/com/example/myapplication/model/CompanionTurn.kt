package com.example.myapplication.model

enum class CompanionOpeningMode { APPROACH, INVITE, DIRECT }
enum class CompanionEncounterState { DRAFT, ACTIVE, ENDED }
enum class CompanionTurnKind { INPUT, CONTINUE, OPENING }
enum class CompanionAttemptState { NOT_STARTED, REQUESTED, RESULT_READY, COMMITTED, FAILED, UNCERTAIN, CANCELLED }
enum class CompanionMemoryState(val storageValue: String) {
    LEGACY("legacy"), WAITING_TURN("waiting_turn"), READY("ready"), CONTROL("control"), UNKNOWN("unknown");

    companion object {
        fun fromStorageValue(value: String?): CompanionMemoryState =
            entries.firstOrNull { it.storageValue == value } ?: UNKNOWN
    }
}

enum class CompanionSourceEligibility { TEXT_ALLOWED, CONTROL_NOOP, UNAVAILABLE }

data class CompanionSourceProof(
    val eligibility: CompanionSourceEligibility,
    val messageFingerprint: String,
)

private fun ChatMessage.companionFingerprint(): String {
    val bytes = com.example.myapplication.system.json.AppJson.gson
        .toJson(copy(companionSourceProof = null)).toByteArray(Charsets.UTF_8)
    return java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

internal fun ChatMessage.withCompanionSourceProof(eligibility: CompanionSourceEligibility): ChatMessage =
    if (companionTurnId.isNullOrBlank() && companionMemoryState == CompanionMemoryState.LEGACY)
        copy(companionSourceProof = null)
    else copy(companionSourceProof = CompanionSourceProof(eligibility, companionFingerprint()))

const val COMPANION_CONTINUE_DISPLAY_TEXT = "（继续）"

data class CompanionEncounter(
    val id: String,
    val context: MemoryRuntimeContext,
    val openingMode: CompanionOpeningMode,
    val state: CompanionEncounterState,
    val revision: Long,
    val createdAt: Long,
    val endedAt: Long? = null,
)

data class CompanionTurn(
    val id: String,
    val encounterId: String,
    val ordinal: Long,
    val kind: CompanionTurnKind,
    val inputMessageId: String?,
    val replyMessageId: String?,
    val inputRevision: Long?,
    val replyRevision: Long?,
    val mutationRevision: Long,
    val committedRevision: Long,
    val selectedAttemptId: String?,
    val activeAttemptId: String?,
    val createdAt: Long,
)

data class CompanionMessageRevision(
    val messageId: String,
    val revision: Long,
    val conversationId: String,
)

/** 只存请求的非敏感引用与版本，不接受整份 ProviderSettings 或凭据。 */
data class CompanionRequestDescriptor(
    val providerId: String = "",
    val modelName: String = "",
    val protocolVersion: Int = 1,
    val settingsRevision: Long = 0,
    val promptVersion: String = "companion-v1",
    val sourceMessages: List<CompanionMessageRevision> = emptyList(),
    val historyScope: AllowedCompanionHistoryScope? = null,
    val entryOrigin: CompanionEntryOrigin? = null,
    val replyMessageId: String? = null,
    val timePolicy: CompanionTimePolicy? = null,
    /** 仅无请求的入口准备记录使用；DIRECT 的选择不伪造一个正式回合。 */
    val entrySelectionId: String? = null,
    val presentation: CompanionPresentationSnapshot? = null,
)

data class CompanionGeneratedArtifact(
    val rawReply: String,
    val completionStatus: PromptCompletionStatus,
    val parts: List<ChatMessagePart> = emptyList(),
    val reasoningContent: String = "",
    val rawFinishReason: String? = null,
    val rawStatus: String? = null,
)

data class CompanionAttempt(
    val id: String,
    val turnId: String,
    val context: MemoryRuntimeContext,
    val descriptor: CompanionRequestDescriptor,
    val state: CompanionAttemptState,
    val expectedTurnRevision: Long,
    val expectedInputRevision: Long?,
    val expectedReplyRevision: Long?,
    val claimToken: String?,
    val leaseUntil: Long?,
    val requestedAt: Long?,
    val receivedAt: Long?,
    val artifact: CompanionGeneratedArtifact?,
    val artifactHash: String?,
)

data class CompanionPreparedTurn(val turn: CompanionTurn, val attempt: CompanionAttempt)

data class CompanionPendingResult(
    val attemptId: String,
    val encounterId: String,
    val turnKind: CompanionTurnKind,
    val createdAt: Long,
    val rawReply: String,
)

enum class CompanionConflict {
    CONTEXT_CHANGED, ENCOUNTER_CHANGED, TURN_CHANGED, SOURCE_CHANGED,
    ATTEMPT_CHANGED, INCOMPLETE_RESULT, INVALID_ARTIFACT, INVALID_INPUT,
}

sealed interface CompanionCommitResult {
    data class Committed(
        val turn: CompanionTurn,
        val inputMessage: ChatMessage?,
        val replyMessage: ChatMessage,
        val alreadyCommitted: Boolean = false,
    ) : CompanionCommitResult

    data class Conflict(val reason: CompanionConflict) : CompanionCommitResult
}

/** linked 消息的资格由 Room 关联图核验后填充，不信任传入/导入的 ready 字符串。 */
fun ChatMessage.companionSourceEligibility(): CompanionSourceEligibility {
    if (status != MessageStatus.COMPLETED || isRecalled) return CompanionSourceEligibility.UNAVAILABLE
    if (companionSourceProof != null && companionSourceProof.messageFingerprint != companionFingerprint())
        return CompanionSourceEligibility.UNAVAILABLE
    if (companionTurnId.isNullOrBlank()) {
        return if (companionMemoryState == CompanionMemoryState.LEGACY)
            CompanionSourceEligibility.TEXT_ALLOWED else CompanionSourceEligibility.UNAVAILABLE
    }
    val proof = companionSourceProof?.takeIf { it.messageFingerprint == companionFingerprint() }
        ?: return CompanionSourceEligibility.UNAVAILABLE
    return when (companionMemoryState) {
        CompanionMemoryState.READY -> proof.eligibility
            .takeIf { it == CompanionSourceEligibility.TEXT_ALLOWED }
            ?: CompanionSourceEligibility.UNAVAILABLE

        CompanionMemoryState.CONTROL -> proof.eligibility
            .takeIf { it == CompanionSourceEligibility.CONTROL_NOOP }
            ?: CompanionSourceEligibility.UNAVAILABLE

        else -> CompanionSourceEligibility.UNAVAILABLE
    }
}
