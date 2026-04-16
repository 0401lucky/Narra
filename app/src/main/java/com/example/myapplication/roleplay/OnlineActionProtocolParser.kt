package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.aiPhotoMessagePart
import com.example.myapplication.model.decodeOnlineThoughtText
import com.example.myapplication.model.emojiMessagePart
import com.example.myapplication.model.isActionPart
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.locationMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.pokeMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.thoughtMessagePart
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.toSpecialPlayCopyText
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.transferResultText
import com.example.myapplication.model.videoCallMessagePart
import com.example.myapplication.model.voiceMessageActionPart
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal data class OnlineActionProtocolParseResult(
    val parts: List<ChatMessagePart>,
    val directives: List<OnlineActionDirective> = emptyList(),
)

internal sealed class OnlineActionDirective {
    data object RecallPreviousAssistant : OnlineActionDirective()
    data class UpdateTransferStatus(
        val status: TransferStatus,
        val refId: String = "",
    ) : OnlineActionDirective()
}

internal object OnlineActionProtocolParser {
    fun parse(
        rawContent: String,
        characterName: String,
    ): OnlineActionProtocolParseResult? {
        // 优先尝试标准 JSON 数组解析
        val candidate = prepareProtocolArrayCandidate(rawContent)
        if (candidate != null) {
            val parsedRoot = runCatching { JsonParser.parseString(candidate) }.getOrNull()
            if (parsedRoot != null && parsedRoot.isJsonArray) {
                return parseArray(
                    array = parsedRoot.asJsonArray,
                    characterName = characterName,
                )
            }
        }
        return trySingleJsonObject(rawContent, characterName)
    }

    /**
     * 在已经确定是线上模式的场景下调用。先走标准 parse，若失败则将内容拆成纯文本气泡。
     * 不要在"判断是否为线上格式"的代码路径中使用此方法。
     */
    fun parseWithFallback(
        rawContent: String,
        characterName: String,
    ): OnlineActionProtocolParseResult? {
        return parse(rawContent, characterName) ?: fallbackPlainText(rawContent)
    }

