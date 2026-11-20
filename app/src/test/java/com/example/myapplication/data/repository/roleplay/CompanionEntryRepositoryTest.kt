package com.example.myapplication.data.repository.roleplay

import android.app.Application
import com.example.myapplication.model.*
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.testutil.CompanionEntryTestFixture
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CompanionEntryRepositoryTest : CompanionEntryTestFixture() {
    @Test
    fun concurrentSelection_usesPersistedIdAndRejectsStaleChoice() = runBlocking<Unit> {
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "z-entry")
        clock -= 100
        val choices = listOf("z-direct", "a-direct").map { id ->
            async {
                runCatching {
                    roleplay.selectCompanionOpening(
                        entry.encounter.id,
                        entry.encounter.id,
                        CompanionOpeningMode.DIRECT,
                        id
                    )
                }
            }
        }.awaitAll()
        assertEquals(1, choices.count { it.isSuccess })
        assertEquals(1, choices.count { it.isFailure })
        val selected = choices.single { it.isSuccess }.getOrThrow()
        assertEquals(CompanionEncounterState.ACTIVE, selected.encounter.state)
        assertTrue(repository.listTurns(selected.encounter.id).isEmpty())
        assertEquals(selected, roleplay.resumeCompanionScenario(context.scenarioId))
        assertEquals(
            selected.encounter.id,
            roleplay.prepareCompanionScenario(context.scenarioId, "z-entry").encounter.id
        )
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun selectionStorageFailure_rollsBackSupersedeAndMarkerTogether() = runBlocking<Unit> {
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        sql("CREATE TRIGGER fail_entry_selection BEFORE UPDATE OF requestDescriptorJson ON companion_attempts BEGIN SELECT RAISE(ABORT, 'synthetic'); END")
        val result = runCatching {
            roleplay.selectCompanionOpening(
                "entry",
                "entry",
                CompanionOpeningMode.DIRECT,
                "direct"
            )
        }
        assertTrue(result.isFailure)
        sql("DROP TRIGGER fail_entry_selection")
        assertNull(repository.getEncounter("direct"))
        assertEquals(
            CompanionEncounterState.DRAFT,
            repository.getEncounter(entry.encounter.id)?.state
        )
        assertEquals(
            "entry",
            roleplay.prepareCompanionScenario(context.scenarioId, "entry").encounter.id
        )
        assertTrue(conversations.listMessages(context.conversationId).isEmpty())
    }

    @Test
    fun newerSelection_keepsOnlineOriginAndStaleReturnCannotChangeIt() = runBlocking<Unit> {
        val context = scene(mode = RoleplayInteractionMode.ONLINE_PHONE)
        val originalSession = roleplay.getSessionByScenario(context.scenarioId)!!
        val first = roleplay.prepareCompanionScenario(context.scenarioId, "z-entry")
        val direct = roleplay.selectCompanionOpening(
            "z-entry",
            "z-entry",
            CompanionOpeningMode.DIRECT,
            "z-direct"
        )
        clock -= 100
        val next = roleplay.prepareCompanionScenario(context.scenarioId, "a-entry")
        assertEquals(RoleplayInteractionMode.ONLINE_PHONE, next.origin.interactionMode)
        assertFalse(roleplay.restoreCompanionOrigin(first.origin, direct.encounter.id))
        val selected = roleplay.selectCompanionOpening(
            "a-entry",
            "a-entry",
            CompanionOpeningMode.DIRECT,
            "a-direct"
        )
        assertFalse(roleplay.restoreCompanionOrigin(first.origin, direct.encounter.id))
        assertEquals(
            CompanionEncounterState.ACTIVE,
            repository.getEncounter(selected.encounter.id)?.state
        )
        assertTrue(roleplay.restoreCompanionOrigin(next.origin, selected.encounter.id))
        val restoredSession = roleplay.getSessionByScenario(context.scenarioId)!!
        assertEquals(originalSession.id, restoredSession.id)
        assertEquals(originalSession.conversationId, restoredSession.conversationId)
        assertEquals(originalSession.runId, restoredSession.runId)
        assertEquals(
            RoleplayInteractionMode.ONLINE_PHONE,
            roleplay.getScenario(context.scenarioId)?.interactionMode
        )
        assertEquals(
            context,
            archive.getRuntimeContext(context.conversationId, context.characterId)
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun foreignSelectionMarker_isRejectedWithoutSwitchingModes() = runBlocking<Unit> {
        val context = scene()
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        val foreign = scene(character = "bob")
        val foreignEncounter =
            repository.createEncounter(foreign, CompanionOpeningMode.DIRECT, "foreign")
        val marker = database.companionDao().getAttempt(requireNotNull(entry.opening).attempt.id)!!
        val descriptor =
            requireNotNull(entry.opening).attempt.descriptor.copy(entrySelectionId = foreignEncounter.id)
        database.companionDao()
            .upsertAttempt(marker.copy(requestDescriptorJson = AppJson.gson.toJson(descriptor)))
        assertTrue(runCatching {
            roleplay.prepareCompanionScenario(
                context.scenarioId,
                "entry"
            )
        }.isFailure)
        assertTrue(runCatching {
            roleplay.selectCompanionOpening(
                "entry",
                "entry",
                CompanionOpeningMode.DIRECT
            )
        }.isFailure)
        assertFalse(roleplay.restoreCompanionOrigin(entry.origin, foreignEncounter.id))
        assertEquals(
            CompanionEncounterState.ACTIVE,
            repository.getEncounter(foreignEncounter.id)?.state
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun oldSession_keepsGreetingAndRunWithoutInventingEncounter() = runBlocking<Unit> {
        val context = scene(greeting = "已存在的旧开场")
        val before = conversations.listMessages(context.conversationId)
        assertTrue(before.any { it.content.contains("已存在的旧开场") })
        assertNull(roleplay.resumeCompanionScenario(context.scenarioId))
        roleplay.startScenario(context.scenarioId)
        assertTrue(repository.listEncounters(context.conversationId).isEmpty())
        assertEquals(before, conversations.listMessages(context.conversationId))
        val entry = roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        roleplay.selectCompanionOpening("entry", "entry", CompanionOpeningMode.DIRECT)
        assertEquals(before, conversations.listMessages(context.conversationId))
        assertEquals(context.runId, entry.session.runId)
    }

    @Test
    fun deleteScenario_cleansItsPreparationMarkersAndKeepsAnotherScenario() = runBlocking<Unit> {
        val context = scene()
        roleplay.prepareCompanionScenario(context.scenarioId, "entry")
        roleplay.selectCompanionOpening("entry", "entry", CompanionOpeningMode.DIRECT, "direct")
        val other = scene(character = "bob")
        roleplay.prepareCompanionScenario(other.scenarioId, "other")
        roleplay.deleteScenario(context.scenarioId)
        assertTrue(database.companionDao().listEncounters(context.conversationId).isEmpty())
        assertNull(database.companionDao().getTurn("entry:opening"))
        assertTrue(database.companionDao().listAttempts("entry:opening").isEmpty())
        assertNotNull(repository.getEncounter("other"))
    }
}
