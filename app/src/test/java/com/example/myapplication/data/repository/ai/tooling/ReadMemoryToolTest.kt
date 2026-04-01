package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PromptMode
import com.example.myapplication.testutil.FakeMemoryRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadMemoryToolTest {
    @Test
    fun execute_returnsRoleplayGroupedMemories() = runBlocking {
        val tool = ReadMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = ReadMemoryTool.NAME,
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryRepository = FakeMemoryRepository(
                    initialEntries = listOf(
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = "assistant-1",
                            content = "角色一贯会先试探，再决定是否交底。",
                            importance = 80,
                        ),
                        MemoryEntry(
                            id = "memory-2",
                            scopeType = MemoryScopeType.CONVERSATION,
                            scopeId = "conversation-1",
                            content = "当前剧情里，角色已经承认自己知道密门位置。",
                            importance = 90,
                        ),
                    ),
                ),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals(1, payload.getAsJsonArray("persistent_memories").size())
        assertEquals(1, payload.getAsJsonArray("scene_state_memories").size())
    }

    @Test
    fun execute_filtersByQueryWithinAccessibleScope() = runBlocking {
        val tool = ReadMemoryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-2",
                name = ReadMemoryTool.NAME,
                argumentsJson = """{"query":"短句","limit":5}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                memoryRepository = FakeMemoryRepository(
                    initialEntries = listOf(
                        MemoryEntry(
                            id = "memory-1",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = "assistant-1",
                            content = "用户喜欢短句回复。",
                        ),
                        MemoryEntry(
                            id = "memory-2",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = "assistant-2",
                            content = "另一个助手的记忆。",
                        ),
                    ),
                ),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    assistant = Assistant(
                        id = "assistant-1",
                        memoryEnabled = true,
                    ),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        val persistentMemories = payload.getAsJsonArray("persistent_memories")
        assertEquals(1, persistentMemories.size())
        assertTrue(persistentMemories[0].asJsonObject["content"].asString.contains("短句"))
    }

    private fun fakeSearchRepository(): com.example.myapplication.data.repository.search.SearchRepository {
        return object : com.example.myapplication.data.repository.search.SearchRepository {
            override suspend fun search(
                source: com.example.myapplication.model.SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): com.example.myapplication.data.repository.search.SearchResult {
                error("测试不应执行搜索")
            }
        }
    }
}
