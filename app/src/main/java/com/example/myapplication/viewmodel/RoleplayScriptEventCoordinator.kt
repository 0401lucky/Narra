package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEvent
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionRequest
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionResult
import com.example.myapplication.roleplay.script.RoleplayScriptInput

internal data class RoleplayScriptEventRequest(
    val event: RoleplayScriptEvent,
    val sessionId: String,
    val scenarioId: String,
    val characterId: String,
    val userText: String = "",
    val promptText: String = "",
    val assistantText: String = "",
)

internal class RoleplayScriptEventCoordinator(
    private val scriptRepository: RoleplayScriptRepository,
    private val scriptEngine: RoleplayScriptEngine,
) {
    suspend fun execute(request: RoleplayScriptEventRequest): RoleplayScriptExecutionResult {
        val scripts = runCatching {
            scriptRepository.listRuntimeScripts(
                characterId = request.characterId,
                scenarioId = request.scenarioId,
                sessionId = request.sessionId,
            )
        }.getOrDefault(emptyList())
        if (scripts.isEmpty()) {
            return RoleplayScriptExecutionResult()
        }

        val initialVariables = scripts.fold(emptyMap<String, String>()) { variables, script ->
            variables + runCatching { scriptRepository.readState(script.id) }.getOrDefault(emptyMap())
        }
        val result = runCatching {
            scriptEngine.execute(
                RoleplayScriptExecutionRequest(
                    event = request.event,
                    input = RoleplayScriptInput(
                        sessionId = request.sessionId,
                        characterId = request.characterId,
                        scenarioId = request.scenarioId,
                        userText = request.userText,
                        promptText = request.promptText,
                        assistantText = request.assistantText,
                        variables = initialVariables,
                    ),
                    scripts = scripts,
                ),
            )
        }.getOrElse {
            return RoleplayScriptExecutionResult(variables = initialVariables)
        }

        result.variableUpdatesByScriptId.forEach { (scriptId, values) ->
            if (values.isNotEmpty()) {
                runCatching {
                    scriptRepository.writeState(scriptId, values)
                }
            }
        }
        return result
    }
}
