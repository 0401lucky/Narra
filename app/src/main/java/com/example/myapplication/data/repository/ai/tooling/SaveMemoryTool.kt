package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PromptMode
import com.google.gson.Gson
import com.google.gson.JsonParser

class SaveMemoryTool(
    private val gson: Gson = Gson(),
) : AppTool {
    override val name: String = NAME

    override val description: String = "在角色扮演模式中保存关键剧情记忆。仅在信息已经明确成立时使用。scene_state 只用于当前剧情已确认的状态、任务进度、关系变化和线索；persistent 只用于多轮都应长期成立的稳定设定、偏好或约束。不要保存一次性情绪、寒暄、猜测、威胁、未确认推断或普通对白。若不确定，就不要调用。长期记忆会先等待用户确认。"

    override val inputSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "content" to mapOf(
                "type" to "string",
                "description" to "要保存的记忆内容，必须是已经明确成立的事实句，不要直接复制对白",
            ),
            "memory_type" to mapOf(
                "type" to "string",
                "enum" to listOf("scene_state", "persistent"),
                "description" to "scene_state 表示当前剧情已确认状态；persistent 表示长期稳定设定或偏好。若只是当前一时情绪或未确认猜测，不要使用 persistent",
            ),
            "reason" to mapOf(
                "type" to "string",
                "description" to "可选，说明为什么需要保存这条记忆，例如“这是稳定偏好”或“这是已确认剧情状态”",
            ),
            "importance" to mapOf(
                "type" to "integer",
                "description" to "可选，重要度 0 到 100",
            ),
        ),
        "required" to listOf("content", "memory_type"),
        "additionalProperties" to false,
    )

    override suspend fun execute(
        invocation: ToolInvocation,
        context: ToolContext,
    ): ToolExecutionResult {
        val runtimeContext = context.runtimeContext
            ?: return errorResult("当前没有可用会话上下文")
        if (runtimeContext.promptMode != PromptMode.ROLEPLAY) {
            return errorResult("save_memory 目前只支持剧情模式")
        }
        val assistant = runtimeContext.assistant
            ?: return errorResult("当前没有可用助手，无法保存记忆")
        if (!assistant.memoryEnabled) {
            return errorResult("当前助手未开启记忆")
        }
        val conversation = runtimeContext.conversation
            ?: return errorResult("当前没有可用会话，无法保存记忆")
        val arguments = parseArguments(invocation)
        if (arguments.content.isBlank()) {
            return errorResult("记忆内容不能为空")
        }

        return when (arguments.memoryType) {
            "scene_state" -> {
                val result = context.memoryWriteService.saveSceneMemory(
                    toolContext = context,
                    content = arguments.content,
                    importance = arguments.importance,
                )
                ToolExecutionResult(
                    payload = gson.toJson(
                        mapOf(
                            "status" to "saved",
                            "memory_type" to "scene_state",
                            "scope" to result.scopeType.storageValue,
                            "content" to arguments.content,
                            "deduplicated" to result.deduplicated,
                        ),
                    ),
                )
            }

            "persistent" -> {
                val proposal = context.memoryWriteService.proposePersistentMemory(
                    toolContext = context,
                    content = arguments.content,
                    reason = arguments.reason,
                    importance = arguments.importance,
                )
                ToolExecutionResult(
                    payload = gson.toJson(
                        mapOf(
                            "status" to "pending_confirmation",
                            "proposal_id" to proposal.id,
                            "memory_type" to "persistent",
                            "scope" to proposal.scopeType.storageValue,
                            "content" to proposal.content,
                            "reason" to proposal.reason,
                            "conversation_id" to conversation.id,
                        ),
                    ),
                )
            }

            else -> errorResult("不支持的记忆类型：${arguments.memoryType}")
        }
    }

    private fun parseArguments(
        invocation: ToolInvocation,
    ): SaveMemoryArgs {
        val json = invocation.argumentsJson
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
            }
        val content = invocation.argumentsMap["content"]?.toString()
            ?: json?.get("content")?.takeIf { !it.isJsonNull }?.asString
            ?: ""
        val memoryType = invocation.argumentsMap["memory_type"]?.toString()
            ?: json?.get("memory_type")?.takeIf { !it.isJsonNull }?.asString
            ?: "scene_state"
        val reason = invocation.argumentsMap["reason"]?.toString()
            ?: json?.get("reason")?.takeIf { !it.isJsonNull }?.asString
            ?: ""
        val importance = invocation.argumentsMap["importance"]?.toString()?.toIntOrNull()
            ?: json?.get("importance")?.takeIf { !it.isJsonNull }?.asInt
            ?: if (memoryType == "persistent") PERSISTENT_IMPORTANCE else SCENE_IMPORTANCE
        return SaveMemoryArgs(
            content = content.trim(),
            memoryType = memoryType.trim(),
            reason = reason.trim(),
            importance = importance.coerceIn(0, 100),
        )
    }

    private fun errorResult(
        message: String,
    ): ToolExecutionResult {
        return ToolExecutionResult(
            payload = gson.toJson(
                mapOf(
                    "status" to "error",
                    "error" to message,
                ),
            ),
            isError = true,
        )
    }

    private data class SaveMemoryArgs(
        val content: String,
        val memoryType: String,
        val reason: String,
        val importance: Int,
    )

    companion object {
        const val NAME = "save_memory"
        private const val SCENE_IMPORTANCE = 70
        private const val PERSISTENT_IMPORTANCE = 60
    }
}
