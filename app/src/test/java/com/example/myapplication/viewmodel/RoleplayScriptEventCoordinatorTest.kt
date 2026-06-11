package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEvent
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionRequest
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionResult
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.roleplay.script.RoleplayScriptUiDirective
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleplayScriptEventCoordinatorTest {
    @Test
    fun execute_loadsVariablesAndPersistsUpdatesForOwningScript() = runTest {
        val script = script("script-1")
        val repository = FakeRoleplayScriptRepository(
            runtimeScripts = listOf(script),
            stateByScriptId = mutableMapOf("script-1" to mapOf("mood" to "quiet")),
        )
        val engine = CapturingScriptEngine { request ->
            assertEquals(mapOf("mood" to "quiet"), request.input.variables)
            assertEquals(RoleplayScriptEvent.BEFORE_PROMPT, request.event)
            assertEquals("session-1", request.input.sessionId)
            assertEquals("scenario-1", request.input.scenarioId)
            assertEquals("character-1", request.input.characterId)
            RoleplayScriptExecutionResult(
                variables = mapOf("mood" to "warm"),
                variableUpdatesByScriptId = mapOf("script-1" to mapOf("mood" to "warm")),
                promptAdditions = listOf("保持暧昧张力。"),
            )
        }
        val coordinator = RoleplayScriptEventCoordinator(
            scriptRepository = repository,
            scriptEngine = engine,
        )

        val result = coordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.BEFORE_PROMPT,
                sessionId = "session-1",
                scenarioId = "scenario-1",
                characterId = "character-1",
                userText = "你在想什么？",
                promptText = "基础提示词",
            ),
        )

        assertEquals(listOf(script), engine.lastRequest?.scripts)
        assertEquals(listOf("保持暧昧张力。"), result.promptAdditions)
        assertEquals(mapOf("mood" to "warm"), repository.stateByScriptId["script-1"])
    }

    @Test
    fun execute_ignoresRepositoryFailureAndKeepsMainFlowEmpty() = runTest {
        val repository = FakeRoleplayScriptRepository(
            runtimeScripts = emptyList(),
            failListRuntimeScripts = true,
        )
        val engine = CapturingScriptEngine {
            error("没有脚本时不应调用引擎")
        }
        val coordinator = RoleplayScriptEventCoordinator(
            scriptRepository = repository,
            scriptEngine = engine,
        )

        val result = coordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.BEFORE_PROMPT,
                sessionId = "session-1",
                scenarioId = "scenario-1",
                characterId = "character-1",
            ),
        )

        assertEquals(RoleplayScriptExecutionResult(), result)
        assertEquals(null, engine.lastRequest)
    }

    @Test
    fun execute_afterAssistantPassesAssistantTextToEngine() = runTest {
        val repository = FakeRoleplayScriptRepository(
            runtimeScripts = listOf(script("script-1")),
        )
        val engine = CapturingScriptEngine { request ->
            assertEquals(RoleplayScriptEvent.AFTER_ASSISTANT, request.event)
            assertEquals("角色回复文本", request.input.assistantText)
            assertEquals("用户输入", request.input.userText)
            RoleplayScriptExecutionResult()
        }
        val coordinator = RoleplayScriptEventCoordinator(
            scriptRepository = repository,
            scriptEngine = engine,
        )

        coordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.AFTER_ASSISTANT,
                sessionId = "session-1",
                scenarioId = "scenario-1",
                characterId = "character-1",
                userText = "用户输入",
                assistantText = "角色回复文本",
            ),
        )

        assertEquals("script-1", engine.lastRequest?.scripts?.singleOrNull()?.id)
    }

    @Test
    fun execute_onSessionStartPersistsInitialVariables() = runTest {
        val repository = FakeRoleplayScriptRepository(
            runtimeScripts = listOf(script("script-1")),
        )
        val engine = CapturingScriptEngine { request ->
            assertEquals(RoleplayScriptEvent.ON_SESSION_START, request.event)
            assertEquals("session-1", request.input.sessionId)
            RoleplayScriptExecutionResult(
                variables = mapOf("entered" to "true"),
                variableUpdatesByScriptId = mapOf("script-1" to mapOf("entered" to "true")),
            )
        }
        val coordinator = RoleplayScriptEventCoordinator(
            scriptRepository = repository,
            scriptEngine = engine,
        )

        coordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.ON_SESSION_START,
                sessionId = "session-1",
                scenarioId = "scenario-1",
                characterId = "character-1",
            ),
        )

        assertEquals(mapOf("entered" to "true"), repository.stateByScriptId["script-1"])
    }

    @Test
    fun execute_renderStateReturnsUiDirectives() = runTest {
        val repository = FakeRoleplayScriptRepository(
            runtimeScripts = listOf(script("script-1")),
        )
        val engine = CapturingScriptEngine { request ->
            assertEquals(RoleplayScriptEvent.RENDER_STATE, request.event)
            RoleplayScriptExecutionResult(
                uiDirectives = listOf(RoleplayScriptUiDirective(type = "notice", payload = "脚本状态已更新")),
            )
        }
        val coordinator = RoleplayScriptEventCoordinator(
            scriptRepository = repository,
            scriptEngine = engine,
        )

        val result = coordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.RENDER_STATE,
                sessionId = "session-1",
                scenarioId = "scenario-1",
                characterId = "character-1",
            ),
        )

        assertEquals("脚本状态已更新", result.uiDirectives.single().payload)
    }

    private fun script(id: String): RoleplayScriptDefinition {
        return RoleplayScriptDefinition(
            id = id,
            name = id,
            scope = RoleplayScriptScope.SESSION,
            ownerId = "session-1",
            source = "function beforePrompt() {}",
            grantedPermissions = setOf(
                RoleplayScriptPermission.READ_VARIABLES,
                RoleplayScriptPermission.WRITE_VARIABLES,
            ),
        )
    }
}

