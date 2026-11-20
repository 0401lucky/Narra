package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
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
class MemoryKnowledgeLifecycleTest {
    @Test
    fun stateKeys_endAndExpireIndependently() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    states = MemoryStateKey.entries.map { stateUpdate(it, "合成${it.label}") },
                )
            })
            val original = f.archive.listCurrentStates(context).associateBy { it.key }
            assertEquals(4, requireNotNull(f.archive.getRecallSnapshot(context)).states.size)
            assertEquals(4, original.values.map { it.id }.distinct().size)
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    states = listOf(
                        stateUpdate(MemoryStateKey.TASK, "罗盘修复已完成", "end", 1),
                    )
                )
            })
            assertEquals(3, requireNotNull(f.archive.getRecallSnapshot(context)).states.size)
            assertEquals(
                "ENDED",
                f.archive.listCurrentStates(context).single { it.key == MemoryStateKey.TASK }.status
            )
            f.now += MemoryStateKey.MOOD.defaultLifetimeMillis + 1
            assertEquals(
                setOf(MemoryStateKey.LOCATION, MemoryStateKey.APPOINTMENT),
                f.archive.listCurrentStates(context).filter { it.status == "ACTIVE" }.map { it.key }
                    .toSet()
            )
            assertEquals(
                "EXPIRED",
                f.archive.getKnowledgeTargets(context).states.single { it.key == MemoryStateKey.MOOD }.status
            )
            assertEquals(
                "",
                f.archive.getKnowledgeTargets(context).states.single { it.key == MemoryStateKey.MOOD }.content
            )
            f.now += MemoryStateKey.APPOINTMENT.defaultLifetimeMillis
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).states.isEmpty())
            assertEquals(4, f.archive.listCurrentStates(context).size)
            val task = f.archive.listCurrentStates(context).single { it.key == MemoryStateKey.TASK }
            assertEquals(2, f.archive.getCurrentStateDetails(context, task.id)?.revisions?.size)
        }
    }

    @Test
    fun reinforceThenRetire_preservesHistoryAndDoesNotDuplicateFacts() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate("喜欢栀子花")
                    )
                )
            })
            val resident = f.archive.listResidentEntries(context).single()
            f.commitKnowledge(f.prepareKnowledge(context, "再次确认喜欢栀子花") {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            resident.content,
                            operation = "reinforce",
                            targetRef = "R0",
                            expectedRevision = 1
                        ),
                    )
                )
            })
            val reinforced = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals(2L, reinforced.entry.revision)
            assertEquals(2, reinforced.revisions.first().sourceRefs.size)
            assertEquals(resident.content, reinforced.entry.content)
            f.commitKnowledge(f.prepareKnowledge(context, "这个偏好不再适用") {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "退役原因",
                            operation = "retire",
                            targetRef = "R0",
                            expectedRevision = 2
                        ),
                    )
                )
            })
            val retired = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals("ARCHIVED", retired.entry.status)
            assertEquals(resident.content, retired.entry.content)
            assertEquals(3, retired.revisions.size)
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
            assertEquals(1, f.archive.listResidentEntries(context).size)
        }
    }

    @Test
    fun originalMessageEdit_invalidatesKnowledgeWithoutAnyEpisode() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("星河路182号")),
                    states = listOf(stateUpdate(MemoryStateKey.LOCATION, "北站")),
                )
            }
            f.commitKnowledge(work)
            val resident = f.archive.listResidentEntries(context).single()
            f.store.upsertMessages(listOf(work.message.copy(content = "原声明已纠正，无法沿用旧来源")))
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).states.isEmpty())
            val details = requireNotNull(f.archive.getResidentDetails(context, resident.id))
            assertEquals("STALE", details.entry.status)
            assertTrue(details.entry.content.isBlank())
            assertTrue(details.revisions.all { it.content.isBlank() })
            assertEquals(MemorySourceStatus.CHANGED, details.sources.single().status)
            assertEquals("", f.archive.getJob(work.jobId)?.artifact)
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
        }
    }

    @Test
    fun manualSources_surviveOriginalMessageClear() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("旧地址")),
                    states = listOf(stateUpdate(MemoryStateKey.TASK, "旧任务")),
                )
            })
            val resident = f.archive.listResidentEntries(context).single()
            f.archive.editResidentEntry(context, resident.id, 1, "人工确认的新地址", false)
            f.archive.setCurrentState(context, MemoryStateKey.TASK, 1, "人工确认的新任务")
            f.store.clearMessages(context.conversationId)
            val fresh = requireNotNull(
                f.archive.getRuntimeContext(
                    context.conversationId,
                    context.characterId
                )
            )
            assertTrue(fresh.generation > context.generation)
            val snapshot = requireNotNull(f.archive.getRecallSnapshot(fresh))
            assertEquals("人工确认的新地址", snapshot.residents.single().content)
            assertEquals("人工确认的新任务", snapshot.states.single().content)
            val details = requireNotNull(f.archive.getResidentDetails(fresh, resident.id))
            assertEquals("", details.revisions.last().content)
            assertEquals(MemoryChangeOrigin.MANUAL, details.revisions.first().origin)
            val sourceIds = details.revisions.first().sourceRefs.map { it.sourceId }.toSet()
            assertTrue(details.sources.filter { it.id in sourceIds }
                .all { it.kind == MemorySourceKind.MANUAL && it.status == MemorySourceStatus.AVAILABLE })
        }
    }

    @Test
    fun sourceDeletionAfterEdit_scrubsAllOldKnowledgeHistory() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate("要删除的认知正文")
                    )
                )
            }
            f.commitKnowledge(work)
            f.store.upsertMessages(listOf(work.message.copy(content = "改写过的原文")))
            val conversation = requireNotNull(f.store.getConversation(context.conversationId))
            f.store.deleteMessageWithAssociations(
                conversation,
                context.conversationId,
                work.message.id
            )
            val details = requireNotNull(
                f.archive.getResidentDetails(
                    context,
                    f.archive.listResidentEntries(context).single().id
                )
            )
            assertTrue(details.revisions.all { it.content.isEmpty() })
            assertTrue(details.sources.all { it.content.isEmpty() && it.status == MemorySourceStatus.DELETED })
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
        }
    }

    @Test
    fun deletingKnowledge_blocksLateResultAndKeepsOriginalMessage() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val original = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("要删除的地址")),
                    states = listOf(stateUpdate(MemoryStateKey.MOOD, "要删除的心境")),
                )
            }
            f.commitKnowledge(original)
            val resident = f.archive.listResidentEntries(context).single()
            val state = f.archive.listCurrentStates(context).single()
            val delayed = f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "迟到的地址",
                            operation = "replace",
                            targetRef = "R0",
                            expectedRevision = 1
                        )
                    ),
                    states = listOf(
                        stateUpdate(
                            MemoryStateKey.MOOD,
                            "迟到的心境",
                            expectedRevision = 1
                        )
                    ),
                )
            }
            assertTrue(
                f.archive.deleteKnowledge(
                    context,
                    MemoryMaterialKind.RESIDENT,
                    resident.id,
                    1
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertTrue(
                f.archive.deleteKnowledge(
                    context,
                    MemoryMaterialKind.STATE,
                    state.id,
                    1
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertTrue(f.commitKnowledge(delayed) is MemoryWriteResult.Conflict)
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertTrue(f.archive.listCurrentStates(context).isEmpty())
            assertTrue(
                requireNotNull(
                    f.archive.getResidentDetails(
                        context,
                        resident.id
                    )
                ).revisions.isEmpty()
            )
            assertTrue(
                requireNotNull(
                    f.archive.getCurrentStateDetails(
                        context,
                        state.id
                    )
                ).revisions.isEmpty()
            )
            assertEquals(
                original.message.content,
                f.store.listMessages(context.conversationId)
                    .first { it.id == original.message.id }.content
            )
            assertEquals("", f.archive.getJob(delayed.jobId)?.artifact)
            assertEquals(MemoryRequestState.STARTED, f.archive.getJob(delayed.jobId)?.requestState)
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
        }
    }

    @Test
    fun archiveAndEndManualActions_keepVersionsAndCanRejectStaleEdits() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val added = f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.CHARACTER,
                "明确设定补充",
                false
            ) as MemoryKnowledgeWriteResult.Written
            assertTrue(
                f.archive.setResidentArchived(
                    context,
                    added.id,
                    1,
                    true
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
            assertTrue(
                f.archive.setResidentArchived(
                    context,
                    added.id,
                    1,
                    false
                ) is MemoryKnowledgeWriteResult.Conflict
            )
            assertTrue(
                f.archive.setResidentArchived(
                    context,
                    added.id,
                    2,
                    false
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertEquals(3, f.archive.getResidentDetails(context, added.id)?.revisions?.size)
            val state = f.archive.setCurrentState(
                context,
                MemoryStateKey.APPOINTMENT,
                0,
                "明晚一起观星"
            ) as MemoryKnowledgeWriteResult.Written
            assertTrue(
                f.archive.endCurrentState(
                    context,
                    state.id,
                    1
                ) is MemoryKnowledgeWriteResult.Written
            )
            assertEquals("ENDED", f.archive.listCurrentStates(context).single().status)
            assertEquals(2, f.archive.getCurrentStateDetails(context, state.id)?.revisions?.size)
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).states.isEmpty())
            assertTrue(
                f.archive.setCurrentState(
                    context,
                    MemoryStateKey.APPOINTMENT,
                    1,
                    "错误的旧版"
                ) is MemoryKnowledgeWriteResult.Conflict
            )
        }
    }

    @Test
    fun deletedSpace_erasesAllKnowledgeWithoutTouchingAnotherSpace() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val first = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            val other = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            listOf(first, other).forEach { context ->
                f.archive.addResidentEntry(
                    context,
                    MemoryResidentCategory.SHARED,
                    "只属于${context.spaceId}",
                    false
                )
                f.archive.setCurrentState(context, MemoryStateKey.TASK, 0, "档案中的任务")
            }
            val before = f.archive.listResidentEntries(other)
            assertTrue(f.archive.deleteSpace(first) is MemoryWriteResult.Committed)
            assertNull(f.archive.getRecallSnapshot(first))
            assertTrue(
                f.database.memoryKnowledgeDao().listSpaceRevisions(first.spaceId)
                    .all { it.content.isEmpty() })
            assertTrue(
                f.archive.addResidentEntry(
                    first,
                    MemoryResidentCategory.USER,
                    "迟到内容",
                    false
                ) is MemoryKnowledgeWriteResult.Conflict
            )
            assertEquals(before, f.archive.listResidentEntries(other))
            assertEquals(1, requireNotNull(f.archive.getRecallSnapshot(other)).states.size)
        }
    }

    @Test
    fun restoreOldCandidates_afterReopenConsumesOnceWithoutModel() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "旧产物里的栀子花偏好",
                            durability = null
                        ) - "operation"
                    ),
                    states = listOf(
                        stateUpdate(
                            MemoryStateKey.MOOD,
                            "旧产物中的平静",
                            expectedRevision = null
                        )
                    ),
                )
            }
            f.commitBeforeKnowledge(work)
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            f.reopen()
            val progress = f.archive.consumeCommittedKnowledge(context)
            assertEquals(MemoryKnowledgeProgress(1, 1, 1), progress)
            val details = requireNotNull(
                f.archive.getResidentDetails(
                    context,
                    f.archive.listResidentEntries(context).single().id
                )
            )
            assertEquals(
                listOf(work.snapshot.sources.single().ref),
                details.revisions.single().sourceRefs
            )
            f.reopen()
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
            assertEquals(details, f.archive.getResidentDetails(context, details.entry.id))
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun restoreOldState_cannotRegressNewerStateOrRenewOldExpiry() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val old = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    states = listOf(
                        stateUpdate(MemoryStateKey.LOCATION, "昨天在南站", expectedRevision = null),
                        stateUpdate(MemoryStateKey.MOOD, "昨天紧张", expectedRevision = null)
                    ),
                )
            }
            f.commitBeforeKnowledge(old)
            f.now += 2 * MemoryStateKey.LOCATION.defaultLifetimeMillis
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    states = listOf(
                        stateUpdate(MemoryStateKey.LOCATION, "今天在北站"),
                    )
                )
            })
            val progress = f.archive.consumeCommittedKnowledge(context)
            assertEquals(1, progress.skippedActions)
            val states = f.archive.listCurrentStates(context)
            assertEquals("今天在北站", states.single { it.key == MemoryStateKey.LOCATION }.content)
            val mood = states.single { it.key == MemoryStateKey.MOOD }
            assertEquals("EXPIRED", mood.status)
            assertEquals(
                old.message.createdAt + MemoryStateKey.MOOD.defaultLifetimeMillis,
                mood.expiresAt
            )
            assertEquals(1, requireNotNull(f.archive.getRecallSnapshot(context)).states.size)
        }
    }

    @Test
    fun restoreFailure_keepsUnconsumedWorkForFreeRetry() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("持久产物")),
                )
            }
            f.commitBeforeKnowledge(work)
            f.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_consumption BEFORE INSERT ON memory_knowledge_consumptions BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
            )
            assertTrue(runCatching { f.archive.consumeCommittedKnowledge(context) }.isFailure)
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertNull(f.database.memoryKnowledgeDao().getConsumption(work.jobId))
            f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_consumption")
            assertEquals(1, f.archive.consumeCommittedKnowledge(context).residentWrites)
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun invalidSavedArtifact_isRecordedWithoutInventingKnowledge() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(
                context,
                freezeTargets = false
            ) { knowledgeResponse(residents = listOf(residentCandidate("未可信候选"))) }
            f.commitBeforeKnowledge(work)
            val job = requireNotNull(f.database.memoryArchiveDao().getJob(work.jobId))
            f.database.memoryArchiveDao().upsertJob(job.copy(artifact = "{broken"))
            assertEquals(
                MemoryKnowledgeProgress(1, skippedActions = 1),
                f.archive.consumeCommittedKnowledge(context)
            )
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertEquals(
                "[\"INVALID_ARTIFACT\"]",
                f.database.memoryKnowledgeDao().getConsumption(work.jobId)?.reasonCodesJson
            )
            assertEquals(0, f.archive.consumeCommittedKnowledge(context).consumedJobs)
        }
    }

    @Test
    fun changedOutputRecord_blocksOldCandidateRecovery() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("旧推导")),
                    memories = listOf(knowledgeEpisode("旧经历")),
                )
            }
            f.commitBeforeKnowledge(work)
            val record = f.archive.listRecords(context).single()
            f.archive.editRecord(context, record.id, record.revision, "用户修订的新经历")
            assertEquals(
                MemoryKnowledgeProgress(1, skippedActions = 1),
                f.archive.consumeCommittedKnowledge(context)
            )
            assertTrue(f.archive.listResidentEntries(context).isEmpty())
            assertEquals(
                "[\"RECORD_CHANGED\"]",
                f.database.memoryKnowledgeDao().getConsumption(work.jobId)?.reasonCodesJson
            )
        }
    }

    @Test
    fun restoredOutput_usesStoredLocalIdMapping() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val work = f.prepareKnowledge(context, freezeTargets = false) {
                knowledgeResponse(
                    residents = listOf(residentCandidate("可恢复的结论")),
                    memories = listOf(knowledgeEpisode("对应经历")),
                )
            }
            f.commitBeforeKnowledge(work)
            val dao = f.database.memoryArchiveDao()
            val original = dao.listJobRecords(work.jobId).single()
            val refs = dao.listRecordSources(original.id, original.revision)
            val history = dao.listRevisions(original.id).single()
            // 模拟已有档案中的真实输出 ID；关联以已持久 job/localItemId 为准。
            dao.deleteRevisions(original.id)
            f.database.openHelper.writableDatabase.execSQL(
                "DELETE FROM memory_records WHERE id = ?",
                arrayOf(original.id)
            )
            val restoredId = "restored-output-record"
            dao.upsertRecord(original.copy(id = restoredId))
            dao.insertRevision(history.copy(recordId = restoredId))
            refs.forEach { dao.insertRecordSource(it.copy(recordId = restoredId)) }
            assertEquals(mapOf("N0" to restoredId), f.archive.getJobRecordIds(work.jobId))
            assertEquals(1, f.archive.consumeCommittedKnowledge(context).residentWrites)
            assertEquals(
                listOf(MemoryRecordVersion(restoredId, 1)),
                requireNotNull(f.archive.getRecallSnapshot(context)).residents.single().supportingRecords
            )
        }
    }

    @Test
    fun editingLegacyKnowledge_preservesUnknownProvenanceInHistory() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val record = f.remember(context, "旧经历仅作出处线索")
            val dao = f.database.memoryArchiveDao()
            val ref =
                requireNotNull(f.archive.getRecordDetails(context, record.id)).sourceRefs.single()
            val source = requireNotNull(dao.getSource(ref.sourceId))
            dao.upsertSource(source.copy(status = "UNKNOWN"))
            dao.upsertResidentEntry(
                MemoryResidentEntryEntity(
                    "legacy-resident",
                    context.spaceId,
                    context.characterId,
                    "USER",
                    context.personaKey,
                    "保留旧认知",
                    3,
                    "READY",
                    false,
                    null,
                    false,
                    f.now,
                    f.now
                )
            )
            dao.upsertDerivedLink(
                MemoryDerivedLinkEntity(
                    "RESIDENT",
                    "legacy-resident",
                    record.id,
                    record.revision
                )
            )
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
            assertTrue(
                f.archive.editResidentEntry(
                    context,
                    "legacy-resident",
                    3,
                    "用户明确修订",
                    false
                ) is MemoryKnowledgeWriteResult.Written
            )
            val details = requireNotNull(f.archive.getResidentDetails(context, "legacy-resident"))
            val legacy = details.revisions.single { it.revision == 3L }
            assertEquals("保留旧认知", legacy.content)
            assertEquals(MemoryChangeOrigin.MIGRATION, legacy.origin)
            assertEquals(listOf(ref), legacy.sourceRefs)
            assertEquals(
                MemorySourceStatus.UNKNOWN,
                details.sources.single { it.id == ref.sourceId }.status
            )
            assertEquals(
                "用户明确修订",
                requireNotNull(f.archive.getRecallSnapshot(context)).residents.single().content
            )
        }
    }

    @Test
    fun foreignLegacySourceReference_neverExposesAnotherPersonaBody() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val other = f.contexts(persona = "另一个身份").single()
            val own = f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.USER,
                "本身份认知",
                false
            ) as MemoryKnowledgeWriteResult.Written
            val foreign = f.archive.addResidentEntry(
                other,
                MemoryResidentCategory.USER,
                "外身份私有来源",
                false
            ) as MemoryKnowledgeWriteResult.Written
            val dao = f.database.memoryKnowledgeDao()
            val foreignLink = dao.listSources("RESIDENT", foreign.id, 1).single()
            // 合成损坏的旧引用：读取层仍须隔离，不能仅信任多态 owner 字段。
            dao.insertSource(foreignLink.copy(ownerId = own.id))
            val details = requireNotNull(f.archive.getResidentDetails(context, own.id))
            assertFalse(details.sources.any { it.content.contains("外身份私有来源") })
            assertTrue(details.sources.all { it.personaKey == context.personaKey })
            assertTrue(requireNotNull(f.archive.getRecallSnapshot(context)).residents.isEmpty())
        }
    }
}
