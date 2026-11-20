package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface MemoryVectorDao {
    @Query("SELECT * FROM memory_embedding_indexes WHERE spaceId = :spaceId AND personaKey = :personaKey ORDER BY generation DESC, vectorSpace")
    suspend fun listIndexes(spaceId: String, personaKey: String): List<MemoryEmbeddingIndexEntity>

    @Query("SELECT * FROM memory_embedding_indexes WHERE spaceId = :spaceId AND personaKey = :personaKey AND vectorSpace = :vectorSpace AND generation = :generation")
    suspend fun getIndex(
        spaceId: String,
        personaKey: String,
        vectorSpace: String,
        generation: Long,
    ): MemoryEmbeddingIndexEntity?

    @Query("SELECT COALESCE(MAX(generation), 0) FROM memory_embedding_indexes WHERE spaceId = :spaceId")
    suspend fun maxGeneration(spaceId: String): Long

    @Upsert
    suspend fun upsertIndex(index: MemoryEmbeddingIndexEntity)

    @Upsert
    suspend fun upsertEmbeddings(embeddings: List<MemoryEmbeddingEntity>)

    @Query("DELETE FROM memory_embedding_indexes WHERE spaceId = :spaceId AND personaKey = :personaKey AND vectorSpace = :vectorSpace AND generation = :generation")
    suspend fun deleteIndex(
        spaceId: String,
        personaKey: String,
        vectorSpace: String,
        generation: Long,
    )

    @Query("UPDATE memory_embedding_indexes SET active = 0 WHERE spaceId = :spaceId AND personaKey = :personaKey AND configurationKey = :configurationKey")
    suspend fun deactivateConfiguration(
        spaceId: String,
        personaKey: String,
        configurationKey: String
    )

    @Query(
        """
        DELETE FROM memory_embeddings
        WHERE spaceId = :spaceId AND vectorSpace = :vectorSpace AND generation = :generation
            AND recordId IN (SELECT id FROM memory_records WHERE spaceId = :spaceId AND personaKey = :personaKey)
        """
    )
    suspend fun deleteGenerationEmbeddings(
        spaceId: String,
        personaKey: String,
        vectorSpace: String,
        generation: Long,
    )

    /** 范围和当前正文版本先过滤；此处不做 topK，归档经历与活动经历同样可召回。 */
    @Query(
        """
        SELECT e.* FROM memory_embeddings e
        JOIN memory_records r ON r.id = e.recordId AND r.revision = e.recordRevision
        WHERE e.spaceId = :spaceId AND r.spaceId = :spaceId AND r.characterId = :characterId
            AND r.personaKey = :personaKey AND r.status IN ('ACTIVE', 'ARCHIVED')
            AND e.vectorSpace = :vectorSpace AND e.generation = :generation AND e.status = 'READY'
        ORDER BY e.recordId, e.chunkIndex
        """
    )
    suspend fun listUsableEmbeddings(
        spaceId: String,
        characterId: String,
        personaKey: String,
        vectorSpace: String,
        generation: Long,
    ): List<MemoryEmbeddingEntity>
}
