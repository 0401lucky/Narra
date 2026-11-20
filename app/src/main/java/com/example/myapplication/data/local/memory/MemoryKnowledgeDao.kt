package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MemoryKnowledgeDao {
    @Query("SELECT * FROM memory_resident_entries WHERE id = :id")
    suspend fun getResident(id: String): MemoryResidentEntryEntity?

    @Query("SELECT * FROM memory_current_states WHERE id = :id")
    suspend fun getState(id: String): MemoryCurrentStateEntity?

    @Query("SELECT * FROM memory_resident_entries WHERE spaceId = :spaceId AND characterId = :characterId AND personaKey = :personaKey AND status != 'DELETED' ORDER BY editedByUser DESC, updatedAt DESC, id LIMIT :limit OFFSET :offset")
    suspend fun listResidents(
        spaceId: String,
        characterId: String,
        personaKey: String,
        limit: Int,
        offset: Int
    ): List<MemoryResidentEntryEntity>

    @Query("SELECT * FROM memory_current_states WHERE spaceId = :spaceId AND characterId = :characterId AND personaKey = :personaKey ORDER BY stateKey, id")
    suspend fun listStates(
        spaceId: String,
        characterId: String,
        personaKey: String
    ): List<MemoryCurrentStateEntity>

    @Insert
    suspend fun insertRevision(revision: MemoryKnowledgeRevisionEntity)

    @Upsert
    suspend fun upsertRevision(revision: MemoryKnowledgeRevisionEntity)

    @Insert
    suspend fun insertSource(source: MemoryKnowledgeSourceEntity)

    @Query("SELECT * FROM memory_knowledge_revisions WHERE ownerKind = :kind AND ownerId = :id ORDER BY revision DESC")
    suspend fun listRevisions(kind: String, id: String): List<MemoryKnowledgeRevisionEntity>

    @Query("SELECT * FROM memory_knowledge_revisions WHERE spaceId = :spaceId ORDER BY ownerKind, ownerId, revision")
    suspend fun listSpaceRevisions(spaceId: String): List<MemoryKnowledgeRevisionEntity>

    @Query("SELECT * FROM memory_knowledge_sources WHERE ownerKind = :kind AND ownerId = :id AND ownerRevision = :revision ORDER BY sourceId, spanStart, spanEnd")
    suspend fun listSources(
        kind: String,
        id: String,
        revision: Long
    ): List<MemoryKnowledgeSourceEntity>

    @Query("SELECT * FROM memory_knowledge_sources WHERE sourceId = :sourceId ORDER BY ownerKind, ownerId, ownerRevision")
    suspend fun listSourceOwners(sourceId: String): List<MemoryKnowledgeSourceEntity>

    @Query("UPDATE memory_knowledge_revisions SET content = '', status = 'STALE' WHERE ownerKind = :kind AND ownerId = :id AND revision = :revision")
    suspend fun scrubRevision(kind: String, id: String, revision: Long)

    @Query("DELETE FROM memory_knowledge_revisions WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun deleteRevisions(kind: String, id: String)

    @Query("DELETE FROM memory_derived_links WHERE ownerKind = :kind AND ownerId = :id")
    suspend fun deleteOwnerLinks(kind: String, id: String)

    @Query("SELECT * FROM memory_knowledge_consumptions WHERE jobId = :jobId")
    suspend fun getConsumption(jobId: String): MemoryKnowledgeConsumptionEntity?

    @Insert
    suspend fun insertConsumption(consumption: MemoryKnowledgeConsumptionEntity)

    @Query("SELECT j.* FROM memory_processing_jobs j LEFT JOIN memory_knowledge_consumptions c ON c.jobId = j.id WHERE j.spaceId = :spaceId AND j.characterId = :characterId AND j.personaKey = :personaKey AND j.status = 'COMMITTED' AND j.purpose IN ('AUTOMATIC_EXTRACTION', 'USER_SELECTION') AND j.artifact != '' AND j.requestJson != '' AND c.jobId IS NULL ORDER BY j.createdAt, j.id LIMIT :limit")
    suspend fun listUnconsumedJobs(
        spaceId: String,
        characterId: String,
        personaKey: String,
        limit: Int
    ): List<MemoryProcessingJobEntity>
}
