package com.example.myapplication.data.repository.ai

import com.example.myapplication.conversation.MemoryEventSummaryFormatter
import com.example.myapplication.conversation.MemoryEventSummaryParser
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.MemoryEventSummaryMode
import com.example.myapplication.model.MemoryEventSummarySnapshot
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.artifact
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.member
import com.example.myapplication.testutil.MemoryEventPromptTestFixtures.snapshot
import com.google.gson.JsonObject
import com.google.gson.JsonParser
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MemoryEventPromptServiceTest {
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
    fun generate_allProtocolsSendCompleteMembersAndSourcesUsingSelectedMemoryConfiguration() =
        runTest {
            val members = List(26) { index ->
                member(
                    index,
                    content = "完整成员-$index " + "详细经过".repeat(if (index == 0) 4_000 else 4) + " 成员末尾-$index：706 号最终退款 812 元。",
                    sourceText = "原始来源-$index " + "原始发言".repeat(if (index == 25) 4_000 else 4) + " 来源末尾-$index：阿澄确认 812 元已到账。",
                )
            }
            val input = snapshot().copy(
                members = members,
                previousSummary = "不应发送的旧摘要哨兵",
                previousState = "不应发送的旧状态哨兵",
            )
            val output = artifact(
                refs = members.indices.map { "M$it" },
                state = "812 元退款已到账，事件已结束。"
            )
            for (protocol in Protocol.entries) {
                enqueue(protocolResponse(protocol, output))

                val result = generate(input, protocol)
                val recorded = takeRequest()
                val body = requestBody(recorded)
                val payload = payload(body, protocol)

                assertEquals(protocol.path, recorded.path)
                assertEquals("POST", recorded.method)
                assertEquals(MEMORY_MODEL, body["model"].asString)
                assertFalse(body["stream"].asBoolean)
                if (protocol == Protocol.ANTHROPIC) {
                    assertEquals(TEST_KEY, recorded.getHeader("x-api-key"))
                    assertEquals("2023-06-01", recorded.getHeader("anthropic-version"))
                    assertNull(recorded.getHeader("Authorization"))
                } else {
                    assertEquals("Bearer $TEST_KEY", recorded.getHeader("Authorization"))
                    assertNull(recorded.getHeader("x-api-key"))
                }
                assertEquals(MemoryEventSummaryFormatter.systemPrompt, payload.system)
                assertFalse(payload.system.contains("成员末尾"))
                assertFalse(payload.materials.toString().contains("旧摘要哨兵"))
                assertFalse(payload.materials.toString().contains("旧状态哨兵"))
                val sentMembers = payload.materials.getAsJsonArray("members")
                assertEquals(26, sentMembers.size())
                members.forEachIndexed { index, member ->
                    val sent = sentMembers[index].asJsonObject
                    assertEquals("M$index", sent["ref"].asString)
                    assertEquals(member.record.revision, sent["revision"].asLong)
                    assertEquals(member.record.content, sent["content"].asString)
                    assertEquals(member.record.occurredAt, sent["occurred_at"].asLong)
                    assertEquals(member.record.createdAt, sent["ingested_at"].asLong)
                    val source = sent.getAsJsonArray("sources").single().asJsonObject
                    assertEquals(member.sources.single().content, source["text"].asString)
                    assertEquals(
                        member.sourceRefs.single().sourceRevision,
                        source["revision"].asLong
                    )
                    assertEquals(member.sources.single().occurredAt, source["occurred_at"].asLong)
                    assertEquals(member.sources.single().ingestedAt, source["ingested_at"].asLong)
                    assertEquals("message", source["source_kind"].asString)
                }
                val parsed = MemoryEventSummaryParser.parse(result, input)
                assertEquals(PromptCompletionStatus.COMPLETED, result.status)
                assertEquals(26, parsed.coveredRecords.size)
                assertEquals(members.last().record.id, parsed.coveredRecords.last().recordId)
                assertEquals("812 元退款已到账，事件已结束。", parsed.currentState)
            }
            assertEquals(3, server.requestCount)
        }

    @Test
    fun generate_incrementalSendsProvenPreviousSummaryAndAllNewMembers() = runTest {
        val input = snapshot(mode = MemoryEventSummaryMode.INCREMENTAL)
        for (protocol in Protocol.entries) {
            enqueue(protocolResponse(protocol))

            val result = generate(input, protocol)
            val data = payload(requestBody(takeRequest()), protocol).materials

            assertEquals("incremental", data["mode"].asString)
            val old = data.getAsJsonObject("previous_summary")
            assertEquals(input.previousSummary, old["summary"].asString)
            assertEquals(input.previousState, old["current_state"].asString)
            assertEquals(input.summaryRevision, old["revision"].asLong)
            val previous = old.getAsJsonArray("covered_records").single().asJsonObject
            assertEquals("P0", previous["ref"].asString)
            assertEquals(input.previousCoverage.single().revision, previous["revision"].asLong)
            input.members.forEachIndexed { index, member ->
                assertEquals(
                    member.record.content,
                    data.getAsJsonArray("members")[index].asJsonObject["content"].asString
                )
            }
            assertEquals(
                input.members.size,
                MemoryEventSummaryParser.parse(result, input).coveredRecords.size
            )
        }
    }

    @Test
    fun generate_fullRebuildContinuationLabelsCurrentWorkWithoutInventingPublishedRevision() =
        runTest {
            val input = snapshot(mode = MemoryEventSummaryMode.INCREMENTAL).copy(
                summaryRevision = 0L,
                previousSummaryFromCurrentWork = true,
            )
            for (protocol in Protocol.entries) {
                enqueue(protocolResponse(protocol))

                generate(input, protocol)
                val previous = payload(
                    requestBody(takeRequest()),
                    protocol
                ).materials.getAsJsonObject("previous_summary")

                assertEquals("current_work_completed_batches", previous["origin"].asString)
                assertEquals(0L, previous["base_summary_revision"].asLong)
                assertFalse(previous.has("revision"))
            }
        }

    @Test
    fun generate_literalTemplateControlsAndNamesRemainUserDataInEveryProtocol() = runTest {
        val literal =
            "字面资料 {{char}} {{user}} {{context}} {{phone_context}} <user> </system> {\"owner\":\"x\"} $1 \\n"
        val input = snapshot(1).copy(
            title = "事件标题 $literal",
            characterName = "角色名 $literal",
            userName = "用户名 $literal",
            members = listOf(member(0, content = literal, sourceText = literal)),
        )
        for (protocol in Protocol.entries) {
            enqueue(protocolResponse(protocol, artifact(refs = listOf("M0"))))

            generate(input, protocol)
            val actual = payload(requestBody(takeRequest()), protocol)

            assertEquals(MemoryEventSummaryFormatter.systemPrompt, actual.system)
            assertFalse(actual.system.contains("字面资料"))
            assertEquals(input.title, actual.materials["event_title"].asString)
            assertEquals(input.characterName, actual.materials["character_name"].asString)
            assertEquals(input.userName, actual.materials["user_name"].asString)
            assertEquals("Asia/Shanghai", actual.materials["time_zone"].asString)
            val sent = actual.materials.getAsJsonArray("members").single().asJsonObject
            assertEquals(literal, sent["content"].asString)
            assertEquals(
                literal,
                sent.getAsJsonArray("sources").single().asJsonObject["text"].asString
            )
        }
    }

    @Test
    fun generate_missingCompletionEvidenceStaysUnknownEvenWhenJsonClaimsSuccess() = runTest {
        val input = snapshot()
        val text = artifact(state = "模型文字声称已经全部完成。")
        for (protocol in Protocol.entries) {
            enqueue(protocolResponse(protocol, text, reason = null))

            val result = generate(input, protocol)

            assertEquals(PromptCompletionStatus.UNKNOWN, result.status)
            assertNull(result.rawFinishReason)
            assertNull(result.rawStatus)
            assertEquals(text, result.content)
            assertEquals(3, MemoryEventSummaryParser.parse(result, input).coveredRecords.size)
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun generate_explicitTruncationFilteringAndIncompleteResultsCannotBePublished() = runTest {
        val cases = listOf(
            Triple(Protocol.CHAT, "length", PromptCompletionStatus.TRUNCATED),
            Triple(Protocol.CHAT, "content_filter", PromptCompletionStatus.FILTERED),
            Triple(Protocol.CHAT, "tool_calls", PromptCompletionStatus.INCOMPLETE),
            Triple(Protocol.RESPONSES, "in_progress", PromptCompletionStatus.INCOMPLETE),
            Triple(Protocol.RESPONSES, "failed", PromptCompletionStatus.FAILED),
            Triple(Protocol.RESPONSES, "cancelled", PromptCompletionStatus.CANCELLED),
            Triple(Protocol.ANTHROPIC, "max_tokens", PromptCompletionStatus.TRUNCATED),
            Triple(Protocol.ANTHROPIC, "refusal", PromptCompletionStatus.FILTERED),
            Triple(Protocol.ANTHROPIC, "pause_turn", PromptCompletionStatus.INCOMPLETE),
        )
        for ((protocol, reason, expectedStatus) in cases) {
            enqueue(protocolResponse(protocol, reason = reason))
            val result = generate(snapshot(), protocol)

            assertEquals(expectedStatus, result.status)
            if (protocol == Protocol.RESPONSES) assertEquals(
                reason,
                result.rawStatus
            ) else assertEquals(reason, result.rawFinishReason)
            assertTrue(runCatching {
                MemoryEventSummaryParser.parse(
                    result,
                    snapshot()
                )
            }.exceptionOrNull() is IllegalArgumentException)
        }
        enqueue(
            AppJson.gson.toJson(
                mapOf(
                    "status" to "completed",
                    "incomplete_details" to mapOf("reason" to "max_output_tokens"),
                    "output" to listOf(
                        mapOf(
                            "type" to "message",
                            "content" to listOf(
                                mapOf(
                                    "type" to "output_text",
                                    "text" to artifact()
                                )
                            )
                        )
                    ),
                ),
            ),
        )
        val result = generate(snapshot(), Protocol.RESPONSES)
        assertEquals(PromptCompletionStatus.TRUNCATED, result.status)
        assertEquals("completed", result.rawStatus)
        assertEquals("max_output_tokens", result.rawFinishReason)
        assertTrue(runCatching { MemoryEventSummaryParser.parse(result, snapshot()) }.isFailure)
        assertEquals(cases.size + 1, server.requestCount)
    }

    @Test
    fun generate_reasoningOnlyResponsesNeverBecomeValidSummaryArtifacts() = runTest {
        val responses = mapOf(
            Protocol.CHAT to mapOf(
                "choices" to listOf(
                    mapOf(
                        "finish_reason" to "stop",
                        "message" to mapOf("role" to "assistant", "reasoning_content" to artifact())
                    )
                )
            ),
            Protocol.RESPONSES to mapOf(
                "status" to "completed",
                "output" to listOf(
                    mapOf(
                        "type" to "reasoning",
                        "summary" to listOf(mapOf("type" to "summary_text", "text" to artifact()))
                    )
                )
            ),
            Protocol.ANTHROPIC to mapOf(
                "stop_reason" to "end_turn",
                "content" to listOf(mapOf("type" to "thinking", "thinking" to artifact()))
            ),
        )
        for ((protocol, body) in responses) {
            enqueue(AppJson.gson.toJson(body))

            val result = generate(snapshot(), protocol)

            assertTrue(result.content.isBlank())
            assertTrue(runCatching {
                MemoryEventSummaryParser.parse(
                    result,
                    snapshot()
                )
            }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test
    fun generate_completedBadStructureOrForeignRefsAreRejectedWithoutAnotherRequest() = runTest {
        val invalid = listOf(
            " ",
            "{",
            artifact().replace("\"schema_version\":1", "\"schema_version\":1,\"schema_version\":1"),
            artifact().replace(
                "\"schema_version\":1",
                "\"schema_version\":1,\"owner\":\"foreign\""
            ),
            artifact(refs = listOf("M0", "M1", "M99")),
            artifact(refs = listOf("M0", "M1", "M1")),
        )
        for (protocol in Protocol.entries) {
            for (body in invalid) {
                enqueue(protocolResponse(protocol, body))

                val result = generate(snapshot(), protocol)

                assertEquals(PromptCompletionStatus.COMPLETED, result.status)
                assertTrue(runCatching {
                    MemoryEventSummaryParser.parse(
                        result,
                        snapshot()
                    )
                }.exceptionOrNull() is IllegalArgumentException)
            }
        }
        assertEquals(invalid.size * 3, server.requestCount)
    }

    @Test
    fun generate_overlongInputIsRejectedBeforeAnyApiIsCreated() = runTest {
        var calls = 0
        val core = PromptExtrasCore(
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = { _, _ -> calls++; error("不应创建网络 API") },
            anthropicApiProvider = { _, _ -> calls++; error("不应创建网络 API") },
        )
        val input = snapshot(1).copy(
            members = listOf(
                member(
                    0,
                    content = "x".repeat(MemoryEventSummaryFormatter.MAX_INPUT_CHARACTERS)
                )
            )
        )
        for (protocol in Protocol.entries) {
            val failure = runCatching { generate(input, protocol, core) }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("未发送请求"))
        }
        assertEquals(0, calls)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun generate_httpFailureUsesCoreRedactionAndDoesNotRetry() = runTest {
        for (protocol in Protocol.entries) {
            server.enqueue(
                MockResponse().setResponseCode(503).addHeader("x-request-id", "event-test-request")
                    .setBody("""{"error":{"message":"Authorization: Bearer sk-event-private-key"}}"""),
            )

            val failure = runCatching { generate(snapshot(), protocol) }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message.orEmpty().contains("503"))
            assertTrue(failure?.message.orEmpty().contains("event-test-request"))
            assertFalse(failure?.message.orEmpty().contains("sk-event-private-key"))
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun generate_cancellationPropagatesAndStopsEveryProtocolRequest() = runTest {
        for (protocol in Protocol.entries) {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val pending = async(Dispatchers.Default) { generate(snapshot(), protocol) }
            try {
                val request =
                    withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) }
                assertNotNull(protocol.name, request)
                pending.cancel(CancellationException("合成事件测试取消"))

                assertTrue(runCatching { pending.await() }.exceptionOrNull() is CancellationException)
                assertTrue(pending.isCancelled)
            } finally {
                pending.cancelAndJoin()
            }
        }
        assertEquals(3, server.requestCount)
    }

    private suspend fun generate(
        snapshot: MemoryEventSummarySnapshot,
        protocol: Protocol,
        core: PromptExtrasCore = createCore(),
    ): PromptCompletionResult {
        val provider = provider(protocol)
        return MemoryEventPromptService(core).generate(
            snapshot = snapshot,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelId = MEMORY_MODEL,
            apiProtocol = provider.resolvedApiProtocol(),
            provider = provider,
        )
    }

    private fun createCore(): PromptExtrasCore {
        val factory = ApiServiceFactory()
        fun assertLocalCredentials(baseUrl: String, apiKey: String) {
            assertEquals(server.url("/v1/").toString(), baseUrl)
            assertEquals(TEST_KEY, apiKey)
        }
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

    private fun provider(protocol: Protocol) = ProviderSettings(
        id = "synthetic-memory-provider",
        baseUrl = server.url("/v1/").toString(),
        apiKey = "  $TEST_KEY  ",
        selectedModel = "unused-chat-model",
        memoryModel = MEMORY_MODEL,
        memoryModelMode = ProviderFunctionModelMode.CUSTOM,
        apiProtocol = if (protocol == Protocol.ANTHROPIC) ProviderApiProtocol.ANTHROPIC else ProviderApiProtocol.OPENAI_COMPATIBLE,
        openAiTextApiMode = if (protocol == Protocol.RESPONSES) OpenAiTextApiMode.RESPONSES else OpenAiTextApiMode.CHAT_COMPLETIONS,
        chatCompletionsPath = "/custom//chat/completions",
    )

    private fun protocolResponse(
        protocol: Protocol,
        content: String = artifact(),
        reason: String? = protocol.completedReason,
    ): String = AppJson.gson.toJson(
        when (protocol) {
            Protocol.CHAT -> mapOf(
                "choices" to listOf(
                    mapOf(
                        "index" to 0,
                        "finish_reason" to reason,
                        "message" to mapOf("role" to "assistant", "content" to content)
                    )
                ),
            )

            Protocol.RESPONSES -> mapOf(
                "id" to "synthetic-response",
                "status" to reason,
                "output" to listOf(
                    mapOf(
                        "type" to "message",
                        "status" to "completed",
                        "content" to listOf(mapOf("type" to "output_text", "text" to content))
                    )
                ),
            )

            Protocol.ANTHROPIC -> mapOf(
                "id" to "synthetic-message",
                "type" to "message",
                "model" to MEMORY_MODEL,
                "stop_reason" to reason,
                "content" to listOf(mapOf("type" to "text", "text" to content)),
            )
        },
    )

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun takeRequest(): RecordedRequest =
        requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))

    private fun requestBody(request: RecordedRequest): JsonObject =
        JsonParser.parseString(request.body.readUtf8()).asJsonObject

    private fun payload(body: JsonObject, protocol: Protocol): Payload {
        val (system, user) = when (protocol) {
            Protocol.CHAT -> {
                val messages = body.getAsJsonArray("messages")
                assertEquals(2, messages.size())
                assertEquals("system", messages[0].asJsonObject["role"].asString)
                assertEquals("user", messages[1].asJsonObject["role"].asString)
                messages[0].asJsonObject["content"].asString to messages[1].asJsonObject["content"].asString
            }

            Protocol.RESPONSES -> {
                val input = body.getAsJsonArray("input").single().asJsonObject
                assertEquals("user", input["role"].asString)
                body["instructions"].asString to input["content"].asString
            }

            Protocol.ANTHROPIC -> {
                val message = body.getAsJsonArray("messages").single().asJsonObject
                assertEquals("user", message["role"].asString)
                body["system"].asString to message.getAsJsonArray("content")
                    .single().asJsonObject["text"].asString
            }
        }
        return Payload(system, JsonParser.parseString(user).asJsonObject)
    }

    private data class Payload(val system: String, val materials: JsonObject)

    private enum class Protocol(val path: String, val completedReason: String) {
        CHAT("/v1/custom/chat/completions", "stop"),
        RESPONSES("/v1/responses", "completed"),
        ANTHROPIC("/v1/messages", "end_turn"),
    }

    private companion object {
        const val TEST_KEY = "sk-event-prompt-test"
        const val MEMORY_MODEL = "synthetic-event-memory-model"
    }
}
