package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.PromptMode
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson

class SaveMemoryTool(
    private val gson: Gson = AppJson.gson,
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
            ?: return errorResult("当前没有可用角色，无法保存记忆")
        if (!assistant.memoryEnabled) {
            return errorResult("当前角色未开启记忆")
        }
        val conversation = runtimeContext.conversation
            ?: return errorResult("当前没有可用会话，无法保存记忆")
        validateArguments(invocation)?.let { message ->
            return errorResult(message)
        }
        val arguments = parseArguments(invocation)
        if (arguments.content.isBlank()) {
            return errorResult("记忆内容不能为空")
        }
        if (arguments.contentTooLong) {
            return errorResult("记忆内容过长，请压缩到 ${MemoryToolPayloadPolicy.MAX_CONTENT_LENGTH} 字以内")
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

    private fun validateArguments(
        invocation: ToolInvocation,
    ): String? {
        val unknownNames = ToolArgumentSupport.argumentNames(invocation) - ALLOWED_ARGUMENTS
        if (unknownNames.isNotEmpty()) {
            return "不支持的参数：${unknownNames.sorted().joinToString("、")}"
        }
        if (ToolArgumentSupport.hasArgument(invocation, "content") &&
            ToolArgumentSupport.stringArgument(invocation, "content") == null
        ) {
            return "记忆内容必须是文本"
        }
        if (ToolArgumentSupport.hasArgument(invocation, "memory_type") &&
            ToolArgumentSupport.stringArgument(invocation, "memory_type") == null
        ) {
            return "记忆类型必须是文本"
        }
        if (ToolArgumentSupport.hasArgument(invocation, "reason") &&
            ToolArgumentSupport.stringArgument(invocation, "reason") == null
        ) {
            return "记忆原因必须是文本"
        }
        if (ToolArgumentSupport.hasArgument(invocation, "importance") &&
            ToolArgumentSupport.intArgument(invocation, "importance") == null
        ) {
            return "记忆重要度必须是数字"
        }
        return null
    }

    private fun parseArguments(
        invocation: ToolInvocation,
    ): SaveMemoryArgs {
        val rawContent = ToolArgumentSupport.stringArgument(invocation, "content").orEmpty()
        val memoryType = ToolArgumentSupport.stringArgument(invocation, "memory_type")
            ?.trim()
            ?.ifBlank { null }
            ?: "scene_state"
        val rawReason = ToolArgumentSupport.stringArgument(invocation, "reason").orEmpty()
        val importance = ToolArgumentSupport.intArgument(invocation, "importance")
            ?: if (memoryType == "persistent") PERSISTENT_IMPORTANCE else SCENE_IMPORTANCE
        return SaveMemoryArgs(
            content = MemoryToolPayloadPolicy.normalizeContent(rawContent),
            contentTooLong = MemoryToolPayloadPolicy.isContentTooLong(rawContent),
            memoryType = memoryType,
            reason = MemoryToolPayloadPolicy.normalizeReason(rawReason),
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
        val contentTooLong: Boolean,
        val memoryType: String,
        val reason: String,
        val importance: Int,
    )

    companion object {
        const val NAME = "save_memory"
        private const val SCENE_IMPORTANCE = 70
        private const val PERSISTENT_IMPORTANCE = 60
        private val ALLOWED_ARGUMENTS = setOf("content", "memory_type", "reason", "importance")
    }
}
