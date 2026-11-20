package com.example.myapplication.data.repository.ai

import android.app.Application
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.context.MemoryRecallRendering
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.*
import com.example.myapplication.testutil.FakeSettingsStore
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.recallRequest
import com.example.myapplication.testutil.remember
import com.google.gson.JsonParser
import com.example.myapplication.system.json.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GatewayMemoryRecallTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer(); server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun openAiStreaming_receiptMatchesActualBodyWithLoggingDisabled() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            val logs = ContextLogStore().apply { setEnabled(false) }
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                messages(request), request.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val actual = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val rawBody = actual.body.readUtf8()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals("/v1/chat/completions", actual.path)
            assertEquals("Bearer synthetic-test-key", actual.getHeader("Authorization"))
            assertEquals(MemoryRecallStatus.SENT, receipt.status)
            assertEquals(MemoryArchiveIdentity.fingerprint(rawBody), receipt.payloadFingerprint)
            assertEquals(request.plan.materials.map { it.ref }, receipt.sent)
            assertEquals(request.anchorMessageId, receipt.anchorMessageId)
            assertTrue(rawBody.contains(request.plan.materials.single().ref.id))
            assertEquals(1, receipt.transmissions.size)
            assertFalse(
                f.database.memoryArchiveDao()
                    .getReceipt(request.requestId)!!.sentJson.contains("synthetic-test-key")
            )
            logs.push(ContextGovernanceSnapshot(memoryRecallRequestId = request.requestId))
            assertTrue(logs.snapshots.value.isEmpty())
        }
    }

    @Test
    fun responsesProtocol_receiptMatchesFinalInputEncoding() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(MockResponse().setBody("""{"id":"r1","output":[{"type":"message","role":"assistant","content":[{"type":"output_text","text":"合成回答"}]}]}"""))
            gateway(f, mode = OpenAiTextApiMode.RESPONSES).sendMessageStream(
                messages(request), request.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val actual = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val body = actual.body.readUtf8()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals("/v1/responses", actual.path)
            assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
            assertEquals(request.plan.materials.map { it.ref }, receipt.sent)
            val payload = com.google.gson.JsonParser.parseString(body).asJsonObject
            assertTrue(payload.getAsJsonArray("input").size() > 0)
            assertFalse(payload.has("messages"))
        }
    }

    @Test
    fun anthropicProtocol_receiptMatchesSystemBlocks() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(MockResponse().setBody("""{"id":"a1","content":[{"type":"text","text":"合成回答"}],"stop_reason":"end_turn"}"""))
            gateway(f, protocol = ProviderApiProtocol.ANTHROPIC).sendMessageStream(
                messages(request), request.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val actual = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            val body = actual.body.readUtf8()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals("/v1/messages", actual.path)
            assertEquals("synthetic-test-key", actual.getHeader("x-api-key"))
            assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
            assertEquals(request.plan.materials.map { it.ref }, receipt.sent)
        }
    }

    @Test
    fun nonStreamingRetrofit_receiptMatchesActualBody() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(chatReply())
            gateway(f).sendMessage(
                messages(request),
                request.plan.section,
                PromptEnvelope(memoryRecallRequest = request)
            )
            val body = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
            assertEquals(request.plan.materials.map { it.ref }, receipt.sent)
        }
    }

    @Test
    fun retryRecordsEachActualPayload_andFinalReceiptUsesLastAttempt() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("{\"error\":\"unsupported temperature\"}")
            )
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                messages(request), request.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val bodies = (1..2).map {
                requireNotNull(
                    server.takeRequest(
                        5,
                        TimeUnit.SECONDS
                    )
                ).body.readUtf8()
            }
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals(2, receipt.transmissions.size)
            assertEquals(
                bodies.map(MemoryArchiveIdentity::fingerprint),
                receipt.transmissions.map { it.payloadFingerprint })
            assertEquals(
                receipt.transmissions.last().payloadFingerprint,
                receipt.payloadFingerprint
            )
            assertTrue(receipt.transmissions.all { it.status == MemoryRecallStatus.SENT })
        }
    }

    @Test
    fun httpFailureRetainsConfirmedSendEvidence_withoutUsingPreviousRound() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val first = prepared(f)
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                messages(first), first.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = first)
            ).toList()
            server.takeRequest()
            val second = f.recallRequest(first.plan)
            server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic failure"))
            assertTrue(runCatching {
                gateway(f).sendMessageStream(
                    messages(second), second.plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = second)
                ).toList()
            }.isFailure)
            val body = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)).body.readUtf8()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(second.plan.context, second.requestId))
            assertEquals(MemoryRecallStatus.FAILED, receipt.status)
            assertEquals("HTTP_FAILED", receipt.failureCode)
            assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
            assertEquals(second.plan.materials.map { it.ref }, receipt.sent)
            assertEquals(
                MemoryRecallStatus.SENT,
                f.archive.getRecallReceipt(first.plan.context, first.requestId)!!.status
            )
            assertNull(f.archive.getRecallReceipt(first.plan.context, "new-round-without-receipt"))
        }
    }

    @Test
    fun disconnectedTransport_keepsPreparedEvidenceAndDoesNotClaimConfirmedSend() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            val client = OkHttpClient.Builder().retryOnConnectionFailure(false)
                .readTimeout(500, TimeUnit.MILLISECONDS).build()
            assertTrue(runCatching {
                gateway(f, client = client).sendMessageStream(
                    messages(request), request.plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = request)
                ).toList()
            }.isFailure)
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals(MemoryRecallStatus.FAILED, receipt.status)
            assertEquals("TRANSPORT_UNCONFIRMED", receipt.failureCode)
            assertTrue(receipt.sent.isEmpty())
            assertEquals(MemoryRecallStatus.PREPARED, receipt.transmissions.single().status)
        }
    }

    @Test
    fun editBetweenAssemblyAndSending_rejectsTheActualNetworkCall() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            val ref = request.plan.materials.single().ref
            val guarded = object : MemoryArchiveRepository by f.archive {
                override suspend fun prepareRecallReceipt(request: MemoryRecallRequest): Boolean {
                    val saved = f.archive.prepareRecallReceipt(request)
                    f.archive.editRecord(
                        request.plan.context,
                        ref.id,
                        ref.revision,
                        "用户刚刚纠正了约定。"
                    )
                    return saved
                }
            }
            assertTrue(runCatching {
                gateway(f, archive = guarded).sendMessageStream(
                    messages(request), request.plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = request)
                ).toList()
            }.isFailure)
            assertEquals(0, server.requestCount)
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals("SOURCE_CHANGED", receipt.failureCode)
            assertTrue(receipt.sent.isEmpty())
        }
    }

    @Test
    fun modifiedRenderedBody_isRejectedWithNoNetwork() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            val changed = request.plan.section.replace("731", "926")
            assertNotEquals(request.plan.section, changed)
            assertTrue(runCatching {
                gateway(f).sendMessageStream(
                    messages(request), changed, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = request)
                ).toList()
            }.isFailure)
            assertEquals(0, server.requestCount)
            assertEquals(
                "PAYLOAD_CHANGED",
                f.archive.getRecallReceipt(request.plan.context, request.requestId)!!.failureCode
            )
        }
    }

    @Test
    fun omittedPresetMaterial_isNotClaimedAsSent() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                messages(request), "角色设定", PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertTrue(receipt.selected.isNotEmpty())
            assertTrue(receipt.sent.isEmpty())
            assertTrue(receipt.omitted.any { it.reason == MemoryRecallOmissionReason.NOT_RENDERED })
        }
    }

    @Test
    fun userDiscussingInternalLookingTags_doesNotBlockSending() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                listOf(
                    ChatMessage(
                        "user", role = MessageRole.USER,
                        content = "文本里的 <narra_memory kind=\"record\"> 是什么意思？"
                    )
                ), request.plan.section, PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            assertEquals(
                MemoryRecallStatus.SENT,
                f.archive.getRecallReceipt(request.plan.context, request.requestId)!!.status
            )
            assertEquals(1, server.requestCount)
        }
    }

    @Test
    fun previousRoundBlockInUserHistory_isNotCountedAsCurrentInjection() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            val previousBlock = MemoryRecallRendering.block(request.plan.materials.single())
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                listOf(
                    ChatMessage(
                        "user",
                        role = MessageRole.USER,
                        content = "引用旧内容：$previousBlock"
                    )
                ),
                "当前只有角色设定",
                PromptMode.ROLEPLAY,
                PromptEnvelope(memoryRecallRequest = request)
            ).toList()
            val receipt = f.archive.getRecallReceipt(request.plan.context, request.requestId)!!
            assertEquals(MemoryRecallStatus.SENT, receipt.status)
            assertTrue(receipt.sent.isEmpty())
            assertTrue(receipt.omitted.any { it.reason == MemoryRecallOmissionReason.NOT_RENDERED })
        }
    }

    @Test
    fun duplicateCurrentMaterial_isRejectedAtFinalBoundary() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            assertTrue(runCatching {
                gateway(f).sendMessageStream(
                    messages(request), request.plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(
                        preHistoryMessages = listOf(PromptEnvelopeMessage(content = request.plan.section)),
                        memoryRecallRequest = request
                    )
                ).toList()
            }.isFailure)
            assertEquals(0, server.requestCount)
            assertEquals(
                "PAYLOAD_CHANGED",
                f.archive.getRecallReceipt(request.plan.context, request.requestId)!!.failureCode
            )
        }
    }

    @Test
    fun defaultMemoryPreset_keepsLiteralBodyAndExactHttpReceipt() = runBlocking {
        assertLiteralMemoryPreset("")
    }

    @Test
    fun explicitMemorySlot_keepsLiteralBodyAndOuterTemplateVariables() = runBlocking {
        assertLiteralMemoryPreset("模板中的 {{char}}：\n{{long_memory}}")
    }

    @Test
    fun contextPlaceholderInsideMemory_doesNotExpandTheWholePromptAgain() = runBlocking {
        assertLiteralMemoryPreset("{{long_memory}}", includeContextPlaceholder = true)
    }

    @Test
    fun repeatedMemoryPresetSlot_deduplicatesTheHeaderAndRecordsWithAnOmissionReceipt() =
        runBlocking {
            assertLiteralMemoryPreset("{{long_memory}}\n{{long_memory}}")
        }

    private suspend fun assertLiteralMemoryPreset(
        memoryTemplate: String,
        includeContextPlaceholder: Boolean = false
    ) {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            val body = "芮珩的原话包含 {{char}}、{{phone_context}}、<user>" +
                    (if (includeContextPlaceholder) " 和 {{context}}" else "") + "，约定在731号室。"
            val record = f.remember(context, body)
            val preset = Preset(
                id = "literal-memory", name = "合成模板", entries = listOf(
                    PresetPromptEntry(
                        id = "outer", title = "角色", kind = PresetPromptEntryKind.CUSTOM,
                        content = "角色为 {{char}}，用户为 {{user}}。", order = 0
                    ),
                    PresetPromptEntry(
                        id = "memory", title = "记忆", kind = PresetPromptEntryKind.LONG_MEMORY,
                        content = memoryTemplate, order = 1
                    ),
                )
            )
            val assembler = com.example.myapplication.context.DefaultPromptContextAssembler(
                memoryArchiveRepository = f.archive,
                presetRepository = com.example.myapplication.testutil.FakePresetRepository(
                    listOf(
                        preset
                    )
                ),
            )
            val assembled = assembler.assemble(
                settings = f.settings.copy(
                    defaultPresetId = preset.id,
                    userDisplayName = "合成用户"
                ),
                assistant = Assistant(
                    id = context.characterId,
                    name = "合成角色",
                    memoryEnabled = true
                ),
                conversation = Conversation(
                    id = context.conversationId,
                    createdAt = f.now,
                    updatedAt = f.now
                ),
                userInputText = "芮珩731",
                recentMessages = emptyList(),
                promptMode = PromptMode.ROLEPLAY,
                markUsage = false,
            )
            assertTrue(assembled.systemPrompt.contains("角色为 合成角色，用户为 合成用户。"))
            assertTrue(assembled.systemPrompt.contains(body))
            assertEquals(
                1,
                Regex(Regex.escape(MemoryRecallRendering.HEADER)).findAll(assembled.systemPrompt)
                    .count()
            )
            val plan = requireNotNull(assembled.memoryRecallPlan)
            assertEquals(record.id, plan.materials.single().ref.id)
            assertEquals(body, plan.materials.single().content)
            val request = f.recallRequest(plan)
            server.enqueue(chatReply())
            gateway(f).sendMessageStream(
                messages(request), assembled.systemPrompt, PromptMode.ROLEPLAY,
                assembled.promptEnvelope.copy(memoryRecallRequest = request)
            ).toList()
            val rawBody = server.takeRequest().body.readUtf8()
            val sentSystem = JsonParser.parseString(rawBody).asJsonObject.getAsJsonArray("messages")
                .first().asJsonObject.get("content").asString
            assertTrue(sentSystem.contains(body))
            val receipt = requireNotNull(f.archive.getRecallReceipt(context, request.requestId))
            assertEquals(plan.materials.map { it.ref }, receipt.sent)
            if (memoryTemplate == "{{long_memory}}\n{{long_memory}}") {
                assertTrue(receipt.omitted.any { it.ref.id == record.id && it.reason == MemoryRecallOmissionReason.DUPLICATE_RENDERING })
            }
            assertEquals(MemoryArchiveIdentity.fingerprint(rawBody), receipt.payloadFingerprint)
            assertEquals(body, f.archive.getRecordDetails(context, record.id)!!.record.content)
        }
    }

    @Test
    fun toolRoundQuotingCurrentMemoryBlock_remainsDataAndDoesNotClaimAnotherInjection() =
        runBlocking {
            MemoryExtractionFixture().use { f ->
                val request = prepared(f)
                val calls = AtomicInteger()
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (calls.getAndIncrement() > 0) return chatReply()
                        val firstBody =
                            JsonParser.parseString(request.body.clone().readUtf8()).asJsonObject
                        val quotedMemory = firstBody.getAsJsonArray("messages")
                            .first().asJsonObject.get("content").asString
                        return MockResponse().setBody(
                            AppJson.gson.toJson(
                                mapOf(
                                    "choices" to listOf(
                                        mapOf(
                                            "message" to mapOf(
                                                "role" to "assistant",
                                                "content" to "引用已看到的材料：$quotedMemory",
                                                "tool_calls" to listOf(
                                                    mapOf(
                                                        "id" to "read-1",
                                                        "type" to "function",
                                                        "function" to mapOf(
                                                            "name" to "read_memory",
                                                            "arguments" to "{\"query\":\"芮珩\"}",
                                                        )
                                                    )
                                                ),
                                            ), "finish_reason" to "tool_calls"
                                        )
                                    ),
                                )
                            )
                        )
                    }
                }
                val toolMemories = object : MemoryRepository by EmptyMemoryRepository {
                    override suspend fun listEntries() = listOf(
                        MemoryEntry(
                            id = "tool-record",
                            scopeType = MemoryScopeType.ASSISTANT,
                            scopeId = request.plan.context.characterId,
                            content = "芮珩的合成工具资料。"
                        )
                    )
                }
                val reply = gateway(f, memoryRepository = toolMemories).sendMessage(
                    messages(request),
                    request.plan.section,
                    PromptEnvelope(memoryRecallRequest = request),
                    GatewayToolingOptions.localContextOnly(
                        GatewayToolRuntimeContext(
                            promptMode = PromptMode.ROLEPLAY,
                            assistant = Assistant(
                                id = request.plan.context.characterId,
                                memoryEnabled = true
                            ),
                            conversation = Conversation(
                                id = request.plan.context.conversationId,
                                createdAt = f.now,
                                updatedAt = f.now
                            ),
                            userInputText = "芮珩", recentMessages = messages(request),
                        )
                    ),
                )
                assertEquals("合成回答", reply.content)
                assertEquals(2, server.requestCount)
                val first = server.takeRequest().body.readUtf8()
                val second = server.takeRequest().body.readUtf8()
                val transcript =
                    JsonParser.parseString(second).asJsonObject.getAsJsonArray("messages")
                assertTrue(transcript.any { message ->
                    message.asJsonObject.get("role").asString == "assistant" &&
                            message.asJsonObject.get("content")?.asString.orEmpty()
                                .contains("<narra_memory key=")
                })
                assertTrue(transcript.any { it.asJsonObject.get("role").asString == "tool" })
                val receipt = requireNotNull(
                    f.archive.getRecallReceipt(
                        request.plan.context,
                        request.requestId
                    )
                )
                assertEquals(2, receipt.transmissions.size)
                assertTrue(receipt.transmissions.all { it.status == MemoryRecallStatus.SENT && it.materials == request.plan.materials.map { material -> material.ref } })
                assertEquals(
                    listOf(first, second).map(MemoryArchiveIdentity::fingerprint),
                    receipt.transmissions.map { it.payloadFingerprint })
            }
        }

    @Test
    fun laterTransmission_modifiedOrDuplicatedApplicationCarrierIsStillRejected() = runBlocking {
        for (duplicate in listOf(false, true)) {
            MemoryExtractionFixture().use { f ->
                val request = prepared(f)
                val session = GatewayRecallSession(request, f.archive)
                session.prepare()
                session.payload(recallPayload(request.plan.section))
                val changed = if (duplicate) request.plan.section + request.plan.section
                else request.plan.section.replace("731", "926")
                assertTrue(runCatching { session.payload(recallPayload(changed)) }.isFailure)
                assertEquals(
                    1,
                    f.archive.getRecallReceipt(
                        request.plan.context,
                        request.requestId
                    )!!.transmissions.size
                )
                assertEquals(0, server.requestCount)
            }
        }
    }

    @Test
    fun laterTransmission_missingApplicationCarrierDoesNotPromoteAnAssistantQuote() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val request = prepared(f)
            val session = GatewayRecallSession(request, f.archive)
            session.prepare()
            session.payload(recallPayload(request.plan.section))
            session.payload(recallPayload("当前材料未插入", request.plan.section))
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(request.plan.context, request.requestId))
            assertEquals(2, receipt.transmissions.size)
            assertTrue(receipt.transmissions.last().materials.isEmpty())
            assertTrue(receipt.omitted.any { it.reason == MemoryRecallOmissionReason.NOT_RENDERED })
            assertEquals(0, server.requestCount)
        }
    }

    private fun recallPayload(system: String, assistantQuote: String = "") = AppJson.gson.toJson(
        mapOf(
            "messages" to buildList {
                add(mapOf("role" to "system", "content" to system))
                add(mapOf("role" to "user", "content" to "芮珩？"))
                if (assistantQuote.isNotBlank()) add(
                    mapOf(
                        "role" to "assistant",
                        "content" to assistantQuote
                    )
                )
            },
        )
    )

    private suspend fun prepared(f: MemoryExtractionFixture): MemoryRecallRequest {
        val context = f.contexts().single()
        f.remember(context, "芮珩约定在731号室归还罗盘。")
        val plan = requireNotNull(
            MemoryRecallEngine(f.archive).recall(
                context,
                "芮珩 731",
                emptyList(),
                6,
                2000,
                false
            )
        )
        return f.recallRequest(plan)
    }

    private fun messages(request: MemoryRecallRequest) = listOf(
        ChatMessage(
            "query",
            request.plan.context.conversationId,
            MessageRole.USER,
            "芮珩呢？"
        )
    )

    private fun gateway(
        f: MemoryExtractionFixture,
        mode: OpenAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS,
        protocol: ProviderApiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
        client: OkHttpClient? = null,
        archive: MemoryArchiveRepository = f.archive,
        memoryRepository: MemoryRepository = EmptyMemoryRepository,
    ): DefaultAiGateway {
        val provider = ProviderSettings(
            id = "p",
            name = "合成提供商",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "synthetic-test-key",
            selectedModel = "gpt-4.1-mini",
            apiProtocol = protocol,
            openAiTextApiMode = mode
        )
        val factory = ApiServiceFactory()
        return DefaultAiGateway(
            FakeSettingsStore(
                f.settings.copy(
                    providers = listOf(provider),
                    selectedProviderId = provider.id
                )
            ),
            factory,
            searchRepository = object : SearchRepository {
                override suspend fun search(
                    source: SearchSourceConfig,
                    query: String,
                    resultCount: Int
                ): SearchResult = error("测试禁止搜索网络")
            },
            streamClientProvider = { base, key ->
                client ?: factory.createStreamingClient(
                    base,
                    key
                )
            },
            memoryArchiveRepository = archive, ioDispatcher = Dispatchers.IO,
            memoryRepository = memoryRepository,
        )
    }

    private fun chatReply() =
        MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"合成回答"},"finish_reason":"stop"}]}""")
}
