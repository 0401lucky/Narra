package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConversationSummaryRepository(
    initialSummaries: List<ConversationSummary> = emptyList(),
    initialSegments: List<ConversationSummarySegment> = emptyList(),
) : ConversationSummaryRepository {
    private val summariesState = MutableStateFlow(initialSummaries)
    private val segmentsState = MutableStateFlow(initialSegments)

    override fun observeSummary(conversationId: String): Flow<ConversationSummary?> {
        return summariesState.map { summaries ->
            summaries.firstOrNull { it.conversationId == conversationId }
        }
    }

    override fun observeSummaries(): Flow<List<ConversationSummary>> = summariesState

    override fun observeSummarySegments(conversationId: String): Flow<List<ConversationSummarySegment>> {
        return segmentsState.map { segments ->
            segments
                .filter { it.conversationId == conversationId }
                .sortedWith(compareBy({ it.startCreatedAt }, { it.endCreatedAt }))
        }
    }

    override suspend fun getSummary(conversationId: String): ConversationSummary? {
        return summariesState.value.firstOrNull { it.conversationId == conversationId }
    }

    override suspend fun listSummaries(): List<ConversationSummary> {
        return summariesState.value
    }

    override suspend fun listSummarySegments(conversationId: String): List<ConversationSummarySegment> {
        return segmentsState.value
            .filter { it.conversationId == conversationId }
            .sortedWith(compareBy({ it.startCreatedAt }, { it.endCreatedAt }))
    }

    override suspend fun listAllSummarySegments(): List<ConversationSummarySegment> {
        return segmentsState.value
            .sortedWith(compareBy({ it.conversationId }, { it.startCreatedAt }, { it.endCreatedAt }))
    }

    override suspend fun upsertSummary(summary: ConversationSummary) {
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == summary.conversationId } + summary
    }

    override suspend fun upsertSummarySegment(segment: ConversationSummarySegment) {
        segmentsState.value = segmentsState.value
            .filterNot { it.id == segment.id } + segment
    }

    override suspend fun deleteSummary(conversationId: String) {
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == conversationId }
        segmentsState.value = segmentsState.value
            .filterNot { it.conversationId == conversationId }
    }

    override suspend fun deleteSummarySegments(conversationId: String) {
        segmentsState.value = segmentsState.value
            .filterNot { it.conversationId == conversationId }
    }
}
