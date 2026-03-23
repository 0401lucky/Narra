package com.example.myapplication.context

import com.example.myapplication.data.repository.context.WorldBookRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.WorldBookEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptContextAssemblerTest {
    private val assembler = DefaultPromptContextAssembler()

    @Test
    fun assemble_returnsEmptyPromptWhenAssistantMissing() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = null,
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "你好",
            recentMessages = emptyList(),
        )

        assertEquals("", result.systemPrompt)
        assertTrue(result.debugDump.contains("世界书命中数：0"))
    }

    @Test
    fun assemble_appendsScenarioAfterSystemPrompt() = runBlocking {
        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "侦探助手",
                systemPrompt = "你是一名冷静的侦探顾问。",
                scenario = "当前场景是一间维多利亚风格的事务所。",
                greeting = "晚上好，先把案情讲给我听。",
                exampleDialogues = listOf(
                    "用户：嫌疑人昨晚出现过吗？\n助手：先核对门卫记录，再交叉比对时间线。",
                ),
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "帮我分析案情",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.startsWith("你是一名冷静的侦探顾问。"))
        assertTrue(result.systemPrompt.contains("【场景设定】"))
        assertTrue(result.systemPrompt.contains("当前场景是一间维多利亚风格的事务所。"))
        assertTrue(result.systemPrompt.contains("【开场白参考】"))
        assertTrue(result.systemPrompt.contains("晚上好，先把案情讲给我听。"))
        assertTrue(result.systemPrompt.contains("【示例对话】"))
        assertTrue(result.systemPrompt.contains("用户：嫌疑人昨晚出现过吗？"))
        assertTrue(result.debugDump.contains("侦探助手"))
        assertTrue(result.debugDump.contains("世界书命中数：0"))
    }

    @Test
    fun assemble_includesMatchedWorldBookEntries() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            worldBookRepository = object : WorldBookRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<WorldBookEntry>())

                override suspend fun listEntries(): List<WorldBookEntry> = emptyList()

                override suspend fun listEnabledEntries(): List<WorldBookEntry> {
                    return listOf(
                        WorldBookEntry(
                            id = "entry-1",
                            title = "白塔城",
                            content = "白塔城是北境最大的贸易都会。",
                            keywords = listOf("白塔城"),
                        ),
                    )
                }

                override suspend fun getEntry(entryId: String): WorldBookEntry? = null

                override suspend fun upsertEntry(entry: WorldBookEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "设定助手",
                systemPrompt = "你是设定整理专家。",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "介绍一下白塔城",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【背景设定（世界书）】"))
        assertTrue(result.systemPrompt.contains("白塔城"))
        assertTrue(result.debugDump.contains("世界书命中数：1"))
    }

    @Test
    fun assemble_includesSelectedMemoriesWhenEnabled() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = object : com.example.myapplication.data.repository.context.MemoryRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<MemoryEntry>())

                override suspend fun listEntries(): List<MemoryEntry> {
                    return listOf(
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = "assistant-1",
                            content = "用户喜欢把回复写成短句。",
                            pinned = true,
                            importance = 80,
                        ),
                    )
                }

                override suspend fun findEntryBySourceMessage(
                    scopeType: MemoryScopeType,
                    scopeId: String,
                    sourceMessageId: String,
                ): MemoryEntry? = null

                override suspend fun upsertEntry(entry: MemoryEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit

                override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "记忆助手",
                systemPrompt = "你要保持上下文一致。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续聊",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【已知信息（记忆）】"))
        assertTrue(result.systemPrompt.contains("稳定事实、偏好或约束"))
        assertTrue(result.systemPrompt.contains("用户喜欢把回复写成短句。"))
        assertTrue(result.debugDump.contains("记忆注入数：1"))
    }

    @Test
    fun assemble_roleplayMemorySectionAddsStrictConsistencyInstruction() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            memoryRepository = object : com.example.myapplication.data.repository.context.MemoryRepository {
                override fun observeEntries() = kotlinx.coroutines.flow.flowOf(emptyList<MemoryEntry>())

                override suspend fun listEntries(): List<MemoryEntry> {
                    return listOf(
                        MemoryEntry(
                            id = "memory-0",
                            scopeType = MemoryScopeType.GLOBAL,
                            scopeId = "",
                            content = "角色一贯会先试探，再决定是否交底。",
                            importance = 75,
                        ),
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.CONVERSATION,
                            scopeId = "c1",
                            content = "当前剧情里，角色已经承认自己知道密门位置。",
                            importance = 80,
                        ),
                    )
                }

                override suspend fun findEntryBySourceMessage(
                    scopeType: MemoryScopeType,
                    scopeId: String,
                    sourceMessageId: String,
                ): MemoryEntry? = null

                override suspend fun upsertEntry(entry: MemoryEntry) = Unit

                override suspend fun deleteEntry(entryId: String) = Unit

                override suspend fun markEntriesUsed(entryIds: List<String>, timestamp: Long) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "霜岚",
                systemPrompt = "你要始终维持角色设定。",
                memoryEnabled = true,
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续逼问她",
            recentMessages = emptyList(),
            promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
        )

        assertTrue(result.systemPrompt.contains("【角色长期记忆】"))
        assertTrue(result.systemPrompt.contains("【当前剧情约束】"))
        assertTrue(result.systemPrompt.contains("必须保持一致"))
        assertTrue(result.systemPrompt.contains("不要与其冲突"))
    }

    @Test
    fun assemble_includesConversationSummary() = runBlocking {
        val assembler = DefaultPromptContextAssembler(
            conversationSummaryRepository = object : com.example.myapplication.data.repository.context.ConversationSummaryRepository {
                override suspend fun getSummary(conversationId: String): ConversationSummary? {
                    return ConversationSummary(
                        conversationId = conversationId,
                        assistantId = "assistant-1",
                        summary = "用户正在调查白塔城的失窃案，已确认嫌疑人与北境商会有关。",
                        coveredMessageCount = 18,
                        updatedAt = 10L,
                    )
                }

                override suspend fun listSummaries(): List<ConversationSummary> = emptyList()

                override suspend fun upsertSummary(summary: ConversationSummary) = Unit

                override suspend fun deleteSummary(conversationId: String) = Unit
            },
        )

        val result = assembler.assemble(
            settings = AppSettings(),
            assistant = Assistant(
                id = "assistant-1",
                name = "摘要助手",
            ),
            conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
            userInputText = "继续调查",
            recentMessages = emptyList(),
        )

        assertTrue(result.systemPrompt.contains("【对话摘要】"))
        assertTrue(result.systemPrompt.contains("用户正在调查白塔城的失窃案"))
        assertTrue(result.debugDump.contains("摘要注入：是（覆盖 18 条）"))
    }
}
