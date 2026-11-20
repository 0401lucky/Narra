package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationSummaryPromptServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: ConversationSummaryPromptService

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val factory = ApiServiceFactory()
        service = ConversationSummaryPromptService(
            PromptExtrasCore(
                apiServiceFactory = factory,
                apiServiceProvider = { baseUrl, apiKey ->
                    assertEquals(server.url("/v1/").toString(), baseUrl)
                    factory.createLongRunning(baseUrl, apiKey)
                },
                anthropicApiProvider = { _, _ -> error("本摘要测试不应请求其它协议") },
            )
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun roleplaySummary_completeResponsePreservesLongOutputAndFullInput() = runBlocking {
        val input = "合成原文".repeat(3_000) + "尾部706号房"
        val output = "完整摘要".repeat(600) + "末尾未解事项"
        enqueue(output, "stop")

        val result = roleplaySummary(input)

        assertEquals(output, result.content)
        assertEquals(PromptCompletionStatus.COMPLETED, result.status)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val messages =
            JsonParser.parseString(request.body.readUtf8()).asJsonObject.getAsJsonArray("messages")
        assertTrue(messages.any { it.asJsonObject["content"].asString.contains(input) })
    }

    @Test
    fun summary_unknownFinishReasonRetainsUnknownInsteadOfClaimingCompletion() = runBlocking {
        enqueue("兼容供应商正文", null)

        val result = service.generateConversationSummaryResult(
            "合成对话", server.url("/v1/").toString(), "test-key", "summary",
            ProviderApiProtocol.OPENAI_COMPATIBLE, null,
        )

        assertEquals("兼容供应商正文", result.content)
        assertEquals(PromptCompletionStatus.UNKNOWN, result.status)
    }

    @Test
    fun summary_explicitlyIncompleteResponsesNeverReturnUsableContent() = runBlocking {
        listOf("length", "content_filter", "tool_calls").forEach { finishReason ->
            enqueue("看似可用的半段正文", finishReason)
            val failure = runCatching { roleplaySummary("合成来源") }.exceptionOrNull()
            assertTrue("finishReason=$finishReason", failure is IllegalStateException)
        }
    }

    @Test
    fun summary_reasoningOnlyAndBlankResponsesAreFailures() = runBlocking {
        server.enqueue(
            MockResponse().setBody(
                """{"choices":[{"index":0,"message":{"role":"assistant","reasoning_content":"只有思考"},"finish_reason":"stop"}]}""",
            )
        )
        assertTrue(runCatching { roleplaySummary("合成来源") }.exceptionOrNull() is IllegalStateException)
        enqueue("  \n ", "stop")
        assertTrue(runCatching { roleplaySummary("合成来源") }.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun summary_cancellationPropagatesUnchanged() = runBlocking {
        val cancellation = CancellationException("合成取消")
        service = ConversationSummaryPromptService(
            PromptExtrasCore(
                ApiServiceFactory(), apiServiceProvider = { _, _ -> throw cancellation },
                anthropicApiProvider = { _, _ -> error("不应调用") },
            )
        )

        val failure = runCatching { roleplaySummary("合成来源") }.exceptionOrNull()

        assertTrue(failure === cancellation)
        assertEquals(0, server.requestCount)
    }

    private suspend fun roleplaySummary(input: String) =
        service.generateRoleplayConversationSummaryResult(
            input, server.url("/v1/").toString(), "test-key", "summary",
            ProviderApiProtocol.OPENAI_COMPATIBLE, null,
        )

    private fun enqueue(content: String, finishReason: String?) {
        val choice = mutableMapOf<String, Any>(
            "index" to 0,
            "message" to mapOf("role" to "assistant", "content" to content),
        )
        if (finishReason != null) choice["finish_reason"] = finishReason
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody(AppJson.gson.toJson(mapOf("choices" to listOf(choice))))
        )
    }
}
