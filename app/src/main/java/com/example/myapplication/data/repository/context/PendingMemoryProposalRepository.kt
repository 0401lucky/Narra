package com.example.myapplication.data.repository.context

import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.MemoryProposalStatus
import com.example.myapplication.model.PendingMemoryProposal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

interface PendingMemoryProposalRepository {
    fun observeProposal(conversationId: String): Flow<PendingMemoryProposal?>

    fun observeHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>>

    suspend fun getProposal(proposalId: String): PendingMemoryProposal?

    suspend fun upsertProposal(proposal: PendingMemoryProposal)

    suspend fun markApproved(
        proposalId: String,
        decidedAt: Long,
    )

    suspend fun markRejected(
        proposalId: String,
        decidedAt: Long,
    )

    suspend fun clearConversation(conversationId: String)
}

class InMemoryPendingMemoryProposalRepository : PendingMemoryProposalRepository {
    private val proposals = MutableStateFlow<Map<String, PendingMemoryProposal>>(emptyMap())
    private val history = MutableStateFlow<List<MemoryProposalHistoryItem>>(emptyList())

    override fun observeProposal(conversationId: String): Flow<PendingMemoryProposal?> {
        return proposals.map { current ->
            current.values
                .filter { proposal -> proposal.conversationId == conversationId }
                .maxByOrNull(PendingMemoryProposal::createdAt)
        }
    }

    override fun observeHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>> {
        return history.map { items ->
            items.filter { item -> item.conversationId == conversationId }
                .sortedByDescending { item -> maxOf(item.decidedAt, item.createdAt) }
        }
    }

    override suspend fun getProposal(proposalId: String): PendingMemoryProposal? {
        return proposals.value[proposalId]
    }

    override suspend fun upsertProposal(proposal: PendingMemoryProposal) {
        proposals.value = proposals.value + (proposal.id to proposal)
        history.value = history.value
            .filterNot { item -> item.id == proposal.id } + MemoryProposalHistoryItem(
            id = proposal.id,
            conversationId = proposal.conversationId,
            assistantId = proposal.assistantId,
            scopeType = proposal.scopeType,
            content = proposal.content,
            reason = proposal.reason,
            importance = proposal.importance,
            status = MemoryProposalStatus.PENDING,
            createdAt = proposal.createdAt,
        )
    }

    override suspend fun markApproved(
        proposalId: String,
        decidedAt: Long,
    ) {
        proposals.value = proposals.value - proposalId
        history.value = history.value.map { item ->
            if (item.id == proposalId) {
                item.copy(
                    status = MemoryProposalStatus.APPROVED,
                    decidedAt = decidedAt,
                )
            } else {
                item
            }
        }
    }

    override suspend fun markRejected(
        proposalId: String,
        decidedAt: Long,
    ) {
        proposals.value = proposals.value - proposalId
        history.value = history.value.map { item ->
            if (item.id == proposalId) {
                item.copy(
                    status = MemoryProposalStatus.REJECTED,
                    decidedAt = decidedAt,
                )
            } else {
                item
            }
        }
    }

    override suspend fun clearConversation(conversationId: String) {
        proposals.value = proposals.value.filterValues { proposal ->
            proposal.conversationId != conversationId
        }
        history.value = history.value.filter { item ->
            item.conversationId != conversationId
        }
    }
}

object EmptyPendingMemoryProposalRepository : PendingMemoryProposalRepository {
    override fun observeProposal(conversationId: String): Flow<PendingMemoryProposal?> = flowOf(null)

    override fun observeHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>> = flowOf(emptyList())

    override suspend fun getProposal(proposalId: String): PendingMemoryProposal? = null

    override suspend fun upsertProposal(proposal: PendingMemoryProposal) = Unit

    override suspend fun markApproved(proposalId: String, decidedAt: Long) = Unit

    override suspend fun markRejected(proposalId: String, decidedAt: Long) = Unit

    override suspend fun clearConversation(conversationId: String) = Unit
}
