package com.example.myapplication.viewmodel

import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemoryManagementViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun togglePinned_updatesMemoryAndDeleteSummary_removesSummary() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val memoryRepository = FakeMemoryRepository(
            initialEntries = listOf(
                MemoryEntry(
                    id = "memory-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    content = "用户喜欢短句回复",
                    pinned = false,
                ),
            ),
        )
        val summaryRepository = FakeConversationSummaryRepository(
            initialSummaries = listOf(
                ConversationSummary(
                    conversationId = "c1",
                    summary = "摘要内容",
                ),
            ),
        )
        val viewModel = MemoryManagementViewModel(
            memoryRepository = memoryRepository,
            conversationSummaryRepository = summaryRepository,
        )

        advanceUntilIdle()

        viewModel.togglePinned("memory-1")
        advanceUntilIdle()
        assertTrue(memoryRepository.currentEntries().single().pinned)

        viewModel.deleteSummary("c1")
        advanceUntilIdle()
        assertEquals(emptyList<ConversationSummary>(), summaryRepository.listSummaries())
    }
}
