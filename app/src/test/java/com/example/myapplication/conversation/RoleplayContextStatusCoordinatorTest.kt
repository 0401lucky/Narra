package com.example.myapplication.conversation

import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayContextStatusCoordinatorTest {
    @Test
    fun buildContextStatus_readsSummaryWhenConversationExists() = runBlocking {
        val coordinator = RoleplayContextStatusCoordinator(
            conversationSummaryRepository = FakeConversationSummaryRepository(
                initialSummaries = listOf(
                    ConversationSummary(
                        conversationId = "conv-1",
                        assistantId = "assistant-1",
                        summary = "已有剧情摘要",
                        coveredMessageCount = 8,
                    ),
                ),
            ),
            memoryRepository = FakeMemoryRepository(),
        )

        val status = coordinator.buildContextStatus(
            conversationId = "conv-1",
            isContinuingSession = true,
            worldBookHitCount = 3,
            memoryInjectionCount = 2,
        )

        assertTrue(status.hasSummary)
        assertEquals(8, status.summaryCoveredMessageCount)
        assertEquals(3, status.worldBookHitCount)
        assertEquals(2, status.memoryInjectionCount)
        assertTrue(status.isContinuingSession)
    }

    @Test
    fun clearConversationScopedContext_onlyDeletesConversationScopeEntries() = runBlocking {
        val memoryRepository = FakeMemoryRepository(
            initialEntries = listOf(
                MemoryEntry(
                    id = "conv-1",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "conv-1",
                    content = "剧情状态",
                ),
                MemoryEntry(
                    id = "assistant-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "长期记忆",
                ),
            ),
        )
        val summaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "conv-1",
                    assistantId = "assistant-1",
                    summary = "待删除摘要",
                ),
            ),
        )
        val coordinator = RoleplayContextStatusCoordinator(
            conversationSummaryRepository = summaryRepository,
            memoryRepository = memoryRepository,
        )

        coordinator.clearConversationScopedContext("conv-1")

        assertEquals(1, memoryRepository.currentEntries().size)
        assertEquals(MemoryScopeType.ASSISTANT, memoryRepository.currentEntries().single().scopeType)
        assertEquals(null, summaryRepository.getSummary("conv-1"))
    }
}
