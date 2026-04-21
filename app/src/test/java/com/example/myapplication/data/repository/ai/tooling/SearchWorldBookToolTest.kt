package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.model.WorldBookScopeType
import com.example.myapplication.testutil.FakeWorldBookRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWorldBookToolTest {
    @Test
    fun execute_returnsOnlyAccessibleEntries() = runBlocking {
        val tool = SearchWorldBookTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = SearchWorldBookTool.NAME,
                argumentsJson = """{"query":"白塔城"}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                worldBookRepository = FakeWorldBookRepository(
                    initialEntries = listOf(
                        WorldBookEntry(
                            id = "entry-1",
                            title = "白塔城",
                            content = "白塔城是北境最大的贸易都会。",
                            enabled = true,
                            scopeType = WorldBookScopeType.GLOBAL,
                        ),
                        WorldBookEntry(
                            id = "entry-2",
                            title = "白塔城密档",
                            content = "只属于别的会话。",
                            enabled = true,
                            scopeType = WorldBookScopeType.CONVERSATION,
                            scopeId = "conversation-2",
                        ),
                    ),
                ),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    assistant = Assistant(id = "assistant-1"),
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        val entries = payload.getAsJsonArray("entries")
        assertEquals(1, entries.size())
        assertTrue(entries[0].asJsonObject["title"].asString.contains("白塔城"))
    }

    @Test
    fun execute_returnsTotalAndTruncatedFlags() = runBlocking {
        val tool = SearchWorldBookTool()
        val entries = (1..6).map { index ->
            WorldBookEntry(
                id = "entry-$index",
                title = "条目$index",
                content = "包含白塔城的内容$index",
                enabled = true,
                scopeType = WorldBookScopeType.GLOBAL,
            )
        }
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = SearchWorldBookTool.NAME,
                argumentsJson = """{"query":"白塔城","limit":3}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                worldBookRepository = FakeWorldBookRepository(initialEntries = entries),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    assistant = Assistant(id = "assistant-1"),
                    conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
                ),
            ),
        )
        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals(6, payload["total"].asInt)
        assertEquals(true, payload["truncated"].asBoolean)
        assertEquals(3, payload.getAsJsonArray("entries").size())
    }

    @Test
    fun execute_flagsContentTruncatedWhenOver400Chars() = runBlocking {
        val longContent = "白塔城" + "x".repeat(500)
        val tool = SearchWorldBookTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = SearchWorldBookTool.NAME,
                argumentsJson = """{"query":"白塔城"}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                worldBookRepository = FakeWorldBookRepository(
                    initialEntries = listOf(
                        WorldBookEntry(
                            id = "entry-1",
                            title = "白塔城",
                            content = longContent,
                            enabled = true,
                            scopeType = WorldBookScopeType.GLOBAL,
                        ),
                    ),
                ),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    assistant = Assistant(id = "assistant-1"),
                    conversation = Conversation(id = "c1", createdAt = 1L, updatedAt = 1L),
                ),
            ),
        )
        val payload = JsonParser.parseString(result.payload).asJsonObject
        val entries = payload.getAsJsonArray("entries")
        assertEquals(true, entries[0].asJsonObject["content_truncated"].asBoolean)
    }

    @Test
    fun description_mentionsFullTextSearchSemantics() {
        val tool = SearchWorldBookTool()
        assertTrue("description=${tool.description}", tool.description.contains("全文搜索"))
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
