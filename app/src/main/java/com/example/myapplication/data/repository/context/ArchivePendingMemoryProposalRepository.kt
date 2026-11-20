package com.example.myapplication.data.repository.context

import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.PendingMemoryProposal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 旧入口保留兼容行为，新记忆建议全部交给带来源守卫的 Room 档案。 */
class ArchivePendingMemoryProposalRepository(
    private val archive: MemoryArchiveRepository,
    private val legacy: PendingMemoryProposalRepository = InMemoryPendingMemoryProposalRepository(),
) : PendingMemoryProposalRepository {
    override fun observeProposal(conversationId: String): Flow<PendingMemoryProposal?> =
        combine(
            archive.observePendingProposal(conversationId),
            legacy.observeProposal(conversationId)
        ) { saved, old -> saved ?: old }

    override fun observeHistory(conversationId: String): Flow<List<MemoryProposalHistoryItem>> =
        combine(
            archive.observeProposalHistory(conversationId),
            legacy.observeHistory(conversationId)
        ) { saved, old ->
            (saved + old).sortedByDescending { maxOf(it.createdAt, it.decidedAt) }
        }

    override suspend fun getProposal(proposalId: String): PendingMemoryProposal? =
        archive.getPendingProposal(proposalId) ?: legacy.getProposal(proposalId)

    override suspend fun upsertProposal(proposal: PendingMemoryProposal) {
        if (proposal.memoryContext == null) legacy.upsertProposal(proposal) else archive.saveProposal(
            proposal
        )
    }

    override suspend fun markApproved(proposalId: String, decidedAt: Long) {
        if (archive.getPendingProposal(proposalId) != null) error("持久建议必须通过原子记忆审批提交")
        legacy.markApproved(proposalId, decidedAt)
    }

    override suspend fun markRejected(proposalId: String, decidedAt: Long) {
        archive.rejectStoredProposal(proposalId)
        legacy.markRejected(proposalId, decidedAt)
    }

    override suspend fun clearConversation(conversationId: String) {
        archive.clearStoredProposals(conversationId)
        legacy.clearConversation(conversationId)
    }
}
