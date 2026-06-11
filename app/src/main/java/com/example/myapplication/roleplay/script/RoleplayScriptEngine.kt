package com.example.myapplication.roleplay.script

interface RoleplayScriptEngine {
    fun isAvailable(): Boolean

    suspend fun execute(request: RoleplayScriptExecutionRequest): RoleplayScriptExecutionResult
}

class DisabledRoleplayScriptEngine(
    private val reason: String = "JavaScript 脚本引擎未启用",
) : RoleplayScriptEngine {
    override fun isAvailable(): Boolean = false

    override suspend fun execute(request: RoleplayScriptExecutionRequest): RoleplayScriptExecutionResult {
        return RoleplayScriptExecutionResult(
            variables = request.input.variables,
            available = false,
            unavailableReason = reason,
        )
    }
}
