package com.example.myapplication.data.repository.context

import android.app.Application
import androidx.room.withTransaction
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.data.local.memory.MemoryRecordRevisionEntity
import com.example.myapplication.data.local.memory.MemoryRecordSourceEntity
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
class MemoryArchiveTransferRepositoryTest {
    @Test
    fun graphRoundTrip() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                source.enable()
                val originalContext = source.contexts().single()
                val first = source.remember(originalContext, "第一版🌠\n芮珩在706号房。")
                source.archive.editRecord(originalContext, first.id, 1L, "第二版🌠\n芮珩在182号房。")
                source.archive.editRecord(
                    originalContext,
                    first.id,
                    2L,
                    "第三版🌠\n共同记录：房号182，尾部数字987654321。"
                )
                val work = source.prepareKnowledge(originalContext, "我们共同修复罗盘706。") {
                    knowledgeResponse(
                        memories = listOf(knowledgeEpisode("共同修复罗盘706")),
                        residents = listOf(
                            residentCandidate(
                                "共同修复过罗盘706",
                                MemoryResidentCategory.SHARED
                            )
                        ),
                        states = listOf(
                            stateUpdate(
                                MemoryStateKey.TASK,
                                "等待新齿轮",
                                expiresAt = source.now + 100_000
                            )
                        )
                    )
                }
                source.commitKnowledge(work)
                val oldResident = source.archive.addResidentEntry(
                    originalContext,
                    MemoryResidentCategory.CHARACTER,
                    "角色原有背景",
                    false
                ) as MemoryKnowledgeWriteResult.Written
                source.archive.setResidentArchived(
                    originalContext,
                    oldResident.id,
                    oldResident.revision,
                    true
                )
                val newResident = source.archive.addResidentEntry(
                    originalContext,
                    MemoryResidentCategory.CHARACTER,
                    "角色更新背景",
                    false
                ) as MemoryKnowledgeWriteResult.Written
                source.database.memoryArchiveDao().upsertResidentEntry(
                    requireNotNull(
                        source.database.memoryKnowledgeDao().getResident(newResident.id)
                    ).copy(supersedesId = oldResident.id)
                )
                val (event, eventRecords) = source.createEvent(
                    originalContext,
                    listOf("芮珩委托修复罗盘", "找到706号齿轮")
                )
                source.eventCoordinator(RecordingMemoryEventAi())
                    .rebuild(originalContext, event.id, event.generation)
                val live = source.remember(originalContext, "新进展：等候取件")
                source.archive.linkEventMembers(
                    originalContext,
                    MemoryRecordVersion(live.id, live.revision),
                    MemoryRecordVersion(eventRecords.first().id, 1L)
                )
                val bundle = source.transferBundle()
                val graph = requireNotNull(bundle.memoryArchive)
                assertEquals(3, graph.recordRevisions.count { it.recordId == first.id })
                assertTrue(graph.derivedLinks.isNotEmpty())
                assertEquals(2, graph.eventMembers.count { it.coveredRevision != null })
                val target = destination.contexts().single()
                val transfer = destination.transfer()
                val plan = transfer.prepareImport(bundle, targetsFor(bundle, target))
                val result =
                    transfer.importArchive(bundle, plan) as MemoryArchiveImportResult.Imported
                assertEquals(graph.itemCount, result.itemCount)
                assertTrue(result.objectMappings.filter { it.kind != MemoryTransferObjectKind.SPACE }
                    .all { it.originalId != it.localId })
                destination.reopen()
                val reopened = requireNotNull(
                    destination.archive.getRuntimeContext(
                        target.conversationId,
                        "a"
                    )
                )
                val id = result.localId(MemoryTransferObjectKind.RECORD, first.id)
                val details = requireNotNull(destination.archive.getRecordDetails(reopened, id))
                assertEquals(listOf(3L, 2L, 1L), details.revisions.map { it.revision })
                assertEquals(
                    graph.recordRevisions.filter { it.recordId == first.id }
                        .associate { it.revision to it.content },
                    details.revisions.associate { it.revision to it.content })
                for (revision in details.revisions) {
                    val history = requireNotNull(
                        destination.archive.getRecordRevisionDetails(
                            reopened.copy(conversationId = ""), id, revision.revision
                        )
                    )
                    assertTrue(history.sources.all { it.kind == MemorySourceKind.IMPORTED && it.conversationId.isEmpty() && it.messageId.isEmpty() })
                    assertEquals(
                        graph.recordSources.filter { it.recordId == first.id && it.recordRevision == revision.revision }
                            .map { it.source.start to it.source.end },
                        history.sourceRefs.map { it.start to it.end })
                }
                val importedEvent = requireNotNull(
                    destination.archive.getEventDetails(
                        reopened,
                        result.localId(MemoryTransferObjectKind.EVENT, event.id)
                    )
                )
                assertEquals(2, importedEvent.members.count { it.coveredRevision != null })
                assertEquals(1, importedEvent.members.count { it.coveredRevision == null })
                assertEquals(
                    result.localId(MemoryTransferObjectKind.RESIDENT, oldResident.id),
                    destination.archive.getResidentDetails(
                        reopened,
                        result.localId(MemoryTransferObjectKind.RESIDENT, newResident.id)
                    )?.entry?.supersedesId
                )
                val recall = requireNotNull(
                    MemoryRecallEngine(destination.archive).recall(
                        reopened,
                        "芮珩罗盘房号182",
                        emptyList(),
                        20,
                        16_000,
                        false
                    )
                )
                assertTrue(recall.section.contains("导入资料"))
                assertTrue(recall.materials.all { it.label.contains("导入资料") })
                val exportedAgain = requireNotNull(destination.transferBundle().memoryArchive)
                assertEquals(graph.recordRevisions.size, exportedAgain.recordRevisions.size)
                assertEquals(graph.recordSources.size, exportedAgain.recordSources.size)
                assertEquals(graph.knowledgeRevisions.size, exportedAgain.knowledgeRevisions.size)
                assertTrue(exportedAgain.origins.any { it.kind == MemoryTransferObjectKind.RECORD && it.localId == id && it.origin.originalId == first.id })
                assertTrue(destination.ai.snapshots.isEmpty())
                assertNoTasks(destination, reopened.spaceId)
            }
        }
    }

    @Test
    fun sourceOnlyPersonas() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                val sourceA = source.contexts(persona = "原身份甲").single()
                val sourceB = source.contexts(persona = "原身份乙").single()
                source.remember(sourceA, "只属于身份甲的原记录")
                source.archive.addResidentEntry(
                    sourceB,
                    MemoryResidentCategory.USER,
                    "身份乙喜欢栀子花",
                    false
                )
                source.archive.setCurrentState(
                    sourceB,
                    MemoryStateKey.LOCATION,
                    0L,
                    "身份乙在合成港湾",
                    source.now + 10_000L
                )
                val bundle = source.transferBundle()
                val targetA = destination.contexts(persona = "本机甲").single()
                val targetB = destination.contexts(persona = "本机乙").single()
                val groups =
                    MemoryTransferLegacyConversion.sourceGroups(requireNotNull(bundle.memoryArchive))
                assertEquals(2, groups.size)
                val targets =
                    groups.associate { it.key to if (it.personaKey == sourceA.personaKey) targetA.importTarget() else targetB.importTarget() }
                val repository = destination.transfer()
                repository.importArchive(
                    bundle,
                    repository.prepareImport(bundle, targets)
                ) as MemoryArchiveImportResult.Imported
                destination.reopen()
                val a = requireNotNull(
                    destination.archive.getRuntimeContext(
                        targetA.conversationId,
                        "a"
                    )
                )
                val b = requireNotNull(
                    destination.archive.getRuntimeContext(
                        targetB.conversationId,
                        "a"
                    )
                )
                assertTrue(requireNotNull(destination.archive.getRecallSnapshot(a)).residents.isEmpty())
                val snapshot = requireNotNull(destination.archive.getRecallSnapshot(b))
                assertTrue(snapshot.records.isEmpty())
                assertEquals("身份乙喜欢栀子花", snapshot.residents.single().content)
                assertEquals("身份乙在合成港湾", snapshot.states.single().content)
                val recall = requireNotNull(
                    MemoryRecallEngine(destination.archive).recall(
                        b,
                        "你好",
                        emptyList(),
                        10,
                        2000,
                        false
                    )
                )
                assertTrue(recall.materials.all { it.label.contains("导入资料") })
            }
        }
    }

    @Test
    fun localIdCollision() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture().use { destination ->
                val context = source.contexts().single()
                val record = source.remember(context, "源库真实语义🌠，编号706", "same-message")
                val bundle = source.transferBundle()
                val graph = requireNotNull(bundle.memoryArchive)
                destination.store.upsertConversationMetadata(
                    Conversation(
                        context.conversationId,
                        "本机会话",
                        "",
                        destination.now,
                        destination.now,
                        assistantId = "a"
                    )
                )
                val target = destination.archive.bindConversation(
                    RoleplayScenario(
                        id = "local-scenario",
                        assistantId = "a",
                        memoryPurpose = MemoryPurpose.CONTINUOUS
                    ), context.conversationId, listOf("a"), "local-run"
                ).single()
                val local = destination.remember(target, "本机完全不同的文字", "same-message")
                val localSourceRef = requireNotNull(
                    destination.archive.getRecordDetails(
                        target,
                        local.id
                    )
                ).sourceRefs.single()
                val collision = requireNotNull(
                    destination.database.memoryArchiveDao().getRecord(local.id)
                ).copy(id = record.id, jobId = null, localItemId = null)
                destination.database.memoryTransferDao().insertRecords(listOf(collision))
                destination.database.memoryTransferDao().insertRecordRevisions(
                    listOf(
                        MemoryRecordRevisionEntity(
                            collision.id,
                            1L,
                            collision.content,
                            "MANUAL",
                            destination.now
                        )
                    )
                )
                destination.database.memoryTransferDao().insertRecordSources(
                    listOf(
                        MemoryRecordSourceEntity(
                            collision.id,
                            1L,
                            localSourceRef.sourceId,
                            localSourceRef.sourceRevision,
                            localSourceRef.start,
                            localSourceRef.end
                        )
                    )
                )
                val transfer = destination.transfer()
                val result = transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                ) as MemoryArchiveImportResult.Imported
                val importedId = result.localId(MemoryTransferObjectKind.RECORD, record.id)
                val importedSourceId =
                    result.localId(MemoryTransferObjectKind.SOURCE, graph.sources.single().id)
                val importedSource = requireNotNull(
                    destination.database.memoryArchiveDao().getSource(importedSourceId)
                )
                assertNull(importedSource.messageId)
                assertEquals("", importedSource.conversationId)
                assertEquals(
                    "本机完全不同的文字",
                    destination.archive.getRecordDetails(target, collision.id)?.record?.content
                )
                assertTrue(
                    destination.archive.getSourceSegments(target, importedSourceId)
                        .joinToString { it.text }.contains("源库真实语义")
                )
                val revisionBefore =
                    destination.database.memoryArchiveDao().maxSourceRevision("same-message")
                destination.store.deleteMessageWithAssociations(
                    requireNotNull(
                        destination.store.getConversation(
                            target.conversationId
                        )
                    ), target.conversationId, "same-message"
                )
                val latestTarget = requireNotNull(
                    destination.archive.getRuntimeContext(
                        target.conversationId,
                        target.characterId
                    )
                )
                assertEquals(
                    record.content,
                    destination.archive.getRecordDetails(latestTarget, importedId)?.record?.content
                )
                assertEquals(
                    MemorySourceStatus.AVAILABLE,
                    destination.archive.getSource(latestTarget, importedSourceId)?.status
                )
                assertEquals(
                    revisionBefore,
                    destination.database.memoryArchiveDao().maxSourceRevision("same-message")
                )
                val origins = transfer.getOrigins(
                    latestTarget,
                    MemoryTransferObjectKind.SOURCE,
                    importedSourceId
                )
                assertTrue(origins.any { it.origin.originalMessageId == "same-message" && it.origin.originalConversationId == context.conversationId && it.origin.originalSourceKind == MemorySourceKind.MESSAGE })
            }
        }
    }

    @Test
    fun unavailableHistory() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture().use { destination ->
                val sourceContext = source.contexts().single()
                val record = source.remember(sourceContext, "原声明", "changed-source")
                source.archive.editRecord(sourceContext, record.id, 1L, "人工独立的现行版本")
                source.add(sourceContext, "changed-source", "已改变的原声明")
                val bundle = source.transferBundle()
                assertTrue(requireNotNull(bundle.memoryArchive).sources.any { it.status == MemorySourceStatus.CHANGED && it.content.isEmpty() })
                val target = destination.contexts().single()
                val transfer = destination.transfer()
                val result = transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                ) as MemoryArchiveImportResult.Imported
                val restored = transfer.exportArchive(false)
                assertTrue(restored.sources.any { it.status == MemorySourceStatus.CHANGED && it.kind == MemorySourceKind.IMPORTED && it.content.isEmpty() })
                assertEquals(
                    "人工独立的现行版本",
                    destination.archive.getRecordDetails(
                        target,
                        result.localId(MemoryTransferObjectKind.RECORD, record.id)
                    )?.record?.content
                )
            }
        }
    }

    @Test
    fun stateConflictAndCas() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                val sourceContext = source.contexts().single()
                source.archive.setCurrentState(
                    sourceContext,
                    MemoryStateKey.MOOD,
                    0L,
                    "原状态一",
                    source.now + 1000
                )
                source.archive.setCurrentState(
                    sourceContext,
                    MemoryStateKey.MOOD,
                    1L,
                    "原状态二",
                    source.now + 1000
                )
                source.remember(sourceContext, "和状态同事务的经历")
                val bundle = source.transferBundle()
                val target = destination.contexts().single()
                destination.archive.setCurrentState(
                    target,
                    MemoryStateKey.MOOD,
                    0L,
                    "本机人工状态",
                    destination.now + 5000
                )
                val old = destination.archive.listCurrentStates(target).single()
                val transfer = destination.transfer()
                val plan = transfer.prepareImport(bundle, targetsFor(bundle, target))
                assertEquals(1L, plan.stateConflicts.single().expectedRevision)
                assertTrue(
                    transfer.importArchive(
                        bundle,
                        plan
                    ) is MemoryArchiveImportResult.Conflict
                )
                assertTrue(destination.archive.listRecords(target).isEmpty())
                destination.archive.setCurrentState(
                    target,
                    MemoryStateKey.MOOD,
                    1L,
                    "预览后改动",
                    destination.now + 5000
                )
                assertTrue(
                    transfer.importArchive(
                        bundle,
                        plan.copy(replaceExistingStates = true)
                    ) is MemoryArchiveImportResult.Conflict
                )
                val refreshed = transfer.prepareImport(bundle, targetsFor(bundle, target))
                    .copy(replaceExistingStates = true)
                val result =
                    transfer.importArchive(bundle, refreshed) as MemoryArchiveImportResult.Imported
                assertEquals(
                    old.id,
                    result.localId(
                        MemoryTransferObjectKind.STATE,
                        requireNotNull(bundle.memoryArchive).states.single().id
                    )
                )
                destination.reopen()
                val state =
                    requireNotNull(destination.archive.getCurrentStateDetails(target, old.id))
                assertEquals(listOf(4L, 3L, 2L, 1L), state.revisions.map { it.revision })
                assertEquals(
                    listOf("原状态二", "原状态一", "预览后改动", "本机人工状态"),
                    state.revisions.map { it.content })
                assertEquals(4L, state.state.revision)
                assertEquals(
                    requireNotNull(bundle.memoryArchive).states.single().expiresAt,
                    state.state.expiresAt
                )
                destination.now += 20_000
                assertTrue(requireNotNull(destination.archive.getRecallSnapshot(target)).states.isEmpty())
            }
        }
    }

    @Test
    fun collapsedStateSlots() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture().use { destination ->
                val a = source.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
                val b = source.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
                source.archive.setCurrentState(a, MemoryStateKey.LOCATION, 0L, "甲港口", null)
                source.archive.setCurrentState(b, MemoryStateKey.LOCATION, 0L, "乙港口", null)
                val bundle = source.transferBundle()
                val target = destination.contexts().single()
                val repository = destination.transfer()
                val plan = repository.prepareImport(bundle, targetsFor(bundle, target))
                    .copy(replaceExistingStates = true)
                assertTrue(plan.stateConflicts.all { it.multipleImportedHeads })
                assertTrue(
                    repository.importArchive(
                        bundle,
                        plan
                    ) is MemoryArchiveImportResult.Conflict
                )
                assertTrue(destination.archive.listCurrentStates(target).isEmpty())
                assertNull(destination.database.memoryTransferDao().getReceipt(plan.localPlanId))
            }
        }
    }

    @Test
    fun sharedImportedSource() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                val context = source.contexts().single()
                val work = source.prepareKnowledge(context, "独立来源：我喜欢栀子花") {
                    knowledgeResponse(residents = listOf(residentCandidate("喜欢栀子花")))
                }
                source.commitKnowledge(work)
                val ref = work.snapshot.sources.single().ref
                val first = source.fromSource(context, ref, "经历一：讨论栀子花")
                val history = source.fromSource(context, ref, "经历二原版")
                source.archive.editRecord(context, history.id, 1L, "经历二独立人工新版")
                val resident = source.archive.listResidentEntries(context).single()
                val bundle = source.transferBundle()
                var target = destination.contexts().single()
                val transfer = destination.transfer()
                val result = transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                ) as MemoryArchiveImportResult.Imported
                val shared = result.localId(MemoryTransferObjectKind.SOURCE, ref.sourceId)
                val firstId = result.localId(MemoryTransferObjectKind.RECORD, first.id)
                val historyId = result.localId(MemoryTransferObjectKind.RECORD, history.id)
                val residentId = result.localId(MemoryTransferObjectKind.RESIDENT, resident.id)
                destination.archive.deleteRecord(target, firstId, 1L)
                target = requireNotNull(
                    destination.archive.getRuntimeContext(
                        target.conversationId,
                        target.characterId
                    )
                )
                assertEquals(
                    MemorySourceStatus.AVAILABLE,
                    destination.archive.getSource(target, shared)?.status
                )
                destination.archive.deleteRecord(target, historyId, 2L)
                target = requireNotNull(
                    destination.archive.getRuntimeContext(
                        target.conversationId,
                        target.characterId
                    )
                )
                assertEquals(
                    "喜欢栀子花",
                    destination.archive.getResidentDetails(target, residentId)?.entry?.content
                )
                destination.archive.editResidentEntry(target, residentId, 1L, "独立人工偏好", false)
                assertEquals(
                    MemorySourceStatus.AVAILABLE,
                    destination.archive.getSource(target, shared)?.status
                )
                destination.archive.deleteKnowledge(
                    target,
                    MemoryMaterialKind.RESIDENT,
                    residentId,
                    2L
                )
                target = requireNotNull(
                    destination.archive.getRuntimeContext(
                        target.conversationId,
                        target.characterId
                    )
                )
                destination.reopen()
                assertEquals("", destination.archive.getSource(target, shared)?.content)
                assertEquals(
                    MemorySourceStatus.DELETED,
                    destination.archive.getSource(target, shared)?.status
                )
                assertTrue(
                    destination.transfer()
                        .getOrigins(target, MemoryTransferObjectKind.SOURCE, shared).isNotEmpty()
                )
                assertTrue(
                    destination.transfer().exportArchive(false).origins.none {
                        it.origin.toString().contains("我喜欢栀子花")
                    })
            }
        }
    }

    @Test
    fun enclosingTransactionRollsBack() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture().use { destination ->
                source.remember(source.contexts().single(), "整笔恢复记录")
                val bundle = source.transferBundle()
                val target = destination.contexts().single()
                var notifications = 0
                val transfer = destination.transfer { notifications++ }
                val plan = transfer.prepareImport(bundle, targetsFor(bundle, target))
                try {
                    destination.database.withTransaction {
                        assertTrue(
                            transfer.importArchive(
                                bundle,
                                plan
                            ) is MemoryArchiveImportResult.Imported
                        )
                        error("后续世界书写入故障")
                    }
                    fail("应当失败")
                } catch (expected: IllegalStateException) {
                    assertEquals("后续世界书写入故障", expected.message)
                }
                assertTrue(destination.archive.listRecords(target).isEmpty())
                assertNull(destination.database.memoryTransferDao().getReceipt(plan.localPlanId))
                assertEquals(0, notifications)
                try {
                    destination.database.withTransaction {
                        transfer.importArchive(bundle, plan)
                        throw CancellationException("合成取消")
                    }
                    fail("应当取消")
                } catch (_: CancellationException) {
                }
                assertTrue(destination.archive.listRecords(target).isEmpty())
                assertNull(destination.database.memoryTransferDao().getReceipt(plan.localPlanId))
                assertEquals(0, notifications)
                destination.database.withTransaction { transfer.importArchive(bundle, plan) }
                transfer.notifyImportCommitted(plan)
                assertEquals(1, notifications)
            }
        }
    }

    @Test
    fun midWriteFailureAndCancellation() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture().use { destination ->
                source.remember(source.contexts().single(), "不可部分保存的资料")
                val bundle = source.transferBundle()
                val target = destination.contexts().single()
                val transfer = destination.transfer()
                val plan = transfer.prepareImport(bundle, targetsFor(bundle, target))
                val sql = destination.database.openHelper.writableDatabase
                sql.execSQL("CREATE TRIGGER abort_transfer BEFORE INSERT ON memory_record_sources BEGIN SELECT RAISE(ABORT, 'synthetic transfer failure'); END")
                var failed = false
                try {
                    transfer.importArchive(bundle, plan)
                } catch (_: Exception) {
                    failed = true
                }
                assertTrue(failed)
                assertTrue(destination.archive.listRecords(target).isEmpty())
                assertTrue(
                    destination.database.memoryTransferDao().listSources(target.spaceId).isEmpty()
                )
                assertNull(destination.database.memoryTransferDao().getReceipt(plan.localPlanId))
                sql.execSQL("DROP TRIGGER abort_transfer")
                val cancellable =
                    RoomMemoryArchiveTransferRepository(
                        destination.database, destination.archive,
                        isCharacterAvailable = {
                            if (destination.database.memoryTransferDao().listRecords(target.spaceId)
                                    .isNotEmpty()
                            ) throw CancellationException("写入后取消")
                            true
                        }, nowProvider = { destination.now })
                var cancelled = false
                try {
                    cancellable.importArchive(bundle, plan)
                } catch (_: CancellationException) {
                    cancelled = true
                }
                assertTrue(cancelled)
                assertTrue(destination.archive.listRecords(target).isEmpty())
                assertNull(destination.database.memoryTransferDao().getReceipt(plan.localPlanId))
            }
        }
    }

    @Test
    fun planReceiptSurvivesReopen() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { destination ->
                source.remember(source.contexts().single(), "只恢复一次的记录")
                val bundle = source.transferBundle()
                val target = destination.contexts().single()
                val transfer = destination.transfer()
                val plan = transfer.prepareImport(bundle, targetsFor(bundle, target))
                val first =
                    transfer.importArchive(bundle, plan) as MemoryArchiveImportResult.Imported
                destination.reopen()
                val retry = destination.transfer()
                    .importArchive(bundle, plan) as MemoryArchiveImportResult.Imported
                assertTrue(retry.alreadyImported)
                assertEquals(first.objectMappings, retry.objectMappings)
                assertEquals(1, destination.archive.listRecords(target).size)
                val other = destination.contexts(characters = listOf("b")).single()
                assertTrue(
                    destination.transfer().importArchive(
                        bundle,
                        plan.copy(targets = targetsFor(bundle, other))
                    ) is MemoryArchiveImportResult.Conflict
                )
                assertTrue(
                    destination.transfer().importArchive(
                        bundle,
                        plan.copy(replaceExistingStates = true)
                    ) is MemoryArchiveImportResult.Conflict
                )
                assertTrue(destination.archive.listRecords(other).isEmpty())
                val newPlan =
                    destination.transfer().prepareImport(bundle, targetsFor(bundle, target))
                assertFalse(
                    (destination.transfer().importArchive(
                        bundle,
                        newPlan
                    ) as MemoryArchiveImportResult.Imported).alreadyImported
                )
                assertEquals(2, destination.archive.listRecords(target).size)
            }
        }
    }

    @Test
    fun legacyArchiveAndFreshDaily() = runBlocking {
        MemoryExtractionFixture(persistent = true).use { destination ->
            val text = "旧资料🌠\n保留末尾98765"
            val bundle = ContextTransferCodec().decode(
                ContextTransferCodec().encode(
                    ContextDataBundle(
                        version = 1, exportedAt = 1L,
                        memoryEntries = listOf(
                            MemoryEntry(
                                "old-record",
                                MemoryScopeType.CONVERSATION,
                                "same-conversation",
                                "outside-character",
                                text,
                                77,
                                true,
                                "same-message",
                                7L,
                                5L,
                                6L
                            )
                        ),
                        conversationSummaries = listOf(
                            ConversationSummary(
                                "same-conversation",
                                summary = "旧整体摘要",
                                coveredMessageCount = 9999
                            )
                        ),
                        conversationSummarySegments = listOf(
                            ConversationSummarySegment(
                                "segment",
                                "missing-conversation",
                                summary = "缺原会话的摘要段"
                            )
                        )
                    )
                )
            )
            val groups = MemoryTransferLegacyConversion.sourceGroups(
                MemoryTransferLegacyConversion.materialize(bundle)
            )
            val repository = destination.transfer()
            val targets = groups.associate {
                it.key to MemoryImportTarget(
                    MemoryImportTargetMode.NEW_DAILY,
                    "a"
                )
            }
            val result = repository.importArchive(
                bundle,
                repository.prepareImport(bundle, targets)
            ) as MemoryArchiveImportResult.Imported
            destination.reopen()
            val runtime = destination.contexts().single()
            assertEquals(3, destination.archive.listRecords(runtime).size)
            val record = destination.archive.listRecords(runtime).single { it.content == text }
            assertEquals(77, record.importance)
            assertTrue(record.pinned)
            assertEquals(5L, record.createdAt)
            assertEquals("", record.legacyScopeId)
            assertEquals(MemoryRecordKind.LEGACY_UNCLASSIFIED, record.kind)
            val origins = destination.transfer()
                .getOrigins(runtime, MemoryTransferObjectKind.RECORD, record.id)
            assertTrue(origins.any { it.origin.originalId == "old-record" && it.origin.originalLegacyScopeId == "same-conversation" && it.origin.originalLegacySourceMessageId == "same-message" })
            assertEquals(
                2,
                destination.transfer()
                    .exportArchive(false).origins.count { it.kind == MemoryTransferObjectKind.RECORD && it.origin.legacySummary != null })
            assertEquals(
                3,
                requireNotNull(destination.archive.getRecallSnapshot(runtime)).records.size
            )
            assertTrue(result.restoredVectorIndexes == 0)
            assertNoTasks(destination, runtime.spaceId)
            assertTrue(
                destination.database.memoryDao().listAllConversationSummarySegments().isEmpty()
            )
        }
    }

    @Test
    fun stateTombstoneOriginsRoundTrip() = runBlocking {
        MemoryExtractionFixture().use { source ->
            MemoryExtractionFixture(persistent = true).use { middle ->
                val context = source.contexts().single()
                source.archive.setCurrentState(
                    context,
                    MemoryStateKey.MOOD,
                    0L,
                    "应被擦除的旧心境",
                    null
                )
                source.archive.setCurrentState(
                    context,
                    MemoryStateKey.MOOD,
                    1L,
                    "应被擦除的新心境",
                    null
                )
                val bundle = source.transferBundle()
                val target = middle.contexts().single()
                val transfer = middle.transfer()
                val imported = transfer.importArchive(
                    bundle,
                    transfer.prepareImport(bundle, targetsFor(bundle, target))
                ) as MemoryArchiveImportResult.Imported
                val originalStateId = requireNotNull(bundle.memoryArchive).states.single().id
                val stateId = imported.localId(MemoryTransferObjectKind.STATE, originalStateId)
                middle.archive.deleteKnowledge(target, MemoryMaterialKind.STATE, stateId, 2L)
                middle.reopen()
                val tombstoneBundle = middle.transferBundle()
                val graph = requireNotNull(tombstoneBundle.memoryArchive)
                assertEquals("DELETED", graph.states.single().status)
                assertEquals(3L, graph.states.single().revision)
                assertTrue(graph.knowledgeRevisions.isEmpty())
                assertEquals(
                    setOf(1L, 2L),
                    graph.origins.filter { it.kind == MemoryTransferObjectKind.STATE }
                        .map { it.localRevision }.toSet()
                )
                assertFalse(ContextTransferCodec().encode(tombstoneBundle).contains("应被擦除"))
                MemoryExtractionFixture().use { destination ->
                    val local = destination.contexts(characters = listOf("b")).single()
                    val repository = destination.transfer()
                    assertTrue(
                        repository.importArchive(
                            tombstoneBundle,
                            repository.prepareImport(
                                tombstoneBundle,
                                targetsFor(tombstoneBundle, local)
                            )
                        ) is MemoryArchiveImportResult.Imported
                    )
                    val restored = repository.exportArchive(false)
                    assertEquals("DELETED", restored.states.single().status)
                    assertEquals(3L, restored.states.single().revision)
                    assertTrue(restored.knowledgeRevisions.isEmpty())
                    assertTrue(restored.sources.all { it.content.isEmpty() })
                    // 删除本身将头递增到 v3；新导入也保存这份无正文墓碑的出处。
                    assertEquals(
                        setOf(1L, 2L, 3L),
                        restored.origins.filter { it.kind == MemoryTransferObjectKind.STATE }
                            .map { it.localRevision }.toSet()
                    )
                    assertTrue(requireNotNull(destination.archive.getRecallSnapshot(local)).states.isEmpty())
                }
            }
        }
    }

    private fun MemoryArchiveImportResult.Imported.localId(
        kind: MemoryTransferObjectKind,
        original: String
    ): String =
        objectMappings.first { it.kind == kind && it.originalId == original && it.originalRevision == null }.localId

    private suspend fun assertNoTasks(f: MemoryExtractionFixture, spaceId: String) {
        assertTrue(f.database.memoryArchiveDao().listSpaceJobs(spaceId).isEmpty())
        assertTrue(f.database.memoryArchiveDao().listProposals(spaceId).isEmpty())
        assertTrue(f.database.memoryArchiveDao().listIngress(spaceId, 0L, 1000).isEmpty())
    }
}