    /**
     * 模型有时输出单个 JSON 对象而非数组，如 {"type":"thought","content":"..."} 。
     * 尝试解析为单对象，成功则包装为数组处理。
     */
    private fun trySingleJsonObject(rawContent: String, characterName: String): OnlineActionProtocolParseResult? {
        val stripped = stripMarkdownCodeFence(rawContent)
        val trimmed = stripped.trim()
        val objectCandidate = if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            trimmed
        } else {
            val firstBrace = stripped.indexOf('{')
            val lastBrace = stripped.lastIndexOf('}')
            if (firstBrace != -1 && lastBrace > firstBrace) {
                stripped.substring(firstBrace, lastBrace + 1)
            } else {
                return null
            }
        }
        val parsedObject = runCatching { JsonParser.parseString(objectCandidate) }.getOrNull()
            ?.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?: return null
        val wrappedArray = JsonArray().apply { add(parsedObject) }
        val result = parseArray(wrappedArray, characterName)
        return result.takeIf { it.parts.isNotEmpty() || it.directives.isNotEmpty() }
    }

    /**
     * 当模型输出非 JSON 格式时，将纯文本按段落拆分为独立气泡。
     * 会剥离 Markdown 代码块、XML 标签以及 JSON 字段残留。
     */
    private fun fallbackPlainText(rawContent: String): OnlineActionProtocolParseResult? {
        // 包含协议结构标签时应交给 RoleplayOutputParser 处理，不在此暴力剥掉
        if (Regex("""(?is)<(/?)(dialogue|narration)\b""").containsMatchIn(rawContent)) {
            return null
        }
        val cleaned = rawContent
            .replace(Regex("```[\\s\\S]*?```"), "")
            .replace(Regex("<(?:dialogue|narration|thought|char)[^>]*>"), "")
            .replace(Regex("</(?:dialogue|narration|thought|char)>"), "")
            // 清理 JSON 字段残留：移除 "type":、"content":、"thought": 等键名
            .replace(Regex("""[{}\[\]]"""), "")
            .replace(Regex(""""(?:type|content|thought|message|text|action|description)"\s*:\s*"""), "")
            .replace(Regex("""(?<=^|,)\s*"[^"]+"\s*:\s*"""), "")
            .trim()
        if (cleaned.isBlank()) {
            return null
        }
        val lines = cleaned.split('\n')
            .map { line ->
                line.trim()
                    .removeSurrounding("\"")
                    .trim()
                    .removeSuffix(",")
                    .trim()
            }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return null
        }
        val parts = lines.map { textMessagePart(it) }
        return OnlineActionProtocolParseResult(
            parts = normalizeChatMessageParts(parts),
        )
    }

    fun extractStreamingPreview(rawContent: String): String {
        val candidate = extractCompleteArrayPrefix(stripMarkdownCodeFence(rawContent)) ?: return ""
        val parsedRoot = runCatching { JsonParser.parseString(candidate) }.getOrNull() ?: return ""
        if (!parsedRoot.isJsonArray) {
            return ""
        }
        val result = parseArray(
            array = parsedRoot.asJsonArray,
            characterName = "角色",
        )
        return buildList {
            addAll(result.parts.map { part -> part.toStreamingPreviewText() })
            addAll(result.directives.map { directive -> directive.toPreviewText() })
        }.filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    private fun parseArray(
        array: JsonArray,
        characterName: String,
    ): OnlineActionProtocolParseResult {
        val parts = mutableListOf<ChatMessagePart>()
        val directives = mutableListOf<OnlineActionDirective>()
        array.forEach { element ->
            when {
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    val content = element.asString.trim()
                    if (content.isNotBlank()) {
                        val thoughtContent = extractInlineThoughtContent(content)
                        if (thoughtContent != null) {
                            parts += thoughtMessagePart(thoughtContent)
                        } else {
                            parts += textMessagePart(sanitizeOnlineDialogueText(content))
                        }
                    }
                }

                element.isJsonObject -> {
                    parseObject(
                        item = element.asJsonObject,
                        characterName = characterName,
                        parts = parts,
                        directives = directives,
                    )
                }
            }
        }
        return OnlineActionProtocolParseResult(
            parts = normalizeChatMessageParts(parts),
            directives = directives.distinct(),
        )
    }

    private fun parseObject(
        item: JsonObject,
        characterName: String,
        parts: MutableList<ChatMessagePart>,
        directives: MutableList<OnlineActionDirective>,
    ) {
        when (item.stringValue("type").lowercase()) {
            "reply_to" -> {
                val content = sanitizeOnlineDialogueText(item.stringValue("content"))
                if (content.isNotBlank()) {
                    parts += textMessagePart(
                        text = content,
                        replyToMessageId = item.stringValue("message_id"),
                        replyToPreview = item.stringValue("reply_preview")
                            .ifBlank { item.stringValue("preview") },
                        replyToSpeakerName = item.stringValue("reply_speaker")
                            .ifBlank { item.stringValue("speaker_name") },
                    )
                }
            }

            "thought" -> {
                item.stringValue("content")
                    .takeIf { it.isNotBlank() }
                    ?.let { content ->
                        parts += thoughtMessagePart(content)
                    }
            }

            "recall" -> {
                if (item.stringValue("target").lowercase() == "previous") {
                    directives += OnlineActionDirective.RecallPreviousAssistant
                }
            }

            "emoji" -> {
                item.stringValue("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += emojiMessagePart(description)
                    }
            }

            "voice_message" -> {
                item.stringValue("content")
                    .takeIf { it.isNotBlank() }
                    ?.let { content ->
                        parts += voiceMessageActionPart(content)
                    }
            }

            "ai_photo" -> {
                item.stringValue("description")
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += aiPhotoMessagePart(description)
                    }
            }

            "location" -> {
                val locationName = item.stringValue("locationName")
                    .ifBlank { item.stringValue("name") }
                    .ifBlank { item.stringValue("location_name") }
                if (locationName.isNotBlank()) {
                    parts += locationMessagePart(
                        locationName = locationName,
                        coordinates = item.stringValue("coordinates"),
                        address = item.stringValue("address"),
                    )
                }
            }

            "transfer" -> {
                val amount = item.numericStringValue("amount")
                if (amount.isNotBlank()) {
                    parts += transferMessagePart(
                        direction = TransferDirection.ASSISTANT_TO_USER,
                        counterparty = characterName,
                        amount = amount,
                        note = item.stringValue("note"),
                    )
                }
            }

            "transfer_action" -> {
                val status = when (item.stringValue("action").lowercase()) {
                    "accept", "accepted", "receive", "received", "收下", "收款" -> TransferStatus.RECEIVED
                    "reject", "rejected", "return", "returned", "refuse", "退回", "不收", "拒绝" -> TransferStatus.REJECTED
                    else -> null
                }
                if (status != null) {
                    directives += OnlineActionDirective.UpdateTransferStatus(
                        status = status,
                        refId = item.stringValue("ref_id")
                            .ifBlank { item.stringValue("transfer_id") }
                            .ifBlank { item.stringValue("message_id") },
                    )
                }
            }

            "poke" -> {
                parts += pokeMessagePart(
                    target = item.stringValue("target"),
                    suffix = item.stringValue("suffix"),
                )
            }

            "video_call" -> {
                item.stringValue("reason")
                    .takeIf { it.isNotBlank() }
                    ?.let { reason ->
                        parts += videoCallMessagePart(reason)
                    }
            }
        }
    }

    private fun prepareProtocolArrayCandidate(rawContent: String): String? {
        val stripped = stripMarkdownCodeFence(rawContent)
        val candidate = extractFirstCompleteJsonArray(stripped)
            ?: extractCompleteArrayPrefix(stripped)
            ?: return null
        return removeTrailingCommas(candidate).trim()
            .takeIf { it.startsWith("[") && it.endsWith("]") }
    }

    private fun stripMarkdownCodeFence(rawContent: String): String {
        val trimmed = rawContent.trim()
        if (!trimmed.startsWith("```")) {
            return trimmed
        }
        return trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    private fun extractFirstCompleteJsonArray(rawContent: String): String? {
        val startIndex = rawContent.indexOf('[')
        if (startIndex == -1) {
            return null
        }
        var inString = false
        var escaped = false
        var depth = 0
        for (index in startIndex until rawContent.length) {
            val char = rawContent[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '[' -> depth += 1
                ']' -> {
                    depth -= 1
                    if (depth == 0) {
                        return rawContent.substring(startIndex, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractCompleteArrayPrefix(rawContent: String): String? {
        val startIndex = rawContent.indexOf('[')
        if (startIndex == -1) {
            return null
        }
        val source = rawContent.substring(startIndex)
        val items = mutableListOf<String>()
        var itemStart = -1
        var inString = false
        var escaped = false
        var objectDepth = 0
        var nestedArrayDepth = 0
        var index = 1
        while (index < source.length) {
            val char = source[index]
            if (itemStart == -1) {
                when {
                    char.isWhitespace() || char == ',' -> {
                        index += 1
                        continue
                    }
                    char == ']' -> break
                    else -> itemStart = index
                }
            }
            if (!inString && objectDepth == 0 && nestedArrayDepth == 0 && (char == ',' || char == ']')) {
                val item = source.substring(itemStart, index).trim()
                if (item.isNotBlank()) {
                    items += item
                }
                itemStart = -1
                if (char == ']') {
                    break
                }
                index += 1
                continue
            }
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                index += 1
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> objectDepth += 1
                '}' -> objectDepth = (objectDepth - 1).coerceAtLeast(0)
                '[' -> nestedArrayDepth += 1
                ']' -> {
                    if (nestedArrayDepth > 0) {
                        nestedArrayDepth -= 1
                    }
                }
            }
            index += 1
        }
        if (itemStart != -1 && !inString && objectDepth == 0 && nestedArrayDepth == 0) {
            val trailingItem = source.substring(itemStart).trim().removeSuffix("]")
                .trim()
                .removeSuffix(",")
                .trim()
            if (trailingItem.isNotBlank()) {
                items += trailingItem
            }
        }
        if (items.isEmpty()) {
            return null
        }
        return buildString {
            append("[")
            append(items.joinToString(separator = ","))
            append("]")
        }
    }

    private fun removeTrailingCommas(rawContent: String): String {
        val builder = StringBuilder(rawContent.length)
        var inString = false
        var escaped = false
        var index = 0
        while (index < rawContent.length) {
            val char = rawContent[index]
            if (inString) {
                builder.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                index += 1
                continue
            }
            if (char == '"') {
                inString = true
                builder.append(char)
                index += 1
                continue
            }
            if (char == ',') {
                val nextIndex = nextNonWhitespaceIndex(rawContent, index + 1)
                if (nextIndex != -1 && rawContent[nextIndex] in charArrayOf('}', ']')) {
                    index += 1
                    continue
                }
            }
            builder.append(char)
            index += 1
        }
        return builder.toString()
    }

    private fun nextNonWhitespaceIndex(rawContent: String, startIndex: Int): Int {
        for (index in startIndex until rawContent.length) {
            if (!rawContent[index].isWhitespace()) {
                return index
            }
        }
        return -1
    }

    // 模型有时把心声作为纯字符串输出（如 "【心声】谁也没你重要"），
    // 而不是正确的 {"type":"thought","content":"..."}。
    // 在这里做前缀探测，让 parseArray 对字符串元素也能生成 thoughtMessagePart。
    private val inlineThoughtPrefixes = listOf("【心声】", "心声：", "心声:")

    // 模型有时将引用候选 ID 残留在对话文本中（如 "ID:1010 刚才班长看你的眼神"），
    // 这些 ID 是 prompt 中的引用短 ID，不应出现在最终文本里。
    private val referenceIdPrefixRegex = Regex("""^ID:\d+\s*""")

    private fun extractInlineThoughtContent(text: String): String? {
        val prefix = inlineThoughtPrefixes.firstOrNull { text.startsWith(it) }
            ?: return null
        return text.removePrefix(prefix).trim().takeIf { it.isNotBlank() }
    }

    private fun sanitizeOnlineDialogueText(text: String): String {
        return referenceIdPrefixRegex.replace(text.trim(), "").trim()
    }

    private fun JsonObject.stringValue(key: String): String {
        return runCatching { get(key)?.takeIf(JsonElement::isJsonPrimitive)?.asString.orEmpty() }
            .getOrDefault("")
            .trim()
    }

    private fun JsonObject.numericStringValue(key: String): String {
        val element = get(key) ?: return ""
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asNumber.toString()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.trim()
            else -> ""
        }
    }

    private fun ChatMessagePart.toActionOrSpecialCopyText(): String {
        return when {
            isActionPart() -> toActionCopyText()
            isSpecialPlayPart() -> toSpecialPlayCopyText()
            else -> text.trim()
        }
    }

    private fun ChatMessagePart.toStreamingPreviewText(): String {
        return when {
            isOnlineThoughtPart() -> "心声：${decodeOnlineThoughtText(text)}"
            text.isNotBlank() -> text.trim()
            else -> toActionOrSpecialCopyText()
        }
    }

    private fun OnlineActionDirective.toPreviewText(): String {
        return when (this) {
            OnlineActionDirective.RecallPreviousAssistant -> "已撤回上一条回复"
            is OnlineActionDirective.UpdateTransferStatus -> status.transferResultText()
        }
    }
}
