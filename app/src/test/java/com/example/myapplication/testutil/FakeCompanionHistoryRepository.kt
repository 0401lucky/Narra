package com.example.myapplication.testutil

import com.example.myapplication.data.repository.roleplay.CompanionHistoryManagementRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionHistorySupport
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

internal class FakeCompanionHistoryRepository : CompanionHistoryManagementRepository {
    val context = MemoryRuntimeContext("alice", "space", "conversation", "scene", "run")
    val binding = CompanionReadingBinding("conversation", "scene", "run", "合成历史", "alice")
    var scope = CompanionReadingScope(context, listOf(binding), emptySet())
    var data = (0 until 125).map(::record)
    var valid = true
    var failure: Exception? = null
    var beforePage: suspend () -> Unit = {}
    var beforeRead: suspend () -> Unit = {}
    var afterPage: suspend () -> Unit = {}
    var afterRead: suspend () -> Unit = {}
    var afterScopeRefresh: suspend () -> Unit = {}
    var beforeMutation: suspend () -> Unit = {}
    var readAllCalls = 0
    var authorizations = 0
    var mutationCalls = 0
    var lastEdits = emptyList<CompanionHistoryEdit>()
    var lastDeletes = emptyList<CompanionMessageRevision>()
    val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    fun record(index: Int) = CompanionReadingRecord(
        ChatMessage(
            "history-${index.toString().padStart(3, '0')}",
            "conversation", if (index % 2 == 0) MessageRole.USER else MessageRole.ASSISTANT,
            "未删节原文 $index", createdAt = 1_000L + index
        ), binding
    )

    override suspend fun authorize(
        scenarioId: String,
        characterId: String,
        archiveSpaceId: String?,
        archiveRunId: String?
    ): CompanionReadingScope? {
        authorizations++
        return scope.takeIf { valid && scenarioId == "scene" && characterId == "alice" }
    }

    override suspend fun archiveChoices(scope: CompanionReadingScope) =
        emptyList<CompanionHistoryArchiveChoice>()

    override suspend fun refreshScope(scope: CompanionReadingScope): CompanionReadingScope? {
        val refreshed = scope.takeIf { valid }
        afterScopeRefresh()
        return refreshed
    }

    override fun observeChanges(scope: CompanionReadingScope): Flow<Unit> = changes

    override suspend fun page(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery,
        before: CompanionReadingCursor?,
        limit: Int
    ): CompanionHistoryPage {
        beforePage()
        failure?.let { throw it }
        check(valid)
        val all = CompanionHistorySupport.filter(data, query)
        val eligible = all.filter { before == null || it.cursor < before }
        val retained = eligible.takeLast(limit)
        val page = CompanionHistoryPage(
            CompanionHistorySupport.groups(all, query), retained, all.size,
            retained.firstOrNull()?.cursor, eligible.size > limit
        )
        afterPage()
        return page
    }

    override suspend fun readAll(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery
    ): List<CompanionReadingRecord> {
        readAllCalls++
        beforeRead()
        failure?.let { throw it }
        check(valid)
        val records = CompanionHistorySupport.filter(data, query)
        afterRead()
        return records
    }

    override suspend fun edit(
        scope: CompanionReadingScope,
        edits: List<CompanionHistoryEdit>
    ): CompanionHistoryMutationResult {
        mutationCalls++
        lastEdits = edits
        beforeMutation()
        if (scope.readOnly) return CompanionHistoryMutationResult.ReadOnly
        if (!valid || edits.any { edit -> data.none { it.message.id == edit.messageId && it.message.memoryRevision == edit.expectedRevision } || edit.content.isBlank() }) {
            return CompanionHistoryMutationResult.Conflict
        }
        data = data.map { record ->
            edits.firstOrNull { it.messageId == record.message.id }?.let { edit ->
                record.copy(
                    message = record.message.copy(
                        content = edit.content,
                        memoryRevision = record.message.memoryRevision + 1
                    )
                )
            } ?: record
        }
        return CompanionHistoryMutationResult.Applied(edits.map { it.messageId }.toSet())
    }

    override suspend fun delete(
        scope: CompanionReadingScope,
        sources: List<CompanionMessageRevision>
    ): CompanionHistoryMutationResult {
        mutationCalls++
        lastDeletes = sources
        beforeMutation()
        if (scope.readOnly) return CompanionHistoryMutationResult.ReadOnly
        if (!valid || sources.any { ref -> data.none { it.message.id == ref.messageId && it.message.memoryRevision == ref.revision && it.message.conversationId == ref.conversationId } }) {
            return CompanionHistoryMutationResult.Conflict
        }
        val ids = sources.map { it.messageId }.toSet()
        data = data.filterNot { it.message.id in ids }
        return CompanionHistoryMutationResult.Applied(ids)
    }
}
