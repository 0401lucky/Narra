package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.AssistantImagePartDto
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatDeltaDto
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toPlainText

internal data class ParsedAssistantOutput(
    val content: String,
    val reasoning: String,
    val parts: List<ChatMessagePart> = emptyList(),
)

internal object GatewayAssistantOutputParser {
    private val thinkTagRegex = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)
    private val closingThinkTagRegex = Regex("</think>")

    fun extractAssistantOutput(
        assistantMessage: AssistantMessageDto?,
    ): ParsedAssistantOutput {
        val reasoning = extractReasoning(assistantMessage).trim()
        val contentParts = extractAssistantContentParts(assistantMessage?.content)
        val mergedParts = mergeAssistantParts(
            contentParts = contentParts,
            imageParts = extractAssistantImageParts(assistantMessage?.images),
        )
        if (mergedParts.isNotEmpty()) {
            val splitParts = splitThinkTaggedParts(mergedParts)
            return splitParts.copy(
                reasoning = listOf(reasoning, splitParts.reasoning)
                    .filter { it.isNotBlank() }
                    .joinToString(separator = "\n\n"),
            )
        }

        val contentText = extractContentText(assistantMessage?.content).trim()
        val splitContent = splitThinkTaggedContent(contentText)
        return splitContent.copy(
            reasoning = listOf(reasoning, splitContent.reasoning)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n"),
        )
    }

    fun extractReasoning(delta: ChatDeltaDto?): String {
        return delta?.reasoningContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            .orEmpty()
    }

    fun extractReasoning(message: AssistantMessageDto?): String {
        return message?.reasoningContent
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            .orEmpty()
    }

    fun extractAssistantImageParts(
        imageParts: List<AssistantImagePartDto>?,
    ): List<ChatMessagePart> {
        return imageParts.orEmpty()
            .mapNotNull(::extractAssistantImagePart)
            .distinctBy { "${it.type}:${it.uri}" }
    }

    private fun extractAssistantImagePart(
        imagePart: AssistantImagePartDto,
    ): ChatMessagePart? {
        val directUrl = imagePart.imageUrl?.url
            ?: imagePart.url
        if (!directUrl.isNullOrBlank()) {
            return imageMessagePart(
                uri = directUrl,
                mimeType = imagePart.mimeType.orEmpty(),
            )
        }
        val base64 = imagePart.b64Json.orEmpty()
        if (base64.isBlank()) {
            return null
        }
        val mimeType = imagePart.mimeType.orEmpty().ifBlank { "image/png" }
        return imageMessagePart(
            uri = "data:$mimeType;base64,$base64",
            mimeType = mimeType,
        )
    }

    private fun extractAssistantContentParts(content: Any?): List<ChatMessagePart> {
        return when (content) {
            is List<*> -> content.flatMap(::extractAssistantContentPart)
            is Map<*, *> -> extractAssistantContentPart(content)
            is String -> listOf(textMessagePart(content))
            else -> emptyList()
        }
    }

    private fun extractAssistantContentPart(contentPart: Any?): List<ChatMessagePart> {
        return when (contentPart) {
            is Map<*, *> -> {
                val type = contentPart["type"]?.toString().orEmpty()
                when (type) {
                    "text" -> {
                        val text = contentPart["text"]?.toString().orEmpty().trim()
                        if (text.isBlank()) emptyList() else listOf(textMessagePart(text))
                    }

                    "image_url" -> extractAssistantImagePart(contentPart)?.let(::listOf).orEmpty()
                    else -> emptyList()
                }
            }

            is String -> listOf(textMessagePart(contentPart))
            else -> emptyList()
        }
    }

    private fun extractAssistantImagePart(contentPart: Map<*, *>): ChatMessagePart? {
        val imageUrl = contentPart["image_url"]
        if (imageUrl !is Map<*, *>) {
            return null
        }
        val url = imageUrl["url"]?.toString().orEmpty().trim()
        if (url.isBlank()) {
            return null
        }
        return imageMessagePart(
            uri = url,
            mimeType = imageUrl["mime_type"]?.toString().orEmpty(),
        )
    }

    private fun mergeAssistantParts(
        contentParts: List<ChatMessagePart>,
        imageParts: List<ChatMessagePart>,
    ): List<ChatMessagePart> {
        val normalizedContentParts = normalizeChatMessageParts(contentParts)
        if (normalizedContentParts.isEmpty()) {
            return imageParts
        }
        if (imageParts.isEmpty()) {
            return normalizedContentParts
        }
        return normalizeChatMessageParts(normalizedContentParts + imageParts)
    }

    private fun extractContentText(content: Any?): String {
        return when (content) {
            is String -> content
            is List<*> -> content.joinToString(separator = "\n\n") { part ->
                extractContentPartText(part)
            }.trim()
            is Map<*, *> -> extractContentPartText(content)
            else -> ""
        }
    }

    private fun extractContentPartText(contentPart: Any?): String {
        return when (contentPart) {
            is String -> contentPart
            is Map<*, *> -> {
                when (contentPart["type"]?.toString().orEmpty()) {
                    "text" -> contentPart["text"]?.toString().orEmpty()
                    else -> ""
                }
            }

            else -> ""
        }
    }

    private fun splitThinkTaggedContent(content: String): ParsedAssistantOutput {
        if (!content.contains("<think>")) {
            return ParsedAssistantOutput(
                content = content.trim(),
                reasoning = "",
            )
        }

        val reasoning = thinkTagRegex.findAll(content)
            .mapNotNull { match ->
                match.groupValues.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            }
            .joinToString(separator = "\n\n")

        val visibleContent = content
            .replace(thinkTagRegex, "")
            .replace(closingThinkTagRegex, "")
            .trim()

        return ParsedAssistantOutput(
            content = visibleContent,
            reasoning = reasoning,
        )
    }

    private fun splitThinkTaggedParts(parts: List<ChatMessagePart>): ParsedAssistantOutput {
        val normalizedParts = normalizeChatMessageParts(parts)
        val splitText = splitThinkTaggedContent(normalizedParts.toPlainText())
        if (splitText.reasoning.isBlank()) {
            return ParsedAssistantOutput(
                content = normalizedParts.toContentMirror(),
                reasoning = "",
                parts = normalizedParts,
            )
        }

        val visibleParts = normalizeChatMessageParts(
            buildList {
                if (splitText.content.isNotBlank()) {
                    add(textMessagePart(splitText.content))
                }
                addAll(normalizedParts.filter { it.type == ChatMessagePartType.IMAGE })
            },
        )
        return ParsedAssistantOutput(
            content = visibleParts.toContentMirror(),
            reasoning = splitText.reasoning,
            parts = visibleParts,
        )
    }
}

