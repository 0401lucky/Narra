package com.example.myapplication.data.repository.context

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.memory.MemoryProposalEntity
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryConflict
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.MemoryProposalStatus
import com.example.myapplication.model.MemoryRecordDraft
import com.example.myapplication.model.MemoryRecordKind
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MemorySourceRef
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** 审批与记忆提交共用一笔事务，重启后仍复查原始来源/运行凭证。 */
internal class MemoryArchiveProposalStore(
    private val database: ChatDatabase,
    private val archive: MemoryArchiveRepository,
    private val validRuntime: suspend (MemoryRuntimeContext) -> Boolean,
    private val validSource: suspend (MemoryRuntimeContext, MemorySourceRef) -> Boolean,
    private val now: () -> Long,
) {
    private val dao = database.memoryArchiveDao()
    private val gson = AppJson.gson

    fun observeProposal(conversationId: String): Flow<PendingMemoryProposal?> =
        dao.observeConversationProposals(conversationId).map { rows ->
            rows.firstOrNull { it.status == "PENDING" && validRuntime(it.context()) }?.toProposal()
        }

    fun observeHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>> =
        dao.observeConversationProposals(conversationId).map { rows ->
            rows.map { row ->
                MemoryProposalHistoryItem(
                    row.id,
                    row.conversationId,
                    row.characterId,
                    MemoryScopeType.fromStorageValue(row.scopeType),
                    row.content,
                    row.reason,
                    row.importance,
                    when (row.status) {
                        "PENDING" -> MemoryProposalStatus.PENDING; "APPROVED" -> MemoryProposalStatus.APPROVED; else -> MemoryProposalStatus.REJECTED
                    },
                    row.createdAt,
                    row.decidedAt ?: 0L,
                )
            }
        }

    suspend fun getProposal(id: String): PendingMemoryProposal? =
        dao.getProposal(id)?.takeIf { it.status == "PENDING" && validRuntime(it.context()) }
            ?.toProposal()

    suspend fun save(proposal: PendingMemoryProposal) = database.withTransaction {
        val context = requireNotNull(proposal.memoryContext) { "记忆建议缺少来源归属" }
        require(validRuntime(context)) { "角色或记忆归属已改变" }
        require(proposal.content.isNotBlank() && proposal.content.length <= 12_000 && proposal.reason.length <= 2000) { "记忆建议正文无效" }
        require(proposal.sourceRefs.isNotEmpty() && proposal.sourceRefs.all {
            validSource(
                context,
                it
            )
        }) { "记忆建议来源已改变" }
        require(proposal.assistantId == context.characterId && proposal.conversationId == context.conversationId) { "记忆建议归属不符" }
        val existing = dao.getProposal(proposal.id)
        if (existing != null) {
            require(existing.toProposal() == proposal && existing.status == "PENDING") { "同一建议不能替换原始材料" }
            return@withTransaction
        }
        dao.upsertProposal(
            MemoryProposalEntity(
                proposal.id,
                context.spaceId,
                context.characterId,
                context.conversationId,
                context.generation,
                context.personaKey,
                proposal.content,
                "FACT",
                "DAILY",
                gson.toJson(proposal.sourceRefs),
                "PENDING",
                null,
                proposal.createdAt,
                null,
                proposal.reason,
                proposal.importance.coerceIn(0, 100),
                context.scenarioId,
                context.runId,
                proposal.scopeType.storageValue,
            )
        )
    }

    suspend fun approve(id: String): MemoryWriteResult = database.withTransaction {
        val row = dao.getProposal(id) ?: return@withTransaction MemoryWriteResult.Conflict(
            MemoryConflict.INVALID_INPUT
        )
        if (row.status == "APPROVED") return@withTransaction MemoryWriteResult.Committed(
            listOfNotNull(row.finalRecordId),
            alreadyCommitted = true
        )
        if (row.status != "PENDING") return@withTransaction MemoryWriteResult.Conflict(
            MemoryConflict.SPACE_CHANGED
        )
        if (!validRuntime(row.context())) {
            dao.upsertProposal(row.copy(status = "INVALIDATED", decidedAt = now()))
            return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.SPACE_CHANGED)
        }
        val refs = row.toProposal().sourceRefs
        if (refs.isEmpty() || !refs.all {
                validSource(
                    row.context(),
                    it
                )
            }) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.SOURCE_CHANGED)
        val owner = UUID.randomUUID().toString()
        val job = archive.planToolJob(row.context(), "proposal:${row.id}", refs)
        val claimed =
            archive.claimJob(job.id, owner) ?: return@withTransaction MemoryWriteResult.Conflict(
                MemoryConflict.JOB_CHANGED
            )
        val batch = MemoryCommitBatch(
            records = listOf(
                MemoryRecordDraft(
                    "N0",
                    row.content,
                    refs,
                    MemoryRecordKind.FACT,
                    importance = row.importance
                )
            )
        )
        if (claimed.artifact.isBlank() && !archive.saveExtraction(
                job.id,
                owner,
                gson.toJson(batch)
            )
        ) return@withTransaction MemoryWriteResult.Conflict(MemoryConflict.JOB_CHANGED)
        val result = archive.commitJob(job.id, owner, batch)
        if (result is MemoryWriteResult.Committed) {
            dao.upsertProposal(
                row.copy(
                    status = "APPROVED",
                    finalRecordId = result.recordIds.single(),
                    decidedAt = now()
                )
            )
        }
        result
    }

    suspend fun reject(id: String): Unit = database.withTransaction {
        dao.getProposal(id)?.takeIf { it.status == "PENDING" }?.let {
            dao.upsertProposal(it.copy(status = "REJECTED", decidedAt = now()))
        }
        Unit
    }

    suspend fun clear(conversationId: String) =
        dao.clearConversationProposals(conversationId, now())

    private fun MemoryProposalEntity.context() = MemoryRuntimeContext(
        characterId,
        spaceId,
        conversationId,
        scenarioId,
        runId,
        personaKey,
        generation
    )

    private fun MemoryProposalEntity.toProposal() = PendingMemoryProposal(
        id,
        conversationId,
        characterId,
        MemoryScopeType.fromStorageValue(scopeType),
        content,
        reason,
        importance,
        createdAt,
        context(),
        gson.fromJson(sourceRefsJson, Array<MemorySourceRef>::class.java).toList(),
    )
}
