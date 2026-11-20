package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/** 资料图使用 ABORT 插入，任何坏边或重复键均由外层主库事务整体回滚。 */
@Dao
interface MemoryTransferDao {
    @Query("SELECT * FROM memory_transfer_receipts WHERE localPlanId = :localPlanId")
    suspend fun getReceipt(localPlanId: String): MemoryTransferReceiptEntity?

    @Insert
    suspend fun insertReceipt(row: MemoryTransferReceiptEntity)

    @Query("SELECT * FROM memory_spaces WHERE status != 'DELETED' ORDER BY id")
    suspend fun listSpaces(): List<MemorySpaceEntity>

    @Query("SELECT * FROM memory_records WHERE spaceId = :spaceId ORDER BY id")
    suspend fun listRecords(spaceId: String): List<MemoryRecordEntity>

    @Query("SELECT r.* FROM memory_record_revisions r JOIN memory_records m ON m.id = r.recordId WHERE m.spaceId = :spaceId ORDER BY r.recordId, r.revision")
    suspend fun listRecordRevisions(spaceId: String): List<MemoryRecordRevisionEntity>

    @Query("SELECT s.* FROM memory_record_sources s JOIN memory_records r ON r.id = s.recordId WHERE r.spaceId = :spaceId ORDER BY s.recordId, s.recordRevision, s.sourceId, s.spanStart, s.spanEnd")
    suspend fun listRecordSources(spaceId: String): List<MemoryRecordSourceEntity>

    @Query("SELECT s.* FROM memory_source_evidence s WHERE s.spaceId = :spaceId AND (EXISTS (SELECT 1 FROM memory_record_sources r WHERE r.sourceId = s.id) OR EXISTS (SELECT 1 FROM memory_knowledge_sources k WHERE k.sourceId = s.id) OR EXISTS (SELECT 1 FROM memory_transfer_origins o WHERE o.sourceId = s.id)) ORDER BY s.id")
    suspend fun listSources(spaceId: String): List<MemorySourceEvidenceEntity>

    @Query("SELECT * FROM memory_event_boxes WHERE spaceId = :spaceId ORDER BY id")
    suspend fun listEvents(spaceId: String): List<MemoryEventBoxEntity>

    @Query("SELECT m.* FROM memory_event_members m JOIN memory_event_boxes e ON e.id = m.eventId WHERE e.spaceId = :spaceId ORDER BY m.eventId, m.recordId")
    suspend fun listMembers(spaceId: String): List<MemoryEventMemberEntity>

    @Query("SELECT * FROM memory_resident_entries WHERE spaceId = :spaceId ORDER BY id")
    suspend fun listResidents(spaceId: String): List<MemoryResidentEntryEntity>

    @Query("SELECT * FROM memory_current_states WHERE spaceId = :spaceId ORDER BY id")
    suspend fun listStates(spaceId: String): List<MemoryCurrentStateEntity>

    @Query("SELECT l.* FROM memory_derived_links l JOIN memory_records r ON r.id = l.recordId WHERE r.spaceId = :spaceId ORDER BY l.ownerKind, l.ownerId, l.recordId")
    suspend fun listDerivedLinks(spaceId: String): List<MemoryDerivedLinkEntity>

    @Query("SELECT * FROM memory_conversation_bindings WHERE spaceId = :spaceId ORDER BY createdAt DESC, conversationId, characterId")
    suspend fun listBindings(spaceId: String): List<MemoryConversationBindingEntity>

    @Query("SELECT * FROM memory_transfer_origins WHERE spaceId = :spaceId ORDER BY id")
    suspend fun listOrigins(spaceId: String): List<MemoryTransferOriginEntity>

    @Insert
    suspend fun insertOrigins(rows: List<MemoryTransferOriginEntity>)

    @Insert
    suspend fun insertRecords(rows: List<MemoryRecordEntity>)

    @Insert
    suspend fun insertRecordRevisions(rows: List<MemoryRecordRevisionEntity>)

    @Insert
    suspend fun insertSources(rows: List<MemorySourceEvidenceEntity>)

    @Insert
    suspend fun insertRecordSources(rows: List<MemoryRecordSourceEntity>)

    @Insert
    suspend fun insertEvents(rows: List<MemoryEventBoxEntity>)

    @Insert
    suspend fun insertMembers(rows: List<MemoryEventMemberEntity>)

    @Insert
    suspend fun insertResidents(rows: List<MemoryResidentEntryEntity>)

    @Insert
    suspend fun insertStates(rows: List<MemoryCurrentStateEntity>)

    @Insert
    suspend fun insertKnowledgeRevisions(rows: List<MemoryKnowledgeRevisionEntity>)

    @Insert
    suspend fun insertKnowledgeSources(rows: List<MemoryKnowledgeSourceEntity>)

    @Insert
    suspend fun insertDerivedLinks(rows: List<MemoryDerivedLinkEntity>)
}