internal class ThinkTagStreamParser {
    private val pending = StringBuilder()
    private var insideThinking = false

    fun shouldConsume(delta: String): Boolean {
        return insideThinking ||
            pending.isNotEmpty() ||
            '<' in delta ||
            '>' in delta
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun consume(delta: String): ParsedAssistantOutput {
        if (delta.isEmpty()) {
            return ParsedAssistantOutput(content = "", reasoning = "")
        }

        pending.append(delta)
        val content = StringBuilder()
        val reasoning = StringBuilder()

        while (pending.isNotEmpty()) {
            if (insideThinking) {
                val closingIndex = pending.indexOf("</think>")
                if (closingIndex >= 0) {
                    reasoning.append(pending.substring(0, closingIndex))
                    pending.delete(0, closingIndex + "</think>".length)
                    insideThinking = false
                } else {
                    val safeLength = pending.length - ("</think>".length - 1)
                    if (safeLength > 0) {
                        reasoning.append(pending.substring(0, safeLength))
                        pending.delete(0, safeLength)
                    }
                    break
                }
            } else {
                val openingIndex = pending.indexOf("<think>")
                if (openingIndex >= 0) {
                    if (openingIndex > 0) {
                        content.append(pending.substring(0, openingIndex))
                    }
                    pending.delete(0, openingIndex + "<think>".length)
                    insideThinking = true
                } else {
                    val safeLength = pending.length - ("<think>".length - 1)
                    if (safeLength > 0) {
                        content.append(pending.substring(0, safeLength))
                        pending.delete(0, safeLength)
                    }
                    break
                }
            }
        }

        return ParsedAssistantOutput(
            content = content.toString(),
            reasoning = reasoning.toString(),
        )
    }

    fun flush(): ParsedAssistantOutput {
        if (pending.isEmpty()) {
            return ParsedAssistantOutput(content = "", reasoning = "")
        }

        val trailing = pending.toString()
        pending.clear()
        return if (insideThinking) {
            insideThinking = false
            ParsedAssistantOutput(content = "", reasoning = trailing)
        } else {
            ParsedAssistantOutput(content = trailing, reasoning = "")
        }
    }
}
