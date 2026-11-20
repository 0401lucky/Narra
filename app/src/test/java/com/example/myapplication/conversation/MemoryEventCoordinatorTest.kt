package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryEventCoordinatorTest {
    @Test
    fun automaticThreshold_countsOnlyNewMembersAndUsesMemoryModel() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context, listOf("起因", "进展一", "进展二"))
            val ai = RecordingMemoryEventAi()
            val coordinator = f.eventCoordinator(ai)
            assertEquals(0, coordinator.processAutomatic(context).committedEvents)
            assertTrue(ai.snapshots.isEmpty())
            val last = f.remember(context, "跨天结局706")
            f.archive.linkEventMembers(context, version(last), version(records.first()))
            assertEquals(1, coordinator.processAutomatic(context).committedEvents)
            assertEquals(listOf("memory-model"), ai.models)
            assertEquals(0, coordinator.processAutomatic(context).committedEvents)
            val latest = f.remember(context, "归还后的新进展")
            f.archive.linkEventMembers(context, version(latest), version(records.first()))
            assertEquals(0, coordinator.processAutomatic(context).committedEvents)
            val detail = f.archive.getEventDetails(context, event.id)!!
            assertEquals(1L, detail.event.summaryRevision)
            assertEquals(1, detail.members.count { it.coveredRevision == null })
            assertEquals(1, ai.snapshots.size)
        }
    }

    @Test
    fun equivalentBodyBudget_canSummarizeFewerLongMembers() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            f.createEvent(
                context,
                listOf("起因原料".repeat(750) + "尾部706", "过程原料".repeat(750) + "最后阿澄")
            )
            val ai = RecordingMemoryEventAi()
            val result = f.eventCoordinator(ai).processAutomatic(context)
            assertEquals("", result.failureCode)
            assertEquals(1, result.committedEvents)
            assertEquals(2, ai.snapshots.flatMap { it.members }.size)
            assertTrue(ai.snapshots.flatMap { it.members }
                .any { it.record.content.endsWith("最后阿澄") })
        }
    }

    @Test
    fun incrementalSummary_freezesOldCoverageAndOnlyNewRawMaterials() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, old) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            val coordinator = f.eventCoordinator(ai)
            coordinator.processAutomatic(context)
            val previous = f.archive.getEventDetails(context, event.id)!!.event
            val latest = (1..4).map { f.remember(context, "第${it + 4}天的新进展") }
            latest.forEach {
                f.archive.linkEventMembers(
                    context,
                    version(it),
                    version(old.first())
                )
            }
            assertEquals(1, coordinator.processAutomatic(context).committedEvents)
            val snapshot = ai.snapshots.last()
            assertEquals(MemoryEventSummaryMode.INCREMENTAL, snapshot.mode)
            assertFalse(snapshot.previousSummaryFromCurrentWork)
            assertEquals(previous.summary, snapshot.previousSummary)
            assertEquals(old.map(::version).toSet(), snapshot.previousCoverage.toSet())
            assertEquals(
                latest.map { it.id }.toSet(),
                snapshot.members.map { it.record.id }.toSet()
            )
            assertEquals(
                8,
                f.archive.getEventDetails(
                    context,
                    event.id
                )!!.members.count { it.coveredRevision != null })
        }
    }

    @Test
    fun fullRebuild_usesAllRawRecordsAndRepairsOmittedDetails() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = {
                memoryEventResponse(
                    it,
                    summary = "已修好罗盘，但第一次摘要遗漏齿轮编号",
                    state = "已归还"
                )
            }
            val coordinator = f.eventCoordinator(ai)
            coordinator.processAutomatic(context)
            ai.answer = { snapshot ->
                assertEquals(MemoryEventSummaryMode.FULL, snapshot.mode)
                assertEquals("", snapshot.previousSummary)
                assertTrue(snapshot.previousCoverage.isEmpty())
                assertEquals(
                    records.map { it.id }.toSet(),
                    snapshot.members.map { it.record.id }.toSet()
                )
                assertTrue(snapshot.members.flatMap { it.sources }
                    .any { it.content.contains("706") })
                memoryEventResponse(
                    snapshot,
                    summary = "芮珩委托修复，使用706号齿轮，最后归还",
                    state = "已归还"
                )
            }
            val rebuilt = coordinator.rebuild(context, event.id, event.generation)
            assertEquals("", rebuilt.failureCode)
            assertEquals(1, rebuilt.committedEvents)
            val detail = f.archive.getEventDetails(context, event.id)!!
            assertEquals(2L, detail.event.summaryRevision)
            assertTrue(detail.event.summary.contains("706"))
            assertTrue(detail.members.all { it.record.content == records.single { record -> record.id == it.record.id }.content })
        }
    }

    @Test
    fun longMultiBatch_allMembersReachModelBeforeAtomicPublication() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val bodies =
                (0..7).map { "第$it 天原料：" + "原始叙述".repeat(400) + "尾部线索编号${706 + it}" }
            val (event, records) = f.createEvent(context, bodies)
            val ai = RecordingMemoryEventAi()
            ai.answer = { snapshot ->
                val detail = f.archive.getEventDetails(context, event.id)!!
                assertEquals(0L, detail.event.summaryRevision)
                assertTrue(detail.members.all { it.coveredRevision == null })
                snapshot.members.forEach { member ->
                    assertEquals(member.record.content, member.sources.single().content)
                    assertEquals(member.sources.single().fullRef(), member.sourceRefs.single())
                }
                memoryEventResponse(snapshot)
            }
            val result =
                f.eventCoordinator(ai, policy = MemoryEventSummaryPolicy(batchContentChars = 2400))
                    .processAutomatic(context)
            assertEquals("", result.failureCode)
            assertEquals(8, result.completedBatches)
            assertEquals(1, result.committedEvents)
            assertEquals(
                records.map { it.id }.toSet(),
                ai.snapshots.flatMap { it.members }.map { it.record.id }.toSet()
            )
            assertEquals(MemoryEventSummaryMode.FULL, ai.snapshots.first().mode)
            assertTrue(
                ai.snapshots.drop(1)
                    .all { it.mode == MemoryEventSummaryMode.INCREMENTAL && it.previousSummaryFromCurrentWork && it.summaryRevision == 0L })
            assertEquals((0..7).map { it }, ai.snapshots.map { it.previousCoverage.size })
            val detail = f.archive.getEventDetails(context, event.id)!!
            assertTrue(detail.members.all { it.coveredRevision == 1L })
            assertTrue(detail.event.summary.contains("713"))
            assertEquals(bodies.toSet(), detail.members.map { it.record.content }.toSet())
        }
    }

    @Test
    fun commitFailRestart_reusesReceivedArtifactWithoutNewRequest() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            val failing = object : MemoryArchiveRepository by f.archive {
                override suspend fun commitEventSummary(
                    jobId: String,
                    owner: String
                ): MemoryWriteResult = error("synthetic database commit failure")
            }
            val first = f.eventCoordinator(ai, failing).processAutomatic(context)
            assertEquals("COMMIT_FAILED", first.failureCode)
            assertEquals(1, ai.snapshots.size)
            assertEquals(0L, f.archive.getEventDetails(context, event.id)!!.event.summaryRevision)
            f.reopen()
            val resumed = f.eventCoordinator(ai).resumePlanned(context)
            assertEquals("", resumed.failureCode)
            assertEquals(1, resumed.committedEvents)
            assertEquals(1, ai.snapshots.size)
            assertEquals(1L, f.archive.getEventDetails(context, event.id)!!.event.summaryRevision)
        }
    }

    @Test
    fun batchSaveFailure_sameProcessReusesResponseAndContinues() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            var fail = true
            val flaky = object : MemoryArchiveRepository by f.archive {
                override suspend fun saveEventSummaryBatch(
                    jobId: String,
                    owner: String,
                    batchIndex: Int,
                    completion: PromptCompletionResult
                ): Boolean {
                    if (batchIndex == 1 && fail) {
                        fail = false; error("synthetic batch disk failure")
                    }
                    return f.archive.saveEventSummaryBatch(jobId, owner, batchIndex, completion)
                }
            }
            val coordinator = f.eventCoordinator(
                ai,
                flaky,
                policy = MemoryEventSummaryPolicy(batchContentChars = 900)
            )
            assertEquals("SAVE_FAILED", coordinator.processAutomatic(context).failureCode)
            val job =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            val artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
            assertEquals(1, artifact.completions.size)
            assertEquals(1, artifact.pendingBatch)
            assertEquals(2, ai.snapshots.size)
            assertEquals(1, coordinator.resumePlanned(context).committedEvents)
            assertEquals(4, ai.snapshots.size)
            assertEquals(
                4,
                f.archive.getEventDetails(
                    context,
                    event.id
                )!!.members.count { it.coveredRevision != null })
        }
    }

    @Test
    fun midBatchRestart_preservesProgressAndDoesNotResendUncertainBatch() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = {
                if (ai.snapshots.size == 2) error("synthetic lost connection") else memoryEventResponse(
                    it
                )
            }
            val result =
                f.eventCoordinator(ai, policy = MemoryEventSummaryPolicy(batchContentChars = 900))
                    .processAutomatic(context)
            assertEquals("NETWORK_UNCERTAIN", result.failureCode)
            assertEquals(2, ai.snapshots.size)
            val original =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            assertEquals(
                1,
                MemoryEventSummaryBatches.readArtifact(original.artifact).completions.size
            )
            f.reopen()
            assertEquals(
                "NETWORK_UNCERTAIN",
                f.eventCoordinator(ai).resumePlanned(context).failureCode
            )
            assertEquals(2, ai.snapshots.size)
            assertTrue(
                f.archive.getEventDetails(
                    context,
                    event.id
                )!!.members.all { it.coveredRevision == null })
            val reviewed = f.archive.planEventSummary(
                context,
                event.id,
                MemoryEventSummaryOptions(
                    manualRebuild = true,
                    expectedGeneration = event.generation
                )
            )
            assertTrue(reviewed is MemoryEventPlanResult.NeedsReview)
            ai.answer = { memoryEventResponse(it) }
            val manual = f.eventCoordinator(ai)
                .rebuild(context, event.id, event.generation, retryUncertain = true)
            assertEquals("", manual.failureCode)
            assertEquals(1, manual.committedEvents)
        }
    }

    @Test
    fun unknownAndInvalidResponses_preserveRawWithoutArchivingOrRetrying() = runBlocking {
        for (unknown in listOf(true, false)) MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = {
                if (unknown) memoryEventResponse(
                    it,
                    status = PromptCompletionStatus.UNKNOWN
                ) else PromptCompletionResult("{", PromptCompletionStatus.COMPLETED)
            }
            val coordinator = f.eventCoordinator(ai)
            assertEquals("INVALID_RESPONSE", coordinator.processAutomatic(context).failureCode)
            assertEquals("INVALID_RESPONSE", coordinator.resumePlanned(context).failureCode)
            assertEquals(1, ai.snapshots.size)
            val detail = f.archive.getEventDetails(context, event.id)!!
            assertEquals(0L, detail.event.summaryRevision)
            assertTrue(detail.members.all { it.coveredRevision == null })
            val job =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            assertEquals(1, MemoryEventSummaryBatches.readArtifact(job.artifact).completions.size)
            assertEquals(MemoryRequestState.STARTED, job.requestState)
        }
    }

    @Test
    fun cancellation_isPropagatedAndLeavesUncertainRequestEvidence() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = { throw CancellationException("synthetic cancellation") }
            try {
                f.eventCoordinator(ai).processAutomatic(context); fail("取消必须继续传播")
            } catch (_: CancellationException) {
            }
            val job =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            assertEquals(MemoryJobStatus.PAUSED, job.status)
            assertEquals(MemoryRequestState.STARTED, job.requestState)
            assertEquals(0, MemoryEventSummaryBatches.readArtifact(job.artifact).pendingBatch)
            assertEquals(
                "NETWORK_UNCERTAIN",
                f.eventCoordinator(ai).resumePlanned(context).failureCode
            )
            assertEquals(1, ai.snapshots.size)
        }
    }

    @Test
    fun consentAndMemoryGuards_blockRequestsAndRetainReceivedWork() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            val coordinator = f.eventCoordinator(ai)
            f.consent = false
            coordinator.processAutomatic(context)
            assertTrue(ai.snapshots.isEmpty())
            f.consent = true
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            coordinator.processAutomatic(context)
            assertTrue(ai.snapshots.isEmpty())
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 1)
            ai.answer = { f.consent = false; memoryEventResponse(it) }
            assertEquals("MEMORY_DISABLED", coordinator.processAutomatic(context).failureCode)
            assertEquals(0L, f.archive.getEventDetails(context, event.id)!!.event.summaryRevision)
            f.consent = true
            assertEquals(1, coordinator.resumePlanned(context).committedEvents)
            assertEquals(1, ai.snapshots.size)
        }
    }

    @Test
    fun missingModel_doesNotMarkARequestAsStarted() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            f.settings =
                f.settings.copy(providers = f.settings.providers.map { it.copy(apiKey = "") })
            assertEquals(
                "MODEL_UNAVAILABLE",
                f.eventCoordinator(ai).processAutomatic(context).failureCode
            )
            val job =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            assertEquals(MemoryRequestState.NOT_STARTED, job.requestState)
            assertEquals("", job.artifact)
            assertTrue(ai.snapshots.isEmpty())
        }
    }

    @Test
    fun leaseStartup_waitsThenCommitsSavedResponseWithoutRequest() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val job =
                (f.archive.planEventSummary(context, event.id) as MemoryEventPlanResult.Planned).job
            assertNotNull(f.archive.claimJob(job.id, "previous-process", 1000L))
            assertTrue(f.archive.beginEventSummaryBatch(job.id, "previous-process", 0))
            assertTrue(
                f.archive.saveEventSummaryBatch(
                    job.id,
                    "previous-process",
                    0,
                    memoryEventResponse(MemoryEventSummaryBatches.readWork(job.requestJson).snapshot)
                )
            )
            val ai = RecordingMemoryEventAi()
            var waits = 0
            val coordinator =
                f.eventCoordinator(ai, waitForLease = { millis -> waits++; f.now += millis })
            assertEquals(1, coordinator.resumePlannedOnStartup(context).committedEvents)
            assertTrue(waits > 0)
            assertTrue(ai.snapshots.isEmpty())
        }
    }

    @Test
    fun editingDuringModelRequest_winsOverLateSummary() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = {
                assertTrue(
                    f.archive.editRecord(
                        context,
                        records.first().id,
                        records.first().revision,
                        "人工纠正：齿轮编号709"
                    ) is MemoryWriteResult.Committed
                )
                memoryEventResponse(it)
            }
            assertEquals(
                "SAVE_REJECTED",
                f.eventCoordinator(ai).processAutomatic(context).failureCode
            )
            val detail = f.archive.getEventDetails(context, event.id)!!
            assertEquals("", detail.event.summary)
            assertEquals(
                "人工纠正：齿轮编号709",
                detail.members.single { it.record.id == records.first().id }.record.content
            )
            assertTrue(detail.members.all { it.coveredRevision == null })
        }
    }

    @Test
    fun twoCoordinators_shareRoomLeaseAndNeverDuplicateRequest() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val ai = RecordingMemoryEventAi()
            ai.answer = { entered.complete(Unit); release.await(); memoryEventResponse(it) }
            val first = async { f.eventCoordinator(ai).processAutomatic(context) }
            entered.await()
            val competing = f.eventCoordinator(ai).processAutomatic(context)
            assertTrue(competing.failureCode in setOf("NETWORK_UNCERTAIN", "LEASE_UNAVAILABLE"))
            release.complete(Unit)
            assertEquals(1, first.await().committedEvents)
            assertEquals(1, ai.snapshots.size)
            assertEquals(1L, f.archive.getEventDetails(context, event.id)!!.event.summaryRevision)
        }
    }

    @Test
    fun completedBatchRestart_continuesFromSavedRawCoverage() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            ai.answer = { f.consent = false; memoryEventResponse(it) }
            val first =
                f.eventCoordinator(ai, policy = MemoryEventSummaryPolicy(batchContentChars = 900))
                    .processAutomatic(context)
            assertEquals("MEMORY_DISABLED", first.failureCode)
            val job =
                f.archive.listRecoverableJobs(context.spaceId).single { it.eventId == event.id }
            val artifact = MemoryEventSummaryBatches.readArtifact(job.artifact)
            assertEquals(1, artifact.completions.size)
            assertNull(artifact.pendingBatch)
            f.reopen()
            f.consent = true
            ai.answer = { memoryEventResponse(it) }
            assertEquals(1, f.eventCoordinator(ai).resumePlanned(context).committedEvents)
            assertEquals(4, ai.snapshots.size)
            assertEquals(
                records.map { it.id }.toSet(),
                ai.snapshots.flatMap { it.members }.map { it.record.id }.toSet()
            )
            assertEquals(
                4,
                f.archive.getEventDetails(
                    context,
                    event.id
                )!!.members.count { it.coveredRevision == 1L })
        }
    }

    @Test
    fun failedIncremental_keepsPreviousReadySummaryAndNewMembersLive() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            val coordinator = f.eventCoordinator(ai)
            coordinator.processAutomatic(context)
            val before = f.archive.getEventDetails(context, event.id)!!
            val latest = (1..4).map { f.remember(context, "摘要之后第$it 条新进展") }
            latest.forEach {
                f.archive.linkEventMembers(
                    context,
                    version(it),
                    version(records.first())
                )
            }
            ai.answer = { memoryEventResponse(it, status = PromptCompletionStatus.UNKNOWN) }
            assertEquals("INVALID_RESPONSE", coordinator.processAutomatic(context).failureCode)
            val after = f.archive.getEventDetails(context, event.id)!!
            assertEquals(before.event.summary, after.event.summary)
            assertEquals(before.event.summaryRevision, after.event.summaryRevision)
            assertEquals(MemoryEventSummaryStatus.READY, after.event.summaryStatus)
            assertTrue(after.members.filter { it.record.id in latest.map { record -> record.id } }
                .all { it.coveredRevision == null && it.status == MemoryEventMemberStatus.LIVE })
            assertEquals(
                4,
                f.archive.getRecallSnapshot(context)!!.events.single().supportingRecords.size
            )
        }
    }

    @Test
    fun manualCorrection_fullRebuildUsesCurrentRevisionAndManualSource() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            val coordinator = f.eventCoordinator(ai)
            coordinator.processAutomatic(context)
            f.archive.editRecord(
                context,
                records[1].id,
                records[1].revision,
                "纠正：实际使用的是709号齿轮"
            )
            val changed = f.archive.getEventDetails(context, event.id)!!
            ai.answer = { snapshot ->
                val member = snapshot.members.single { it.record.id == records[1].id }
                assertEquals(2L, member.record.revision)
                assertEquals(MemorySourceKind.MANUAL, member.sources.single().kind)
                assertTrue(member.sources.single().content.contains("709"))
                memoryEventResponse(snapshot, summary = "使用709号齿轮完成修复", state = "已归还")
            }
            assertEquals(
                1,
                coordinator.rebuild(context, event.id, changed.event.generation).committedEvents
            )
            val rebuilt = f.archive.getEventDetails(context, event.id)!!
            assertTrue(rebuilt.event.summary.contains("709"))
            assertEquals(
                2L,
                rebuilt.members.single { it.record.id == records[1].id }.coveredRevision
            )
            assertEquals(2, f.archive.getRecordDetails(context, records[1].id)!!.revisions.size)
        }
    }

    @Test
    fun uncertainEvent_doesNotHideAnotherSavedEventsExpiringLease() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val first = f.createEvent(context).first
            val second = f.createEvent(
                context,
                listOf("另一事件起因", "另一事件进展", "另一事件转折", "另一事件结果"),
                "另一事件"
            ).first
            val uncertain =
                (f.archive.planEventSummary(context, first.id) as MemoryEventPlanResult.Planned).job
            f.archive.claimJob(uncertain.id, "uncertain")
            f.archive.beginEventSummaryBatch(uncertain.id, "uncertain", 0)
            f.archive.releaseJob(
                uncertain.id,
                "uncertain",
                MemoryJobStatus.PAUSED,
                "NETWORK_UNCERTAIN"
            )
            f.now++
            val saved = (f.archive.planEventSummary(
                context,
                second.id
            ) as MemoryEventPlanResult.Planned).job
            f.archive.claimJob(saved.id, "previous-process", 1000L)
            f.archive.beginEventSummaryBatch(saved.id, "previous-process", 0)
            f.archive.saveEventSummaryBatch(
                saved.id, "previous-process", 0,
                memoryEventResponse(MemoryEventSummaryBatches.readWork(saved.requestJson).snapshot)
            )
            val ai = RecordingMemoryEventAi()
            var waits = 0
            val result =
                f.eventCoordinator(ai, waitForLease = { millis -> waits++; f.now += millis })
                    .resumePlannedOnStartup(context)
            assertEquals("NETWORK_UNCERTAIN", result.failureCode)
            assertEquals(1, result.committedEvents)
            assertTrue(waits > 0)
            assertTrue(ai.snapshots.isEmpty())
            assertEquals(
                MemoryEventSummaryStatus.EMPTY,
                f.archive.getEventDetails(context, first.id)!!.event.summaryStatus
            )
            assertEquals(
                MemoryEventSummaryStatus.READY,
                f.archive.getEventDetails(context, second.id)!!.event.summaryStatus
            )
            assertEquals(
                0,
                MemoryEventSummaryBatches.readArtifact(f.archive.getJob(uncertain.id)!!.artifact).pendingBatch
            )
        }
    }

    private fun version(record: MemoryRecord) = MemoryRecordVersion(record.id, record.revision)
}
