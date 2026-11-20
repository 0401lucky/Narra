package com.example.myapplication.data.repository.ai.tooling

import android.app.Application
import com.example.myapplication.conversation.MemoryExtractionBatcher
import com.example.myapplication.conversation.ConversationSummaryCoverageSupport
import com.example.myapplication.data.repository.context.ArchivePendingMemoryProposalRepository
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.context.RoomMemoryRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MemoryCommitBatch
import com.example.myapplication.model.MemoryExtractionArtifact
import com.example.myapplication.model.MemoryExtractionSnapshot
import com.example.myapplication.model.MemoryJobPurpose
import com.example.myapplication.model.MemoryProposalStatus
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MemoryWriteResult as ArchiveWriteResult
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.FakeMemoryRepository
import com.example.myapplication.testutil.FakeSettingsStore
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.memoryResponse
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryArchiveToolFlowTest {
    private lateinit var fixture: MemoryExtractionFixture
    private val noSearch = object : SearchRepository {
        override suspend fun search(
            source: SearchSourceConfig,
            query: String,
            resultCount: Int
        ): SearchResult =
            error("记忆工具测试禁止真实搜索")
    }

    @Before
    fun setUp() = runBlocking {
        fixture = MemoryExtractionFixture(persistent = true)
        fixture.enable()
        fixture.ai.answer = { error("记忆工具保存和审批不应请求模型") }
    }

    @After
    fun tearDown() {
        fixture.close()
    }

    @Test
    fun persistentToolProposal_survivesReopenAndApprovalWritesExactlyOnce() = runBlocking {
        val context = fixture.contexts().single()
        fixture.add(context, "source", "我已确认以后把暗号设为青石。")
        val originalServices = createServices()
        val result = SaveMemoryTool().execute(
            ToolInvocation(
                "propose", SaveMemoryTool.NAME, argumentsMap = mapOf(
                    "memory_type" to "persistent",
                    "content" to "双方约定的暗号是青石。",
                    "reason" to "明确长期约定",
                    "importance" to 82,
                )
            ),
            toolContext(context, originalServices),
        )
        assertFalse(result.isError)
        val payload = JsonParser.parseString(result.payload).asJsonObject
        assertEquals("pending_confirmation", payload.get("status").asString)
        val proposalId = payload.get("proposal_id").asString
        val original = requireNotNull(originalServices.pending.getProposal(proposalId))
        assertEquals(context, original.memoryContext)
        assertTrue(original.sourceRefs.isNotEmpty())
        assertEquals(0, storedRecordCount())

        fixture.reopen()
        val restoredServices = createServices()

        assertEquals(original, restoredServices.pending.getProposal(proposalId))
        assertEquals(
            original,
            restoredServices.pending.observeProposal(context.conversationId).first()
        )
        assertEquals(
            MemoryProposalStatus.PENDING,
            restoredServices.pending.observeHistory(context.conversationId).first().single().status
        )
        val saved = requireNotNull(restoredServices.write.approveProposal(proposalId))
        assertEquals("双方约定的暗号是青石。", saved.content)
        assertEquals(82, saved.importance)
        assertEquals(1, storedRecordCount())
        assertNull(restoredServices.pending.observeProposal(context.conversationId).first())
        restoredServices.write.approveProposal(proposalId)
            ?.let { repeated -> assertEquals(saved.id, repeated.id) }
        assertEquals(1, storedRecordCount())
        val replay =
            fixture.archive.approveStoredProposal(proposalId) as ArchiveWriteResult.Committed
        assertTrue(replay.alreadyCommitted)
        assertEquals(listOf(saved.id), replay.recordIds)
        assertEquals(1, fixture.archive.getRecordDetails(context, saved.id)!!.revisions.size)
        assertEquals(
            MemoryProposalStatus.APPROVED,
            restoredServices.pending.observeHistory(context.conversationId).first().single().status
        )
        assertTrue(restoredServices.legacy.currentEntries().isEmpty())
        assertTrue(fixture.ai.snapshots.isEmpty())
    }

    @Test
    fun rejectedProposal_survivesReopenAndCannotCreateMemory() = runBlocking {
        val context = fixture.contexts().single()
        fixture.add(context, "source", "这只是候选约定，暂时不要记住。")
        val services = createServices()
        val proposal = propose(context, services, "尚待确认的候选约定。")

        services.write.rejectProposal(proposal.id)
        fixture.reopen()
        val restored = createServices()

        assertNull(restored.pending.getProposal(proposal.id))
        assertNull(restored.write.approveProposal(proposal.id))
        assertTrue(fixture.archive.approveStoredProposal(proposal.id) is ArchiveWriteResult.Conflict)
        val history = restored.pending.observeHistory(context.conversationId).first().single()
        assertEquals(MemoryProposalStatus.REJECTED, history.status)
        assertEquals(proposal.content, history.content)
        assertEquals(0, storedRecordCount())
    }

    @Test
    fun toolAndSelectedWrites_doNotAdvanceLaterAutomaticCursorOrHideUnprocessedSpans() =
        runBlocking {
            val context = fixture.contexts().single()
            fixture.add(context, "first", "第一条中已确认剧情线索。")
            val services = createServices()
            val sourceOne = fixture.archive.listPendingIngress(context).single().source
            val runtime = toolContext(context, services)
            val saved = services.write.saveSceneMemory(runtime, "门钥匙在青石下。", 75)
            val replay = services.write.saveSceneMemory(runtime, "门钥匙在青石下。", 75)
            assertFalse(saved.deduplicated)
            assertTrue(replay.deduplicated)
            assertEquals(1, storedRecordCount())
            val toolRecord =
                fixture.database.memoryArchiveDao().listRecords(context.spaceId, 10, 0).single()
            assertEquals(
                MemoryJobPurpose.TOOL_WRITE,
                fixture.archive.getJob(requireNotNull(toolRecord.jobId))!!.purpose
            )
            assertEquals(0L, fixture.archive.getCursor(context.spaceId)!!.committedSequence)
            assertTrue(fixture.archive.listCommittedSpans(sourceOne.id).isEmpty())

            fixture.ai.answer = { memoryResponse(it, empty = true) }
            val selected = fixture.coordinator().processSelection(
                requireNotNull(fixture.archive.getManagementContext(context.characterId, context.spaceId, context.personaKey)),
                com.example.myapplication.model.MemoryMessageSelection(context.conversationId, listOf("first")),
                reprocessExisting = true,
            )
            assertEquals("", selected.failureCode)
            assertTrue(selected.committedJobs > 0)
            assertEquals(0L, fixture.archive.getCursor(context.spaceId)!!.committedSequence)
            assertTrue(fixture.archive.listCommittedSpans(sourceOne.id).isEmpty())
            val manualRequestCount = fixture.ai.snapshots.size
            assertTrue(manualRequestCount > 0)

            fixture.add(context, "second", "第二条自动提取没有长期内容。")
            val sourceTwo = fixture.archive.listPendingIngress(context)
                .single { it.source.messageId == "second" }
            val snapshot = MemoryExtractionSnapshot(
                sources = MemoryExtractionBatcher.sourceSpans(
                    sourceTwo.source,
                    fixture.archive.getSourceSegments(context, sourceTwo.source.id),
                    context.characterId
                ),
                targets = emptyList(),
                characterName = "甲",
                userName = "合成用户",
                sourceSequence = sourceTwo.sequence,
            )
            val job = fixture.archive.planJob(
                context,
                "automatic-second",
                snapshot.sources.map { it.ref },
                requestJson = AppJson.gson.toJson(snapshot)
            )
            assertNotNull(fixture.archive.claimJob(job.id, "auto"))
            assertTrue(
                fixture.archive.saveExtraction(
                    job.id,
                    "auto",
                    AppJson.gson.toJson(
                        MemoryExtractionArtifact(
                            completion = memoryResponse(
                                snapshot,
                                empty = true
                            )
                        )
                    )
                )
            )
            assertTrue(
                fixture.archive.commitJob(
                    job.id,
                    "auto",
                    MemoryCommitBatch()
                ) is ArchiveWriteResult.Committed
            )
            assertEquals(0L, fixture.archive.getCursor(context.spaceId)!!.committedSequence)
            assertEquals(
                listOf("first"),
                fixture.archive.listPendingIngress(context).map { it.source.messageId })
            assertTrue(fixture.archive.listCommittedSpans(sourceOne.id).isEmpty())

            val progress =
                fixture.coordinator().processAutomatic(context.conversationId, context.characterId)

            assertEquals("", progress.failureCode)
            assertEquals(
                setOf("first"),
                fixture.ai.snapshots.drop(manualRequestCount).flatMap { it.sources }
                    .map { it.messageId }.toSet()
            )
            assertEquals(
                sourceTwo.sequence,
                fixture.archive.getCursor(context.spaceId)!!.committedSequence
            )
            assertTrue(fixture.archive.listPendingIngress(context).isEmpty())
            assertEquals(1, storedRecordCount())
        }

    @Test
    fun independentSpace_cannotReadOrWriteDailyOrOtherCharacterThroughScopeArguments() =
        runBlocking {
            fixture.settings = fixture.settings.copy(assistants = fixture.settings.assistants.map {
                it.copy(useGlobalMemory = true)
            })
            val daily = fixture.contexts().single()
            val story = fixture.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
            val other = fixture.contexts(characters = listOf("b")).single()
            val services = createServices()
            remember(daily, services, "daily-source", "仅日常 A 可见 DLY_A")
            remember(other, services, "other-source", "仅角色 B 可见 CHAR_B")
            remember(story, services, "story-source", "仅独立剧情可见 STORY_A")
            val runtime = toolContext(story, services)
            val reader = ReadMemoryTool(memoryArchiveRepository = fixture.archive)
            val normal = reader.execute(ToolInvocation("read", ReadMemoryTool.NAME), runtime)
            assertEquals(setOf("仅独立剧情可见 STORY_A"), memoryContents(normal))
            val injected = reader.execute(
                ToolInvocation(
                    "injected-read", ReadMemoryTool.NAME, argumentsMap = mapOf(
                        "scope" to "global",
                        "scope_id" to daily.spaceId,
                        "character_id" to "b",
                        "space_id" to other.spaceId,
                    )
                ), runtime
            )
            assertFalse(injected.payload.contains("DLY_A"))
            assertFalse(injected.payload.contains("CHAR_B"))
            if (!injected.isError) assertEquals(
                setOf("仅独立剧情可见 STORY_A"),
                memoryContents(injected)
            )
            val rejected = SaveMemoryTool().execute(
                ToolInvocation(
                    "injected-write", SaveMemoryTool.NAME, argumentsMap = mapOf(
                        "content" to "越权材料",
                        "memory_type" to "persistent",
                        "scope" to "global",
                        "space_id" to daily.spaceId,
                    )
                ), runtime
            )
            assertTrue(rejected.isError)
            assertEquals(3, storedRecordCount())
            assertTrue(fixture.database.memoryArchiveDao().listProposals(story.spaceId).isEmpty())

            val proposal = propose(story, services, "独立剧情中的长期约定。")
            assertEquals(MemoryScopeType.ASSISTANT, proposal.scopeType)
            assertEquals(story, proposal.memoryContext)
            assertNotNull(services.write.approveProposal(proposal.id))
            assertEquals(
                setOf("仅日常 A 可见 DLY_A"),
                fixture.archive.listRecords(daily).map { it.content }.toSet()
            )
            assertEquals(
                setOf("仅角色 B 可见 CHAR_B"),
                fixture.archive.listRecords(other).map { it.content }.toSet()
            )
            assertEquals(2, fixture.archive.listRecords(story).size)

            val forged = runtime.copy(
                runtimeContext = requireNotNull(runtime.runtimeContext).copy(memoryContext = daily)
            )
            assertTrue(
                reader.execute(
                    ToolInvocation("forged-read", ReadMemoryTool.NAME),
                    forged
                ).isError
            )
            var refused = false
            try {
                services.write.saveSceneMemory(forged, "伪造归属不能保存", 70)
            } catch (_: IllegalArgumentException) {
                refused = true
            }
            assertTrue(refused)
            assertEquals(4, storedRecordCount())
        }

    @Test
    fun availability_usesCurrentArchiveInsteadOfOtherSpaceOrGlobalPreference() = runBlocking {
        val daily = fixture.contexts().single()
        val story = fixture.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
        val services = createServices()
        remember(daily, services, "daily", "日常档案的材料")
        val provider = fixture.settings.providers.single().copy(
            models = listOf(
                ModelInfo(
                    modelId = "chat-model",
                    abilities = setOf(ModelAbility.TOOL),
                    abilitiesCustomized = true
                ),
            )
        )
        val resolver = ToolAvailabilityResolver(
            noSearch, services.legacy, EmptyWorldBookRepository, EmptyConversationSummaryRepository,
            services.write, fixture.archive,
        )

        suspend fun resolve() = resolver.resolve(
            fixture.settings, provider, "chat-model", PromptMode.ROLEPLAY,
            GatewayToolingOptions.localContextOnly(
                requireNotNull(
                    toolContext(
                        story,
                        services
                    ).runtimeContext
                ).copy(memoryContext = null)
            ),
        )

        val emptyStory = resolve()
        assertEquals(story, emptyStory.toolContext.runtimeContext?.memoryContext)
        assertFalse(ReadMemoryTool.NAME in emptyStory.enabledToolNames)
        assertTrue(SaveMemoryTool.NAME in emptyStory.enabledToolNames)

        remember(story, services, "story", "当前剧情的材料")

        val populatedStory = resolve()
        assertTrue(ReadMemoryTool.NAME in populatedStory.enabledToolNames)
        assertTrue(SaveMemoryTool.NAME in populatedStory.enabledToolNames)
    }

    @Test
    fun summaryToolAndAvailabilityRejectAnotherCharacterContext() = runBlocking {
        val contexts = fixture.contexts(characters = listOf("a", "b"))
        val a = contexts.single { it.characterId == "a" }
        val b = contexts.single { it.characterId == "b" }
        val services = createServices()
        remember(b, services, "source", "仅属于乙的记忆")
        val summaries = RoomMemoryRepository(fixture.database.memoryDao())
        val messages = fixture.store.listMessages(b.conversationId)
        summaries.upsertSummary(
            ConversationSummary(
                conversationId = b.conversationId, assistantId = "b", summary = "乙的私有摘要哨兵",
                coveredMessageCount = messages.size, memoryContext = b,
                sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(messages),
                completionStatus = PromptCompletionStatus.COMPLETED,
            )
        )
        val tool = GetConversationSummaryTool(memoryArchiveRepository = fixture.archive)
        val normal = toolContext(b, services).copy(conversationSummaryRepository = summaries)
        val valid = tool.execute(ToolInvocation("valid", tool.name), normal)
        assertFalse(valid.isError)
        assertTrue(valid.payload.contains("乙的私有摘要哨兵"))
        val aContext = toolContext(a, services)
        val forged = aContext.copy(
            conversationSummaryRepository = summaries,
            runtimeContext = requireNotNull(aContext.runtimeContext).copy(memoryContext = b),
        )
        val rejected = tool.execute(ToolInvocation("forged", tool.name), forged)
        assertTrue(rejected.isError)
        assertFalse(rejected.payload.contains("乙的私有摘要哨兵"))
        val provider = fixture.settings.providers.single().copy(
            models = listOf(
                ModelInfo(
                    modelId = "chat-model",
                    abilities = setOf(ModelAbility.TOOL),
                    abilitiesCustomized = true
                ),
            )
        )
        val available = ToolAvailabilityResolver(
            noSearch,
            services.legacy,
            EmptyWorldBookRepository,
            summaries,
            services.write,
            fixture.archive,
        ).resolve(
            fixture.settings, provider, "chat-model", PromptMode.ROLEPLAY,
            GatewayToolingOptions.localContextOnly(requireNotNull(forged.runtimeContext)),
        )
        assertFalse(GetConversationSummaryTool.NAME in available.enabledToolNames)
        assertFalse(ReadMemoryTool.NAME in available.enabledToolNames)
    }

    @Test
    fun editedSource_erasesOnlyItsPendingProposalAndRejectsLateApproval() = runBlocking {
        val context = fixture.contexts().single()
        val services = createServices()
        fixture.add(context, "first", "旧地址为青石路 128 号。")
        val stale = propose(context, services, "旧地址为青石路 128 号。")
        fixture.add(context, "second", "另一条独立且未变更的约定。")
        val unaffected = propose(context, services, "另一条独立约定。")

        fixture.add(context, "first", "地址已改为青石路 182 号。")

        assertNull(services.write.approveProposal(stale.id))
        assertScrubbed(stale.id)
        assertNotNull(services.pending.getProposal(unaffected.id))
        assertNotNull(services.write.approveProposal(unaffected.id))
        assertEquals(
            listOf(unaffected.content),
            fixture.archive.listRecords(context).map { it.content })
    }

    @Test
    fun changedPersona_invalidatesLateApprovalButPreservesOriginalProposalHistory() = runBlocking {
        val context = fixture.contexts(persona = "旧扮演身份").single()
        fixture.add(context, "source", "旧身份下的明确约定。")
        val services = createServices()
        val proposal = propose(context, services, "只属于旧身份的约定。")
        val original = requireNotNull(fixture.database.memoryArchiveDao().getProposal(proposal.id))

        val changed = fixture.archive.bindConversation(
            RoleplayScenario(
                id = context.scenarioId,
                assistantId = context.characterId,
                userPersonaOverride = "新的扮演身份",
                memoryPurpose = MemoryPurpose.CONTINUOUS
            ),
            context.conversationId, listOf(context.characterId), context.runId,
        ).single()

        assertNotEquals(context.personaKey, changed.personaKey)
        assertTrue(changed.generation > context.generation)
        assertNull(services.write.approveProposal(proposal.id))
        assertNull(services.pending.observeProposal(context.conversationId).first())
        val archived = requireNotNull(fixture.database.memoryArchiveDao().getProposal(proposal.id))
        assertEquals("INVALIDATED", archived.status)
        assertEquals(original.content, archived.content)
        assertEquals(original.reason, archived.reason)
        assertEquals(original.sourceRefsJson, archived.sourceRefsJson)
        assertEquals(context.personaKey, archived.personaKey)
        assertEquals(context.generation, archived.generation)
        assertEquals(0, storedRecordCount())
    }

    @Test
    fun archivedRun_blocksLateApprovalAndRetainsTheOldProposalAndSources() = runBlocking {
        val context = fixture.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
        fixture.add(context, "source", "当前独立剧情中的约定。")
        val services = createServices()
        val proposal = propose(context, services, "属于旧独立剧情的建议。")
        val original = requireNotNull(fixture.database.memoryArchiveDao().getProposal(proposal.id))

        fixture.archive.archiveConversation(context.conversationId)

        assertNull(fixture.archive.getRuntimeContext(context.conversationId, context.characterId))
        assertTrue(fixture.archive.getSpace(context.spaceId)!!.generation > context.generation)
        assertNull(services.write.approveProposal(proposal.id))
        assertNull(services.pending.observeProposal(context.conversationId).first())
        val archived = requireNotNull(fixture.database.memoryArchiveDao().getProposal(proposal.id))
        assertEquals("INVALIDATED", archived.status)
        assertEquals(original.content, archived.content)
        assertEquals(original.reason, archived.reason)
        assertEquals(original.sourceRefsJson, archived.sourceRefsJson)
        assertEquals(context.spaceId, archived.spaceId)
        assertEquals(context.runId, archived.runId)
        assertEquals(0, storedRecordCount())
    }

    @Test
    fun explicitlyDeletedSpace_scrubsProposalAndPreventsLateApproval() = runBlocking {
        val context = fixture.contexts(purpose = MemoryPurpose.INDEPENDENT).single()
        fixture.add(context, "source", "这份档案稍后会被明确删除。")
        val services = createServices()
        val proposal = propose(context, services, "属于待删除档案的建议。")

        assertTrue(fixture.archive.deleteSpace(context) is ArchiveWriteResult.Committed)

        assertNull(services.write.approveProposal(proposal.id))
        assertScrubbed(proposal.id)
        assertEquals(0, storedRecordCount())
    }

    private fun createServices(): Services {
        val legacy = FakeMemoryRepository()
        val pending = ArchivePendingMemoryProposalRepository(fixture.archive)
        return Services(
            legacy, pending,
            DefaultMemoryWriteService(
                FakeSettingsStore(fixture.settings),
                legacy,
                pending,
                fixture.ai,
                nowProvider = { fixture.now },
                memoryArchiveRepository = fixture.archive
            ),
        )
    }

    private suspend fun toolContext(
        context: MemoryRuntimeContext,
        services: Services
    ): ToolContext = ToolContext(
        searchRepository = noSearch,
        memoryRepository = services.legacy,
        memoryWriteService = services.write,
        runtimeContext = GatewayToolRuntimeContext(
            promptMode = PromptMode.ROLEPLAY,
            assistant = fixture.settings.resolvedAssistants()
                .single { it.id == context.characterId },
            conversation = requireNotNull(fixture.store.getConversation(context.conversationId)),
            recentMessages = fixture.store.listMessages(context.conversationId),
            memoryContext = context,
        ),
    )

    private suspend fun propose(
        context: MemoryRuntimeContext,
        services: Services,
        content: String
    ): PendingMemoryProposal =
        services.write.proposePersistentMemory(
            toolContext(context, services),
            content,
            "来自当前用户明确发言",
            74
        )

    private suspend fun remember(
        context: MemoryRuntimeContext,
        services: Services,
        messageId: String,
        content: String
    ) {
        fixture.add(context, messageId, content)
        services.write.saveSceneMemory(toolContext(context, services), content, 70)
    }

    private fun memoryContents(result: ToolExecutionResult): Set<String> {
        assertFalse(result.isError)
        return JsonParser.parseString(result.payload).asJsonObject.getAsJsonArray("memories")
            .map { it.asJsonObject.get("content").asString }.toSet()
    }

    private suspend fun assertScrubbed(id: String) {
        val proposal = requireNotNull(fixture.database.memoryArchiveDao().getProposal(id))
        assertEquals("INVALIDATED", proposal.status)
        assertEquals("", proposal.content)
        assertEquals("", proposal.reason)
        assertEquals("[]", proposal.sourceRefsJson)
        assertNull(proposal.finalRecordId)
    }

    private fun storedRecordCount(): Int = fixture.database.openHelper.readableDatabase
        .query("SELECT COUNT(*) FROM memory_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private data class Services(
        val legacy: FakeMemoryRepository,
        val pending: ArchivePendingMemoryProposalRepository,
        val write: DefaultMemoryWriteService,
    )
}
