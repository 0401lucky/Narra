package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.system.json.AppJson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** 只在单次 HTTP 响应内累计结束证据，不继承工具前置轮的状态。 */
internal class GatewayCompletionTracker {
    private var reported = CompletionEvidenceSupport.evidence(PromptCompletionStatus.UNKNOWN)
    private var hasRefusal = false
    private var hasPendingTool = false
    private var hasMalformedData = false
    private val unfinishedItems = mutableMapOf<String, PromptCompletionStatus>()

    fun observeOpenAi(data: String, apiMode: OpenAiTextApiMode) {
        val root = parse(data) ?: return
        if (apiMode == OpenAiTextApiMode.RESPONSES) {
            observeResponses(root)
        } else if (root.has("candidates")) {
            merge(CompletionEvidenceSupport.gemini(root))
        } else {
            val choice = root.getAsJsonArrayOrNull("choices")?.firstOrNull()?.completionObject()
            val delta = choice?.get("delta").completionObject()
                ?: choice?.get("message").completionObject()
            hasRefusal = hasRefusal || !delta?.completionString("refusal").isNullOrBlank() ||
                    hasRefusalContent(delta)
            hasPendingTool =
                hasPendingTool || delta?.getAsJsonArrayOrNull("tool_calls")?.any() == true ||
                        delta?.get("function_call")?.isJsonNull == false
            merge(CompletionEvidenceSupport.chat(choice?.completionString("finish_reason")))
        }
        if (root.get("error")?.isJsonNull == false ||
            root.completionString("type") in setOf("error", "response.error")
        ) {
            merge(CompletionEvidenceSupport.evidence(PromptCompletionStatus.FAILED))
        }
    }

    fun observeAnthropic(data: String) {
        val root = parse(data) ?: return
        when (root.completionString("type")) {
            "message_start" -> {
                val message = root.get("message").completionObject()
                merge(CompletionEvidenceSupport.anthropic(message?.completionString("stop_reason")))
            }

            "message_delta" -> {
                val delta = root.get("delta").completionObject()
                merge(CompletionEvidenceSupport.anthropic(delta?.completionString("stop_reason")))
            }

            "content_block_start" -> {
                when (root.get("content_block").completionObject()?.completionString("type")
                    ?.lowercase()) {
                    "refusal" -> hasRefusal = true
                    "tool_use" -> hasPendingTool = true
                }
            }

            "error" -> merge(CompletionEvidenceSupport.evidence(PromptCompletionStatus.FAILED))
        }
    }

    fun evidence(): ChatStreamEvent.CompletionEvidence {
        val payloadStatus = when {
            reported.status != PromptCompletionStatus.COMPLETED && reported.status != PromptCompletionStatus.UNKNOWN ->
                reported.status

            hasMalformedData -> PromptCompletionStatus.INCOMPLETE
            else -> unfinishedItems.values.maxByOrNull(::priority) ?: reported.status
        }
        return reported.copy(
            status = CompletionEvidenceSupport.resolve(
                reported = payloadStatus,
                hasRefusal = hasRefusal,
                hasPendingTool = hasPendingTool,
            ),
        )
    }

