package com.example.myapplication.viewmodel

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.viewmodel.SimpleMemoryEditorViewModel.Companion.composeDraft
import com.example.myapplication.viewmodel.SimpleMemoryEditorViewModel.Companion.parseDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SimpleMemoryEditorViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun parseDraft_strips_list_markers_and_dedupes() {
        val text = """
            - 用户喜欢简洁
            * 用户在调查钟楼
            • 用户喜欢简洁

            ·用户的咖啡偏好是黑咖
              - 用户喜欢简洁
        """.trimIndent()

        val parsed = parseDraft(text)

        assertEquals(
            listOf("用户喜欢简洁", "用户在调查钟楼", "用户的咖啡偏好是黑咖"),
            parsed,
        )
    }

    @Test
    fun composeDraft_pinned_first() {
        val now = System.currentTimeMillis()
        val entries = listOf(
            MemoryEntry(id = "1", content = "B 普通", pinned = false, updatedAt = now - 10),
            MemoryEntry(id = "2", content = "A 置顶", pinned = true, updatedAt = now - 100),
            MemoryEntry(id = "3", content = "C 普通新", pinned = false, updatedAt = now),
        )

        val composed = composeDraft(entries)

        assertEquals(
            "- A 置顶\n- C 普通新\n- B 普通",
            composed,
        )
    }

    @Test
    fun initializesDraftFromFirstRepositoryEmission() = runTest {
        val repo = ManualMemoryRepository()
        val assistant = Assistant(id = "assistant-1", name = "陆宴清", useGlobalMemory = false)
        val viewModel = SimpleMemoryEditorViewModel(
            assistantId = "assistant-1",
            conversationId = "conv-1",
            memoryRepository = repo,
            assistantsProvider = { listOf(assistant) },
        )
        runCurrent()

        repo.emit(
            listOf(
                MemoryEntry(
                    id = "core-1",
                    scopeType = MemoryScopeType.ASSISTANT,
                    scopeId = "assistant-1",
                    characterId = "assistant-1",
                    content = "用户记得旧车站那晚",
                ),
                MemoryEntry(
                    id = "scene-1",
                    scopeType = MemoryScopeType.CONVERSATION,
                    scopeId = "conv-1",
                    characterId = "assistant-1",
                    content = "当前会话停在雨夜车站",
                ),
            ),
        )
        advanceUntilIdle()

        assertEquals("- 用户记得旧车站那晚", viewModel.uiState.value.coreDraft)
        assertEquals("- 当前会话停在雨夜车站", viewModel.uiState.value.sceneDraft)
    }

    @Test
    fun save_inserts_new_and_deletes_missing() = runTest {
        val now = 1000L
        val existing = listOf(
            MemoryEntry(
                id = "keep-1",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-1",
                characterId = "assistant-1",
                content = "保留这条",
                pinned = true,
                createdAt = now,
                updatedAt = now,
            ),
            MemoryEntry(
                id = "drop-1",
                scopeType = MemoryScopeType.ASSISTANT,
                scopeId = "assistant-1",
                characterId = "assistant-1",
                content = "应当删掉",
                createdAt = now,
                updatedAt = now,
            ),
        )
        val repo = FakeMemoryRepository(initialEntries = existing)
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            useGlobalMemory = false,
        )
        val viewModel = SimpleMemoryEditorViewModel(
            assistantId = "assistant-1",
            conversationId = null,
            memoryRepository = repo,
            assistantsProvider = { listOf(assistant) },
        )

        // 触发首批快照消费 + 改写草稿：保留 + 新增
        viewModel.uiState.value
        viewModel.updateCoreDraft("- 保留这条\n- 新加的一条\n- 又一条")
        viewModel.save()

        val after = repo.currentEntries()
        val contents = after.map { it.content }.sorted()
        assertEquals(listOf("保留这条", "又一条", "新加的一条").sorted(), contents)
        assertTrue(after.any { it.id == "keep-1" && it.pinned })
        assertTrue(after.none { it.id == "drop-1" })
        // 新增条目落到 ASSISTANT scope，scopeId 为 assistantId
        val newcomers = after.filter { it.id != "keep-1" }
        assertTrue(newcomers.all { it.scopeType == MemoryScopeType.ASSISTANT })
        assertTrue(newcomers.all { it.scopeId == "assistant-1" })
    }

    @Test
    fun save_uses_global_scope_when_assistant_prefers_global() = runTest {
        val repo = FakeMemoryRepository(initialEntries = emptyList())
        val assistant = Assistant(
            id = "assistant-1",
            name = "陆宴清",
            useGlobalMemory = true,
        )
        val viewModel = SimpleMemoryEditorViewModel(
            assistantId = "assistant-1",
            conversationId = null,
            memoryRepository = repo,
            assistantsProvider = { listOf(assistant) },
        )

        viewModel.uiState.value
        viewModel.updateCoreDraft("- 全局记忆 A\n- 全局记忆 B")
        viewModel.save()

        val after = repo.currentEntries()
        assertEquals(2, after.size)
        assertTrue(after.all { it.scopeType == MemoryScopeType.GLOBAL })
        assertTrue(after.all { it.scopeId.isBlank() })
    }

    @Test
    fun save_with_conversation_scene_keeps_core_and_scene_separate() = runTest {
        val now = 2000L
        val sceneEntry = MemoryEntry(
            id = "scene-1",
            scopeType = MemoryScopeType.CONVERSATION,
            scopeId = "conv-7",
            characterId = "assistant-1",
            content = "雨夜停在码头",
            createdAt = now,
            updatedAt = now,
        )
        val repo = FakeMemoryRepository(initialEntries = listOf(sceneEntry))
        val assistant = Assistant(id = "assistant-1", name = "陆宴清", useGlobalMemory = false)
        val viewModel = SimpleMemoryEditorViewModel(
            assistantId = "assistant-1",
            conversationId = "conv-7",
            memoryRepository = repo,
            assistantsProvider = { listOf(assistant) },
        )

        viewModel.uiState.value
        // 核心区追加，剧情区在原有基础上增删
        viewModel.updateCoreDraft("- 用户喜欢简洁")
        viewModel.updateSceneDraft("- 雨夜停在码头\n- 角色对调查表示警惕")
        viewModel.save()

        val after = repo.currentEntries()
        val coreEntries = after.filter { it.scopeType == MemoryScopeType.ASSISTANT }
        val sceneEntries = after.filter { it.scopeType == MemoryScopeType.CONVERSATION }
        assertEquals(listOf("用户喜欢简洁"), coreEntries.map { it.content })
        assertTrue(coreEntries.all { it.scopeId == "assistant-1" })
        assertEquals(setOf("雨夜停在码头", "角色对调查表示警惕"), sceneEntries.map { it.content }.toSet())
        assertTrue(sceneEntries.all { it.scopeId == "conv-7" })
        // 原 scene-1 的 id 因内容保留而沿用
        assertTrue(sceneEntries.any { it.id == "scene-1" })
    }

    private class ManualMemoryRepository : MemoryRepository {
        private val flow = MutableSharedFlow<List<MemoryEntry>>()
        private var entries: List<MemoryEntry> = emptyList()

        override fun observeEntries(): Flow<List<MemoryEntry>> = flow

        override suspend fun listEntries(): List<MemoryEntry> = entries

        override suspend fun findEntryBySourceMessage(
            scopeType: MemoryScopeType,
            scopeId: String,
            sourceMessageId: String,
        ): MemoryEntry? {
            return entries.firstOrNull { entry ->
                entry.scopeType == scopeType &&
                    entry.resolvedScopeId() == scopeId.trim() &&
                    entry.sourceMessageId == sourceMessageId
            }
        }

        override suspend fun upsertEntry(entry: MemoryEntry) {
            entries = entries.filterNot { it.id == entry.id } + entry
        }

        override suspend fun deleteEntry(entryId: String) {
            entries = entries.filterNot { it.id == entryId }
        }

        override suspend fun pruneToCapacity(capacity: Int) = Unit

        override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit

        suspend fun emit(nextEntries: List<MemoryEntry>) {
            entries = nextEntries
            flow.emit(nextEntries)
        }
    }
}
