package com.example.myapplication.data.repository.roleplay.script

import com.example.myapplication.data.local.roleplay.script.RoleplayScriptDao
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptEntity
import com.example.myapplication.data.local.roleplay.script.RoleplayScriptStateEntity
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayScriptRepositoryTest {
    @Test
    fun upsertScript_roundTripsScopeOwnerAndPermissionStorageValues() = runTest {
        val dao = FakeRoleplayScriptDao()
        val repository = RoomRoleplayScriptRepository(
            scriptDao = dao,
            nowProvider = { 100L },
        )

        repository.upsertScript(
            RoleplayScriptDefinition(
                id = " script-1 ",
                name = " ",
                scope = RoleplayScriptScope.CHARACTER,
                ownerId = " character-1 ",
                source = "function beforePrompt() {}",
                grantedPermissions = setOf(
                    RoleplayScriptPermission.WRITE_VARIABLES,
                    RoleplayScriptPermission.READ_FILE,
                ),
            ),
        )

        val loaded = repository.getScript("script-1")

        assertEquals("script-1", loaded?.id)
        assertEquals("未命名脚本", loaded?.name)
        assertEquals(RoleplayScriptScope.CHARACTER, loaded?.scope)
        assertEquals("character-1", loaded?.ownerId)
        assertEquals(
            setOf(RoleplayScriptPermission.WRITE_VARIABLES, RoleplayScriptPermission.READ_FILE),
            loaded?.grantedPermissions,
        )
        assertEquals("""["write_variables","read_file"]""", dao.rawScripts.single().grantedPermissionsJson)
        assertEquals(100L, loaded?.updatedAt)
    }

    @Test
    fun upsertScript_clearsOwnerIdForGlobalScript() = runTest {
        val dao = FakeRoleplayScriptDao()
        val repository = RoomRoleplayScriptRepository(
            scriptDao = dao,
            nowProvider = { 200L },
        )

        repository.upsertScript(
            RoleplayScriptDefinition(
                id = "global-1",
                name = "全局",
                scope = RoleplayScriptScope.GLOBAL,
                ownerId = "should-drop",
                source = "function beforePrompt() {}",
            ),
        )

        assertEquals("", repository.getScript("global-1")?.ownerId)
        assertEquals("", dao.rawScripts.single().ownerId)
    }

    @Test
    fun listRuntimeScripts_filtersByScopeOwnerAndExecutableRules() = runTest {
        val dao = FakeRoleplayScriptDao(
            scripts = listOf(
                scriptEntity("session", RoleplayScriptScope.SESSION, "session-1", updatedAt = 4L),
                scriptEntity("wrong-session", RoleplayScriptScope.SESSION, "session-2", updatedAt = 1L),
                scriptEntity("scenario", RoleplayScriptScope.SCENARIO, "scenario-1", updatedAt = 3L),
                scriptEntity("character", RoleplayScriptScope.CHARACTER, "character-1", updatedAt = 2L),
                scriptEntity("wrong-character", RoleplayScriptScope.CHARACTER, "character-2", updatedAt = 1L),
                scriptEntity("global", RoleplayScriptScope.GLOBAL, "", updatedAt = 1L),
                scriptEntity("disabled", RoleplayScriptScope.GLOBAL, "", enabled = false),
                scriptEntity("blank", RoleplayScriptScope.GLOBAL, "", source = " "),
            ),
        )
        val repository = RoomRoleplayScriptRepository(dao)

        val runtimeScripts = repository.listRuntimeScripts(
            characterId = "character-1",
            scenarioId = "scenario-1",
            sessionId = "session-1",
        )

        assertEquals(listOf("global", "character", "scenario", "session"), runtimeScripts.map { it.id })
    }

    @Test
    fun writeState_trimsKeysAndSupportsDelete() = runTest {
        val dao = FakeRoleplayScriptDao()
        val repository = RoomRoleplayScriptRepository(
            scriptDao = dao,
            nowProvider = { 300L },
        )

        repository.writeState(
            scriptId = " script-1 ",
            values = mapOf(
                " mood " to "warm",
                "" to "ignored",
                "energy" to "",
            ),
        )
        repository.deleteStateValue("script-1", " energy ")

        assertEquals(mapOf("mood" to "warm"), repository.readState("script-1"))
        assertTrue(dao.rawStates.all { it.updatedAt == 300L })
    }

    private fun scriptEntity(
        id: String,
        scope: RoleplayScriptScope,
        ownerId: String,
        source: String = "function beforePrompt() {}",
        enabled: Boolean = true,
        updatedAt: Long = 0L,
    ): RoleplayScriptEntity {
        return RoleplayScriptEntity(
            id = id,
            name = id,
            scope = scope.storageValue,
            ownerId = ownerId,
            source = source,
            enabled = enabled,
            grantedPermissionsJson = "[]",
            createdAt = updatedAt,
            updatedAt = updatedAt,
        )
    }
}

