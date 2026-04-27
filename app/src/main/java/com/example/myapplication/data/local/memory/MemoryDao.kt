package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_entries ORDER BY pinned DESC, importance DESC, updatedAt DESC")
    fun observeMemoryEntries(): Flow<List<MemoryEntryEntity>>

    @Query("SELECT * FROM memory_entries ORDER BY pinned DESC, importance DESC, updatedAt DESC")
    suspend fun listMemoryEntries(): List<MemoryEntryEntity>

    @Query(
        """
        SELECT * FROM memory_entries
        WHERE scopeType = :scopeType AND scopeId = :scopeId AND sourceMessageId = :sourceMessageId
        LIMIT 1
        """,
    )
    suspend fun findMemoryBySourceMessage(
        scopeType: String,
        scopeId: String,
        sourceMessageId: String,
    ): MemoryEntryEntity?

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getConversationSummary(conversationId: String): ConversationSummaryEntity?

    @Query("SELECT * FROM conversation_summaries WHERE conversationId = :conversationId LIMIT 1")
    fun observeConversationSummary(conversationId: String): Flow<ConversationSummaryEntity?>

    @Query("SELECT * FROM conversation_summaries ORDER BY updatedAt DESC")
    suspend fun listConversationSummaries(): List<ConversationSummaryEntity>

    @Query("SELECT * FROM conversation_summaries ORDER BY updatedAt DESC")
    fun observeConversationSummaries(): Flow<List<ConversationSummaryEntity>>

    @Upsert
    suspend fun upsertMemoryEntry(entry: MemoryEntryEntity)

    @Upsert
    suspend fun upsertConversationSummary(summary: ConversationSummaryEntity)

    @Query("DELETE FROM memory_entries WHERE id = :entryId")
    suspend fun deleteMemoryEntry(entryId: String)

    @Query(
        """
        DELETE FROM memory_entries
        WHERE id NOT IN (
            SELECT id FROM memory_entries
            ORDER BY pinned DESC, importance DESC, updatedAt DESC
            LIMIT :capacity
        )
        """,
    )
    suspend fun pruneMemoriesToCapacity(capacity: Int)

    @Query("UPDATE memory_entries SET lastUsedAt = :lastUsedAt, updatedAt = :updatedAt WHERE id IN (:entryIds)")
    suspend fun updateMemoryLastUsed(
        entryIds: List<String>,
        lastUsedAt: Long,
        updatedAt: Long,
    )

    @Query("DELETE FROM conversation_summaries WHERE conversationId = :conversationId")
    suspend fun deleteConversationSummary(conversationId: String)
}
