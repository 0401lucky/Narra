package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ImageUrlContentPartDto
import com.example.myapplication.model.ResponseApiInputItemDto
import com.example.myapplication.model.ResponseApiReasoningDto
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.model.ResponseApiSummaryContentDto
import com.example.myapplication.model.ResponseApiToolDto
import com.example.myapplication.model.TextContentPartDto
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class ResponseApiParsedOutput(
    val content: String = "",
    val reasoning: String = "",
)

internal sealed interface ResponseApiStreamEvent {
    data class ContentDelta(
        val value: String,
    ) : ResponseApiStreamEvent

    data class ReasoningDelta(
        val value: String,
    ) : ResponseApiStreamEvent

    data object Completed : ResponseApiStreamEvent
}

internal object ResponseApiSupport {
    fun buildRequest(request: ChatCompletionRequest): ResponseApiRequest {
        val systemPrompt = request.messages
            .filter { it.role == "system" }
            .joinToString(separator = "\n\n") { message ->
                extractPlainText(message.content)
            }
            .trim()
            .ifBlank { null }
        return ResponseApiRequest(
            model = request.model,
            input = request.messages.mapNotNull(::toInputItem),
            instructions = systemPrompt,
            stream = request.stream,
            temperature = request.temperature,
            topP = request.topP,
            reasoning = request.reasoningEffort?.let { effort ->
                ResponseApiReasoningDto(
                    effort = effort,
                    summary = "auto",
                )
            },
            tools = request.tools.map { tool ->
                ResponseApiToolDto(
                    name = tool.function.name,
                    description = tool.function.description,
                    parameters = tool.function.parameters,
                )
            },
        )
    }

    fun parseResponse(response: ResponseApiResponse): ResponseApiParsedOutput {
        val content = buildString {
            response.output.forEach { item ->
                when (item.type.lowercase()) {
                    "message" -> item.content.forEach { contentItem ->
                        if (contentItem.type.lowercase() == "output_text") {
                            append(contentItem.text.orEmpty())
                        }
                    }
                }
            }
        }.trim()
        val reasoning = buildString {
            response.output.forEach { item ->
                when (item.type.lowercase()) {
                    "reasoning" -> item.summary.forEach { summaryItem: ResponseApiSummaryContentDto ->
                        if (summaryItem.type.lowercase() == "summary_text") {
                            append(summaryItem.text.orEmpty())
                        }
                    }
                }
            }
        }.trim()
        return ResponseApiParsedOutput(
            content = content,
            reasoning = reasoning,
        )
    }

    fun parseStreamEvent(rawData: String): ResponseApiStreamEvent? {
        val json = runCatching { JsonParser.parseString(rawData).asJsonObject }.getOrNull() ?: return null
        return when (json.getString("type")) {
            "response.output_text.delta" -> ResponseApiStreamEvent.ContentDelta(
                json.getString("delta"),
            )
            "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> ResponseApiStreamEvent.ReasoningDelta(
                json.getString("delta"),
            )
            "response.completed" -> ResponseApiStreamEvent.Completed
            else -> null
        }
    }

    private fun toInputItem(message: ChatMessageDto): ResponseApiInputItemDto? {
        if (message.role == "system") {
            return null
        }
        val role = when (message.role) {
            "assistant" -> "assistant"
            else -> "user"
        }
        val content = when (val rawContent = message.content) {
            is String -> rawContent.takeIf { it.isNotBlank() } ?: return null
            is List<*> -> rawContent.mapNotNull { part ->
                when (part) {
                    is TextContentPartDto -> mapOf(
                        "type" to if (role == "assistant") "output_text" else "input_text",
                        "text" to part.text,
                    )
                    is ImageUrlContentPartDto -> mapOf(
                        "type" to if (role == "assistant") "output_image" else "input_image",
                        "image_url" to part.imageUrl.url,
                    )
                    is Map<*, *> -> when (part["type"]?.toString().orEmpty()) {
                        "text" -> mapOf(
                            "type" to if (role == "assistant") "output_text" else "input_text",
                            "text" to part["text"]?.toString().orEmpty(),
                        )
                        "image_url" -> {
                            val imageUrl = part["image_url"] as? Map<*, *>
                            mapOf(
                                "type" to if (role == "assistant") "output_image" else "input_image",
                                "image_url" to imageUrl?.get("url")?.toString().orEmpty(),
                            )
                        }
                        else -> null
                    }
                    else -> null
                }
            }.takeIf { it.isNotEmpty() } ?: return null
            else -> return null
        }
        return ResponseApiInputItemDto(
            role = role,
            content = content,
        )
    }

    private fun extractPlainText(content: Any): String {
        return when (content) {
            is String -> content
            is List<*> -> content.joinToString(separator = "\n\n") { part ->
                when (part) {
                    is TextContentPartDto -> part.text
                    is Map<*, *> -> part["text"]?.toString().orEmpty()
                    else -> ""
                }
            }.trim()
            is Map<*, *> -> content["text"]?.toString().orEmpty()
            else -> ""
        }
    }

    private fun JsonObject.getString(key: String): String {
        return get(key)?.asString.orEmpty()
    }
}
