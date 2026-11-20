package com.example.myapplication.conversation

import android.app.Application
import android.database.sqlite.SQLiteException
import com.example.myapplication.context.DefaultPromptContextAssembler
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.memory.MemoryEngineStateEntity
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.data.repository.roleplay.CompanionHistoryRepository
import com.example.myapplication.data.repository.roleplay.CompanionRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayOnlineReferenceSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.testutil.CompanionEntryTestFixture
import com.example.myapplication.testutil.FakePresetRepository
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionRequestCoordinatorTest : CompanionEntryTestFixture() {
    @Test
    fun approachAndInvite_freezeFreshHistoryAtExplicitGeneration() = runBlocking<Unit> {
        for (mode in listOf(CompanionOpeningMode.APPROACH, CompanionOpeningMode.INVITE)) {
            val context = scene()
            val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry-$mode")
            val selected =
                roleplay.selectCompanionOpening(entry.encounter.id, entry.encounter.id, mode)
            val fresh = add(context, "准备页停留后新收到的原文-$mode")
            val turn = repository.listTurns(selected.encounter.id).single()
            val coordinator = requests()
            val attempt = requireNotNull(coordinator.newAttempt(turn.id))
            assertTrue(attempt.descriptor.sourceMessages.any { it.messageId == fresh.id })
            reply()
            assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Generated)
            val body = payload()
            assertTrue(body.contains(fresh.content))
            assertTrue(body.contains(if (mode == CompanionOpeningMode.APPROACH) "开场候选：我靠近" else "开场候选：角色靠近"))
            assertTrue(body.contains("第三人称"))
            assertFalse(body.contains("静态问候哨兵"))
            val tools = JsonParser.parseString(body).asJsonObject.get("tools")
            assertTrue(tools == null || tools.isJsonNull || tools.asJsonArray.isEmpty())
            assertEquals(
                listOf(fresh.id),
                conversations.listMessages(context.conversationId).map { it.id })
            repository.endEncounter(selected.encounter.id)
            assertTrue(coordinator.acceptOpening(attempt.id) is CompanionGenerationResult.Rejected)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun openingAccept_failureKeepsPreviewAndRepeatedAcceptCommitsOnce() = runBlocking<Unit> {
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        var failCommit = true
        val fault = object : CompanionRepository by repository {
            override suspend fun commitAttempt(
                id: String,
                acceptOpening: Boolean
            ): CompanionCommitResult {
                if (acceptOpening && failCommit) throw SQLiteException("synthetic commit failure")
                return repository.commitAttempt(id, acceptOpening)
            }
        }
        val coordinator = requests(companionRepository = fault)
        val attempt = requireNotNull(coordinator.newAttempt(requireNotNull(entry.opening).turn.id))
        reply()
        assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Generated)
        payload()
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        assertTrue(archive.listPendingIngress(context).isEmpty())
        assertEquals(
            CompanionGenerationResult.NeedsSave(attempt.id, true),
            coordinator.acceptOpening(attempt.id)
        )
        assertNotNull(repository.getAttempt(attempt.id)?.artifact)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        failCommit = false
        assertTrue(coordinator.retrySave(attempt.id) is CompanionGenerationResult.Generated)
        assertTrue(coordinator.acceptOpening(attempt.id) is CompanionGenerationResult.Saved)
        val again = coordinator.acceptOpening(attempt.id) as CompanionGenerationResult.Saved
        assertTrue(again.receipt.alreadyCommitted)
        assertEquals(1, conversations.listMessages(context.conversationId).size)
        assertEquals(1, commits)
        assertEquals(1, server.requestCount)
        assertTrue(archive.listPendingIngress(context).isNotEmpty())
    }

    @Test
    fun discardedOpeningRerolls_neverBecomeMessagesOrSources() = runBlocking<Unit> {
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        val coordinator = requests()
        val turn = requireNotNull(entry.opening).turn
        val ids = mutableListOf<String>()
        repeat(3) {
            val attempt = requireNotNull(coordinator.newAttempt(turn.id))
            ids += attempt.id
            reply("<narration>候选 $it</narration>")
            assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Generated)
            payload()
            assertTrue(conversations.listMessages(context.conversationId).isEmpty())
            assertTrue(archive.listPendingIngress(context).isEmpty())
        }
        repository.endEncounter(entry.encounter.id)
        ids.forEach { assertTrue(coordinator.acceptOpening(it) is CompanionGenerationResult.Rejected) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun continueFailureRetryAndReroll_keepTypedHttpSemanticsAndStoredControl() = runBlocking<Unit> {
        val context = scene()
        add(context, "此前用户明确说只坐一会儿")
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val coordinator = requests()
        val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
        server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic failure"))
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Failed)
        val bodies = mutableListOf(payload())
        val retry =
            requireNotNull(coordinator.newAttempt(prepared.turn.id, allowUncertainRetry = true))
        reply()
        val saved = coordinator.generate(retry.id) as CompanionGenerationResult.Saved
        bodies += payload()
        val oldReply = saved.receipt.replyMessage
        val reroll = requireNotNull(coordinator.newAttempt(prepared.turn.id))
        reply("<dialogue>那就继续坐一会儿。</dialogue>")
        val replaced = coordinator.generate(reroll.id) as CompanionGenerationResult.Saved
        bodies += payload()
        bodies.forEach { body ->
            val users = messages(body).filter { it.asJsonObject["role"].asString == "user" }
            val instruction = text(users.last().asJsonObject["content"])
            assertTrue(instruction.contains("【场景留白】"))
            assertTrue(instruction.contains("本轮用户没有新增言行"))
            assertFalse(body.contains("（继续）"))
        }
        assertEquals(oldReply.id, replaced.receipt.replyMessage.id)
        assertEquals(oldReply.createdAt, replaced.receipt.replyMessage.createdAt)
        val stored = conversations.listMessages(context.conversationId)
        val control = stored.single { it.companionMemoryState == CompanionMemoryState.CONTROL }
        assertEquals(COMPANION_CONTINUE_DISPLAY_TEXT, control.content)
        assertFalse(stored.any { it.content.contains("【场景留白】") })
        val online = roleplay.getScenario(context.scenarioId)!!
            .copy(interactionMode = RoleplayInteractionMode.ONLINE_PHONE)
        val projected = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
            stored,
            online,
            settings.settingsFlow.first().assistants.first(),
            settings.settingsFlow.first(),
            RoleplayOutputParser()
        )
        assertFalse(projected.any { it.id == control.id })
        assertTrue(projected.any { it.id == oldReply.id })
        assertEquals(3, server.requestCount)
    }

    @Test
    fun ordinaryContinueText_isInputAndScenarioNamesStillResolve() = runBlocking<Unit> {
        val context = scene()
        val scenario = roleplay.getScenario(context.scenarioId)!!.copy(
            descriptionPromptEnabled = true,
            description = "{{char}} 正和 {{user}} 一起等雨停。",
            characterDisplayNameOverride = "阿璃",
            userDisplayNameOverride = "小舟"
        )
        roleplay.upsertScenario(scenario)
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val coordinator = requests()
        val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "继续")
        reply()
        assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        assertTrue(body.contains("阿璃 正和 小舟 一起等雨停。"))
        assertTrue(body.contains("【本轮互动】"))
        assertFalse(body.contains("【场景留白】"))
        assertFalse(body.contains("{{char}}"))
        val input = conversations.listMessages(context.conversationId)
            .single { it.role == MessageRole.USER }
        assertEquals("继续", input.content)
        assertEquals(CompanionSourceEligibility.TEXT_ALLOWED, input.companionSourceEligibility())
    }

    @Test
    fun finalHttp_aggregatesOnlySameDailyRolePersonaAndExcludesGroupPrivate() = runBlocking<Unit> {
        val old = scene()
        add(old, "允许的旧会话新鲜花店线索")
        val otherRole = scene(character = "bob")
        add(otherRole, "禁止的乙角色原文")
        val otherPersona = scene(persona = "身份乙")
        add(otherPersona, "禁止的乙身份原文")
        val independent = scene(purpose = MemoryPurpose.INDEPENDENT)
        add(independent, "禁止的独立剧情原文")
        val group = RoleplayScenario(
            id = "group",
            assistantId = "alice",
            memoryPurpose = MemoryPurpose.CONTINUOUS,
            userPersonaOverride = "身份甲",
            chatType = RoleplayChatType.GROUP,
            interactionMode = RoleplayInteractionMode.ONLINE_PHONE
        )
        roleplay.upsertScenarioWithParticipants(
            group, listOf(
                RoleplayGroupParticipant(scenarioId = group.id, assistantId = "alice"),
                RoleplayGroupParticipant(scenarioId = group.id, assistantId = "bob")
            )
        )
        val groupSession = roleplay.startScenario(group.id).session
        val groupContext = archive.getRuntimeContext(groupSession.conversationId, "alice")!!
        add(
            groupContext, "禁止的群私有原文", role = MessageRole.ASSISTANT,
            parts = listOf(thoughtMessagePart("禁止的群私有原文"))
        )
        val current = scene()
        val body = sendInput(current)
        assertTrue(body.contains("允许的旧会话新鲜花店线索"))
        listOf(
            "禁止的乙角色原文",
            "禁止的乙身份原文",
            "禁止的独立剧情原文",
            "禁止的群私有原文"
        ).forEach {
            assertFalse(body.contains(it))
        }
        assertTrue(archive.listRecords(current).isEmpty())
        val independentBody = sendInput(independent)
        assertTrue(independentBody.contains("禁止的独立剧情原文"))
        assertFalse(independentBody.contains("允许的旧会话新鲜花店线索"))
    }

    @Test
    fun finalHttp_emptyAuthorizationAndBreakpointCannotFallBackToRawHistory() = runBlocking<Unit> {
        val context = scene()
        val before = add(context, "范围外断点前", "before")
        add(context, "范围外排除行", "excluded")
        add(context, "合法断点后", "after")
        var requestedSelection = CompanionHistorySelection(20, allowedConversationIds = emptySet())
        val scoped = object : CompanionHistoryRepository by history {
            override suspend fun freeze(
                context: MemoryRuntimeContext, cutoff: CompanionHistoryCursor,
                selection: CompanionHistorySelection, originalScope: AllowedCompanionHistoryScope?
            ) =
                history.freeze(context, cutoff, requestedSelection, originalScope)
        }
        val empty = sendInput(context, requests(historyRepository = scoped))
        assertFalse(empty.contains("范围外"))
        assertFalse(empty.contains("合法断点后"))
        requestedSelection = CompanionHistorySelection(
            20,
            startsAfter = mapOf(
                context.conversationId to CompanionHistoryCursor(
                    before.createdAt,
                    before.id
                )
            ),
            excludedMessageIds = setOf("excluded")
        )
        val limited = sendInput(context, requests(historyRepository = scoped))
        assertTrue(limited.contains("合法断点后"))
        assertFalse(limited.contains("范围外断点前"))
        assertFalse(limited.contains("范围外排除行"))
    }

    @Test
    fun reroll_finalHttpKeepsOriginalCutoff() = runBlocking<Unit> {
        val context = scene()
        add(context, "本轮合法起点", "before")
        val coordinator = requests()
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "原触发输入")
        reply()
        assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Saved)
        payload()
        add(context, "之后才出现的线上内容", "later")
        val attempt = requireNotNull(coordinator.newAttempt(turn.turn.id))
        reply()
        assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        assertTrue(body.contains("本轮合法起点"))
        assertTrue(body.contains("原触发输入"))
        assertFalse(body.contains("之后才出现的线上内容"))
    }

    @Test
    fun chatFinalGuard_sourceDeletedDuringReceiptPreparationSendsNothing() =
        verifyLastGuard(OpenAiTextApiMode.CHAT_COMPLETIONS, ProviderApiProtocol.OPENAI_COMPATIBLE)

    @Test
    fun responsesFinalGuard_sourceDeletedDuringReceiptPreparationSendsNothing() =
        verifyLastGuard(OpenAiTextApiMode.RESPONSES, ProviderApiProtocol.OPENAI_COMPATIBLE)

    @Test
    fun anthropicFinalGuard_sourceDeletedDuringReceiptPreparationSendsNothing() =
        verifyLastGuard(OpenAiTextApiMode.CHAT_COMPLETIONS, ProviderApiProtocol.ANTHROPIC)

    @Test
    fun responsesContinue_rerollKeepsSemanticsAndActualReceipt() =
        verifyContinueProtocol(OpenAiTextApiMode.RESPONSES, ProviderApiProtocol.OPENAI_COMPATIBLE)

    @Test
    fun anthropicContinue_rerollKeepsSemanticsAndActualReceipt() =
        verifyContinueProtocol(OpenAiTextApiMode.CHAT_COMPLETIONS, ProviderApiProtocol.ANTHROPIC)

    @Test
    fun inputEditedDuringFinalPreparation_doesNotSendStaleInput() =
        verifyLastMutation { context, requestId ->
            val attempt = repository.getAttempt(requestId)!!
            val turn = repository.getTurn(attempt.turnId)!!
            assertTrue(
                repository.editMessage(
                    requireNotNull(turn.inputMessageId),
                    requireNotNull(turn.inputRevision),
                    "最新人工输入"
                )
            )
            assertEquals(
                "最新人工输入",
                conversations.listMessages(context.conversationId)
                    .last { it.role == MessageRole.USER }.content
            )
        }

    @Test
    fun modeChangedDuringFinalPreparation_doesNotSendOfflinePrompt() =
        verifyLastMutation { context, _ ->
            roleplay.upsertScenario(
                roleplay.getScenario(context.scenarioId)!!
                    .copy(interactionMode = RoleplayInteractionMode.ONLINE_PHONE)
            )
        }

    private fun verifyLastMutation(mutate: suspend (MemoryRuntimeContext, String) -> Unit) =
        runBlocking<Unit> {
            val context = scene()
            val mutating = object : MemoryArchiveRepository by archive {
                override suspend fun prepareRecallTransmission(
                    requestId: String, payloadFingerprint: String,
                    materials: List<MemoryMaterialRef>, omitted: List<MemoryRecallOmission>
                ): Int? {
                    val sequence = requireNotNull(
                        archive.prepareRecallTransmission(
                            requestId,
                            payloadFingerprint,
                            materials,
                            omitted
                        )
                    )
                    mutate(context, requestId)
                    return sequence
                }
            }
            val coordinator = requests(gateway = gateway(archiveRepository = mutating))
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val prepared =
                coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "原来的输入")
            reply()
            assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Failed)
            assertEquals(0, server.requestCount)
            assertEquals(
                "SOURCE_CHANGED",
                archive.getRecallReceipt(context, prepared.attempt.id)?.failureCode
            )
        }

    @Test
    fun fallbackBlockedBeforeSecondHttp_keepsFirstTransmissionEvidence() = runBlocking<Unit> {
        val provider =
            settings.settingsFlow.first().providers.single().copy(selectedModel = "gpt-4.1-mini")
        settings.saveProviderSettings(listOf(provider), provider.id)
        val context = scene()
        val source = add(context, "兼容重试前会删除的原文")
        var preparations = 0
        val mutating = object : MemoryArchiveRepository by archive {
            override suspend fun prepareRecallTransmission(
                requestId: String, payloadFingerprint: String,
                materials: List<MemoryMaterialRef>, omitted: List<MemoryRecallOmission>
            ): Int? {
                val sequence = requireNotNull(
                    archive.prepareRecallTransmission(
                        requestId,
                        payloadFingerprint,
                        materials,
                        omitted
                    )
                )
                preparations++
                if (preparations == 2) conversations.deleteMessage(
                    context.conversationId,
                    source.id
                )
                return sequence
            }
        }
        val coordinator = requests(gateway = gateway(archiveRepository = mutating))
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "本轮输入")
        server.enqueue(
            MockResponse().setResponseCode(400).setBody("""{"error":"unsupported temperature"}""")
        )
        reply()
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Failed)
        assertEquals(2, preparations)
        assertEquals(1, server.requestCount)
        val receipt = requireNotNull(archive.getRecallReceipt(context, prepared.attempt.id))
        assertEquals("SOURCE_CHANGED", receipt.failureCode)
        assertEquals(
            listOf(MemoryRecallStatus.SENT, MemoryRecallStatus.PREPARED),
            receipt.transmissions.map { it.status })
        assertNotNull(receipt.transmissions.first().sentAt)
        assertNull(receipt.transmissions.last().sentAt)
    }

    private fun verifyContinueProtocol(mode: OpenAiTextApiMode, api: ProviderApiProtocol) =
        runBlocking<Unit> {
            protocol(mode, api)
            val context = scene()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val coordinator = requests()
            val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
            var attempt = turn.attempt
            repeat(2) { iteration ->
                if (iteration > 0) attempt = requireNotNull(coordinator.newAttempt(turn.turn.id))
                reply(mode = mode, api = api)
                assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Saved)
                val body = payload()
                assertTrue(body.contains("【场景留白】"))
                assertTrue(body.contains("本轮用户没有新增言行"))
                assertFalse(body.contains(COMPANION_CONTINUE_DISPLAY_TEXT))
                val receipt = requireNotNull(archive.getRecallReceipt(context, attempt.id))
                assertEquals(MemoryRecallStatus.SENT, receipt.status)
                assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
            }
            assertEquals(2, server.requestCount)
            assertEquals(2, conversations.listMessages(context.conversationId).size)
        }

    private fun verifyLastGuard(mode: OpenAiTextApiMode, api: ProviderApiProtocol) =
        runBlocking<Unit> {
            protocol(mode, api)
            val context = scene()
            val source = add(context, "准备回执时将删除的原文")
            var preparedCount = 0
            val mutating = object : MemoryArchiveRepository by archive {
                override suspend fun prepareRecallTransmission(
                    requestId: String, payloadFingerprint: String,
                    materials: List<MemoryMaterialRef>, omitted: List<MemoryRecallOmission>
                ): Int? {
                    val sequence = requireNotNull(
                        archive.prepareRecallTransmission(
                            requestId,
                            payloadFingerprint,
                            materials,
                            omitted
                        )
                    )
                    preparedCount++
                    conversations.deleteMessage(context.conversationId, source.id)
                    return sequence
                }
            }
            val coordinator = requests(gateway = gateway(archiveRepository = mutating))
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "这次输入")
            reply(mode = mode, api = api)
            assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Failed)
            assertEquals(1, preparedCount)
            assertEquals(0, server.requestCount)
            val receipt = requireNotNull(archive.getRecallReceipt(context, turn.attempt.id))
            assertEquals(MemoryRecallStatus.FAILED, receipt.status)
            assertEquals("SOURCE_CHANGED", receipt.failureCode)
            assertTrue(receipt.sent.isEmpty())
            assertNull(receipt.sentAt)
            assertEquals(MemoryRecallStatus.PREPARED, receipt.transmissions.single().status)
            assertNull(receipt.transmissions.single().sentAt)
        }

    @Test
    fun memoryRecall_eachRequestIsFreshAndDisabledOrFailedRecallHasNoOldInjection() =
        runBlocking<Unit> {
            database.memoryArchiveDao()
                .upsertEngineState(MemoryEngineStateEntity(engineVersion = 2, updatedAt = clock++))
            val initial = settings.settingsFlow.first()
            settings.saveAssistants(
                initial.assistants.map { it.copy(memoryEnabled = true) },
                "alice"
            )
            val context = scene()
            archive.addRecord(
                context,
                ManualMemoryRecordDraft(
                    "固定召回哨兵，原文中的 {{char}} 不得二次展开。",
                    pinned = true
                )
            )
            val coordinator = requests()
            val first = sendInput(context, coordinator)
            assertTrue(first.contains("固定召回哨兵"))
            assertTrue(first.contains("{{char}}"))
            settings.saveAssistants(initial.assistants, "alice")
            val disabled = sendInput(context, coordinator)
            assertFalse(disabled.contains("固定召回哨兵"))
            settings.saveAssistants(
                initial.assistants.map { it.copy(memoryEnabled = true) },
                "alice"
            )
            val failing = object : MemoryArchiveRepository by archive {
                override suspend fun getRecallSnapshot(context: MemoryRuntimeContext): MemoryRecallSnapshot? =
                    error("synthetic recall failure")
            }
            val failed = sendInput(
                context, requests(
                    assembler = DefaultPromptContextAssembler(
                        memoryArchiveRepository = failing, worldBookRepository = worldBook
                    )
                )
            )
            assertFalse(failed.contains("固定召回哨兵"))
            assertTrue(failed.contains("【本轮互动】"))
            assertEquals(3, server.requestCount)
        }

    @Test
    fun presetDepthZeroWorldBook_preservesRolesOrderAndActualInputDecoration() = runBlocking<Unit> {
        worldBook.upsertEntry(
            WorldBookEntry(
                id = "flowers",
                title = "花店",
                content = "世界书海边花店哨兵",
                keywords = listOf("海边花店")
            )
        )
        worldBook.upsertEntry(
            WorldBookEntry(
                id = "control",
                title = "不应触发",
                content = "控制指令误触发哨兵",
                keywords = listOf("本轮互动")
            )
        )
        val preset = Preset(
            id = "depth", name = "深度测试", entries = listOf(
                PresetPromptEntry(id = "role", title = "角色", content = "{{context}}", order = 0),
                PresetPromptEntry(
                    id = "a",
                    title = "A",
                    role = PresetPromptRole.ASSISTANT,
                    content = "深度0-A",
                    injectionPosition = PresetPromptInjectionPosition.ABSOLUTE,
                    injectionDepth = 0,
                    injectionOrder = 30
                ),
                PresetPromptEntry(
                    id = "book",
                    title = "世界书",
                    role = PresetPromptRole.SYSTEM,
                    kind = PresetPromptEntryKind.WORLD_INFO_BEFORE,
                    content = "{{world_info}}",
                    injectionPosition = PresetPromptInjectionPosition.ABSOLUTE,
                    injectionDepth = 0,
                    injectionOrder = 20
                ),
                PresetPromptEntry(
                    id = "u",
                    title = "U",
                    role = PresetPromptRole.USER,
                    content = "深度0-U",
                    injectionPosition = PresetPromptInjectionPosition.ABSOLUTE,
                    injectionDepth = 0,
                    injectionOrder = 10
                ),
            )
        )
        val context = scene()
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(presetId = preset.id)
        )
        val coordinator = requests(
            assembler = DefaultPromptContextAssembler(
                memoryArchiveRepository = archive,
                worldBookRepository = worldBook,
                presetRepository = FakePresetRepository(listOf(preset))
            )
        )
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val prepared =
            coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "海边花店还开着吗？")
        reply()
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        val actual = messages(body).map { it.asJsonObject }
        val inputIndex =
            actual.indexOfFirst { it["role"].asString == "user" && text(it["content"]).contains("海边花店还开着吗？") }
        assertTrue(inputIndex >= 0)
        assertTrue(text(actual[inputIndex]["content"]).contains("【本轮互动】"))
        assertEquals(
            listOf("assistant", "system", "user"),
            actual.takeLast(3).map { it["role"].asString })
        assertEquals("深度0-A", text(actual[actual.size - 3]["content"]))
        assertTrue(text(actual[actual.size - 2]["content"]).contains("世界书海边花店哨兵"))
        assertEquals("深度0-U", text(actual.last()["content"]))
        assertFalse(actual.takeLast(3).any { text(it["content"]).contains("【本轮互动】") })
        assertFalse(body.contains("控制指令误触发哨兵"))
        val receipt = requireNotNull(archive.getRecallReceipt(context, prepared.attempt.id))
        assertEquals(MemoryArchiveIdentity.fingerprint(body), receipt.payloadFingerprint)
        assertEquals(MemoryRecallStatus.SENT, receipt.status)
    }

    private suspend fun sendInput(
        context: MemoryRuntimeContext,
        coordinator: CompanionRequestCoordinator = requests()
    ): String {
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "这次想聊什么？")
        reply()
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
        return payload()
    }

    private fun messages(body: String): List<JsonElement> =
        JsonParser.parseString(body).asJsonObject["messages"].asJsonArray.toList()

    private fun text(value: JsonElement): String = when {
        value.isJsonPrimitive -> value.asString
        value.isJsonArray -> value.asJsonArray.joinToString("\n") { part -> part.asJsonObject.get("text")?.asString.orEmpty() }
        else -> ""
    }
}
