package com.example.myapplication.conversation

import android.app.Application
import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.repository.context.MemoryArchiveRepository
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.RoleplayTimeAwarenessSupport
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionEntryTestFixture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionTimePolicyTest : CompanionEntryTestFixture() {
    @Test
    fun frozenCharacterZone_crossesNewYearWithoutChangingOriginalTime() = runBlocking<Unit> {
        clock = Instant.parse("2026-12-31T23:50:00Z").toEpochMilli()
        val context = scene()
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(enableTimeAwareness = true)
        )
        val original = add(context, "叙事日期仍是 1999 年的冬夜")
        val state = timedSettings("Asia/Tokyo")
        val store = settingsStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val prepared =
            coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "现在能坐一会儿吗？")
        val policy = requireNotNull(prepared.attempt.descriptor.timePolicy)
        assertEquals("Asia/Tokyo", policy.zoneId)
        clock += 2 * 60 * 60 * 1_000L
        reply()
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        assertTrue(
            body.contains(
                RoleplayTimeAwarenessSupport.formatCurrentPromptTime(
                    policy.requestTime,
                    ZoneId.of("Asia/Tokyo")
                )
            )
        )
        assertTrue(body.contains("2027年1月1日"))
        assertTrue(body.contains("08:50"))
        assertFalse(body.contains("10:50"))
        assertTrue(body.contains(original.content))
        assertEquals(
            original.createdAt,
            conversations.listMessages(context.conversationId)
                .first { it.id == original.id }.createdAt
        )
    }

    @Test
    fun disabledTime_keepsNarrativeDatesAndSuppressesRealTimeWrapping() = runBlocking<Unit> {
        val context = scene()
        val original = add(context, "故事原文：1999年12月31日，等最后一场雪。")
        val state = timedSettings("Pacific/Kiritimati")
        val store = settingsStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val prepared = coordinator.beginTurn(encounter.id, CompanionTurnKind.CONTINUE)
        assertFalse(requireNotNull(prepared.attempt.descriptor.timePolicy).enabled)
        reply()
        assertTrue(coordinator.generate(prepared.attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        assertTrue(body.contains(original.content))
        assertFalse(body.contains("当前绝对时间"))
        assertFalse(body.contains("【时间感知】"))
        assertFalse(body.contains("【时间旁白】"))
        assertEquals(
            original.createdAt,
            conversations.listMessages(context.conversationId)
                .first { it.id == original.id }.createdAt
        )
    }

    @Test
    fun zoneChangedDuringPayloadPreparation_rejectsBeforeHttp() = runBlocking<Unit> {
        val context = scene()
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(enableTimeAwareness = true)
        )
        val state = timedSettings("UTC")
        val store = settingsStore(state)
        var prepared = false
        val mutating = object : MemoryArchiveRepository by archive {
            override suspend fun prepareRecallTransmission(
                requestId: String, payloadFingerprint: String,
                materials: List<MemoryMaterialRef>, omitted: List<MemoryRecallOmission>
            ): Int? {
                val sequence = archive.prepareRecallTransmission(
                    requestId,
                    payloadFingerprint,
                    materials,
                    omitted
                )
                state.value = state.value.copy(
                    companionCharacterSettings = mapOf(
                        "alice" to CompanionCharacterSettings(
                            revision = 8,
                            zoneId = "Europe/Paris"
                        )
                    )
                )
                prepared = true
                return sequence
            }
        }
        val coordinator = requests(
            settingsStore = store,
            gateway = gateway(archiveRepository = mutating, settingsStore = store)
        )
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "这次输入")
        reply()
        assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Failed)
        assertTrue(prepared)
        assertEquals(0, server.requestCount)
        assertEquals("UTC", turn.attempt.descriptor.timePolicy?.zoneId)
        assertEquals(7L, turn.attempt.descriptor.settingsRevision)
        val receipt = requireNotNull(archive.getRecallReceipt(context, turn.attempt.id))
        assertEquals("SOURCE_CHANGED", receipt.failureCode)
        assertEquals(MemoryRecallStatus.PREPARED, receipt.transmissions.single().status)
    }

    @Test
    fun zoneChangedAfterHttp_keepsActualFrozenSnapshotForSavedReply() = runBlocking<Unit> {
        val context = scene()
        roleplay.upsertScenario(
            roleplay.getScenario(context.scenarioId)!!.copy(enableTimeAwareness = true)
        )
        val state = timedSettings("Asia/Shanghai")
        val store = settingsStore(state)
        val coordinator = requests(settingsStore = store, gateway = gateway(settingsStore = store))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                state.value = state.value.copy(
                    companionCharacterSettings = mapOf(
                        "alice" to CompanionCharacterSettings(
                            revision = 8,
                            zoneId = "America/New_York"
                        )
                    )
                )
                return MockResponse().setHeader("Content-Type", "application/json").setBody(
                    AppJson.gson.toJson(
                        mapOf(
                            "choices" to listOf(
                                mapOf(
                                    "message" to mapOf(
                                        "role" to "assistant",
                                        "content" to "<dialogue>好。</dialogue>"
                                    ), "finish_reason" to "stop"
                                )
                            )
                        )
                    )
                )
            }
        }
        val encounter = repository.createEncounter(context, CompanionOpeningMode.DIRECT)
        val turn = coordinator.beginTurn(encounter.id, CompanionTurnKind.INPUT, "能坐一会儿吗？")
        assertTrue(coordinator.generate(turn.attempt.id) is CompanionGenerationResult.Saved)
        val body = payload()
        val saved = requireNotNull(repository.getAttempt(turn.attempt.id))
        assertEquals(7L, saved.descriptor.settingsRevision)
        assertEquals("Asia/Shanghai", saved.descriptor.timePolicy?.zoneId)
        assertEquals(8L, state.value.companionSettings("alice").revision)
        val policy = requireNotNull(saved.descriptor.timePolicy)
        assertTrue(
            body.contains(
                RoleplayTimeAwarenessSupport.formatCurrentPromptTime(
                    policy.requestTime,
                    ZoneId.of("Asia/Shanghai")
                )
            )
        )
        assertEquals(1, server.requestCount)
    }

    private suspend fun timedSettings(zone: String) = MutableStateFlow(
        settings.settingsFlow.first().copy(
            companionCharacterSettings = mapOf(
                "alice" to CompanionCharacterSettings(
                    revision = 7,
                    zoneId = zone
                )
            )
        )
    )

    private fun settingsStore(state: MutableStateFlow<AppSettings>) =
        object : SettingsStore by settings {
            override val settingsFlow = state
        }
}
