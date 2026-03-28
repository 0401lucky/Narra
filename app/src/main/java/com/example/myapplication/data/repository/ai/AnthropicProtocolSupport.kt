package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.AnthropicContentPartDto
import com.example.myapplication.model.AnthropicImageSourceDto
import com.example.myapplication.model.AnthropicMessageDto
import com.example.myapplication.model.AnthropicMessageRequest
import com.example.myapplication.model.AnthropicMessageResponse
import com.example.myapplication.model.AnthropicThinkingDto
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ImageUrlContentPartDto
import com.example.myapplication.model.ImageUrlDto
import com.example.myapplication.model.TextContentPartDto
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class AnthropicStreamDelta(
    val content: String = "",
    val reasoning: String = "",
    val stop: Boolean = false,
    val errorMessage: String? = null,
)

internal object AnthropicProtocolSupport {
    private const val DefaultMaxTokens = 8_192

    fun buildMessageRequest(
        request: ChatCompletionRequest,
    ): AnthropicMessageRequest {
        val systemPrompt = request.messages
            .filter { it.role == "system" }
            .joinToString(separator = "\n\n") { message ->
                extractPlainText(message.content)
            }
            .trim()
            .ifBlank { null }
        val messages = request.messages.mapNotNull(::toAnthropicMessage)
        return AnthropicMessageRequest(
            model = request.model,
            messages = messages,
            maxTokens = DefaultMaxTokens,
            system = systemPrompt,
            stream = request.stream,
            temperature = request.temperature,
            topP = request.topP,
            thinking = request.thinking?.let { thinking ->
                AnthropicThinkingDto(
                    budgetTokens = thinking.budgetTokens,
                )
            },
        )
    }

    fun toAssistantReply(response: AnthropicMessageResponse): AssistantReply {
        val content = buildString {
            response.content.forEach { block ->
                when (block.type.lowercase()) {
                    "text" -> append(block.text.orEmpty())
                }
            }
        }.trim()
        val reasoning = buildString {
            response.content.forEach { block ->
                when (block.type.lowercase()) {
                    "thinking" -> append(block.thinking.orEmpty().ifBlank { block.text.orEmpty() })
                }
            }
        }.trim()
        return AssistantReply(
            content = content,
            reasoningContent = reasoning,
        )
    }

    fun extractContentText(response: AnthropicMessageResponse): String {
        return toAssistantReply(response).content
    }

    fun parseStreamData(rawData: String): AnthropicStreamDelta {
        val json = runCatching {
            JsonParser.parseString(rawData).asJsonObject
        }.getOrNull() ?: return AnthropicStreamDelta()
        val type = json.getString("type").lowercase()
        return when (type) {
            "content_block_delta" -> parseContentBlockDelta(json)
            "message_stop" -> AnthropicStreamDelta(stop = true)
            "error" -> AnthropicStreamDelta(
                errorMessage = json.getAsJsonObject("error")
                    ?.getString("message")
                    .orEmpty()
                    .ifBlank { rawData.trim() },
            )
            else -> AnthropicStreamDelta()
        }
    }

    private fun parseContentBlockDelta(json: JsonObject): AnthropicStreamDelta {
        val delta = json.getAsJsonObject("delta") ?: return AnthropicStreamDelta()
        return when (delta.getString("type").lowercase()) {
            "text_delta" -> AnthropicStreamDelta(
                content = delta.getString("text"),
            )
            "thinking_delta" -> AnthropicStreamDelta(
                reasoning = delta.getString("thinking"),
            )
            else -> AnthropicStreamDelta()
        }
    }

    private fun toAnthropicMessage(message: ChatMessageDto): AnthropicMessageDto? {
        if (message.role == "system") {
            return null
        }
        val role = when (message.role) {
            "assistant" -> "assistant"
            else -> "user"
        }
        val contentParts = toAnthropicContentParts(message.content)
        if (contentParts.isEmpty()) {
            return null
        }
        return AnthropicMessageDto(
            role = role,
            content = contentParts,
        )
    }

    private fun toAnthropicContentParts(content: Any): List<AnthropicContentPartDto> {
        return when (content) {
            is String -> listOfNotNull(
                content.trim().takeIf { it.isNotBlank() }?.let { text ->
                    AnthropicContentPartDto(
                        type = "text",
                        text = text,
                    )
                },
            )
            is List<*> -> content.flatMap(::toAnthropicContentPart)
            is Map<*, *> -> toAnthropicContentPart(content)
            else -> emptyList()
        }
    }

    private fun toAnthropicContentPart(contentPart: Any?): List<AnthropicContentPartDto> {
        return when (contentPart) {
            is TextContentPartDto -> listOf(
                AnthropicContentPartDto(
                    type = "text",
                    text = contentPart.text,
                ),
            )
            is ImageUrlContentPartDto -> imageContentPartFromUrl(contentPart.imageUrl)
            is Map<*, *> -> {
                when (contentPart["type"]?.toString().orEmpty()) {
                    "text" -> listOfNotNull(
                        contentPart["text"]?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { text ->
                            AnthropicContentPartDto(
                                type = "text",
                                text = text,
                            )
                        },
                    )
                    "image_url" -> {
                        val imageUrl = contentPart["image_url"]
                        if (imageUrl is Map<*, *>) {
                            imageContentPartFromUrl(
                                ImageUrlDto(url = imageUrl["url"]?.toString().orEmpty()),
                            )
                        } else {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }
            }
            is String -> listOfNotNull(
                contentPart.trim().takeIf { it.isNotBlank() }?.let { text ->
                    AnthropicContentPartDto(
                        type = "text",
                        text = text,
                    )
                },
            )
            else -> emptyList()
        }
    }

    private fun imageContentPartFromUrl(imageUrl: ImageUrlDto): List<AnthropicContentPartDto> {
        val url = imageUrl.url.trim()
        if (url.isBlank()) {
            return emptyList()
        }
        val parsedDataUrl = parseDataUrl(url) ?: return emptyList()
        return listOf(
            AnthropicContentPartDto(
                type = "image",
                source = AnthropicImageSourceDto(
                    type = "base64",
                    mediaType = parsedDataUrl.mimeType,
                    data = parsedDataUrl.base64,
                ),
            ),
        )
    }

    private fun parseDataUrl(value: String): ParsedDataUrl? {
        if (!value.startsWith("data:", ignoreCase = true)) {
            return null
        }
        val separatorIndex = value.indexOf(',')
        if (separatorIndex == -1) {
            return null
        }
        val metadata = value.substring(5, separatorIndex)
        val data = value.substring(separatorIndex + 1).trim()
        if (data.isBlank()) {
            return null
        }
        val metadataParts = metadata.split(';')
        val mimeType = metadataParts.firstOrNull().orEmpty().ifBlank { "image/png" }
        if (metadataParts.none { it.equals("base64", ignoreCase = true) }) {
            return null
        }
        return ParsedDataUrl(
            mimeType = mimeType,
            base64 = data,
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

    private data class ParsedDataUrl(
        val mimeType: String,
        val base64: String,
    )
}
