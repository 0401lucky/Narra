package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.roleplay.script.RoleplayScriptDefinition
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionRequest
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionResult
import com.example.myapplication.roleplay.script.RoleplayScriptPermission
import com.example.myapplication.roleplay.script.RoleplayScriptScope
import com.example.myapplication.roleplay.script.RoleplayScriptUiDirective
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoleplayScriptLabViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveScript_rejectsNonGlobalScriptWithoutOwnerId() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeRoleplayScriptLabRepository()
        val viewModel = RoleplayScriptLabViewModel(
            scriptRepository = repository,
            idProvider = { "script-1" },
        )
        val job = launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.updateName("会话脚本")
        viewModel.updateScope(RoleplayScriptScope.SESSION)
        viewModel.updateSource("function beforeSend() {}")
        viewModel.saveScript()
        advanceUntilIdle()

        assertEquals("非全局脚本需要选择或填写绑定目标", viewModel.uiState.value.message)
        assertEquals(emptyList<RoleplayScriptDefinition>(), repository.listScripts())
        job.cancel()
    }

    @Test
    fun saveScript_persistsGlobalScriptAndClearsOwnerId() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeRoleplayScriptLabRepository()
        val viewModel = RoleplayScriptLabViewModel(
            scriptRepository = repository,
            idProvider = { "script-1" },
        )
        val job = launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.updateName("全局提示")
        viewModel.updateOwnerId("should-drop")
        viewModel.updateScope(RoleplayScriptScope.GLOBAL)
        viewModel.updateSource("function beforePrompt() { return { promptAdditions: ['x'] }; }")
        viewModel.togglePermission(RoleplayScriptPermission.MODIFY_PROMPT)
        viewModel.saveScript()
        advanceUntilIdle()

        val saved = repository.listScripts().single()
        assertEquals("script-1", saved.id)
        assertEquals("全局提示", saved.name)
        assertEquals(RoleplayScriptScope.GLOBAL, saved.scope)
        assertEquals("", saved.ownerId)
        assertTrue(RoleplayScriptPermission.MODIFY_PROMPT in saved.grantedPermissions)
        assertEquals("脚本已保存", viewModel.uiState.value.message)
        job.cancel()
    }

    @Test
    fun selectAndDeleteScript_resetsDraftAndRemovesRepositoryItem() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeRoleplayScriptLabRepository(
            scripts = listOf(
                RoleplayScriptDefinition(
                    id = "script-1",
                    name = "旧脚本",
                    scope = RoleplayScriptScope.GLOBAL,
                    source = "function beforePrompt() {}",
                ),
            ),
        )
        var idIndex = 0
        val ids = listOf("draft-1", "draft-2")
        val viewModel = RoleplayScriptLabViewModel(
            scriptRepository = repository,
            idProvider = { ids.getOrElse(idIndex++) { "draft-x" } },
        )
        val job = launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.selectScript("script-1")
        advanceUntilIdle()
        assertEquals("script-1", viewModel.uiState.value.selectedScriptId)

        viewModel.deleteSelectedScript()
        advanceUntilIdle()

        assertEquals(emptyList<RoleplayScriptDefinition>(), repository.listScripts())
        assertEquals("", viewModel.uiState.value.selectedScriptId)
        assertEquals("脚本已删除", viewModel.uiState.value.message)
        assertTrue(viewModel.uiState.value.draft.id.startsWith("draft-"))
        job.cancel()
    }

    @Test
    fun applyTemplate_updatesDraftPermissionsAndTestEvent() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeRoleplayScriptLabRepository()
        val viewModel = RoleplayScriptLabViewModel(
            scriptRepository = repository,
            idProvider = { "script-1" },
        )
        val job = launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.applyTemplate("send_rewrite")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("发送前改写", state.draft.name)
        assertEquals(RoleplayScriptScope.GLOBAL, state.draft.scope)
        assertTrue(RoleplayScriptPermission.MODIFY_OUTGOING_MESSAGE in state.draft.grantedPermissions)
        assertTrue("beforeSend" in state.draft.source)
        job.cancel()
    }

    @Test
    fun runScriptTest_exposesExecutionResult() = runTest(mainDispatcherRule.dispatcher.scheduler) {
        val repository = FakeRoleplayScriptLabRepository()
        val engine = FakeRoleplayScriptLabEngine(
            result = RoleplayScriptExecutionResult(
                variables = mapOf("round" to "2"),
                promptAdditions = listOf("追加提示"),
                outgoingMessage = "改写后的文本",
                uiDirectives = listOf(RoleplayScriptUiDirective("notice", "脚本生效")),
            ),
        )
        val viewModel = RoleplayScriptLabViewModel(
            scriptRepository = repository,
            scriptEngine = engine,
            idProvider = { "script-1" },
        )
        val job = launch { viewModel.uiState.collect { } }

        advanceUntilIdle()
        viewModel.updateSource("function beforePrompt() {}")
        viewModel.updateTestVariablesText("round=1")
        viewModel.runScriptTest()
        advanceUntilIdle()

        val result = viewModel.uiState.value.test.result
        assertEquals(false, viewModel.uiState.value.test.isRunning)
        assertEquals("追加提示", result?.promptAdditions?.single())
        assertEquals("改写后的文本", result?.outgoingMessage)
        assertEquals("2", result?.variables?.get("round"))
        assertEquals("脚本生效", result?.uiDirectives?.single()?.payload)
        assertEquals(mapOf("round" to "1"), engine.lastRequest?.input?.variables)
        job.cancel()
    }
}

private class FakeRoleplayScriptLabRepository(
    scripts: List<RoleplayScriptDefinition> = emptyList(),
) : RoleplayScriptRepository {
    private val scriptsState = MutableStateFlow(scripts)

    override fun observeScripts(): Flow<List<RoleplayScriptDefinition>> = scriptsState

    override suspend fun listScripts(): List<RoleplayScriptDefinition> = scriptsState.value

    override suspend fun getScript(scriptId: String): RoleplayScriptDefinition? {
        return scriptsState.value.firstOrNull { it.id == scriptId }
    }

    override suspend fun listRuntimeScripts(
        characterId: String,
        scenarioId: String,
        sessionId: String,
    ): List<RoleplayScriptDefinition> = scriptsState.value

    override suspend fun upsertScript(script: RoleplayScriptDefinition) {
        val normalized = if (script.scope == RoleplayScriptScope.GLOBAL) {
            script.copy(ownerId = "")
        } else {
            script
        }
        scriptsState.value = scriptsState.value.filterNot { it.id == normalized.id } + normalized
    }

    override suspend fun deleteScript(scriptId: String) {
        scriptsState.value = scriptsState.value.filterNot { it.id == scriptId }
    }

    override suspend fun readState(scriptId: String): Map<String, String> = emptyMap()

    override suspend fun writeState(scriptId: String, values: Map<String, String>) = Unit

    override suspend fun deleteStateValue(scriptId: String, stateKey: String) = Unit
}

private class FakeRoleplayScriptLabEngine(
    private val result: RoleplayScriptExecutionResult,
) : RoleplayScriptEngine {
    var lastRequest: RoleplayScriptExecutionRequest? = null
        private set

    override fun isAvailable(): Boolean = true

    override suspend fun execute(request: RoleplayScriptExecutionRequest): RoleplayScriptExecutionResult {
        lastRequest = request
        return result
    }
}
