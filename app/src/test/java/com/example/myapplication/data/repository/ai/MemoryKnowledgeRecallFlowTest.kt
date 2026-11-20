package com.example.myapplication.data.repository.ai

import android.app.Application
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryKnowledgeRecallFlowTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer(); server.start()
    }

    @After
    fun cleanup() {
        server.shutdown()
    }

    @Test
    fun greeting_sendsBackgroundWithAuthorityAndExactSourceReceipt() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = MemoryResidentCategory.entries.map {
                        residentCandidate(
                            "背景${it.name}：栀子花", it,
                            subjective = it == MemoryResidentCategory.RELATIONSHIP
                        )
                    },
                    states = MemoryStateKey.entries.map {
                        stateUpdate(
                            it,
                            "当前${it.label}：合成状态"
                        )
                    },
                )
            })
            val world = FakeWorldBookRepository(
                listOf(
                    WorldBookEntry(
                        id = "world", title = "设定",
                        content = "世界书设定哨兵：北岸城没有机场。", alwaysActive = true
                    )
                )
            )
            val assistant = f.settings.assistants.first()
                .copy(systemPrompt = "角色卡哨兵：角色是芮珩的家人。", memoryMaxItems = 1)
            val assembled =
                DefaultPromptContextAssembler(
                    worldBookRepository = world, memoryArchiveRepository = f.archive,
                    memoryRecallEngine = MemoryRecallEngine(f.archive, now = { f.now })
                ).assemble(
                    f.settings,
                    assistant,
                    requireNotNull(f.store.getConversation(context.conversationId)),
                    "你好",
                    emptyList(),
                    PromptMode.ROLEPLAY,
                    includePhoneSnapshot = false,
                    markUsage = false,
                )
            val plan = requireNotNull(assembled.memoryRecallPlan)
            assertEquals(8, plan.materials.size)
            assertTrue(plan.materials.all {
                it.ref.kind in setOf(
                    MemoryMaterialKind.RESIDENT,
                    MemoryMaterialKind.STATE
                )
            })
            val sent = transmit(f, plan, assembled.systemPrompt, assembled.promptEnvelope)
            assertTrue(sent.system.contains("世界书设定哨兵：北岸城没有机场。"))
            assertTrue(sent.system.contains("角色卡哨兵：角色是芮珩的家人。"))
            assertTrue(sent.system.contains("认知仅作背景，无须复述或催办"))
            assertTrue(sent.system.contains("明确角色设定与世界书优先"))
            assertTrue(sent.system.contains("彼此相处 · 主观看法"))
            plan.materials.forEach { material ->
                // Gateway 为每次真实请求绑定新的 renderKey，正文和版本必须保持一致。
                assertTrue(sent.system.contains("kind=\"${material.ref.kind.name}\" id=\"${material.ref.id}\" revision=\"${material.ref.revision}\""))
                assertTrue(sent.system.contains("\n${material.label}\n${material.content}\n</narra_memory>"))
                assertTrue(material.ref.supportingRecords.isEmpty())
                assertTrue(material.ref.sourceRefs.isNotEmpty())
            }
            assertTrue(f.archive.listRecords(context).isEmpty())
            assertTrue(f.ai.snapshots.isEmpty())
        }
    }

    @Test
    fun correction_sendsOnlyNewResidentRevisionAndKeepsOldReceipt() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.commitKnowledge(f.prepareKnowledge(context) {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate("旧地址128号")
                    )
                )
            })
            val first = transmit(f, recall(f, context))
            val work = f.prepareKnowledge(context, "用户明确：正确地址182号") {
                knowledgeResponse(
                    residents = listOf(
                        residentCandidate(
                            "新地址182号",
                            operation = "replace",
                            targetRef = "R0",
                            expectedRevision = 1
                        ),
                    )
                )
            }
            f.commitKnowledge(work)
            val second = transmit(f, recall(f, context))
            assertFalse(second.system.contains("旧地址128号"))
            assertTrue(second.system.contains("新地址182号"))
            assertEquals(2L, second.receipt.sent.single().revision)
            assertEquals(
                listOf(work.snapshot.sources.single().ref),
                second.receipt.sent.single().sourceRefs
            )
            assertEquals(1L, first.receipt.sent.single().revision)
            assertEquals(
                first.receipt,
                f.archive.getRecallReceipt(context, first.receipt.requestId)
            )
        }
    }

    @Test
    fun expiredOrEndedState_isAbsentFromNextActualRequest() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.SHARED,
                "仍然有效的共同背景",
                false
            )
            f.archive.setCurrentState(context, MemoryStateKey.MOOD, 0, "短期心境哨兵", f.now + 1000)
            val task = f.archive.setCurrentState(
                context,
                MemoryStateKey.TASK,
                0,
                "待办哨兵"
            ) as MemoryKnowledgeWriteResult.Written
            assertEquals(3, transmit(f, recall(f, context)).receipt.sent.size)
            f.archive.endCurrentState(context, task.id, task.revision)
            f.now += 2000
            val sent = transmit(f, recall(f, context))
            assertEquals(listOf(MemoryMaterialKind.RESIDENT), sent.receipt.sent.map { it.kind })
            assertFalse(sent.system.contains("短期心境哨兵"))
            assertFalse(sent.system.contains("待办哨兵"))
            assertEquals(2, f.archive.listCurrentStates(context).size)
        }
    }

    @Test
    fun mutationAfterPrepare_blocksHttpAndMarksCurrentReceiptFailed() = runBlocking {
        listOf("expire", "end", "source").forEach { mutation ->
            MemoryExtractionFixture().use { f ->
                val context = f.contexts().single()
                val work = f.prepareKnowledge(context) {
                    knowledgeResponse(
                        states = listOf(
                            stateUpdate(
                                MemoryStateKey.MOOD,
                                "待校验的当前心境",
                                expiresAt = f.now + 1000
                            ),
                        )
                    )
                }
                f.commitKnowledge(work)
                val state = f.archive.listCurrentStates(context).single()
                val plan = recall(f, context)
                val user = f.add(context, "request-${UUID.randomUUID()}", "你好")
                val request = f.recallRequest(plan)
                val guarded = object : MemoryArchiveRepository by f.archive {
                    override suspend fun prepareRecallReceipt(request: MemoryRecallRequest): Boolean {
                        val saved = f.archive.prepareRecallReceipt(request)
                        when (mutation) {
                            "expire" -> f.now += 2000
                            "end" -> f.archive.endCurrentState(context, state.id, state.revision)
                            "source" -> f.store.upsertMessages(listOf(work.message.copy(content = "原文在请求前改变")))
                        }
                        return saved
                    }
                }
                assertTrue(runCatching {
                    gateway(f, guarded).sendMessageStream(
                        listOf(user), plan.section,
                        PromptMode.ROLEPLAY, PromptEnvelope(memoryRecallRequest = request)
                    ).toList()
                }.isFailure)
                assertEquals(0, server.requestCount)
                val receipt = requireNotNull(f.archive.getRecallReceipt(context, request.requestId))
                assertEquals(MemoryRecallStatus.FAILED, receipt.status)
                assertEquals("SOURCE_CHANGED", receipt.failureCode)
                assertTrue(receipt.sent.isEmpty())
                assertTrue(receipt.transmissions.isEmpty())
            }
        }
    }

    @Test
    fun independentAndPersonaRequests_neverContainDailyPrivateBackground() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val daily = f.contexts().single()
            f.archive.addResidentEntry(
                daily,
                MemoryResidentCategory.USER,
                "日常私有认知哨兵",
                false
            )
            val isolated = listOf(
                f.contexts(purpose = MemoryPurpose.INDEPENDENT).single(),
                f.contexts(persona = "另一扮演身份").single(),
                f.contexts(characters = listOf("b")).single()
            )
            isolated.forEach { context ->
                val own = "独立背景${context.runId}"
                f.archive.addResidentEntry(context, MemoryResidentCategory.USER, own, false)
                val sent = transmit(f, recall(f, context))
                assertTrue(sent.system.contains(own))
                assertFalse(sent.system.contains("日常私有认知哨兵"))
                assertEquals(1, sent.receipt.sent.size)
            }
        }
    }

    @Test
    fun budgetOmissions_areInReceiptWhileCompleteArchiveSurvives() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            repeat(8) { index ->
                f.archive.addResidentEntry(
                    context,
                    MemoryResidentCategory.USER,
                    "条目$index：" + "完整背景材料。".repeat(80),
                    false
                )
            }
            val before = f.archive.listResidentEntries(context)
            val plan = recall(f, context, budget = 1000)
            assertTrue(plan.materials.isNotEmpty())
            assertTrue(plan.omitted.any { it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
            val sent = transmit(f, plan)
            assertEquals(plan.omitted, sent.receipt.omitted)
            assertEquals(plan.materials.map { it.ref }, sent.receipt.sent)
            assertEquals(before, f.archive.listResidentEntries(context))
        }
    }

    @Test
    fun budgetBelowOneWholeResident_sendsExplicitEmptyReceiptWithoutDeleting() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts().single()
            f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.USER,
                "完整条目：" + "完整背景材料。".repeat(80),
                false
            )
            val before = f.archive.listResidentEntries(context)
            val plan = recall(f, context, budget = 700)
            assertTrue(plan.materials.isEmpty())
            assertEquals(1, plan.selected.size)
            assertEquals(MemoryRecallOmissionReason.TOKEN_BUDGET, plan.omitted.single().reason)
            val sent = transmit(f, plan)
            assertTrue(sent.receipt.sent.isEmpty())
            assertEquals(plan.selected, sent.receipt.selected)
            assertEquals(plan.omitted, sent.receipt.omitted)
            assertFalse(sent.system.contains("完整背景材料。"))
            assertEquals(before, f.archive.listResidentEntries(context))
        }
    }

    @Test
    fun disabledMemory_keepsArchiveButRemovesBackgroundFromAssembly() = runBlocking {
        MemoryExtractionFixture().use { f ->
            f.enable()
            val context = f.contexts().single()
            f.archive.addResidentEntry(
                context,
                MemoryResidentCategory.USER,
                "已关闭记忆的正文哨兵",
                false
            )
            val assistant = f.settings.assistants.first().copy(memoryEnabled = false)
            val result =
                DefaultPromptContextAssembler(memoryArchiveRepository = f.archive).assemble(
                    f.settings,
                    assistant,
                    requireNotNull(f.store.getConversation(context.conversationId)),
                    "你好",
                    emptyList(),
                    PromptMode.ROLEPLAY,
                    includePhoneSnapshot = false,
                )
            assertNull(result.memoryRecallPlan)
            assertFalse(result.systemPrompt.contains("已关闭记忆的正文哨兵"))
            assertEquals(1, f.archive.listResidentEntries(context).size)
            assertEquals(0, server.requestCount)
        }
    }

    private suspend fun recall(
        f: MemoryExtractionFixture,
        context: MemoryRuntimeContext,
        budget: Int = 2000
    ): MemoryRecallPlan =
        requireNotNull(
            MemoryRecallEngine(f.archive, now = { f.now }).recall(
                context,
                "你好",
                emptyList(),
                1,
                budget,
                false
            )
        )

    private suspend fun transmit(
        f: MemoryExtractionFixture, plan: MemoryRecallPlan, systemPrompt: String = plan.section,
        envelope: PromptEnvelope = PromptEnvelope(),
    ): Sent {
        val user = f.add(plan.context, "request-${UUID.randomUUID()}", "你好")
        val request = f.recallRequest(plan)
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"合成回复"},"finish_reason":"stop"}]}""")
        )
        gateway(f).sendMessageStream(
            listOf(user), systemPrompt, PromptMode.ROLEPLAY,
            envelope.copy(memoryRecallRequest = request)
        ).toList()
        val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer $KEY", recorded.getHeader("Authorization"))
        val raw = recorded.body.readUtf8()
        val messages = JsonParser.parseString(raw).asJsonObject.getAsJsonArray("messages")
        val system = messages.filter { it.asJsonObject["role"].asString == "system" }
            .joinToString("\n") { it.asJsonObject["content"].asString }
        val receipt = requireNotNull(f.archive.getRecallReceipt(plan.context, request.requestId))
        assertEquals(MemoryRecallStatus.SENT, receipt.status)
        assertEquals(plan.materials.map { it.ref }, receipt.sent)
        assertEquals(plan.selected, receipt.selected)
        assertEquals(user.id, receipt.userMessageId)
        assertEquals(request.anchorMessageId, receipt.anchorMessageId)
        assertEquals(MemoryArchiveIdentity.fingerprint(raw), receipt.payloadFingerprint)
        assertEquals(receipt.sent, receipt.transmissions.single().materials)
        return Sent(system, receipt)
    }

    private fun gateway(
        f: MemoryExtractionFixture,
        archive: MemoryArchiveRepository = f.archive
    ): DefaultAiGateway {
        val provider = ProviderSettings(
            id = "local",
            baseUrl = server.url("/v1/").toString(),
            apiKey = KEY,
            selectedModel = "synthetic-chat"
        )
        val factory = ApiServiceFactory()
        fun local(base: String, key: String) {
            check(base == provider.baseUrl && key == KEY) { "认知测试只允许本机 MockWebServer" }
        }
        return DefaultAiGateway(
            settingsStore = FakeSettingsStore(
                f.settings.copy(
                    providers = listOf(provider),
                    selectedProviderId = provider.id
                )
            ),
            apiServiceFactory = factory,
            apiServiceProvider = { base, key -> local(base, key); factory.create(base, key) },
            imageApiServiceProvider = { _, _ -> error("认知测试禁止图片 API") },
            anthropicApiProvider = { _, _ -> error("认知测试禁止 Anthropic API") },
            streamClientProvider = { base, key ->
                local(base, key); factory.createStreamingClient(
                base,
                key
            )
            },
            requestClientProvider = { base, key ->
                local(base, key); factory.createRequestClient(
                base,
                key
            )
            },
            anthropicStreamClientProvider = { _, _ -> error("认知测试禁止 Anthropic 流请求") },
            searchRepository = object : SearchRepository {
                override suspend fun search(
                    source: SearchSourceConfig,
                    query: String,
                    resultCount: Int
                ): SearchResult = error("认知测试禁止搜索 API")
            },
            ioDispatcher = Dispatchers.IO,
            memoryArchiveRepository = archive,
        )
    }

    private data class Sent(val system: String, val receipt: MemoryRecallReceipt)
    private companion object {
        const val KEY = "knowledge-http-test-key"
    }
}
