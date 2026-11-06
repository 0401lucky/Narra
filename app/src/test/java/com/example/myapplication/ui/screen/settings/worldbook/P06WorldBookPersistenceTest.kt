package com.example.myapplication.ui.screen.settings.worldbook

import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.testutil.FakeWorldBookRepository
import com.example.myapplication.viewmodel.MainDispatcherRule
import com.example.myapplication.viewmodel.WorldBookViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 真实 VM + 局部 gate；Fake 不冒充 Room 规范化或数据库事务。 */
@OptIn(ExperimentalCoroutinesApi::class)
class P06WorldBookPersistenceTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveEntry_keepsBusyUntilWriteCompletesAndPreservesPayload() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val original = fixture()
            val delegate = FakeWorldBookRepository(listOf(original))
            val gate = CompletableDeferred<Unit>()
            val writes = mutableListOf<WorldBookEntry>()
            val repository = object : WorldBookRepository by delegate {
                override suspend fun upsertEntry(entry: WorldBookEntry) {
                    writes += entry
                    gate.await()
                    delegate.upsertEntry(entry)
                }
            }
            val viewModel = WorldBookViewModel(repository)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                runCurrent()
                val edited = original.copy(title = "已编辑", content = "保留正文换行\n")
                viewModel.saveEntry(edited)
                runCurrent()
                assertTrue(viewModel.uiState.value.isSaving)
                assertEquals(original, delegate.getEntry(original.id))
                val written = writes.single()
                assertEquals(edited.copy(updatedAt = written.updatedAt), written)
                gate.complete(Unit)
                runCurrent()
                assertFalse(viewModel.uiState.value.isSaving)
                assertEquals(written, delegate.getEntry(original.id))
                assertEquals("世界书已保存", viewModel.uiState.value.message)
                viewModel.consumeMessage()
                runCurrent()
                assertNull(viewModel.uiState.value.message)
            } finally {
                gate.complete(Unit)
                collector.cancel()
            }
        }

    @Test
    fun saveEntry_failureRestoresBusyWithoutChangingStoredEntry() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val original = fixture()
            val delegate = FakeWorldBookRepository(listOf(original))
            val gate = CompletableDeferred<Unit>()
            var attempts = 0
            val repository = object : WorldBookRepository by delegate {
                override suspend fun upsertEntry(entry: WorldBookEntry) {
                    attempts++
                    gate.await()
                    error("合成条目保存失败")
                }
            }
            val viewModel = WorldBookViewModel(repository)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                runCurrent()
                viewModel.saveEntry(original.copy(title = "未写入"))
                runCurrent()
                assertTrue(viewModel.uiState.value.isSaving)
                gate.complete(Unit)
                runCurrent()
                assertEquals(1, attempts)
                assertFalse(viewModel.uiState.value.isSaving)
                assertEquals("保存失败，请重试", viewModel.uiState.value.message)
                assertEquals(listOf(original), delegate.listEntries())
            } finally {
                gate.complete(Unit)
                collector.cancel()
            }
        }

    @Test
    fun deleteEntry_waitsForWriteAndDeletesOnlyTheRequestedId() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val original = fixture()
            val other = original.copy(id = "other-entry")
            val delegate = FakeWorldBookRepository(listOf(original, other))
            val gate = CompletableDeferred<Unit>()
            val deleted = mutableListOf<String>()
            val repository = object : WorldBookRepository by delegate {
                override suspend fun deleteEntry(entryId: String) {
                    deleted += entryId
                    gate.await()
                    delegate.deleteEntry(entryId)
                }
            }
            val viewModel = WorldBookViewModel(repository)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                runCurrent()
                viewModel.deleteEntry(original.id)
                runCurrent()
                assertTrue(viewModel.uiState.value.isSaving)
                assertEquals(2, delegate.listEntries().size)
                gate.complete(Unit)
                runCurrent()
                assertEquals(listOf(original.id), deleted)
                assertFalse(viewModel.uiState.value.isSaving)
                assertEquals(listOf(other), delegate.listEntries())
                assertEquals("世界书已删除", viewModel.uiState.value.message)
            } finally {
                gate.complete(Unit)
                collector.cancel()
            }
        }

    @Test
    fun deleteEntry_failureKeepsStoredDataAndReturnsFixedMessage() =
        runTest(mainDispatcherRule.dispatcher.scheduler) {
            val original = fixture()
            val delegate = FakeWorldBookRepository(listOf(original))
            val gate = CompletableDeferred<Unit>()
            val repository = object : WorldBookRepository by delegate {
                override suspend fun deleteEntry(entryId: String) {
                    gate.await()
                    error("合成删除失败")
                }
            }
            val viewModel = WorldBookViewModel(repository)
            val collector = launch { viewModel.uiState.collect {} }
            try {
                runCurrent()
                viewModel.deleteEntry(original.id)
                runCurrent()
                assertTrue(viewModel.uiState.value.isSaving)
                gate.complete(Unit)
                runCurrent()
                assertFalse(viewModel.uiState.value.isSaving)
                assertEquals("删除失败，请重试", viewModel.uiState.value.message)
                assertEquals(listOf(original), delegate.listEntries())
            } finally {
                gate.complete(Unit)
                collector.cancel()
            }
        }

    private fun fixture() = WorldBookEntry(
        id = "p06-persistence-entry", bookId = "p06-persistence-book", sourceBookName = "原书名",
        title = "原条目", content = "原正文\n", createdAt = 10, updatedAt = 20,
        extrasJson = "{\"depth\":4,\"unknown\":true}",
    )
}
