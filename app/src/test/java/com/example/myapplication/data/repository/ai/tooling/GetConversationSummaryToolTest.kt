package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.PromptMode
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetConversationSummaryToolTest {
    @Test
    fun execute_returnsCurrentConversationSummary() = runBlocking {
        val tool = GetConversationSummaryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-1",
                name = GetConversationSummaryTool.NAME,
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                conversationSummaryRepository = FakeConversationSummaryRepository(
                    initialSummaries = listOf(
                        ConversationSummary(
                            conversationId = "conversation-1",
                            assistantId = "assistant-1",
                            summary = "已经确认主要线索指向北境商会。",
                            coveredMessageCount = 8,
                            updatedAt = 100L,
                        ),
                    ),
                ),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("conversation-1", payload["conversation_id"].asString)
        assertEquals(8, payload["covered_message_count"].asInt)
    }

    @Test
    fun execute_returnsErrorWhenSummaryMissing() = runBlocking {
        val tool = GetConversationSummaryTool()
        val result = tool.execute(
            invocation = ToolInvocation(
                id = "call-2",
                name = GetConversationSummaryTool.NAME,
            ),
            context = ToolContext(
                searchRepository = fakeSearchRepository(),
                conversationSummaryRepository = FakeConversationSummaryRepository(),
                runtimeContext = GatewayToolRuntimeContext(
                    promptMode = PromptMode.CHAT,
                    conversation = Conversation(
                        id = "conversation-1",
                        createdAt = 1L,
                        updatedAt = 1L,
                    ),
                ),
            ),
        )

        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertTrue(payload["error"].asString.contains("暂无缓存摘要"))
        assertTrue(result.isError)
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
