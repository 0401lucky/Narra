package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.context.WorldBookScopeSupport
import com.example.myapplication.model.WorldBookEntry
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson
import com.google.gson.JsonParser

class SearchWorldBookTool(
    private val gson: Gson = AppJson.gson,
) : AppTool {
    override val name: String = NAME

    override val description: String = "搜索当前助手与会话可访问的世界书条目，返回相关设定内容。"

    override val inputSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "要搜索的世界书关键词",
            ),
            "limit" to mapOf(
                "type" to "integer",
                "description" to "最多返回多少条结果，范围 1 到 8",
            ),
        ),
        "required" to listOf("query"),
        "additionalProperties" to false,
    )

    override suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult {
        val runtimeContext = context.runtimeContext
            ?: return errorResult("当前没有可用的会话上下文")
        val conversation = runtimeContext.conversation
            ?: return errorResult("当前没有可用会话，无法搜索世界书")
        val arguments = parseArguments(invocation)
        if (arguments.query.isBlank()) {
            return errorResult("搜索词不能为空")
        }

        val entries = WorldBookScopeSupport.filterAccessibleEntries(
            entries = context.worldBookRepository.listEnabledEntries(),
            assistant = runtimeContext.assistant,
            conversation = conversation,
        )
        val matchedEntries = WorldBookScopeSupport.sortByPriority(
            entries.filter { entry -> entry.matchesSearch(arguments.query) },
        ).take(arguments.limit)

        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "query" to arguments.query,
                    "entries" to matchedEntries.map(::toPayloadItem),
                ),
            ),
        )
    }

    private fun parseArguments(
        invocation: ToolInvocation,
    ): SearchWorldBookArgs {
        val json = invocation.argumentsJson
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
        val query = invocation.argumentsMap["query"]?.toString()
            ?: json?.get("query")?.takeIf { !it.isJsonNull }?.asString
            ?: ""
        val limit = invocation.argumentsMap["limit"]?.toString()?.toIntOrNull()
            ?: json?.get("limit")?.takeIf { !it.isJsonNull }?.asInt
            ?: DEFAULT_LIMIT
        return SearchWorldBookArgs(
            query = query.trim(),
            limit = limit.coerceIn(1, 8),
        )
    }

    private fun toPayloadItem(
        entry: WorldBookEntry,
    ): Map<String, Any> {
        return mapOf(
            "id" to entry.id,
            "title" to entry.title,
            "content" to entry.content.trim().take(MAX_CONTENT_LENGTH),
            "scope_type" to entry.scopeType.storageValue,
            "source_book_name" to entry.sourceBookName,
            "keywords" to entry.keywords,
            "always_active" to entry.alwaysActive,
            "priority" to entry.priority,
        )
    }

    private fun errorResult(
        message: String,
    ): ToolExecutionResult {
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "error" to message,
                    "entries" to emptyList<Map<String, Any>>(),
                ),
            ),
            isError = true,
        )
    }

    private data class SearchWorldBookArgs(
        val query: String,
        val limit: Int,
    )

    companion object {
        const val NAME = "search_worldbook"
        private const val DEFAULT_LIMIT = 5
        private const val MAX_CONTENT_LENGTH = 400
    }
}
