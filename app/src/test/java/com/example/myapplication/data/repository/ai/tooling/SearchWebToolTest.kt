package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.data.repository.search.SearchResultItem
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceType
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWebToolTest {
    private val searchToolConfig = SearchToolConfig(
        source = SearchSourceConfig(
            id = "test-source",
            type = SearchSourceType.BRAVE,
            name = "测试搜索",
            enabled = true,
            apiKey = "test-key",
        ),
        resultCount = 3,
    )

    @Test
    fun execute_supportsJsonArguments() = runBlocking {
        val tool = SearchWebTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = SearchWebTool.NAME,
                argumentsJson = """{"query":"今日汇率"}""",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                searchToolConfig = searchToolConfig,
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("今日汇率", payload["query"].asString)
        assertEquals(1, payload.getAsJsonArray("results").size())
        assertEquals(1, result.citations.size)
        assertTrue(!result.isError)
    }

    @Test
    fun execute_supportsMapArguments() = runBlocking {
        val tool = SearchWebTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-2",
                name = SearchWebTool.NAME,
                argumentsMap = mapOf("query" to "今天油价"),
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                searchToolConfig = searchToolConfig,
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("今天油价", payload["query"].asString)
        assertTrue(!result.isError)
    }

    @Test
    fun execute_returnsErrorPayloadWhenQueryMissing() = runBlocking {
        val tool = SearchWebTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-3",
                name = SearchWebTool.NAME,
                argumentsJson = "{}",
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                searchToolConfig = searchToolConfig,
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("搜索词不能为空", payload["error"].asString)
        assertTrue(result.isError)
    }

    @Test
    fun execute_returnsErrorPayloadWhenSearchFails() = runBlocking {
        val tool = SearchWebTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-4",
                name = SearchWebTool.NAME,
                argumentsJson = """{"query":"坏请求"}""",
            ),
            context = ToolContext(
                searchRepository = object : SearchRepository {
                    override suspend fun search(
                        source: SearchSourceConfig,
                        query: String,
                        resultCount: Int,
                    ): SearchResult {
                        error("搜索失败")
                    }
                },
                searchToolConfig = searchToolConfig,
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("坏请求", payload["query"].asString)
        assertEquals("搜索失败", payload["error"].asString)
        assertTrue(result.isError)
    }

    private fun fakeSearchRepository(): SearchRepository {
        return object : SearchRepository {
            override suspend fun search(
                source: SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): SearchResult {
                return SearchResult(
                    query = query,
                    items = listOf(
                        SearchResultItem(
                            title = "示例来源",
                            url = "https://example.com/search",
                            snippet = "这是搜索摘要",
                            sourceLabel = source.name,
                        ),
                    ),
                )
            }
        }
    }
}
