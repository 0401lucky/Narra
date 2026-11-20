package com.example.myapplication.data.local.memory

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
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

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun getSummarySourceMessages(conversationId: String): List<MessageEntity>

    @Query(
        """
        SELECT conversationId, assistantId, summary, messageCount AS coveredMessageCount,
            updatedAt, sourceSnapshotJson, completionStatus, memoryContextJson
        FROM conversation_summary_segments
        WHERE conversationId = :conversationId AND memoryContextJson = :memoryContextJson AND isAggregate = 1
        LIMIT 1
        """,
    )
    suspend fun getConversationSummaryCache(
        conversationId: String,
        memoryContextJson: String
    ): ConversationSummaryEntity?

    suspend fun getScopedConversationSummary(
        conversationId: String,
        memoryContextJson: String
    ): ConversationSummaryEntity? {
        return getConversationSummary(conversationId)?.takeIf { it.memoryContextJson == memoryContextJson }
            ?: getConversationSummaryCache(conversationId, memoryContextJson)
    }

    @Transaction
    suspend fun getCurrentRuntimeSummary(
        conversationId: String,
        assistantId: String,
        memoryContextJson: String
    ): ConversationSummaryEntity? {
        if (!isSummaryRuntimeCurrent(conversationId, assistantId, memoryContextJson)) return null
        return getScopedConversationSummary(conversationId, memoryContextJson)
    }

    @Query("SELECT * FROM memory_conversation_bindings WHERE conversationId = :conversationId AND characterId = :characterId LIMIT 1")
    suspend fun getSummaryRuntimeBinding(
        conversationId: String,
        characterId: String
    ): MemoryConversationBindingEntity?

    @Query("SELECT * FROM memory_spaces WHERE id = :spaceId LIMIT 1")
    suspend fun getSummaryRuntimeSpace(spaceId: String): MemorySpaceEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM memory_conversation_bindings WHERE conversationId = :conversationId)")
    suspend fun hasSummaryRuntimeBinding(conversationId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM memory_conversation_bindings WHERE conversationId = :conversationId)")
    fun observeSummaryRuntimeBinding(conversationId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM conversations WHERE id = :conversationId)")
    suspend fun hasSummaryConversation(conversationId: String): Boolean

    suspend fun isSummaryRuntimeCurrent(
        conversationId: String,
        assistantId: String,
        memoryContextJson: String
    ): Boolean {
        if (memoryContextJson.isEmpty()) return !hasSummaryRuntimeBinding(conversationId)
        val context = ConversationSummaryCoverageSupport.decodeMemoryContext(memoryContextJson)
            ?: return false
        if (context.conversationId != conversationId || context.characterId != assistantId) return false
        val binding = getSummaryRuntimeBinding(conversationId, context.characterId) ?: return false
        val space = getSummaryRuntimeSpace(context.spaceId) ?: return false
        return !binding.archived && space.status == "ACTIVE" && space.characterId == context.characterId &&
                space.generation == context.generation && binding.spaceId == context.spaceId &&
                binding.personaKey == context.personaKey && binding.scenarioId == context.scenarioId && binding.runId == context.runId
    }

    @Query(
        """
        SELECT * FROM conversation_summary_segments
        WHERE conversationId = :conversationId AND isAggregate = 0
        ORDER BY startCreatedAt ASC, endCreatedAt ASC, id ASC
        """,
    )
    suspend fun listConversationSummarySegments(conversationId: String): List<ConversationSummarySegmentEntity>

    @Query(
        """
        SELECT * FROM conversation_summary_segments
        WHERE isAggregate = 0
        ORDER BY conversationId ASC, startCreatedAt ASC, endCreatedAt ASC, id ASC
        """,
    )
    suspend fun listAllConversationSummarySegments(): List<ConversationSummarySegmentEntity>

    @Query(
        """
        SELECT * FROM conversation_summary_segments
        WHERE conversationId = :conversationId AND isAggregate = 0
        ORDER BY startCreatedAt ASC, endCreatedAt ASC, id ASC
        """,
    )
    fun observeConversationSummarySegments(conversationId: String): Flow<List<ConversationSummarySegmentEntity>>

    @Upsert
    suspend fun upsertMemoryEntry(entry: MemoryEntryEntity)

    @Upsert
    suspend fun upsertConversationSummary(summary: ConversationSummaryEntity)

    @Upsert
    suspend fun upsertConversationSummarySegment(segment: ConversationSummarySegmentEntity)

    @Transaction
    suspend fun upsertConversationSummaryWithCache(summary: ConversationSummaryEntity) {
        if (hasSummaryConversation(summary.conversationId)) {
            val previous = getConversationSummary(summary.conversationId)
            if (previous != null && previous.memoryContextJson != summary.memoryContextJson) {
                upsertConversationSummarySegment(previous.toAggregateCache())
            }
            upsertConversationSummarySegment(summary.toAggregateCache())
        }
        upsertConversationSummary(summary)
    }

    @Transaction
    suspend fun publishConversationSummary(
        summary: ConversationSummaryEntity,
        expectedSummary: ConversationSummaryEntity?,
        expectedMessages: List<MessageEntity>,
    ): Boolean {
        if (!isSummaryRuntimeCurrent(
                summary.conversationId,
                summary.assistantId,
                summary.memoryContextJson
            ) ||
            getScopedConversationSummary(
                summary.conversationId,
                summary.memoryContextJson
            ) != expectedSummary ||
            getSummarySourceMessages(summary.conversationId) != expectedMessages
        ) return false
        upsertConversationSummaryWithCache(summary)
        return true
    }

    @Transaction
    suspend fun saveConversationSummarySegment(
        segment: ConversationSummarySegmentEntity,
        expectedSummary: ConversationSummaryEntity?,
        expectedMessages: List<MessageEntity>,
    ): Boolean {
        if (segment.isAggregate || !isSummaryRuntimeCurrent(
                segment.conversationId,
                segment.assistantId,
                segment.memoryContextJson
            ) ||
            getScopedConversationSummary(
                segment.conversationId,
                segment.memoryContextJson
            ) != expectedSummary ||
            getSummarySourceMessages(segment.conversationId) != expectedMessages
        ) return false
        upsertConversationSummarySegment(segment)
        return true
    }

    @Query("DELETE FROM memory_entries WHERE id = :entryId")
    suspend fun deleteMemoryEntry(entryId: String)

    @Query("DELETE FROM memory_entries WHERE sourceMessageId = :sourceMessageId")
    suspend fun deleteMemoryEntriesBySourceMessageId(sourceMessageId: String)

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

    @Query("DELETE FROM conversation_summary_segments WHERE conversationId = :conversationId")
    suspend fun deleteConversationSummarySegments(conversationId: String)

    @Transaction
    suspend fun deleteConversationSummaryAndSegments(conversationId: String) {
        deleteConversationSummary(conversationId)
        deleteConversationSummarySegments(conversationId)
    }
}