private class CapturingScriptEngine(
    private val resultFactory: (RoleplayScriptExecutionRequest) -> RoleplayScriptExecutionResult,
) : RoleplayScriptEngine {
    var lastRequest: RoleplayScriptExecutionRequest? = null

    override fun isAvailable(): Boolean = true

    override suspend fun execute(request: RoleplayScriptExecutionRequest): RoleplayScriptExecutionResult {
        lastRequest = request
        return resultFactory(request)
    }
}

private class FakeRoleplayScriptRepository(
    private val runtimeScripts: List<RoleplayScriptDefinition>,
    val stateByScriptId: MutableMap<String, Map<String, String>> = mutableMapOf(),
    private val failListRuntimeScripts: Boolean = false,
) : RoleplayScriptRepository {
    override fun observeScripts(): Flow<List<RoleplayScriptDefinition>> = flowOf(runtimeScripts)

    override suspend fun listScripts(): List<RoleplayScriptDefinition> = runtimeScripts

    override suspend fun getScript(scriptId: String): RoleplayScriptDefinition? {
        return runtimeScripts.firstOrNull { script -> script.id == scriptId }
    }

    override suspend fun listRuntimeScripts(
        characterId: String,
        scenarioId: String,
        sessionId: String,
    ): List<RoleplayScriptDefinition> {
        if (failListRuntimeScripts) {
            error("脚本列表读取失败")
        }
        return runtimeScripts
    }

    override suspend fun upsertScript(script: RoleplayScriptDefinition) = Unit

    override suspend fun deleteScript(scriptId: String) = Unit

    override suspend fun readState(scriptId: String): Map<String, String> {
        return stateByScriptId[scriptId].orEmpty()
    }

    override suspend fun writeState(scriptId: String, values: Map<String, String>) {
        stateByScriptId[scriptId] = stateByScriptId[scriptId].orEmpty() + values
    }

    override suspend fun deleteStateValue(scriptId: String, stateKey: String) = Unit
}
