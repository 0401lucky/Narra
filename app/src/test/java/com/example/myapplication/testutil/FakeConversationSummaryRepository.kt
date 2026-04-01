package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.model.ConversationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConversationSummaryRepository(
    initialSummaries: List<ConversationSummary> = emptyList(),
) : ConversationSummaryRepository {
    private val summariesState = MutableStateFlow(initialSummaries)

    override fun observeSummary(conversationId: String): Flow<ConversationSummary?> {
        return summariesState.map { summaries ->
            summaries.firstOrNull { it.conversationId == conversationId }
        }
    }

    override fun observeSummaries(): Flow<List<ConversationSummary>> = summariesState

    override suspend fun getSummary(conversationId: String): ConversationSummary? {
        return summariesState.value.firstOrNull { it.conversationId == conversationId }
    }

    override suspend fun listSummaries(): List<ConversationSummary> {
        return summariesState.value
    }

    override suspend fun upsertSummary(summary: ConversationSummary) {
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == summary.conversationId } + summary
    }

    override suspend fun deleteSummary(conversationId: String) {
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == conversationId }
    }
}
