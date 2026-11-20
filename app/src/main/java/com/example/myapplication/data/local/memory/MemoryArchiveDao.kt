package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryArchiveDao {
    @Query("SELECT DISTINCT characterId FROM memory_spaces WHERE status != 'DELETED' ORDER BY characterId")
    suspend fun listArchiveCharacterIds(): List<String>

    @Query("SELECT * FROM memory_conversation_bindings WHERE spaceId = :spaceId ORDER BY createdAt DESC, conversationId, characterId")
    suspend fun listSpaceBindings(spaceId: String): List<MemoryConversationBindingEntity>

    @Query("SELECT * FROM memory_source_evidence WHERE spaceId = :spaceId ORDER BY ingestedAt, id")
    suspend fun listSpaceSources(spaceId: String): List<MemorySourceEvidenceEntity>

    @Query(
        """
        SELECT personaKey FROM memory_conversation_bindings WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT personaKey FROM memory_source_evidence WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT personaKey FROM memory_records WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT personaKey FROM memory_resident_entries WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT personaKey FROM memory_current_states WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT personaKey FROM memory_event_boxes WHERE spaceId = :spaceId AND characterId = :characterId
        UNION SELECT 'unknown' AS personaKey FROM memory_conversation_bindings b JOIN messages m ON m.conversationId = b.conversationId
            WHERE b.spaceId = :spaceId AND b.characterId = :characterId AND NOT EXISTS
                (SELECT 1 FROM memory_source_evidence s WHERE s.spaceId = :spaceId AND s.characterId = :characterId AND s.messageId = m.id)
        ORDER BY personaKey
    """
    )
    suspend fun listArchivePersonaKeys(spaceId: String, characterId: String): List<String>

    @Query(
        """
        SELECT * FROM memory_records WHERE spaceId = :spaceId AND characterId = :characterId
        AND personaKey = :personaKey AND status != 'DELETED'
        AND (:category = '' OR category = :category) AND (:status = '' OR status = :status)
        AND (:search = '' OR instr(lower(content), lower(:search)) > 0)
        ORDER BY pinned DESC, importance DESC, updatedAt DESC, id LIMIT :limit OFFSET :offset
    """
    )
    suspend fun queryManagementRecords(
        spaceId: String, characterId: String, personaKey: String, category: String,
        status: String, search: String, limit: Int, offset: Int,
    ): List<MemoryRecordEntity>

    @Query(
        """
        SELECT COUNT(*) FROM memory_records WHERE spaceId = :spaceId AND characterId = :characterId
        AND personaKey = :personaKey AND status != 'DELETED'
        AND (:category = '' OR category = :category) AND (:status = '' OR status = :status)
        AND (:search = '' OR instr(lower(content), lower(:search)) > 0)
    """
    )
    suspend fun countManagementRecords(
        spaceId: String, characterId: String, personaKey: String, category: String,
        status: String, search: String,
    ): Int

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM memory_records r JOIN memory_spaces s ON s.id = r.spaceId
            WHERE r.characterId = :characterId AND r.status != 'DELETED' AND s.status != 'DELETED')
        + (SELECT COUNT(*) FROM memory_event_boxes r JOIN memory_spaces s ON s.id = r.spaceId
            WHERE r.characterId = :characterId AND r.status != 'DELETED' AND s.status != 'DELETED')
        + (SELECT COUNT(*) FROM memory_resident_entries r JOIN memory_spaces s ON s.id = r.spaceId
            WHERE r.characterId = :characterId AND r.status != 'DELETED' AND s.status != 'DELETED')
        + (SELECT COUNT(*) FROM memory_current_states r JOIN memory_spaces s ON s.id = r.spaceId
            WHERE r.characterId = :characterId AND r.status != 'DELETED' AND s.status != 'DELETED')
    """
    )
    suspend fun countArchiveItems(characterId: String): Int

    @Query("SELECT * FROM memory_processing_controls WHERE spaceId = :spaceId AND jobId = :jobId")
    suspend fun getProcessingControl(spaceId: String, jobId: String): MemoryProcessingControlEntity?

    @Upsert
    suspend fun upsertProcessingControl(control: MemoryProcessingControlEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM memory_processing_controls WHERE spaceId = :spaceId AND (jobId = '' OR jobId = :jobId OR jobId = (SELECT controlJobId FROM memory_processing_jobs WHERE id = :jobId)) AND userPaused = 1)")
    suspend fun isProcessingPaused(spaceId: String, jobId: String): Boolean

    @Query(
        """
        SELECT DISTINCT j.* FROM memory_processing_jobs j
        JOIN memory_job_sources l ON l.jobId = j.id JOIN memory_source_evidence s ON s.id = l.sourceId
        WHERE j.spaceId = :spaceId AND j.characterId = :characterId
        AND s.conversationId = :conversationId AND s.messageId IN (:messageIds)
        ORDER BY j.createdAt, j.id
    """
    )
    suspend fun listSelectionJobs(
        spaceId: String, characterId: String, conversationId: String, messageIds: List<String>,
    ): List<MemoryProcessingJobEntity>

    @Query("UPDATE memory_conversation_bindings SET archived = 1 WHERE spaceId = :spaceId")
    suspend fun archiveSpaceBindings(spaceId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM memory_record_sources WHERE sourceId = :sourceId)")
    suspend fun hasAnyRecordSourceReference(sourceId: String): Boolean

    @Query("SELECT * FROM memory_spaces WHERE id = :id")
    suspend fun getSpace(id: String): MemorySpaceEntity?

    @Query("SELECT * FROM memory_spaces WHERE identityKey = :key")
    suspend fun findSpace(key: String): MemorySpaceEntity?

    @Query("SELECT * FROM memory_spaces WHERE characterId = :characterId AND status != 'DELETED' ORDER BY updatedAt DESC, id")
    fun observeSpaces(characterId: String): Flow<List<MemorySpaceEntity>>

    @Query("SELECT * FROM memory_spaces WHERE characterId = :characterId ORDER BY updatedAt DESC, id")
    suspend fun listSpaces(characterId: String): List<MemorySpaceEntity>

    @Query("SELECT * FROM memory_spaces WHERE scenarioId = :scenarioId AND purpose = 'independent' ORDER BY id")
    suspend fun listIndependentScenarioSpaces(scenarioId: String): List<MemorySpaceEntity>

    @Upsert
    suspend fun upsertSpace(space: MemorySpaceEntity)

    @Query("UPDATE memory_spaces SET generation = generation + 1, updatedAt = :now WHERE id = :id")
    suspend fun advanceGeneration(id: String, now: Long)

    @Query("SELECT * FROM memory_conversation_bindings WHERE conversationId = :conversationId AND characterId = :characterId")
    suspend fun getBinding(
        conversationId: String,
        characterId: String
    ): MemoryConversationBindingEntity?

    @Query("SELECT * FROM memory_conversation_bindings WHERE conversationId = :conversationId ORDER BY characterId")
    suspend fun listBindings(conversationId: String): List<MemoryConversationBindingEntity>

    @Query("SELECT * FROM memory_conversation_bindings WHERE characterId = :characterId AND archived = 0 ORDER BY createdAt DESC, conversationId")
    suspend fun listActiveCharacterBindings(characterId: String): List<MemoryConversationBindingEntity>

    @Query("SELECT * FROM memory_conversation_bindings WHERE scenarioId = :scenarioId ORDER BY createdAt, conversationId, characterId")
    suspend fun listScenarioBindings(scenarioId: String): List<MemoryConversationBindingEntity>

    @Upsert
    suspend fun upsertBinding(binding: MemoryConversationBindingEntity)

    @Query("UPDATE memory_conversation_bindings SET archived = 1 WHERE conversationId = :conversationId")
    suspend fun archiveBindings(conversationId: String)

    @Query("UPDATE memory_processing_jobs SET status = 'INVALIDATED', leaseOwner = '', leaseUntil = 0, failureCode = 'BINDING_CHANGED', updatedAt = :now WHERE conversationId = :conversationId AND characterId = :characterId AND status != 'COMMITTED'")
    suspend fun invalidateBindingJobs(conversationId: String, characterId: String, now: Long)

    @Query("UPDATE memory_proposals SET status = 'INVALIDATED', decidedAt = :now WHERE conversationId = :conversationId AND characterId = :characterId AND status = 'PENDING'")
    suspend fun invalidateBindingProposals(conversationId: String, characterId: String, now: Long)

    @Query("UPDATE memory_proposals SET status = 'INVALIDATED', decidedAt = :now WHERE spaceId = :spaceId AND status = 'PENDING'")
    suspend fun invalidateSpaceProposals(spaceId: String, now: Long)

    @Query("SELECT * FROM memory_records WHERE id = :id")
    suspend fun getRecord(id: String): MemoryRecordEntity?

    @Query("SELECT * FROM memory_records WHERE spaceId = :spaceId AND status != 'DELETED' ORDER BY pinned DESC, importance DESC, updatedAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun listRecords(spaceId: String, limit: Int, offset: Int): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_records WHERE spaceId = :spaceId AND characterId = :characterId AND status != 'DELETED' AND (:allPersonas OR personaKey = :personaKey) ORDER BY pinned DESC, importance DESC, updatedAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun listScopedRecords(
        spaceId: String,
        characterId: String,
        personaKey: String,
        allPersonas: Boolean,
        limit: Int,
        offset: Int
    ): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_records WHERE spaceId = :spaceId AND characterId = :characterId AND personaKey = :personaKey AND status IN ('ACTIVE', 'ARCHIVED') ORDER BY id")
    suspend fun listRecallRecords(
        spaceId: String,
        characterId: String,
        personaKey: String
    ): List<MemoryRecordEntity>

    @Query(
        """
        SELECT r.* FROM memory_records r JOIN memory_spaces s ON s.id = r.spaceId
        WHERE r.spaceId = :spaceId AND r.characterId = :characterId AND r.status != 'DELETED' AND s.status != 'DELETED'
        AND (:conversationId = '' OR (r.personaKey = :personaKey AND s.generation = :generation AND s.status = 'ACTIVE'
            AND EXISTS (SELECT 1 FROM memory_conversation_bindings b WHERE b.conversationId = :conversationId
                AND b.characterId = :characterId AND b.spaceId = :spaceId AND b.personaKey = :personaKey AND b.archived = 0)))
        ORDER BY r.pinned DESC, r.importance DESC, r.updatedAt DESC, r.id
    """
    )
    fun observeRecords(
        spaceId: String,
        characterId: String,
        conversationId: String,
        personaKey: String,
        generation: Long
    ): Flow<List<MemoryRecordEntity>>

    @Query("SELECT * FROM memory_records WHERE legacyScopeType != '' AND status != 'DELETED' ORDER BY pinned DESC, importance DESC, updatedAt DESC, id")
    suspend fun listLegacyRecords(): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_records WHERE legacyScopeType != '' AND status != 'DELETED' ORDER BY pinned DESC, importance DESC, updatedAt DESC, id")
    fun observeLegacyRecords(): Flow<List<MemoryRecordEntity>>

    @Query("UPDATE memory_records SET lastUsedAt = :timestamp WHERE id IN (:ids) AND status != 'DELETED'")
    suspend fun markRecordsUsed(ids: List<String>, timestamp: Long)

    @Query("SELECT * FROM memory_records WHERE jobId = :jobId ORDER BY localItemId, id")
    suspend fun listJobRecords(jobId: String): List<MemoryRecordEntity>

    @Upsert
    suspend fun upsertRecord(record: MemoryRecordEntity)

    @Query("SELECT * FROM memory_record_revisions WHERE recordId = :recordId ORDER BY revision DESC")
    suspend fun listRevisions(recordId: String): List<MemoryRecordRevisionEntity>

    @Insert
    suspend fun insertRevision(revision: MemoryRecordRevisionEntity)

    @Query("DELETE FROM memory_record_revisions WHERE recordId = :recordId")
    suspend fun deleteRevisions(recordId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecordSource(link: MemoryRecordSourceEntity)

    @Query("SELECT * FROM memory_record_sources WHERE recordId = :recordId AND recordRevision = :revision ORDER BY sourceId, spanStart, spanEnd")
    suspend fun listRecordSources(recordId: String, revision: Long): List<MemoryRecordSourceEntity>

    @Query("SELECT DISTINCT r.* FROM memory_records r JOIN memory_record_sources s ON r.id = s.recordId AND r.revision = s.recordRevision WHERE s.sourceId = :sourceId AND r.status != 'DELETED'")
    suspend fun listCurrentRecordsForSource(sourceId: String): List<MemoryRecordEntity>

    @Query("SELECT * FROM memory_source_evidence WHERE id = :id")
    suspend fun getSource(id: String): MemorySourceEvidenceEntity?

    @Query("SELECT * FROM memory_source_evidence WHERE conversationId = :conversationId AND messageId = :messageId ORDER BY messageRevision, id")
    suspend fun listMessageSources(
        conversationId: String,
        messageId: String
    ): List<MemorySourceEvidenceEntity>

    @Query("SELECT DISTINCT s.* FROM memory_source_evidence s JOIN memory_record_sources l ON l.sourceId = s.id JOIN memory_records r ON r.id = l.recordId WHERE s.kind = 'LEGACY_HINT' AND s.conversationId = :conversationId AND r.legacySourceMessageId = :messageId")
    suspend fun listLegacyMessageSources(
        conversationId: String,
        messageId: String
    ): List<MemorySourceEvidenceEntity>

    @Query("SELECT * FROM memory_source_evidence WHERE conversationId = :conversationId ORDER BY ingestedAt, id")
    suspend fun listConversationSources(conversationId: String): List<MemorySourceEvidenceEntity>

    @Query("SELECT COALESCE(MAX(messageRevision), 0) FROM memory_source_evidence WHERE messageId = :messageId")
    suspend fun maxSourceRevision(messageId: String): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSource(source: MemorySourceEvidenceEntity): Long

    @Upsert
    suspend fun upsertSource(source: MemorySourceEvidenceEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIngress(event: MemoryIngressEventEntity): Long

    @Query("SELECT COUNT(*) FROM memory_ingress_events WHERE sourceId = :sourceId")
    suspend fun countSourceIngress(sourceId: String): Int

    @Query("SELECT * FROM memory_ingress_events WHERE spaceId = :spaceId AND sequence > :afterSequence ORDER BY sequence LIMIT :limit")
    suspend fun listIngress(
        spaceId: String,
        afterSequence: Long,
        limit: Int
    ): List<MemoryIngressEventEntity>

    @Query("SELECT e.* FROM memory_ingress_events e JOIN memory_source_evidence s ON s.id = e.sourceId WHERE e.spaceId = :spaceId AND e.status = 'PENDING' AND (:allPersonas OR s.personaKey = :personaKey) ORDER BY e.sequence LIMIT :limit")
    suspend fun listPendingIngress(
        spaceId: String,
        personaKey: String,
        allPersonas: Boolean,
        limit: Int
    ): List<MemoryIngressEventEntity>

    @Query("UPDATE memory_ingress_events SET status = :status WHERE sourceId = :sourceId")
    suspend fun updateIngressStatus(sourceId: String, status: String)

    @Query("SELECT * FROM memory_processing_cursors WHERE spaceId = :spaceId")
    suspend fun getCursor(spaceId: String): MemoryProcessingCursorEntity?

    @Upsert
    suspend fun upsertCursor(cursor: MemoryProcessingCursorEntity)

    @Query("SELECT * FROM memory_processing_jobs WHERE id = :id")
    suspend fun getJob(id: String): MemoryProcessingJobEntity?

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId ORDER BY createdAt, id")
    suspend fun listSpaceJobs(spaceId: String): List<MemoryProcessingJobEntity>

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId AND characterId = :characterId AND (:allPersonas OR personaKey = :personaKey) ORDER BY createdAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun listJobHistory(
        spaceId: String,
        characterId: String,
        personaKey: String,
        allPersonas: Boolean,
        limit: Int,
        offset: Int
    ): List<MemoryProcessingJobEntity>

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId AND idempotencyKey = :key")
    suspend fun findJob(spaceId: String, key: String): MemoryProcessingJobEntity?

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId AND status NOT IN ('COMMITTED', 'INVALIDATED') ORDER BY createdAt, id")
    suspend fun listRecoverableJobs(spaceId: String): List<MemoryProcessingJobEntity>

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId AND personaKey = :personaKey AND purpose = 'AUTOMATIC_EXTRACTION' AND status != 'COMMITTED' AND (artifact != '' OR requestState != 'NOT_STARTED') ORDER BY createdAt, id")
    suspend fun listUnresolvedAutomaticJobs(
        spaceId: String,
        personaKey: String
    ): List<MemoryProcessingJobEntity>

    @Query("SELECT COUNT(*) FROM memory_processing_jobs WHERE spaceId = :spaceId AND id != :jobId AND leaseOwner != '' AND leaseUntil > :now AND status IN ('EXTRACTING', 'EXTRACTED')")
    suspend fun countOtherLeases(spaceId: String, jobId: String, now: Long): Int

    @Upsert
    suspend fun upsertJob(job: MemoryProcessingJobEntity)

    @Insert
    suspend fun insertJobSources(sources: List<MemoryJobSourceEntity>)

    @Query("SELECT * FROM memory_job_sources WHERE jobId = :jobId ORDER BY sourceId, spanStart, spanEnd")
    suspend fun listJobSources(jobId: String): List<MemoryJobSourceEntity>

    @Query("SELECT s.* FROM memory_job_sources s JOIN memory_processing_jobs j ON j.id = s.jobId WHERE s.sourceId = :sourceId AND j.status = 'COMMITTED' AND j.purpose = 'AUTOMATIC_EXTRACTION' ORDER BY s.spanStart, s.spanEnd")
    suspend fun listCommittedSpans(sourceId: String): List<MemoryJobSourceEntity>

    @Query("SELECT * FROM memory_processing_jobs WHERE spaceId = :spaceId AND personaKey = :personaKey AND status = 'COMMITTED' AND purpose IN ('AUTOMATIC_EXTRACTION', 'USER_SELECTION') AND artifact != '' ORDER BY createdAt, id LIMIT :limit OFFSET :offset")
    suspend fun listCommittedExtractionJobs(
        spaceId: String,
        personaKey: String,
        limit: Int,
        offset: Int
    ): List<MemoryProcessingJobEntity>

    @Query("UPDATE memory_processing_jobs SET status = 'INVALIDATED', artifact = '', requestJson = '', targetRevisionsJson = '{}', leaseOwner = '', leaseUntil = 0, failureCode = :reason, updatedAt = :now WHERE id IN (SELECT jobId FROM memory_job_sources WHERE sourceId = :sourceId) AND status != 'COMMITTED'")
    suspend fun invalidateSourceJobs(sourceId: String, reason: String, now: Long)

    @Query("UPDATE memory_processing_jobs SET status = 'INVALIDATED', leaseOwner = '', leaseUntil = 0, failureCode = :reason, updatedAt = :now WHERE spaceId = :spaceId AND status != 'COMMITTED'")
    suspend fun invalidateSpaceJobs(spaceId: String, reason: String, now: Long)

    @Query("UPDATE memory_processing_jobs SET artifact = '', requestJson = '' WHERE spaceId = :spaceId AND status = 'COMMITTED'")
    suspend fun scrubCommittedArtifacts(spaceId: String)

    @Query("UPDATE memory_processing_jobs SET artifact = '', requestJson = '', targetRevisionsJson = '{}' WHERE spaceId = :spaceId")
    suspend fun scrubSpaceJobSnapshots(spaceId: String)

    @Query("UPDATE memory_processing_jobs SET artifact = '', requestJson = '' WHERE id IN (SELECT jobId FROM memory_job_sources WHERE sourceId = :sourceId)")
    suspend fun scrubSourceArtifacts(sourceId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRelation(intent: MemoryRelationIntentEntity)

    @Query("SELECT * FROM memory_relation_intents WHERE spaceId = :spaceId AND status = 'PENDING' ORDER BY createdAt, id")
    suspend fun listPendingRelations(spaceId: String): List<MemoryRelationIntentEntity>

    @Query("UPDATE memory_relation_intents SET status = :status WHERE id = :id AND status = 'PENDING'")
    suspend fun updateRelationStatus(id: String, status: String)

    @Query("SELECT * FROM memory_processing_jobs WHERE eventId = :eventId ORDER BY createdAt, id")
    suspend fun listEventJobs(eventId: String): List<MemoryProcessingJobEntity>

    @Query("UPDATE memory_processing_jobs SET status = 'INVALIDATED', leaseOwner = '', leaseUntil = 0, failureCode = :reason, updatedAt = :now WHERE eventId = :eventId AND status != 'COMMITTED'")
    suspend fun invalidateEventJobs(eventId: String, reason: String, now: Long)

    @Query("UPDATE memory_processing_jobs SET status = 'INVALIDATED', leaseOwner = '', leaseUntil = 0, failureCode = 'EVENT_CHANGED', updatedAt = :now WHERE eventId IN (SELECT eventId FROM memory_event_members WHERE recordId = :recordId) AND status != 'COMMITTED'")
    suspend fun invalidateRecordEventJobs(recordId: String, now: Long)

    @Query("UPDATE memory_relation_intents SET status = 'INVALIDATED', eventName = CASE WHEN :scrubContent THEN '' ELSE eventName END WHERE fromRecordId = :recordId OR targetRecordId = :recordId")
    suspend fun invalidateRelations(recordId: String, scrubContent: Boolean)

    @Query("UPDATE memory_event_boxes SET summary = '', summaryStatus = 'STALE', currentState = '', generation = generation + 1, updatedAt = :now WHERE id IN (SELECT eventId FROM memory_event_members WHERE recordId = :recordId)")
    suspend fun invalidateEventSummaries(recordId: String, now: Long)

    @Query("UPDATE memory_event_boxes SET title = '' WHERE id IN (SELECT eventId FROM memory_event_members WHERE recordId = :recordId)")
    suspend fun scrubEventTitles(recordId: String)

    @Query("UPDATE memory_event_members SET coveredRevision = NULL, status = 'LIVE' WHERE eventId IN (SELECT eventId FROM memory_event_members WHERE recordId = :recordId)")
    suspend fun invalidateEventMember(recordId: String)

    @Query("UPDATE memory_resident_entries SET content = '', status = 'STALE', updatedAt = :now WHERE id IN (SELECT ownerId FROM memory_derived_links WHERE ownerKind = 'RESIDENT' AND recordId = :recordId)")
    suspend fun invalidateResidentEntries(recordId: String, now: Long)

    @Query("UPDATE memory_current_states SET content = '', status = 'STALE', updatedAt = :now WHERE id IN (SELECT ownerId FROM memory_derived_links WHERE ownerKind = 'STATE' AND recordId = :recordId)")
    suspend fun invalidateCurrentStates(recordId: String, now: Long)

    @Query("DELETE FROM memory_embeddings WHERE recordId = :recordId")
    suspend fun deleteRecordEmbeddings(recordId: String)

    @Query("DELETE FROM memory_event_members WHERE recordId = :recordId")
    suspend fun deleteEventMemberships(recordId: String)

    @Query("DELETE FROM memory_derived_links WHERE recordId = :recordId")
    suspend fun deleteDerivedLinks(recordId: String)

    @Upsert
    suspend fun upsertEventBox(event: MemoryEventBoxEntity)

    @Query("SELECT * FROM memory_event_boxes WHERE id = :eventId")
    suspend fun getEventBox(eventId: String): MemoryEventBoxEntity?

    @Query("SELECT b.* FROM memory_event_boxes b JOIN memory_event_members m ON m.eventId = b.id WHERE m.recordId = :recordId AND b.status != 'DELETED' ORDER BY b.createdAt, b.id")
    suspend fun listRecordEvents(recordId: String): List<MemoryEventBoxEntity>

    @Query("SELECT * FROM memory_event_boxes WHERE spaceId = :spaceId ORDER BY updatedAt DESC, id")
    suspend fun listEventBoxes(spaceId: String): List<MemoryEventBoxEntity>

    @Upsert
    suspend fun upsertEventMember(member: MemoryEventMemberEntity)

    @Query("SELECT * FROM memory_event_members WHERE eventId = :eventId ORDER BY addedAt, recordId")
    suspend fun listEventMembers(eventId: String): List<MemoryEventMemberEntity>

    @Query("DELETE FROM memory_event_members WHERE eventId = :eventId AND recordId = :recordId")
    suspend fun deleteEventMember(eventId: String, recordId: String)

    @Upsert
    suspend fun upsertResidentEntry(entry: MemoryResidentEntryEntity)

    @Query("SELECT * FROM memory_resident_entries WHERE spaceId = :spaceId ORDER BY category, updatedAt DESC, id")
    suspend fun listResidentEntries(spaceId: String): List<MemoryResidentEntryEntity>

    @Upsert
    suspend fun upsertCurrentState(state: MemoryCurrentStateEntity)

    @Query("SELECT * FROM memory_current_states WHERE spaceId = :spaceId ORDER BY stateKey, personaKey, id")
    suspend fun listCurrentStates(spaceId: String): List<MemoryCurrentStateEntity>

    @Upsert
    suspend fun upsertDerivedLink(link: MemoryDerivedLinkEntity)

    @Query("SELECT * FROM memory_derived_links WHERE ownerKind = :ownerKind AND ownerId = :ownerId ORDER BY recordId")
    suspend fun listDerivedLinks(ownerKind: String, ownerId: String): List<MemoryDerivedLinkEntity>

    @Upsert
    suspend fun upsertEmbeddingIndex(index: MemoryEmbeddingIndexEntity)

    @Query("SELECT * FROM memory_embedding_indexes WHERE spaceId = :spaceId ORDER BY vectorSpace, generation")
    suspend fun listEmbeddingIndexes(spaceId: String): List<MemoryEmbeddingIndexEntity>

    @Upsert
    suspend fun upsertEmbedding(embedding: MemoryEmbeddingEntity)

    @Query("SELECT * FROM memory_embeddings WHERE spaceId = :spaceId ORDER BY recordId, recordRevision, vectorSpace, generation, chunkIndex")
    suspend fun listEmbeddings(spaceId: String): List<MemoryEmbeddingEntity>

    @Upsert
    suspend fun upsertReceipt(receipt: MemoryRecallReceiptEntity)

    @Query("SELECT * FROM memory_recall_receipts WHERE requestId = :requestId")
    suspend fun getReceipt(requestId: String): MemoryRecallReceiptEntity?

    @Upsert
    suspend fun upsertProposal(proposal: MemoryProposalEntity)

    @Query("SELECT * FROM memory_proposals WHERE spaceId = :spaceId ORDER BY createdAt DESC, id")
    suspend fun listProposals(spaceId: String): List<MemoryProposalEntity>

    @Query("SELECT * FROM memory_proposals WHERE id = :id")
    suspend fun getProposal(id: String): MemoryProposalEntity?

    @Query("SELECT p.* FROM memory_proposals p JOIN memory_spaces s ON s.id = p.spaceId LEFT JOIN memory_conversation_bindings b ON b.conversationId = p.conversationId AND b.characterId = p.characterId WHERE p.conversationId = :conversationId AND (p.status != 'PENDING' OR (s.status = 'ACTIVE' AND s.generation = p.generation AND b.archived = 0 AND b.personaKey = p.personaKey)) ORDER BY p.createdAt DESC, p.id")
    fun observeConversationProposals(conversationId: String): Flow<List<MemoryProposalEntity>>

    @Query("UPDATE memory_proposals SET content = '', reason = '', sourceRefsJson = '[]', status = 'INVALIDATED', decidedAt = :now WHERE conversationId = :conversationId AND status = 'PENDING'")
    suspend fun clearConversationProposals(conversationId: String, now: Long)

    @Query("UPDATE memory_proposals SET content = '', reason = '', sourceRefsJson = '[]' WHERE finalRecordId = :recordId")
    suspend fun scrubRecordProposals(recordId: String)

    @Query("SELECT * FROM memory_engine_state WHERE id = 1")
    suspend fun getEngineState(): MemoryEngineStateEntity?

    @Upsert
    suspend fun upsertEngineState(state: MemoryEngineStateEntity)

    @Query("UPDATE memory_source_evidence SET content = '', status = 'DELETED' WHERE spaceId = :spaceId")
    suspend fun scrubSpaceSources(spaceId: String)

    @Query("UPDATE memory_event_boxes SET title = '', summary = '', currentState = '', status = 'DELETED', summaryStatus = 'STALE', generation = generation + 1, updatedAt = :now WHERE spaceId = :spaceId")
    suspend fun scrubSpaceEvents(spaceId: String, now: Long)

    @Query("UPDATE memory_resident_entries SET content = '', status = 'DELETED', updatedAt = :now WHERE spaceId = :spaceId")
    suspend fun scrubSpaceResidents(spaceId: String, now: Long)

    @Query("UPDATE memory_current_states SET content = '', status = 'DELETED', updatedAt = :now WHERE spaceId = :spaceId")
    suspend fun scrubSpaceStates(spaceId: String, now: Long)

    @Query("UPDATE memory_knowledge_revisions SET content = '', status = 'DELETED', recordRefsJson = '[]' WHERE spaceId = :spaceId")
    suspend fun scrubSpaceKnowledgeRevisions(spaceId: String)

    @Query("UPDATE memory_proposals SET content = '', reason = '', sourceRefsJson = '[]', status = 'INVALIDATED', decidedAt = :now WHERE spaceId = :spaceId")
    suspend fun scrubSpaceProposals(spaceId: String, now: Long)

    @Query("DELETE FROM memory_embedding_indexes WHERE spaceId = :spaceId")
    suspend fun deleteSpaceEmbeddingIndexes(spaceId: String)

    @Query("DELETE FROM memory_embeddings WHERE spaceId = :spaceId")
    suspend fun deleteSpaceEmbeddings(spaceId: String)

    @Transaction
    suspend fun scrubSpace(spaceId: String, now: Long) {
        scrubSpaceSources(spaceId)
        scrubSpaceEvents(spaceId, now)
        scrubSpaceResidents(spaceId, now)
        scrubSpaceStates(spaceId, now)
        scrubSpaceKnowledgeRevisions(spaceId)
        scrubSpaceProposals(spaceId, now)
        scrubSpaceJobSnapshots(spaceId)
        deleteSpaceEmbeddings(spaceId)
        deleteSpaceEmbeddingIndexes(spaceId)
    }
}
