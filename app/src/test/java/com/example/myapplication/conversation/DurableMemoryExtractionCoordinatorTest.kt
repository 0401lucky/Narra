package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.RoomMemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.memoryResponse
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.*
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DurableMemoryExtractionCoordinatorTest {
    private lateinit var f: MemoryExtractionFixture

    @Before
    fun setup() {
        f = MemoryExtractionFixture(persistent = true)
    }

    @After
    fun cleanup() {
        f.close()
    }

    @Test
    fun longTail() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        val body = "第一段🙂\n".repeat(90) + "尾部阿澄约定11月19日706号房"
        f.add(context, "long", body)
        f.ai.answer =
            { snapshot -> memoryResponse(snapshot, snapshot.sources.last().text.takeLast(60)) }
        val result = f.coordinator(spanChars = 128, inputChars = 128)
            .processAutomatic(context.conversationId, "a")
        assertEquals("", result.failureCode)
        assertTrue(f.ai.snapshots.size > 2)
        assertTrue(f.ai.snapshots.last().sources.last().text.contains("阿澄约定11月19日706号房"))
        assertTrue(f.archive.listPendingIngress(context).isEmpty())
        assertTrue(f.archive.listRecords(context).any { it.content.contains("706号房") })
        assertTrue(f.ai.models.all { it == "memory-model" })
        val calls = f.ai.snapshots.size
        f.reopen()
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertEquals(calls, f.ai.snapshots.size)
    }

    @Test
    fun secondBatchInvalid() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "long", "完整长消息".repeat(80))
        f.ai.answer = {
            if (f.ai.snapshots.size == 2) PromptCompletionResult(
                "{",
                PromptCompletionStatus.COMPLETED
            ) else memoryResponse(it)
        }
        val result = f.coordinator(spanChars = 128, inputChars = 128)
            .processAutomatic(context.conversationId, "a")
        assertEquals(1, result.committedJobs)
        assertEquals("INVALID_RESPONSE", result.failureCode)
        assertEquals(0L, f.archive.getCursor(context.spaceId)!!.committedSequence)
        assertEquals(1, f.archive.listPendingIngress(context).size)
        assertTrue(f.archive.listRecoverableJobs(context.spaceId).any { it.artifact.isNotEmpty() })
        f.reopen()
        f.coordinator().resumePlanned(context)
        assertEquals(2, f.ai.snapshots.size)
    }

    @Test
    fun commitFailureReopensWithoutNetwork() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "合成有效原文")
        f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_c2 BEFORE INSERT ON memory_record_revisions BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        assertEquals(
            "COMMIT_FAILED",
            f.coordinator().processAutomatic(context.conversationId, "a").failureCode
        )
        assertTrue(f.archive.listRecords(context).isEmpty())
        assertTrue(f.archive.listRecoverableJobs(context.spaceId).single().artifact.isNotBlank())
        f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_c2")
        f.reopen()
        assertEquals(1, f.coordinator().resumePlanned(context).committedJobs)
        assertEquals(1, f.ai.snapshots.size)
        assertEquals(1, f.archive.listRecords(context).size)
    }

    @Test
    fun savedResponseSurvivesMemoryPause() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "合成有效原文")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0); memoryResponse(snapshot)
        }
        assertEquals(
            "MEMORY_DISABLED",
            f.coordinator().processAutomatic(context.conversationId, "a").failureCode
        )
        assertTrue(f.archive.listRecords(context).isEmpty())
        assertTrue(f.archive.listRecoverableJobs(context.spaceId).single().artifact.isNotBlank())
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 1)
        f.reopen()
        assertEquals(1, f.coordinator().resumePlanned(context).committedJobs)
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun cancellationDoesNotRetryUnknownRequest() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        val called = CompletableDeferred<Unit>()
        f.ai.answer = { called.complete(Unit); awaitCancellation() }
        val task = launch { f.coordinator().processAutomatic(context.conversationId, "a") }
        called.await()
        task.cancelAndJoin()
        assertEquals(0L, f.archive.getCursor(context.spaceId)!!.committedSequence)
        f.reopen()
        assertEquals("NETWORK_UNCERTAIN", f.coordinator().resumePlanned(context).failureCode)
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun uncertainFailureIsNotBlindlyRetried() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        f.ai.answer = { throw java.io.IOException("synthetic timeout") }
        assertEquals(
            "NETWORK_UNCERTAIN",
            f.coordinator().processAutomatic(context.conversationId, "a").failureCode
        )
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertEquals(1, f.ai.snapshots.size)
        assertEquals(1, f.archive.listPendingIngress(context).size)
    }

    @Test
    fun saveBooleanMustSucceed() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        val rejecting = object : MemoryArchiveRepository by f.archive {
            override suspend fun saveExtraction(jobId: String, owner: String, artifact: String) =
                false
        }
        val coordinator = f.coordinator(rejecting)
        assertEquals(
            "SAVE_REJECTED",
            coordinator.processAutomatic(context.conversationId, "a").failureCode
        )
        assertTrue(f.archive.listRecords(context).isEmpty())
        assertEquals(0L, f.archive.getCursor(context.spaceId)!!.committedSequence)
        coordinator.processAutomatic(context.conversationId, "a")
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun requestKeepsLeaseWhileModelRuns() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        var renewals = 0
        val renewing = object : MemoryArchiveRepository by f.archive {
            override suspend fun renewLease(
                jobId: String,
                owner: String,
                leaseMillis: Long
            ): Boolean {
                renewals++
                return f.archive.renewLease(jobId, owner, leaseMillis)
            }
        }
        f.ai.answer = { delay(500); memoryResponse(it) }
        assertEquals(
            1,
            f.coordinator(renewing, leaseMillis = 1000)
                .processAutomatic(context.conversationId, "a").committedJobs
        )
        assertTrue(renewals >= 1)
    }

    @Test
    fun committedJobCannotBeReportedAsLeaseLost() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        val slowReturn = object : MemoryArchiveRepository by f.archive {
            override suspend fun commitJob(
                jobId: String,
                owner: String,
                batch: MemoryCommitBatch
            ): MemoryWriteResult {
                val result = f.archive.commitJob(jobId, owner, batch)
                delay(500)
                return result
            }
        }
        val result = f.coordinator(slowReturn, leaseMillis = 1_000L)
            .processAutomatic(context.conversationId, "a")
        assertEquals("", result.failureCode)
        assertEquals(1, result.committedJobs)
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun emptyResultCommitsButIncompleteDoesNot() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "empty", "没有长期内容")
        f.ai.answer = { memoryResponse(it, empty = true) }
        assertEquals(1, f.coordinator().processAutomatic(context.conversationId, "a").committedJobs)
        assertTrue(f.archive.listRecords(context).isEmpty())
        val cursor = f.archive.getCursor(context.spaceId)!!.committedSequence
        f.add(context, "incomplete", "有长期内容")
        f.ai.answer = { memoryResponse(it).copy(status = PromptCompletionStatus.TRUNCATED) }
        assertEquals(
            "INVALID_RESPONSE",
            f.coordinator().processAutomatic(context.conversationId, "a").failureCode
        )
        assertEquals(cursor, f.archive.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun switchesAndUnfinishedMessagesBlockModel() = runBlocking {
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        f.archive.keepCompatibilityEngine()
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertTrue(f.ai.snapshots.isEmpty())
        f.enable(); f.consent = false
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertTrue(f.ai.snapshots.isEmpty())
        f.consent = true; f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertTrue(f.ai.snapshots.isEmpty())
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 4)
        f.add(
            context,
            "loading",
            "尚未完成",
            role = MessageRole.ASSISTANT,
            status = MessageStatus.LOADING
        )
        f.add(
            context,
            "failed",
            "已失败",
            role = MessageRole.ASSISTANT,
            status = MessageStatus.ERROR
        )
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertTrue(f.ai.snapshots.isEmpty())
    }

    @Test
    fun multipleOnlineBubblesDoNotIncreaseFrequency() = runBlocking {
        f.enable(); f.settings = f.settings.copy(memoryAutoSummaryEvery = 4)
        val context = f.contexts().single()
        f.add(context, "u1", "第一轮用户消息")
        repeat(8) {
            f.add(
                context,
                "a$it",
                "线上回复第${it}段",
                role = MessageRole.ASSISTANT,
                speakerId = "a"
            )
        }
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertTrue(f.ai.snapshots.isEmpty())
        f.add(context, "u2", "第二轮用户消息")
        f.add(context, "a-last", "第二轮回复", role = MessageRole.ASSISTANT, speakerId = "a")
        f.coordinator().processAutomatic(context.conversationId, "a")
        assertEquals(1, f.ai.snapshots.size)
        assertEquals(11, f.ai.snapshots.single().sources.size)
    }

    @Test
    fun groupPrivateMaterialIsNotBroadcast() = runBlocking {
        f.enable()
        val contexts = f.contexts(listOf("a", "b"))
        val a = contexts.first { it.characterId == "a" };
        val b = contexts.first { it.characterId == "b" }
        f.add(
            a,
            "mixed",
            "",
            MessageRole.ASSISTANT,
            parts = listOf(textMessagePart("公开见面约定"), thoughtMessagePart("私有心声哨兵")),
            speakerId = "a"
        )
        f.coordinator().processAutomatic(b.conversationId, "b")
        assertFalse(f.ai.snapshots.last().sources.any { it.text.contains("私有心声哨兵") })
        assertTrue(f.ai.snapshots.last().sources.any { it.skipReason == "PRIVATE_TO_SPEAKER" })
        f.coordinator().processAutomatic(a.conversationId, "a")
        assertTrue(f.ai.snapshots.last().sources.any { it.text.contains("私有心声哨兵") })
    }

    @Test
    fun manualArchiveSelectionKeepsPersonaAndCursor() = runBlocking {
        f.enable()
        val context = f.contexts(persona = "旅人").single()
        f.add(context, "selected", "明确选中来源")
        f.archive.archiveConversation(context.conversationId)
        val space = f.archive.getSpace(context.spaceId)!!
        val management = context.copy(conversationId = "", generation = space.generation)
        val result = f.coordinator().processSelection(
            management,
            MemoryMessageSelection(context.conversationId, listOf("selected"))
        )
        assertEquals(1, result.committedJobs)
        assertEquals(context.personaKey, f.archive.listRecords(management).single().personaKey)
        assertEquals(0L, f.archive.getCursor(context.spaceId)!!.committedSequence)
        f.coordinator().processSelection(
            management,
            MemoryMessageSelection(context.conversationId, listOf("selected"))
        )
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun sourceEditRejectsLateArtifactAndScrubsSnapshot() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        val original = f.add(context, "source", "删除原料哨兵")
        f.ai.answer = { snapshot ->
            f.store.upsertMessages(listOf(original.copy(content = "用户修改后的正文"))); memoryResponse(
            snapshot
        )
        }
        val result = f.coordinator().processAutomatic(context.conversationId, "a")
        assertEquals("SAVE_REJECTED", result.failureCode)
        assertTrue(f.archive.listRecords(context).isEmpty())
        f.database.openHelper.readableDatabase.query("SELECT requestJson, artifact FROM memory_processing_jobs")
            .use { cursor ->
                while (cursor.moveToNext()) {
                    assertEquals("", cursor.getString(0)); assertEquals("", cursor.getString(1))
                }
            }
    }

    @Test
    fun selectedOldHistoryStaysUnknownAndDoesNotAutoBackfill() = runBlocking {
        f.enable()
        val conversationId = "legacy-selection"
        f.store.upsertConversationWithMessages(
            Conversation(conversationId, "旧原文", "", f.now, f.now, assistantId = "a"),
            listOf(
                ChatMessage(
                    "old",
                    conversationId,
                    MessageRole.USER,
                    "过去的资料",
                    createdAt = f.now
                )
            )
        )
        val context = f.archive.bindConversation(
            RoleplayScenario(
                id = "old-scene",
                assistantId = "a",
                userPersonaOverride = "现在的身份",
                memoryPurpose = MemoryPurpose.CONTINUOUS
            ),
            conversationId, listOf("a"), "old-run"
        ).single()
        assertTrue(f.archive.listPendingIngress(context).isEmpty())
        val result = f.coordinator().processSelection(
            context,
            MemoryMessageSelection(conversationId, listOf("old", "missing"))
        )
        assertEquals(1, result.committedJobs)
        assertEquals(1, result.skippedSources)
        assertEquals(MEMORY_UNKNOWN_PERSONA, f.ai.snapshots.single().sources.single().personaKey)
        assertTrue(f.ai.snapshots.single().sources.single().visibleCharacterIds.isEmpty())
        assertFalse(f.archive.getCursor(context.spaceId)!!.historyBackfilled)
        f.store.upsertMessages(
            listOf(
                f.store.listMessages(conversationId).single().copy(content = "编辑所选旧原文")
            )
        )
        assertTrue(f.archive.listPendingIngress(context).isEmpty())
    }

    @Test
    fun artifactsKeepDeferredActionsWithoutCredentials() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "完整来源")
        f.ai.answer = { snapshot ->
            memoryResponse(snapshot).let {
                it.copy(
                    content = it.content.replace(
                        "\"state_updates\":[]",
                        "\"state_updates\":[{\"state_key\":\"location\",\"operation\":\"set\",\"content\":\"706\",\"source_refs\":[\"M0\"]}]"
                    )
                )
            }
        }
        f.coordinator().processAutomatic(context.conversationId, "a")
        val job = f.archive.listCommittedExtractionJobs(context).single()
        assertTrue(job.artifact.contains("state_updates"))
        assertTrue(job.artifact.contains("706"))
        assertFalse(job.artifact.contains("synthetic-secret-sentinel"))
        assertFalse(job.requestJson.contains("synthetic-secret-sentinel"))
        assertEquals(1, f.archive.getJobRecordIds(job.id).size)
    }

    @Test
    fun mediaOnlyCommitsSkippedReceiptWithoutModel() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(
            context,
            "image",
            "图片已生成",
            parts = listOf(imageMessagePart("content://private/image"))
        )
        val result = f.coordinator().processAutomatic(context.conversationId, "a")
        assertEquals(1, result.committedJobs)
        assertEquals(1, result.skippedSources)
        assertTrue(f.ai.snapshots.isEmpty())
        assertTrue(f.archive.listRecords(context).isEmpty())
        assertTrue(f.archive.listPendingIngress(context).isEmpty())
        assertTrue(
            f.archive.listCommittedExtractionJobs(context)
                .single().artifact.contains("MEDIA_WITHOUT_SEMANTICS")
        )
    }

    @Test
    fun manualEditWinsOverDelayedCorrection() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "initial", "地址原来是705")
        f.coordinator().processAutomatic(context.conversationId, "a")
        val old = f.archive.listRecords(context).single()
        f.add(context, "correction", "正确地址是706")
        f.ai.answer = { _ ->
            f.archive.editRecord(context, old.id, old.revision, "用户手工确认707")
            PromptCompletionResult(
                "{\"schema_version\":2,\"memories\":[],\"corrections\":[{\"target_ref\":\"O0\",\"expected_revision\":1,\"replacement\":\"706\",\"source_refs\":[\"M0\"]}],\"state_updates\":[],\"resident_candidates\":[]}",
                PromptCompletionStatus.COMPLETED
            )
        }
        assertEquals(
            "RECORD_CHANGED",
            f.coordinator().processAutomatic(context.conversationId, "a").failureCode
        )
        assertEquals(
            "用户手工确认707",
            f.archive.getRecordDetails(context, old.id)!!.record.content
        )
        assertEquals(1, f.archive.listPendingIngress(context).size)
        f.reopen(); f.coordinator().resumePlanned(context)
        assertEquals(2, f.ai.snapshots.size)
    }

    @Test
    fun automaticBatchesRefreshUnrequestedTargets() = runBlocking {
        verifyUnrequestedTargetRefresh(selected = false)
    }

    @Test
    fun selectedBatchesRefreshUnrequestedTargets() = runBlocking {
        verifyUnrequestedTargetRefresh(selected = true)
    }

    private suspend fun verifyUnrequestedTargetRefresh(selected: Boolean) {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "initial", "原地址705")
        f.coordinator().processAutomatic(context.conversationId, "a")
        val original = f.archive.listRecords(context).single()
        val cursorBefore = f.archive.getCursor(context.spaceId)!!.committedSequence
        f.add(context, "correction", "正确地址706。".repeat(12))
        f.add(context, "later", "另一项完整事实。".repeat(12))
        f.ai.answer = { snapshot ->
            if (snapshot.sources.first().messageId == "correction") {
                f.settings =
                    f.settings.copy(memoryExtractionPrompt = "后来修改的模板不能覆盖冻结模板")
                val target = snapshot.targets.indexOfFirst { it.id == original.id }
                PromptCompletionResult(
                    """{"schema_version":2,"memories":[],"corrections":[{"target_ref":"O$target","expected_revision":1,"replacement":"正确地址706","source_refs":["M0"]}],"state_updates":[],"resident_candidates":[]}""",
                    PromptCompletionStatus.COMPLETED,
                )
            } else memoryResponse(snapshot, "后一批的新经历")
        }
        val coordinator = f.coordinator(spanChars = 128, inputChars = 128)
        val result = if (selected) coordinator.processSelectedMessages(
            context.conversationId,
            "a",
            listOf("correction", "later")
        )
        else coordinator.processAutomatic(context.conversationId, "a")
        assertEquals("", result.failureCode)
        assertEquals(2, result.committedJobs)
        assertEquals(3, f.ai.snapshots.size)
        val last = f.ai.snapshots.last()
        assertEquals(2L, last.targets.single { it.id == original.id }.revision)
        assertEquals("正确地址706", last.targets.single { it.id == original.id }.content)
        assertEquals(MemoryExtractionFormat.V2, last.format)
        assertEquals("", last.promptOverride)
        assertEquals(2, f.archive.listRecords(context).size)
        val superseded = f.archive.listJobHistory(context.copy(conversationId = "")).single {
            it.failureCode == "TARGETS_CHANGED_BEFORE_REQUEST"
        }
        assertEquals(MemoryJobStatus.INVALIDATED, superseded.status)
        assertEquals(1L, superseded.targetRevisions[original.id])
        assertTrue(superseded.artifact.isBlank())
        assertTrue(superseded.requestJson.isNotBlank())
        if (selected) assertEquals(
            cursorBefore,
            f.archive.getCursor(context.spaceId)!!.committedSequence
        )
        else assertTrue(f.archive.listPendingIngress(context).isEmpty())
        f.reopen()
        if (selected) f.coordinator()
            .processSelectedMessages(context.conversationId, "a", listOf("correction", "later"))
        else f.coordinator().resumePlanned(context)
        assertEquals(3, f.ai.snapshots.size)
    }

    @Test
    fun startupWaitsForSavedLeaseAndDeduplicatesRecovery() = runTest {
        val context = f.contexts().single()
        val job = savedJobWithOldLease(context)
        val waiting = CompletableDeferred<Unit>()
        var waits = 0
        val coordinator = startupCoordinator(testScheduler) {
            waits++
            waiting.complete(Unit)
            delay(it)
        }
        val first = async { coordinator.resumePlannedOnStartup(context) }
        waiting.await()
        assertEquals("old-process", f.archive.getJob(job.id)!!.leaseOwner)
        assertTrue(f.archive.listRecords(context).isEmpty())
        val second = async { coordinator.resumePlannedOnStartup(context) }
        assertEquals(1, first.await().committedJobs)
        assertEquals(0, second.await().committedJobs)
        assertEquals(1, waits)
        assertEquals(1_000L, testScheduler.currentTime)
        assertEquals(1, f.ai.snapshots.size)
        assertEquals(MemoryJobStatus.COMMITTED, f.archive.getJob(job.id)!!.status)
    }

    @Test
    fun startupDoesNotStealRenewedLease() = runTest {
        val context = f.contexts().single()
        val job = savedJobWithOldLease(context)
        val waiting = CompletableDeferred<Unit>()
        val renewed = CompletableDeferred<Unit>()
        val clock = { f.now + testScheduler.currentTime }
        val archive = RoomMemoryArchiveRepository(f.database, clock)
        var firstWait = true
        val coordinator = startupCoordinator(testScheduler) { duration ->
            val wakeAt = testScheduler.currentTime + duration
            if (firstWait) {
                firstWait = false
                waiting.complete(Unit)
                // Room 续租使用真实执行器；在它完成前不让虚拟时钟越过原定唤醒点。
                renewed.await()
            }
            delay((wakeAt - testScheduler.currentTime).coerceAtLeast(0L))
        }
        val recovery = async { coordinator.resumePlannedOnStartup(context) }
        waiting.await()
        delay(500)
        assertTrue(archive.renewLease(job.id, "old-process", 1_000L))
        renewed.complete(Unit)
        assertEquals(1, recovery.await().committedJobs)
        assertEquals(1_500L, testScheduler.currentTime)
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun startupStopsWhenConsentIsWithdrawnDuringLeaseWait() = runTest {
        val context = f.contexts().single()
        val job = savedJobWithOldLease(context)
        val waiting = CompletableDeferred<Unit>()
        val coordinator = startupCoordinator(testScheduler) { waiting.complete(Unit); delay(it) }
        val recovery = async { coordinator.resumePlannedOnStartup(context) }
        waiting.await()
        f.consent = false
        assertEquals("MEMORY_DISABLED", recovery.await().failureCode)
        assertEquals(1, f.ai.snapshots.size)
        assertTrue(f.archive.getJob(job.id)!!.artifact.isNotBlank())
        assertTrue(f.archive.listRecords(context).isEmpty())
    }

    @Test
    fun startupStopsWhenMemoryIsDisabledDuringLeaseWait() = runTest {
        val context = f.contexts().single()
        val job = savedJobWithOldLease(context)
        val waiting = CompletableDeferred<Unit>()
        val coordinator = startupCoordinator(testScheduler) { waiting.complete(Unit); delay(it) }
        val recovery = async { coordinator.resumePlannedOnStartup(context) }
        waiting.await()
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
        assertEquals("MEMORY_DISABLED", recovery.await().failureCode)
        assertEquals(1, f.ai.snapshots.size)
        assertTrue(f.archive.getJob(job.id)!!.artifact.isNotBlank())
        assertTrue(f.archive.listRecords(context).isEmpty())
    }

    @Test
    fun cancellingStartupCancelsItsLeaseWait() = runTest {
        val context = f.contexts().single()
        val job = savedJobWithOldLease(context)
        val waiting = CompletableDeferred<Unit>()
        val coordinator = startupCoordinator(testScheduler) { waiting.complete(Unit); delay(it) }
        val recovery = launch { coordinator.resumePlannedOnStartup(context) }
        waiting.await()
        recovery.cancelAndJoin()
        delay(2_000L)
        assertEquals(MemoryJobStatus.EXTRACTED, f.archive.getJob(job.id)!!.status)
        assertTrue(f.archive.listRecords(context).isEmpty())
        assertEquals(1, f.ai.snapshots.size)
    }

    @Test
    fun startupNeverReissuesRequestWithoutSavedArtifact() = runTest {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "未确定网络结果的来源")
        f.ai.answer = { throw java.io.IOException("synthetic timeout") }
        withContext(Dispatchers.IO) {
            f.coordinator().processAutomatic(context.conversationId, "a")
        }
        f.reopen()
        val coordinator = startupCoordinator(testScheduler) { error("无产物不得启动延后重试") }
        assertEquals("NETWORK_UNCERTAIN", coordinator.resumePlannedOnStartup(context).failureCode)
        assertEquals(1, f.ai.snapshots.size)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun archivedPaidSourceWaitsForExplicitSelectionWithoutBlockingNewSources() = runBlocking {
        f.enable()
        val previous = f.contexts().single()
        f.add(previous, "paid", "旧运行已收到产物的来源")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            memoryResponse(snapshot, "旧运行产物哨兵")
        }
        f.coordinator().processAutomatic(previous.conversationId, "a")
        val paidJob = f.archive.listRecoverableJobs(previous.spaceId).single()
        assertEquals(MemoryRequestState.STARTED, paidJob.requestState)
        f.archive.archiveConversation(previous.conversationId)
        val current = f.contexts().single()
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 1)
        f.ai.answer = { memoryResponse(it, "新来源经历") }
        f.add(current, "new", "新运行独立来源")
        f.reopen()
        val result = f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals("REQUIRES_MANUAL_REVIEW", result.failureCode)
        assertEquals(1, result.committedJobs)
        assertEquals(listOf("new"), f.ai.snapshots.last().sources.map { it.messageId })
        assertEquals(2, f.ai.snapshots.size)
        assertEquals(0L, f.archive.getCursor(current.spaceId)!!.committedSequence)
        assertEquals(
            listOf("paid"),
            f.archive.listPendingIngress(current).map { it.source.messageId })
        assertTrue(f.archive.getJob(paidJob.id)!!.artifact.contains("旧运行产物哨兵"))
        f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals(2, f.ai.snapshots.size)
        val selected = f.coordinator().processSelection(
            current.copy(conversationId = ""),
            MemoryMessageSelection(previous.conversationId, listOf("paid")),
            reprocessExisting = true,
        )
        assertEquals(1, selected.committedJobs)
        assertEquals(3, f.ai.snapshots.size)
        assertEquals(0L, f.archive.getCursor(current.spaceId)!!.committedSequence)
    }

    @Test
    fun generationChangePreservesUncertainRequestEvidenceAfterDeletingSnapshot() = runBlocking {
        f.enable()
        val previous = f.contexts().single()
        f.add(previous, "initial", "原来已确认的旧记忆")
        f.coordinator().processAutomatic(previous.conversationId, "a")
        val record = f.archive.listRecords(previous).single()
        val cursorBefore = f.archive.getCursor(previous.spaceId)!!.committedSequence
        f.add(previous, "uncertain", "已经发出但响应未知的来源")
        f.ai.answer = { throw java.io.IOException("synthetic timeout") }
        f.coordinator().processAutomatic(previous.conversationId, "a")
        val uncertain = f.archive.listRecoverableJobs(previous.spaceId).single()
        assertEquals(MemoryRequestState.STARTED, uncertain.requestState)
        f.archive.deleteRecord(previous, record.id, record.revision)
        val current = f.archive.getRuntimeContext(previous.conversationId, "a")!!
        val invalidated = f.archive.getJob(uncertain.id)!!
        assertEquals("RECORD_DELETED", invalidated.failureCode)
        assertEquals("", invalidated.requestJson)
        assertEquals(MemoryRequestState.STARTED, invalidated.requestState)
        f.reopen()
        f.ai.answer = { memoryResponse(it, "新来源仍可处理") }
        f.add(current, "new", "不依赖旧请求的新来源")
        val progress = f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals("REQUIRES_MANUAL_REVIEW", progress.failureCode)
        assertEquals(1, progress.committedJobs)
        assertEquals(3, f.ai.snapshots.size)
        assertEquals(listOf("new"), f.ai.snapshots.last().sources.map { it.messageId })
        assertEquals(cursorBefore, f.archive.getCursor(current.spaceId)!!.committedSequence)
        assertEquals(
            listOf("uncertain"),
            f.archive.listPendingIngress(current).map { it.source.messageId })
    }

    @Test
    fun deletingUnrelatedRecordRetainsPaidArtifactWithoutAutomaticRecharge() = runBlocking {
        f.enable()
        val original = f.contexts().single()
        f.add(original, "paid", "已收到产物的来源")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            memoryResponse(snapshot, "付费产物保留哨兵")
        }
        f.coordinator().processAutomatic(original.conversationId, "a")
        val paidJob = f.archive.listRecoverableJobs(original.spaceId).single()
        val other = f.contexts().single()
        f.add(other, "unrelated", "另一来源的经历")
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 1)
        f.ai.answer = { memoryResponse(it, "另一来源的经历") }
        f.coordinator().processAutomatic(other.conversationId, "a")
        val unrelated = f.archive.listRecords(other).single()
        f.archive.deleteRecord(other, unrelated.id, unrelated.revision)
        f.reopen()
        val current = f.archive.getRuntimeContext(other.conversationId, "a")!!
        assertTrue(f.archive.getJob(paidJob.id)!!.artifact.contains("付费产物保留哨兵"))
        assertTrue(f.archive.getJob(paidJob.id)!!.requestJson.isNotBlank())
        f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals(2, f.ai.snapshots.size)
        assertEquals(0L, f.archive.getCursor(current.spaceId)!!.committedSequence)
        assertEquals(
            listOf("paid"),
            f.archive.listPendingIngress(current).map { it.source.messageId })
    }

    @Test
    fun unrequestedOldSnapshotCanBeProcessedInTheNewDailyRun() = runBlocking {
        f.enable()
        val previous = f.contexts().single()
        f.add(previous, "unrequested", "尚未发出请求的完整来源")
        val source = f.archive.listPendingIngress(previous).single().source
        val snapshot = MemoryExtractionSnapshot(
            MemoryExtractionBatcher.sourceSpans(
                source,
                f.archive.getSourceSegments(previous, source.id),
                "a"
            ),
            emptyList(), "甲", "用户",
        )
        val oldJob = f.archive.planJob(
            previous,
            "unrequested-old",
            snapshot.sources.map { it.ref },
            requestJson = AppJson.gson.toJson(snapshot)
        )
        assertEquals(MemoryRequestState.NOT_STARTED, oldJob.requestState)
        f.archive.archiveConversation(previous.conversationId)
        f.reopen()
        val current = f.contexts().single()
        val result = f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals("", result.failureCode)
        assertEquals(1, result.committedJobs)
        assertEquals(1, f.ai.snapshots.size)
        assertTrue(f.archive.listPendingIngress(current).isEmpty())
    }

    @Test
    fun protectedOldSourcesDoNotIncreaseAutomaticFrequency() = runBlocking {
        f.enable()
        val previous = f.contexts().single()
        f.add(previous, "old-user", "旧用户来源")
        f.add(previous, "old-reply", "旧助手来源", role = MessageRole.ASSISTANT, speakerId = "a")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            memoryResponse(snapshot)
        }
        f.coordinator().processAutomatic(previous.conversationId, "a")
        f.archive.archiveConversation(previous.conversationId)
        val current = f.contexts().single()
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 4)
        f.ai.answer = { memoryResponse(it) }
        f.add(current, "new-user-1", "第一轮新发言")
        f.add(current, "new-reply-1", "第一轮新回复", role = MessageRole.ASSISTANT, speakerId = "a")
        f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals(1, f.ai.snapshots.size)
        f.add(current, "new-user-2", "第二轮新发言")
        f.add(current, "new-reply-2", "第二轮新回复", role = MessageRole.ASSISTANT, speakerId = "a")
        f.coordinator().processAutomatic(current.conversationId, "a")
        assertEquals(2, f.ai.snapshots.size)
        assertEquals(
            setOf("new-user-1", "new-reply-1", "new-user-2", "new-reply-2"),
            f.ai.snapshots.last().sources.map { it.messageId }.toSet()
        )
        assertEquals(0L, f.archive.getCursor(current.spaceId)!!.committedSequence)
    }

    @Test
    fun claimedRequestStateWinsOverAnEarlierUnrequestedSnapshot() = runBlocking {
        f.enable()
        val context = f.contexts().single()
        f.add(context, "source", "两个协调器共享的来源")
        var preempted = false
        val racing = object : MemoryArchiveRepository by f.archive {
            override suspend fun claimJob(
                jobId: String,
                owner: String,
                leaseMillis: Long
            ): MemoryProcessingJob? {
                if (!preempted) {
                    preempted = true
                    assertNotNull(f.archive.claimJob(jobId, "other-process", leaseMillis))
                    assertTrue(f.archive.markRequestStarted(jobId, "other-process"))
                    assertFalse(f.archive.markRequestStarted(jobId, "other-process"))
                    f.archive.releaseJob(
                        jobId,
                        "other-process",
                        MemoryJobStatus.PAUSED,
                        "NETWORK_UNCERTAIN"
                    )
                }
                return f.archive.claimJob(jobId, owner, leaseMillis)
            }
        }
        val result = f.coordinator(racing).processAutomatic(context.conversationId, "a")
        assertEquals("NETWORK_UNCERTAIN", result.failureCode)
        assertTrue(f.ai.snapshots.isEmpty())
        assertEquals(
            MemoryRequestState.STARTED,
            f.archive.listRecoverableJobs(context.spaceId).single().requestState
        )
        assertEquals(0L, f.archive.getCursor(context.spaceId)!!.committedSequence)
    }

    private suspend fun savedJobWithOldLease(context: MemoryRuntimeContext): MemoryProcessingJob {
        f.enable()
        f.add(context, "source", "已付费收到的完整来源")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            memoryResponse(snapshot)
        }
        withContext(Dispatchers.IO) {
            f.coordinator().processAutomatic(context.conversationId, "a")
        }
        f.settings = f.settings.copy(memoryAutoSummaryEvery = 1)
        val saved = f.archive.listRecoverableJobs(context.spaceId).single()
        f.archive.claimJob(saved.id, "old-process", 1_000L)
        f.reopen()
        return f.archive.getJob(saved.id)!!
    }

    private fun startupCoordinator(
        scheduler: TestCoroutineScheduler,
        waitForLease: suspend (Long) -> Unit,
    ): DurableMemoryExtractionCoordinator {
        val clock = { f.now + scheduler.currentTime }
        val archive = RoomMemoryArchiveRepository(
            f.database,
            clock,
            { id -> f.settings.resolvedAssistants().any { it.id == id } })
        return DurableMemoryExtractionCoordinator(
            archive, f.ai, { f.settings }, { f.consent },
            nowProvider = clock, waitForLease = waitForLease,
        )
    }

    @Test
    fun archivedPaidArtifactIsRetainedButCannotPublish() = runBlocking {
        f.enable()
        val context = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
        f.add(context, "source", "旧档案的原料哨兵")
        f.ai.answer = { snapshot ->
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0); memoryResponse(
            snapshot,
            "旧档案产物哨兵"
        )
        }
        f.coordinator().processAutomatic(context.conversationId, "a")
        val paused = f.archive.listRecoverableJobs(context.spaceId).single()
        f.archive.claimJob(paused.id, "old-owner")
        f.archive.archiveConversation(context.conversationId)
        val archived = f.archive.getJob(paused.id)!!
        assertEquals(MemoryJobStatus.INVALIDATED, archived.status)
        assertTrue(archived.artifact.contains("旧档案产物哨兵"))
        assertTrue(archived.requestJson.contains("旧档案的原料哨兵"))
        assertNull(f.archive.claimJob(paused.id, "new-owner"))
        assertTrue(
            f.archive.commitJob(
                paused.id,
                "old-owner",
                MemoryCommitBatch()
            ) is MemoryWriteResult.Conflict
        )
        val management = context.copy(
            conversationId = "",
            generation = f.archive.getSpace(context.spaceId)!!.generation
        )
        assertTrue(
            f.archive.listJobHistory(management).single().artifact.contains("旧档案产物哨兵")
        )
        f.archive.deleteSpace(management)
        assertEquals("", f.archive.getJob(paused.id)!!.artifact)
        assertEquals("", f.archive.getJob(paused.id)!!.requestJson)
    }
}
