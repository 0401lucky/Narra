package com.example.myapplication.data.repository.ai

import com.example.myapplication.conversation.MemoryExtractionParser
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MemoryExtractionPromptServiceTest {
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
    fun promptSeparatesRulesFromFullSourcesAndOmitsPrivateSpans() = runTest {
        enqueue(EMPTY)
        val source = span("来源正文哨兵：明天11月19日在706见阿澄")
        val private = source.copy(text = "私有正文哨兵", skipReason = "PRIVATE_TO_SPEAKER")
        val snapshot = MemoryExtractionSnapshot(
            listOf(source, private),
            listOf(MemoryExtractionTarget("record", 8, "旧的约定")),
            "甲",
            "用户",
            timeZoneId = "Asia/Shanghai"
        )
        request(snapshot)
        val recorded = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/v1/chat/completions", recorded.path)
        val messages =
            JsonParser.parseString(recorded.body.readUtf8()).asJsonObject.getAsJsonArray("messages")
        assertEquals("system", messages[0].asJsonObject["role"].asString)
        assertEquals("user", messages[1].asJsonObject["role"].asString)
        val rules = messages[0].asJsonObject["content"].asString
        val sources = messages[1].asJsonObject["content"].asString
        assertFalse(rules.contains("来源正文哨兵"))
        assertTrue(rules.contains("Asia/Shanghai"))
        assertTrue(sources.contains(source.text))
        assertFalse(sources.contains("私有正文哨兵"))
        assertTrue(sources.contains("\"revision\":5"))
        assertTrue(sources.contains("\"persona\":\"original-persona\""))
        assertTrue(sources.contains("\"ref\":\"O0\""))
    }

    @Test
    fun explicitTruncationRemainsVisibleToDomain() = runTest {
        enqueue(EMPTY, "length")
        val snapshot = MemoryExtractionSnapshot(listOf(span("有效来源")), emptyList(), "甲", "用户")
        val response = request(snapshot)
        assertEquals(PromptCompletionStatus.TRUNCATED, response.status)
        assertTrue(runCatching { MemoryExtractionParser.parse(response, snapshot) }.isFailure)
    }

    @Test
    fun customLegacyTemplateIsPreservedAndUsesExplicitAdapter() = runTest {
        enqueue("[\"合法旧模板条目\"]")
        val template = "用户模板原样 $0 {{conversation}} 已知 {{known_memories}}"
        val snapshot = MemoryExtractionSnapshot(
            listOf(span("源材料 $1")),
            emptyList(),
            "甲",
            "用户",
            MemoryExtractionFormat.LEGACY_LIST,
            template
        )
        val response = request(snapshot)
        assertEquals(template, snapshot.promptOverride)
        val request = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS)).body.readUtf8()
        assertTrue(request.contains("用户模板原样"))
        assertTrue(request.contains("$0"))
        assertTrue(request.contains("$1"))
        assertEquals(
            MemoryRecordKind.LEGACY_UNCLASSIFIED,
            MemoryExtractionParser.parse(response, snapshot).records.single().kind
        )
    }

    @Test
    fun knowledgeTargetsAndAuthorityRules_areSeparatedFromSourceData() = runTest {
        enqueue(EMPTY)
        val snapshot = MemoryExtractionSnapshot(
            listOf(span("本轮明确纠正地址为182号")), emptyList(), "甲", "用户",
            knowledgeTargets = MemoryKnowledgeTargets(
                residents = listOf(
                    MemoryResidentTarget(
                        "resident", 7, MemoryResidentCategory.USER,
                        "旧地址认知哨兵128号", false, true
                    )
                ),
                states = listOf(
                    MemoryStateTarget(
                        "state",
                        3,
                        MemoryStateKey.TASK,
                        "状态哨兵",
                        "ACTIVE",
                        true
                    )
                ),
            ),
        )
        request(snapshot)
        val messages = JsonParser.parseString(
            requireNotNull(
                server.takeRequest(
                    2,
                    TimeUnit.SECONDS
                )
            ).body.readUtf8()
        )
            .asJsonObject.getAsJsonArray("messages")
        val system = messages[0].asJsonObject["content"].asString
        val data = messages[1].asJsonObject["content"].asString
        assertFalse(system.contains("认知哨兵"))
        assertFalse(system.contains("状态哨兵"))
        assertTrue(data.contains("\"ref\":\"R0\""))
        assertTrue(data.contains("\"revision\":7"))
        assertTrue(data.contains("\"manual_protected\":true"))
        assertTrue(data.contains("\"state_key\":\"task\""))
        assertTrue(system.contains("人工保护目标只作背景"))
        assertTrue(system.contains("不能清空整类"))
        assertTrue(system.contains("心境、当前位置、当前任务和短期约定只放state_updates"))
        assertTrue(system.contains("既有角色卡、明确关系设定和世界书优先"))
        assertTrue(system.contains("无需每轮复述或催办"))
    }

    private suspend fun request(snapshot: MemoryExtractionSnapshot): PromptCompletionResult {
        val provider = ProviderSettings(
            id = "mock",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "synthetic-key",
            selectedModel = "memory-model"
        )
        return DefaultAiPromptExtrasService(ApiServiceFactory()).requestMemoryExtraction(
            snapshot,
            provider.baseUrl,
            provider.apiKey,
            "memory-model",
            provider = provider
        )
    }

    private fun enqueue(body: String, finish: String = "stop") {
        server.enqueue(
            MockResponse().setHeader("Content-Type", "application/json").setBody(
                AppJson.gson.toJson(
                    mapOf(
                        "id" to "test", "choices" to listOf(
                            mapOf(
                                "index" to 0,
                                "finish_reason" to finish,
                                "message" to mapOf("role" to "assistant", "content" to body)
                            ),
                        )
                    )
                )
            )
        )
    }

    private fun span(text: String) = MemoryExtractionSpan(
        MemorySourceRef("source", 5, 0, text.length), "message", "user", "USER", "original-persona",
        listOf("a"), 1_800_000_000_000L, MemorySourceKind.MESSAGE, MemorySemanticKind.SPEECH, text
    )

    companion object {
        private const val EMPTY =
            "{\"schema_version\":2,\"memories\":[],\"corrections\":[],\"state_updates\":[],\"resident_candidates\":[]}"
    }
}
