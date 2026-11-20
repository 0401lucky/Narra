package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceIds
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.FakeSettingsStore
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiGatewayCompletionEvidenceTest {
    private lateinit var server: MockWebServer
    private var searchCalls = 0

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        searchCalls = 0
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun stream_chatEndReasons_preserveContentAndOnlyOneTerminalEvidence() = runBlocking<Unit> {
        val reasons = listOf(
            "stop" to PromptCompletionStatus.COMPLETED,
            "length" to PromptCompletionStatus.TRUNCATED,
            "content_filter" to PromptCompletionStatus.FILTERED,
            "tool_calls" to PromptCompletionStatus.INCOMPLETE,
            "cancelled" to PromptCompletionStatus.CANCELLED,
            null to PromptCompletionStatus.UNKNOWN,
        )
        for ((reason, expected) in reasons) {
            enqueueSse(chatStream(reason))
            val events = gateway().sendMessageStream(messages()).toList()
            val evidence = terminalEvidence(events)
            assertEquals(expected, evidence.status)
            assertEquals(reason, evidence.rawFinishReason)
            assertNull(evidence.rawStatus)
            assertEquals(TEXT, visibleText(events))
            assertRequest(Protocol.CHAT)
        }
    }

    @Test
    fun stream_malformedChunkBeforeStop_doesNotConfirmCompleteContent() = runBlocking<Unit> {
        enqueueSse(
            sse(
                chatChunk(mapOf("content" to TEXT)),
                "not-json",
                chatChunk(emptyMap(), "stop"),
                "[DONE]"
            )
        )
        val events = gateway().sendMessageStream(messages()).toList()
        assertEquals(PromptCompletionStatus.INCOMPLETE, terminalEvidence(events).status)
        assertEquals("stop", terminalEvidence(events).rawFinishReason)
        assertEquals(TEXT, visibleText(events))
        assertRequest(Protocol.CHAT)
    }

    @Test
    fun stream_chatRefusalOrPendingToolOverridesReportedStop() = runBlocking<Unit> {
        for ((extra, expected) in listOf(
            mapOf("refusal" to "拒绝标记") to PromptCompletionStatus.FILTERED,
            mapOf("tool_calls" to listOf(mapOf("index" to 0))) to PromptCompletionStatus.INCOMPLETE,
            mapOf("function_call" to mapOf("name" to "read_memory")) to PromptCompletionStatus.INCOMPLETE,
        )) {
            enqueueSse(
                sse(
                    chatChunk(mapOf("content" to TEXT) + extra),
                    chatChunk(emptyMap(), "stop"),
                    "[DONE]",
                )
            )
            val evidence = terminalEvidence(gateway().sendMessageStream(messages()).toList())
            assertEquals(expected, evidence.status)
            assertEquals("stop", evidence.rawFinishReason)
            assertRequest(Protocol.CHAT)
        }
    }

    @Test
    fun stream_responsesTerminalEvidence_preservesStatusAndIncompleteReason() = runBlocking<Unit> {
        val cases = listOf(
            Triple(
                "response.completed",
                mapOf<String, Any?>("status" to "completed"),
                PromptCompletionStatus.COMPLETED
            ),
            Triple("response.completed", emptyMap(), PromptCompletionStatus.COMPLETED),
            Triple(
                "response.incomplete", mapOf(
                    "status" to "incomplete",
                    "incomplete_details" to mapOf("reason" to "max_output_tokens")
                ), PromptCompletionStatus.TRUNCATED
            ),
            Triple(
                "response.cancelled",
                mapOf("status" to "cancelled"),
                PromptCompletionStatus.CANCELLED
            ),
            Triple(
                "response.completed", mapOf(
                    "status" to "completed", "output" to listOf(
                        mapOf(
                            "type" to "message",
                            "content" to listOf(mapOf("type" to "refusal", "refusal" to "拒绝"))
                        ),
                    )
                ), PromptCompletionStatus.FILTERED
            ),
            Triple(
                "response.completed", mapOf(
                    "status" to "completed", "output" to listOf(
                        mapOf("type" to "function_call", "call_id" to "pending"),
                    )
                ), PromptCompletionStatus.INCOMPLETE
            ),
        )
        for ((type, body, expected) in cases) {
            enqueueSse(
                sse(
                    json("type" to "response.output_text.delta", "delta" to TEXT),
                    json("type" to type, "response" to body),
                )
            )
            val events = gateway(Protocol.RESPONSES).sendMessageStream(messages()).toList()
            val evidence = terminalEvidence(events)
            assertEquals(expected, evidence.status)
            assertEquals(body["status"], evidence.rawStatus)
            assertEquals(
                (body["incomplete_details"] as? Map<*, *>)?.get("reason"),
                evidence.rawFinishReason
            )
            assertEquals(TEXT, visibleText(events))
            assertRequest(Protocol.RESPONSES)
        }
    }

    @Test
    fun stream_responsesProgressDoesNotPoisonCompletedItemButIncompleteDoneCannotBeErased() =
        runBlocking<Unit> {
            for ((doneStatus, expected) in listOf(
                "completed" to PromptCompletionStatus.COMPLETED,
                "incomplete" to PromptCompletionStatus.INCOMPLETE,
            )) {
                enqueueSse(
                    sse(
                        json(
                            "type" to "response.output_item.added", "output_index" to 0,
                            "item" to mapOf(
                                "id" to "item-1",
                                "type" to "message",
                                "status" to "in_progress"
                            )
                        ),
                        json("type" to "response.output_text.delta", "delta" to TEXT),
                        json(
                            "type" to "response.output_item.done", "output_index" to 0,
                            "item" to mapOf(
                                "id" to "item-1",
                                "type" to "message",
                                "status" to doneStatus
                            )
                        ),
                        json(
                            "type" to "response.completed",
                            "response" to mapOf(
                                "status" to "completed",
                                "output" to emptyList<Any>()
                            )
                        ),
                    )
                )
                assertEquals(
                    expected,
                    terminalEvidence(
                        gateway(Protocol.RESPONSES).sendMessageStream(messages()).toList()
                    ).status
                )
                assertRequest(Protocol.RESPONSES)
            }
        }

    @Test
    fun stream_responsesMalformedTerminalPayload_cannotConfirmComplete() = runBlocking<Unit> {
        enqueueSse(
            sse(
                json("type" to "response.output_text.delta", "delta" to TEXT),
                json(
                    "type" to "response.completed",
                    "response" to mapOf("status" to "completed", "output" to "invalid")
                ),
            )
        )
        val events = gateway(Protocol.RESPONSES).sendMessageStream(messages()).toList()
        assertEquals(PromptCompletionStatus.INCOMPLETE, terminalEvidence(events).status)
        assertEquals(TEXT, visibleText(events))
        assertRequest(Protocol.RESPONSES)
    }

    @Test
    fun stream_responsesTextDoneOrEofAlone_remainsUnknown() = runBlocking<Unit> {
        enqueueSse(
            sse(
                json("type" to "response.output_text.delta", "delta" to TEXT),
                json("type" to "response.output_text.done", "text" to TEXT),
            )
        )
        val evidence =
            terminalEvidence(gateway(Protocol.RESPONSES).sendMessageStream(messages()).toList())
        assertEquals(PromptCompletionStatus.UNKNOWN, evidence.status)
        assertNull(evidence.rawStatus)
        assertRequest(Protocol.RESPONSES)
    }

    @Test
    fun stream_anthropicStopReasonsAndTransportStop_areSeparate() = runBlocking<Unit> {
        for ((reason, expected) in listOf(
            "end_turn" to PromptCompletionStatus.COMPLETED,
            "stop_sequence" to PromptCompletionStatus.COMPLETED,
            "max_tokens" to PromptCompletionStatus.TRUNCATED,
            "refusal" to PromptCompletionStatus.FILTERED,
            "tool_use" to PromptCompletionStatus.INCOMPLETE,
            "cancelled" to PromptCompletionStatus.CANCELLED,
            null to PromptCompletionStatus.UNKNOWN,
        )) {
            enqueueSse(anthropicStream(reason))
            val events = gateway(Protocol.ANTHROPIC).sendMessageStream(messages()).toList()
            val evidence = terminalEvidence(events)
            assertEquals(expected, evidence.status)
            assertEquals(reason, evidence.rawFinishReason)
            assertEquals(TEXT, visibleText(events))
            assertRequest(Protocol.ANTHROPIC)
        }
    }

    @Test
    fun stream_anthropicPendingToolIsNotACompletedAnswer() = runBlocking<Unit> {
        enqueueSse(
            sse(
                json(
                    "type" to "content_block_start",
                    "content_block" to mapOf("type" to "tool_use", "id" to "pending")
                ),
                json(
                    "type" to "content_block_delta",
                    "delta" to mapOf("type" to "text_delta", "text" to TEXT)
                ),
                json("type" to "message_delta", "delta" to mapOf("stop_reason" to "end_turn")),
                json("type" to "message_stop"),
            )
        )
        assertEquals(
            PromptCompletionStatus.INCOMPLETE,
            terminalEvidence(
                gateway(Protocol.ANTHROPIC).sendMessageStream(messages()).toList()
            ).status
        )
        assertRequest(Protocol.ANTHROPIC)
    }

    @Test
    fun jsonResponses_nonStreamAndStreamFallback_keepEvidenceForEveryProtocol() =
        runBlocking<Unit> {
            for (protocol in Protocol.entries) {
                enqueueJson(responseBody(protocol, truncated = true))
                val reply = gateway(protocol).sendMessage(messages())
                assertEquals(PromptCompletionStatus.TRUNCATED, reply.completionEvidence.status)
                assertEquals(TEXT, reply.content)
                assertRequest(protocol)
                enqueueJson(responseBody(protocol, truncated = true))
                val events = gateway(protocol).sendMessageStream(messages()).toList()
                assertEquals(reply.completionEvidence, terminalEvidence(events))
                assertEquals(TEXT, visibleText(events))
                assertRequest(protocol)
            }
        }

    @Test
    fun stream_mislabelledSseAndClientFallback_keepEvidence() = runBlocking<Unit> {
        for (protocol in Protocol.entries) {
            enqueueJson(streamBody(protocol))
            assertEquals(
                PromptCompletionStatus.COMPLETED,
                terminalEvidence(gateway(protocol).sendMessageStream(messages()).toList()).status
            )
            assertRequest(protocol)
        }
        for (protocol in listOf(Protocol.CHAT, Protocol.RESPONSES)) {
            enqueueJson(responseBody(protocol, truncated = true))
            assertEquals(
                PromptCompletionStatus.TRUNCATED,
                terminalEvidence(
                    gateway(
                        protocol,
                        forceNonStream = true
                    ).sendMessageStream(messages()).toList()
                ).status
            )
            assertRequest(protocol)
        }
    }

    @Test
    fun stream_toolRouteWithoutCalls_usesTheBufferedFinalReplyEvidence() = runBlocking<Unit> {
        for (protocol in Protocol.entries) {
            enqueueJson(responseBody(protocol, truncated = true))
            val events = gateway(protocol).sendMessageStream(
                messages(), toolingOptions = GatewayToolingOptions.searchOnly(true),
            ).toList()
            assertEquals(PromptCompletionStatus.TRUNCATED, terminalEvidence(events).status)
            assertTrue(
                JsonParser.parseString(assertRequest(protocol).body.readUtf8()).asJsonObject
                    .getAsJsonArray("tools").size() > 0
            )
        }
        assertEquals(0, searchCalls)
    }

    @Test
    fun stream_afterToolCall_usesOnlyTheFinalStreamEvidence() = runBlocking<Unit> {
        for (protocol in Protocol.entries) {
            val previousCalls = searchCalls
            enqueueJson(toolResponse(protocol))
            enqueueJson(responseBody(protocol, text = "前置轮内容"))
            enqueueSse(streamBody(protocol, truncated = true))
            val events = gateway(protocol).sendMessageStream(
                messages(), toolingOptions = GatewayToolingOptions.searchOnly(true),
            ).toList()
            assertEquals(PromptCompletionStatus.TRUNCATED, terminalEvidence(events).status)
            assertEquals(TEXT, visibleText(events))
            assertFalse(visibleText(events).contains("前置轮"))
            assertEquals(previousCalls + 1, searchCalls)
            repeat(3) { assertRequest(protocol) }
        }
    }

    @Test
    fun stream_remoteError_keepsFailureEvidenceWithoutSuccessfulTerminalOrSecrets() =
        runBlocking<Unit> {
            for (protocol in Protocol.entries) {
                val error = when (protocol) {
                    Protocol.CHAT -> json("error" to mapOf("message" to "Authorization: Bearer private-fixture"))
                    Protocol.RESPONSES -> json(
                        "type" to "response.failed", "response" to mapOf(
                            "status" to "failed",
                            "error" to mapOf(
                                "code" to "server_error",
                                "message" to "Bearer private-fixture"
                            ),
                        )
                    )

                    Protocol.ANTHROPIC -> json(
                        "type" to "error",
                        "error" to mapOf("message" to "Bearer private-fixture")
                    )
                }
                enqueueSse(sse(error))
                val events = mutableListOf<ChatStreamEvent>()
                val failure = runCatching {
                    gateway(protocol).sendMessageStream(messages()).collect { events += it }
                }
                    .exceptionOrNull()
                assertNotNull("应拒绝 $protocol 的上游失败", failure)
                assertFalse(events.contains(ChatStreamEvent.Completed))
                assertEquals(
                    PromptCompletionStatus.FAILED,
                    events.filterIsInstance<ChatStreamEvent.CompletionEvidence>().single().status
                )
                assertFalse(events.toString().contains("private-fixture"))
                assertFalse(failure?.message.orEmpty().contains("private-fixture"))
                assertRequest(protocol)
            }
        }

    @Test
    fun stream_responsesFailedWithoutDetails_stillFails() = runBlocking<Unit> {
        for (body in listOf(json("status" to "failed"), "\"malformed\"")) {
            enqueueSse(
                sse(
                    json(
                        "type" to "response.failed",
                        "response" to JsonParser.parseString(body)
                    )
                )
            )
            val events = mutableListOf<ChatStreamEvent>()
            val failure = runCatching {
                gateway(Protocol.RESPONSES).sendMessageStream(messages()).collect { events += it }
            }.exceptionOrNull()
            assertNotNull(failure)
            assertFalse(events.contains(ChatStreamEvent.Completed))
            assertEquals(
                "body=$body, failure=$failure", 1,
                events.filterIsInstance<ChatStreamEvent.CompletionEvidence>().size
            )
            assertEquals(
                PromptCompletionStatus.FAILED,
                events.filterIsInstance<ChatStreamEvent.CompletionEvidence>().single().status
            )
            assertRequest(Protocol.RESPONSES)
        }
    }

    @Test
    fun stream_dispatchFailure_keepsExactlyOneEvidence() = runBlocking<Unit> {
        repeat(40) {
            val events = mutableListOf<ChatStreamEvent>()
            val failure = runCatching {
                flow<ChatStreamEvent> {
                    emit(ChatStreamEvent.CompletionEvidence(PromptCompletionStatus.FAILED))
                    throw java.io.IOException("synthetic")
                }.withCompletionFailureEvidence(Dispatchers.Default).collect { events += it }
            }.exceptionOrNull()
            assertNotNull(failure)
            assertEquals(1, events.filterIsInstance<ChatStreamEvent.CompletionEvidence>().size)
            assertFalse(events.contains(ChatStreamEvent.Completed))
        }
    }

    @Test
    fun stream_consumerError_isNotRelabeledAsServerFailure() = runBlocking<Unit> {
        val localFailure = IllegalStateException("consumer failure")
        val events = mutableListOf<ChatStreamEvent>()
        val failure = runCatching {
            flow<ChatStreamEvent> { emit(ChatStreamEvent.ContentDelta("local")) }
                .withCompletionFailureEvidence(Dispatchers.Default).collect {
                    events += it
                    throw localFailure
                }
        }.exceptionOrNull()
        assertEquals(localFailure.javaClass, failure?.javaClass)
        assertEquals(localFailure.message, failure?.message)
        assertTrue(events.none { it is ChatStreamEvent.CompletionEvidence })
    }

    @Test
    fun stream_consumerCancellation_neverEmitsSuccessfulTerminal() = runBlocking<Unit> {
        enqueueSse(chatStream("stop"))
        val started = CompletableDeferred<Unit>()
        val events = mutableListOf<ChatStreamEvent>()
        val job = async(Dispatchers.Default) {
            gateway().sendMessageStream(messages()).collect { event ->
                events += event
                if (event is ChatStreamEvent.ContentDelta) {
                    started.complete(Unit)
                    awaitCancellation()
                }
            }
        }
        withTimeout(5_000) { started.await() }
        job.cancelAndJoin()
        assertFalse(events.contains(ChatStreamEvent.Completed))
        assertTrue(events.none { it is ChatStreamEvent.CompletionEvidence })
        assertRequest(Protocol.CHAT)
    }

    @Test
    fun nonStream_reasoningOnlyIsNotConvertedIntoVisibleAnswer() = runBlocking<Unit> {
        for (protocol in Protocol.entries) {
            val body = when (protocol) {
                Protocol.CHAT -> json(
                    "choices" to listOf(
                        mapOf(
                            "finish_reason" to "stop",
                            "message" to mapOf("reasoning_content" to "只有推理"),
                        )
                    )
                )

                Protocol.RESPONSES -> json(
                    "status" to "completed", "output" to listOf(
                        mapOf(
                            "type" to "reasoning",
                            "summary" to listOf(
                                mapOf(
                                    "type" to "summary_text",
                                    "text" to "只有推理"
                                )
                            ),
                        )
                    )
                )

                Protocol.ANTHROPIC -> json(
                    "stop_reason" to "end_turn",
                    "content" to listOf(mapOf("type" to "thinking", "thinking" to "只有推理"))
                )
            }
            enqueueJson(body)
            assertNotNull(runCatching { gateway(protocol).sendMessage(messages()) }.exceptionOrNull())
            assertRequest(protocol)
        }
    }

    @Test
    fun stream_existingGeminiCompatibility_keepsCandidateEndReason() = runBlocking<Unit> {
        val body = json(
            "candidates" to listOf(
                mapOf(
                    "finishReason" to "MAX_TOKENS",
                    "content" to mapOf("parts" to listOf(mapOf("text" to TEXT))),
                )
            )
        )
        for (sseMode in listOf(false, true)) {
            if (sseMode) enqueueSse(sse(body, "[DONE]")) else enqueueJson(body)
            val events = gateway().sendMessageStream(messages()).toList()
            assertEquals(PromptCompletionStatus.TRUNCATED, terminalEvidence(events).status)
            assertEquals("MAX_TOKENS", terminalEvidence(events).rawFinishReason)
            assertEquals(TEXT, visibleText(events))
            assertRequest(Protocol.CHAT)
        }
    }

    @Test
    fun stream_rawMetadataNeverCopiesAnErrorSentence() = runBlocking<Unit> {
        enqueueSse(chatStream("Authorization: Bearer private-fixture"))
        val evidence = terminalEvidence(gateway().sendMessageStream(messages()).toList())
        assertEquals(PromptCompletionStatus.INCOMPLETE, evidence.status)
        assertNull(evidence.rawFinishReason)
        assertRequest(Protocol.CHAT)
    }

    private fun terminalEvidence(events: List<ChatStreamEvent>): ChatStreamEvent.CompletionEvidence {
        val evidence = events.filterIsInstance<ChatStreamEvent.CompletionEvidence>().single()
        assertEquals(ChatStreamEvent.Completed, events.last())
        assertEquals(evidence, events[events.lastIndex - 1])
        return evidence
    }

    private fun visibleText(events: List<ChatStreamEvent>) =
        events.filterIsInstance<ChatStreamEvent.ContentDelta>()
            .joinToString("") { it.value }

    private fun messages() =
        listOf(ChatMessage(id = "input", role = MessageRole.USER, content = "合成输入"))

    private fun assertRequest(protocol: Protocol): okhttp3.mockwebserver.RecordedRequest {
        val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals(
            when (protocol) {
                Protocol.CHAT -> "/v1/chat/completions"
                Protocol.RESPONSES -> "/v1/responses"
                Protocol.ANTHROPIC -> "/v1/messages"
            }, request.path
        )
        if (protocol == Protocol.ANTHROPIC) {
            assertEquals(KEY, request.getHeader("x-api-key"))
        } else {
            assertEquals("Bearer " + KEY, request.getHeader("Authorization"))
        }
        return request
    }

    private fun gateway(
        protocol: Protocol = Protocol.CHAT,
        forceNonStream: Boolean = false
    ): DefaultAiGateway {
        val factory = ApiServiceFactory()
        val provider = ProviderSettings(
            id = "completion", name = "合成协议", baseUrl = server.url("/v1/").toString(),
            apiKey = KEY, selectedModel = MODEL,
            apiProtocol = if (protocol == Protocol.ANTHROPIC) ProviderApiProtocol.ANTHROPIC else ProviderApiProtocol.OPENAI_COMPATIBLE,
            openAiTextApiMode = if (protocol == Protocol.RESPONSES) OpenAiTextApiMode.RESPONSES else OpenAiTextApiMode.CHAT_COMPLETIONS,
            models = listOf(
                ModelInfo(
                    MODEL,
                    abilities = setOf(ModelAbility.TOOL),
                    abilitiesCustomized = true
                )
            ),
        )
        val settings = AppSettings(
            providers = listOf(provider), selectedProviderId = provider.id,
            searchSettings = SearchSettings(
                sources = listOf(
                    SearchSourceConfig(
                        id = SearchSourceIds.BRAVE, type = SearchSourceType.BRAVE,
                        name = "合成搜索", enabled = true, apiKey = "synthetic-search-key",
                    )
                ),
                selectedSourceId = SearchSourceIds.BRAVE,
            ),
        )
        return DefaultAiGateway(
            settingsStore = FakeSettingsStore(settings),
            apiServiceFactory = factory,
            searchRepository = object : SearchRepository {
                override suspend fun search(
                    source: SearchSourceConfig,
                    query: String,
                    resultCount: Int
                ): SearchResult {
                    searchCalls += 1
                    return SearchResult(query = query, answer = "合成结果", items = emptyList())
                }
            },
            streamClientProvider = { baseUrl, apiKey ->
                if (forceNonStream) error("测试非流式回退")
                factory.createStreamingClient(baseUrl, apiKey)
            },
            ioDispatcher = Dispatchers.IO,
        )
    }

    private fun responseBody(
        protocol: Protocol,
        truncated: Boolean = false,
        text: String = TEXT
    ): String = when (protocol) {
        Protocol.CHAT -> json(
            "choices" to listOf(
                mapOf(
                    "finish_reason" to if (truncated) "length" else "stop",
                    "message" to mapOf("role" to "assistant", "content" to text),
                )
            )
        )

        Protocol.RESPONSES -> json(
            "id" to "response-final", "status" to if (truncated) "incomplete" else "completed",
            "incomplete_details" to if (truncated) mapOf("reason" to "max_output_tokens") else null,
            "output" to listOf(
                mapOf(
                    "type" to "message",
                    "content" to listOf(mapOf("type" to "output_text", "text" to text))
                )
            ),
        )

        Protocol.ANTHROPIC -> json(
            "stop_reason" to if (truncated) "max_tokens" else "end_turn",
            "content" to listOf(mapOf("type" to "text", "text" to text))
        )
    }

    private fun toolResponse(protocol: Protocol): String = when (protocol) {
        Protocol.CHAT -> json(
            "choices" to listOf(
                mapOf(
                    "finish_reason" to "tool_calls",
                    "message" to mapOf(
                        "tool_calls" to listOf(
                            mapOf(
                                "id" to "tool-1", "type" to "function",
                                "function" to mapOf(
                                    "name" to "search_web",
                                    "arguments" to json("query" to "合成查询")
                                )
                            )
                        )
                    )
                )
            )
        )

        Protocol.RESPONSES -> json(
            "id" to "tool-response", "status" to "completed",
            "output" to listOf(
                mapOf(
                    "type" to "function_call", "call_id" to "tool-1",
                    "name" to "search_web", "arguments" to json("query" to "合成查询")
                )
            )
        )

        Protocol.ANTHROPIC -> json(
            "stop_reason" to "tool_use", "content" to listOf(
                mapOf(
                    "type" to "tool_use",
                    "id" to "tool-1",
                    "name" to "search_web",
                    "input" to mapOf("query" to "合成查询")
                ),
            )
        )
    }

    private fun streamBody(protocol: Protocol, truncated: Boolean = false) = when (protocol) {
        Protocol.CHAT -> chatStream(if (truncated) "length" else "stop")
        Protocol.RESPONSES -> sse(
            json("type" to "response.output_text.delta", "delta" to TEXT),
            json(
                "type" to if (truncated) "response.incomplete" else "response.completed",
                "response" to JsonParser.parseString(responseBody(protocol, truncated))
            ),
        )

        Protocol.ANTHROPIC -> anthropicStream(if (truncated) "max_tokens" else "end_turn")
    }

    private fun chatChunk(delta: Map<String, Any?>, reason: String? = null) =
        json("choices" to listOf(mapOf("index" to 0, "delta" to delta, "finish_reason" to reason)))

    private fun chatStream(reason: String?) = sse(
        chatChunk(mapOf("content" to TEXT)), chatChunk(emptyMap(), reason), "[DONE]",
    )

    private fun anthropicStream(reason: String?) = sse(
        json(
            "type" to "content_block_delta",
            "delta" to mapOf("type" to "text_delta", "text" to TEXT)
        ),
        json("type" to "message_delta", "delta" to mapOf("stop_reason" to reason)),
        json("type" to "message_stop"),
    )

    private fun enqueueJson(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun enqueueSse(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream").setBody(body))
    }

    private fun json(vararg values: Pair<String, Any?>) = AppJson.gson.toJson(mapOf(*values))
    private fun sse(vararg values: String) = values.joinToString("") { "data: " + it + "\n\n" }

    private enum class Protocol { CHAT, RESPONSES, ANTHROPIC }

    private companion object {
        const val TEXT = "合成正文"
        const val KEY = "synthetic-completion-key"
        const val MODEL = "completion-test-model"
    }
}
