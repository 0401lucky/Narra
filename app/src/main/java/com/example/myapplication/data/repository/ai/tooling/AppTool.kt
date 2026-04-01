package com.example.myapplication.data.repository.ai.tooling

interface AppTool {
    val name: String
    val description: String
    val inputSchema: Map<String, Any>
    val readOnly: Boolean
        get() = true
    val concurrencySafe: Boolean
        get() = true

    suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult
}
