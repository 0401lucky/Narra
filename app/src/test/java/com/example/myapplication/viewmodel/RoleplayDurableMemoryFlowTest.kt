package com.example.myapplication.viewmodel

import android.app.Application
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.ConversationSummaryCoordinator
import com.example.myapplication.conversation.ConversationMemoryExtractionCoordinator
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.conversation.RoleplayContextStatusCoordinator
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ai.GatewayRequestMessageBuilder
import com.example.myapplication.data.repository.ai.parseAssistantSpecialOutputLocally
import com.example.myapplication.data.repository.context.ContextLogStore
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyPendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoomRoleplayRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeSettingsStore
import com.example.myapplication.testutil.remember
import com.example.myapplication.data.repository.ai.DefaultAiGateway
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.context.MemoryArchiveIdentity
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RoleplayDurableMemoryFlowTest {
    private lateinit var f: MemoryExtractionFixture
    private val sentPayloads = mutableListOf<String>()

    @Before
    fun setup() {
        f = MemoryExtractionFixture()
    }

    @After
    fun cleanup() {
        f.close()
    }

    @Test
    fun completedSingleReplyUsesDurableSources() = runBlocking {
        f.enable()
        val run = createRun(group = false)
        val tasks = mutableListOf<Job>()
        val outcome = execute(run, "a", tasks, this)
        tasks.joinAll()
        assertNull(outcome.errorMessage)
        assertEquals(1, f.ai.snapshots.size)
        val context = f.archive.getRuntimeContext(run.session.conversationId, "a")!!
        assertEquals(1, f.archive.listRecords(context).size)
        assertTrue(f.ai.snapshots.single().sources.any { it.speakerRole == "USER" })
        assertTrue(f.ai.snapshots.single().sources.any { it.speakerRole == "ASSISTANT" && it.speakerId == "a" })
        assertTrue(f.archive.listPendingIngress(context).isEmpty())
    }

    @Test
    fun actualGroupRepliesWriteOnlyBoundCharacterSpaces() = runBlocking {
        f.enable()
        val run = createRun(group = true)
        val tasks = mutableListOf<Job>()
        assertNull(execute(run, "a", tasks, this).errorMessage)
        tasks.joinAll(); tasks.clear()
        assertNull(execute(run, "b", tasks, this).errorMessage)
        tasks.joinAll()
        val a = f.archive.getRuntimeContext(run.session.conversationId, "a")!!
        val b = f.archive.getRuntimeContext(run.session.conversationId, "b")!!
        assertNotEquals(a.spaceId, b.spaceId)
        assertTrue(f.archive.listRecords(a).isNotEmpty())
        assertTrue(f.archive.listRecords(b).isNotEmpty())
        assertNull(f.archive.getRuntimeContext(run.session.conversationId, "outsider"))
        assertTrue(f.ai.snapshots.all { snapshot ->
            snapshot.sources.all {
                it.visibleCharacterIds.toSet() == setOf(
                    "a",
                    "b"
                )
            }
        })
    }

    @Test
    fun failedAndCancelledRepliesDoNotLaunchExtraction() = runBlocking {
        f.enable()
        val run = createRun(group = false)
        val tasks = mutableListOf<Job>()
        val failed = execute(run, "a", tasks, this, failure = true)
        assertNotNull(failed.errorMessage)
        assertTrue(tasks.isEmpty())
        val started = CompletableDeferred<Unit>()
        val cancelled = launch { execute(run, "a", tasks, this, started = started) }
        started.await(); cancelled.cancelAndJoin()
        assertTrue(tasks.isEmpty())
        assertTrue(f.ai.snapshots.isEmpty())
        assertTrue(
            f.store.listMessages(run.session.conversationId)
                .filter { it.role == MessageRole.ASSISTANT }
                .none { it.status == MessageStatus.COMPLETED })
    }

    @Test
    fun scopedSummaryAndFinalGroupPromptPreservePrivateBoundary() = runBlocking {
        f.enable()
        val summaryProvider = f.settings.providers.single().copy(
            id = "summary-provider",
            baseUrl = "https://summary.example.invalid/v1/",
            apiKey = "synthetic-summary-key",
            selectedModel = "summary-chat-model",
            titleSummaryModel = "dedicated-summary-model",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        f.settings = f.settings.copy(
            providers = f.settings.providers + summaryProvider,
            functionModelProviderIds = f.settings.functionModelProviderIds.copy(
                titleSummaryProviderId = summaryProvider.id
            ),
        )
        val run = createRun(group = true)
        val a = f.archive.getRuntimeContext(run.session.conversationId, "a")!!
        val b = f.archive.getRuntimeContext(run.session.conversationId, "b")!!
        f.add(a, "public-first", "公开提问哨兵")
        val mixed = f.add(
            a,
            "private-mixed",
            "",
            MessageRole.ASSISTANT,
            parts = listOf(textMessagePart("公开回答"), thoughtMessagePart("甲私有心声哨兵")),
            speakerId = "a"
        )
        f.store.upsertMessages(
            listOf(
                mixed.copy(
                    replyToMessageId = "private-thought",
                    replyToPreview = "甲私有引用哨兵",
                    replyToSpeakerName = "甲私有称呼哨兵",
                    reasoningContent = "甲私有推理哨兵",
                )
            )
        )
        f.add(a, "public-last", "公开的后来进展")
        val summaryInputs = mutableListOf<String>()
        val ai = object : AiPromptExtrasService by f.ai {
            override suspend fun generateRoleplayConversationSummaryResult(
                conversationText: String, baseUrl: String, apiKey: String, modelId: String,
                apiProtocol: ProviderApiProtocol, provider: ProviderSettings?
            ): PromptCompletionResult {
                assertEquals(summaryProvider, provider)
                assertEquals(summaryProvider.baseUrl, baseUrl)
                assertEquals(summaryProvider.apiKey, apiKey)
                assertEquals(
                    summaryProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY),
                    modelId
                )
                summaryInputs += conversationText
                return PromptCompletionResult(
                    if (conversationText.contains("甲私有心声哨兵")) "甲私有心声哨兵" else "公共摘要哨兵",
                    PromptCompletionStatus.COMPLETED
                )
            }
        }
        var state = RoleplayUiState(
            settings = f.settings,
            currentScenario = run.scenario,
            currentSession = run.session,
            currentAssistant = f.settings.assistants.first()
        )
        val memory = FakeMemoryRepository()
        val support = RoleplayContextUpdateSupport(
            scope = this,
            uiState = { state },
            updateUiState = { state = it(state) },
            summaryCoordinator = ConversationSummaryCoordinator(ai, run.summaries) { ++f.now },
            memoryExtractionCoordinator = ConversationMemoryExtractionCoordinator(
                ai,
                memory,
                durableCoordinator = f.coordinator()
            ),
            contextStatusCoordinator = RoleplayContextStatusCoordinator(run.summaries, memory),
            pendingMemoryProposalRepository = EmptyPendingMemoryProposalRepository,
            aiPromptExtrasService = ai,
            refreshContextGovernance = { _, _, _, _, _ -> },
            loadPersistedMessages = { f.store.listMessages(it) },
        )

        suspend fun summarize(character: String, force: Boolean = false) =
            support.updateConversationSummary(
                run.session.conversationId,
                f.store.listMessages(run.session.conversationId),
                f.settings,
                f.settings.assistants.single { it.id == character },
                run.scenario,
                0,
                0,
                1,
                4000,
                force,
            )
        summarize("a", true)
        assertTrue(summaryInputs.single().contains("甲私有心声哨兵"))
        val aCalls = summaryInputs.size
        summarize("b")
        assertTrue(summaryInputs.drop(aCalls).isNotEmpty())
        assertTrue(summaryInputs.drop(aCalls).none { it.contains("甲私有心声哨兵") })
        val bSummary = run.summaries.getSummaryForContext(b)!!
        assertEquals(
            1,
            ConversationSummaryCoverageSupport.verifiedSummaryCoverage(
                bSummary,
                f.store.listMessages(b.conversationId),
                b
            )
        )
        val bothCalls = summaryInputs.size
        summarize("a")
        assertEquals(bothCalls, summaryInputs.size)
        val tasks = mutableListOf<Job>()
        assertNull(execute(run, "b", tasks, this).errorMessage)
        tasks.joinAll()
        assertTrue(sentPayloads.last().contains("公共摘要哨兵"))
        assertFalse(sentPayloads.last().contains("甲私有心声哨兵"))
        assertFalse(sentPayloads.last().contains("甲私有引用哨兵"))
        assertFalse(sentPayloads.last().contains("甲私有称呼哨兵"))
        assertFalse(sentPayloads.last().contains("甲私有推理哨兵"))
        assertTrue(
            f.store.listMessages(a.conversationId)
                .single { it.id == "private-mixed" }.parts.any { it.text.contains("甲私有心声哨兵") })
        val beforePersonaChange = summaryInputs.size
        val updated = f.archive.bindConversation(
            run.scenario.copy(userPersonaOverride = "新身份"),
            a.conversationId,
            listOf("a", "b"),
            a.runId
        )
        val changedB = updated.single { it.characterId == "b" }
        assertNull(run.summaries.getSummaryForContext(b))
        assertNull(run.summaries.getSummaryForContext(changedB))
        summarize("b")
        assertEquals(beforePersonaChange, summaryInputs.size)
    }

    private suspend fun createRun(group: Boolean): Run {
        val repository = ConversationRepository(f.store) { ++f.now }
        val roleplay = RoomRoleplayRepository(
            f.database.roleplayDao(),
            repository,
            { ++f.now },
            database = f.database,
            memoryArchiveRepository = f.archive
        )
        val scenario = RoleplayScenario(
            id = "scene", assistantId = "a", memoryPurpose = MemoryPurpose.CONTINUOUS,
            interactionMode = if (group) RoleplayInteractionMode.ONLINE_PHONE else RoleplayInteractionMode.OFFLINE_DIALOGUE,
            chatType = if (group) RoleplayChatType.GROUP else RoleplayChatType.SINGLE
        )
        if (group) roleplay.upsertScenarioWithParticipants(
            scenario, listOf(
                RoleplayGroupParticipant(scenarioId = scenario.id, assistantId = "a"),
                RoleplayGroupParticipant(scenarioId = scenario.id, assistantId = "b", sortOrder = 1)
            )
        ) else roleplay.upsertScenario(scenario)
        return Run(
            repository,
            roleplay,
            scenario,
            roleplay.startScenario(scenario.id).session,
            RoomMemoryRepository(f.database.memoryDao())
        )
    }

    @Test
    fun actualRpHttpRequestAndFailedNextTurn_keepIndependentDurableReceiptsWithLogsOff() =
        runBlocking {
            val server = MockWebServer()
            server.start()
            try {
                f.enable()
                val provider = f.settings.providers.single()
                    .copy(baseUrl = server.url("/v1/").toString(), selectedModel = "gpt-4.1-mini")
                f.settings = f.settings.copy(
                    providers = listOf(provider),
                    contextLogEnabled = false,
                    memoryAutoSummaryEvery = 0
                )
                val run = createRun(group = false)
                val context =
                    requireNotNull(f.archive.getRuntimeContext(run.session.conversationId, "a"))
                val record = f.remember(context, "芮珩将在731号室归还红色罗盘。")
                val gateway = DefaultAiGateway(
                    FakeSettingsStore(f.settings), ApiServiceFactory(),
                    searchRepository = object : SearchRepository {
                        override suspend fun search(
                            source: SearchSourceConfig,
                            query: String,
                            resultCount: Int
                        ): SearchResult = error("测试不允许搜索网络")
                    }, memoryArchiveRepository = f.archive
                )
                val logs = ContextLogStore().apply { setEnabled(false) }
                val jobs = mutableListOf<Job>()
                server.enqueue(MockResponse().setBody("""{"choices":[{"message":{"role":"assistant","content":"<dialogue speaker=\"character\" emotion=\"平静\">罗盘已经修好了。</dialogue>"},"finish_reason":"stop"}]}"""))
                assertNull(
                    execute(
                        run,
                        "a",
                        jobs,
                        this,
                        gatewayOverride = gateway,
                        logs = logs,
                        userText = "芮珩 731 的约定呢？"
                    ).errorMessage
                )
                jobs.joinAll()
                val firstBody = requireNotNull(
                    server.takeRequest(
                        5,
                        java.util.concurrent.TimeUnit.SECONDS
                    )
                ).body.readUtf8()
                val firstMessage = f.store.listMessages(context.conversationId)
                    .last { it.role == MessageRole.ASSISTANT }
                assertTrue(firstMessage.memoryRecallRequestId.isNotBlank())
                val firstReceipt = requireNotNull(
                    f.archive.getRecallReceipt(
                        context,
                        firstMessage.memoryRecallRequestId
                    )
                )
                assertEquals(listOf(record.id), firstReceipt.sent.map { it.id })
                assertEquals(
                    MemoryArchiveIdentity.fingerprint(firstBody),
                    firstReceipt.payloadFingerprint
                )
                assertEquals(firstMessage.id, firstReceipt.anchorMessageId)
                assertTrue(logs.snapshots.value.isEmpty())

                val preview =
                    DefaultPromptContextAssembler(memoryArchiveRepository = f.archive).assemble(
                        f.settings,
                        f.settings.assistants.first(),
                        requireNotNull(run.conversations.getConversation(context.conversationId)),
                        "芮珩",
                        f.store.listMessages(context.conversationId),
                        promptMode = PromptMode.ROLEPLAY,
                        markUsage = false
                    )
                assertNotNull(preview.memoryRecallPlan)
                assertNull(preview.promptEnvelope.memoryRecallRequest)
                assertNull(f.archive.getRecallReceipt(context, "missing-current-request"))

                server.enqueue(
                    MockResponse().setResponseCode(500).setBody("synthetic server failure")
                )
                assertNotNull(
                    execute(
                        run,
                        "a",
                        jobs,
                        this,
                        gatewayOverride = gateway,
                        logs = logs,
                        userText = "再确认芮珩的731号室。"
                    ).errorMessage
                )
                val failedMessage = f.store.listMessages(context.conversationId)
                    .last { it.role == MessageRole.ASSISTANT }
                assertNotEquals(
                    firstMessage.memoryRecallRequestId,
                    failedMessage.memoryRecallRequestId
                )
                val failed = requireNotNull(
                    f.archive.getRecallReceipt(
                        context,
                        failedMessage.memoryRecallRequestId
                    )
                )
                assertEquals(MemoryRecallStatus.FAILED, failed.status)
                assertEquals("HTTP_FAILED", failed.failureCode)
                assertEquals(
                    MemoryRecallStatus.SENT,
                    f.archive.getRecallReceipt(context, firstReceipt.requestId)!!.status
                )
                assertTrue(f.ai.snapshots.isEmpty())
            } finally {
                server.shutdown()
            }
        }

    private suspend fun execute(
        run: Run,
        characterId: String,
        memoryTasks: MutableList<Job>,
        scope: CoroutineScope,
        failure: Boolean = false,
        started: CompletableDeferred<Unit>? = null,
        gatewayOverride: AiGateway? = null,
        logs: ContextLogStore = ContextLogStore(),
        userText: String = "约好明天去706号房",
    ): RoleplayRoundTripExecutionOutcome {
        val assistant = f.settings.assistants.single { it.id == characterId }
        val gateway = gatewayOverride ?: object : AiGateway {
            override suspend fun generateImage(
                prompt: String,
                modelId: String
            ): List<com.example.myapplication.data.repository.ImageGenerationResult> =
                error("测试不允许图片网络")

            override suspend fun sendMessage(
                messages: List<ChatMessage>,
                systemPrompt: String,
                promptEnvelope: PromptEnvelope,
                toolingOptions: GatewayToolingOptions
            ): AssistantReply = error("不应走非流式生成")

            override fun sendMessageStream(
                messages: List<ChatMessage>, systemPrompt: String, promptMode: PromptMode,
                promptEnvelope: PromptEnvelope, toolingOptions: GatewayToolingOptions
            ): Flow<ChatStreamEvent> = flow {
                sentPayloads += com.example.myapplication.system.json.AppJson.gson.toJson(
                    GatewayRequestMessageBuilder.build(
                        messages, systemPrompt, promptMode, promptEnvelope,
                        imagePayloadResolver = { error("测试不允许解析真实图片") },
                        filePromptResolver = { error("测试不允许读取真实文件") },
                    )
                )
                emit(ChatStreamEvent.ContentDelta("我记住了，明天在706号房见阿澄。"))
                if (failure) throw java.io.IOException("synthetic failure")
                if (started != null) {
                    started.complete(Unit); awaitCancellation()
                }
                emit(ChatStreamEvent.Completed)
            }

            override fun parseAssistantSpecialOutput(
                content: String, existingParts: List<ChatMessagePart>, statusCardsEnabled: Boolean,
                hideStatusBlocksInBubble: Boolean
            ) = parseAssistantSpecialOutputLocally(
                content,
                existingParts,
                statusCardsEnabled,
                hideStatusBlocksInBubble
            )
        }
        var state = RoleplayUiState(
            settings = f.settings,
            currentScenario = run.scenario,
            currentSession = run.session,
            currentAssistant = assistant,
            isSending = true
        )
        val durable = f.coordinator()
        val executor = RoleplayRoundTripExecutor(
            aiGateway = gateway,
            conversationRepository = run.conversations,
            conversationSummaryRepository = run.summaries,
            phoneSnapshotRepository = EmptyPhoneSnapshotRepository,
            roleplayRepository = run.roleplay,
            promptContextAssembler = DefaultPromptContextAssembler(
                conversationSummaryRepository = run.summaries,
                memoryArchiveRepository = f.archive
            ),
            assistantRoundTripRunner = ConversationAssistantRoundTripRunner(
                run.conversations,
                gateway
            ),
            outputParser = RoleplayOutputParser(),
            nowProvider = { ++f.now },
            currentUiState = { state },
            updateUiState = { state = it(state) },
            updateRawMessages = {},
            launchGiftImageGeneration = { _, _ -> },
            launchVoiceSynthesis = { _, _ -> },
            launchConversationSummaryGeneration = { _, _, _, _, _ -> },
            launchAutomaticMemoryExtraction = { conversationId, _, _, target, _ ->
                memoryTasks += scope.launch {
                    durable.processAutomatic(
                        conversationId,
                        requireNotNull(target).id
                    )
                }
            },
            contextLogStore = logs,
            usesDurableMemory = { durable.isEnabled() },
            resolveMemoryRuntimeContext = { conversationId, character ->
                f.archive.getRuntimeContext(
                    conversationId,
                    character
                )
            },
        )
        val previous = f.store.listMessages(run.session.conversationId)
        val user = ChatMessage(
            "user-${UUID.randomUUID()}",
            run.session.conversationId,
            MessageRole.USER,
            userText,
            createdAt = ++f.now
        )
        val loading = ChatMessage(
            "reply-${UUID.randomUUID()}",
            run.session.conversationId,
            MessageRole.ASSISTANT,
            "",
            MessageStatus.LOADING,
            createdAt = ++f.now,
            speakerId = characterId,
            speakerName = assistant.name,
            roleplayInteractionMode = run.scenario.interactionMode
        )
        return executor.execute(
            state,
            run.scenario,
            run.session,
            "chat-model",
            assistant,
            previous + user,
            previous + user,
            RoundTripInitialPersistence.Append(listOf(user, loading)),
            loading,
            { previous + user + it })
    }

    private data class Run(
        val conversations: ConversationRepository,
        val roleplay: RoomRoleplayRepository,
        val scenario: RoleplayScenario,
        val session: RoleplaySession,
        val summaries: RoomMemoryRepository
    )
}
