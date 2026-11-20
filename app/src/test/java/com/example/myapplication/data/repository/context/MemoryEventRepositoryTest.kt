package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.conversation.MemoryEventSummaryBatches
import com.example.myapplication.data.local.memory.MemoryRelationIntentEntity
import com.example.myapplication.data.local.memory.MemoryEventMemberEntity
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
class MemoryEventRepositoryTest {
    @Test
    fun committedRelations_areConsumedOnceWithoutMatchingTitles() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val first = f.remember(context, "芮珩的罗盘需要修复")
            f.add(context, "progress", "找到了706号齿轮")
            val source = f.archive.listPendingIngress(context).single().source
            val job = f.archive.planJob(
                context,
                "event-intent",
                listOf(source.fullRef()),
                mapOf(first.id to first.revision)
            )
            f.archive.claimJob(job.id, "owner")
            f.archive.saveExtraction(job.id, "owner", "{\"synthetic\":true}")
            val result = f.archive.commitJob(
                job.id, "owner", MemoryCommitBatch(
                    records = listOf(
                        MemoryRecordDraft(
                            "N0",
                            "找到了706号齿轮",
                            listOf(source.fullRef())
                        )
                    ),
                    relations = listOf(
                        MemoryRelationDraft(
                            "N0",
                            targetRecordId = first.id,
                            eventName = "罗盘"
                        )
                    ),
                )
            ) as MemoryWriteResult.Committed
            val consumed = f.archive.consumeEventRelations(context)
            assertEquals(1, consumed.applied)
            val event = f.archive.getEventDetails(context, consumed.eventIds.single())!!
            assertEquals(
                setOf(first.id, result.recordIds.single()),
                event.members.map { it.record.id }.toSet()
            )
            assertTrue(event.members.all { it.coveredRevision == null && it.status == MemoryEventMemberStatus.LIVE })
            assertEquals(0, f.archive.consumeEventRelations(context).applied)
            val second = f.createEvent(
                context,
                listOf("另一只罗盘在海边遗失", "与修理无关，正在海边寻找"),
                "罗盘"
            ).first
            assertNotEquals(event.event.id, second.id)
            assertEquals(2, f.archive.listEvents(context).size)
        }
    }

    @Test
    fun rejectedLinks_doNotMutateAnyBox() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val contexts = f.contexts(listOf("a", "b"), persona = "甲身份")
            val a = contexts.first { it.characterId == "a" }
            val b = contexts.first { it.characterId == "b" }
            val (event, records) = f.createEvent(a)
            val foreign = f.remember(b, "乙的私有经历")
            val otherPersona = f.contexts(persona = "乙身份").single()
            val foreignPersona = f.remember(otherPersona, "另一身份的经历")
            val otherSpace =
                f.contexts(persona = "甲身份", purpose = MemoryPurpose.INDEPENDENT).single()
            val isolated = f.remember(otherSpace, "另一剧情的经历")
            val before = f.archive.getEventDetails(a, event.id)
            listOf(
                f.archive.linkEventMembers(a, version(records[0]), version(records[0])),
                f.archive.linkEventMembers(a, version(records[0]), version(foreign)),
                f.archive.linkEventMembers(a, version(records[0]), version(foreignPersona)),
                f.archive.linkEventMembers(a, version(records[0]), version(isolated)),
                f.archive.linkEventMembers(
                    a,
                    version(records[0]).copy(revision = 8),
                    version(records[1])
                ),
            ).forEach { assertTrue(it is MemoryEventMutationResult.Conflict) }
            val duplicate = f.archive.linkEventMembers(
                a,
                version(records[1]),
                version(records[0]),
                "不能借重复关联改名"
            ) as MemoryEventMutationResult.Updated
            assertTrue(duplicate.alreadyApplied)
            assertEquals(before, f.archive.getEventDetails(a, event.id))
            assertNull(f.archive.getEventDetails(b, event.id))
            assertNull(f.archive.getEventDetails(otherPersona.copy(conversationId = ""), event.id))
        }
    }

    @Test
    fun differentEventLinks_rejectWithoutMergingEitherHistory() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (first, firstRecords) = f.createEvent(
                context,
                listOf("北岸罗盘送修", "北岸罗盘归还"),
                "罗盘"
            )
            val (second, secondRecords) = f.createEvent(
                context,
                listOf("南岸罗盘遗失", "南岸罗盘找到"),
                "罗盘"
            )
            val before = f.archive.listEvents(context)
                .associate { it.id to f.archive.getEventDetails(context, it.id) }

            val linked = f.archive.linkEventMembers(
                context,
                version(firstRecords.first()),
                version(secondRecords.first()),
                "仍不能按同名合并"
            )

            assertEquals(
                MemoryEventMutationResult.Conflict(MemoryEventConflict.DIFFERENT_EVENTS),
                linked
            )
            f.database.memoryArchiveDao().insertRelation(
                MemoryRelationIntentEntity(
                    "different-event-intent", context.spaceId, context.characterId,
                    firstRecords.first().id, firstRecords.first().revision,
                    secondRecords.first().id, secondRecords.first().revision,
                    "same_event", "罗盘", "PENDING", f.now
                )
            )
            val consumed = f.archive.consumeEventRelations(context)
            assertEquals(0, consumed.applied)
            assertEquals(1, consumed.ignored)
            assertTrue(consumed.eventIds.isEmpty())
            assertEquals(
                setOf(first.id, second.id),
                f.archive.listEvents(context).map { it.id }.toSet()
            )
            assertEquals(
                before,
                f.archive.listEvents(context)
                    .associate { it.id to f.archive.getEventDetails(context, it.id) })
            assertTrue(
                f.database.memoryArchiveDao().listPendingRelations(context.spaceId).isEmpty()
            )
        }
    }

    @Test
    fun invalidIntents_neverCreateMemberships() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val a = f.remember(context, "第一件事")
            val b = f.remember(context, "第二件事")
            val dao = f.database.memoryArchiveDao()
            dao.insertRelation(
                MemoryRelationIntentEntity(
                    "self",
                    context.spaceId,
                    "a",
                    a.id,
                    1,
                    a.id,
                    1,
                    "same_event",
                    "自环",
                    "PENDING",
                    f.now
                )
            )
            dao.insertRelation(
                MemoryRelationIntentEntity(
                    "label",
                    context.spaceId,
                    "a",
                    a.id,
                    1,
                    b.id,
                    1,
                    "same_topic",
                    "同主题",
                    "PENDING",
                    f.now
                )
            )
            dao.insertRelation(
                MemoryRelationIntentEntity(
                    "stale",
                    context.spaceId,
                    "a",
                    a.id,
                    1,
                    b.id,
                    9,
                    "same_event",
                    "旧版",
                    "PENDING",
                    f.now
                )
            )
            val result = f.archive.consumeEventRelations(context)
            assertEquals(0, result.applied)
            assertEquals(3, result.ignored)
            assertTrue(f.archive.listEvents(context).isEmpty())
            assertTrue(dao.listPendingRelations(context.spaceId).isEmpty())
        }
    }

    @Test
    fun onlyCompletedAllBatches_publishCoverageAtomically() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val job = planned(f, context, event.id)
            f.archive.claimJob(job.id, "owner")
            assertFalse(f.archive.beginEventSummaryBatch(job.id, "owner", 1))
            assertTrue(f.archive.beginEventSummaryBatch(job.id, "owner", 0))
            assertFalse(f.archive.beginEventSummaryBatch(job.id, "owner", 0))
            assertTrue(f.archive.commitEventSummary(job.id, "owner") is MemoryWriteResult.Conflict)
            val response =
                memoryEventResponse(MemoryEventSummaryBatches.readWork(job.requestJson).snapshot)
            assertFalse(f.archive.saveEventSummaryBatch(job.id, "other", 0, response))
            assertTrue(f.archive.saveEventSummaryBatch(job.id, "owner", 0, response))
            assertTrue(f.archive.saveEventSummaryBatch(job.id, "owner", 0, response))
            assertTrue(
                f.archive.commitJob(
                    job.id,
                    "owner",
                    MemoryCommitBatch()
                ) is MemoryWriteResult.Conflict
            )
            assertEquals(
                MemoryEventSummaryStatus.EMPTY,
                f.archive.getEventDetails(context, event.id)!!.event.summaryStatus
            )
            val cursor = f.archive.getCursor(context.spaceId)
            assertTrue(f.archive.commitEventSummary(job.id, "owner") is MemoryWriteResult.Committed)
            val result = f.archive.getEventDetails(context, event.id)!!
            assertEquals(1L, result.event.summaryRevision)
            assertTrue(result.members.all { it.coveredRevision == it.record.revision && it.status == MemoryEventMemberStatus.ARCHIVED })
            assertEquals(cursor, f.archive.getCursor(context.spaceId))
            assertTrue(
                (f.archive.commitEventSummary(
                    job.id,
                    "other"
                ) as MemoryWriteResult.Committed).alreadyCommitted
            )
            assertEquals(
                records.map { it.content },
                result.members.sortedBy { records.indexOfFirst { record -> record.id == it.record.id } }
                    .map { it.record.content })
        }
    }

    @Test
    fun commitFailure_rollsBackSummaryAndAllMemberships() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val job = saved(f, context, event.id)
            f.database.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_event_member BEFORE UPDATE ON memory_event_members WHEN NEW.recordId = '${records.last().id}' BEGIN SELECT RAISE(ABORT, 'synthetic transaction failure'); END")
            assertTrue(runCatching { f.archive.commitEventSummary(job.id, "owner") }.isFailure)
            val rolledBack = f.archive.getEventDetails(context, event.id)!!
            assertEquals("", rolledBack.event.summary)
            assertEquals(0L, rolledBack.event.summaryRevision)
            assertTrue(rolledBack.members.all { it.status == MemoryEventMemberStatus.LIVE && it.coveredRevision == null })
            assertEquals(MemoryJobStatus.EXTRACTED, f.archive.getJob(job.id)!!.status)
            assertTrue(f.archive.getJob(job.id)!!.artifact.isNotBlank())
            f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_event_member")
            assertTrue(f.archive.commitEventSummary(job.id, "owner") is MemoryWriteResult.Committed)
            assertEquals(
                MemoryEventSummaryStatus.READY,
                f.archive.getEventDetails(context, event.id)!!.event.summaryStatus
            )
        }
    }

    @Test
    fun addedMember_doesNotEnterAnInflightSnapshotCoverage() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val latest = f.remember(context, "11月5日归还之后发现新线索")
            val job = saved(f, context, event.id)
            f.archive.linkEventMembers(context, version(latest), version(records.first()))
            assertTrue(f.archive.commitEventSummary(job.id, "owner") is MemoryWriteResult.Committed)
            val current = f.archive.getEventDetails(context, event.id)!!
            assertEquals(event.generation, current.event.generation)
            assertEquals(4, current.members.count { it.status == MemoryEventMemberStatus.ARCHIVED })
            assertEquals(null, current.members.single { it.record.id == latest.id }.coveredRevision)
            assertEquals(
                MemoryEventMemberStatus.LIVE,
                current.members.single { it.record.id == latest.id }.status
            )
            val conclusion = f.archive.listEventConclusions(context).single()
            assertEquals(records.map(::version).toSet(), conclusion.coveredRecords.toSet())
            assertFalse(conclusion.event.summary.contains("新线索"))
        }
    }

    @Test
    fun editAndUnlink_invalidatePriorCoverageAndLateWork() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            f.eventCoordinator(ai).processAutomatic(context)
            val job = (f.archive.planEventSummary(
                context,
                event.id,
                MemoryEventSummaryOptions(
                    manualRebuild = true,
                    expectedGeneration = event.generation
                )
            ) as MemoryEventPlanResult.Planned).job
            savePlanned(f, job)
            f.archive.editRecord(context, records.first().id, 1, "纠正：委托者是阿澄")
            assertTrue(f.archive.commitEventSummary(job.id, "owner") is MemoryWriteResult.Conflict)
            val edited = f.archive.getEventDetails(context, event.id)!!
            assertEquals("", edited.event.summary)
            assertTrue(edited.members.all { it.coveredRevision == null && it.status == MemoryEventMemberStatus.LIVE })
            assertTrue(f.archive.getRecallSnapshot(context)!!.events.isEmpty())
            val unlinked = f.archive.unlinkEventMember(
                context,
                event.id,
                records.last().id,
                edited.event.generation
            ) as MemoryEventMutationResult.Updated
            assertEquals(edited.event.generation + 1, unlinked.generation)
            assertEquals(3, f.archive.getEventDetails(context, event.id)!!.members.size)
            assertEquals(
                records.last().content,
                f.archive.getRecordDetails(context, records.last().id)!!.record.content
            )
            val again = f.archive.unlinkEventMember(
                context,
                event.id,
                records.last().id,
                unlinked.generation
            ) as MemoryEventMutationResult.Updated
            assertTrue(again.alreadyApplied)
        }
    }

    @Test
    fun sourceDeletion_erasesDerivedBodiesButPreservesOtherEvents() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, records) = f.createEvent(context)
            val other = f.createEvent(
                context,
                listOf("另一事件一", "另一事件二", "另一事件三", "另一事件四"),
                "其它事件"
            ).first
            val ai = RecordingMemoryEventAi()
            f.eventCoordinator(ai).processAutomatic(context)
            val beforeOther = f.archive.getEventDetails(context, other.id)
            val job = (f.archive.planEventSummary(
                context,
                event.id,
                MemoryEventSummaryOptions(
                    manualRebuild = true,
                    expectedGeneration = event.generation
                )
            ) as MemoryEventPlanResult.Planned).job
            savePlanned(f, job)
            val source = f.archive.getRecordDetails(context, records.first().id)!!.sources.single()
            val conversation = f.store.getConversation(context.conversationId)!!
            f.store.deleteMessageWithAssociations(
                conversation,
                context.conversationId,
                source.messageId
            )
            assertEquals("", f.archive.getJob(job.id)!!.artifact)
            assertEquals("", f.archive.getJob(job.id)!!.requestJson)
            assertEquals(MemoryRequestState.STARTED, f.archive.getJob(job.id)!!.requestState)
            val current = f.archive.getEventDetails(context, event.id)!!
            assertEquals("", current.event.summary)
            assertEquals("", current.event.currentState)
            assertEquals("", current.event.title)
            assertEquals(3, current.members.size)
            assertEquals(beforeOther, f.archive.getEventDetails(context, other.id))
            assertEquals(
                MemoryRecordStatus.DELETED,
                f.archive.getRecordDetails(context, records.first().id)!!.record.status
            )
        }
    }

    @Test
    fun archive_preservesValidHistoryAndBlocksLateRequests() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            val ai = RecordingMemoryEventAi()
            f.eventCoordinator(ai).processAutomatic(context)
            val before = f.archive.getEventDetails(context, event.id)!!
            val result = f.archive.setEventArchived(
                context,
                event.id,
                before.event.generation,
                true
            ) as MemoryEventMutationResult.Updated
            val after = f.archive.getEventDetails(context, event.id)!!
            assertEquals(before.event.summary, after.event.summary)
            assertEquals(before.members, after.members)
            assertEquals(MemoryEventStatus.ARCHIVED, after.event.status)
            assertEquals(
                MemoryEventPlanResult.NotNeeded,
                f.archive.planEventSummary(context, event.id)
            )
            assertEquals(1, f.archive.getRecallSnapshot(context)!!.events.size)
            assertTrue(
                (f.archive.setEventArchived(
                    context,
                    event.id,
                    result.generation,
                    true
                ) as MemoryEventMutationResult.Updated).alreadyApplied
            )
        }
    }

    @Test
    fun noManagementContextCanCreateAutomaticSummaryJob() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            val (event, _) = f.createEvent(context)
            assertTrue(
                f.archive.planEventSummary(
                    context.copy(conversationId = ""),
                    event.id
                ) is MemoryEventPlanResult.Conflict
            )
            val wrong = context.copy(personaKey = "other-persona")
            assertTrue(f.archive.listEvents(wrong.copy(conversationId = "")).isEmpty())
            assertNull(f.archive.getEventDetails(wrong.copy(conversationId = ""), event.id))
            assertTrue(
                f.archive.planEventSummary(
                    wrong,
                    event.id
                ) is MemoryEventPlanResult.Conflict
            )
            f.archive.archiveConversation(context.conversationId)
            assertTrue(
                f.archive.planEventSummary(
                    context,
                    event.id
                ) is MemoryEventPlanResult.Conflict
            )
            assertNotNull(f.archive.getEventDetails(context.copy(conversationId = ""), event.id))
        }
    }

    @Test
    fun unrequestedOldRunJob_isReplannedWithNewRuntimeWithoutRebindingHistory() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val original = f.contexts().single()
            val (event, _) = f.createEvent(original)
            val oldJob = planned(f, original, event.id)
            f.archive.archiveConversation(original.conversationId)
            val current = f.contexts().single()
            assertEquals(original.spaceId, current.spaceId)
            val next = planned(f, current, event.id)
            assertNotEquals(oldJob.id, next.id)
            assertEquals(current, next.context)
            assertEquals(original, f.archive.getJob(oldJob.id)!!.context)
            assertEquals(MemoryJobStatus.INVALIDATED, f.archive.getJob(oldJob.id)!!.status)
            savePlanned(f, next)
            assertTrue(
                f.archive.commitEventSummary(
                    next.id,
                    "owner"
                ) is MemoryWriteResult.Committed
            )
        }
    }

    @Test
    fun personaRoundTrip_replansOnlyUnrequestedJobForNewSpaceGeneration() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val original = f.contexts(persona = "身份甲").single()
            val (event, _) = f.createEvent(original)
            val oldJob = planned(f, original, event.id)
            suspend fun bind(persona: String) = f.archive.bindConversation(
                RoleplayScenario(
                    id = original.scenarioId, assistantId = original.characterId,
                    userPersonaOverride = persona, memoryPurpose = MemoryPurpose.CONTINUOUS
                ),
                original.conversationId, listOf(original.characterId), original.runId,
            ).single()
            bind("身份乙")
            val current = bind("身份甲")
            assertTrue(current.generation > original.generation)
            val next = planned(f, current, event.id)
            assertNotEquals(oldJob.id, next.id)
            assertEquals(current, next.context)
            assertNotNull(f.archive.claimJob(next.id, "new-generation"))
            assertNull(f.archive.claimJob(oldJob.id, "old-generation"))
        }
    }

    @Test
    fun changedRuntime_cannotBypassPaidOrReceivedJobProtection() = runBlocking {
        for (evidence in listOf(
            "STARTED",
            "RECEIVED",
            "UNKNOWN"
        )) MemoryExtractionFixture().use { f ->
            val original = f.contexts().single()
            val (event, _) = f.createEvent(original)
            val oldJob = planned(f, original, event.id)
            assertNotNull(f.archive.claimJob(oldJob.id, "old"))
            assertTrue(f.archive.beginEventSummaryBatch(oldJob.id, "old", 0))
            if (evidence == "RECEIVED") {
                assertTrue(
                    f.archive.saveEventSummaryBatch(
                        oldJob.id, "old", 0,
                        memoryEventResponse(MemoryEventSummaryBatches.readWork(oldJob.requestJson).snapshot)
                    )
                )
            }
            f.archive.releaseJob(oldJob.id, "old", MemoryJobStatus.PAUSED, "NETWORK_UNCERTAIN")
            if (evidence == "UNKNOWN") {
                val dao = f.database.memoryArchiveDao()
                dao.upsertJob(dao.getJob(oldJob.id)!!.copy(requestState = "UNKNOWN"))
            }
            f.archive.archiveConversation(original.conversationId)
            val current = f.contexts().single()
            val result =
                f.archive.planEventSummary(current, event.id) as MemoryEventPlanResult.NeedsReview
            assertEquals(listOf(oldJob.id), result.jobIds)
            assertEquals(1, f.database.memoryArchiveDao().listEventJobs(event.id).size)
            assertEquals(original, f.archive.getJob(oldJob.id)!!.context)
        }
    }

    @Test
    fun defaultPersonaList_neverLeaksUnprovenLegacyBoxMetadata() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            assertEquals(MEMORY_UNKNOWN_PERSONA, context.personaKey)
            val (valid, records) = f.createEvent(
                context,
                listOf("默认身份的起因", "默认身份的进展")
            )
            val privateContext = f.contexts(persona = "另外的扮演身份").single()
            val privateRecord = f.remember(privateContext, "另一身份的私有记录")
            val isolated = f.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            val isolatedRecord = f.remember(isolated, "独立剧情私有记录")
            val dao = f.database.memoryArchiveDao()
            val oldBox = dao.getEventBox(valid.id)!!
                .copy(title = "未经证明的旧私有标题", summary = "未经证明的旧私有正文")
            listOf("mixed", "foreign", "empty").forEach { dao.upsertEventBox(oldBox.copy(id = it)) }
            dao.upsertEventMember(
                MemoryEventMemberEntity(
                    "mixed",
                    records.first().id,
                    null,
                    "LIVE",
                    f.now
                )
            )
            dao.upsertEventMember(
                MemoryEventMemberEntity(
                    "mixed",
                    privateRecord.id,
                    null,
                    "LIVE",
                    f.now
                )
            )
            dao.upsertEventMember(
                MemoryEventMemberEntity(
                    "foreign",
                    isolatedRecord.id,
                    null,
                    "LIVE",
                    f.now
                )
            )
            assertEquals(listOf(valid.id), f.archive.listEvents(context).map { it.id })
            listOf("mixed", "foreign", "empty").forEach { id ->
                assertNull(f.archive.getEventDetails(context, id))
                assertEquals("未经证明的旧私有正文", dao.getEventBox(id)!!.summary)
            }
            assertNotNull(f.archive.getEventDetails(context, valid.id))
        }
    }

    private fun version(record: MemoryRecord) = MemoryRecordVersion(record.id, record.revision)
    private suspend fun planned(
        f: MemoryExtractionFixture,
        context: MemoryRuntimeContext,
        eventId: String
    ) =
        (f.archive.planEventSummary(context, eventId) as MemoryEventPlanResult.Planned).job

    private suspend fun saved(
        f: MemoryExtractionFixture,
        context: MemoryRuntimeContext,
        eventId: String
    ): MemoryProcessingJob =
        planned(f, context, eventId).also { savePlanned(f, it) }

    private suspend fun savePlanned(f: MemoryExtractionFixture, job: MemoryProcessingJob) {
        assertNotNull(f.archive.claimJob(job.id, "owner"))
        assertTrue(f.archive.beginEventSummaryBatch(job.id, "owner", 0))
        val work = MemoryEventSummaryBatches.readWork(job.requestJson)
        assertEquals(1, work.batchEnds.size)
        assertTrue(
            f.archive.saveEventSummaryBatch(
                job.id,
                "owner",
                0,
                memoryEventResponse(work.snapshot)
            )
        )
    }
}
