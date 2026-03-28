package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.isValidTransferPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.transferMessagePart
import java.util.UUID

internal object GatewaySpecialPlaySupport {
    const val defaultSpecialPlayPrompt: String = """
你支持一个仅限聊天内展示的“转账”特殊玩法。
- 当你决定给用户转账时，必须输出一条 XML 自闭合标签：
  <transfer id="唯一ID" direction="assistant_to_user" amount="88.00" counterparty="用户" note="备注" />
- 当你确认已经收下用户之前发来的转账时，必须输出：
  <transfer-update ref="之前的转账ID" status="received" />
- 除了 transfer 和 transfer-update，不要输出任何其他玩法标签。
- 标签不要放进代码块，不要解释标签语法本身。
- 可以同时输出正常对话正文，界面会把标签渲染成玩法卡片。
"""

    private val transferTagRegex = Regex("""<transfer\s+([^>]+)/>""")
    private val transferUpdateTagRegex = Regex("""<transfer-update\s+([^>]+)/>""")
    private val xmlAttributeRegex = Regex("(\\w+)=\"([^\"]*)\"")

    fun shouldInjectSpecialPlayPrompt(messages: List<ChatMessage>): Boolean {
        if (messages.isEmpty()) {
            return false
        }
        val latestUserMessage = messages.lastOrNull { it.role == com.example.myapplication.model.MessageRole.USER }
        if (latestUserMessage != null && latestUserMessage.hasAnyTransferPart()) {
            return true
        }
        return messages.any { it.hasPendingTransferPart() }
    }

    fun buildSpecialPlayPrompt(
        part: ChatMessagePart,
    ): String? {
        if (!part.isValidTransferPart()) {
            return null
        }
        val direction = when (part.specialDirection) {
            TransferDirection.USER_TO_ASSISTANT -> "user_to_assistant"
            TransferDirection.ASSISTANT_TO_USER -> "assistant_to_user"
            null -> return null
        }
        val status = when (part.specialStatus) {
            TransferStatus.PENDING -> "pending"
            TransferStatus.RECEIVED -> "received"
            null -> return null
        }
        return buildString {
            append("<transfer")
            append(" id=\"").append(part.specialId.escapeXmlAttribute()).append('"')
            append(" direction=\"").append(direction).append('"')
            append(" amount=\"").append(part.specialAmount.escapeXmlAttribute()).append('"')
            append(" counterparty=\"").append(part.specialCounterparty.escapeXmlAttribute()).append('"')
            append(" note=\"").append(part.specialNote.escapeXmlAttribute()).append('"')
            append(" status=\"").append(status).append("\" />")
        }
    }

    fun parseAssistantSpecialOutput(
        content: String,
        existingParts: List<ChatMessagePart>,
    ): ParsedAssistantSpecialOutput {
        val preservedNonTextParts = normalizeChatMessageParts(existingParts)
            .filter { part -> part.type != ChatMessagePartType.TEXT }
        if (content.isBlank()) {
            return ParsedAssistantSpecialOutput(
                content = "",
                parts = preservedNonTextParts,
            )
        }

        val renderedParts = mutableListOf<ChatMessagePart>()
        val transferUpdates = mutableListOf<TransferUpdateDirective>()
        var cursor = 0
        val matches = buildList {
            addAll(transferTagRegex.findAll(content))
            addAll(transferUpdateTagRegex.findAll(content))
        }.sortedBy { it.range.first }

        matches.forEach { match ->
            val range = match.range
            if (range.first > cursor) {
                val prefix = content.substring(cursor, range.first)
                if (prefix.isNotBlank()) {
                    renderedParts += textMessagePart(prefix.trim())
                }
            }

            val rawAttributes = match.groupValues.getOrNull(1).orEmpty()
            val attributes = rawAttributes.parseXmlAttributes()
            when {
                match.value.startsWith("<transfer-update") -> {
                    val refId = attributes["ref"].orEmpty().trim()
                    val status = attributes["status"].orEmpty().trim()
                    if (refId.isNotBlank() && status == "received") {
                        transferUpdates += TransferUpdateDirective(
                            refId = refId,
                            status = TransferStatus.RECEIVED,
                        )
                    }
                }

                match.value.startsWith("<transfer") -> {
                    val direction = when (attributes["direction"].orEmpty().trim()) {
                        "assistant_to_user" -> TransferDirection.ASSISTANT_TO_USER
                        "user_to_assistant" -> TransferDirection.USER_TO_ASSISTANT
                        else -> null
                    }
                    if (direction != null) {
                        transferMessagePart(
                            id = attributes["id"].orEmpty().ifBlank { UUID.randomUUID().toString() },
                            direction = direction,
                            status = when (attributes["status"].orEmpty().trim()) {
                                "received" -> TransferStatus.RECEIVED
                                else -> TransferStatus.PENDING
                            },
                            counterparty = attributes["counterparty"].orEmpty().ifBlank { "对方" },
                            amount = attributes["amount"].orEmpty(),
                            note = attributes["note"].orEmpty(),
                        ).takeIf { it.isValidTransferPart() }?.let(renderedParts::add)
                    }
                }
            }

            cursor = range.last + 1
        }

        if (cursor < content.length) {
            val suffix = content.substring(cursor)
            if (suffix.isNotBlank()) {
                renderedParts += textMessagePart(suffix.trim())
            }
        }

        val normalizedVisibleParts = normalizeChatMessageParts(renderedParts)
        return ParsedAssistantSpecialOutput(
            content = normalizedVisibleParts.toPlainText(),
            parts = normalizeChatMessageParts(normalizedVisibleParts + preservedNonTextParts),
            transferUpdates = transferUpdates,
        )
    }

    private fun String.parseXmlAttributes(): Map<String, String> {
        return xmlAttributeRegex.findAll(this)
            .associate { match ->
                match.groupValues[1] to match.groupValues[2]
            }
    }

    private fun String.escapeXmlAttribute(): String {
        return this
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun ChatMessage.hasAnyTransferPart(): Boolean {
        return normalizeChatMessageParts(parts).any { part ->
            part.type == ChatMessagePartType.SPECIAL && part.isValidTransferPart()
        }
    }

    private fun ChatMessage.hasPendingTransferPart(): Boolean {
        return normalizeChatMessageParts(parts).any { part ->
            part.type == ChatMessagePartType.SPECIAL &&
                part.isValidTransferPart() &&
                part.specialStatus == TransferStatus.PENDING
        }
    }
}
