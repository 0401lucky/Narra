package com.example.myapplication.data.repository.context

import com.example.myapplication.data.local.memory.ConversationSummaryEntity
import com.example.myapplication.data.local.memory.ConversationSummarySegmentEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.data.local.memory.MemoryEntryEntity
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface MemoryRepository {
    fun observeEntries(): Flow<List<MemoryEntry>>

    suspend fun listEntries(): List<MemoryEntry>

    suspend fun findEntryBySourceMessage(
        scopeType: MemoryScopeType,
        scopeId: String,
        sourceMessageId: String,
    ): MemoryEntry?

    suspend fun upsertEntry(entry: MemoryEntry)

    suspend fun deleteEntry(entryId: String)

    /**
     * 全局兜底裁剪：当 memory_entries 总条数超过 [capacity] 时，按 listEntries 默认排序
     * （pinned DESC → importance DESC → updatedAt DESC）保留头部，超出部分整体删除。
     *
     * 注意：与 [com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator]
     * 内基于 `Assistant.memoryMaxItems` 的 per-scope 裁剪并行存在；本方法是跨 scope 的兜底。
     */
    suspend fun pruneToCapacity(capacity: Int)

    suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long)
}

interface ConversationSummaryRepository {
    fun observeSummary(conversationId: String): Flow<ConversationSummary?>

    fun observeSummaries(): Flow<List<ConversationSummary>>

    fun observeSummarySegments(conversationId: String): Flow<List<ConversationSummarySegment>> = flowOf(emptyList())

    suspend fun getSummary(conversationId: String): ConversationSummary?

    suspend fun listSummaries(): List<ConversationSummary>

    suspend fun listSummarySegments(conversationId: String): List<ConversationSummarySegment> = emptyList()

    suspend fun listAllSummarySegments(): List<ConversationSummarySegment> = emptyList()

    suspend fun upsertSummary(summary: ConversationSummary)

    suspend fun upsertSummarySegment(segment: ConversationSummarySegment) = Unit

    suspend fun deleteSummary(conversationId: String)

    suspend fun deleteSummarySegments(conversationId: String) = Unit
}

