package com.example.myapplication.testutil

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MemoryRuntimeContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConversationSummaryRepository(
    initialSummaries: List<ConversationSummary> = emptyList(),
    initialSegments: List<ConversationSummarySegment> = emptyList(),
    var sourceMessages: List<ChatMessage>? = null,
    var currentMemoryContexts: List<MemoryRuntimeContext>? = null,
) : ConversationSummaryRepository {
    private val summariesState = MutableStateFlow(initialSummaries)
    private val cachedSummaries = mutableMapOf<Pair<String, MemoryRuntimeContext?>, ConversationSummary>().apply {
        initialSummaries.forEach { put(it.conversationId to it.memoryContext, it) }
    }
    private val segmentsState = MutableStateFlow(initialSegments)

    override fun observeSummary(conversationId: String): Flow<ConversationSummary?> {
        return summariesState.map { summaries ->
            if (isBoundConversation(conversationId)) null else summaries.firstOrNull { it.conversationId == conversationId && it.memoryContext == null }
                ?: cachedSummaries[conversationId to null]
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
        if (isBoundConversation(conversationId)) return null
        return summaryForContext(conversationId, null)
    }

    override suspend fun getSummaryForContext(context: MemoryRuntimeContext): ConversationSummary? {
        if (!isCurrentContext(context.conversationId, context)) return null
        return summaryForContext(context.conversationId, context)
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

    override suspend fun getSummarySourceMessages(conversationId: String): List<ChatMessage>? {
        return sourceMessages?.filter { it.conversationId == conversationId }
    }

    override suspend fun saveSummaryIfSourcesCurrent(
        summary: ConversationSummary,
        expectedSummary: ConversationSummary?,
    ): Boolean {
        if (!isCurrentContext(summary.conversationId, summary.memoryContext) ||
            summaryForContext(summary.conversationId, summary.memoryContext) != expectedSummary
        ) return false
        val currentSources = getSummarySourceMessages(summary.conversationId)
        if (currentSources != null && (
                ConversationSummaryCoverageSupport.processedSummaryCoverage(summary, currentSources, summary.memoryContext) == 0 ||
                    summary.coveredMessageCount != ConversationSummaryCoverageSupport.verifiedSummaryCoverage(summary, currentSources, summary.memoryContext)
                )
        ) return false
        upsertSummary(summary)
        return true
    }

    override suspend fun saveSummarySegmentIfSourcesCurrent(
        segment: ConversationSummarySegment,
        expectedSummary: ConversationSummary?,
    ): Boolean {
        if (!isCurrentContext(segment.conversationId, segment.memoryContext) ||
            summaryForContext(segment.conversationId, segment.memoryContext) != expectedSummary
        ) return false
        val currentSources = getSummarySourceMessages(segment.conversationId)
        if (currentSources != null && ConversationSummaryCoverageSupport.segmentRange(segment, currentSources, segment.memoryContext) == null) return false
        upsertSummarySegment(segment)
        return true
    }

    override suspend fun upsertSummary(summary: ConversationSummary) {
        cachedSummaries[summary.conversationId to summary.memoryContext] = summary
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == summary.conversationId } + summary
    }

    override suspend fun upsertSummarySegment(segment: ConversationSummarySegment) {
        segmentsState.value = segmentsState.value
            .filterNot { it.id == segment.id } + segment
    }

    override suspend fun deleteSummary(conversationId: String) {
        cachedSummaries.keys.removeAll { it.first == conversationId }
        summariesState.value = summariesState.value
            .filterNot { it.conversationId == conversationId }
        segmentsState.value = segmentsState.value
            .filterNot { it.conversationId == conversationId }
    }

    override suspend fun deleteSummarySegments(conversationId: String) {
        cachedSummaries.keys.removeAll { it.first == conversationId }
        segmentsState.value = segmentsState.value
            .filterNot { it.conversationId == conversationId }
    }

    private fun summaryForContext(conversationId: String, context: MemoryRuntimeContext?): ConversationSummary? =
        summariesState.value.firstOrNull { it.conversationId == conversationId && it.memoryContext == context }
            ?: cachedSummaries[conversationId to context]

    private fun isCurrentContext(conversationId: String, context: MemoryRuntimeContext?): Boolean {
        if (context == null) return !isBoundConversation(conversationId)
        val current = currentMemoryContexts ?: return true
        return context in current
    }

    private fun isBoundConversation(conversationId: String): Boolean =
        currentMemoryContexts?.any { it.conversationId == conversationId } == true ||
            cachedSummaries.keys.any { it.first == conversationId && it.second != null }
}
