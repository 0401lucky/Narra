package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
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
class MemoryKnowledgeCoordinatorTest {
    @Test
    fun consentMemoryEngineAndRuntimeGates_preventConsumption() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.archive.keepCompatibilityEngine()
            val context = f.contexts().single()
            var calls = 0
            val archive = object : MemoryArchiveRepository by f.archive {
                override suspend fun consumeCommittedKnowledge(context: MemoryRuntimeContext): MemoryKnowledgeProgress {
                    calls++
                    return MemoryKnowledgeProgress(consumedJobs = 1)
                }
            }
            val coordinator = MemoryKnowledgeCoordinator(archive, { f.settings }, { f.consent })
            assertEquals("MEMORY_DISABLED", coordinator.resume(context).failureCode)
            f.enable()
            f.consent = false
            assertEquals("MEMORY_DISABLED", coordinator.resume(context).failureCode)
            f.consent = true
            f.settings =
                f.settings.copy(assistants = f.settings.assistants.map { it.copy(memoryEnabled = false) })
            assertEquals("MEMORY_DISABLED", coordinator.resume(context).failureCode)
            f.settings = f.settings.copy(
                assistants = listOf(
                    Assistant(
                        "a",
                        name = "甲",
                        memoryEnabled = true
                    )
                )
            )
            assertEquals(
                "MEMORY_DISABLED",
                coordinator.resume(context.copy(conversationId = "")).failureCode
            )
            assertEquals(0, calls)
            assertEquals(1, coordinator.resume(context).consumedJobs)
            assertEquals(1, calls)
        }
    }

    @Test
    fun cancellationAndDatabaseErrors_areNotReportedAsSuccess() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            listOf(
                CancellationException("synthetic cancellation"),
                IllegalStateException("synthetic database failure")
            ).forEach { failure ->
                val archive = object : MemoryArchiveRepository by f.archive {
                    override suspend fun consumeCommittedKnowledge(context: MemoryRuntimeContext): MemoryKnowledgeProgress =
                        throw failure
                }
                val thrown = runCatching {
                    MemoryKnowledgeCoordinator(
                        archive,
                        { f.settings },
                        { f.consent }).resume(context)
                }.exceptionOrNull()
                assertSame(failure, thrown)
            }
        }
    }

    @Test
    fun actualExtraction_withoutEpisodesFreezesKnowledgeTargetsAndCommits() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            f.ai.answer =
                { knowledgeResponse(residents = listOf(residentCandidate("星河路128号"))) }
            f.add(context, "first-declaration", "我住在星河路128号")
            val first = f.coordinator().processAutomatic(context.conversationId, "a")
            assertEquals(1, first.committedJobs)
            assertEquals(0, first.createdRecords)
            assertTrue(f.archive.listRecords(context).isEmpty())
            val resident = f.archive.listResidentEntries(context).single()
            f.ai.answer = { snapshot ->
                val target = requireNotNull(snapshot.knowledgeTargets).residents.single()
                assertEquals(resident.id, target.id)
                assertEquals(1L, target.revision)
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "星河路182号",
                            operation = "replace",
                            targetRef = "R0",
                            expectedRevision = 1
                        )
                    )
                )
            }
            f.add(context, "correction", "更正地址，现在是星河路182号")
            val second = f.coordinator().processAutomatic(context.conversationId, "a")
            assertEquals(1, second.committedJobs)
            assertEquals("星河路182号", f.archive.listResidentEntries(context).single().content)
            assertEquals(2, f.ai.snapshots.size)
            assertEquals(
                0,
                MemoryKnowledgeCoordinator(
                    f.archive,
                    { f.settings },
                    { f.consent }).resume(context).consumedJobs
            )
            assertEquals(2, f.ai.snapshots.size)
        }
    }

    @Test
    fun startupResume_reusesSavedC2ActionsWithoutModel() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("已保存的长期背景")),
                    states = listOf(
                        stateUpdate(
                            MemoryStateKey.TASK,
                            "已保存的任务",
                            expectedRevision = null
                        )
                    ),
                )
            }
            f.commitBeforeKnowledge(work)
            val coordinator = MemoryKnowledgeCoordinator(f.archive, { f.settings }, { f.consent })
            assertEquals(MemoryKnowledgeProgress(1, 1, 1), coordinator.resume(context))
            assertEquals(0, coordinator.resume(context).consumedJobs)
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun persistentPause_keepsOldKnowledgeArtifactUntilExplicitResume() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val context = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", context.spaceId))
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(residents = listOf(residentCandidate("暂停中的旧产物")))
            }
            f.commitBeforeKnowledge(work)
            f.archive.setProcessingPaused(management, 0, true)
            f.reopen()
            val coordinator = MemoryKnowledgeCoordinator(f.archive, { f.settings }, { f.consent })
            assertEquals("USER_PAUSED", coordinator.resume(context).failureCode)
            assertNull(f.database.memoryKnowledgeDao().getConsumption(work.jobId))
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            f.archive.setProcessingPaused(management, 1, false)
            f.archive.setProcessingPaused(management, 0, true, work.jobId)
            assertEquals("USER_PAUSED", coordinator.resume(context).failureCode)
            f.archive.setProcessingPaused(management, 1, false, work.jobId)
            assertEquals(1, coordinator.resume(context).residentWrites)
            assertEquals("暂停中的旧产物", f.archive.listResidentEntries(context).single().content)
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }
}
