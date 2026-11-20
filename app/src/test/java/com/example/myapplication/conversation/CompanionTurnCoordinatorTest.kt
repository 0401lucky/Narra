package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.*
import com.example.myapplication.testutil.CompanionTestFixture
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionTurnCoordinatorTest : CompanionTestFixture() {
    private fun reply(
        text: String = "本轮合成回复",
        status: PromptCompletionStatus = PromptCompletionStatus.COMPLETED
    ) = flowOf(
        ChatStreamEvent.ContentDelta(text),
        ChatStreamEvent.CompletionEvidence(status, "fixture"),
        ChatStreamEvent.Completed,
    )

    @Test
    fun generatedCommit_isIdempotentAndNotifiesOnce() = runBlocking {
        val turn = prepared()
        var calls = 0
        var notifications = 0
        val coordinator = CompanionTurnCoordinator(repository, onCommitted = { notifications++ })
        assertTrue(coordinator.generate(turn.attempt.id) { calls++; reply() } is CompanionGenerationResult.Saved)
        assertTrue(coordinator.retrySave(turn.attempt.id) is CompanionGenerationResult.Saved)
        assertEquals(1, calls)
        assertEquals(1, notifications)
        assertEquals(2, repository.messages(turn.turn.encounterId).size)
    }

    @Test
    fun claimWriteFailure_neverCallsModelOrConsumesRecoveryCapacity() = runBlocking<Unit> {
        val turn = prepared()
        val coordinator = CompanionTurnCoordinator(repository)
        var calls = 0
        sql("CREATE TRIGGER block_claim BEFORE UPDATE ON companion_attempts WHEN NEW.state = 'REQUESTED' BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        repeat(9) {
            assertTrue(runCatching { coordinator.generate(turn.attempt.id) { calls++; reply() } }.isFailure)
        }
        assertEquals(0, calls)
        assertEquals(
            CompanionAttemptState.NOT_STARTED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
        sql("DROP TRIGGER block_claim")
        assertTrue(coordinator.generate(turn.attempt.id) { calls++; reply() } is CompanionGenerationResult.Saved)
        assertEquals(1, calls)
    }

    @Test
    fun canonicalFailure_reopensAndSavesWithoutCallingModel() = runBlocking {
        useFileDatabase()
        val turn = prepared()
        var calls = 0
        sql("CREATE TRIGGER block_canonical BEFORE UPDATE ON companion_turns WHEN NEW.committedRevision > OLD.committedRevision BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        val coordinator = CompanionTurnCoordinator(repository)
        val result = coordinator.generate(turn.attempt.id) { calls++; reply("已收到的完整答") }
        assertEquals(CompanionGenerationResult.NeedsSave(turn.attempt.id, true), result)
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
        assertEquals(
            CompanionAttemptState.RESULT_READY,
            repository.getAttempt(turn.attempt.id)!!.state
        )
        sql("DROP TRIGGER block_canonical")
        reopen()
        val restored = CompanionTurnCoordinator(repository).retrySave(turn.attempt.id)
        assertTrue(restored is CompanionGenerationResult.Saved)
        assertEquals(1, calls)
        assertEquals("已收到的完整答", repository.messages(turn.turn.encounterId).last().content)
    }

    @Test
    fun artifactFailure_keepsMemoryCopyAndRetriesStorageOnly() = runBlocking {
        val turn = prepared()
        var calls = 0
        val coordinator = CompanionTurnCoordinator(repository)
        sql("CREATE TRIGGER block_artifact BEFORE UPDATE ON companion_attempts WHEN NEW.artifactJson IS NOT NULL AND OLD.artifactJson IS NULL BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        val result = coordinator.generate(turn.attempt.id) { calls++; reply() }
        assertEquals(CompanionGenerationResult.NeedsSave(turn.attempt.id, false), result)
        assertEquals(
            CompanionAttemptState.REQUESTED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
        assertNull(repository.getAttempt(turn.attempt.id)!!.artifact)
        sql("DROP TRIGGER block_artifact")
        assertTrue(coordinator.retrySave(turn.attempt.id) is CompanionGenerationResult.Saved)
        assertEquals(1, calls)
    }

    @Test
    fun concurrentRequests_reserveBoundedRecoverySlotsBeforeCallingModel() = runBlocking<Unit> {
        val turns = List(9) { prepared() }
        val coordinator = CompanionTurnCoordinator(repository)
        repeat(9) { index ->
            assertTrue(coordinator.generate("missing-$index") { error("缺失请求不能调用模型") }
                    is CompanionGenerationResult.Rejected)
        }
        sql("CREATE TRIGGER block_artifact BEFORE UPDATE ON companion_attempts WHEN NEW.artifactJson IS NOT NULL AND OLD.artifactJson IS NULL BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        val allStarted = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var calls = 0
        val requests = turns.take(8).map { turn ->
            async {
                coordinator.generate(turn.attempt.id) {
                    flow {
                        calls++
                        if (calls == 8) allStarted.complete(Unit)
                        release.await()
                        emitAll(reply())
                    }
                }
            }
        }
        try {
            withTimeout(10_000) { allStarted.await() }
            assertTrue(coordinator.generate(turns.last().attempt.id) { calls++; reply() }
                    is CompanionGenerationResult.Rejected)
            assertEquals(8, calls)
            assertEquals(
                CompanionAttemptState.NOT_STARTED,
                repository.getAttempt(turns.last().attempt.id)!!.state
            )
            release.complete(Unit)
            assertTrue(
                requests.awaitAll()
                    .all { it is CompanionGenerationResult.NeedsSave && !it.artifactPersisted })
            assertTrue(coordinator.generate(turns.last().attempt.id) { calls++; reply() }
                    is CompanionGenerationResult.Rejected)
            sql("DROP TRIGGER block_artifact")
            assertTrue(coordinator.retrySave(turns.first().attempt.id) is CompanionGenerationResult.Saved)
            assertTrue(coordinator.generate(turns.last().attempt.id) { calls++; reply() }
                    is CompanionGenerationResult.Saved)
            assertEquals(9, calls)
        } finally {
            release.complete(Unit)
            requests.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun lostLease_cancelsCollectorAndDoesNotPublish() = runBlocking<Unit> {
        val turn = prepared()
        val tick = CompletableDeferred<Unit>()
        val started = CompletableDeferred<Unit>()
        val stopped = CompletableDeferred<Unit>()
        val losingRepository = object : CompanionRepository by repository {
            override suspend fun renewLease(id: String, owner: String, leaseMillis: Long) = false
        }
        val coordinator =
            CompanionTurnCoordinator(losingRepository, waitForLease = { tick.await() })
        val request = async {
            coordinator.generate(turn.attempt.id) {
                flow {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        stopped.complete(Unit)
                    }
                }
            }
        }
        try {
            withTimeout(5_000) { started.await() }
            tick.complete(Unit)
            assertEquals(true, withTimeoutOrNull(5_000) { stopped.await(); true })
            request.join()
            assertTrue(request.isCancelled)
            assertEquals(
                CompanionAttemptState.CANCELLED,
                repository.getAttempt(turn.attempt.id)!!.state
            )
            assertNull(repository.getAttempt(turn.attempt.id)!!.artifact)
            assertTrue(archive.listPendingIngress(turn.attempt.context).isEmpty())
        } finally {
            request.cancelAndJoin()
        }
    }

    @Test
    fun committedRetry_afterRerollEditOrDelete_rejectsStaleReceipt() = runBlocking<Unit> {
        val turn = prepared()
        var notifications = 0
        val coordinator = CompanionTurnCoordinator(repository, onCommitted = { notifications++ })
        val first =
            coordinator.generate(turn.attempt.id) { reply("原答") } as CompanionGenerationResult.Saved
        val reroll = repository.newAttempt(turn.turn.id)!!
        val stale = CompanionGenerationResult.Rejected(CompanionConflict.TURN_CHANGED)
        assertEquals(stale, coordinator.retrySave(turn.attempt.id))
        val second =
            coordinator.generate(reroll.id) { reply("重生成答") } as CompanionGenerationResult.Saved
        assertEquals(first.receipt.replyMessage.id, second.receipt.replyMessage.id)
        assertTrue(second.receipt.replyMessage.memoryRevision > first.receipt.replyMessage.memoryRevision)
        assertEquals(reroll.id, second.receipt.turn.selectedAttemptId)
        assertEquals(stale, coordinator.retrySave(turn.attempt.id))
        val duplicate = coordinator.retrySave(reroll.id) as CompanionGenerationResult.Saved
        assertEquals(second.receipt.copy(alreadyCommitted = true), duplicate.receipt)
        assertEquals(2, notifications)
        assertEquals(2, repository.messages(turn.turn.encounterId).size)
        assertTrue(
            repository.editMessage(
                second.receipt.replyMessage.id,
                second.receipt.replyMessage.memoryRevision,
                "人工新正文"
            )
        )
        assertEquals(stale, coordinator.retrySave(reroll.id))
        val edited = repository.messages(turn.turn.encounterId).last()
        assertEquals("人工新正文", edited.content)
        assertTrue(repository.deleteMessage(edited.id, edited.memoryRevision))
        assertEquals(stale, coordinator.retrySave(reroll.id))
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
        assertEquals(2, notifications)
    }

    @Test
    fun artifactLostOnRestart_remainsUncertainNotFirstRequest() = runBlocking {
        useFileDatabase()
        val turn = prepared()
        val coordinator = CompanionTurnCoordinator(repository)
        sql("CREATE TRIGGER block_artifact BEFORE UPDATE ON companion_attempts WHEN NEW.artifactJson IS NOT NULL BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        assertTrue(coordinator.generate(turn.attempt.id) { reply() } is CompanionGenerationResult.NeedsSave)
        sql("DROP TRIGGER block_artifact")
        clock += 1_000_000
        reopen()
        assertEquals(
            CompanionAttemptState.UNCERTAIN,
            repository.recoverEncounter(turn.turn.encounterId).single().state
        )
        assertTrue(CompanionTurnCoordinator(repository).retrySave(turn.attempt.id) is CompanionGenerationResult.Rejected)
        assertNull(repository.claimAttempt(turn.attempt.id, "another"))
        assertNull(repository.newAttempt(turn.turn.id))
    }

    @Test
    fun missingEvidence_orEarlyEnd_doesNotPublish() = runBlocking {
        val first = prepared()
        val coordinator = CompanionTurnCoordinator(repository)
        val unknown = coordinator.generate(first.attempt.id) {
            flowOf(ChatStreamEvent.ContentDelta("无结束原因"), ChatStreamEvent.Completed)
        }
        assertEquals(
            CompanionGenerationResult.Generated(
                first.attempt.id,
                PromptCompletionStatus.UNKNOWN
            ), unknown
        )
        assertTrue(archive.listPendingIngress(first.attempt.context).isEmpty())
        val second = prepared()
        val incomplete = coordinator.generate(second.attempt.id) {
            flowOf(
                ChatStreamEvent.ContentDelta("未结束"),
                ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.COMPLETED)
            )
        }
        assertEquals(
            CompanionGenerationResult.Generated(
                second.attempt.id,
                PromptCompletionStatus.INCOMPLETE
            ), incomplete
        )
        assertTrue(archive.listPendingIngress(second.attempt.context).isEmpty())
    }

    @Test
    fun networkFailure_keepsInputAndNeedsExplicitRetry() = runBlocking {
        val turn = prepared()
        var calls = 0
        val result = CompanionTurnCoordinator(repository).generate(turn.attempt.id) {
            calls++
            flow { throw IOException("synthetic") }
        }
        assertTrue(result is CompanionGenerationResult.Failed)
        assertEquals(1, calls)
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
        assertEquals(
            CompanionAttemptState.UNCERTAIN,
            repository.getAttempt(turn.attempt.id)!!.state
        )
        assertNull(repository.newAttempt(turn.turn.id))
    }

    @Test
    fun cancellation_doesNotPublishPartialBody() = runBlocking {
        val turn = prepared()
        val started = CompletableDeferred<Unit>()
        val job = launch {
            CompanionTurnCoordinator(repository).generate(turn.attempt.id) {
                flow {
                    emit(ChatStreamEvent.ContentDelta("尚未完成"))
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(
            CompanionAttemptState.CANCELLED,
            repository.getAttempt(turn.attempt.id)!!.state
        )
        assertTrue(archive.listPendingIngress(turn.attempt.context).isEmpty())
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
    }

    @Test
    fun opening_waitsForExplicitAccept() = runBlocking {
        val turn = prepared(kind = CompanionTurnKind.OPENING, mode = CompanionOpeningMode.INVITE)
        val coordinator = CompanionTurnCoordinator(repository)
        val result = coordinator.generate(turn.attempt.id) { reply("角色走近的预览") }
        assertTrue(result is CompanionGenerationResult.Generated)
        assertTrue(repository.messages(turn.turn.encounterId).isEmpty())
        assertTrue(coordinator.acceptOpening(turn.attempt.id) is CompanionGenerationResult.Saved)
        assertEquals(1, repository.messages(turn.turn.encounterId).size)
    }

    @Test
    fun actualGatewayAndRoom_preserveBodyAndCompletion() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                    "data: {\"choices\":[{\"delta\":{\"content\":\"真实HTTP合成答\"}}]}\n\n" +
                            "data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}\n\n" +
                            "data: [DONE]\n\n"
                )
            )
            val provider = ProviderSettings(
                id = "test", name = "合成", baseUrl = server.url("/v1/").toString(),
                apiKey = "synthetic-key", selectedModel = "test-model",
                openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS
            )
            val gateway = DefaultAiGateway(
                settingsStore = FakeSettingsStore(
                    AppSettings(
                        providers = listOf(provider),
                        selectedProviderId = provider.id
                    )
                ),
                apiServiceFactory = ApiServiceFactory(),
                searchRepository = object : SearchRepository {
                    override suspend fun search(
                        source: SearchSourceConfig,
                        query: String,
                        resultCount: Int
                    ): SearchResult =
                        error("本测试不允许搜索")
                },
                ioDispatcher = Dispatchers.IO,
            )
            val turn = prepared()
            val input = repository.messages(turn.turn.encounterId).single()
            val result = CompanionTurnCoordinator(repository).generate(turn.attempt.id) {
                gateway.sendMessageStream(listOf(input))
            } as CompanionGenerationResult.Saved
            assertEquals("真实HTTP合成答", result.receipt.replyMessage.content)
            assertEquals(
                CompanionSourceEligibility.TEXT_ALLOWED,
                result.receipt.replyMessage.companionSourceEligibility()
            )
            assertEquals(
                PromptCompletionStatus.COMPLETED,
                repository.getAttempt(turn.attempt.id)!!.artifact!!.completionStatus
            )
            val request = server.takeRequest(5, TimeUnit.SECONDS)!!
            assertEquals("/v1/chat/completions", request.path)
            assertTrue(request.body.readUtf8().contains("一起坐一会儿"))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }
}
