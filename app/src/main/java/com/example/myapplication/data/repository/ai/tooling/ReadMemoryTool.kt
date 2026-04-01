package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.context.MemoryScopeSupport
import com.example.myapplication.context.MemorySelector
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class ReadMemoryTool(
    private val memorySelector: MemorySelector = MemorySelector(),
    private val gson: Gson = Gson(),
) : AppTool {
    override val name: String = NAME

    override val description: String = "读取当前助手与会话可访问的长期记忆与场景记忆，帮助模型回忆已确认事实。"

    override val inputSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "要查找的记忆关键词；留空则读取当前最相关的记忆",
            ),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "最多返回多少条记忆，范围 1 到 8",
            ),
        ),
        "additionalProperties" to false,
    )

    override suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult {
        val runtimeContext = context.runtimeContext
            ?: return errorResult("当前没有可用的会话上下文")
        val assistant = runtimeContext.assistant
            ?: return errorResult("当前没有可用助手，无法读取记忆")
        val conversation = runtimeContext.conversation
            ?: return errorResult("当前没有可用会话，无法读取记忆")

        val arguments = parseArguments(invocation)
        val accessibleEntries = MemoryScopeSupport.filterAccessibleEntries(
            entries = context.memoryRepository.listEntries(),
            assistant = assistant,
            conversation = conversation,
        )
        if (accessibleEntries.isEmpty()) {
            return ToolExecutionResult(
                payload = gson.toJson(
                    mapOf(
                        "query" to arguments.query,
                        "persistent_memories" to emptyList<Map<String, Any>>(),
                        "scene_state_memories" to emptyList<Map<String, Any>>(),
                    ),
                ),
            )
        }

        val selectedEntries = if (arguments.query.isBlank()) {
            memorySelector.select(
                entries = accessibleEntries,
                assistant = assistant,
                conversation = conversation,
                promptMode = runtimeContext.promptMode,
                userInputText = runtimeContext.userInputText,
                recentMessages = runtimeContext.recentMessages,
            ).take(arguments.limit)
        } else {
            MemoryScopeSupport.sortByPriority(
                accessibleEntries.filter { entry ->
                    entry.content.contains(arguments.query, ignoreCase = true)
                },
            ).take(arguments.limit)
        }

        val persistentMemories = selectedEntries
            .filter { it.scopeType != MemoryScopeType.CONVERSATION }
            .map(::toPayloadItem)
        val sceneStateMemories = selectedEntries
            .filter { it.scopeType == MemoryScopeType.CONVERSATION }
            .map(::toPayloadItem)

        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "query" to arguments.query,
                    "persistent_memories" to persistentMemories,
                    "scene_state_memories" to sceneStateMemories,
                ),
            ),
        )
    }

    private fun parseArguments(
        invocation: ToolInvocation,
    ): ReadMemoryArgs {
        val jsonObject = invocation.argumentsJson
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
        val query = invocation.argumentsMap["query"]?.toString()
            ?: jsonObject?.get("query")?.takeIf { !it.isJsonNull }?.asString
            ?: ""
        val limit = invocation.argumentsMap["limit"]?.toString()?.toIntOrNull()
            ?: jsonObject?.get("limit")?.takeIf { !it.isJsonNull }?.asInt
            ?: DEFAULT_LIMIT
        return ReadMemoryArgs(
            query = query.trim(),
            limit = limit.coerceIn(1, 8),
        )
    }

    private fun toPayloadItem(
        entry: MemoryEntry,
    ): Map<String, Any> {
        return mapOf(
            "id" to entry.id,
            "scope_type" to entry.scopeType.storageValue,
            "content" to entry.content,
            "pinned" to entry.pinned,
            "importance" to entry.importance,
        )
    }

    private fun errorResult(
        message: String,
    ): ToolExecutionResult {
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "error" to message,
                    "persistent_memories" to emptyList<Map<String, Any>>(),
                    "scene_state_memories" to emptyList<Map<String, Any>>(),
                ),
            ),
            isError = true,
        )
    }

    private data class ReadMemoryArgs(
        val query: String,
        val limit: Int,
    )

    companion object {
        const val NAME = "read_memory"
        private const val DEFAULT_LIMIT = 5
    }
}