    private fun observeResponses(root: JsonObject) {
        val type = root.completionString("type").orEmpty()
        if (type.startsWith("response.refusal.")) hasRefusal = true
        val item = root.get("item").completionObject()
        if (type in setOf(
                "response.output_item.added",
                "response.output_item.done"
            ) && item != null
        ) {
            hasPendingTool = hasPendingTool || CompletionEvidenceSupport.isPendingResponseTool(
                item.completionString("type").orEmpty(),
            )
            hasRefusal = hasRefusal || hasRefusalContent(item)
            val id = root.get("output_index")?.takeIf { it.isJsonPrimitive }?.asString
                ?: item.completionString("id")
            if (id != null) {
                val status = item.completionString("status")
                if (type == "response.output_item.done" &&
                    (status.isNullOrBlank() || status.trim().equals("completed", ignoreCase = true))
                ) {
                    unfinishedItems.remove(id)
                } else if (!status.isNullOrBlank() && !status.trim()
                        .equals("completed", ignoreCase = true)
                ) {
                    unfinishedItems[id] = CompletionEvidenceSupport.status(status)
                    if (type == "response.output_item.done") {
                        merge(
                            CompletionEvidenceSupport.evidence(
                                CompletionEvidenceSupport.status(
                                    status
                                )
                            )
                        )
                    }
                }
            }
        }
        val terminalStatus = when (type) {
            "response.completed" -> "completed"
            "response.incomplete" -> "incomplete"
            "response.failed" -> "failed"
            "response.cancelled", "response.canceled" -> "cancelled"
            else -> null
        } ?: return
        val rawBody = root.get("response")
        val body = rawBody.completionObject()
        if (rawBody != null && body == null) {
            merge(
                CompletionEvidenceSupport.evidence(
                    if (terminalStatus == "failed") PromptCompletionStatus.FAILED else PromptCompletionStatus.INCOMPLETE
                )
            )
            return
        }
        val parsed = body?.let {
            runCatching { AppJson.gson.fromJson(it, ResponseApiResponse::class.java) }.getOrNull()
        }
        if (body != null && parsed == null) {
            merge(
                CompletionEvidenceSupport.evidence(
                    if (terminalStatus == "failed" || body.get("error")?.isJsonNull == false) PromptCompletionStatus.FAILED
                    else PromptCompletionStatus.INCOMPLETE,
                    rawStatus = body.completionString("status"),
                )
            )
            return
        }
        val response = parsed ?: ResponseApiResponse()
        // 终止事件本身是明确协议证据；不把 output_text.done 或流 EOF 当作完成。
        val effective = response.copy(status = response.status ?: terminalStatus)
        body?.getAsJsonArrayOrNull("output")?.forEachIndexed { index, value ->
            val output = value.completionObject()
            val status = output?.completionString("status")
            if (status.isNullOrBlank() || status.trim().equals("completed", ignoreCase = true)) {
                unfinishedItems.remove(index.toString())
                output?.completionString("id")?.let(unfinishedItems::remove)
            }
        }
        val resolved = CompletionEvidenceSupport.responses(effective)
        merge(
            CompletionEvidenceSupport.evidence(
                resolved.status,
                resolved.rawFinishReason,
                response.status
            )
        )
        if (terminalStatus != "completed") {
            merge(
                CompletionEvidenceSupport.evidence(
                    CompletionEvidenceSupport.status(terminalStatus),
                    resolved.rawFinishReason,
                    response.status,
                )
            )
        }
    }

    private fun merge(incoming: ChatStreamEvent.CompletionEvidence) {
        if (priority(incoming.status) > priority(reported.status)) {
            reported = incoming
        } else if (priority(incoming.status) == priority(reported.status) &&
            (incoming.rawFinishReason != null || incoming.rawStatus != null)
        ) {
            reported = incoming
        }
    }

    private fun priority(status: PromptCompletionStatus): Int = when (status) {
        PromptCompletionStatus.UNKNOWN -> 0
        PromptCompletionStatus.COMPLETED -> 1
        PromptCompletionStatus.INCOMPLETE -> 2
        PromptCompletionStatus.TRUNCATED -> 3
        PromptCompletionStatus.FILTERED -> 4
        PromptCompletionStatus.CANCELLED -> 5
        PromptCompletionStatus.FAILED -> 6
    }

    private fun hasRefusalContent(root: JsonObject?): Boolean =
        root?.getAsJsonArrayOrNull("content")
            ?.any { element ->
                val part = element.completionObject()
                part?.completionString("type").equals("refusal", ignoreCase = true) ||
                        !part?.completionString("refusal").isNullOrBlank()
            } == true

    private fun parse(data: String): JsonObject? {
        val root = runCatching { JsonParser.parseString(data).completionObject() }.getOrNull()
        if (root == null && data.isNotBlank()) hasMalformedData = true
        return root
    }
}

private sealed interface GatewayStreamFrame {
    data class Event(val value: ChatStreamEvent) : GatewayStreamFrame
    data class Failure(val cause: Throwable) : GatewayStreamFrame
}

/** 先把异常封为终止数据再跨dispatcher，避免异常取消丢掉缓冲中的结束证据。 */
internal fun Flow<ChatStreamEvent>.withCompletionFailureEvidence(
    dispatcher: CoroutineDispatcher,
): Flow<ChatStreamEvent> = flow {
    var failureDelivered = false
    this@withCompletionFailureEvidence
        .map<ChatStreamEvent, GatewayStreamFrame> { GatewayStreamFrame.Event(it) }
        .catch { failure ->
            if (failure is CancellationException) throw failure
            emit(GatewayStreamFrame.Failure(failure))
        }
        .flowOn(dispatcher)
        .collect { frame ->
            when (frame) {
                is GatewayStreamFrame.Event -> {
                    emit(frame.value)
                    if (frame.value is ChatStreamEvent.CompletionEvidence &&
                        frame.value.status == PromptCompletionStatus.FAILED
                    ) failureDelivered = true
                }

                is GatewayStreamFrame.Failure -> {
                    if (!failureDelivered) emit(
                        CompletionEvidenceSupport.evidence(
                            PromptCompletionStatus.FAILED
                        )
                    )
                    throw frame.cause
                }
            }
        }
}
