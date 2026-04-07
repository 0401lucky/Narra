package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.isValidTransferPart
import com.example.myapplication.model.isValidSpecialPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.taskMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.transferMessagePart
import java.util.UUID

internal object GatewaySpecialPlaySupport {
    const val defaultSpecialPlayPrompt: String = """
你支持一个仅限聊天内展示的“特殊玩法卡片”协议，仅在互动确实适合可视化展示时使用。
- 你可以输出 XML 自闭合标签：
  <play id="唯一ID" type="transfer|invite|gift|task" ... />
- 字段要求：
  1. 转账：type="transfer"，并带 direction="assistant_to_user|user_to_assistant" amount="88.00" counterparty="用户" note="备注" status="pending|received"
  2. 邀约：type="invite"，并带 target="用户" place="天台" time="今晚九点" note="附言"
  3. 礼物：type="gift"，并带 target="用户" item="黑胶唱片" note="附言"
  4. 委托：type="task"，并带 title="寻找钥匙" objective="在旧图书馆找到铜钥匙" reward="一个答案" deadline="天亮前"
- 当你确认已经收下用户之前发来的转账时，输出：
  <play-update ref="之前的转账ID" status="received" />
- 非转账玩法不要输出 update 标签。
- 标签不要放进代码块，不要解释标签语法本身。
- 可以同时输出正常对话正文，界面会把标签渲染成玩法卡片。
"""

    private val playTagRegex = Regex("""<play\s+([^>]+)/>""")
    private val playUpdateTagRegex = Regex("""<play-update\s+([^>]+)/>""")
    private val xmlAttributeRegex = Regex("(\\w+)=\"([^\"]*)\"")

    fun shouldInjectSpecialPlayPrompt(messages: List<ChatMessage>): Boolean {
        return messages.any { it.hasAnySpecialPlayPart() }
    }

    fun buildSpecialPlayPrompt(
        part: ChatMessagePart,
    ): String? {
        if (!part.isValidSpecialPart()) {
            return null
        }
        return buildString {
            append("<play")
            append(" id=\"").append(part.specialId.escapeXmlAttribute()).append('"')
            append(" type=\"").append(part.specialType?.protocolValue.orEmpty().escapeXmlAttribute()).append('"')
            when (part.specialType) {
                ChatSpecialType.TRANSFER -> {
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
                    append(" direction=\"").append(direction).append('"')
                    append(" amount=\"").append(part.specialAmount.escapeXmlAttribute()).append('"')
                    append(" counterparty=\"").append(part.specialCounterparty.escapeXmlAttribute()).append('"')
                    append(" note=\"").append(part.specialNote.escapeXmlAttribute()).append('"')
                    append(" status=\"").append(status).append('"')
                }

                ChatSpecialType.INVITE -> {
                    append(" target=\"").append(part.specialMetadataValue("target").escapeXmlAttribute()).append('"')
                    append(" place=\"").append(part.specialMetadataValue("place").escapeXmlAttribute()).append('"')
                    append(" time=\"").append(part.specialMetadataValue("time").escapeXmlAttribute()).append('"')
                    append(" note=\"").append(part.specialMetadataValue("note").escapeXmlAttribute()).append('"')
                }

                ChatSpecialType.GIFT -> {
                    append(" target=\"").append(part.specialMetadataValue("target").escapeXmlAttribute()).append('"')
                    append(" item=\"").append(part.specialMetadataValue("item").escapeXmlAttribute()).append('"')
                    append(" note=\"").append(part.specialMetadataValue("note").escapeXmlAttribute()).append('"')
                }

                ChatSpecialType.TASK -> {
                    append(" title=\"").append(part.specialMetadataValue("title").escapeXmlAttribute()).append('"')
                    append(" objective=\"").append(part.specialMetadataValue("objective").escapeXmlAttribute()).append('"')
                    append(" reward=\"").append(part.specialMetadataValue("reward").escapeXmlAttribute()).append('"')
                    append(" deadline=\"").append(part.specialMetadataValue("deadline").escapeXmlAttribute()).append('"')
                }

                null -> return null
            }
            append(" />")
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
            addAll(playTagRegex.findAll(content))
            addAll(playUpdateTagRegex.findAll(content))
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
                match.value.startsWith("<play-update") -> {
                    val refId = attributes["ref"].orEmpty().trim()
                    val status = attributes["status"].orEmpty().trim()
                    if (refId.isNotBlank() && status == "received") {
                        transferUpdates += TransferUpdateDirective(
                            refId = refId,
                            status = TransferStatus.RECEIVED,
                        )
                    }
                }

                match.value.startsWith("<play") -> {
                    parsePlayPart(attributes)?.let(renderedParts::add)
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

    private fun parsePlayPart(attributes: Map<String, String>): ChatMessagePart? {
        val id = attributes["id"].orEmpty().ifBlank { UUID.randomUUID().toString() }
        return when (ChatSpecialType.fromProtocolValue(attributes["type"].orEmpty())) {
            ChatSpecialType.TRANSFER -> {
                val direction = when (attributes["direction"].orEmpty().trim()) {
                    "assistant_to_user" -> TransferDirection.ASSISTANT_TO_USER
                    "user_to_assistant" -> TransferDirection.USER_TO_ASSISTANT
                    else -> null
                }
                if (direction == null) {
                    null
                } else {
                    transferMessagePart(
                        id = id,
                        direction = direction,
                        status = when (attributes["status"].orEmpty().trim()) {
                            "received" -> TransferStatus.RECEIVED
                            else -> TransferStatus.PENDING
                        },
                        counterparty = attributes["counterparty"].orEmpty().ifBlank { "对方" },
                        amount = attributes["amount"].orEmpty(),
                        note = attributes["note"].orEmpty(),
                    ).takeIf { it.isValidTransferPart() }
                }
            }

            ChatSpecialType.INVITE -> inviteMessagePart(
                id = id,
                target = attributes["target"].orEmpty(),
                place = attributes["place"].orEmpty(),
                time = attributes["time"].orEmpty(),
                note = attributes["note"].orEmpty(),
            ).takeIf(ChatMessagePart::isValidSpecialPart)

            ChatSpecialType.GIFT -> giftMessagePart(
                id = id,
                target = attributes["target"].orEmpty(),
                item = attributes["item"].orEmpty(),
                note = attributes["note"].orEmpty(),
            ).takeIf(ChatMessagePart::isValidSpecialPart)

            ChatSpecialType.TASK -> taskMessagePart(
                id = id,
                title = attributes["title"].orEmpty(),
                objective = attributes["objective"].orEmpty(),
                reward = attributes["reward"].orEmpty(),
                deadline = attributes["deadline"].orEmpty(),
            ).takeIf(ChatMessagePart::isValidSpecialPart)

            null -> null
        }
    }

    private fun ChatMessage.hasAnySpecialPlayPart(): Boolean {
        return normalizeChatMessageParts(parts).any { part ->
            part.type == ChatMessagePartType.SPECIAL && part.isValidSpecialPart()
        }
    }
}
