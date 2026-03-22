package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.model.ConversationSummary

class FakeConversationSummaryRepository(
    initialSummaries: List<ConversationSummary> = emptyList(),
) : ConversationSummaryRepository {
    private val summaryMap = initialSummaries.associateBy { it.conversationId }.toMutableMap()

    override suspend fun getSummary(conversationId: String): ConversationSummary? {
        return summaryMap[conversationId]
    }

    override suspend fun listSummaries(): List<ConversationSummary> {
        return summaryMap.values.toList()
    }

    override suspend fun upsertSummary(summary: ConversationSummary) {
        summaryMap[summary.conversationId] = summary
    }

    override suspend fun deleteSummary(conversationId: String) {
        summaryMap.remove(conversationId)
    }
}