class RoomMemoryRepository(
    private val memoryDao: MemoryDao,
) : MemoryRepository, ConversationSummaryRepository {
    override fun observeEntries(): Flow<List<MemoryEntry>> {
        return memoryDao.observeMemoryEntries().map { entries ->
            entries.map(::toMemoryDomain)
        }
    }

    override suspend fun listEntries(): List<MemoryEntry> {
        return memoryDao.listMemoryEntries().map(::toMemoryDomain)
    }

    override suspend fun findEntryBySourceMessage(
        scopeType: MemoryScopeType,
        scopeId: String,
        sourceMessageId: String,
    ): MemoryEntry? {
        return memoryDao.findMemoryBySourceMessage(
            scopeType = scopeType.storageValue,
            scopeId = scopeId.trim(),
            sourceMessageId = sourceMessageId,
        )?.let(::toMemoryDomain)
    }

    override suspend fun upsertEntry(entry: MemoryEntry) {
        memoryDao.upsertMemoryEntry(toMemoryEntity(entry))
    }

    override suspend fun deleteEntry(entryId: String) {
        memoryDao.deleteMemoryEntry(entryId)
    }

    override suspend fun pruneToCapacity(capacity: Int) {
        val safeCapacity = capacity.coerceAtLeast(1)
        memoryDao.pruneMemoriesToCapacity(safeCapacity)
    }

    override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) {
        if (entryIds.isEmpty()) return
        memoryDao.updateMemoryLastUsed(
            entryIds = entryIds,
            lastUsedAt = timestamp,
            updatedAt = timestamp,
        )
    }

    override suspend fun getSummary(conversationId: String): ConversationSummary? {
        return memoryDao.getConversationSummary(conversationId)?.let(::toSummaryDomain)
    }

    override fun observeSummary(conversationId: String): Flow<ConversationSummary?> {
        return memoryDao.observeConversationSummary(conversationId).map { entity ->
            entity?.let(::toSummaryDomain)
        }
    }

    override suspend fun listSummaries(): List<ConversationSummary> {
        return memoryDao.listConversationSummaries().map(::toSummaryDomain)
    }

    override fun observeSummaries(): Flow<List<ConversationSummary>> {
        return memoryDao.observeConversationSummaries().map { summaries ->
            summaries.map(::toSummaryDomain)
        }
    }

    override fun observeSummarySegments(conversationId: String): Flow<List<ConversationSummarySegment>> {
        return memoryDao.observeConversationSummarySegments(conversationId).map { segments ->
            segments.map(::toSummarySegmentDomain)
        }
    }

    override suspend fun listSummarySegments(conversationId: String): List<ConversationSummarySegment> {
        return memoryDao.listConversationSummarySegments(conversationId).map(::toSummarySegmentDomain)
    }

    override suspend fun listAllSummarySegments(): List<ConversationSummarySegment> {
        return memoryDao.listAllConversationSummarySegments().map(::toSummarySegmentDomain)
    }

    override suspend fun upsertSummary(summary: ConversationSummary) {
        memoryDao.upsertConversationSummary(
            ConversationSummaryEntity(
                conversationId = summary.conversationId,
                assistantId = summary.assistantId,
                summary = summary.summary,
                coveredMessageCount = summary.coveredMessageCount,
                updatedAt = summary.updatedAt,
            ),
        )
    }

    override suspend fun upsertSummarySegment(segment: ConversationSummarySegment) {
        memoryDao.upsertConversationSummarySegment(toSummarySegmentEntity(segment))
    }

    override suspend fun deleteSummary(conversationId: String) {
        memoryDao.deleteConversationSummary(conversationId)
        memoryDao.deleteConversationSummarySegments(conversationId)
    }

    override suspend fun deleteSummarySegments(conversationId: String) {
        memoryDao.deleteConversationSummarySegments(conversationId)
    }

    private fun toMemoryDomain(entity: MemoryEntryEntity): MemoryEntry {
        return MemoryEntry(
            id = entity.id,
            scopeType = MemoryScopeType.fromStorageValue(entity.scopeType),
            scopeId = entity.scopeId,
            characterId = entity.characterId,
            content = entity.content,
            importance = entity.importance,
            pinned = entity.pinned,
            sourceMessageId = entity.sourceMessageId,
            lastUsedAt = entity.lastUsedAt,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toMemoryEntity(entry: MemoryEntry): MemoryEntryEntity {
        return MemoryEntryEntity(
            id = entry.id,
            scopeType = entry.scopeType.storageValue,
            scopeId = entry.resolvedScopeId(),
            characterId = entry.characterId.trim(),
            content = entry.content.trim(),
            importance = entry.importance,
            pinned = entry.pinned,
            sourceMessageId = entry.sourceMessageId,
            lastUsedAt = entry.lastUsedAt,
            createdAt = entry.createdAt,
            updatedAt = entry.updatedAt,
        )
    }

    private fun toSummaryDomain(entity: ConversationSummaryEntity): ConversationSummary {
        return ConversationSummary(
            conversationId = entity.conversationId,
            assistantId = entity.assistantId,
            summary = entity.summary,
            coveredMessageCount = entity.coveredMessageCount,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toSummarySegmentDomain(entity: ConversationSummarySegmentEntity): ConversationSummarySegment {
        return ConversationSummarySegment(
            id = entity.id,
            conversationId = entity.conversationId,
            assistantId = entity.assistantId,
            startMessageId = entity.startMessageId,
            endMessageId = entity.endMessageId,
            startCreatedAt = entity.startCreatedAt,
            endCreatedAt = entity.endCreatedAt,
            messageCount = entity.messageCount,
            summary = entity.summary,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }

    private fun toSummarySegmentEntity(segment: ConversationSummarySegment): ConversationSummarySegmentEntity {
        return ConversationSummarySegmentEntity(
            id = segment.id,
            conversationId = segment.conversationId,
            assistantId = segment.assistantId,
            startMessageId = segment.startMessageId,
            endMessageId = segment.endMessageId,
            startCreatedAt = segment.startCreatedAt,
            endCreatedAt = segment.endCreatedAt,
            messageCount = segment.messageCount,
            summary = segment.summary,
            createdAt = segment.createdAt,
            updatedAt = segment.updatedAt,
        )
    }
}

object EmptyMemoryRepository : MemoryRepository {
    override fun observeEntries(): Flow<List<MemoryEntry>> = flowOf(emptyList())

    override suspend fun listEntries(): List<MemoryEntry> = emptyList()

    override suspend fun findEntryBySourceMessage(
        scopeType: MemoryScopeType,
        scopeId: String,
        sourceMessageId: String,
    ): MemoryEntry? = null

    override suspend fun upsertEntry(entry: MemoryEntry) = Unit

    override suspend fun deleteEntry(entryId: String) = Unit

    override suspend fun pruneToCapacity(capacity: Int) = Unit

    override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
}

object EmptyConversationSummaryRepository : ConversationSummaryRepository {
    override fun observeSummary(conversationId: String): Flow<ConversationSummary?> = flowOf(null)

    override fun observeSummaries(): Flow<List<ConversationSummary>> = flowOf(emptyList())

    override suspend fun getSummary(conversationId: String): ConversationSummary? = null

    override suspend fun listSummaries(): List<ConversationSummary> = emptyList()

    override suspend fun upsertSummary(summary: ConversationSummary) = Unit

    override suspend fun deleteSummary(conversationId: String) = Unit
}
