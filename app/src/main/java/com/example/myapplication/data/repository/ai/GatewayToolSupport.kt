package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.repository.ai.tooling.AppTool
import com.example.myapplication.data.repository.ai.tooling.ToolInvocation
import com.example.myapplication.model.AnthropicToolDto
import com.example.myapplication.model.ChatFunctionDefinitionDto
import com.example.myapplication.model.ChatToolDto
import com.example.myapplication.model.ChatToolFunctionDto
import com.example.myapplication.model.ResponseApiToolDto

internal object GatewayToolSupport {
    fun toOpenAiTools(
        tools: List<AppTool>,
    ): List<ChatToolDto> {
        return tools.map { tool ->
            ChatToolDto(
                function = ChatFunctionDefinitionDto(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.inputSchema,
                ),
            )
        }
    }

    fun toResponseApiTools(
        tools: List<AppTool>,
    ): List<ResponseApiToolDto> {
        return tools.map { tool ->
            ResponseApiToolDto(
                name = tool.name,
                description = tool.description,
                parameters = tool.inputSchema,
            )
        }
    }

    fun toAnthropicTools(
        tools: List<AppTool>,
    ): List<AnthropicToolDto> {
        return tools.map { tool ->
            AnthropicToolDto(
                name = tool.name,
                description = tool.description,
                inputSchema = tool.inputSchema,
            )
        }
    }

    fun parseToolCall(
        function: ChatToolFunctionDto,
        id: String,
    ): ToolInvocation? {
        val toolName = function.name.trim()
        if (toolName.isBlank()) {
            return null
        }
        return ToolInvocation(
            id = id.ifBlank { toolName },
            name = toolName,
            argumentsJson = function.arguments,
        )
    }

    fun parseToolCall(
        name: String?,
        arguments: String?,
        id: String?,
    ): ToolInvocation? {
        val toolName = name.orEmpty().trim()
        if (toolName.isBlank()) {
            return null
        }
        return ToolInvocation(
            id = id.orEmpty().ifBlank { toolName },
            name = toolName,
            argumentsJson = arguments,
        )
    }

    fun parseToolCall(
        name: String?,
        input: Map<String, Any>?,
        id: String?,
    ): ToolInvocation? {
        val toolName = name.orEmpty().trim()
        if (toolName.isBlank()) {
            return null
        }
        return ToolInvocation(
            id = id.orEmpty().ifBlank { toolName },
            name = toolName,
            argumentsMap = input.orEmpty(),
        )
    }
}
