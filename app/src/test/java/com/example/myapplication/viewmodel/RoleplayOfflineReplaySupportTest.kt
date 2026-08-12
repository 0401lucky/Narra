package com.example.myapplication.viewmodel

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.system.performance.BenchmarkFixtureIds
import com.example.myapplication.system.performance.BenchmarkOfflineReplaySpec
import com.example.myapplication.system.performance.BenchmarkAiViolationTracker
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.system.performance.BenchmarkPhase
import com.example.myapplication.system.performance.BenchmarkRuntimePolicy
import com.example.myapplication.system.performance.BenchmarkScenarioId
import com.example.myapplication.system.performance.BenchmarkSessionDescriptor
import com.example.myapplication.system.performance.F3BenchmarkAiDependencyPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayOfflineReplaySupportTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun prepare_fixedFixture_installsNonBlankSeedAndReadyPhase() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val harness = ReplayHarness(CoroutineScope(coroutineContext + Job()))

            harness.support.prepare()
            harness.support.prepare()

            assertEquals(RoleplayOfflineReplayPhase.READY, harness.uiState.value.offlineReplayPhase)
            assertEquals(BenchmarkOfflineReplaySpec.DEFAULT.seedContent, harness.uiState.value.streamingContent)
            assertTrue(harness.uiState.value.isSending)
        }

    @Test
    fun prepare_nonFixedMessageCount_staysDisabled() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val harness = ReplayHarness(
            scope = CoroutineScope(coroutineContext + Job()),
            rawMessageCount = 99,
        )

        harness.support.prepare()

        assertEquals(RoleplayOfflineReplayPhase.DISABLED, harness.uiState.value.offlineReplayPhase)
        assertEquals("", harness.uiState.value.streamingContent)
        assertFalse(harness.uiState.value.isSending)
    }

    @Test
    fun start_calledRepeatedly_keepsSingleActiveReplayJob() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val firstChunkGate = CompletableDeferred<Unit>()
            val harness = ReplayHarness(
                scope = CoroutineScope(coroutineContext + Job()),
                delayChunk = { firstChunkGate.await() },
            )

            harness.support.prepare()
            harness.support.start()
            runCurrent()
            val firstJob = harness.sendingJob

            harness.support.start()

            assertTrue(firstJob?.isActive == true)
            assertTrue(firstJob === harness.sendingJob)
            assertEquals(1, harness.beginRunCount)
            assertEquals(1, harness.installJobCount)
            assertEquals(RoleplayOfflineReplayPhase.RUNNING, harness.uiState.value.offlineReplayPhase)

            firstChunkGate.complete(Unit)
            advanceUntilIdle()

            assertEquals(RoleplayOfflineReplayPhase.COMPLETED, harness.uiState.value.offlineReplayPhase)
            assertEquals(1, harness.finishRunCount)
            assertNull(harness.sendingJob)
        }

    @Test
    fun start_activeSendingJob_doesNotEnterReplay() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val harness = ReplayHarness(CoroutineScope(coroutineContext + Job()))
            harness.support.prepare()
            val activeJob = Job()
            harness.sendingJob = activeJob

            harness.support.start()

            assertEquals(RoleplayOfflineReplayPhase.READY, harness.uiState.value.offlineReplayPhase)
            assertEquals(BenchmarkOfflineReplaySpec.DEFAULT.seedContent, harness.uiState.value.streamingContent)
            assertEquals(0, harness.beginRunCount)
            assertEquals(0, harness.installJobCount)
            assertTrue(harness.sendingJob === activeJob)

            activeJob.cancel()
            harness.sendingJob = null
        }

    @Test
    fun start_replaysDefaultChunksAndTracesOnlyAccumulationAndPublication() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            var insidePumpTrace = false
            val delays = mutableListOf<Long>()
            var traceCount = 0
            val harness = ReplayHarness(
                scope = CoroutineScope(coroutineContext + Job()),
                delayChunk = { delayMillis ->
                    assertFalse(insidePumpTrace)
                    delays += delayMillis
                },
                tracePump = { block ->
                    assertFalse(insidePumpTrace)
                    insidePumpTrace = true
                    traceCount += 1
                    try {
                        block()
                    } finally {
                        insidePumpTrace = false
                    }
                },
                onStateChanged = { before, after ->
                    if (
                        before.streamingContent != after.streamingContent &&
                        after.offlineReplayPhase != RoleplayOfflineReplayPhase.READY
                    ) {
                        assertTrue(insidePumpTrace)
                    }
                },
            )

            harness.support.prepare()
            harness.support.start()
            advanceUntilIdle()

            val expectedPumpCount = BenchmarkOfflineReplaySpec.DEFAULT.chunks.size - 1
            assertEquals(List(expectedPumpCount) { 48L }, delays)
            assertEquals(expectedPumpCount, traceCount)
            assertEquals(RoleplayOfflineReplayPhase.COMPLETED, harness.uiState.value.offlineReplayPhase)
            assertEquals(BenchmarkOfflineReplaySpec.DEFAULT.completedContent, harness.uiState.value.streamingContent)
            assertTrue(harness.uiState.value.isSending)
            assertNull(harness.sendingJob)
        }

    @Test
    fun start_conversationSwitchWhileDelayed_oldJobDoesNotWriteNewConversation() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val firstChunkGate = CompletableDeferred<Unit>()
            var traceCount = 0
            val harness = ReplayHarness(
                scope = CoroutineScope(coroutineContext + Job()),
                delayChunk = { firstChunkGate.await() },
                tracePump = { block ->
                    traceCount += 1
                    block()
                },
            )

            harness.support.prepare()
            harness.support.start()
            runCurrent()
            harness.switchConversation(
                conversationId = "new-conversation",
                streamingContent = "新会话内容",
            )

            firstChunkGate.complete(Unit)
            advanceUntilIdle()

            assertEquals("new-conversation", harness.uiState.value.currentSession?.conversationId)
            assertEquals("新会话内容", harness.uiState.value.streamingContent)
            assertEquals(RoleplayOfflineReplayPhase.DISABLED, harness.uiState.value.offlineReplayPhase)
            assertFalse(harness.uiState.value.isSending)
            assertNull(harness.uiState.value.errorMessage)
            assertEquals(0, traceCount)
            assertEquals(1, harness.finishRunCount)
            assertNull(harness.sendingJob)
        }

    @Test
    fun start_cancelledWhileDelayed_doesNotPublishFailure() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val delayStarted = CompletableDeferred<Unit>()
            val harness = ReplayHarness(
                scope = CoroutineScope(coroutineContext + Job()),
                delayChunk = {
                    delayStarted.complete(Unit)
                    awaitCancellation()
                },
            )

            harness.support.prepare()
            harness.support.start()
            runCurrent()
            assertTrue(delayStarted.isCompleted)
            val replayJob = requireNotNull(harness.sendingJob)

            harness.cancelActiveReplay()
            advanceUntilIdle()

            assertTrue(replayJob.isCancelled)
            assertEquals(RoleplayOfflineReplayPhase.DISABLED, harness.uiState.value.offlineReplayPhase)
            assertFalse(harness.uiState.value.isSending)
            assertEquals("", harness.uiState.value.streamingContent)
            assertNull(harness.uiState.value.errorMessage)
            assertEquals(1, harness.finishRunCount)
            assertNull(harness.sendingJob)
        }

    @Test
    fun start_delayFailure_movesToFailedWithoutProviderFallback() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val harness = ReplayHarness(
                scope = CoroutineScope(coroutineContext + Job()),
                delayChunk = { error("测试注入失败") },
            )

            harness.support.prepare()
            harness.support.start()
            advanceUntilIdle()

            assertEquals(RoleplayOfflineReplayPhase.FAILED, harness.uiState.value.offlineReplayPhase)
            assertFalse(harness.uiState.value.isSending)
            assertEquals("", harness.uiState.value.streamingContent)
            assertEquals("离线流式重放失败", harness.uiState.value.errorMessage)
            assertNull(harness.sendingJob)
        }

    @Test
    fun providerViolation_readyRunningOrCompleted_movesReplayToFailedImmediately() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            listOf(
                RoleplayOfflineReplayPhase.READY,
                RoleplayOfflineReplayPhase.RUNNING,
                RoleplayOfflineReplayPhase.COMPLETED,
            ).forEach { phase ->
                val policy = BenchmarkRuntimePolicy.resolve(
                    appChannel = BenchmarkContract.BASELINE_CHANNEL,
                    descriptor = BenchmarkSessionDescriptor(
                        schemaVersion = BenchmarkContract.SCHEMA_VERSION,
                        sessionId = "session-1234567890",
                        scenarioId = BenchmarkScenarioId.F3_ROLEPLAY_STREAM_REPLAY,
                        fixtureVersion = BenchmarkContract.FIXTURE_VERSION,
                        phase = BenchmarkPhase.MEASURE,
                        createdAtEpochMillis = 1_000L,
                        ttlMillis = BenchmarkContract.DEFAULT_TTL_MILLIS,
                    ),
                    requestedSessionId = "session-1234567890",
                    nowEpochMillis = 1_000L,
                )
                val dependencies = F3BenchmarkAiDependencyPolicy.resolve(
                    runtimePolicy = policy,
                    aiGatewayProvider = { error("不应求值") },
                    aiPromptExtrasServiceProvider = { error("不应求值") },
                )
                val boundHarness = ReplayHarness(
                    scope = CoroutineScope(coroutineContext + Job()),
                    aiViolationTracker = dependencies.violationTracker,
                )
                boundHarness.support.prepare()
                boundHarness.uiState.value = boundHarness.uiState.value.copy(offlineReplayPhase = phase)
                runCatching { dependencies.aiGateway.sendMessageStream(emptyList()) }

                assertEquals(RoleplayOfflineReplayPhase.FAILED, boundHarness.uiState.value.offlineReplayPhase)
                assertFalse(boundHarness.uiState.value.isSending)
                assertEquals("离线流式重放失败", boundHarness.uiState.value.errorMessage)
                boundHarness.support.dispose()
            }
        }

    private class ReplayHarness(
        scope: CoroutineScope,
        rawMessageCount: Int = 100,
        delayChunk: suspend (Long) -> Unit = {},
        tracePump: ((() -> Unit) -> Unit) = { block -> block() },
        onStateChanged: (RoleplayUiState, RoleplayUiState) -> Unit = { _, _ -> },
        aiViolationTracker: BenchmarkAiViolationTracker? = null,
    ) {
        val uiState = MutableStateFlow(
            RoleplayUiState(
                currentScenario = RoleplayScenario(
                    id = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
                    assistantId = BenchmarkFixtureIds.ASSISTANT_ID,
                ),
                currentSession = RoleplaySession(
                    id = BenchmarkFixtureIds.ROLEPLAY_SESSION_ID,
                    scenarioId = BenchmarkFixtureIds.ROLEPLAY_SCENARIO_ID,
                    conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                ),
            ),
        )
        private val rawMessages = List(rawMessageCount) { index ->
            ChatMessage(
                id = "benchmark-roleplay-message-${index.toString().padStart(3, '0')}",
                conversationId = BenchmarkFixtureIds.ROLEPLAY_CONVERSATION_ID,
                role = if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
                content = "固定消息-$index",
                status = MessageStatus.COMPLETED,
            )
        }
        var sendingJob: Job? = null
        private var activeRunId = 0L
        var beginRunCount = 0
            private set
        var installJobCount = 0
            private set
        var finishRunCount = 0
            private set

        val support = RoleplayOfflineReplaySupport(
            scope = scope,
            offlineReplaySpec = BenchmarkOfflineReplaySpec.DEFAULT,
            currentUiState = { uiState.value },
            currentRawMessages = { rawMessages },
            updateUiState = { reducer ->
                val before = uiState.value
                val after = reducer(before)
                onStateChanged(before, after)
                uiState.value = after
            },
            activeSendingJob = { sendingJob },
            installSendingJob = { job ->
                installJobCount += 1
                sendingJob = job
            },
            beginSendingRun = {
                beginRunCount += 1
                activeRunId += 1
                activeRunId
            },
            isSendingRunActive = { runId -> runId != 0L && runId == activeRunId },
            finishSendingRun = { runId ->
                finishRunCount += 1
                if (runId == activeRunId) {
                    sendingJob = null
                }
            },
            isCurrentConversation = { conversationId ->
                uiState.value.currentSession?.conversationId == conversationId
            },
            delayChunk = delayChunk,
            tracePump = tracePump,
            aiViolationTracker = aiViolationTracker,
        )

        fun switchConversation(
            conversationId: String,
            streamingContent: String,
        ) {
            val currentSession = requireNotNull(uiState.value.currentSession)
            uiState.value = uiState.value.copy(
                currentSession = currentSession.copy(
                    id = "session-$conversationId",
                    conversationId = conversationId,
                ),
                isSending = false,
                streamingContent = streamingContent,
                offlineReplayPhase = RoleplayOfflineReplayPhase.DISABLED,
                errorMessage = null,
            )
        }

        fun cancelActiveReplay() {
            val activeJob = sendingJob
            activeRunId += 1
            sendingJob = null
            uiState.value = uiState.value.copy(
                isSending = false,
                streamingContent = "",
                offlineReplayPhase = RoleplayOfflineReplayPhase.DISABLED,
                errorMessage = null,
            )
            activeJob?.cancel()
        }
    }
}
