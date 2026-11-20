package com.example.myapplication.conversation

import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.model.*
import com.example.myapplication.system.logging.logFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed interface CompanionGenerationResult {
    data class Saved(val receipt: CompanionCommitResult.Committed) : CompanionGenerationResult
    data class Generated(val attemptId: String, val completionStatus: PromptCompletionStatus) :
        CompanionGenerationResult

    data class NeedsSave(val attemptId: String, val artifactPersisted: Boolean) :
        CompanionGenerationResult

    data class Rejected(val reason: CompanionConflict) : CompanionGenerationResult
    data class Failed(val attemptId: String) : CompanionGenerationResult
}

/** 只协调本次显式请求。保存已收产物与 canonical 发布是不同步骤，不自动重新收费。 */
class CompanionTurnCoordinator(
    private val repository: CompanionRepository,
    private val onCommitted: (CompanionCommitResult.Committed) -> Unit = {},
    private val leaseMillis: Long = 120_000,
    private val waitForLease: suspend (Long) -> Unit = { delay(it) },
) {
    private data class Received(val owner: String, val artifact: CompanionGeneratedArtifact)

    private val received = ConcurrentHashMap<String, Received>()
    private val inFlight = mutableSetOf<String>()
    private val artifactLock = Any()

    suspend fun generate(
        attemptId: String,
        stream: suspend (CompanionAttempt) -> Flow<ChatStreamEvent>,
    ): CompanionGenerationResult {
        // 请求前预留恢复槽；尚未返回的请求也可能同时遭遇首次落盘失败。
        val reserved = synchronized(artifactLock) {
            if (received.size + inFlight.size >= MAX_MEMORY_ARTIFACTS ||
                received.containsKey(attemptId) || attemptId in inFlight
            ) false else inFlight.add(attemptId)
        }
        if (!reserved) return CompanionGenerationResult.Rejected(CompanionConflict.INVALID_INPUT)
        return try {
            generateReserved(attemptId, stream)
        } finally {
            synchronized(artifactLock) { inFlight.remove(attemptId) }
        }
    }

    private suspend fun generateReserved(
        attemptId: String,
        stream: suspend (CompanionAttempt) -> Flow<ChatStreamEvent>,
    ): CompanionGenerationResult {
        val owner = UUID.randomUUID().toString()
        val attempt = repository.claimAttempt(attemptId, owner, leaseMillis)
            ?: return CompanionGenerationResult.Rejected(CompanionConflict.ATTEMPT_CHANGED)
        val artifact = try {
            coroutineScope {
                val heartbeat = launch {
                    while (true) {
                        waitForLease((leaseMillis / 3).coerceAtLeast(1))
                        if (!repository.renewLease(attemptId, owner, leaseMillis)) {
                            this@coroutineScope.cancel("陪伴请求已失去保存权限")
                            break
                        }
                    }
                }
                try {
                    collect(stream(attempt))
                } finally {
                    heartbeat.cancelAndJoin()
                }
            }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                runCatching { repository.cancelAttempt(attemptId, owner) }
                    .logFailure("CompanionTurn") { "could not persist cancelled request state" }
            }
            throw cancelled
        } catch (_: Exception) {
            // 网络结果不确定；保留 REQUESTED 证据，不把失败重写为可自动首次请求。
            withContext(NonCancellable) {
                runCatching { repository.cancelAttempt(attemptId, owner, uncertain = true) }
                    .logFailure("CompanionTurn") { "could not persist uncertain request state" }
            }
            return CompanionGenerationResult.Failed(attemptId)
        }
        synchronized(artifactLock) {
            inFlight.remove(attemptId)
            received[attemptId] = Received(owner, artifact)
        }
        return saveReceived(attemptId)
    }

    suspend fun retrySave(attemptId: String): CompanionGenerationResult {
        if (received.containsKey(attemptId)) return saveReceived(attemptId)
        val attempt = repository.getAttempt(attemptId)
            ?: return CompanionGenerationResult.Rejected(CompanionConflict.ATTEMPT_CHANGED)
        if (attempt.state !in setOf(
                CompanionAttemptState.RESULT_READY,
                CompanionAttemptState.COMMITTED
            )
        )
            return CompanionGenerationResult.Rejected(CompanionConflict.ATTEMPT_CHANGED)
        return finish(
            attemptId,
            attempt.artifact?.completionStatus ?: PromptCompletionStatus.UNKNOWN
        )
    }

    suspend fun acceptOpening(attemptId: String): CompanionGenerationResult =
        commit(attemptId, acceptOpening = true)

    suspend fun pendingArtifact(attemptId: String): CompanionGeneratedArtifact? {
        val result = received[attemptId] ?: return null
        if (!repository.canSaveGeneratedArtifact(attemptId, result.owner)) {
            discardMemoryArtifact(attemptId)
            return null
        }
        return result.artifact
    }

    suspend fun prunePendingArtifacts() {
        received.keys.toList().forEach { pendingArtifact(it) }
    }

    fun discardMemoryArtifact(attemptId: String) {
        synchronized(artifactLock) { received.remove(attemptId) }
    }

    private suspend fun saveReceived(attemptId: String): CompanionGenerationResult {
        val result = received[attemptId] ?: return CompanionGenerationResult.Rejected(
            CompanionConflict.INVALID_ARTIFACT
        )
        val saved = try {
            repository.saveGeneratedArtifact(attemptId, result.owner, result.artifact)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CompanionGenerationResult.NeedsSave(attemptId, artifactPersisted = false)
        }
        if (!saved) {
            discardMemoryArtifact(attemptId)
            return CompanionGenerationResult.Rejected(CompanionConflict.ATTEMPT_CHANGED)
        }
        discardMemoryArtifact(attemptId)
        return try {
            finish(attemptId, result.artifact.completionStatus)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            CompanionGenerationResult.NeedsSave(attemptId, artifactPersisted = true)
        }
    }

    private suspend fun finish(
        attemptId: String,
        status: PromptCompletionStatus
    ): CompanionGenerationResult {
        val attempt = repository.getAttempt(attemptId)
            ?: return CompanionGenerationResult.Rejected(CompanionConflict.ATTEMPT_CHANGED)
        if (attempt.state == CompanionAttemptState.COMMITTED) return commit(attemptId)
        val turn = repository.getTurn(attempt.turnId)
            ?: return CompanionGenerationResult.Rejected(CompanionConflict.TURN_CHANGED)
        if (turn.kind == CompanionTurnKind.OPENING || status != PromptCompletionStatus.COMPLETED)
            return CompanionGenerationResult.Generated(attemptId, status)
        return commit(attemptId)
    }

    private suspend fun commit(
        attemptId: String,
        acceptOpening: Boolean = false
    ): CompanionGenerationResult {
        val result = try {
            repository.commitAttempt(attemptId, acceptOpening)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return CompanionGenerationResult.NeedsSave(attemptId, artifactPersisted = true)
        }
        return when (result) {
            is CompanionCommitResult.Conflict -> CompanionGenerationResult.Rejected(result.reason)
            is CompanionCommitResult.Committed -> {
                if (!result.alreadyCommitted) runCatching { onCommitted(result) }
                    .logFailure("CompanionTurn") { "committed turn post-processing could not be scheduled" }
                CompanionGenerationResult.Saved(result)
            }
        }
    }

    private suspend fun collect(events: Flow<ChatStreamEvent>): CompanionGeneratedArtifact {
        val text = StringBuilder()
        val reasoning = StringBuilder()
        var evidence = ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.UNKNOWN)
        var terminal = false
        var afterTerminal = false
        events.collect { event ->
            if (terminal && event !is ChatStreamEvent.Completed) afterTerminal = true
            when (event) {
                is ChatStreamEvent.ContentDelta -> {
                    require(text.length + event.value.length <= MAX_REPLY_CHARS) { "陪伴结果超出上限" }
                    text.append(event.value)
                }

                is ChatStreamEvent.ReasoningDelta -> {
                    require(reasoning.length + event.value.length <= MAX_REPLY_CHARS)
                    reasoning.append(event.value)
                }

                is ChatStreamEvent.ReasoningStepDelta -> {
                    require(reasoning.length + event.value.length <= MAX_REPLY_CHARS)
                    reasoning.append(event.value)
                }

                is ChatStreamEvent.CompletionEvidence -> evidence = event
                ChatStreamEvent.Completed -> terminal = true
                else -> Unit
            }
        }
        val rawReply = text.toString()
        val readable = evidence.status != PromptCompletionStatus.COMPLETED ||
                com.example.myapplication.roleplay.companion.CompanionReplyParser()
                    .hasReadableBody(rawReply)
        return CompanionGeneratedArtifact(
            rawReply = rawReply,
            completionStatus = if (!terminal || afterTerminal) PromptCompletionStatus.INCOMPLETE
            else if (text.isBlank() || !readable) PromptCompletionStatus.INCOMPLETE else evidence.status,
            reasoningContent = reasoning.toString(),
            rawFinishReason = evidence.rawFinishReason,
            rawStatus = evidence.rawStatus,
        )
    }

    companion object {
        private const val MAX_MEMORY_ARTIFACTS = 8
        private const val MAX_REPLY_CHARS = 2_000_000
    }
}
