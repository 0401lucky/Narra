package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.context.MemoryRepository
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
    fun togglePinned_updatesMemoryAndDeleteSummary_removesSummary() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
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

    @Test
    fun confirmedEntry_keepsSeenRevisionAfterBackgroundUpdate() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val selected =
                MemoryEntry(id = "memory", content = "确认时所见正文", archiveRevision = 3)
            val storage = FakeMemoryRepository(listOf(selected))
            var deletionRevision: Long? = null
            var pinnedRevision: Long? = null
            val repository = object : MemoryRepository by storage {
                override suspend fun deleteEntry(entryId: String, expectedRevision: Long) {
                    deletionRevision = expectedRevision
                    error("记录已有新版本，请重新查看")
                }

                override suspend fun upsertEntry(entry: MemoryEntry) {
                    pinnedRevision = entry.archiveRevision
                    error("记录已有新版本，请重新查看")
                }
            }
            val model = MemoryManagementViewModel(repository, FakeConversationSummaryRepository())
            advanceUntilIdle()
            storage.upsertEntry(
                selected.copy(
                    content = "背景任务生成的新正文",
                    archiveRevision = 4
                )
            )
            advanceUntilIdle()
            model.deleteMemory(selected)
            model.deleteMemory(selected)
            advanceUntilIdle()
            assertEquals(3L, deletionRevision)
            assertEquals(4L, storage.currentEntries().single().archiveRevision)
            assertTrue(model.uiState.value.message.orEmpty().contains("新版本"))
            model.togglePinned(selected)
            advanceUntilIdle()
            assertEquals(3L, pinnedRevision)
            assertTrue(!model.uiState.value.isBusy)
        }
}
