package com.example.myapplication.data.repository.ai.tooling

class ToolRegistry(
    tools: List<AppTool>,
) {
    private val registeredTools = tools.toList()
    private val toolsByName = registeredTools.associateBy(AppTool::name)

    fun find(name: String): AppTool? = toolsByName[name]

    fun resolve(names: Set<String>): List<AppTool> {
        return registeredTools.filter { tool -> tool.name in names }
    }

    fun all(): List<AppTool> = registeredTools
}
