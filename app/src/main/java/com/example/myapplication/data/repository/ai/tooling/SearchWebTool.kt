package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.repository.search.toCitations
import com.example.myapplication.model.MessageCitation
import com.google.gson.Gson
import com.google.gson.JsonParser

class SearchWebTool(
    private val gson: Gson = Gson(),
) : AppTool {
    override val name: String = NAME

    override val description: String = "搜索网页并返回标题、链接和摘要，用于回答时引用最新来源。"

    override val inputSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "query" to mapOf(
                "type" to "string",
                "description" to "要搜索的网页查询词",
            ),
        ),
        "required" to listOf("query"),
        "additionalProperties" to false,
    )

    override suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult {
        val query = parseQuery(invocation)
        if (query.isNullOrBlank()) {
            return errorResult(query.orEmpty(), "搜索词不能为空")
        }
        val config = context.searchToolConfig
            ?: return errorResult(query, "当前未配置可用搜索源")
        return runCatching {
            val result = context.searchRepository.search(
                source = config.source,
                query = query,
                resultCount = config.resultCount,
            )
            ToolExecutionResult(
                payload = gson.toJson(
                    mapOf(
                        "query" to result.query,
                        "results" to result.items.map { item ->
                            mapOf(
                                "title" to item.title,
                                "url" to item.url,
                                "snippet" to item.snippet,
                                "source" to item.sourceLabel,
                            )
                        },
                    ),
                ),
                citations = result.toCitations().distinctBy(MessageCitation::url),
            )
        }.getOrElse { throwable ->
            errorResult(query, throwable.message ?: "搜索失败")
        }
    }

    private fun parseQuery(invocation: ToolInvocation): String? {
        invocation.argumentsMap["query"]
            ?.toString()
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { return it }
        val normalized = invocation.argumentsJson.orEmpty().trim()
        if (normalized.isBlank()) {
            return null
        }
        val json = runCatching {
            JsonParser.parseString(normalized).asJsonObject
        }.getOrNull() ?: return null
        return json.get("query")?.asString?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun errorResult(
        query: String,
        message: String,
    ): ToolExecutionResult {
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "query" to query,
                    "error" to message,
                    "results" to emptyList<Map<String, String>>(),
                ),
            ),
            isError = true,
        )
    }

    companion object {
        const val NAME = "search_web"
    }
}
