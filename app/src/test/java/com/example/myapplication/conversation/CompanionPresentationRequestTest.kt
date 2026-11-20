package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionPresentationSupport
import com.example.myapplication.roleplay.companion.CompanionReplyProjector
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionEntryTestFixture
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.myapplication.viewmodel.CompanionEntryPhase
import com.example.myapplication.viewmodel.CompanionRecoveryAction
import com.example.myapplication.viewmodel.RoleplayCompanionViewModel
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionPresentationRequestTest : CompanionEntryTestFixture() {
    private val protocols = listOf(
        ProviderApiProtocol.OPENAI_COMPATIBLE to OpenAiTextApiMode.CHAT_COMPLETIONS,
        ProviderApiProtocol.OPENAI_COMPATIBLE to OpenAiTextApiMode.RESPONSES,
        ProviderApiProtocol.ANTHROPIC to OpenAiTextApiMode.CHAT_COMPLETIONS,
    )

    @Test
    fun retryAndRerollAcrossThreeProtocols_useDifferentSnapshotPolicies() = runBlocking<Unit> {
        for ((api, mode) in protocols) {
            protocol(mode, api)
            val state = writingState()
            val store = stateStore(state)
            val coordinator =
                requests(settingsStore = store, gateway = gateway(settingsStore = store))
            val context = scene()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val prepared =
                coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "用户明确只坐一会儿")
            val originalSnapshot = requireNotNull(prepared.attempt.descriptor.presentation)
            server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic failure"))
            assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Failed)
            assertTrue(payload().contains("旧补充哨兵"))

            val old = state.value.companionSettings("alice")
            state.value = state.value.copy(
                companionCharacterSettings = mapOf(
                    "alice" to old.copy(
                        revision = 4,
                        writing = old.writing.copy(
                            style = CompanionWritingStyle.PLAIN,
                            pointOfView = CompanionPointOfView.THIRD_NAME,
                            extraStyle = "新补充哨兵",
                            observation = old.writing.observation.copy(
                                customFields = listOf(
                                    CompanionObservationField("obs_coat", "新名称哨兵")
                                )
                            ),
                        ),
                    )
                )
            )
            val retry = requireNotNull(
                coordinator.newAttempt(
                    prepared.turn.id,
                    true,
                    CompanionAttemptIntent.RETRY
                )
            )
            assertEquals(4L, retry.descriptor.settingsRevision)
            assertEquals(originalSnapshot, retry.descriptor.presentation)
            reply(observedReply, mode, api)
            val saved = coordinator.generate(retry.id) as CompanionGenerationResult.Saved
            val retryBody = payload()
            assertTrue(retryBody.contains("旧补充哨兵"))
            assertTrue(retryBody.contains("旧名称哨兵"))
            assertFalse(retryBody.contains("新补充哨兵"))
            assertFalse(retryBody.contains("新名称哨兵"))
            assertTrue(retryBody.contains(requireNotNull(originalSnapshot.detailHint).text))
            assertTrue(retryBody.contains("甲角色事实哨兵"))
            assertEquals(
                "旧名称哨兵",
                CompanionReplyProjector.project(saved.receipt.replyMessage)!!.observation!!.fields.single().label
            )

            val reroll = requireNotNull(
                coordinator.newAttempt(
                    prepared.turn.id,
                    true,
                    CompanionAttemptIntent.REGENERATE
                )
            )
            assertEquals(4L, reroll.descriptor.presentation!!.sourceSettingsRevision)
            assertEquals(CompanionWritingStyle.PLAIN, reroll.descriptor.presentation!!.style)
            reply(observedReply, mode, api)
            val replaced = coordinator.generate(reroll.id) as CompanionGenerationResult.Saved
            val rerollBody = payload()
            assertTrue(rerollBody.contains("新补充哨兵"))
            assertTrue(rerollBody.contains("新名称哨兵"))
            assertFalse(rerollBody.contains("旧补充哨兵"))
            assertEquals(saved.receipt.replyMessage.id, replaced.receipt.replyMessage.id)
            assertEquals(
                saved.receipt.replyMessage.createdAt,
                replaced.receipt.replyMessage.createdAt
            )
            assertEquals(
                "新名称哨兵",
                CompanionReplyProjector.project(replaced.receipt.replyMessage)!!.observation!!.fields.single().label
            )
            assertEquals(
                1,
                conversations.listMessages(context.conversationId)
                    .count { it.role == MessageRole.USER })
            repository.endEncounter(encounter.id)
        }
        assertEquals(9, server.requestCount)
    }

    @Test
    fun retriedOldPresentationStillUsesNewLiveGuard_atLastHttpBoundary() = runBlocking<Unit> {
        for ((api, mode) in protocols) {
            protocol(mode, api)
            val state = writingState()
            val store = stateStore(state)
            var armed = false
            var visited = false
            val mutating = object : MemoryArchiveRepository by archive {
                override suspend fun prepareRecallTransmission(
                    requestId: String, payloadFingerprint: String,
                    materials: List<MemoryMaterialRef>, omitted: List<MemoryRecallOmission>,
                ): Int? {
                    val sequence = archive.prepareRecallTransmission(
                        requestId,
                        payloadFingerprint,
                        materials,
                        omitted
                    )
                    if (armed) {
                        visited = true
                        val current = state.value.companionSettings("alice")
                        state.value = state.value.copy(
                            companionCharacterSettings = mapOf(
                                "alice" to current.copy(revision = current.revision + 1)
                            )
                        )
                    }
                    return sequence
                }
            }
            val coordinator = requests(
                settingsStore = store,
                gateway = gateway(settingsStore = store, archiveRepository = mutating)
            )
            val context = scene()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "输入")
            server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic failure"))
            assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Failed)
            payload()
            val current = state.value.companionSettings("alice")
            state.value =
                state.value.copy(companionCharacterSettings = mapOf("alice" to current.copy(revision = 4)))
            val retry = requireNotNull(
                coordinator.newAttempt(
                    prepared.turn.id,
                    true,
                    CompanionAttemptIntent.RETRY
                )
            )
            assertEquals(3L, retry.descriptor.presentation!!.sourceSettingsRevision)
            assertEquals(4L, retry.descriptor.settingsRevision)
            armed = true
            val before = server.requestCount
            assertFalse(coordinator.generate(retry.id) is CompanionGenerationResult.Saved)
            assertTrue(visited)
            assertEquals(before, server.requestCount)
            repository.endEncounter(encounter.id)
        }
    }

    @Test
    fun fiveStylesAndDefault_preserveRoleFactsAndUseOnlyRealInputForWorldBook() =
        runBlocking<Unit> {
            worldBook.upsertEntry(
                WorldBookEntry(
                    id = "real",
                    keywords = listOf("橡树"),
                    content = "真实输入触发的世界书哨兵"
                )
            )
            worldBook.upsertEntry(
                WorldBookEntry(
                    id = "style-only",
                    keywords = listOf("STYLE_ONLY_TRIGGER"),
                    content = "不得由文风触发的世界书哨兵"
                )
            )
            val state = writingState()
            val store = stateStore(state)
            val coordinator =
                requests(settingsStore = store, gateway = gateway(settingsStore = store))
            val context = scene()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val styles = listOf<CompanionWritingStyle?>(null) + CompanionWritingStyle.entries
            for ((index, style) in styles.withIndex()) {
                state.value = state.value.copy(
                    companionCharacterSettings = mapOf(
                        "alice" to CompanionCharacterSettings(
                            revision = index.toLong(),
                            writing = CompanionWritingSettings(
                                style = style,
                                extraStyle = "STYLE_ONLY_TRIGGER",
                                detailHintsEnabled = false
                            ),
                        )
                    )
                )
                val prepared =
                    coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "橡树还在吗？")
                reply()
                assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
                val body = payload()
                if (style != null) assertTrue(body.contains("文风：${style.label}"))
                else assertFalse(body.contains("文风："))
                assertTrue(body.contains("甲角色事实哨兵"))
                assertTrue(body.contains("真实输入触发的世界书哨兵"))
                assertFalse(body.contains("不得由文风触发的世界书哨兵"))
                assertFalse(body.contains("本轮可选细节线索"))
            }
            assertEquals(6, server.requestCount)
        }

    @Test
    fun sameStyleKeepsTwoCharactersDifferentAndDoesNotRewriteHistory() = runBlocking<Unit> {
        val state = writingState()
        state.value = state.value.copy(
            companionCharacterSettings = mapOf(
                "alice" to state.value.companionSettings("alice"),
                "bob" to state.value.companionSettings("alice"),
            )
        )
        val store = stateStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        for (character in listOf("alice", "bob")) {
            val context = scene(character = character)
            val original = add(context, "用户原文-$character")
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "你好")
            reply()
            assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
            val body = payload()
            assertTrue(body.contains(if (character == "alice") "甲角色事实哨兵" else "乙角色事实哨兵"))
            assertFalse(body.contains(if (character == "alice") "乙角色事实哨兵" else "甲角色事实哨兵"))
            assertEquals(
                original,
                conversations.listMessages(context.conversationId).first { it.id == original.id })
            repository.endEncounter(encounter.id)
        }
    }

    @Test
    fun pointOfViewOnlyAffectsNarration_andOpeningKeepsThirdPerson() = runBlocking<Unit> {
        val state = writingState()
        val store = stateStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        val context = scene()
        for (pointOfView in CompanionPointOfView.entries) {
            val current = state.value.companionSettings("alice")
            state.value = state.value.copy(
                companionCharacterSettings = mapOf(
                    "alice" to current.copy(
                        revision = current.revision + 1,
                        writing = current.writing.copy(pointOfView = pointOfView)
                    )
                )
            )
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
            reply()
            assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
            val body = payload()
            assertTrue(body.contains("叙述人称："))
            assertTrue(body.contains("台词中的我/你按正常说话保留"))
            assertTrue(body.contains("本轮用户没有新增言行"))
            repository.endEncounter(encounter.id)
        }
        for (mode in listOf(CompanionOpeningMode.APPROACH, CompanionOpeningMode.INVITE)) {
            val entry = roleplay.prepareCompanionScenario(context.scenarioId, "opening-$mode")
            val selected =
                roleplay.selectCompanionOpening(entry.encounter.id, entry.encounter.id, mode)
            val turn = repository.listTurns(selected.encounter.id).single()
            val attempt = requireNotNull(coordinator.newAttempt(turn.id))
            reply()
            assertTrue(coordinator.generate(attempt.id) is CompanionGenerationResult.Generated)
            val body = payload()
            assertTrue(body.contains("开场候选保持第三人称"))
            assertFalse(body.contains("叙述人称：角色以我叙述"))
            repository.endEncounter(selected.encounter.id)
        }
    }

    @Test
    fun snapshotSeedIsReproducibleAndDoesNotMutateConfiguration() {
        val settings = CompanionCharacterSettings(revision = 3, writing = writing())
        val first = CompanionPresentationSupport.freeze(settings, "stable request")
        assertEquals(first, CompanionPresentationSupport.freeze(settings, "stable request"))
        assertNotNull(first.detailHint)
        assertEquals(3L, settings.revision)
        assertEquals("旧补充哨兵", settings.writing.extraStyle)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun viewModelRecoverAndRegenerate_dispatchTheirOwnSnapshotIntent() = runBlocking<Unit> {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val state = writingState()
        val store = stateStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        val viewModel =
            RoleplayCompanionViewModel(SavedStateHandle(), roleplay, repository, coordinator, store)
        try {
            val context = scene()
            viewModel.open(context.scenarioId, "viewmodel-entry")
            settle(viewModel)
            viewModel.chooseOpening(CompanionOpeningMode.DIRECT)
            settle(viewModel)
            assertEquals(CompanionEntryPhase.ACTIVE, viewModel.uiState.value.phase)
            server.enqueue(MockResponse().setResponseCode(500).setBody("synthetic failure"))
            viewModel.send("先坐一会儿")
            settle(viewModel)
            payload()
            assertEquals(CompanionRecoveryAction.GENERATE, viewModel.uiState.value.recoveryAction)
            val original = state.value.companionSettings("alice")
            state.value = state.value.copy(
                companionCharacterSettings = mapOf(
                    "alice" to original.copy(
                        revision = 4, writing = original.writing.copy(extraStyle = "新补充哨兵"),
                    )
                )
            )
            reply()
            viewModel.recover()
            settle(viewModel)
            val retried = payload()
            assertTrue(retried.contains("旧补充哨兵"))
            assertFalse(retried.contains("新补充哨兵"))
            assertEquals(CompanionRecoveryAction.NONE, viewModel.uiState.value.recoveryAction)
            reply()
            viewModel.regenerate()
            settle(viewModel)
            val rerolled = payload()
            assertTrue(rerolled.contains("新补充哨兵"))
            assertFalse(rerolled.contains("旧补充哨兵"))
            assertEquals(3, server.requestCount)
        } finally {
            viewModel.cancel()
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun observationOnlyHttpResult_remainsIncompleteAndCannotBeAccepted() = runBlocking<Unit> {
        val state = writingState()
        val store = stateStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "only-observe")
        val attempt = requireNotNull(coordinator.newAttempt(requireNotNull(entry.opening).turn.id))
        reply("<observe version=\"1\"><field id=\"time\">傍晚</field></observe>")
        val result = coordinator.generate(attempt.id) as CompanionGenerationResult.Generated
        payload()
        assertEquals(PromptCompletionStatus.INCOMPLETE, result.completionStatus)
        assertTrue(coordinator.acceptOpening(attempt.id) is CompanionGenerationResult.Rejected)
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
    }

    @Test
    fun overflowingStoredVersions_neverSendAsSupportedProtocol() = runBlocking<Unit> {
        for (field in listOf("protocolVersion", "snapshotVersion")) {
            val context = scene()
            val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
            val coordinator = requests()
            val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "原文保留")
            val json =
                JsonParser.parseString(AppJson.gson.toJson(prepared.attempt.descriptor)).asJsonObject
            if (field == "protocolVersion") json.addProperty(field, 4294967297L)
            else json["presentation"].asJsonObject.addProperty(field, 4294967297L)
            val raw = AppJson.gson.toJson(json)
            database.openHelper.writableDatabase.execSQL(
                "UPDATE companion_attempts SET requestDescriptorJson = ?, requestFingerprint = ? WHERE id = ?",
                arrayOf(raw, MemoryArchiveIdentity.fingerprint(raw), prepared.attempt.id),
            )
            assertFalse(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
            assertEquals(0, server.requestCount)
            assertTrue(runCatching {
                coordinator.newAttempt(
                    prepared.turn.id,
                    true,
                    CompanionAttemptIntent.RETRY
                )
            }.exceptionOrNull() is IllegalArgumentException)
            repository.endEncounter(encounter.id)
        }
    }

    private fun writing() = CompanionWritingSettings(
        style = CompanionWritingStyle.LITERARY, pointOfView = CompanionPointOfView.FIRST_YOU,
        extraStyle = "旧补充哨兵", detailHintsEnabled = true,
        observation = CompanionObservationSettings(
            enabled = true,
            customFields = listOf(CompanionObservationField("obs_coat", "旧名称哨兵"))
        ),
    )

    private suspend fun writingState() = MutableStateFlow(
        settings.settingsFlow.first().copy(
            companionCharacterSettings = mapOf(
                "alice" to CompanionCharacterSettings(
                    revision = 3,
                    writing = writing()
                )
            ),
        )
    )

    private fun stateStore(state: MutableStateFlow<AppSettings>) =
        object : SettingsStore by settings {
            override val settingsFlow = state
        }

    private suspend fun settle(viewModel: RoleplayCompanionViewModel) {
        withTimeout(10_000) { viewModel.uiState.first { !it.isBusy } }
    }

    companion object {
        private const val observedReply =
            "<observe version=\"1\"><field id=\"obs_coat\">蓝色外套</field></observe><dialogue>好，就坐一会儿。</dialogue>"
    }
}
