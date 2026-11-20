package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryKnowledgeRepositoryTest {
    @Test
    fun onlyKnowledge_hasOwnSourceHistoryAndIdempotentCommit() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            assertEquals(MEMORY_UNKNOWN_PERSONA, context.personaKey)
            val work = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate(NEW_ADDRESS)),
                    states = listOf(stateUpdate(MemoryStateKey.TASK, "检修编号706的罗盘")),
                )
            }
            val result = f.commitKnowledge(work) as MemoryWriteResult.Committed
            assertTrue(result.recordIds.isEmpty())
            assertTrue(f.archive.listRecords(context).isEmpty())
            assertTrue(f.archive.listPendingIngress(context).isEmpty())
            val resident = f.archive.listResidentEntries(context).single()
            val state = f.archive.listCurrentStates(context).single()
            val details = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals(
                listOf(work.snapshot.sources.single().ref),
                details.revisions.single().sourceRefs
            )
            assertEquals(work.message.content, details.sources.single().content)
            assertEquals(1L, resident.revision)
            assertTrue(details.revisions.single().supportingRecords.isEmpty())
            assertEquals(1L, state.revision)
            assertTrue((f.commitKnowledge(work) as MemoryWriteResult.Committed).alreadyCommitted)
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
            assertEquals(details, f.archive.getResidentDetails(context, resident.id))
            assertEquals(
                1,
                f.database.memoryKnowledgeDao().getConsumption(work.jobId)?.residentWrites
            )
            assertEquals(1, f.database.memoryKnowledgeDao().getConsumption(work.jobId)?.stateWrites)
            val recall = requireNotNull(
                MemoryRecallEngine(f.archive, now = { f.now }).recall(
                    context, "你好", emptyList(), 0, 2000, false,
                )
            )
            assertEquals(
                setOf(MemoryMaterialKind.RESIDENT, MemoryMaterialKind.STATE),
                recall.materials.map { it.ref.kind }.toSet()
            )
        }
    }

    @Test
    fun addressCorrection_replacesSameIdAndPreservesVersions() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val first = f.prepareKnowledge(context, OLD_ADDRESS) {
                knowledgeResponse(
                    residents = listOf(residentCandidate(OLD_ADDRESS)),
                    memories = listOf(knowledgeEpisode(OLD_ADDRESS)),
                )
            }
            val recordId =
                (f.commitKnowledge(first) as MemoryWriteResult.Committed).recordIds.single()
            val resident = f.archive.listResidentEntries(context).single()
            val second = f.prepareKnowledge(
                context, NEW_ADDRESS,
                targets = listOf(MemoryExtractionTarget(recordId, 1, OLD_ADDRESS))
            ) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            NEW_ADDRESS,
                            operation = "replace",
                            targetRef = "R0",
                            expectedRevision = 1
                        )
                    ),
                    corrections = listOf(
                        mapOf(
                            "target_ref" to "O0", "expected_revision" to 1,
                            "replacement" to NEW_ADDRESS, "source_refs" to listOf("M0")
                        )
                    ),
                )
            }
            assertTrue(f.commitKnowledge(second) is MemoryWriteResult.Committed)
            val details = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals(2L, details.entry.revision)
            assertEquals(NEW_ADDRESS, details.entry.content)
            assertEquals(listOf(NEW_ADDRESS, OLD_ADDRESS), details.revisions.map { it.content })
            val recalled = requireNotNull(f.archive.getRecallSnapshot(context)).residents.single()
            assertEquals(2L, recalled.revision)
            assertEquals(listOf(MemoryRecordVersion(recordId, 2)), recalled.supportingRecords)
            assertEquals(listOf(second.snapshot.sources.single().ref), recalled.sourceRefs)
            assertFalse(recalled.content.contains("128"))
        }
    }

    @Test
    fun manualEdits_winAgainstFrozenResidentAndStateActions() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate(OLD_ADDRESS)),
                    states = listOf(stateUpdate(MemoryStateKey.TASK, "修复罗盘")),
                )
            })
            val resident = f.archive.listResidentEntries(context).single()
            val state = f.archive.listCurrentStates(context).single()
            val delayed = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            NEW_ADDRESS,
                            operation = "replace",
                            targetRef = "R0",
                            expectedRevision = 1
                        )
                    ),
                    states = listOf(stateUpdate(MemoryStateKey.TASK, "旧任务已结束", "end", 1)),
                    memories = listOf(knowledgeEpisode("独立有效的新经历")),
                )
            }
            assertTrue(
                f.archive.editResidentEntry(
                    context,
                    resident.id,
                    1,
                    "用户确认：北岸路909号",
                    false
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertTrue(
                f.archive.setCurrentState(
                    context,
                    MemoryStateKey.TASK,
                    state.revision,
                    "用户改为校准新罗盘"
                ) is MemoryKnowledgeWriteResult.Written
            )
            val result = f.commitKnowledge(delayed) as MemoryWriteResult.Committed
            assertEquals(1, result.recordIds.size)
            assertEquals(
                "用户确认：北岸路909号",
                f.archive.listResidentEntries(context).single().content
            )
            assertEquals(
                "用户改为校准新罗盘",
                f.archive.listCurrentStates(context).single().content
            )
            val consumption =
                requireNotNull(f.database.memoryKnowledgeDao().getConsumption(delayed.jobId))
            assertEquals(0, consumption.residentWrites)
            assertEquals(0, consumption.stateWrites)
            assertEquals(2, consumption.skippedActions)
            assertTrue(consumption.reasonCodesJson.contains("MANUAL_PROTECTED"))
            assertTrue(consumption.reasonCodesJson.contains("RECORD_CHANGED"))
        }
    }

    @Test
    fun missingActions_doNotClearAnyCategoryOrState() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = MemoryResidentCategory.entries.map {
                        residentCandidate(
                            "保留${it.name}",
                            it
                        )
                    },
                    states = MemoryStateKey.entries.map { stateUpdate(it, "保留${it.name}") },
                )
            })
            val residents = f.archive.listResidentEntries(context)
            val states = f.archive.listCurrentStates(context)
            f.commitKnowledge(f.prepareKnowledge(context) { knowledgeResponse() })
            assertEquals(residents, f.archive.listResidentEntries(context))
            assertEquals(states, f.archive.listCurrentStates(context))
        }
    }

    @Test
    fun fourCategories_obeyCapacityAndTokenBudgetWithoutDeletingArchive() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val defaults = MemoryResidentLimits()
            MemoryResidentCategory.entries.forEach { category ->
                repeat(defaults.capacity(category) + 2) { index ->
                    f.archive.addResidentEntry(
                        context,
                        category,
                        "${category.name}：认知$index，" + "背景保留。".repeat(60),
                        false
                    )
                }
            }
            val all = f.archive.listResidentEntries(context)
            assertEquals(48, all.size)
            val snapshot = requireNotNull(f.archive.getRecallSnapshot(context))
            assertEquals(40, snapshot.residents.size)
            val categories = all.associate { it.id to it.category }
            assertEquals(
                mapOf(
                MemoryResidentCategory.USER to 12, MemoryResidentCategory.CHARACTER to 10,
                MemoryResidentCategory.RELATIONSHIP to 10, MemoryResidentCategory.SHARED to 8
            ),
                snapshot.residents.groupingBy { categories.getValue(it.id) }.eachCount()
            )
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    context,
                    "你好",
                    emptyList(),
                    0,
                    600,
                    false
                )
            )
            assertTrue(plan.estimatedTokens <= 600)
            assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
            assertEquals(all, f.archive.listResidentEntries(context))
            val limited = RoomMemoryArchiveRepository(
                f.database, { f.now }, { true },
                residentLimits = { MemoryResidentLimits(1, 2, 0, 3) })
            assertEquals(6, requireNotNull(limited.getRecallSnapshot(context)).residents.size)
        }
    }

    @Test
    fun temporaryCandidates_stayOutOfResidentAndSubjectiveLabelSurvives() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, semanticKind = MemorySemanticKind.THOUGHT) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate("今天紧张", durability = "temporary"),
                        residentCandidate("在北站"),
                        residentCandidate("我觉得芮珩信任我", MemoryResidentCategory.RELATIONSHIP),
                    ),
                    states = listOf(stateUpdate(MemoryStateKey.LOCATION, "在北站")),
                )
            }
            f.commitKnowledge(work)
            val entry = f.archive.listResidentEntries(context).single()
            assertEquals("我觉得芮珩信任我", entry.content)
            assertTrue(entry.subjective)
            assertTrue(
                requireNotNull(f.archive.getRecallSnapshot(context)).residents.single().label.contains(
                    "主观看法"
                )
            )
            assertEquals(
                2,
                f.database.memoryKnowledgeDao().getConsumption(work.jobId)?.skippedActions
            )
        }
    }

    @Test
    fun partialWriteFailure_rollsBackKnowledgeRecordAndCursor() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate(NEW_ADDRESS)),
                    states = listOf(stateUpdate(MemoryStateKey.TASK, "故障哨兵")),
                    memories = listOf(knowledgeEpisode("原子经历")),
                )
            }
            f.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_knowledge BEFORE INSERT ON memory_knowledge_revisions WHEN NEW.ownerKind = 'STATE' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
            )
            assertTrue(runCatching { f.commitKnowledge(work) }.isFailure)
            assertTrue(f.archive.listRecords(context).isEmpty())
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertTrue(f.archive.listCurrentStates(context).isEmpty())
            assertNull(f.database.memoryKnowledgeDao().getConsumption(work.jobId))
            assertEquals(0L, f.archive.getCursor(context.spaceId)?.committedSequence)
            assertEquals(MemoryJobStatus.EXTRACTED, f.archive.getJob(work.jobId)?.status)
            assertTrue(requireNotNull(f.archive.getJob(work.jobId)).artifact.isNotBlank())
            f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_knowledge")
            assertTrue(f.commitKnowledge(work) is MemoryWriteResult.Committed)
            assertEquals(1, f.archive.listResidentEntries(context).size)
            assertEquals(1, f.archive.listCurrentStates(context).size)
            assertEquals(1, f.archive.listRecords(context).size)
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun independentPersonaAndCharacter_cannotReadOrUpdateDailyKnowledge() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val daily = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(daily) {
                knowledgeResponse(
                    residents = listOf(residentCandidate(NEW_ADDRESS)),
                    states = listOf(stateUpdate(MemoryStateKey.LOCATION, "北站")),
                )
            })
            val resident = f.archive.listResidentEntries(daily).single()
            val state = f.archive.listCurrentStates(daily).single()
            val isolated = listOf(
                f.contexts(purpose = MemoryPurpose.INDEPENDENT).single(),
                f.contexts(persona = "剧情里的公主").single(),
                f.contexts(characters = listOf("b")).single()
            )
            isolated.forEach { context ->
                assertTrue(f.archive.listResidentEntries(context).isEmpty())
                assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
                assertNull(f.archive.getResidentDetails(context, resident.id))
                assertNull(
                    f.archive.getCurrentStateDetails(
                        context.copy(conversationId = ""),
                        state.id
                    )
                )
                assertTrue(
                    f.archive.editResidentEntry(
                        context,
                        resident.id,
                        1,
                        "越权",
                        false
                    ) is MemoryKnowledgeWriteResult.Conflict
                )
                assertTrue(
                    f.archive.endCurrentState(
                        context,
                        state.id,
                        1
                    ) is MemoryKnowledgeWriteResult.Conflict
                )
            }
            val nextDaily = f.contexts().single()
            assertEquals(daily.spaceId, nextDaily.spaceId)
            assertEquals(resident, f.archive.listResidentEntries(nextDaily).single())
            assertEquals(NEW_ADDRESS, f.archive.listResidentEntries(daily).single().content)
        }
    }

    @Test
    fun actionForgery_cannotBypassSavedResponseOrSourceScope() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(NEW_ADDRESS)
                    )
                )
            }
            val forged =
                work.batch.copy(residents = work.batch.residents.map { it.copy(content = "未在响应中的内容") })
            assertEquals(
                MemoryWriteResult.Conflict(MemoryConflict.INVALID_INPUT),
                f.archive.commitJob(work.jobId, KNOWLEDGE_TEST_OWNER, forged)
            )
            assertNull(f.database.memoryKnowledgeDao().getConsumption(work.jobId))
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertTrue(f.commitKnowledge(work) is MemoryWriteResult.Committed)
        }
    }

    private companion object {
        const val OLD_ADDRESS = "芮珩住在星河路128号，编号607。"
        const val NEW_ADDRESS = "芮珩搬到星河路182号，编号706。"
    }
}
