package com.example.myapplication.viewmodel

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.system.performance.BenchmarkFixtureIds
import com.example.myapplication.system.performance.BenchmarkOfflineReplaySpec
import com.example.myapplication.system.performance.BenchmarkAiViolationTracker
import com.example.myapplication.system.performance.PerformanceTraceContract
import com.example.myapplication.system.performance.trace
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

enum class RoleplayOfflineReplayPhase {
    DISABLED,
    READY,
    RUNNING,
    COMPLETED,
    FAILED,
}

internal class RoleplayOfflineReplaySupport(
    private val scope: CoroutineScope,
    offlineReplaySpec: BenchmarkOfflineReplaySpec?,
    private val currentUiState: () -> RoleplayUiState,
    private val currentRawMessages: () -> List<ChatMessage>,
    private val updateUiState: (((RoleplayUiState) -> RoleplayUiState) -> Unit),
    private val activeSendingJob: () -> Job?,
    private val installSendingJob: (Job) -> Unit,
    private val beginSendingRun: () -> Long,
    private val isSendingRunActive: (Long) -> Boolean,
    private val finishSendingRun: (Long) -> Unit,
    private val isCurrentConversation: (String) -> Boolean,
    private val delayChunk: suspend (Long) -> Unit = { delay(it) },
    aiViolationTracker: BenchmarkAiViolationTracker? = null,
    private val tracePump: ((() -> Unit) -> Unit) = { block ->
        PerformanceTraceContract.ROLEPLAY_REPLAY_PUMP.trace(block)
    },
) {
    private val replaySpec = offlineReplaySpec?.also { spec ->
        require(spec === BenchmarkOfflineReplaySpec.DEFAULT) {
            "F3 离线重放只允许使用 BenchmarkOfflineReplaySpec.DEFAULT"
        }
    }
    private val hasNoAiViolations: () -> Boolean = {
        aiViolationTracker?.hasNoViolations() != false
    }
    private val removeAiViolationListener = aiViolationTracker?.addViolationListener {
        failClosedIfActive()
    }

    fun prepare() {
        val spec = replaySpec ?: return
        if (!hasNoAiViolations()) return
        val state = currentUiState()
        if (state.offlineReplayPhase != RoleplayOfflineReplayPhase.DISABLED) return
        if (state.isSending || activeSendingJob()?.isActive == true) return
        val conversationId = state.currentSession?.conversationId.orEmpty()
        if (!isEligibleFixture(state, conversationId, spec)) return

        updateUiState { current ->
            if (
                current.offlineReplayPhase == RoleplayOfflineReplayPhase.DISABLED &&
                hasNoAiViolations() &&
                !current.isSending &&
                current.currentSession?.conversationId == conversationId
            ) {
                RoleplayStateSupport.prepareOfflineReplay(
                    current = current,
                    seedContent = spec.seedContent,
                )
            } else {
                current
            }
        }
    }

    fun start() {
        val spec = replaySpec ?: return
        if (!hasNoAiViolations()) {
            failClosedIfActive()
            return
        }
        val state = currentUiState()
        val conversationId = state.currentSession?.conversationId.orEmpty()
        if (state.offlineReplayPhase != RoleplayOfflineReplayPhase.READY) return
        if (!spec.isInitialContent(state.streamingContent)) return
        if (activeSendingJob()?.isActive == true) return
        if (!isEligibleFixture(state, conversationId, spec)) return

        var transitionedToRunning = false
        updateUiState { current ->
            if (
                current.offlineReplayPhase == RoleplayOfflineReplayPhase.READY &&
                current.currentSession?.conversationId == conversationId &&
                spec.isInitialContent(current.streamingContent)
            ) {
                transitionedToRunning = true
                RoleplayStateSupport.beginOfflineReplay(current)
            } else {
                current
            }
        }
        if (!transitionedToRunning) return

        val runId = beginSendingRun()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            var accumulatedContent = spec.seedContent
            try {
                val replayChunks = spec.chunks.drop(1)
                replayChunks.forEachIndexed { index, chunk ->
                    // 固定 48ms 间隔不计入 pump section，只测累积与状态发布。
                    delayChunk(spec.chunkDelayMillis)
                    coroutineContext.ensureActive()
                    if (!isReplayRunCurrent(runId, conversationId)) return@launch
                    if (!hasNoAiViolations()) {
                        failClosedIfActive()
                        return@launch
                    }

                    var published = false
                    tracePump {
                        val nextContent = accumulatedContent + chunk
                        updateUiState { current ->
                            if (
                                hasNoAiViolations() &&
                                isReplayRunCurrent(runId, conversationId) &&
                                current.offlineReplayPhase == RoleplayOfflineReplayPhase.RUNNING &&
                                current.currentSession?.conversationId == conversationId
                            ) {
                                published = true
                                RoleplayStateSupport.publishOfflineReplayContent(
                                    current = current,
                                    content = nextContent,
                                    completed = index == replayChunks.lastIndex,
                                )
                            } else {
                                current
                            }
                        }
                        if (published) {
                            accumulatedContent = nextContent
                        }
                    }
                    if (!published) return@launch
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                if (isReplayRunCurrent(runId, conversationId)) {
                    updateUiState { current ->
                        if (current.currentSession?.conversationId == conversationId) {
                            RoleplayStateSupport.failOfflineReplay(current)
                        } else {
                            current
                        }
                    }
                }
            } finally {
                finishSendingRun(runId)
            }
        }
        installSendingJob(job)
        job.start()
    }

    fun dispose() {
        removeAiViolationListener?.invoke()
    }

    private fun failClosedIfActive() {
        updateUiState { current ->
            if (current.offlineReplayPhase in FAIL_CLOSED_PHASES) {
                RoleplayStateSupport.failOfflineReplay(current)
            } else {
                current
            }
        }
    }

    private fun isEligibleFixture(
        state: RoleplayUiState,
        conversationId: String,
        spec: BenchmarkOfflineReplaySpec,
    ): Boolean {
        if (!spec.matchesFixedFixture(state.currentScenario?.id, conversationId)) return false
        val rawMessages = currentRawMessages()
        if (!spec.hasExpectedPersistedMessageCount(rawMessages.size)) return false
        return rawMessages.all { message ->
            BenchmarkFixtureIds.isRoleplayMessageId(message.id) &&
                message.conversationId == conversationId &&
                message.status == MessageStatus.COMPLETED
        }
    }

    private fun isReplayRunCurrent(
        runId: Long,
        conversationId: String,
    ): Boolean {
        return isSendingRunActive(runId) && isCurrentConversation(conversationId)
    }

    private companion object {
        val FAIL_CLOSED_PHASES = setOf(
            RoleplayOfflineReplayPhase.READY,
            RoleplayOfflineReplayPhase.RUNNING,
            RoleplayOfflineReplayPhase.COMPLETED,
        )
    }
}
