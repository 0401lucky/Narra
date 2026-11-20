package com.example.myapplication.conversation

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.RoleplayContextStatus

class RoleplayContextStatusCoordinator(
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val memoryRepository: MemoryRepository,
) {
    suspend fun buildContextStatus(
        conversationId: String?,
        isContinuingSession: Boolean,
        worldBookHitCount: Int,
        memoryInjectionCount: Int,
        memoryContext: com.example.myapplication.model.MemoryRuntimeContext? = null,
    ): RoleplayContextStatus {
        if (conversationId.isNullOrBlank()) {
            return RoleplayContextStatus(
                isContinuingSession = isContinuingSession,
                worldBookHitCount = worldBookHitCount,
                memoryInjectionCount = memoryInjectionCount,
            )
        }
        val stored =
            if (memoryContext == null) conversationSummaryRepository.getSummary(conversationId)
            else conversationSummaryRepository.getSummaryForContext(memoryContext)
        val sourceMessages = conversationSummaryRepository.getSummarySourceMessages(conversationId)
        val summary =
            if (sourceMessages == null) stored else ConversationSummaryCoverageSupport.usableSummary(
                stored,
                sourceMessages,
                memoryContext
            )
        return RoleplayContextStatus(
            hasSummary = summary?.summary?.isNotBlank() == true,
            summaryCoveredMessageCount = summary?.coveredMessageCount ?: 0,
            worldBookHitCount = worldBookHitCount,
            memoryInjectionCount = memoryInjectionCount,
            isContinuingSession = isContinuingSession,
        )
    }

    suspend fun clearConversationScopedContext(conversationId: String) {
        conversationSummaryRepository.deleteSummary(conversationId)
        memoryRepository.listEntries()
            .filter { entry ->
                entry.scopeType == MemoryScopeType.CONVERSATION &&
                        entry.resolvedScopeId() == conversationId && entry.archiveRevision == null
            }
            .forEach { entry ->
                // 新档案已经由原文清理事务按来源失效；保留其独立人工版本。
                memoryRepository.deleteEntry(entry.id)
            }
    }
}
