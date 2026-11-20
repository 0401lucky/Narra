package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.AnthropicMessageResponse
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ResponseApiResponse
import com.google.gson.JsonElement
import com.google.gson.JsonObject

/** 主回复与辅助任务共用协议结束规则；传输结束不等于正文完整。 */
internal object CompletionEvidenceSupport {
    private val pendingResponseToolTypes = setOf(
        "function_call", "custom_tool_call", "computer_call", "local_shell_call",
        "shell_call", "apply_patch_call", "mcp_approval_request",
    )
    private val completionCode = Regex("[A-Za-z0-9_.:-]*")

    fun chat(
        message: AssistantMessageDto?,
        finishReason: String?,
    ): ChatStreamEvent.CompletionEvidence = chat(
        finishReason = finishReason,
        hasRefusal = !message?.refusal.isNullOrBlank() || hasRefusalPart(message?.content),
        hasPendingTool = !message?.toolCalls.isNullOrEmpty() || message?.functionCall != null,
    )

    fun chat(
        finishReason: String?,
        hasRefusal: Boolean = false,
        hasPendingTool: Boolean = false,
    ) = evidence(
        status = resolve(
            reported = status(finishReason, "stop"),
            hasRefusal = hasRefusal,
            hasPendingTool = hasPendingTool,
        ),
        rawFinishReason = finishReason,
    )

    fun anthropic(body: AnthropicMessageResponse) = anthropic(
        stopReason = body.stopReason,
        hasRefusal = body.content.any { it.type.equals("refusal", ignoreCase = true) },
        hasPendingTool = body.content.any { it.type.equals("tool_use", ignoreCase = true) },
    )

    fun anthropic(
        stopReason: String?,
        hasRefusal: Boolean = false,
        hasPendingTool: Boolean = false,
    ) = evidence(
        status = resolve(
            reported = status(stopReason, "end_turn", "stop_sequence"),
            hasRefusal = hasRefusal,
            hasPendingTool = hasPendingTool,
        ),
        rawFinishReason = stopReason,
    )

    fun responses(body: ResponseApiResponse?): ChatStreamEvent.CompletionEvidence {
        if (body == null) return evidence(PromptCompletionStatus.UNKNOWN)
        val reported = status(body.status, "completed")
        val incomplete = body.incompleteDetails?.let { details ->
            status(details.reason).takeUnless { it == PromptCompletionStatus.UNKNOWN }
                ?: PromptCompletionStatus.INCOMPLETE
        }
        val unfinishedItem = body.output.firstOrNull { item ->
            !item.status.isNullOrBlank() && !item.status.trim()
                .equals("completed", ignoreCase = true)
        }
        val resolved = when {
            reported == PromptCompletionStatus.FAILED || reported == PromptCompletionStatus.CANCELLED -> reported
            body.error != null -> PromptCompletionStatus.FAILED
            body.output.any { item ->
                item.content.any {
                    it.type.equals(
                        "refusal",
                        ignoreCase = true
                    ) || !it.refusal.isNullOrBlank()
                }
            } -> PromptCompletionStatus.FILTERED

            incomplete != null -> incomplete
            reported != PromptCompletionStatus.COMPLETED && reported != PromptCompletionStatus.UNKNOWN -> reported
            unfinishedItem != null -> status(unfinishedItem.status)
            body.output.any { isPendingResponseTool(it.type) } -> PromptCompletionStatus.INCOMPLETE
            else -> reported
        }
        return evidence(resolved, body.incompleteDetails?.reason ?: body.error?.code, body.status)
    }

    fun gemini(root: JsonObject): ChatStreamEvent.CompletionEvidence {
        val candidates = root.getAsJsonArrayOrNull("candidates")?.toList().orEmpty()
        val selected = candidates.mapNotNull { it.asJsonObjectOrNull() }.firstOrNull { candidate ->
            candidate.get("content")?.asJsonObjectOrNull()?.getAsJsonArrayOrNull("parts")
                ?.any { part ->
                    val item = part.asJsonObjectOrNull()
                    item?.booleanValue("thought") != true && item?.completionString("text")
                        ?.isNotBlank() == true
                } == true
        } ?: candidates.firstOrNull()?.asJsonObjectOrNull()
        val reason = selected?.completionString("finishReason")
        val blockReason =
            root.get("promptFeedback")?.asJsonObjectOrNull()?.completionString("blockReason")
        return evidence(
            if (!blockReason.isNullOrBlank()) PromptCompletionStatus.FILTERED else status(
                reason,
                "stop"
            ),
            blockReason ?: reason,
        )
    }

    fun result(content: String, evidence: ChatStreamEvent.CompletionEvidence) =
        PromptCompletionResult(
            content = content,
            status = evidence.status,
            rawFinishReason = evidence.rawFinishReason,
            rawStatus = evidence.rawStatus,
        )

    fun status(rawReason: String?, vararg completedReasons: String): PromptCompletionStatus =
        when (rawReason?.trim()?.lowercase().orEmpty()) {
            "" -> PromptCompletionStatus.UNKNOWN
            in completedReasons -> PromptCompletionStatus.COMPLETED
            "length", "max_tokens", "max_output_tokens", "max_completion_tokens",
            "context_length_exceeded", "model_context_window_exceeded" -> PromptCompletionStatus.TRUNCATED

            "content_filter", "refusal", "safety", "recitation", "blocklist", "prohibited_content" ->
                PromptCompletionStatus.FILTERED

            "failed", "error" -> PromptCompletionStatus.FAILED
            "cancelled", "canceled" -> PromptCompletionStatus.CANCELLED
            else -> PromptCompletionStatus.INCOMPLETE
        }

    fun resolve(
        reported: PromptCompletionStatus,
        hasRefusal: Boolean,
        hasPendingTool: Boolean,
    ): PromptCompletionStatus = when {
        reported == PromptCompletionStatus.FAILED || reported == PromptCompletionStatus.CANCELLED -> reported
        hasRefusal -> PromptCompletionStatus.FILTERED
        reported != PromptCompletionStatus.COMPLETED && reported != PromptCompletionStatus.UNKNOWN -> reported
        hasPendingTool -> PromptCompletionStatus.INCOMPLETE
        else -> reported
    }

    fun isPendingResponseTool(type: String) = type.lowercase() in pendingResponseToolTypes

    fun evidence(
        status: PromptCompletionStatus,
        rawFinishReason: String? = null,
        rawStatus: String? = null,
    ) = ChatStreamEvent.CompletionEvidence(
        status = status,
        rawFinishReason = safeCode(rawFinishReason),
        rawStatus = safeCode(rawStatus),
    )

    private fun safeCode(value: String?): String? = value?.takeIf {
        it.length <= 128 && completionCode.matches(it.trim())
    }

    private fun hasRefusalPart(content: Any?): Boolean = when (content) {
        is List<*> -> content.any(::hasRefusalPart)
        is Map<*, *> -> (content["type"] as? String).equals("refusal", ignoreCase = true) ||
                !(content["refusal"] as? String).isNullOrBlank()

        else -> false
    }
}

internal fun JsonObject.completionString(key: String): String? = get(key)
    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
    ?.asString

internal fun JsonElement?.completionObject(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject
