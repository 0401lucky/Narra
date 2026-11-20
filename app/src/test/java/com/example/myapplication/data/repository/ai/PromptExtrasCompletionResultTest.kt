package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.AnthropicMessageRequest
import com.example.myapplication.model.AnthropicMessageResponse
import com.example.myapplication.model.AnthropicModelsResponse
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.TestOpenAiCompatibleApi
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class PromptExtrasCompletionResultTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun requestResult_chat_preservesCompletionAndRequestContract() = runTest {
        enqueue(chatResponse(content = "  $EMPTY_ARTIFACT  "))
        val provider = provider().copy(chatCompletionsPath = "/custom//chat/completions")

        val result = requestResult(provider = provider)

        assertEquals(EMPTY_ARTIFACT, result.content)
        assertEquals(PromptCompletionStatus.COMPLETED, result.status)
        assertEquals("stop", result.rawFinishReason)
        assertNull(result.rawStatus)
        val recorded = takeRequest()
        assertEquals("/v1/custom/chat/completions", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer $TEST_KEY", recorded.getHeader("Authorization"))
        assertNull(recorded.getHeader("x-api-key"))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals(TEST_MODEL, body.get("model").asString)
        assertFalse(body.get("stream").asBoolean)
        assertEquals(256, body.get("max_tokens").asInt)
        assertEquals(0.6f, body.get("temperature").asFloat, 0.0001f)
        assertEquals(0.8f, body.get("top_p").asFloat, 0.0001f)
        assertEquals(
            SYSTEM_PROMPT,
            body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString
        )
        assertEquals(
            USER_PROMPT,
            body.getAsJsonArray("messages")[1].asJsonObject.get("content").asString
        )
    }

    @Test
    fun requestResult_responses_preservesCompletionAndRequestContract() = runTest {
        enqueue(responsesResponse())

        val result = requestResult(provider = provider(TestProtocol.RESPONSES))

        assertEquals(EMPTY_ARTIFACT, result.content)
        assertEquals(PromptCompletionStatus.COMPLETED, result.status)
        assertEquals("completed", result.rawStatus)
        assertNull(result.rawFinishReason)
        val recorded = takeRequest()
        assertEquals("/v1/responses", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals("Bearer $TEST_KEY", recorded.getHeader("Authorization"))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals(TEST_MODEL, body.get("model").asString)
        assertEquals(SYSTEM_PROMPT, body.get("instructions").asString)
        assertEquals(256, body.get("max_output_tokens").asInt)
        assertFalse(body.has("max_tokens"))
        assertFalse(body.has("messages"))
        assertFalse(body.get("stream").asBoolean)
        assertEquals("low", body.getAsJsonObject("reasoning").get("effort").asString)
        val input = body.getAsJsonArray("input")
        assertEquals(1, input.size())
        assertEquals("user", input[0].asJsonObject.get("role").asString)
        assertEquals(USER_PROMPT, input[0].asJsonObject.get("content").asString)
    }

    @Test
    fun requestResult_anthropic_preservesCompletionAndRequestContract() = runTest {
        enqueue(anthropicResponse())

        val result = requestResult(provider = provider(TestProtocol.ANTHROPIC))

        assertEquals(EMPTY_ARTIFACT, result.content)
        assertEquals(PromptCompletionStatus.COMPLETED, result.status)
        assertEquals("end_turn", result.rawFinishReason)
        assertNull(result.rawStatus)
        val recorded = takeRequest()
        assertEquals("/v1/messages", recorded.path)
        assertEquals("POST", recorded.method)
        assertEquals(TEST_KEY, recorded.getHeader("x-api-key"))
        assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
        assertNull(recorded.getHeader("Authorization"))
        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals(TEST_MODEL, body.get("model").asString)
        assertEquals(SYSTEM_PROMPT, body.get("system").asString)
        assertEquals(256, body.get("max_tokens").asInt)
        assertFalse(body.get("stream").asBoolean)
        val messages = body.getAsJsonArray("messages")
        assertEquals(1, messages.size())
        assertEquals("user", messages[0].asJsonObject.get("role").asString)
        val content = messages[0].asJsonObject.getAsJsonArray("content")[0].asJsonObject
        assertEquals("text", content.get("type").asString)
        assertEquals(USER_PROMPT, content.get("text").asString)
    }

    @Test
    fun requestResult_chat_classifiesLimitsFiltersAndInterruptedTurns() = runTest {
        val cases = mapOf(
            "length" to PromptCompletionStatus.TRUNCATED,
            "max_tokens" to PromptCompletionStatus.TRUNCATED,
            "max_output_tokens" to PromptCompletionStatus.TRUNCATED,
            "max_completion_tokens" to PromptCompletionStatus.TRUNCATED,
            "context_length_exceeded" to PromptCompletionStatus.TRUNCATED,
            "model_context_window_exceeded" to PromptCompletionStatus.TRUNCATED,
            "content_filter" to PromptCompletionStatus.FILTERED,
            "refusal" to PromptCompletionStatus.FILTERED,
            "tool_calls" to PromptCompletionStatus.INCOMPLETE,
            "function_call" to PromptCompletionStatus.INCOMPLETE,
            "future_stop_reason" to PromptCompletionStatus.INCOMPLETE,
            "failed" to PromptCompletionStatus.FAILED,
            "cancelled" to PromptCompletionStatus.CANCELLED,
        )
        val core = createCore()
        for ((reason, expected) in cases) {
            enqueue(chatResponse(finishReason = reason))
            val result = requestResult(core = core)
            assertEquals(reason, expected, result.status)
            assertEquals(reason, result.rawFinishReason)
            assertEquals(EMPTY_ARTIFACT, result.content)
        }
        assertEquals(cases.size, server.requestCount)
    }

    @Test
    fun requestResult_responses_classifiesIncompleteDetails() = runTest {
        val cases = mapOf(
            "max_output_tokens" to PromptCompletionStatus.TRUNCATED,
            "content_filter" to PromptCompletionStatus.FILTERED,
            "steered" to PromptCompletionStatus.INCOMPLETE,
            "max_messages" to PromptCompletionStatus.INCOMPLETE,
            "future_incomplete_reason" to PromptCompletionStatus.INCOMPLETE,
        )
        val core = createCore()
        for ((reason, expected) in cases) {
            enqueue(responsesResponse(status = "incomplete", reason = reason))
            val result = requestResult(provider(TestProtocol.RESPONSES), core)
            assertEquals(reason, expected, result.status)
            assertEquals(reason, result.rawFinishReason)
            assertEquals("incomplete", result.rawStatus)
            assertEquals(EMPTY_ARTIFACT, result.content)
        }
        assertEquals(cases.size, server.requestCount)
    }

    @Test
    fun requestResult_responses_doesNotCompleteOtherLifecycleStates() = runTest {
        val cases = mapOf(
            "queued" to PromptCompletionStatus.INCOMPLETE,
            "in_progress" to PromptCompletionStatus.INCOMPLETE,
            "incomplete" to PromptCompletionStatus.INCOMPLETE,
            "failed" to PromptCompletionStatus.FAILED,
            "cancelled" to PromptCompletionStatus.CANCELLED,
            "future_status" to PromptCompletionStatus.INCOMPLETE,
        )
        val core = createCore()
        for ((status, expected) in cases) {
            enqueue(responsesResponse(status = status))
            val result = requestResult(provider(TestProtocol.RESPONSES), core)
            assertEquals(status, expected, result.status)
            assertEquals(status, result.rawStatus)
            assertEquals(EMPTY_ARTIFACT, result.content)
        }
        assertEquals(cases.size, server.requestCount)
    }

    @Test
    fun requestResult_anthropic_classifiesStopReasons() = runTest {
        val cases = mapOf(
            "end_turn" to PromptCompletionStatus.COMPLETED,
            "stop_sequence" to PromptCompletionStatus.COMPLETED,
            "max_tokens" to PromptCompletionStatus.TRUNCATED,
            "model_context_window_exceeded" to PromptCompletionStatus.TRUNCATED,
            "tool_use" to PromptCompletionStatus.INCOMPLETE,
            "pause_turn" to PromptCompletionStatus.INCOMPLETE,
            "refusal" to PromptCompletionStatus.FILTERED,
            "future_stop_reason" to PromptCompletionStatus.INCOMPLETE,
        )
        val core = createCore()
        for ((reason, expected) in cases) {
            enqueue(anthropicResponse(stopReason = reason))
            val result = requestResult(provider(TestProtocol.ANTHROPIC), core)
            assertEquals(reason, expected, result.status)
            assertEquals(reason, result.rawFinishReason)
            assertEquals(EMPTY_ARTIFACT, result.content)
        }
        assertEquals(cases.size, server.requestCount)
    }

    @Test
    fun requestResult_missingOrBlankCompletionEvidence_isUnknown() = runTest {
        val core = createCore()
        for (protocol in TestProtocol.entries) {
            for (reason in listOf(null, " ")) {
                enqueue(protocolResponse(protocol, reason = reason))
                val result = requestResult(provider(protocol), core)
                assertEquals(protocol.name, PromptCompletionStatus.UNKNOWN, result.status)
                assertEquals(EMPTY_ARTIFACT, result.content)
                if (protocol == TestProtocol.RESPONSES) {
                    assertEquals(reason, result.rawStatus)
                } else {
                    assertEquals(reason, result.rawFinishReason)
                }
            }
        }
    }

    @Test
    fun requestResult_explicitNullCompletionEvidence_isUnknown() = runTest {
        val responses = mapOf(
            TestProtocol.CHAT to """{"choices":[{"finish_reason":null,"message":{"content":"{}"}}]}""",
            TestProtocol.RESPONSES to """
                {"status":null,"incomplete_details":null,
                 "output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}]}
            """.trimIndent(),
            TestProtocol.ANTHROPIC to """{"stop_reason":null,"content":[{"type":"text","text":"{}"}]}""",
        )
        for ((protocol, body) in responses) {
            enqueue(body)
            val result = requestResult(provider(protocol))
            assertEquals(protocol.name, PromptCompletionStatus.UNKNOWN, result.status)
            assertEquals("{}", result.content)
            assertNull(result.rawFinishReason)
            assertNull(result.rawStatus)
        }
    }

    @Test
    fun requestResult_normalizesStatusWithoutRewritingRawEvidence() = runTest {
        val reasons = mapOf(
            TestProtocol.CHAT to " STOP ",
            TestProtocol.RESPONSES to " Completed ",
            TestProtocol.ANTHROPIC to " END_TURN ",
        )
        for ((protocol, reason) in reasons) {
            enqueue(protocolResponse(protocol, reason = reason))
            val result = requestResult(provider(protocol))
            assertEquals(PromptCompletionStatus.COMPLETED, result.status)
            assertEquals(reason, result.rawStatus ?: result.rawFinishReason)
        }
    }

    @Test
    fun requestResult_chat_refusalOverridesStopOrMissingReason() = runTest {
        for (reason in listOf("stop", null)) {
            enqueue(
                chatResponse(
                    finishReason = reason,
                    messageFields = mapOf("refusal" to "合成拒绝内容")
                )
            )
            val result = requestResult()
            assertEquals(PromptCompletionStatus.FILTERED, result.status)
            assertEquals(EMPTY_ARTIFACT, result.content)
            assertEquals(reason, result.rawFinishReason)
        }
    }

    @Test
    fun requestResult_chat_refusalContentPartOverridesStop() = runTest {
        enqueue(
            chatResponse(
                content = listOf(
                    mapOf("type" to "text", "text" to EMPTY_ARTIFACT),
                    mapOf("type" to "refusal", "refusal" to "合成拒绝内容"),
                ),
            ),
        )

        val result = requestResult()

        assertEquals(PromptCompletionStatus.FILTERED, result.status)
        assertEquals(EMPTY_ARTIFACT, result.content)
        assertEquals("stop", result.rawFinishReason)
    }

    @Test
    fun requestResult_chat_pendingToolsOverrideStopOrMissingReason() = runTest {
        val toolFields = listOf(
            mapOf(
                "tool_calls" to listOf(
                    mapOf(
                        "id" to "call_1",
                        "type" to "function",
                        "function" to mapOf("name" to "lookup", "arguments" to "{}"),
                    ),
                ),
            ),
            mapOf("function_call" to mapOf("name" to "lookup", "arguments" to "{}")),
        )
        for (fields in toolFields) {
            for (reason in listOf("stop", null)) {
                enqueue(chatResponse(finishReason = reason, messageFields = fields))
                val result = requestResult()
                assertEquals(PromptCompletionStatus.INCOMPLETE, result.status)
                assertEquals(EMPTY_ARTIFACT, result.content)
            }
        }
    }

    @Test
    fun requestResult_responses_refusalOverridesCompleted() = runTest {
        enqueue(
            """
            {
              "status":"completed",
              "output":[{"type":"message","content":[
                {"type":"output_text","text":"{}"},
                {"type":"refusal","refusal":"合成拒绝内容"}
              ]}]
            }
            """.trimIndent(),
        )

        val result = requestResult(provider(TestProtocol.RESPONSES))

        assertEquals(PromptCompletionStatus.FILTERED, result.status)
        assertEquals("{}", result.content)
        assertEquals("completed", result.rawStatus)
    }

    @Test
    fun requestResult_responses_pendingFunctionOverridesCompleted() = runTest {
        enqueue(
            """
            {
              "status":"completed",
              "output":[
                {"type":"message","status":"completed","content":[{"type":"output_text","text":"{}"}]},
                {"type":"function_call","status":"completed","call_id":"call_1","name":"lookup","arguments":"{}"}
              ]
            }
            """.trimIndent(),
        )

        val result = requestResult(provider(TestProtocol.RESPONSES))

        assertEquals(PromptCompletionStatus.INCOMPLETE, result.status)
        assertEquals("{}", result.content)
        assertEquals("completed", result.rawStatus)
    }

    @Test
    fun requestResult_responses_unfinishedOutputOverridesCompleted() = runTest {
        val cases = mapOf(
            "in_progress" to PromptCompletionStatus.INCOMPLETE,
            "incomplete" to PromptCompletionStatus.INCOMPLETE,
            "failed" to PromptCompletionStatus.FAILED,
            "cancelled" to PromptCompletionStatus.CANCELLED,
            "future_item_status" to PromptCompletionStatus.INCOMPLETE,
        )
        for ((itemStatus, expected) in cases) {
            enqueue(responsesResponse(itemStatus = itemStatus))
            val result = requestResult(provider(TestProtocol.RESPONSES))
            assertEquals(itemStatus, expected, result.status)
            assertEquals("completed", result.rawStatus)
            assertEquals(EMPTY_ARTIFACT, result.content)
        }
    }

    @Test
    fun requestResult_responses_incompleteDetailsWithoutReasonCannotComplete() = runTest {
        enqueue(
            """
            {"status":"completed","incomplete_details":{},
             "output":[{"type":"message","content":[{"type":"output_text","text":"{}"}]}]}
            """.trimIndent(),
        )

        val result = requestResult(provider(TestProtocol.RESPONSES))

        assertEquals(PromptCompletionStatus.INCOMPLETE, result.status)
        assertEquals("{}", result.content)
        assertNull(result.rawFinishReason)
    }

    @Test
    fun requestResult_responses_errorPreservesCodeWithoutCopyingErrorMessage() = runTest {
        enqueue(
            """
            {
              "status":"completed",
              "error":{"code":"server_error","message":"Authorization: Bearer sk-error-body-secret"},
              "output":[{"type":"message","content":[{"type":"output_text","text":"partial"}]}]
            }
            """.trimIndent(),
        )

        val result = requestResult(provider(TestProtocol.RESPONSES))

        assertEquals(PromptCompletionStatus.FAILED, result.status)
        assertEquals("server_error", result.rawFinishReason)
        assertEquals("completed", result.rawStatus)
        assertEquals("partial", result.content)
        assertFalse(AppJson.gson.toJson(result).contains("sk-error-body-secret"))
    }

    @Test
    fun requestResult_anthropic_pendingToolOverridesEndTurn() = runTest {
        enqueue(
            """
            {
              "stop_reason":"end_turn",
              "content":[
                {"type":"text","text":"{}"},
                {"type":"tool_use","id":"call_1","name":"lookup","input":{}}
              ]
            }
            """.trimIndent(),
        )

        val result = requestResult(provider(TestProtocol.ANTHROPIC))

        assertEquals(PromptCompletionStatus.INCOMPLETE, result.status)
        assertEquals("{}", result.content)
        assertEquals("end_turn", result.rawFinishReason)
    }

    @Test
    fun requestResult_reasoningOnlyResponsesKeepVisibleContentEmpty() = runTest {
        val responses = mapOf(
            TestProtocol.CHAT to """{"choices":[{"finish_reason":"stop","message":{"content":null,"reasoning_content":"{}"}}]}""",
            TestProtocol.RESPONSES to """
                {"status":"completed",
                 "output":[{"type":"reasoning","summary":[{"type":"summary_text","text":"{}"}]}]}
            """.trimIndent(),
            TestProtocol.ANTHROPIC to """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"{}"}]}""",
        )
        for ((protocol, body) in responses) {
            enqueue(body)
            val result = requestResult(provider(protocol))
            assertEquals(protocol.name, "", result.content)
            assertEquals(PromptCompletionStatus.COMPLETED, result.status)
        }
    }

    @Test
    fun requestResult_emptyTextAndValidEmptyArtifactRemainDistinct() = runTest {
        for (protocol in TestProtocol.entries) {
            enqueue(protocolResponse(protocol, content = "  "))
            enqueue(protocolResponse(protocol))

            val noContent = requestResult(provider(protocol))
            val emptyArtifact = requestResult(provider(protocol))

            assertEquals(protocol.name, "", noContent.content)
            assertEquals(EMPTY_ARTIFACT, emptyArtifact.content)
            assertEquals(PromptCompletionStatus.COMPLETED, noContent.status)
            assertEquals(PromptCompletionStatus.COMPLETED, emptyArtifact.status)
        }
    }

    @Test
    fun requestResult_absentOutputHasNoCompletionEvidence() = runTest {
        val responses = mapOf(
            TestProtocol.CHAT to """{"choices":[]}""",
            TestProtocol.RESPONSES to """{"output":[]}""",
            TestProtocol.ANTHROPIC to """{"content":[]}""",
        )
        for ((protocol, body) in responses) {
            enqueue(body)
            val result = requestResult(provider(protocol))
            assertEquals("", result.content)
            assertEquals(PromptCompletionStatus.UNKNOWN, result.status)
        }
    }

    @Test
    fun requestCompletionContent_keepsLegacyTextForAllProtocolsAndStates() = runTest {
        val core = createCore()
        for (protocol in TestProtocol.entries) {
            val incompleteReason = when (protocol) {
                TestProtocol.CHAT -> "length"
                TestProtocol.RESPONSES -> "incomplete"
                TestProtocol.ANTHROPIC -> "max_tokens"
            }
            for (reason in listOf(incompleteReason, null)) {
                enqueue(protocolResponse(protocol, content = "  旧接口正文  ", reason = reason))
                val provider = provider(protocol)
                val content = core.requestCompletionContent(
                    baseUrl = provider.baseUrl,
                    apiKey = provider.apiKey,
                    operation = "测试旧接口",
                    request = completionRequest(),
                    apiProtocol = provider.resolvedApiProtocol(),
                    provider = provider,
                )
                assertEquals(protocol.name, "旧接口正文", content)
            }
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun requestResult_samplingFallbackRetainsFinalStatusAndSharedCache() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(400)
                .setBody("""{"error":{"message":"unsupported temperature parameter"}}""")
        )
        enqueue(chatResponse(finishReason = "length"))
        val provider = provider()
        val core = createCore()
        val sampledRequest = core.buildRequestWithRoleplaySampling(
            model = TEST_MODEL,
            messages = completionRequest().messages,
            baseUrl = provider.baseUrl,
            apiProtocol = provider.resolvedApiProtocol(),
        )

        val result =
            requestResult(provider, core, sampledRequest, allowRoleplaySamplingFallback = true)

        assertEquals(PromptCompletionStatus.TRUNCATED, result.status)
        assertEquals("length", result.rawFinishReason)
        val first = JsonParser.parseString(takeRequest().body.readUtf8()).asJsonObject
        val retry = JsonParser.parseString(takeRequest().body.readUtf8()).asJsonObject
        assertTrue(first.has("temperature"))
        assertTrue(first.has("top_p"))
        assertFalse(retry.has("temperature"))
        assertFalse(retry.has("top_p"))
        val next = core.buildRequestWithRoleplaySampling(
            model = TEST_MODEL,
            messages = sampledRequest.messages,
            baseUrl = provider.baseUrl,
            apiProtocol = provider.resolvedApiProtocol(),
        )
        assertNull(next.temperature)
        assertNull(next.topP)
        enqueue(chatResponse(content = "旧接口复用缓存"))
        assertEquals(
            "旧接口复用缓存",
            core.requestCompletionContent(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                operation = "测试旧接口",
                request = next,
                apiProtocol = provider.resolvedApiProtocol(),
                provider = provider,
                allowRoleplaySamplingFallback = true,
            ),
        )
        assertEquals(3, server.requestCount)
    }

    @Test
    fun requestResult_samplingFallbackDoesNotRetryUnrelated400OrResponses() = runTest {
        val cases = mapOf(
            TestProtocol.CHAT to "model not found",
            TestProtocol.RESPONSES to "unsupported temperature parameter",
        )
        for ((protocol, message) in cases) {
            server.enqueue(
                MockResponse().setResponseCode(400).setBody("""{"error":{"message":"$message"}}""")
            )
            val failure = runCatching {
                requestResult(provider(protocol), allowRoleplaySamplingFallback = true)
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message.orEmpty().contains("400"))
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun requestResult_httpFailureStaysRedactedAndDoesNotRetry() = runTest {
        for (protocol in TestProtocol.entries) {
            server.enqueue(
                MockResponse().setResponseCode(503)
                    .addHeader("x-request-id", "protocol-test-request")
                    .addHeader("retry-after", "3")
                    .setBody(
                        """
                        {"error":{"message":"Authorization: Bearer sk-protocol-private-key",
                                  "api_key":"sk-another-private-key"}}
                        """.trimIndent(),
                    ),
            )

            val failure = runCatching { requestResult(provider(protocol)) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            val message = failure?.message.orEmpty()
            assertTrue(message.contains("503"))
            assertTrue(message.contains("protocol-test-request"))
            assertTrue(message.contains("retry-after: 3"))
            assertFalse(message.contains("sk-protocol-private-key"))
            assertFalse(message.contains("sk-another-private-key"))
            assertTrue(message.contains("[REDACTED]"))
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun requestResult_invalidJsonAndMissingResponseBodyThrow() = runTest {
        for (protocol in TestProtocol.entries) {
            enqueue("{")
            val invalidJson = runCatching { requestResult(provider(protocol)) }.exceptionOrNull()
            assertNotNull(protocol.name, invalidJson)
            server.enqueue(MockResponse().setResponseCode(204))
            val noBody = runCatching { requestResult(provider(protocol)) }.exceptionOrNull()
            assertTrue(noBody is IllegalStateException)
            assertTrue(noBody?.message.orEmpty().contains("响应体为空"))
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun requestResult_cancellationStopsPendingNetworkRequests() = runTest {
        val core = createCore()
        for (protocol in TestProtocol.entries) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending = async(Dispatchers.Default) { requestResult(provider(protocol), core) }
            try {
                val recorded =
                    withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
                assertNotNull(protocol.name, recorded)
                pending.cancel(CancellationException("测试取消"))
                val failure = runCatching { pending.await() }.exceptionOrNull()
                assertTrue(failure is CancellationException)
                assertTrue(pending.isCancelled)
            } finally {
                pending.cancelAndJoin()
            }
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun requestResult_uncertainIOExceptionIsNotRetried() = runTest {
        val failure = IOException("请求结果未知")
        var calls = 0
        val core = throwingCore(failure) { calls++ }
        for (protocol in TestProtocol.entries) {
            val actual = runCatching {
                requestResult(
                    provider(protocol),
                    core,
                    allowRoleplaySamplingFallback = true
                )
            }
                .exceptionOrNull()
            assertSame(failure, actual)
        }
        assertEquals(3, calls)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun requestResult_apiCancellationPropagatesWithoutRetryOrResult() = runTest {
        val cancellation = CancellationException("测试取消")
        var calls = 0
        val core = throwingCore(cancellation) { calls++ }
        for (protocol in TestProtocol.entries) {
            val actual = runCatching {
                requestResult(
                    provider(protocol),
                    core,
                    allowRoleplaySamplingFallback = true
                )
            }
                .exceptionOrNull()
            assertSame(cancellation, actual)
        }
        assertEquals(3, calls)
        assertEquals(0, server.requestCount)
    }

    private fun createCore(): PromptExtrasCore {
        val factory = ApiServiceFactory()
        return PromptExtrasCore(
            apiServiceFactory = factory,
            apiServiceProvider = { baseUrl, apiKey ->
                assertLocalCredentials(baseUrl, apiKey)
                factory.createLongRunning(baseUrl, apiKey)
            },
            anthropicApiProvider = { baseUrl, apiKey ->
                assertLocalCredentials(baseUrl, apiKey)
                factory.createLongRunningAnthropic(baseUrl, apiKey)
            },
        )
    }

    private fun throwingCore(failure: Throwable, onCall: () -> Unit): PromptExtrasCore {
        val api = object : TestOpenAiCompatibleApi() {
            override suspend fun createChatCompletionAt(
                url: String,
                request: ChatCompletionRequest,
            ): Response<ChatCompletionResponse> {
                onCall()
                throw failure
            }

            override suspend fun createResponseAt(
                url: String,
                request: ResponseApiRequest,
            ): Response<ResponseApiResponse> {
                onCall()
                throw failure
            }
        }
        val anthropicApi = object : AnthropicApi {
            override suspend fun listModels(): Response<AnthropicModelsResponse> =
                error("不应调用 listModels")

            override suspend fun createMessage(request: AnthropicMessageRequest): Response<AnthropicMessageResponse> {
                onCall()
                throw failure
            }
        }
        return PromptExtrasCore(
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = { baseUrl, apiKey ->
                assertLocalCredentials(baseUrl, apiKey)
                api
            },
            anthropicApiProvider = { baseUrl, apiKey ->
                assertLocalCredentials(baseUrl, apiKey)
                anthropicApi
            },
        )
    }

    private fun assertLocalCredentials(baseUrl: String, apiKey: String) {
        assertEquals(server.url("/v1/").toString(), baseUrl)
        assertEquals(TEST_KEY, apiKey)
    }

    private suspend fun requestResult(
        provider: ProviderSettings = provider(),
        core: PromptExtrasCore = createCore(),
        request: ChatCompletionRequest = completionRequest(),
        allowRoleplaySamplingFallback: Boolean = false,
    ): PromptCompletionResult {
        return core.requestCompletionResult(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            operation = "测试提取",
            request = request,
            apiProtocol = provider.resolvedApiProtocol(),
            provider = provider,
            allowRoleplaySamplingFallback = allowRoleplaySamplingFallback,
        )
    }

    private fun provider(protocol: TestProtocol = TestProtocol.CHAT): ProviderSettings {
        return ProviderSettings(
            id = "completion-protocol-test",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "  $TEST_KEY  ",
            selectedModel = TEST_MODEL,
            apiProtocol = if (protocol == TestProtocol.ANTHROPIC) {
                ProviderApiProtocol.ANTHROPIC
            } else {
                ProviderApiProtocol.OPENAI_COMPATIBLE
            },
            openAiTextApiMode = if (protocol == TestProtocol.RESPONSES) {
                OpenAiTextApiMode.RESPONSES
            } else {
                OpenAiTextApiMode.CHAT_COMPLETIONS
            },
        )
    }

    private fun completionRequest(): ChatCompletionRequest {
        return ChatCompletionRequest(
            model = TEST_MODEL,
            messages = listOf(
                ChatMessageDto("system", SYSTEM_PROMPT),
                ChatMessageDto("user", USER_PROMPT)
            ),
            temperature = 0.6f,
            topP = 0.8f,
            maxTokens = 256,
            reasoningEffort = "low",
        )
    }

    private fun enqueue(body: String) {
        server.enqueue(
            MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json")
                .setBody(body)
        )
    }

    private fun takeRequest(): RecordedRequest {
        return checkNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "未收到测试请求" }
    }

    private fun protocolResponse(
        protocol: TestProtocol,
        content: String = EMPTY_ARTIFACT,
        reason: String? = when (protocol) {
            TestProtocol.CHAT -> "stop"
            TestProtocol.RESPONSES -> "completed"
            TestProtocol.ANTHROPIC -> "end_turn"
        },
    ): String {
        return when (protocol) {
            TestProtocol.CHAT -> chatResponse(content, reason)
            TestProtocol.RESPONSES -> responsesResponse(content, reason)
            TestProtocol.ANTHROPIC -> anthropicResponse(content, reason)
        }
    }

    private fun chatResponse(
        content: Any? = EMPTY_ARTIFACT,
        finishReason: String? = "stop",
        messageFields: Map<String, Any?> = emptyMap(),
    ): String {
        return AppJson.gson.toJson(
            mapOf(
                "choices" to listOf(
                    mapOf(
                        "index" to 0,
                        "finish_reason" to finishReason,
                        "message" to (mapOf(
                            "role" to "assistant",
                            "content" to content
                        ) + messageFields),
                    ),
                ),
            ),
        )
    }

    private fun responsesResponse(
        content: String = EMPTY_ARTIFACT,
        status: String? = "completed",
        reason: String? = null,
        itemStatus: String = "completed",
    ): String {
        return AppJson.gson.toJson(
            mapOf(
                "id" to "response_test",
                "status" to status,
                "incomplete_details" to reason?.let { mapOf("reason" to it) },
                "output" to listOf(
                    mapOf(
                        "type" to "message",
                        "status" to itemStatus,
                        "content" to listOf(mapOf("type" to "output_text", "text" to content)),
                    ),
                ),
            ),
        )
    }

    private fun anthropicResponse(
        content: String = EMPTY_ARTIFACT,
        stopReason: String? = "end_turn"
    ): String {
        return AppJson.gson.toJson(
            mapOf(
                "id" to "message_test",
                "type" to "message",
                "model" to TEST_MODEL,
                "stop_reason" to stopReason,
                "content" to listOf(mapOf("type" to "text", "text" to content)),
            ),
        )
    }

    private enum class TestProtocol {
        CHAT,
        RESPONSES,
        ANTHROPIC,
    }

    private companion object {
        const val TEST_KEY = "sk-completion-protocol-test"
        const val TEST_MODEL = "test-model"
        const val SYSTEM_PROMPT = "仅整理合成测试原料。"
        const val USER_PROMPT = "M0：合成测试中没有长期信息。"
        const val EMPTY_ARTIFACT =
            """{"schema_version":2,"memories":[],"corrections":[],"state_updates":[],"resident_candidates":[]}"""
    }
}
