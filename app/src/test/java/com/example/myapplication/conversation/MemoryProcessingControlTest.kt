package com.example.myapplication.conversation

import android.app.Application
import androidx.room.withTransaction
import com.example.myapplication.data.local.memory.MemoryJobSourceEntity
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryProcessingControlTest {
    @Test
    fun spacePause_persistsAndBlocksNewRequests() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val runtime = f.contexts().single()
            f.add(runtime, "pending", "需要记住叶栖706")
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertTrue(
                f.archive.setProcessingPaused(
                    management,
                    0,
                    true
                ) is MemoryProcessingControlResult.Updated
            )
            assertEquals(runtime.generation, f.archive.getSpace(runtime.spaceId)?.generation)
            val source = f.archive.listPendingIngress(runtime).single().source
            assertTrue(runCatching {
                f.archive.planJob(
                    runtime,
                    "paused-plan",
                    listOf(source.fullRef())
                )
            }.isFailure)
            assertEquals(
                "USER_PAUSED",
                f.coordinator().processAutomatic(runtime.conversationId, "a").failureCode
            )
            assertTrue(f.ai.snapshots.isEmpty())
            f.reopen()
            assertTrue(requireNotNull(f.archive.getProcessingControl(management)).userPaused)
            assertTrue(
                f.archive.setProcessingPaused(
                    management,
                    0,
                    false
                ) is MemoryProcessingControlResult.Conflict
            )
            assertTrue(
                f.archive.setProcessingPaused(
                    management,
                    1,
                    false
                ) is MemoryProcessingControlResult.Updated
            )
            assertTrue(f.coordinator().processAutomatic(runtime.conversationId, "a").updated)
            assertEquals(1, f.ai.snapshots.size)
        }
    }

    @Test
    fun pauseAfterCompletion_savesThenResumesSameArtifact() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            f.add(runtime, "response-pause", "已经请求的完整合成来源")
            f.ai.answer = { snapshot ->
                f.archive.setProcessingPaused(management, 0, true)
                memoryResponse(snapshot, "已经收到的706号经历")
            }
            val progress = f.coordinator().processAutomatic(runtime.conversationId, "a")
            assertEquals("USER_PAUSED", progress.failureCode)
            val saved = f.archive.listJobHistory(management).single()
            assertTrue(saved.artifact.isNotBlank())
            assertEquals(MemoryRequestState.STARTED, saved.requestState)
            assertTrue(f.archive.listRecords(management).isEmpty())
            f.reopen()
            assertEquals("USER_PAUSED", f.coordinator().resumePlanned(runtime).failureCode)
            f.archive.setProcessingPaused(management, 1, false)
            val result = f.coordinator().resumeJob(management, saved.id)
            assertEquals(1, result.committedJobs)
            assertEquals(1, f.ai.snapshots.size)
            assertEquals(MemoryJobStatus.COMMITTED, f.archive.getJob(saved.id)?.status)
            assertEquals("已经收到的706号经历", f.archive.listRecords(management).single().content)
        }
    }

    @Test
    fun cancelledRequest_doesNotResendOnExplicitContinue() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", context.spaceId))
            f.add(context, "uncertain", "收费状态不确定")
            f.ai.answer = { throw CancellationException("synthetic cancel") }
            try {
                f.coordinator().processAutomatic(context.conversationId, "a")
                fail("取消应继续传播")
            } catch (_: CancellationException) {
                // 取消只保留费用证据。
            }
            val job = f.archive.listJobHistory(management).single()
            assertEquals(MemoryRequestState.STARTED, job.requestState)
            assertEquals(
                "NETWORK_UNCERTAIN",
                f.coordinator().resumeJob(management, job.id).failureCode
            )
            assertEquals(1, f.ai.snapshots.size)
        }
    }

    @Test
    fun selectionOverlap_readsPastThousandJobsAndNeedsReview() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", context.spaceId))
            f.add(context, "old-target", "旧选区")
            f.add(context, "other", "其它来源")
            f.add(context, "fresh", "新的选区")
            val sources =
                f.archive.listPendingIngress(context).associate { it.source.messageId to it.source }
            val old = f.archive.planSelectedJob(
                management,
                "old",
                listOf(sources.getValue("old-target").fullRef()),
                requestJson = "{}"
            )
            val dao = f.database.memoryArchiveDao()
            val entity = requireNotNull(dao.getJob(old.id))
            f.database.withTransaction {
                dao.upsertJob(
                    entity.copy(
                        status = "COMMITTED",
                        requestState = "STARTED",
                        artifact = ""
                    )
                )
                repeat(1005) { index ->
                    val id = "later-$index"
                    dao.upsertJob(
                        entity.copy(
                            id = id,
                            idempotencyKey = id,
                            status = "COMMITTED",
                            createdAt = f.now + index + 1
                        )
                    )
                    val ref = sources.getValue("other").fullRef()
                    dao.insertJobSources(
                        listOf(
                            MemoryJobSourceEntity(
                                id,
                                ref.sourceId,
                                ref.sourceRevision,
                                ref.start,
                                ref.end
                            )
                        )
                    )
                }
            }
            val selection =
                MemoryMessageSelection(context.conversationId, listOf("old-target", "fresh"))
            val inspection = f.archive.inspectSelection(management, selection)
            assertEquals(listOf(old.id), inspection.overlappingJobs.map { it.jobId })
            assertFalse(inspection.hasUncertainRequest)
            assertEquals(
                "REQUIRES_SELECTION_REVIEW",
                f.coordinator().processSelection(management, selection).failureCode
            )
            assertTrue(f.ai.snapshots.isEmpty())
            assertTrue(
                f.coordinator()
                    .processSelection(management, selection, reprocessExisting = true).updated
            )
            assertEquals(1, f.ai.snapshots.size)
            assertEquals(0L, f.archive.getCursor(context.spaceId)?.committedSequence)
        }
    }

    @Test
    fun replannedTask_retainsItsUserPauseControl() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            f.add(runtime, "replan", "未请求的合法原料")
            val source = f.archive.listPendingIngress(runtime).single().source
            val original =
                f.archive.planJob(runtime, "original", listOf(source.fullRef()), requestJson = "{}")
            val next = requireNotNull(
                f.archive.replanUnrequestedJob(
                    original.id,
                    "replacement",
                    emptyMap(),
                    "{}"
                )
            )
            f.archive.setProcessingPaused(management, 0, true, original.id)
            assertTrue(f.archive.isProcessingPaused(runtime, next.id))
            assertNull(f.archive.claimJob(next.id, "worker"))
            val inherited = requireNotNull(f.archive.getProcessingControl(management, next.id))
            assertTrue(inherited.userPaused)
            f.archive.setProcessingPaused(management, inherited.version, false, next.id)
            assertNotNull(f.archive.claimJob(next.id, "worker"))
        }
    }

    @Test
    fun archivedPaidTask_isReviewOnlyAfterGenerationChanges() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val runtime = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            f.add(runtime, "archived-paid", "旧独立运行来源")
            val source = f.archive.listPendingIngress(runtime).single().source
            val job =
                f.archive.planJob(runtime, "paid", listOf(source.fullRef()), requestJson = "{}")
            f.archive.claimJob(job.id, "worker")
            f.archive.markRequestStarted(job.id, "worker")
            f.archive.saveExtraction(job.id, "worker", "saved-artifact")
            f.archive.archiveConversation(runtime.conversationId)
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertEquals(
                "REQUIRES_MANUAL_REVIEW",
                f.coordinator().resumeJob(management, job.id).failureCode
            )
            assertEquals("saved-artifact", f.archive.getJob(job.id)?.artifact)
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun eventPause_persistsBatchAndContinueDoesNotRebuild() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            val (event, _) = f.createEvent(runtime)
            val ai = RecordingMemoryEventAi()
            ai.answer = { snapshot ->
                f.archive.setProcessingPaused(management, 0, true)
                memoryEventResponse(snapshot)
            }
            val result = f.eventCoordinator(ai).rebuild(management, event.id, event.generation)
            assertEquals("USER_PAUSED", result.failureCode)
            val saved = f.archive.listJobHistory(management)
                .first { it.purpose == MemoryJobPurpose.EVENT_REBUILD }
            val generation =
                requireNotNull(f.archive.getEventDetails(management, event.id)).event.generation
            assertTrue(saved.artifact.isNotBlank())
            f.reopen()
            f.archive.setProcessingPaused(management, 1, false)
            assertEquals(
                1,
                f.eventCoordinator(ai).resumeEventJob(management, saved.id).committedEvents
            )
            assertEquals(1, ai.snapshots.size)
            assertEquals(
                generation,
                f.archive.getEventDetails(management, event.id)?.event?.generation
            )
        }
    }

    @Test
    fun characterRemoval_stopsInFlightOwnerWithoutErasingArchive() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            f.remember(runtime, "保留的旧经历")
            f.add(runtime, "late", "迟到来源")
            val source = f.archive.listPendingIngress(runtime).single().source
            val job = f.archive.planJob(runtime, "late-job", listOf(source.fullRef()))
            f.archive.claimJob(job.id, "worker")
            f.archive.markRequestStarted(job.id, "worker")
            f.settings =
                f.settings.copy(assistants = f.settings.assistants.filterNot { it.id == "a" })
            f.archive.closeCharacterSpaces("a")
            assertNull(f.archive.getRuntimeContext(runtime.conversationId, "a"))
            assertFalse(f.archive.saveExtraction(job.id, "worker", "late-artifact"))
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertEquals("保留的旧经历", f.archive.listRecords(management).single().content)
            assertEquals(MemoryJobStatus.INVALIDATED, f.archive.getJob(job.id)?.status)
        }
    }

    @Test
    fun selectedArchivedJob_resumesFrozenArtifactWithAutoDisabled() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val runtime =
                f.contexts(purpose = MemoryPurpose.INDEPENDENT, persona = "旧身份").single()
            f.add(runtime, "selected-old", "旧独立档案中用户明确选定的完整来源")
            f.archive.archiveConversation(runtime.conversationId)
            val management = requireNotNull(
                f.archive.getManagementContext(
                    "a",
                    runtime.spaceId,
                    runtime.personaKey
                )
            )
            f.settings = f.settings.copy(memoryAutoSummaryEvery = 0)
            f.ai.answer = { snapshot ->
                f.archive.setProcessingPaused(management, 0, true)
                memoryResponse(snapshot, "旧独立档案的经历")
            }
            assertEquals(
                "USER_PAUSED",
                f.coordinator().processSelection(
                    management,
                    MemoryMessageSelection(runtime.conversationId, listOf("selected-old"))
                ).failureCode
            )
            val job = f.archive.listJobHistory(management).single()
            assertEquals(MemoryJobPurpose.USER_SELECTION, job.purpose)
            f.reopen()
            f.archive.setProcessingPaused(management, 1, false)
            assertEquals(1, f.coordinator().resumeJob(management, job.id).committedJobs)
            assertEquals(1, f.ai.snapshots.size)
            assertEquals(management, f.archive.getJob(job.id)?.context)
            assertNull(f.archive.getRuntimeContext(runtime.conversationId, "a"))
            assertEquals(0L, f.archive.getCursor(runtime.spaceId)?.committedSequence)
        }
    }

    @Test
    fun pausedMaintenance_keepsRelationsPendingAndRecallAvailable() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            val first = f.remember(runtime, "起因")
            val second = f.remember(runtime, "进展")
            val dao = f.database.memoryArchiveDao()
            dao.insertRelation(
                com.example.myapplication.data.local.memory.MemoryRelationIntentEntity(
                    "c6-pending", runtime.spaceId, "a", first.id, 1, second.id, 1,
                    "same_event", "合成事件", "PENDING", f.now,
                )
            )
            f.archive.setProcessingPaused(management, 0, true)
            assertEquals(0, f.archive.consumeEventRelations(runtime).applied)
            assertEquals(1, dao.listPendingRelations(runtime.spaceId).size)
            f.archive.addRecord(management, ManualMemoryRecordDraft("暂停时仍可手动维护"))
            f.archive.archiveRecallOverflow(runtime, 0)
            assertEquals(
                3,
                f.archive.listRecords(runtime).count { it.status == MemoryRecordStatus.ACTIVE })
            assertEquals(3, f.archive.getRecallSnapshot(runtime)?.records?.size)
            f.archive.setProcessingPaused(management, 1, false)
            val originatingJob = requireNotNull(dao.getRecord(first.id)?.jobId)
            f.archive.setProcessingPaused(management, 0, true, originatingJob)
            assertEquals(0, f.archive.consumeEventRelations(runtime).applied)
            assertEquals(1, dao.listPendingRelations(runtime.spaceId).size)
            f.archive.setProcessingPaused(management, 1, false, originatingJob)
            assertEquals(1, f.archive.consumeEventRelations(runtime).applied)
            f.archive.archiveRecallOverflow(runtime, 0)
            assertTrue(
                f.archive.listRecords(runtime).all { it.status == MemoryRecordStatus.ARCHIVED })
        }
    }
}
