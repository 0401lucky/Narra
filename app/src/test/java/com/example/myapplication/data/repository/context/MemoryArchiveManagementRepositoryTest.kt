package com.example.myapplication.data.repository.context

import android.app.Application
import androidx.room.withTransaction
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.data.local.memory.MemoryKnowledgeSourceEntity
import com.example.myapplication.data.local.memory.MemoryRecordSourceEntity
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
class MemoryArchiveManagementRepositoryTest {
    @Test
    fun freshDaily_manualRecordThenChatUsesSameSpace() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val management = f.archive.ensureDailySpace("a")
            val result = f.archive.addRecord(
                management,
                ManualMemoryRecordDraft("第一段🙂\n尾部706", category = MemoryCategory.SHARED)
            ) as MemoryWriteResult.Committed
            val detail =
                requireNotNull(f.archive.getRecordDetails(management, result.recordIds.single()))
            assertEquals("第一段🙂\n尾部706", detail.record.content)
            assertEquals(MemorySourceKind.MANUAL, detail.sources.single().kind)
            assertTrue(f.archive.listJobHistory(management).isEmpty())
            assertTrue(f.archive.listPendingIngress(management).isEmpty())
            assertEquals(management.spaceId, f.contexts().single().spaceId)
            assertEquals("", management.runId)
            assertEquals(management, f.archive.getManagementContext("a", management.spaceId))
        }
    }

    @Test
    fun personas_includeKnowledgeOnlyAndDeletedCharacter() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val management = f.archive.ensureDailySpace("a")
            val oldPersona = management.copy(personaKey = "mask:old-only")
            f.archive.addResidentEntry(
                oldPersona,
                MemoryResidentCategory.SHARED,
                "合成旧身份的纪念日",
                false
            )
            assertTrue(f.archive.listRecords(management).isEmpty())
            assertTrue(
                f.archive.listArchivePersonas(management).any { it.key == oldPersona.personaKey })
            val query = MemoryArchiveQuery(tab = MemoryArchiveTab.RESIDENTS)
            assertEquals(0, f.archive.queryArchive(management, query).totalCount)
            assertEquals(1, f.archive.queryArchive(oldPersona, query).totalCount)
            f.settings =
                f.settings.copy(assistants = f.settings.assistants.filterNot { it.id == "a" })
            f.archive.closeCharacterSpaces("a")
            assertTrue("a" in f.archive.listArchiveCharacterIds())
            val retained = requireNotNull(
                f.archive.getManagementContext(
                    "a",
                    management.spaceId,
                    oldPersona.personaKey
                )
            )
            assertEquals(1, f.archive.queryArchive(retained, query).totalCount)
        }
    }

    @Test
    fun searchAndCategory_filterBeforePagingAcrossPersonas() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.archive.ensureDailySpace("a")
            f.database.withTransaction {
                repeat(73) { index ->
                    f.now++
                    f.archive.addRecord(
                        context, ManualMemoryRecordDraft(
                            "合成第${index}条 " + if (index % 7 == 0) "叶栖706" else "普通经历",
                            category = if (index % 2 == 0) MemoryCategory.WORK_STUDY else MemoryCategory.DAILY,
                        )
                    )
                }
                f.archive.addRecord(
                    context.copy(personaKey = "mask:other"),
                    ManualMemoryRecordDraft("叶栖706", category = MemoryCategory.WORK_STUDY)
                )
            }
            val page = f.archive.queryArchive(
                context,
                MemoryArchiveQuery(
                    search = "叶栖706",
                    category = MemoryCategory.WORK_STUDY,
                    limit = 2,
                    offset = 2
                )
            )
            assertEquals(6, page.totalCount)
            assertEquals(2, page.items.size)
            assertTrue(page.hasMore)
            assertTrue(page.items.all { (it as MemoryArchiveItem.Record).record.category == MemoryCategory.WORK_STUDY })
            assertEquals(4, page.nextOffset)
            assertEquals(74, f.archive.countArchiveItems("a"))
        }
    }

    @Test
    fun revisionSources_remainBoundToSelectedBody() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.archive.ensureDailySpace("a")
            val id = (f.archive.addRecord(
                context,
                ManualMemoryRecordDraft("旧地址706")
            ) as MemoryWriteResult.Committed).recordIds.single()
            val before = requireNotNull(f.archive.getRecordRevisionDetails(context, id, 1))
            assertTrue(
                f.archive.editRecord(
                    context,
                    id,
                    1,
                    "新地址819\n完整次段"
                ) is MemoryWriteResult.Committed
            )
            val old = requireNotNull(f.archive.getRecordRevisionDetails(context, id, 1))
            val current = requireNotNull(f.archive.getRecordRevisionDetails(context, id, 2))
            assertEquals(before, old.copy(record = before.record))
            assertEquals("旧地址706", old.sources.single().content)
            assertEquals("新地址819\n完整次段", current.sources.single().content)
            assertNotEquals(old.sourceRefs, current.sourceRefs)
            assertNull(
                f.archive.getRecordRevisionDetails(
                    context.copy(personaKey = "mask:other"),
                    id,
                    1
                )
            )
            assertTrue(
                f.archive.editRecord(
                    context,
                    id,
                    1,
                    "迟到覆盖"
                ) is MemoryWriteResult.Conflict
            )
        }
    }

    @Test
    fun metadataCas_keepsSourcesAndRejectsStaleEditor() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.archive.ensureDailySpace("a")
            val id = (f.archive.addRecord(
                context,
                ManualMemoryRecordDraft("完整来源")
            ) as MemoryWriteResult.Committed).recordIds.single()
            val original = requireNotNull(f.archive.getRecordDetails(context, id))
            assertTrue(
                f.archive.setRecordPinned(
                    context,
                    id,
                    1,
                    true,
                    f.now + 1000
                ) is MemoryWriteResult.Committed
            )
            assertTrue(
                f.archive.setRecordArchived(
                    context,
                    id,
                    1,
                    true
                ) is MemoryWriteResult.Conflict
            )
            assertTrue(
                f.archive.setRecordArchived(
                    context,
                    id,
                    2,
                    true
                ) is MemoryWriteResult.Committed
            )
            val current = requireNotNull(f.archive.getRecordDetails(context, id))
            assertEquals(3L, current.record.revision)
            assertEquals(original.sourceRefs, current.sourceRefs)
            assertEquals(
                1,
                f.archive.queryArchive(
                    context,
                    MemoryArchiveQuery(status = MemoryArchiveStatusFilter.ARCHIVED)
                ).totalCount
            )
        }
    }

    @Test
    fun archivedReceipt_requiresRealMessageAndSelectedPersona() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            f.enable()
            val runtime = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            f.remember(runtime, "叶栖的706号预约")
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    runtime,
                    "叶栖706",
                    emptyList(),
                    8,
                    2000,
                    false
                )
            )
            val request = f.recallRequest(plan)
            assertTrue(f.archive.prepareRecallReceipt(request))
            val sequence = requireNotNull(
                f.archive.prepareRecallTransmission(
                    request.requestId,
                    "a".repeat(64),
                    plan.materials.map { it.ref },
                    plan.omitted
                )
            )
            assertTrue(f.archive.confirmRecallTransmission(request.requestId, sequence))
            f.archive.archiveConversation(runtime.conversationId)
            f.reopen()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            assertNull(f.archive.getRecallReceipt(runtime, request.requestId))
            val receipt = requireNotNull(
                f.archive.getArchivedRecallReceipt(
                    management,
                    runtime.conversationId,
                    request.anchorMessageId,
                    request.requestId
                )
            )
            assertEquals(MemoryRecallStatus.SENT, receipt.status)
            assertEquals(runtime, receipt.context)
            f.add(runtime, "newest-no-receipt", "本轮尚无现场", MessageRole.ASSISTANT)
            assertNull(
                f.archive.getArchivedRecallReceipt(
                    management,
                    runtime.conversationId,
                    "newest-no-receipt",
                    request.requestId
                )
            )
            assertNull(
                f.archive.getArchivedRecallReceipt(
                    management.copy(personaKey = "mask:other"),
                    runtime.conversationId,
                    request.anchorMessageId,
                    request.requestId
                )
            )
            val page = f.archive.listArchiveMessages(management, runtime.conversationId, 1, 0)
            val tail = f.archive.listArchiveMessages(
                management,
                runtime.conversationId,
                1,
                page.totalCount - 1
            )
            assertEquals("newest-no-receipt", tail.items.single().message.id)
        }
    }

    @Test
    fun sharedImportedSource_survivesAllHistoryAndKnowledgeReferences() = runBlocking {
        MemoryExtractionFixture(true).use { f ->
            var context = f.archive.ensureDailySpace("a")
            val first = (f.archive.addRecord(
                context,
                ManualMemoryRecordDraft("共享导入正文")
            ) as MemoryWriteResult.Committed).recordIds.single()
            val second = (f.archive.addRecord(
                context,
                ManualMemoryRecordDraft("第二条旧版")
            ) as MemoryWriteResult.Committed).recordIds.single()
            f.archive.editRecord(context, second, 1, "第二条新版")
            val sharedRef =
                requireNotNull(f.archive.getRecordDetails(context, first)).sourceRefs.single()
            val dao = f.database.memoryArchiveDao()
            dao.upsertSource(requireNotNull(dao.getSource(sharedRef.sourceId)).copy(kind = "IMPORTED"))
            dao.insertRecordSource(
                MemoryRecordSourceEntity(
                    second,
                    1,
                    sharedRef.sourceId,
                    sharedRef.sourceRevision,
                    sharedRef.start,
                    sharedRef.end
                )
            )
            val resident = f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.SHARED,
                "直接来源的认知",
                false
            ) as MemoryKnowledgeWriteResult.Written
            f.database.memoryKnowledgeDao().insertSource(
                MemoryKnowledgeSourceEntity(
                    "RESIDENT",
                    resident.id,
                    1,
                    sharedRef.sourceId,
                    sharedRef.sourceRevision,
                    sharedRef.start,
                    sharedRef.end
                )
            )
            f.archive.deleteRecord(context, first, 1)
            context = requireNotNull(f.archive.getManagementContext("a", context.spaceId))
            assertEquals("共享导入正文", dao.getSource(sharedRef.sourceId)?.content)
            f.archive.deleteKnowledge(context, MemoryMaterialKind.RESIDENT, resident.id, 1)
            assertEquals("共享导入正文", dao.getSource(sharedRef.sourceId)?.content)
            f.archive.deleteRecord(context, second, 2)
            f.reopen()
            assertEquals("", f.database.memoryArchiveDao().getSource(sharedRef.sourceId)?.content)
            assertEquals(
                "DELETED",
                f.database.memoryArchiveDao().getSource(sharedRef.sourceId)?.status
            )
        }
    }

    @Test
    fun failedRevisionInsert_rollsBackManualRecordAndSource() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.archive.ensureDailySpace("a")
            f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER c6_fail BEFORE INSERT ON memory_record_revisions BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END")
            assertTrue(runCatching {
                f.archive.addRecord(
                    context,
                    ManualMemoryRecordDraft("事务不会留下半条记录")
                )
            }.isFailure)
            assertTrue(f.database.memoryArchiveDao().listSpaceSources(context.spaceId).isEmpty())
            assertEquals(0, f.archive.countArchiveItems("a"))
        }
    }

    @Test
    fun importedMaterials_areLabeledAndFlagCannotBeForged() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            val record = f.remember(runtime, "导入的叶栖706号约定")
            val recordSource =
                requireNotNull(f.archive.getRecordDetails(runtime, record.id)).sources.single()
            val dao = f.database.memoryArchiveDao()
            dao.upsertSource(
                requireNotNull(dao.getSource(recordSource.id)).copy(
                    kind = "IMPORTED",
                    messageId = null,
                    conversationId = ""
                )
            )
            val resident = f.archive.addResidentEntry(
                runtime,
                MemoryResidentCategory.USER,
                "导入的长期认知",
                false
            ) as MemoryKnowledgeWriteResult.Written
            val residentSource =
                requireNotNull(f.archive.getResidentDetails(runtime, resident.id)).sources.single()
            dao.upsertSource(requireNotNull(dao.getSource(residentSource.id)).copy(kind = "IMPORTED"))
            val plan = requireNotNull(
                MemoryRecallEngine(f.archive).recall(
                    runtime,
                    "叶栖706",
                    emptyList(),
                    8,
                    2000,
                    false
                )
            )
            val imported = plan.materials.filter { it.ref.imported }
            assertEquals(
                setOf(MemoryMaterialKind.RECORD, MemoryMaterialKind.RESIDENT),
                imported.map { it.ref.kind }.toSet()
            )
            assertTrue(imported.all { it.label.contains("导入资料") && it.label.contains("不视作本机共同经历") })
            assertTrue(f.archive.validateRecallMaterials(runtime, imported.map { it.ref }))
            assertFalse(
                f.archive.validateRecallMaterials(
                    runtime,
                    listOf(imported.first().ref.copy(imported = false))
                )
            )
        }
    }

    @Test
    fun latestAssistant_doesNotFallBackThroughPersonaFilter() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            f.add(runtime, "older-assistant", "旧轮回答", MessageRole.ASSISTANT)
            f.add(runtime, "newer-assistant", "新身份本轮回答", MessageRole.ASSISTANT)
            val dao = f.database.memoryArchiveDao()
            val source = dao.listMessageSources(runtime.conversationId, "newer-assistant").single()
            dao.upsertSource(source.copy(personaKey = "mask:other"))
            assertEquals(
                listOf("older-assistant"),
                f.archive.listArchiveMessages(
                    management,
                    runtime.conversationId
                ).items.map { it.message.id })
            val actual = requireNotNull(
                f.archive.getLatestArchiveAssistantMessage(
                    management,
                    runtime.conversationId
                )
            )
            assertEquals("newer-assistant", actual.message.id)
            assertEquals("mask:other", actual.personaKey)
        }
    }

    @Test
    fun staleEventFilter_usesSummaryStatusAfterRecordEdit() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val runtime = f.contexts().single()
            val management = requireNotNull(f.archive.getManagementContext("a", runtime.spaceId))
            val (event, records) = f.createEvent(runtime, listOf("活动起因", "活动后续"))
            val query = MemoryArchiveQuery(
                tab = MemoryArchiveTab.EVENTS,
                status = MemoryArchiveStatusFilter.STALE
            )
            assertEquals(0, f.archive.queryArchive(management, query).totalCount)
            val first = records.first()
            f.archive.editRecord(management, first.id, first.revision, "用户更正的活动起因")
            val current = requireNotNull(f.archive.getEventDetails(management, event.id)).event
            assertEquals(MemoryEventStatus.ACTIVE, current.status)
            assertEquals(MemoryEventSummaryStatus.STALE, current.summaryStatus)
            assertEquals(
                listOf(event.id),
                f.archive.queryArchive(management, query).items.map { it.id })
            f.archive.setEventArchived(management, event.id, current.generation, true)
            assertEquals(1, f.archive.queryArchive(management, query).totalCount)
            assertEquals(
                1,
                f.archive.queryArchive(
                    management,
                    query.copy(status = MemoryArchiveStatusFilter.ARCHIVED)
                ).totalCount
            )
        }
    }

    @Test
    fun jobHistory_filtersPersonaBeforePagingAndControl() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val first = f.archive.ensureDailySpace("a", "mask:first")
            val second = first.copy(personaKey = "mask:second")
            suspend fun source(context: MemoryRuntimeContext): MemorySourceRef {
                val id = (f.archive.addRecord(
                    context,
                    ManualMemoryRecordDraft("手动原料")
                ) as MemoryWriteResult.Committed).recordIds.single()
                return requireNotNull(f.archive.getRecordDetails(context, id)).sourceRefs.single()
            }

            val firstSource = source(first)
            val secondSource = source(second)
            val oldest = f.archive.planSelectedJob(
                first,
                "first-old",
                listOf(firstSource),
                requestJson = "{}"
            )
            var otherId = ""
            repeat(3) { index ->
                f.now++
                otherId = f.archive.planSelectedJob(
                    second,
                    "other-$index",
                    listOf(secondSource),
                    requestJson = "{}"
                ).id
            }
            f.now++
            val newest = f.archive.planSelectedJob(
                first,
                "first-new",
                listOf(firstSource),
                requestJson = "{}"
            )
            assertEquals(newest.id, f.archive.listJobHistory(first, 1, 0).single().id)
            assertEquals(oldest.id, f.archive.listJobHistory(first, 1, 1).single().id)
            assertEquals(3, f.archive.listJobHistory(second).size)
            assertNull(f.archive.getProcessingControl(first, otherId))
            assertEquals(
                "CONTEXT_UNAVAILABLE",
                f.coordinator().resumeJob(first, otherId).failureCode
            )
        }
    }
}
