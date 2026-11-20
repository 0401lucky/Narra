package com.example.myapplication.data.local.companion

import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.CompanionDescriptorCodec
import com.example.myapplication.model.CompanionReplyMetadata
import com.example.myapplication.model.CompanionSourceEligibility
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.companionRawHash
import com.example.myapplication.system.json.AppJson

/** 只在消息/回合的同一 Room 事务内写，读取也验证当前关联图而非信任消息标签。 */
internal class CompanionSourceStorage(private val database: ChatDatabase) {
    private val dao = database.companionDao()

    suspend fun eligibility(message: MessageEntity): CompanionSourceEligibility {
        return read(message, includeReplyMetadata = false).eligibility
    }

    data class ReadResult(
        val eligibility: CompanionSourceEligibility,
        val replyMetadata: CompanionReplyMetadata?
    )

    suspend fun read(message: MessageEntity, includeReplyMetadata: Boolean = true): ReadResult {
        val turn = message.companionTurnId.takeIf { it.isNotBlank() }?.let { dao.getTurn(it) }
        val encounter = turn?.let { dao.getEncounter(it.encounterId) }
        val selected = turn?.selectedAttemptId?.let { dao.getAttempt(it) }
        val eligibility = classify(message, turn, encounter, selected)
        val metadata =
            if (includeReplyMetadata && eligibility == CompanionSourceEligibility.TEXT_ALLOWED &&
                message.role == "ASSISTANT" && turn != null && encounter != null && selected != null &&
                turn.replyMessageId == message.id && selected.requestFingerprint == MemoryArchiveIdentity.fingerprint(
                    selected.requestDescriptorJson
                )
            ) runCatching {
                val descriptor = CompanionDescriptorCodec.decode(selected.requestDescriptorJson)
                CompanionReplyMetadata(
                    messageId = message.id,
                    conversationId = message.conversationId,
                    turnId = turn.id,
                    sourceRevision = message.memoryRevision,
                    rawHash = companionRawHash(message.content),
                    speakerId = message.speakerId,
                    outputFormat = RoleplayOutputFormat.valueOf(message.roleplayOutputFormat),
                    interactionMode = RoleplayInteractionMode.fromStorageValueOrNull(message.roleplayInteractionMode),
                    selectedAttemptId = selected.id,
                    protocolVersion = descriptor.protocolVersion,
                    presentation = descriptor.presentation,
                )
            }.getOrNull() else null
        return ReadResult(eligibility, metadata)
    }

    companion object {
        fun classify(
            message: MessageEntity,
            turn: CompanionTurnEntity?,
            encounter: CompanionEncounterEntity?,
            selectedAttempt: CompanionAttemptEntity?,
        ): CompanionSourceEligibility {
            if (message.status != "COMPLETED" || message.isRecalled) return CompanionSourceEligibility.UNAVAILABLE
            if (message.companionTurnId.isBlank()) {
                return if (message.companionMemoryState == "legacy") CompanionSourceEligibility.TEXT_ALLOWED
                else CompanionSourceEligibility.UNAVAILABLE
            }
            if (turn == null || encounter == null || turn.id != message.companionTurnId ||
                turn.encounterId != encounter.id
            ) return CompanionSourceEligibility.UNAVAILABLE
            if (encounter.conversationId != message.conversationId || turn.committedRevision <= 0 ||
                selectedAttempt == null || turn.selectedAttemptId != selectedAttempt.id ||
                selectedAttempt.turnId != turn.id || selectedAttempt.state != "COMMITTED"
            ) return CompanionSourceEligibility.UNAVAILABLE
            val runtime = runCatching {
                AppJson.gson.fromJson(
                    selectedAttempt.frozenRuntimeJson,
                    MemoryRuntimeContext::class.java
                )
            }.getOrNull() ?: return CompanionSourceEligibility.UNAVAILABLE
            if (runtime.conversationId != encounter.conversationId || runtime.scenarioId != encounter.scenarioId ||
                runtime.runId != encounter.runId || runtime.spaceId != encounter.spaceId ||
                runtime.characterId != encounter.characterId || runtime.personaKey != encounter.personaKey ||
                runtime.generation != encounter.generation
            ) return CompanionSourceEligibility.UNAVAILABLE
            val matchesInput = message.role == "USER" && turn.inputMessageId == message.id &&
                    turn.inputRevision == message.memoryRevision
            val matchesReply =
                message.role == "ASSISTANT" && message.speakerId == encounter.characterId &&
                        turn.replyMessageId == message.id &&
                        turn.replyRevision == message.memoryRevision
            return when {
                message.companionMemoryState == "ready" && (matchesInput && turn.kind == "INPUT" || matchesReply) ->
                    CompanionSourceEligibility.TEXT_ALLOWED

                message.companionMemoryState == "control" && turn.kind == "CONTINUE" && matchesInput ->
                    CompanionSourceEligibility.CONTROL_NOOP

                else -> CompanionSourceEligibility.UNAVAILABLE
            }
        }
    }