private class FakeRoleplayScriptDao(
    scripts: List<RoleplayScriptEntity> = emptyList(),
    states: List<RoleplayScriptStateEntity> = emptyList(),
) : RoleplayScriptDao {
    private val scriptsState = MutableStateFlow(scripts)
    private val statesState = MutableStateFlow(states)

    val rawScripts: List<RoleplayScriptEntity>
        get() = scriptsState.value

    val rawStates: List<RoleplayScriptStateEntity>
        get() = statesState.value

    override fun observeScripts(): Flow<List<RoleplayScriptEntity>> {
        return scriptsState.map(::sortScripts)
    }

    override suspend fun listScripts(): List<RoleplayScriptEntity> {
        return sortScripts(scriptsState.value)
    }

    override suspend fun listScriptsByOwner(
        scope: String,
        ownerId: String,
    ): List<RoleplayScriptEntity> {
        return scriptsState.value
            .filter { script -> script.scope == scope && script.ownerId == ownerId }
            .sortedWith(
                compareByDescending<RoleplayScriptEntity> { it.updatedAt }
                    .thenBy { it.name.lowercase() },
            )
    }

    override suspend fun getScript(scriptId: String): RoleplayScriptEntity? {
        return scriptsState.value.firstOrNull { script -> script.id == scriptId }
    }

    override suspend fun upsertScript(script: RoleplayScriptEntity) {
        scriptsState.value = scriptsState.value.filterNot { it.id == script.id } + script
    }

    override suspend fun deleteScript(scriptId: String) {
        scriptsState.value = scriptsState.value.filterNot { script -> script.id == scriptId }
        statesState.value = statesState.value.filterNot { state -> state.scriptId == scriptId }
    }

    override suspend fun listState(scriptId: String): List<RoleplayScriptStateEntity> {
        return statesState.value
            .filter { state -> state.scriptId == scriptId }
            .sortedBy { state -> state.stateKey }
    }

    override suspend fun upsertStateValues(values: List<RoleplayScriptStateEntity>) {
        val targetKeys = values.map { state -> state.scriptId to state.stateKey }.toSet()
        statesState.value = statesState.value
            .filterNot { state -> state.scriptId to state.stateKey in targetKeys } + values
    }

    override suspend fun deleteStateValue(scriptId: String, stateKey: String) {
        statesState.value = statesState.value.filterNot { state ->
            state.scriptId == scriptId && state.stateKey == stateKey
        }
    }

    override suspend fun deleteState(scriptId: String) {
        statesState.value = statesState.value.filterNot { state -> state.scriptId == scriptId }
    }

    private fun sortScripts(scripts: List<RoleplayScriptEntity>): List<RoleplayScriptEntity> {
        return scripts.sortedWith(
            compareBy<RoleplayScriptEntity> { it.scope }
                .thenBy { it.ownerId }
                .thenByDescending { it.updatedAt }
                .thenBy { it.name.lowercase() },
        )
    }
}
