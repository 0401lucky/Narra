package com.example.myapplication.data.repository.context

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.myapplication.data.local.RoomConversationStore
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryCurrentStateEntity
import com.example.myapplication.data.local.memory.MemoryDerivedLinkEntity
import com.example.myapplication.data.local.memory.MemoryEmbeddingEntity
import com.example.myapplication.data.local.memory.MemoryEventBoxEntity
import com.example.myapplication.data.local.memory.MemoryEventMemberEntity
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.data.local.memory.MemoryResidentEntryEntity
import com.example.myapplication.data.local.memory.MemoryProposalEntity
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryConflict
import com.example.myapplication.model.MemoryJobStatus
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecordCorrection
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordStatus
import com.example.myapplication.model.MemoryRelationDraft
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemorySourceEvidence
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemorySourceStatus
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryFoundationRepositoryTest {
    private lateinit var application: Context
    private lateinit var database: ChatDatabase
    private lateinit var store: RoomConversationStore
    private lateinit var repository: RoomMemoryArchiveRepository
    private var now = 1_000L
    private val availableCharacters = MutableStateFlow(setOf("a", "b"))
    private val databaseNames = mutableListOf<String>()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        databaseNames.forEach { application.deleteDatabase(it) }
    }

    @Test
    fun capture_keepsFullLongSourceAndOnlyQueuesCompletedMessages() = runBlocking {
        val context = context()
        val body = "长段落".repeat(3_000) + "尾部约定：11月19日 706 号房"
        val source = source(context, "long", body)
        assertEquals(body, source.content)
        assertEquals(body.length, source.contentLength)
        assertEquals(1L, source.messageRevision)
        assertFalse(repository.getCursor(context.spaceId)!!.historyBackfilled)
        store.upsertMessages(listOf(message(context, "partial", "未完成", MessageStatus.LOADING)))
        store.upsertMessages(listOf(message(context, "failed", "失败", MessageStatus.ERROR)))
        assertEquals(
            listOf("long"),
            repository.listPendingIngress(context).map { it.source.messageId })
        store.upsertMessages(listOf(message(context, "partial", "完整回复")))
        val completed = repository.listPendingIngress(context)
            .single { it.source.messageId == "partial" }.source
        assertEquals(2L, completed.messageRevision)
        assertEquals("完整回复", completed.content)
        assertEquals(2, repository.getEngineVersion())
    }

    @Test
    fun binding_existingHistoryRemainsUnbackfilled() = runBlocking {
        val conversation = Conversation("old", "旧会话", "", 1, 1, assistantId = "a")
        store.upsertConversationWithMessages(
            conversation,
            listOf(ChatMessage("old-message", "old", MessageRole.USER, "原文保留"))
        )
        val context = repository.bindConversation(
            RoleplayScenario(id = "old-scene", assistantId = "a"), "old", listOf("a"), "old-run",
        ).single()
        assertTrue(repository.listPendingIngress(context).isEmpty())
        assertEquals("原文保留", store.listMessages("old").single().content)
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        assertFalse(repository.getCursor(context.spaceId)!!.historyBackfilled)
        store.upsertMessages(
            listOf(
                store.listMessages("old").single().copy(content = "用户修改的旧原文")
            )
        )
        assertTrue(repository.listPendingIngress(context).isEmpty())
        assertEquals("用户修改的旧原文", store.listMessages("old").single().content)
        assertFalse(repository.getCursor(context.spaceId)!!.historyBackfilled)
    }

    @Test
    fun commit_sqlFailureRollsBackWholeBatchAndRetryUsesSavedArtifact() = runBlocking {
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_second_revision BEFORE INSERT ON memory_record_revisions WHEN NEW.content = '第二条' BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END",
        )
        val batch = MemoryCommitBatch(
            records = listOf(
                MemoryRecordDraft("first", "第一条", listOf(source.fullRef())),
                MemoryRecordDraft("second", "第二条", listOf(source.fullRef())),
            )
        )
        assertTrue(runCatching { repository.commitJob(jobId, OWNER, batch) }.isFailure)
        assertTrue(repository.listRecords(context).isEmpty())
        assertEquals(MemoryJobStatus.EXTRACTED, repository.getJob(jobId)!!.status)
        assertEquals(ARTIFACT, repository.getJob(jobId)!!.artifact)
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        assertEquals(1, repository.listPendingIngress(context).size)
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_second_revision")
        val result = repository.commitJob(jobId, OWNER, batch) as MemoryWriteResult.Committed
        assertEquals(2, result.recordIds.size)
        assertTrue(repository.listPendingIngress(context).isEmpty())
        assertTrue(result.committedSequence > 0)
        val retried = repository.commitJob(jobId, OWNER, batch) as MemoryWriteResult.Committed
        assertTrue(retried.alreadyCommitted)
        assertEquals(result.recordIds, retried.recordIds)
        assertEquals(2, repository.listRecords(context).size)
    }

    @Test
    fun commit_splitSourceOnlyAdvancesWhenAllSpansHaveSucceeded() = runBlocking {
        val context = context()
        val source = source(context, body = "abcdef")
        val first = source.fullRef().copy(end = 3)
        val second = source.fullRef().copy(start = 3)
        val firstJob = extractedJob(context, listOf(first), key = "first")
        repository.commitJob(firstJob, OWNER, MemoryCommitBatch())
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        val secondJob = extractedJob(context, listOf(second), key = "second")
        assertTrue(repository.releaseJob(secondJob, OWNER, MemoryJobStatus.FAILED, "COMMIT_FAILED"))
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        assertEquals(
            MemoryJobStatus.EXTRACTED,
            repository.claimJob(secondJob, "recovered")!!.status
        )
        assertEquals(ARTIFACT, repository.getJob(secondJob)!!.artifact)
        repository.commitJob(secondJob, "recovered", MemoryCommitBatch())
        assertTrue(repository.getCursor(context.spaceId)!!.committedSequence > 0)
        assertTrue(repository.listPendingIngress(context).isEmpty())
        assertEquals("abcdef", repository.getSource(context, source.id)!!.content)
    }

    @Test
    fun commit_outOfOrderEventsCannotSkipUnprocessedPrefix() = runBlocking {
        val context = context()
        val first = source(context, "m1", "先到的消息")
        val second = source(context, "m2", "后到的消息")
        repository.commitJob(
            extractedJob(context, listOf(second.fullRef()), "later"),
            OWNER,
            MemoryCommitBatch()
        )
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        repository.commitJob(
            extractedJob(context, listOf(first.fullRef()), "earlier"),
            OWNER,
            MemoryCommitBatch()
        )
        assertEquals(2L, repository.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun job_reopenRetainsArtifactAndLease() = runBlocking {
        database.close()
        // Robolectric 已按测试隔离目录；短文件名避免 Windows native SQLite 的路径长度限制。
        val name = "reopen.db".also(databaseNames::add)
        openDatabase(name)
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        assertNull(repository.claimJob(jobId, "other"))
        database.close()
        openDatabase(name)
        now += 121_000L
        val resumed = repository.claimJob(jobId, "other")!!
        assertEquals(MemoryJobStatus.EXTRACTED, resumed.status)
        assertEquals(ARTIFACT, resumed.artifact)
        assertEquals(listOf(source.fullRef()), resumed.sources)
        repository.commitJob(jobId, "other", MemoryCommitBatch())
        assertEquals(MemoryJobStatus.COMMITTED, repository.getJob(jobId)!!.status)
    }

    @Test
    fun lease_serializesSameSpaceButAllowsDifferentCharacters() = runBlocking {
        val a = context()
        val b = context(characterId = "b")
        val aSource = source(a)
        val bSource = source(b)
        val aFirst = repository.planJob(a, "first", listOf(aSource.fullRef()))
        val aSecond = repository.planJob(a, "second", listOf(aSource.fullRef()))
        val bFirst = repository.planJob(b, "first", listOf(bSource.fullRef()))
        assertNotNull(repository.claimJob(aFirst.id, OWNER))
        assertNull(repository.claimJob(aSecond.id, "another"))
        assertNotNull(repository.claimJob(bFirst.id, "another"))
        assertTrue(repository.releaseJob(aFirst.id, OWNER, MemoryJobStatus.PAUSED))
        assertNotNull(repository.claimJob(aSecond.id, "another"))
    }

    @Test
    fun correction_manualEditPreservesIdAndRejectsLateExpectedRevision() = runBlocking {
        val context = context()
        val firstSource = source(context, "m1", "名字叫阿澄")
        val recordId = commitRecord(context, firstSource, "名字叫阿澄")
        val correctionSource = source(context, "m2", "名字改为阿橙")
        val jobId = extractedJob(
            context,
            listOf(correctionSource.fullRef()),
            "correction",
            mapOf(recordId to 1L)
        )
        assertTrue(
            repository.editRecord(
                context,
                recordId,
                1L,
                "用户手动更正：阿程"
            ) is MemoryWriteResult.Committed
        )
        val rejected = repository.commitJob(
            jobId, OWNER, MemoryCommitBatch(
                corrections = listOf(
                    MemoryRecordCorrection(
                        recordId,
                        1L,
                        "阿橙",
                        listOf(correctionSource.fullRef())
                    ),
                )
            )
        )
        assertEquals(MemoryWriteResult.Conflict(MemoryConflict.RECORD_CHANGED), rejected)
        val details = repository.getRecordDetails(context, recordId)!!
        assertEquals(recordId, details.record.id)
        assertEquals(2L, details.record.revision)
        assertEquals("用户手动更正：阿程", details.record.content)
        assertEquals(listOf(2L, 1L), details.revisions.map { it.revision })
        assertEquals("名字叫阿澄", details.revisions.last().content)
    }

    @Test
    fun correction_retryKeepsCommittedJobAfterItsTargetRevisionAdvanced() = runBlocking {
        val context = context()
        val recordId = commitRecord(context, source(context, "before-correction"), "旧地址")
        val source = source(context, "correction", "新地址")
        val refs = listOf(source.fullRef())
        val targets = mapOf(recordId to 1L)
        val jobId = extractedJob(context, refs, "stable-correction", targets)
        val batch = MemoryCommitBatch(
            corrections = listOf(
                MemoryRecordCorrection(recordId, 1L, "新地址", refs),
            )
        )
        assertTrue(repository.commitJob(jobId, OWNER, batch) is MemoryWriteResult.Committed)
        val retried = repository.planJob(context, "stable-correction", refs, targets)
        assertEquals(jobId, retried.id)
        assertEquals(MemoryJobStatus.COMMITTED, retried.status)
        assertTrue(
            (repository.commitJob(
                jobId,
                OWNER,
                batch
            ) as MemoryWriteResult.Committed).alreadyCommitted
        )
        val details = repository.getRecordDetails(context, recordId)!!
        assertEquals(2L, details.record.revision)
        assertEquals(2, details.revisions.size)
    }

    @Test
    fun commit_changedTargetSnapshotCannotBeRepublishedAsNewRecord() = runBlocking {
        val context = context()
        val recordId = commitRecord(context, source(context, "original"), "旧地址")
        val laterSource = source(context, "follow-up", "关于旧地址的后续讨论")
        val jobId =
            extractedJob(context, listOf(laterSource.fullRef()), "new-copy", mapOf(recordId to 1L))
        val previousCursor = repository.getCursor(context.spaceId)!!.committedSequence
        repository.editRecord(context, recordId, 1L, "用户确认的新地址")
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.RECORD_CHANGED),
            repository.commitJob(
                jobId, OWNER, MemoryCommitBatch(
                    records = listOf(
                        MemoryRecordDraft(
                            "copy",
                            "使用旧地址产生的新记忆",
                            listOf(laterSource.fullRef())
                        ),
                    )
                )
            ),
        )
        assertEquals(previousCursor, repository.getCursor(context.spaceId)!!.committedSequence)
        assertEquals("用户确认的新地址", repository.listRecords(context).single().content)
    }

    @Test
    fun persona_runtimeReadsAndCorrectionsCannotCrossDailyIdentities() = runBlocking {
        val a = context(persona = "戴着面具的旅人")
        val b = context(persona = "扮演的医生")
        assertEquals(a.spaceId, b.spaceId)
        val aSource = source(a, "persona-a", "旅人的住址")
        val recordId = commitRecord(a, aSource, "旅人住在 17 号")
        val bSource = source(b, "persona-b", "医生的住址")
        assertTrue(repository.listRecords(b).isEmpty())
        assertTrue(repository.observeRecords(b).first().isEmpty())
        assertNull(repository.getRecordDetails(b, recordId))
        assertNull(repository.getSource(b, aSource.id))
        assertEquals(
            listOf("persona-b"),
            repository.listPendingIngress(b).map { it.source.messageId })
        assertTrue(runCatching {
            repository.planJob(
                b,
                "cross-persona",
                listOf(bSource.fullRef()),
                mapOf(recordId to 1L)
            )
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.RECORD_CHANGED),
            repository.editRecord(b, recordId, 1L, "不应覆盖")
        )
        assertEquals(recordId, repository.listRecords(b.copy(conversationId = "")).single().id)
    }

    @Test
    fun manualEdit_fromArchivePreservesTheRecordsOriginalPersona() = runBlocking {
        val context = context(persona = "旅人")
        val source = source(context)
        val recordId = commitRecord(context, source, "旅人的经历")
        val management = context.copy(conversationId = "", personaKey = "unknown")
        repository.editRecord(management, recordId, 1L, "人工修订后的经历")
        val details = repository.getRecordDetails(management, recordId)!!
        assertEquals(context.personaKey, details.record.personaKey)
        assertEquals(context.personaKey, details.sources.single().personaKey)
    }

    @Test
    fun archiveContext_cannotPlanAutomaticCorrectionsAcrossPersonas() = runBlocking {
        val traveler = context(persona = "旅人")
        val doctor = context(persona = "医生")
        val travelerSource = source(traveler, "traveler", "旅人的经历")
        val doctorRecord = commitRecord(doctor, source(doctor, "doctor"), "医生的经历")
        repository.archiveConversation(traveler.conversationId)
        val management = traveler.copy(conversationId = "")
        assertEquals(doctorRecord, repository.listRecords(management).single().id)
        assertTrue(runCatching {
            repository.planJob(
                management, "archive-bypass", listOf(travelerSource.fullRef()),
                mapOf(doctorRecord to 1L),
            )
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(
            "医生的经历",
            repository.getRecordDetails(doctor, doctorRecord)!!.record.content
        )
    }

    @Test
    fun job_persistedWithoutRuntimeCannotClaimSaveOrCommit() = runBlocking {
        val context = context()
        val source = source(context)
        val job = repository.planJob(context, "missing-runtime", listOf(source.fullRef()))
        val dao = database.memoryArchiveDao()
        val unbound = dao.getJob(job.id)!!.copy(conversationId = "")
        dao.upsertJob(unbound)
        assertNull(repository.claimJob(job.id, OWNER))
        val extracting =
            unbound.copy(status = "EXTRACTING", leaseOwner = OWNER, leaseUntil = now + 120_000)
        dao.upsertJob(extracting)
        assertFalse(repository.saveExtraction(job.id, OWNER, ARTIFACT))
        dao.upsertJob(extracting.copy(status = "EXTRACTED", artifact = ARTIFACT))
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.SPACE_CHANGED),
            repository.commitJob(job.id, OWNER, MemoryCommitBatch()),
        )
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun observeRecords_rechecksRunAndCharacterChangesWithoutRoomWrites() = runBlocking {
        val context = context()
        val recordId = commitRecord(context, source(context), "角色经历")
        assertTrue(repository.observeRecords(context.copy(runId = "other-run")).first().isEmpty())
        assertTrue(
            repository.observeRecords(context.copy(scenarioId = "other-scene")).first().isEmpty()
        )
        val updates = Channel<List<MemoryRecord>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.observeRecords(context).collect { updates.send(it) }
        }
        try {
            assertEquals(recordId, withTimeout(5_000) { updates.receive() }.single().id)
            availableCharacters.value -= "a"
            assertTrue(withTimeout(5_000) { updates.receive() }.isEmpty())
            assertTrue(repository.listRecords(context).isEmpty())
            assertTrue(repository.observeRecords(context).first().isEmpty())
        } finally {
            collector.cancelAndJoin()
            updates.close()
        }
    }

    @Test
    fun sources_invalidCrossSpaceAndSplitSurrogateAreRejectedBeforePlanning() = runBlocking {
        val a = context()
        val b = context(characterId = "b")
        val source = source(a, body = "a😀b")
        assertTrue(runCatching {
            repository.planJob(
                b,
                "cross",
                listOf(source.fullRef())
            )
        }.isFailure)
        assertTrue(runCatching {
            repository.planJob(
                a,
                "split",
                listOf(source.fullRef().copy(end = 2))
            )
        }.isFailure)
        assertTrue(runCatching {
            repository.planJob(a, "empty-span", listOf(source.fullRef().copy(end = 0)))
        }.isFailure)
        assertTrue(repository.listRecoverableJobs(a.spaceId).isEmpty())
        assertTrue(repository.listRecords(a).isEmpty())
    }

    @Test
    fun delete_recordScrubsHistoryAndDerivedDataWithoutDeletingOriginalConversation() =
        runBlocking {
            val context = context()
            val source = source(context)
            val recordId = commitRecord(context, source, "要删除的经历")
            addDerived(context, recordId)
            val pendingSource = source(context, "later", "尚未提交")
            val pendingJob = extractedJob(context, listOf(pendingSource.fullRef()), "late")
            assertTrue(
                repository.deleteRecord(
                    context,
                    recordId,
                    1L
                ) is MemoryWriteResult.Committed
            )
            val dao = database.memoryArchiveDao()
            assertEquals("DELETED", dao.getRecord(recordId)!!.status)
            assertEquals("", dao.getRecord(recordId)!!.content)
            assertTrue(dao.listRevisions(recordId).isEmpty())
            assertTrue(dao.listEmbeddings(context.spaceId).isEmpty())
            assertEquals("", dao.listEventBoxes(context.spaceId).single().summary)
            assertEquals("", dao.listResidentEntries(context.spaceId).single().content)
            assertEquals("", dao.listCurrentStates(context.spaceId).single().content)
            assertEquals(MemoryJobStatus.INVALIDATED, repository.getJob(pendingJob)!!.status)
            // 代次变化撤销提交权限；不含已删除记录的其它来源产物仍留作历史。
            assertEquals(ARTIFACT, repository.getJob(pendingJob)!!.artifact)
            assertTrue(
                repository.commitJob(
                    pendingJob,
                    OWNER,
                    MemoryCommitBatch()
                ) is MemoryWriteResult.Conflict
            )
            assertEquals(2, store.listMessages(context.conversationId).size)
            assertNotNull(store.getConversation(context.conversationId))
        }

    @Test
    fun source_editInvalidatesItsDerivedFactsWithoutTouchingOtherCharacter() = runBlocking {
        val a = context()
        val b = context(characterId = "b")
        val aSource = source(a, "a-message", "旧地址")
        val bSource = source(b, "b-message", "另一角色地址")
        val aId = commitRecord(a, aSource, "A 的旧地址")
        val bId = commitRecord(b, bSource, "B 的地址")
        addDerived(a, aId)
        store.upsertMessages(listOf(message(a, "a-message", "新地址")))
        val dao = database.memoryArchiveDao()
        assertEquals(MemorySourceStatus.CHANGED, repository.getSource(a, aSource.id)!!.status)
        assertEquals(MemoryRecordStatus.STALE, repository.getRecordDetails(a, aId)!!.record.status)
        assertEquals("STALE", dao.listEventBoxes(a.spaceId).single().summaryStatus)
        assertEquals("STALE", dao.listResidentEntries(a.spaceId).single().status)
        assertTrue(dao.listEmbeddings(a.spaceId).isEmpty())
        assertEquals("B 的地址", repository.getRecordDetails(b, bId)!!.record.content)
        assertEquals(MemoryRecordStatus.ACTIVE, repository.getRecordDetails(b, bId)!!.record.status)
        val replacement = repository.listPendingIngress(a).single().source
        assertNotEquals(aSource.id, replacement.id)
        assertEquals(2L, replacement.messageRevision)
        assertEquals("新地址", replacement.content)
    }

    @Test
    fun source_timestampEditRevisionsAndRejectsOldTask() = runBlocking {
        val context = context()
        val source = source(context, "timed", "明天在 706 号见面")
        val jobId = extractedJob(context, listOf(source.fullRef()))
        val original = store.listMessages(context.conversationId).single()
        val changedTime = original.createdAt + 86_400_000L
        store.upsertMessages(listOf(original.copy(createdAt = changedTime)))
        val replacement = repository.listPendingIngress(context).single().source
        assertEquals(source.messageRevision + 1, replacement.messageRevision)
        assertEquals(changedTime, replacement.occurredAt)
        assertEquals(source.content, replacement.content)
        assertEquals(MemorySourceStatus.CHANGED, repository.getSource(context, source.id)!!.status)
        assertEquals(MemoryJobStatus.INVALIDATED, repository.getJob(jobId)!!.status)
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.JOB_CHANGED),
            repository.commitJob(jobId, OWNER, MemoryCommitBatch()),
        )
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
        assertEquals(
            replacement.messageRevision,
            store.listMessages(context.conversationId).single().memoryRevision
        )
    }

    @Test
    fun source_completionKeepsOriginalPersonaAndAudience() = runBlocking {
        val original = context(persona = "旅人")
        store.upsertMessages(
            listOf(
                message(
                    original,
                    "streaming",
                    "未完成的敏感片段",
                    MessageStatus.LOADING
                )
            )
        )
        val receipt =
            database.memoryArchiveDao().listMessageSources(original.conversationId, "streaming")
                .single()
        assertEquals("", receipt.content)
        assertEquals("UNKNOWN", receipt.status)
        assertTrue(repository.listPendingIngress(original).isEmpty())
        val current = repository.bindConversation(
            RoleplayScenario(
                id = original.scenarioId,
                assistantId = "a",
                memoryPurpose = MemoryPurpose.CONTINUOUS,
                userPersonaOverride = "医生",
            ),
            original.conversationId, listOf("a", "b"), original.runId,
        )
        store.upsertMessages(
            listOf(
                store.listMessages(original.conversationId).single().copy(
                    content = "旅人身份下开始的完整回复", status = MessageStatus.COMPLETED,
                )
            )
        )
        current.forEach { assertTrue(repository.listPendingIngress(it).isEmpty()) }
        val management = current.single { it.characterId == "a" }.copy(conversationId = "")
        val evidence = repository.listPendingIngress(management).single().source
        assertEquals(original.personaKey, evidence.personaKey)
        assertEquals(listOf("a"), evidence.visibleCharacterIds)
        assertEquals("旅人身份下开始的完整回复", evidence.content)
        assertEquals(2L, evidence.messageRevision)
    }

    @Test
    fun source_editKeepsOriginalPersonaAfterBindingChanges() = runBlocking {
        val original = context(persona = "旅人")
        val evidence = source(original, "old-persona", "旅人的住址")
        val current = repository.bindConversation(
            RoleplayScenario(
                id = original.scenarioId,
                assistantId = "a",
                memoryPurpose = MemoryPurpose.CONTINUOUS,
                userPersonaOverride = "医生",
            ),
            original.conversationId, listOf("a"), original.runId,
        ).single()
        val message = store.listMessages(original.conversationId).single()
        store.upsertMessages(listOf(message.copy(content = "旅人修订后的住址")))
        assertTrue(repository.listPendingIngress(current).isEmpty())
        val replacement =
            repository.listPendingIngress(current.copy(conversationId = "")).single().source
        assertEquals(evidence.personaKey, replacement.personaKey)
        assertEquals("旅人修订后的住址", replacement.content)
        assertEquals(2L, replacement.messageRevision)
        assertTrue(runCatching {
            repository.planJob(current, "persona-edit", listOf(replacement.fullRef()))
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun source_editDoesNotExposeHistoryToNewParticipants() = runBlocking {
        val a = context()
        source(a, "before-join", "加入前的消息")
        val contexts = repository.bindConversation(
            RoleplayScenario(
                id = a.scenarioId,
                assistantId = "a",
                memoryPurpose = MemoryPurpose.CONTINUOUS
            ),
            a.conversationId, listOf("a", "b"), a.runId,
        )
        val b = contexts.single { it.characterId == "b" }
        val original = store.listMessages(a.conversationId).single()
        store.upsertMessages(listOf(original.copy(content = "修订加入前的消息")))
        assertTrue(repository.listPendingIngress(b).isEmpty())
        assertEquals(
            listOf("a"),
            repository.listPendingIngress(a).single().source.visibleCharacterIds
        )
        source(a, "after-join", "加入后的消息")
        val shared = repository.listPendingIngress(b).single().source
        assertEquals("after-join", shared.messageId)
        assertEquals(listOf("a", "b"), shared.visibleCharacterIds)
    }

    @Test
    fun source_deleteAfterEditScrubsStaleRecordAndItsHistory() = runBlocking {
        val context = context()
        val source = source(context, "edited-then-deleted", "旧消息")
        val recordId = commitRecord(context, source, "旧记忆")
        store.upsertMessages(
            listOf(
                store.listMessages(context.conversationId).single().copy(content = "新消息")
            )
        )
        assertEquals(
            MemoryRecordStatus.STALE,
            repository.getRecordDetails(context, recordId)!!.record.status
        )
        store.deleteMessageWithAssociations(
            store.getConversation(context.conversationId)!!,
            context.conversationId,
            source.messageId,
        )
        val dao = database.memoryArchiveDao()
        assertEquals("DELETED", dao.getRecord(recordId)!!.status)
        assertEquals("", dao.getRecord(recordId)!!.content)
        assertTrue(dao.listRevisions(recordId).isEmpty())
        assertEquals(MemorySourceStatus.DELETED, repository.getSource(context, source.id)!!.status)
    }

    @Test
    fun source_clearConversationPreservesIndependentManualRevision() = runBlocking {
        val context = context()
        val source = source(context)
        val recordId = commitRecord(context, source, "自动提取的旧版")
        repository.editRecord(context, recordId, 1L, "用户独立确认后的新版")
        store.clearMessages(context.conversationId)
        val current = repository.getRuntimeContext(context.conversationId, context.characterId)!!
        val details = repository.getRecordDetails(current, recordId)!!
        assertEquals(2L, details.record.revision)
        assertEquals(MemoryRecordStatus.ACTIVE, details.record.status)
        assertEquals("用户独立确认后的新版", details.record.content)
        assertEquals("用户独立确认后的新版", details.sources.single().content)
        assertTrue(store.listMessages(context.conversationId).isEmpty())
    }

    @Test
    fun source_clearConversationAlsoRemovesLegacyCompatibilityCopy() = runBlocking {
        val context = context()
        val source = source(context)
        val recordId = commitRecord(context, source, "旧兼容记忆")
        val dao = database.memoryArchiveDao()
        dao.upsertRecord(
            dao.getRecord(recordId)!!.copy(
                legacyScopeType = "conversation", legacyScopeId = context.conversationId,
                legacySourceMessageId = source.messageId,
            )
        )
        database.memoryDao().upsertMemoryEntry(
            MemoryEntryEntity(
                id = recordId,
                scopeType = "conversation",
                scopeId = context.conversationId,
                characterId = context.characterId,
                sourceMessageId = source.messageId,
                content = "旧兼容记忆",
            )
        )
        store.clearMessages(context.conversationId)
        assertTrue(database.memoryDao().listMemoryEntries().isEmpty())
        assertEquals("DELETED", dao.getRecord(recordId)!!.status)
    }

    @Test
    fun source_changesInvalidateOnlyItsPendingProposals() = runBlocking {
        val context = context()
        val original = source(context, "proposal-origin", "旧事实")
        val unrelated = source(context, "other-origin", "另一事实")
        val dao = database.memoryArchiveDao()
        listOf(original, unrelated).forEach { evidence ->
            dao.upsertProposal(
                MemoryProposalEntity(
                    id = evidence.id,
                    spaceId = context.spaceId,
                    characterId = context.characterId,
                    conversationId = context.conversationId,
                    generation = context.generation,
                    personaKey = context.personaKey,
                    content = evidence.content,
                    kind = "FACT",
                    category = "DAILY",
                    sourceRefsJson = AppJson.gson.toJson(listOf(evidence.fullRef())),
                    status = "PENDING",
                    finalRecordId = null,
                    createdAt = now,
                    decidedAt = null,
                )
            )
        }
        store.upsertMessages(listOf(store.listMessages(context.conversationId).single {
            it.id == original.messageId
        }.copy(content = "更正后的事实")))
        val proposals = dao.listProposals(context.spaceId).associateBy { it.id }
        assertEquals("INVALIDATED", proposals.getValue(original.id).status)
        assertEquals("", proposals.getValue(original.id).content)
        assertEquals("[]", proposals.getValue(original.id).sourceRefsJson)
        assertEquals("PENDING", proposals.getValue(unrelated.id).status)
        assertEquals(unrelated.content, proposals.getValue(unrelated.id).content)
    }

    @Test
    fun source_deletingThenReusingMessageIdCannotReviveOldJobOrRecord() = runBlocking {
        val context = context()
        val source = source(context, "reused", "原文")
        val recordId = commitRecord(context, source, "旧记忆")
        val jobId = extractedJob(context, listOf(source.fullRef()), "in-flight")
        store.deleteMessageWithAssociations(
            store.getConversation(context.conversationId)!!,
            context.conversationId,
            "reused"
        )
        assertEquals("DELETED", database.memoryArchiveDao().getRecord(recordId)!!.status)
        assertTrue(database.memoryArchiveDao().listRevisions(recordId).isEmpty())
        assertEquals(MemoryJobStatus.INVALIDATED, repository.getJob(jobId)!!.status)
        val replacement = source(context, "reused", "原文")
        assertEquals(2L, replacement.messageRevision)
        assertNotEquals(source.id, replacement.id)
        assertTrue(
            repository.commitJob(
                jobId, OWNER, MemoryCommitBatch(
                    records = listOf(
                        MemoryRecordDraft("one", "不能复活", listOf(source.fullRef())),
                    )
                )
            ) is MemoryWriteResult.Conflict
        )
        assertEquals("", database.memoryArchiveDao().getRecord(recordId)!!.content)
    }

    @Test
    fun source_directDatabaseChangeIsRecheckedAtCommit() = runBlocking {
        val context = context()
        val source = source(context, "m1", "原来的消息")
        val jobId = extractedJob(context, listOf(source.fullRef()))
        database.openHelper.writableDatabase.execSQL("UPDATE messages SET content = 'changed outside store' WHERE id = 'm1'")
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.SOURCE_CHANGED),
            repository.commitJob(jobId, OWNER, MemoryCommitBatch())
        )
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun source_directTimestampChangeIsRecheckedAtCommit() = runBlocking {
        val context = context()
        val source = source(context, "time-anchor")
        val jobId = extractedJob(context, listOf(source.fullRef()))
        database.openHelper.writableDatabase.execSQL("UPDATE messages SET createdAt = createdAt + 1 WHERE id = 'time-anchor'")
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.SOURCE_CHANGED),
            repository.commitJob(jobId, OWNER, MemoryCommitBatch()),
        )
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun character_removedAfterExtractionCannotCommitAndClosingSpacesRetainsArchive() = runBlocking {
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        availableCharacters.value -= "a"
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.SPACE_CHANGED),
            repository.commitJob(jobId, OWNER, MemoryCommitBatch())
        )
        assertNull(repository.getRuntimeContext(context.conversationId, "a"))
        repository.closeCharacterSpaces("a")
        assertEquals("ARCHIVED", database.memoryArchiveDao().getSpace(context.spaceId)!!.status)
        assertTrue(
            database.memoryArchiveDao().getSpace(context.spaceId)!!.generation > context.generation
        )
        assertEquals(MemoryJobStatus.INVALIDATED, repository.getJob(jobId)!!.status)
        assertEquals("完整经历", store.listMessages(context.conversationId).single().content)
    }

    @Test
    fun cancellation_outerTransactionRollsBackCompletedBatch() = runBlocking {
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        val failure = runCatching {
            database.withTransaction {
                repository.commitJob(
                    jobId,
                    OWNER,
                    MemoryCommitBatch(
                        records = listOf(
                            MemoryRecordDraft(
                                "one",
                                "经历",
                                listOf(source.fullRef())
                            )
                        )
                    )
                )
                throw CancellationException("synthetic cancellation")
            }
        }.exceptionOrNull()
        assertTrue(failure is CancellationException)
        assertTrue(repository.listRecords(context).isEmpty())
        assertEquals(MemoryJobStatus.EXTRACTED, repository.getJob(jobId)!!.status)
        assertEquals(0L, repository.getCursor(context.spaceId)!!.committedSequence)
    }

    @Test
    fun relations_invalidSelfReferenceRejectsEntireBatchAndValidIntentIsAtomic() = runBlocking {
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        val records = listOf(
            MemoryRecordDraft("a", "起因", listOf(source.fullRef())),
            MemoryRecordDraft("b", "结果", listOf(source.fullRef()))
        )
        assertEquals(
            MemoryWriteResult.Conflict(MemoryConflict.INVALID_INPUT), repository.commitJob(
                jobId, OWNER,
                MemoryCommitBatch(
                    records = records,
                    relations = listOf(MemoryRelationDraft("a", targetLocalId = "a"))
                )
            )
        )
        assertTrue(repository.listRecords(context).isEmpty())
        repository.commitJob(
            jobId,
            OWNER,
            MemoryCommitBatch(
                records = records,
                relations = listOf(MemoryRelationDraft("b", targetLocalId = "a"))
            )
        )
        val relation = database.memoryArchiveDao().listPendingRelations(context.spaceId).single()
        assertEquals(context.spaceId, relation.spaceId)
        assertNotEquals(relation.fromRecordId, relation.targetRecordId)
        assertEquals(1L, relation.fromRevision)
        assertEquals(1L, relation.targetRevision)
    }

    @Test
    fun delete_recordScrubsDerivedRelationNameAndKeepsOtherRecord() = runBlocking {
        val context = context()
        val source = source(context)
        val jobId = extractedJob(context, listOf(source.fullRef()))
        repository.commitJob(
            jobId, OWNER, MemoryCommitBatch(
                records = listOf(
                    MemoryRecordDraft("first", "旧地址", listOf(source.fullRef())),
                    MemoryRecordDraft("second", "后来搬家", listOf(source.fullRef())),
                ),
                relations = listOf(
                    MemoryRelationDraft(
                        "second",
                        targetLocalId = "first",
                        eventName = "住在 706 号的事件"
                    )
                ),
            )
        )
        val relation = database.memoryArchiveDao().listPendingRelations(context.spaceId).single()
        repository.deleteRecord(context, relation.targetRecordId, 1L)
        database.openHelper.readableDatabase.query("SELECT eventName, status FROM memory_relation_intents")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("", cursor.getString(0))
                assertEquals("INVALIDATED", cursor.getString(1))
            }
        assertEquals(
            "后来搬家",
            database.memoryArchiveDao().getRecord(relation.fromRecordId)!!.content
        )
    }

    private fun openDatabase(name: String? = null) {
        if (name != null) application.getDatabasePath(name).parentFile?.mkdirs()
        database =
            (if (name == null) Room.inMemoryDatabaseBuilder(application, ChatDatabase::class.java)
            else Room.databaseBuilder(application, ChatDatabase::class.java, name))
                .allowMainThreadQueries().build()
        store = RoomConversationStore(database) { now }
        repository = RoomMemoryArchiveRepository(
            database, { now }, { it in availableCharacters.value },
            { characterId -> availableCharacters.map { characterId in it } },
        )
    }

    private suspend fun context(
        characterId: String = "a",
        persona: String = ""
    ): MemoryRuntimeContext {
        val conversationId = UUID.randomUUID().toString()
        store.upsertConversationMetadata(
            Conversation(
                conversationId,
                "合成对话",
                "",
                1,
                1,
                assistantId = characterId
            )
        )
        return repository.bindConversation(
            RoleplayScenario(
                id = "scene-$conversationId",
                assistantId = characterId,
                userPersonaOverride = persona,
                memoryPurpose = MemoryPurpose.CONTINUOUS
            ),
            conversationId, listOf(characterId), "run-$conversationId",
        ).single()
    }

    private fun message(
        context: MemoryRuntimeContext,
        id: String,
        body: String,
        status: MessageStatus = MessageStatus.COMPLETED
    ) =
        ChatMessage(id, context.conversationId, MessageRole.USER, body, status, createdAt = now)

    private suspend fun source(
        context: MemoryRuntimeContext,
        id: String = UUID.randomUUID().toString(),
        body: String = "完整经历"
    ): MemorySourceEvidence {
        store.upsertMessages(listOf(message(context, id, body)))
        return repository.listPendingIngress(context).single { it.source.messageId == id }.source
    }

    private suspend fun extractedJob(
        context: MemoryRuntimeContext,
        refs: List<MemorySourceRef>,
        key: String = UUID.randomUUID().toString(),
        targets: Map<String, Long> = emptyMap(),
    ): String {
        val job = repository.planJob(context, key, refs, targets)
        assertNotNull(repository.claimJob(job.id, OWNER))
        assertTrue(repository.saveExtraction(job.id, OWNER, ARTIFACT))
        return job.id
    }

    private suspend fun commitRecord(
        context: MemoryRuntimeContext,
        source: MemorySourceEvidence,
        content: String
    ): String {
        val jobId = extractedJob(context, listOf(source.fullRef()))
        return (repository.commitJob(
            jobId, OWNER, MemoryCommitBatch(
                records = listOf(
                    MemoryRecordDraft("record", content, listOf(source.fullRef())),
                )
            )
        ) as MemoryWriteResult.Committed).recordIds.single()
    }

    private suspend fun addDerived(context: MemoryRuntimeContext, recordId: String) {
        val dao = database.memoryArchiveDao()
        dao.upsertEventBox(
            MemoryEventBoxEntity(
                "event",
                context.spaceId,
                context.characterId,
                "合成事件",
                "旧摘要",
                1,
                "READY",
                "ACTIVE",
                "未完成",
                1,
                now,
                now
            )
        )
        dao.upsertEventMember(MemoryEventMemberEntity("event", recordId, 1, "ARCHIVED", now))
        dao.upsertResidentEntry(
            MemoryResidentEntryEntity(
                "resident",
                context.spaceId,
                context.characterId,
                "USER",
                context.personaKey,
                "旧认知",
                1,
                "READY",
                false,
                null,
                false,
                now,
                now
            )
        )
        dao.upsertCurrentState(
            MemoryCurrentStateEntity(
                "state",
                context.spaceId,
                context.characterId,
                "mood",
                context.personaKey,
                "旧状态",
                1,
                "ACTIVE",
                null,
                null,
                now,
                now
            )
        )
        dao.upsertDerivedLink(MemoryDerivedLinkEntity("RESIDENT", "resident", recordId, 1))
        dao.upsertDerivedLink(MemoryDerivedLinkEntity("STATE", "state", recordId, 1))
        dao.upsertEmbedding(
            MemoryEmbeddingEntity(
                recordId,
                1,
                0,
                "synthetic-model",
                1,
                context.spaceId,
                1,
                0,
                1,
                byteArrayOf(0, 0, 0, 0),
                "READY",
                now
            )
        )
    }

    private companion object {
        const val OWNER = "test-worker"
        const val ARTIFACT =
            "{\"schema_version\":2,\"memories\":[],\"corrections\":[],\"state_updates\":[],\"resident_candidates\":[]}"
    }
}
