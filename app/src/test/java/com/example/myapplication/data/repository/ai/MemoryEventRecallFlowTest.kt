package com.example.myapplication.data.repository.ai

import android.app.Application
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.context.MemoryRecallEngine
import com.example.myapplication.context.MemoryRecallRendering
import com.example.myapplication.context.MemoryRecallText
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MemoryEventDetails
import com.example.myapplication.model.MemoryEventMemberStatus
import com.example.myapplication.model.MemoryEventMutationResult
import com.example.myapplication.model.MemoryEventSummaryStatus
import com.example.myapplication.model.MemoryMaterialKind
import com.example.myapplication.model.MemoryPurpose
import com.example.myapplication.model.MemoryRecallMaterial
import com.example.myapplication.model.MemoryRecallOmissionReason
import com.example.myapplication.model.MemoryRecallPlan
import com.example.myapplication.model.MemoryRecallReceipt
import com.example.myapplication.model.MemoryRecallRequest
import com.example.myapplication.model.MemoryRecallStatus
import com.example.myapplication.model.MemoryRecord
import com.example.myapplication.model.MemoryRecordVersion
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.MemoryWriteResult
import com.example.myapplication.model.Preset
import com.example.myapplication.model.PresetPromptEntry
import com.example.myapplication.model.PresetPromptEntryKind
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.testutil.FakePresetRepository
import com.example.myapplication.testutil.FakeSettingsStore
import com.example.myapplication.testutil.MemoryExtractionFixture
import com.example.myapplication.testutil.RecordingMemoryEventAi
import com.example.myapplication.testutil.eventCoordinator
import com.example.myapplication.testutil.memoryEventResponse
import com.example.myapplication.testutil.recallRequest
import com.example.myapplication.testutil.remember
import com.google.gson.JsonParser
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class MemoryEventRecallFlowTest {
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
    fun liveMemberHit_sendsPreviousSummaryWithOnlyTheFourCoveredVersions() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val plan = recall(f, event.context, LIVE_QUERY)

            assertEquals(
                setOf(event.eventId, event.live.id),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertSummaryCoverage(
                f,
                event,
                plan.materials.single { it.ref.kind == MemoryMaterialKind.EVENT })
            val transmission = transmit(f, plan, LIVE_QUERY)

            assertTrue(transmission.system.contains(event.summary))
            assertTrue(transmission.system.contains(event.live.content))
            val sentEvent = transmission.receipt.sent.single { it.kind == MemoryMaterialKind.EVENT }
            assertEquals(event.coveredVersions.toSet(), sentEvent.supportingRecords.toSet())
            assertFalse(sentEvent.supportingRecords.any { it.recordId == event.live.id })
            assertEquals(
                MemoryEventMemberStatus.LIVE,
                details(f, event).members.single { it.record.id == event.live.id }.status
            )
            assertEquals(1, event.ai.snapshots.size)
        }
    }

    @Test
    fun archivedMemberHit_expandsLatestLiveProgressIntoActualRequest() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val plan = recall(f, event.context, ORIGIN_QUERY)

            assertEquals(
                setOf(event.covered.first().id, event.eventId, event.live.id),
                plan.materials.map { it.ref.id }.toSet()
            )
            val progress = plan.materials.single { it.ref.id == event.live.id }
            assertTrue(progress.label.contains("尚未收入事件摘要的新进展"))
            assertSummaryCoverage(
                f,
                event,
                plan.materials.single { it.ref.kind == MemoryMaterialKind.EVENT })
            val transmission = transmit(f, plan, ORIGIN_QUERY)

            assertTrue(transmission.system.contains(event.covered.first().content))
            assertTrue(transmission.system.contains(event.live.content))
            assertTrue(transmission.system.contains(event.summary))
            assertEquals(
                event.coveredVersions.toSet(),
                transmission.receipt.sent.single { it.kind == MemoryMaterialKind.EVENT }.supportingRecords.toSet()
            )
        }
    }

    @Test
    fun scopeAndPersonaIsolation_excludesForeignEventFromRequestsAndReceipts() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val original = transmit(f, recall(f, event.context, ORIGIN_QUERY), ORIGIN_QUERY)
            val contexts = listOf(
                f.contexts(characters = listOf("b"), persona = PERSONA).single(),
                f.contexts(persona = "另一合成身份").single(),
                f.contexts(persona = PERSONA, purpose = MemoryPurpose.INDEPENDENT).single(),
            )

            contexts.forEachIndexed { index, context ->
                val own = f.remember(context, "$ORIGIN_QUERY 在隔离档案 $index 中只指向独立材料。")
                val plan = recall(f, context, ORIGIN_QUERY)
                assertEquals(listOf(own.id), plan.materials.map { it.ref.id })
                assertFalse(plan.selected.any { it.id == event.eventId || it.id == event.live.id })
                val transmission = transmit(f, plan, ORIGIN_QUERY)

                assertTrue(transmission.system.contains(own.content))
                assertFalse(transmission.system.contains(SUMMARY_SENTINEL))
                assertFalse(transmission.system.contains(event.live.content))
                assertFalse(transmission.rawBody.contains(event.eventId))
                assertFalse(transmission.rawBody.contains(event.covered.first().id))
                assertNull(f.archive.getRecallReceipt(context, original.request.requestId))
                assertNull(
                    f.archive.getRecallReceipt(
                        event.context,
                        transmission.request.requestId
                    )
                )
            }
            assertEquals(4, server.requestCount)
        }
    }

    @Test
    fun tightBudget_sendsExactLiveTailAndRecordsOmittedSummaryWithoutChangingArchive() =
        runBlocking {
            MemoryExtractionFixture().use { f ->
                val longLive = "正文首部保留哨兵。" + "不影响本轮检索的维修记录。".repeat(160) +
                        "末尾 $LIVE_QUERY 已验收，编号 812 的校准结果为合格。"
                val longSummary =
                    "$SUMMARY_SENTINEL：" + "委托、检查、备件和交付都有完整原料。".repeat(90)
                val event = publishWithLiveProgress(f, summary = longSummary, liveBody = longLive)
                val before = details(f, event)
                val recordBefore =
                    requireNotNull(f.archive.getRecordDetails(event.context, event.live.id))
                val plan = recall(f, event.context, LIVE_QUERY, tokenBudget = 320)

                assertTrue(plan.estimatedTokens <= 320)
                assertEquals(MemoryRecallText.estimateTokens(plan.section), plan.estimatedTokens)
                val material = plan.materials.single()
                assertEquals(event.live.id, material.ref.id)
                assertTrue(material.fragment)
                assertTrue(material.ref.spanStart > 0)
                assertEquals(
                    longLive.substring(material.ref.spanStart, material.ref.spanEnd),
                    material.content
                )
                assertTrue(material.content.contains(LIVE_QUERY))
                assertTrue(plan.omitted.any { it.ref.id == event.eventId && it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
                assertTrue(plan.omitted.any { it.ref.id == event.live.id && it.reason == MemoryRecallOmissionReason.PARTIAL_RECORD })
                val transmission = transmit(f, plan, LIVE_QUERY)

                assertFalse(transmission.system.contains(SUMMARY_SENTINEL))
                assertFalse(transmission.system.contains("正文首部保留哨兵"))
                assertTrue(transmission.receipt.selected.any { it.id == event.eventId })
                assertFalse(transmission.receipt.sent.any { it.id == event.eventId })
                assertTrue(transmission.receipt.omitted.containsAll(plan.omitted))
                assertEquals(before, details(f, event))
                assertEquals(recordBefore, f.archive.getRecordDetails(event.context, event.live.id))
            }
        }

    @Test
    fun omittedLiveOutcome_labelsOldSummaryStateAsHistoricalInActualHttp() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val finalLive = "第五天 $LIVE_QUERY 已通过终验，罗盘修复事件现已结束。" +
                    "完整结案记录需要保留。".repeat(70)
            val event = publishWithLiveProgress(f, liveBody = finalLive)
            val before = details(f, event)
            val wide = recall(f, event.context, ORIGIN_QUERY, maxExperiences = 2)
            assertEquals(
                setOf(event.covered.first().id, event.eventId, event.live.id),
                wide.materials.map { it.ref.id }.toSet()
            )
            val previousMaterials = wide.materials.filter { it.ref.id != event.live.id }
            val budget =
                MemoryRecallText.estimateTokens(MemoryRecallRendering.section(previousMaterials)) + 16
            val plan =
                recall(f, event.context, ORIGIN_QUERY, tokenBudget = budget, maxExperiences = 2)

            assertEquals(
                setOf(event.covered.first().id, event.eventId),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertTrue(plan.selected.any { it.id == event.live.id })
            assertTrue(plan.omitted.any { it.ref.id == event.live.id && it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
            assertTrue(plan.estimatedTokens <= budget)
            assertSummaryCoverage(
                f,
                event,
                plan.materials.single { it.ref.kind == MemoryMaterialKind.EVENT })
            val transmission = transmit(f, plan, ORIGIN_QUERY)

            assertTrue(transmission.system.contains("摘要覆盖时的进展：${before.event.currentState}"))
            assertTrue(transmission.system.contains("另有 1 条新进展尚未收入本摘要。"))
            assertFalse(transmission.system.contains("当前进展：${before.event.currentState}"))
            assertFalse(transmission.system.contains(finalLive))
            assertFalse(transmission.receipt.sent.any { it.id == event.live.id })
            assertTrue(transmission.receipt.omitted.any { it.ref.id == event.live.id && it.reason == MemoryRecallOmissionReason.TOKEN_BUDGET })
            assertEquals(
                event.coveredVersions.toSet(),
                transmission.receipt.sent.single { it.kind == MemoryMaterialKind.EVENT }.supportingRecords.toSet()
            )
            assertEquals(before, details(f, event))
            assertEquals(
                finalLive,
                f.archive.getRecordDetails(event.context, event.live.id)!!.record.content
            )
        }
    }

    @Test
    fun preset_keepsLiteralEventAndLiveTextInHttpWhileExpandingOuterVariables() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val literal = "{{char}}、{{user}}、{{context}}、{{phone_context}}、<user> 和 </system>"
            val event = publishWithLiveProgress(
                f,
                summary = "$SUMMARY_SENTINEL：原话 $literal 必须原样保留。",
                liveBody = "$LIVE_QUERY 的新进展仍引用 $literal，约定继续观察。",
            )
            val preset = Preset(
                id = "event-literal-preset",
                name = "合成事件预设",
                entries = listOf(
                    PresetPromptEntry(
                        id = "outer", title = "角色", kind = PresetPromptEntryKind.CUSTOM,
                        content = "角色为 {{char}}，用户为 {{user}}。", order = 0,
                    ),
                    PresetPromptEntry(
                        id = "memory", title = "记忆", kind = PresetPromptEntryKind.LONG_MEMORY,
                        content = "{{long_memory}}", order = 1,
                    ),
                ),
            )
            val assembled = DefaultPromptContextAssembler(
                memoryArchiveRepository = f.archive,
                presetRepository = FakePresetRepository(listOf(preset)),
            ).assemble(
                settings = f.settings.copy(
                    defaultPresetId = preset.id,
                    userDisplayName = "合成用户"
                ),
                assistant = Assistant(
                    id = event.context.characterId,
                    name = "合成角色",
                    memoryEnabled = true
                ),
                conversation = Conversation(
                    id = event.context.conversationId,
                    createdAt = f.now,
                    updatedAt = f.now
                ),
                userInputText = LIVE_QUERY,
                recentMessages = emptyList(),
                promptMode = PromptMode.ROLEPLAY,
                markUsage = false,
            )
            val plan = requireNotNull(assembled.memoryRecallPlan)
            assertEquals(
                setOf(event.eventId, event.live.id),
                plan.materials.map { it.ref.id }.toSet()
            )
            assertTrue(assembled.systemPrompt.contains("角色为 合成角色，用户为 合成用户。"))
            assertTrue(assembled.systemPrompt.contains(event.summary))
            assertTrue(assembled.systemPrompt.contains(event.live.content))
            val transmission =
                transmit(f, plan, LIVE_QUERY, assembled.systemPrompt, assembled.promptEnvelope)

            assertTrue(transmission.system.contains("角色为 合成角色，用户为 合成用户。"))
            assertTrue(transmission.system.contains(event.summary))
            assertTrue(transmission.system.contains(event.live.content))
            assertEquals(
                1,
                Regex(Regex.escape(MemoryRecallRendering.HEADER)).findAll(transmission.system)
                    .count()
            )
            assertSummaryCoverage(
                f,
                event,
                plan.materials.single { it.ref.kind == MemoryMaterialKind.EVENT })
            assertEquals(event.summary, details(f, event).event.summary)
        }
    }

    @Test
    fun editCoveredMember_removesStaleSummaryFromNextHttpAndKeepsCorrectedRecall() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val previous = transmit(f, recall(f, event.context, LIVE_QUERY), LIVE_QUERY)
            val changed = event.covered.first()
            val corrected = "$ORIGIN_QUERY 已纠正：实际委托编号是 915，旧约定取消。"
            assertTrue(
                f.archive.editRecord(
                    event.context,
                    changed.id,
                    changed.revision,
                    corrected
                ) is MemoryWriteResult.Committed
            )

            val plan = recall(f, event.context, "$ORIGIN_QUERY $LIVE_QUERY")
            assertFalse(plan.selected.any { it.kind == MemoryMaterialKind.EVENT })
            assertEquals((event.covered.map { it.id } + event.live.id).toSet(),
                plan.materials.map { it.ref.id }.toSet())
            val current = plan.materials.single { it.ref.id == changed.id }
            assertEquals(changed.revision + 1, current.ref.revision)
            assertEquals(corrected, current.content)
            val transmission = transmit(f, plan, "$ORIGIN_QUERY $LIVE_QUERY")

            assertTrue(transmission.system.contains(corrected))
            assertTrue(transmission.system.contains(event.live.content))
            assertFalse(transmission.system.contains(SUMMARY_SENTINEL))
            assertFalse(transmission.system.contains(changed.content))
            assertFalse(transmission.receipt.sent.any { it.kind == MemoryMaterialKind.EVENT })
            assertNotEquals(previous.request.requestId, transmission.request.requestId)
            assertTrue(previous.receipt.sent.any { it.id == event.eventId })
        }
    }

    @Test
    fun deleteCoveredMember_removesStaleSummaryFromHttpButRetainsLegalLiveRecall() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val previous = transmit(f, recall(f, event.context, LIVE_QUERY), LIVE_QUERY)
            val deleted = event.covered.first()
            assertTrue(
                f.archive.deleteRecord(
                    event.context,
                    deleted.id,
                    deleted.revision
                ) is MemoryWriteResult.Committed
            )
            val currentContext = requireNotNull(
                f.archive.getRuntimeContext(
                    event.context.conversationId,
                    event.context.characterId
                )
            )
            assertTrue(currentContext.generation > event.context.generation)
            assertNull(f.archive.getRecallSnapshot(event.context))

            val plan = recall(f, currentContext, LIVE_QUERY)
            val remainingIds =
                event.covered.filter { it.id != deleted.id }.map { it.id } + event.live.id
            assertEquals(remainingIds.toSet(), plan.materials.map { it.ref.id }.toSet())
            assertFalse(plan.selected.any { it.kind == MemoryMaterialKind.EVENT || it.id == deleted.id })
            val transmission = transmit(f, plan, LIVE_QUERY)

            assertTrue(transmission.system.contains(event.live.content))
            assertFalse(transmission.system.contains(SUMMARY_SENTINEL))
            assertFalse(transmission.rawBody.contains(event.eventId))
            assertFalse(transmission.rawBody.contains(deleted.id))
            assertFalse(transmission.receipt.sent.any { it.kind == MemoryMaterialKind.EVENT || it.id == deleted.id })
            assertNotEquals(previous.request.requestId, transmission.request.requestId)
            assertNull(f.archive.getRecallReceipt(currentContext, previous.request.requestId))
            assertEquals(
                event.live.content,
                f.archive.getRecordDetails(currentContext, event.live.id)!!.record.content
            )
        }
    }

    @Test
    fun coveredSourceChangesAfterAssembly_rejectsEventBeforeAnyHttpSend() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val event = publishWithLiveProgress(f)
            val plan = recall(f, event.context, LIVE_QUERY)
            val changed = event.covered.first()
            assertFalse(plan.materials.any { it.ref.kind == MemoryMaterialKind.RECORD && it.ref.id == changed.id })
            val user = f.add(event.context, "query-${UUID.randomUUID()}", LIVE_QUERY)
            val request = f.recallRequest(plan)
            val guarded = object : MemoryArchiveRepository by f.archive {
                override suspend fun prepareRecallReceipt(request: MemoryRecallRequest): Boolean {
                    val saved = f.archive.prepareRecallReceipt(request)
                    assertTrue(
                        f.archive.editRecord(
                            event.context,
                            changed.id,
                            changed.revision,
                            "$ORIGIN_QUERY 已在发送前更正。"
                        ) is MemoryWriteResult.Committed
                    )
                    return saved
                }
            }
            server.enqueue(chatReply())

            val result = runCatching {
                gateway(f, guarded).sendMessageStream(
                    listOf(user), plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = request),
                ).toList()
            }

            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
            val receipt =
                requireNotNull(f.archive.getRecallReceipt(event.context, request.requestId))
            assertEquals(MemoryRecallStatus.FAILED, receipt.status)
            assertEquals("SOURCE_CHANGED", receipt.failureCode)
            assertTrue(receipt.sent.isEmpty())
            assertTrue(receipt.transmissions.isEmpty())
        }
    }

    @Test
    fun unlinkAfterAssembly_rejectsRawEventExpansionBeforeAnyHttpSend() = runBlocking {
        MemoryExtractionFixture().use { f ->
            val context = f.contexts(persona = PERSONA).single()
            val origin = f.remember(context, "原始前情：在公园遗失合成钥匙。")
            val latest = f.remember(context, "$LIVE_QUERY 凭条码领取成功。")
            val linked = f.archive.linkEventMembers(
                context,
                latest.version(),
                origin.version(),
                "失物领取"
            ) as MemoryEventMutationResult.Updated
            val plan = recall(f, context, LIVE_QUERY)
            assertTrue(plan.materials.all { it.ref.kind == MemoryMaterialKind.RECORD })
            assertEquals(setOf(origin.id, latest.id), plan.materials.map { it.ref.id }.toSet())
            assertEquals(
                linked.eventId,
                plan.materials.single { it.ref.id == origin.id }.ref.eventContext!!.eventId
            )
            val user = f.add(context, "query-${UUID.randomUUID()}", LIVE_QUERY)
            val request = f.recallRequest(plan)
            val guarded = object : MemoryArchiveRepository by f.archive {
                override suspend fun prepareRecallReceipt(request: MemoryRecallRequest): Boolean {
                    val saved = f.archive.prepareRecallReceipt(request)
                    assertTrue(
                        f.archive.unlinkEventMember(
                            context,
                            linked.eventId,
                            origin.id,
                            linked.generation
                        ) is MemoryEventMutationResult.Updated
                    )
                    return saved
                }
            }
            server.enqueue(chatReply())

            val result = runCatching {
                gateway(f, guarded).sendMessageStream(
                    listOf(user), plan.section, PromptMode.ROLEPLAY,
                    PromptEnvelope(memoryRecallRequest = request),
                ).toList()
            }

            assertTrue(result.isFailure)
            assertEquals(0, server.requestCount)
            val receipt = requireNotNull(f.archive.getRecallReceipt(context, request.requestId))
            assertEquals(MemoryRecallStatus.FAILED, receipt.status)
            assertEquals("SOURCE_CHANGED", receipt.failureCode)
            assertTrue(receipt.sent.isEmpty())
            assertTrue(receipt.transmissions.isEmpty())
            assertEquals(
                origin.content,
                f.archive.getRecordDetails(context, origin.id)!!.record.content
            )
        }
    }

    /** 来源实际跨天；所有记录均经真实来源/版本/任务事务写入，再由真实事件协调器发布。 */
    private suspend fun publishWithLiveProgress(
        f: MemoryExtractionFixture,
        summary: String = "$SUMMARY_SENTINEL：受托检查罗盘，依次更换齿轮并完成交付。",
        liveBody: String = "第五天 $LIVE_QUERY 发来验收补充：温度校准仍需复查，编号 812。",
    ): EventFixture {
        f.enable()
        val context = f.contexts(persona = PERSONA).single()
        val contents = listOf(
            "第一天 $ORIGIN_QUERY 委托修复罗盘，初始编号 706。",
            "第二天 PROGRESSSHARP 检查确认需要更换齿轮。",
            "第三天 DELIVERYBIRCH 新齿轮已送达并完成安装。",
            "第四天 FINISHEDCEDAR 修复完成，罗盘已交还。",
        )
        val records = contents.map { content ->
            f.now += DAY_MILLIS
            f.remember(context, content)
        }
        var eventId = ""
        records.drop(1).forEach { record ->
            val linked = f.archive.linkEventMembers(
                context,
                record.version(),
                records.first().version(),
                "罗盘修复"
            )
            assertTrue(linked is MemoryEventMutationResult.Updated)
            eventId = (linked as MemoryEventMutationResult.Updated).eventId
        }
        val ai = RecordingMemoryEventAi().apply {
            answer = { snapshot ->
                memoryEventResponse(
                    snapshot,
                    summary = summary,
                    state = "已交付，等待后续验收。"
                )
            }
        }
        val coordinator = f.eventCoordinator(ai)
        val processed = coordinator.processAutomatic(context)
        assertEquals("", processed.failureCode)
        assertEquals(1, processed.committedEvents)
        assertEquals(1, ai.snapshots.size)
        assertEquals(
            records.map { it.version() }.toSet(),
            ai.snapshots.single().members.map { it.record.version() }.toSet()
        )
        val occurred =
            ai.snapshots.single().members.flatMap { it.sources }.map { it.occurredAt }.sorted()
        assertEquals(4, occurred.size)
        assertTrue(occurred.zipWithNext().all { (left, right) -> right - left >= DAY_MILLIS })
        val published = requireNotNull(f.archive.getEventDetails(context, eventId))
        assertEquals(MemoryEventSummaryStatus.READY, published.event.summaryStatus)
        assertEquals(4, published.members.size)
        assertTrue(published.members.all { it.status == MemoryEventMemberStatus.ARCHIVED && it.coveredRevision == it.record.revision })

        f.now += DAY_MILLIS
        val live = f.remember(context, liveBody)
        assertTrue(
            f.archive.linkEventMembers(
                context,
                live.version(),
                records.first().version(),
                "罗盘修复"
            ) is MemoryEventMutationResult.Updated
        )
        val next = coordinator.processAutomatic(context)
        assertEquals("", next.failureCode)
        assertEquals(0, next.committedEvents)
        assertEquals(1, ai.snapshots.size)
        val updated = requireNotNull(f.archive.getEventDetails(context, eventId))
        assertEquals(MemoryEventSummaryStatus.READY, updated.event.summaryStatus)
        assertEquals(published.event.summaryRevision, updated.event.summaryRevision)
        assertEquals(summary, updated.event.summary)
        val added = updated.members.single { it.record.id == live.id }
        assertEquals(MemoryEventMemberStatus.LIVE, added.status)
        assertNull(added.coveredRevision)
        return EventFixture(context, eventId, records, live, summary, ai)
    }

    private suspend fun assertSummaryCoverage(
        f: MemoryExtractionFixture,
        event: EventFixture,
        material: MemoryRecallMaterial,
    ) {
        val snapshot = requireNotNull(f.archive.getRecallSnapshot(event.context))
        val summary = snapshot.events.single { it.id == event.eventId }
        assertEquals(event.coveredVersions.toSet(), summary.supportingRecords.toSet())
        assertEquals(
            (event.coveredVersions + event.live.version()).toSet(),
            summary.associatedRecords.toSet()
        )
        assertEquals(event.coveredVersions.toSet(), material.ref.supportingRecords.toSet())
        val coveredSources = event.covered.flatMap { record ->
            requireNotNull(f.archive.getRecordDetails(event.context, record.id)).sourceRefs
        }.toSet()
        val liveSources = requireNotNull(
            f.archive.getRecordDetails(
                event.context,
                event.live.id
            )
        ).sourceRefs.toSet()
        assertEquals(coveredSources, material.ref.sourceRefs.toSet())
        assertTrue(material.ref.sourceRefs.none { it in liveSources })
        assertTrue(material.content.contains(event.summary))
        assertTrue(material.content.contains("摘要覆盖时的进展：已交付，等待后续验收。"))
        assertTrue(material.content.contains("另有 1 条新进展尚未收入本摘要。"))
        assertFalse(material.content.contains("当前进展：已交付，等待后续验收。"))
    }

    private suspend fun recall(
        f: MemoryExtractionFixture,
        context: MemoryRuntimeContext,
        query: String,
        tokenBudget: Int = 2_000,
        maxExperiences: Int = 8,
    ): MemoryRecallPlan = requireNotNull(
        MemoryRecallEngine(f.archive, now = { f.now }).recall(
            context,
            query,
            emptyList(),
            maxExperiences,
            tokenBudget,
            false
        ),
    )

    /** 比对真实 HTTP 的材料正文、ID/revision/span，再核对持久回执及原始 body 指纹。 */
    private suspend fun transmit(
        f: MemoryExtractionFixture,
        plan: MemoryRecallPlan,
        query: String,
        systemPrompt: String = plan.section,
        envelope: PromptEnvelope = PromptEnvelope(),
    ): Transmission {
        val user = f.add(plan.context, "query-${UUID.randomUUID()}", query)
        val request = f.recallRequest(plan)
        server.enqueue(chatReply())
        gateway(f).sendMessageStream(
            listOf(user), systemPrompt, PromptMode.ROLEPLAY,
            envelope.copy(memoryRecallRequest = request),
        ).toList()
        val recorded = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer $HTTP_KEY", recorded.getHeader("Authorization"))
        val rawBody = recorded.body.readUtf8()
        val body = JsonParser.parseString(rawBody).asJsonObject
        assertEquals(HTTP_MODEL, body["model"].asString)
        val system = body.getAsJsonArray("messages")
            .filter { it.asJsonObject["role"].asString == "system" }
            .joinToString("\n") { it.asJsonObject["content"].asString }
        val receipt = requireNotNull(f.archive.getRecallReceipt(plan.context, request.requestId))
        assertEquals(MemoryRecallStatus.SENT, receipt.status)
        assertEquals(request.requestId, receipt.requestId)
        assertEquals(request.anchorMessageId, receipt.anchorMessageId)
        assertEquals(user.id, receipt.userMessageId)
        assertEquals(plan.selected, receipt.selected)
        assertEquals(plan.materials.map { it.ref }, receipt.sent)
        assertTrue(receipt.omitted.containsAll(plan.omitted))
        assertEquals(MemoryArchiveIdentity.fingerprint(rawBody), receipt.payloadFingerprint)
        val attempt = receipt.transmissions.single()
        assertEquals(MemoryRecallStatus.SENT, attempt.status)
        assertEquals(receipt.sent, attempt.materials)
        assertEquals(receipt.payloadFingerprint, attempt.payloadFingerprint)
        val snapshot = requireNotNull(f.archive.getRecallSnapshot(plan.context))
        plan.materials.forEach { material ->
            val ref = material.ref
            val opening = Regex(
                "<narra_memory key=\"[^\"]+\" kind=\"${ref.kind.name}\" id=\"${Regex.escape(ref.id)}\" revision=\"${ref.revision}\" span=\"${ref.spanStart}:${ref.spanEnd}\" fragment=\"${material.fragment}\">",
            )
            val match = opening.findAll(system).toList().single()
            assertTrue(
                system.substring(match.range.last + 1)
                    .startsWith("\n${material.label}\n${material.content}\n</narra_memory>")
            )
            assertEquals(
                MemoryArchiveIdentity.fingerprint(material.content),
                ref.contentFingerprint
            )
            val full = if (ref.kind == MemoryMaterialKind.RECORD) {
                val record = snapshot.records.single { it.id == ref.id }
                assertEquals(record.revision, ref.revision)
                assertEquals(
                    snapshot.recordSources[record.id].orEmpty().toSet(),
                    ref.sourceRefs.toSet()
                )
                record.content
            } else {
                assertEquals(MemoryMaterialKind.EVENT, ref.kind)
                val event = snapshot.events.single { it.id == ref.id }
                assertEquals(event.revision, ref.revision)
                assertEquals(event.supportingRecords.toSet(), ref.supportingRecords.toSet())
                assertEquals(event.sourceRefs.toSet(), ref.sourceRefs.toSet())
                event.content
            }
            assertEquals(full.substring(ref.spanStart, ref.spanEnd), material.content)
        }
        return Transmission(request, receipt, rawBody, system)
    }

    private fun gateway(
        f: MemoryExtractionFixture,
        archive: MemoryArchiveRepository = f.archive,
    ): DefaultAiGateway {
        val provider = ProviderSettings(
            id = "event-http-provider", name = "合成事件传输",
            baseUrl = server.url("/v1/").toString(), apiKey = HTTP_KEY, selectedModel = HTTP_MODEL,
        )
        val factory = ApiServiceFactory()
        fun assertLocal(baseUrl: String, key: String) {
            assertEquals(server.url("/v1/").toString(), baseUrl)
            assertEquals(HTTP_KEY, key)
        }
        return DefaultAiGateway(
            settingsStore = FakeSettingsStore(
                f.settings.copy(
                    providers = listOf(provider),
                    selectedProviderId = provider.id
                )
            ),
            apiServiceFactory = factory,
            apiServiceProvider = { base, key -> assertLocal(base, key); factory.create(base, key) },
            imageApiServiceProvider = { _, _ -> error("事件测试禁止图片 API") },
            anthropicApiProvider = { _, _ -> error("本事件传输测试只配置本机 Chat API") },
            streamClientProvider = { base, key ->
                assertLocal(
                    base,
                    key
                ); factory.createStreamingClient(base, key)
            },
            requestClientProvider = { base, key ->
                assertLocal(
                    base,
                    key
                ); factory.createRequestClient(base, key)
            },
            anthropicStreamClientProvider = { _, _ -> error("本事件传输测试只配置本机 Chat API") },
            searchRepository = object : SearchRepository {
                override suspend fun search(
                    source: SearchSourceConfig,
                    query: String,
                    resultCount: Int
                ): SearchResult = error("事件测试禁止搜索网络")
            },
            ioDispatcher = Dispatchers.IO,
            memoryArchiveRepository = archive,
        )
    }

    private suspend fun details(
        f: MemoryExtractionFixture,
        event: EventFixture
    ): MemoryEventDetails =
        requireNotNull(f.archive.getEventDetails(event.context, event.eventId))

    private fun MemoryRecord.version() = MemoryRecordVersion(id, revision)

    private data class EventFixture(
        val context: MemoryRuntimeContext,
        val eventId: String,
        val covered: List<MemoryRecord>,
        val live: MemoryRecord,
        val summary: String,
        val ai: RecordingMemoryEventAi,
    ) {
        val coveredVersions: List<MemoryRecordVersion>
            get() = covered.map {
                MemoryRecordVersion(
                    it.id,
                    it.revision
                )
            }
    }

    private data class Transmission(
        val request: MemoryRecallRequest,
        val receipt: MemoryRecallReceipt,
        val rawBody: String,
        val system: String,
    )

    private fun chatReply() = MockResponse().setHeader("Content-Type", "application/json")
        .setBody("""{"choices":[{"message":{"role":"assistant","content":"合成回复"},"finish_reason":"stop"}]}""")

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val PERSONA = "合成修复者"
        const val ORIGIN_QUERY = "ORIGINQUARTZ"
        const val LIVE_QUERY = "LIVEAZURITE"
        const val SUMMARY_SENTINEL = "事件前情证据哨兵"
        const val HTTP_KEY = "event-http-test-key"
        const val HTTP_MODEL = "synthetic-event-chat-model"
    }
}