    suspend fun verifyExternalWrite(old: MessageEntity?, next: MessageEntity) {
        if (old?.companionTurnId.isNullOrBlank()) return
        require(
            next.companionTurnId == old!!.companionTurnId &&
                    next.companionMemoryState == old.companionMemoryState
        ) { "陪伴消息的关联和资格只能由回合事务修改" }
        if (old.companionMemoryState == "control") {
            require(next.content == old.content && next.partsJson == old.partsJson) { "继续控制行不能改成普通台词" }
        }
    }

    suspend fun onExternalChange(old: MessageEntity, next: MessageEntity) {
        if (old.companionTurnId.isBlank()) return
        val turn = dao.getTurn(old.companionTurnId) ?: return
        dao.upsertTurn(
            turn.copy(
                mutationRevision = turn.mutationRevision + 1,
                committedRevision = if (turn.committedRevision > 0) turn.committedRevision + 1 else 0,
                inputRevision = if (turn.inputMessageId == old.id) next.memoryRevision else turn.inputRevision,
                replyRevision = if (turn.replyMessageId == old.id) next.memoryRevision else turn.replyRevision,
                // 未提交输入仍需沿原回合显式重试；旧请求状态/claim 与 CAS 已失效。
                activeAttemptId = turn.activeAttemptId.takeIf { turn.committedRevision == 0L },
            )
        )
        dao.eraseTurnArtifacts(turn.id)
    }

    suspend fun onDelete(messageId: String) {
        val turn = dao.getMessageTurn(messageId) ?: return
        dao.upsertTurn(
            turn.copy(
                mutationRevision = turn.mutationRevision + 1,
                inputMessageId = turn.inputMessageId?.takeUnless { it == messageId },
                inputRevision = turn.inputRevision.takeUnless { turn.inputMessageId == messageId },
                replyMessageId = turn.replyMessageId?.takeUnless { it == messageId },
                replyRevision = turn.replyRevision.takeUnless { turn.replyMessageId == messageId },
                selectedAttemptId = turn.selectedAttemptId.takeUnless { turn.replyMessageId == messageId },
                activeAttemptId = null,
            )
        )
        dao.eraseTurnArtifacts(turn.id)
    }

    suspend fun onClear(conversationId: String, now: Long) {
        dao.listEncounters(conversationId).forEach { encounter ->
            dao.listTurns(encounter.id).forEach { turn ->
                dao.upsertTurn(
                    turn.copy(
                        mutationRevision = turn.mutationRevision + 1, selectedAttemptId = null,
                        activeAttemptId = null, inputMessageId = null, inputRevision = null,
                        replyMessageId = null, replyRevision = null,
                    )
                )
                dao.eraseTurnArtifacts(turn.id)
            }
            dao.upsertEncounter(
                encounter.copy(
                    state = "ENDED",
                    revision = encounter.revision + 1,
                    activeKey = null,
                    endedAt = now,
                )
            )
        }
    }
}
