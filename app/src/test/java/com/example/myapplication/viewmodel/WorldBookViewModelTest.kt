package com.example.myapplication.viewmodel

import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.testutil.FakeWorldBookRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorldBookViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun renameBook_updatesAllEntriesInSameBook() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeWorldBookRepository(
            initialEntries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    bookId = "book-1",
                    title = "璃珠都市",
                    content = "A",
                    sourceBookName = "旧书名",
                ),
                WorldBookEntry(
                    id = "entry-2",
                    bookId = "book-1",
                    title = "夜巡守则",
                    content = "B",
                    sourceBookName = "旧书名",
                ),
                WorldBookEntry(
                    id = "entry-3",
                    bookId = "book-2",
                    title = "独立条目",
                    content = "C",
                    sourceBookName = "",
                ),
            ),
        )
        val viewModel = WorldBookViewModel(repository)

        advanceUntilIdle()
        viewModel.renameBook("book-1", "新书名")
        advanceUntilIdle()

        val entriesById = repository.listEntries().associateBy { it.id }
        assertEquals("新书名", entriesById["entry-1"]?.sourceBookName)
        assertEquals("新书名", entriesById["entry-2"]?.sourceBookName)
        assertEquals("", entriesById["entry-3"]?.sourceBookName)
        assertEquals("世界书已重命名", viewModel.uiState.value.message)
    }

    @Test
    fun deleteBook_removesAllEntriesInSameBookOnly() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeWorldBookRepository(
            initialEntries = listOf(
                WorldBookEntry(
                    id = "entry-1",
                    bookId = "book-target",
                    title = "璃珠都市",
                    content = "A",
                    sourceBookName = "目标书",
                ),
                WorldBookEntry(
                    id = "entry-2",
                    bookId = "book-target",
                    title = "夜巡守则",
                    content = "B",
                    sourceBookName = "目标书",
                ),
                WorldBookEntry(
                    id = "entry-3",
                    bookId = "book-keep",
                    title = "白塔城",
                    content = "C",
                    sourceBookName = "保留书",
                ),
            ),
        )
        val viewModel = WorldBookViewModel(repository)

        advanceUntilIdle()
        viewModel.deleteBook("book-target")
        advanceUntilIdle()

        val remainingEntries = repository.listEntries()
        assertEquals(listOf("entry-3"), remainingEntries.map { it.id })
        assertTrue(remainingEntries.all { it.sourceBookName == "保留书" })
        assertEquals("整本世界书已删除", viewModel.uiState.value.message)
    }

    @Test
    fun renameBook_failureEmitsRetryMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeWorldBookRepository(
            initialEntries = listOf(
                WorldBookEntry(id = "e1", bookId = "book-1", title = "t", content = "c", sourceBookName = "旧"),
            ),
        ).apply { failNextBookMutation = true }
        val viewModel = WorldBookViewModel(repository)

        advanceUntilIdle()
        viewModel.renameBook("book-1", "新")
        advanceUntilIdle()

        assertEquals("重命名失败，请重试", viewModel.uiState.value.message)
        assertEquals("旧", repository.listEntries().first().sourceBookName)
    }

    @Test
    fun deleteBook_failureEmitsRetryMessage() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeWorldBookRepository(
            initialEntries = listOf(
                WorldBookEntry(id = "e1", bookId = "book-1", title = "t", content = "c", sourceBookName = "目标"),
            ),
        ).apply { failNextBookMutation = true }
        val viewModel = WorldBookViewModel(repository)

        advanceUntilIdle()
        viewModel.deleteBook("book-1")
        advanceUntilIdle()

        assertEquals("删除失败，请重试", viewModel.uiState.value.message)
        assertEquals(1, repository.listEntries().size)
    }
}
