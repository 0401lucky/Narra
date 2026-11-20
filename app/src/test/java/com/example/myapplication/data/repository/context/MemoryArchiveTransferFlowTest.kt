package com.example.myapplication.data.repository.context

import android.app.Application
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.*
import com.example.myapplication.testutil.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryArchiveTransferFlowTest {
    @Test
    fun importedMaterialsReachActualHttpWithReceipt() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            MemoryExtractionFixture().use { source ->
                MemoryExtractionFixture(persistent = true).use { destination ->
                    val from = source.contexts().single()
                    source.commitKnowledge(
                        source.prepareKnowledge(
                            from,
                            "原对话：芮珩喜欢栀子花，等在706号港湾。"
                        ) {
                            knowledgeResponse(
                                residents = listOf(residentCandidate("芮珩喜欢栀子花")),
                                states = listOf(
                                    stateUpdate(
                                        MemoryStateKey.LOCATION,
                                        "706号港湾",
                                        expiresAt = source.now + 100_000
                                    )
                                ),
                                memories = listOf(knowledgeEpisode("芮珩在706号港湾等候"))
                            )
                        })
                    val bundle = source.transferBundle()
                    val target = destination.contexts().single()
                    val transfer = destination.transfer()
                    transfer.importArchive(
                        bundle,
                        transfer.prepareImport(bundle, targetsFor(bundle, target))
                    )
                    destination.reopen()
                    val plan = requireNotNull(
                        MemoryRecallEngine(destination.archive, now = { destination.now }).recall(
                            target,
                            "芮珩在706号港湾等候", emptyList(), 10, 8000, false
                        )
                    )
                    assertEquals(
                        setOf(
                            MemoryMaterialKind.RECORD,
                            MemoryMaterialKind.RESIDENT,
                            MemoryMaterialKind.STATE
                        ), plan.materials.map { it.ref.kind }.toSet()
                    )
                    assertTrue(plan.materials.all { it.ref.imported && it.label.contains("导入资料") })
                    val user = destination.add(target, "import-http-user", "芮珩在706号港湾等候")
                    val request = destination.recallRequest(plan)
                    val provider = ProviderSettings(
                        id = "loopback",
                        baseUrl = server.url("/v1/").toString(),
                        apiKey = "synthetic-import-key",
                        selectedModel = "synthetic-chat"
                    )
                    val factory = ApiServiceFactory()
                    fun local(base: String, key: String) {
                        check(base == provider.baseUrl && key == provider.apiKey)
                    }

                    val gateway = DefaultAiGateway(
                        settingsStore = FakeSettingsStore(
                            destination.settings.copy(
                                providers = listOf(
                                    provider
                                ), selectedProviderId = provider.id
                            )
                        ),
                        apiServiceFactory = factory,
                        apiServiceProvider = { base, key ->
                            local(base, key); factory.create(
                            base,
                            key
                        )
                        },
                        imageApiServiceProvider = { _, _ -> error("资料测试禁止图片 API") },
                        anthropicApiProvider = { _, _ -> error("资料测试禁止 Anthropic API") },
                        streamClientProvider = { base, key ->
                            local(
                                base,
                                key
                            ); factory.createStreamingClient(base, key)
                        },
                        requestClientProvider = { base, key ->
                            local(
                                base,
                                key
                            ); factory.createRequestClient(base, key)
                        },
                        anthropicStreamClientProvider = { _, _ -> error("资料测试禁止 Anthropic 流") },
                        searchRepository = object : SearchRepository {
                            override suspend fun search(
                                source: SearchSourceConfig,
                                query: String,
                                resultCount: Int
                            ): SearchResult = error("资料测试禁止外部搜索")
                        },
                        ioDispatcher = Dispatchers.IO,
                        memoryArchiveRepository = destination.archive,
                    )
                    server.enqueue(
                        MockResponse().setHeader("Content-Type", "application/json")
                            .setBody("""{"choices":[{"message":{"role":"assistant","content":"合成回复"},"finish_reason":"stop"}]}""")
                    )
                    gateway.sendMessageStream(
                        listOf(user),
                        plan.section,
                        PromptMode.ROLEPLAY,
                        PromptEnvelope(memoryRecallRequest = request)
                    ).toList()
                    val http = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                    val body = http.body.readUtf8()
                    assertEquals("/v1/chat/completions", http.path)
                    assertTrue(body.contains("导入资料"))
                    assertTrue(body.contains("未验证本机原聊天"))
                    plan.materials.forEach { assertTrue(body.contains(it.ref.id)) }
                    val receipt = requireNotNull(
                        destination.archive.getRecallReceipt(
                            target,
                            request.requestId
                        )
                    )
                    assertEquals(MemoryRecallStatus.SENT, receipt.status)
                    assertTrue(receipt.sent.all { it.imported })
                    assertEquals(plan.materials.map { it.ref }, receipt.sent)
                    assertEquals(
                        MemoryArchiveIdentity.fingerprint(body),
                        receipt.payloadFingerprint
                    )
                    assertTrue(destination.ai.snapshots.isEmpty())
                }
            }
        }
    }
}
