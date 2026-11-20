package com.example.myapplication.conversation

import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.ai.DefaultAiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.roleplay.RoleplayTranscriptFormatter
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.FakeConversationSummaryRepository
import com.example.myapplication.testutil.fakeSummaryRuntimeArchive
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ConversationSummaryPrivacyFlowTest {
    private lateinit var server: MockWebServer
    private lateinit var extras: AiPromptExtrasService
    private lateinit var settings: AppSettings
    private val contextA =
        MemoryRuntimeContext("a", "space-a", "conv", "scene", "run", "persona", 1)
    private val contextB = contextA.copy(characterId = "b", spaceId = "space-b")

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val factory = ApiServiceFactory()
        extras = DefaultAiPromptExtrasService(
            apiServiceFactory = factory,
            apiServiceProvider = { baseUrl, apiKey ->
                assertEquals(server.url("/v1/").toString(), baseUrl)
                factory.createLongRunning(baseUrl, apiKey)
            },
        )
        val provider = ProviderSettings(
            "synthetic",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "synthetic",
            selectedModel = "summary"
        )
        settings = AppSettings(providers = listOf(provider), selectedProviderId = provider.id)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun mixedPrivateMessageIsAbsentFromOtherCharactersSummaryRequestAndFinalContext() =
        runBlocking {
            val messages = listOf(
                ChatMessage("m1", "conv", MessageRole.USER, "大家在车站集合", createdAt = 1),
                ChatMessage(
                    "m2", "conv", MessageRole.ASSISTANT,
                    "<dialogue speaker=\"character\">MIXED_PUBLIC_SENTINEL</dialogue><thought>A_PRIVATE_SENTINEL</thought>",
                    createdAt = 2, speakerId = "a", speakerName = "角色A",
                ),
                ChatMessage(
                    "m3",
                    "conv",
                    MessageRole.USER,
                    "公开约定：11月19日706号房",
                    createdAt = 3
                ),
            )
            val repository = FakeConversationSummaryRepository(
                sourceMessages = messages,
                currentMemoryContexts = listOf(contextA, contextB)
            )
            enqueue("A_PRIVATE_SENTINEL 仅供A的摘要")
            assertTrue(update(repository, messages, contextA) { true }.updated)
            val requestA = server.takeRequest().body.readUtf8()
            assertTrue(requestA.contains("A_PRIVATE_SENTINEL"))

            enqueue("B获知大家在车站集合")
            enqueue("B获知11月19日706号房的公开约定")
            val resultB = update(repository, messages, contextB) { it.id != "m2" }
            repeat(2) {
                val requestB = server.takeRequest().body.readUtf8()
                assertFalse(requestB.contains("A_PRIVATE_SENTINEL"))
                assertFalse(requestB.contains("MIXED_PUBLIC_SENTINEL"))
            }
            assertEquals(1, resultB.coveredMessageCount)
            assertEquals(
                listOf("m1"),
                repository.getSummaryForContext(contextB)!!.sourceSnapshot.map { it.messageId })
            assertTrue(
                repository.listSummarySegments("conv")
                    .any { it.memoryContext == contextB && it.startMessageId == "m3" })

            repeat(2) {
                assertFalse(update(repository, messages, contextA) { true }.updated)
                assertFalse(update(repository, messages, contextB) { it.id != "m2" }.updated)
            }
            assertEquals(3, server.requestCount)
            val promptB = assemble(repository, messages.filterNot { it.id == "m2" }, contextB)
            assertFalse(promptB.systemPrompt.contains("A_PRIVATE_SENTINEL"))
            assertFalse(promptB.contextSections.any { it.content.contains("A_PRIVATE_SENTINEL") })
            assertTrue(promptB.systemPrompt.contains("706号房"))
            assertEquals(
                messages, ConversationSummaryCoverageSupport.trimRequestMessagesWithSummary(
                    repository.getSummaryForContext(contextB), messages, 1, contextB,
                )
            )
            assertEquals(messages, repository.sourceMessages)
        }

    @Test
    fun alternatingCharactersReuseTheirOwnMergedArtifactsWithoutNewSourceRequests() = runBlocking {
        val messages = (1..3).map {
            ChatMessage(
                "m$it",
                "conv",
                MessageRole.USER,
                "公开材料$it" + "细节".repeat(650),
                createdAt = it.toLong()
            )
        }
        val repository = FakeConversationSummaryRepository(
            sourceMessages = messages,
            currentMemoryContexts = listOf(contextA, contextB)
        )
        repeat(3) { enqueue("A段落$it") }
        enqueue("A_ONLY_AGGREGATE_SENTINEL")
        assertEquals(3, update(repository, messages, contextA) { true }.coveredMessageCount)
        repeat(4) { server.takeRequest() }
        repeat(3) { enqueue("B段落$it") }
        enqueue("B_ONLY_AGGREGATE_SENTINEL")
        assertEquals(3, update(repository, messages, contextB) { true }.coveredMessageCount)
        repeat(4) {
            assertFalse(
                server.takeRequest().body.readUtf8().contains("A_ONLY_AGGREGATE_SENTINEL")
            )
        }

        repeat(3) {
            assertFalse(update(repository, messages, contextA) { true }.updated)
            assertFalse(update(repository, messages, contextB) { true }.updated)
        }

        assertEquals(8, server.requestCount)
        assertEquals(6, repository.listSummarySegments("conv").size)
        assertEquals(
            "A_ONLY_AGGREGATE_SENTINEL",
            repository.getSummaryForContext(contextA)!!.summary
        )
        assertEquals(
            "B_ONLY_AGGREGATE_SENTINEL",
            repository.getSummaryForContext(contextB)!!.summary
        )
        val promptA = assemble(repository, messages, contextA)
        assertTrue(promptA.systemPrompt.contains("A_ONLY_AGGREGATE_SENTINEL"))
        assertFalse(promptA.systemPrompt.contains("B_ONLY_AGGREGATE_SENTINEL"))
    }

    @Test
    fun personaChangeWithIdenticalMessagesDoesNotReuseOldSummaryOrRebillBlockedHistory() =
        runBlocking {
            val messages = listOf(
                ChatMessage(
                    "m1",
                    "conv",
                    MessageRole.USER,
                    "OLD_PERSONA_PRIVATE_SENTINEL",
                    createdAt = 1
                )
            )
            val repository = FakeConversationSummaryRepository(
                sourceMessages = messages,
                currentMemoryContexts = listOf(contextA)
            )
            enqueue("OLD_PERSONA_PRIVATE_SENTINEL 的原身份摘要")
            update(repository, messages, contextA) { true }
            server.takeRequest()
            val oldSegments = repository.listSummarySegments("conv")
            val next = contextA.copy(personaKey = "new-persona")
            repository.currentMemoryContexts = listOf(next)

            repeat(3) { assertFalse(update(repository, messages, next) { false }.updated) }

            assertEquals(1, server.requestCount)
            assertNull(repository.getSummaryForContext(next))
            val prompt = assemble(repository, emptyList(), next)
            assertFalse(prompt.systemPrompt.contains("OLD_PERSONA_PRIVATE_SENTINEL"))
            assertFalse(prompt.contextSections.any { it.content.contains("OLD_PERSONA_PRIVATE_SENTINEL") })
            assertEquals(oldSegments, repository.listSummarySegments("conv"))
            assertEquals(messages, repository.sourceMessages)
        }

    private suspend fun update(
        repository: FakeConversationSummaryRepository,
        messages: List<ChatMessage>,
        context: MemoryRuntimeContext,
        allowed: (ChatMessage) -> Boolean,
    ) = ConversationSummaryCoordinator(extras, repository).updateConversationSummary(
        conversationId = "conv",
        assistantId = context.characterId,
        completedMessages = messages,
        settings = settings,
        config = SummaryGenerationConfig(0, 0, 1, segmentTargetCharacterCount = 1_200),
        expectedMemoryContext = context,
        isSourceAllowed = allowed,
        buildSummaryInput = { sources ->
            RoleplayTranscriptFormatter.formatMessageSegments(
                sources,
                "用户",
                "角色${context.characterId}",
                true
            ).joinToString("\n")
        },
        generateSummary = { input, baseUrl, apiKey, model, protocol ->
            extras.generateRoleplayConversationSummaryResult(
                input,
                baseUrl,
                apiKey,
                model,
                protocol,
                settings.activeProvider()
            )
        },
    )

    private suspend fun assemble(
        repository: FakeConversationSummaryRepository,
        messages: List<ChatMessage>,
        context: MemoryRuntimeContext,
    ) = DefaultPromptContextAssembler(
        conversationSummaryRepository = repository,
        memoryArchiveRepository = fakeSummaryRuntimeArchive { repository.currentMemoryContexts.orEmpty() },
    ).assemble(
        settings, Assistant(id = context.characterId, name = "合成角色"),
        Conversation("conv", createdAt = 1, updatedAt = 3, assistantId = context.characterId),
        "继续", messages, promptMode = PromptMode.ROLEPLAY,
    )

    private fun enqueue(content: String) {
        server.enqueue(
            MockResponse().setBody(
                AppJson.gson.toJson(
                    mapOf(
                        "choices" to listOf(
                            mapOf(
                                "index" to 0,
                                "message" to mapOf("role" to "assistant", "content" to content),
                                "finish_reason" to "stop",
                            )
                        ),
                    )
                )
            )
        )
    }
}
