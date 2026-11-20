package com.example.myapplication.data.repository.roleplay

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionHistorySupport
import kotlinx.coroutines.flow.Flow

/** 管理/导出复用阅读来源；它不生成 AllowedCompanionHistoryScope 或改变主运行。 */
class RoomCompanionHistoryManagementRepository(
    private val database: ChatDatabase,
    private val reading: CompanionReadingRepository,
    private val companion: CompanionRepository,
) : CompanionHistoryManagementRepository {
    override suspend fun authorize(
        scenarioId: String,
        characterId: String,
        archiveSpaceId: String?,
        archiveRunId: String?,
    ): CompanionReadingScope? {
        val current = reading.authorize(scenarioId, characterId) ?: return null
        if (archiveSpaceId == null && archiveRunId == null) return current
        if (archiveSpaceId.isNullOrBlank() || archiveRunId.isNullOrBlank()) return null
        return reading.authorizeArchive(current, archiveSpaceId, archiveRunId)
    }

    override suspend fun archiveChoices(scope: CompanionReadingScope) =
        reading.archiveChoices(scope)

    override suspend fun refreshScope(scope: CompanionReadingScope) = reading.refreshScope(scope)
    override fun observeChanges(scope: CompanionReadingScope): Flow<Unit> =
        reading.observeChanges(scope)

    override suspend fun readAll(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery,
    ): List<CompanionReadingRecord> = database.withTransaction {
        query.validate()
        val refreshed = checkNotNull(reading.refreshScope(scope)) { "历史范围已改变，请重新打开" }
        validateQuery(refreshed, query)
        CompanionHistorySupport.filter(reading.readCanonicalAll(refreshed), query)
    }

    override suspend fun page(
        scope: CompanionReadingScope,
        query: CompanionHistoryQuery,
        before: CompanionReadingCursor?,
        limit: Int,
    ): CompanionHistoryPage = database.withTransaction {
        require(limit in 1..200) { "历史分页大小无效" }
        val refreshed = checkNotNull(reading.refreshScope(scope)) { "历史范围已改变，请重新打开" }
        validateQuery(refreshed, query)
        val authorized = reading.readCanonicalAll(refreshed)
        val all = CompanionHistorySupport.filter(authorized, query)
        val eligible = all.filter { before == null || it.cursor < before }
        val retained = eligible.takeLast(limit)
        val encounters = refreshed.encounterIds
            .mapNotNull { companion.getEncounter(it) }.associateBy { it.id }
        val recordedEncounters = authorized.mapNotNull { it.encounterId }.toSet()
        val emptyGroups =
            encounters.values.filter { it.id !in recordedEncounters }.mapNotNull { encounter ->
                refreshed.bindings.firstOrNull { it.conversationId == encounter.context.conversationId }
                    ?.let { CompanionHistorySupport.emptyEncounterGroup(encounter, it, query) }
            }
        val groups = (CompanionHistorySupport.groups(all, query, encounters) + emptyGroups)
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
        CompanionHistoryPage(
            groups, retained, all.size,
            retained.firstOrNull()?.cursor, eligible.size > limit
        )
    }

    private fun validateQuery(scope: CompanionReadingScope, query: CompanionHistoryQuery) {
        query.validate()
        require(query.conversationId == null || scope.bindings.any { it.conversationId == query.conversationId }) { "会话不在授权阅读范围内" }
        require(query.encounterId == null || query.encounterId in scope.encounterIds) { "场次不在授权阅读范围内" }
    }

    override suspend fun edit(
        scope: CompanionReadingScope,
        edits: List<CompanionHistoryEdit>,
    ): CompanionHistoryMutationResult = database.withTransaction {
        if (scope.readOnly) return@withTransaction CompanionHistoryMutationResult.ReadOnly
        if (edits.isEmpty() || edits.map { it.messageId }.distinct().size != edits.size ||
            edits.any { it.expectedRevision <= 0L || it.content.isBlank() }
        ) return@withTransaction CompanionHistoryMutationResult.Conflict
        val records = reading.readCanonicalAll(scope).associateBy { it.message.id }
        if (edits.any { records[it.messageId]?.binding?.readOnly == true }) return@withTransaction CompanionHistoryMutationResult.ReadOnly
        val mutations = edits.map { edit ->
            val record = records[edit.messageId]
                ?: return@withTransaction CompanionHistoryMutationResult.Conflict
            if (record.message.memoryRevision != edit.expectedRevision ||
                record.message.companionMemoryState == CompanionMemoryState.CONTROL
            ) return@withTransaction CompanionHistoryMutationResult.Conflict
            CompanionMessageMutation(
                CompanionMessageRevision(
                    edit.messageId, edit.expectedRevision,
                    record.message.conversationId
                ), edit.content
            )
        }
        apply(mutations)
    }

    override suspend fun delete(
        scope: CompanionReadingScope,
        sources: List<CompanionMessageRevision>,
    ): CompanionHistoryMutationResult = database.withTransaction {
        if (scope.readOnly) return@withTransaction CompanionHistoryMutationResult.ReadOnly
        if (sources.isEmpty() || sources.map { it.messageId }.distinct().size != sources.size ||
            sources.any { it.revision <= 0L }
        ) return@withTransaction CompanionHistoryMutationResult.Conflict
        val records = reading.readCanonicalAll(scope).associateBy { it.message.id }
        if (sources.any { records[it.messageId]?.binding?.readOnly == true }) return@withTransaction CompanionHistoryMutationResult.ReadOnly
        if (sources.any { ref ->
                val message = records[ref.messageId]?.message
                message == null || message.memoryRevision != ref.revision || message.conversationId != ref.conversationId
            }) return@withTransaction CompanionHistoryMutationResult.Conflict
        apply(sources.map { CompanionMessageMutation(it) })
    }

    private suspend fun apply(mutations: List<CompanionMessageMutation>): CompanionHistoryMutationResult =
        if (companion.mutateMessages(mutations)) CompanionHistoryMutationResult.Applied(mutations.map { it.source.messageId }
            .toSet())
        else CompanionHistoryMutationResult.Conflict
}
