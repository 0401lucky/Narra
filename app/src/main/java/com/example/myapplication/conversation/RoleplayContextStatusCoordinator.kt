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
    ): RoleplayContextStatus {
        if (conversationId.isNullOrBlank()) {
            return RoleplayContextStatus(
                isContinuingSession = isContinuingSession,
                worldBookHitCount = worldBookHitCount,
                memoryInjectionCount = memoryInjectionCount,
            )
        }
        val summary = conversationSummaryRepository.getSummary(conversationId)
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
                    entry.resolvedScopeId() == conversationId
            }
            .forEach { entry ->
                memoryRepository.deleteEntry(entry.id)
            }
    }
}
