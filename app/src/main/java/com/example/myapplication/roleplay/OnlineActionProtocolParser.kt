package com.example.myapplication.roleplay

import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.PunishIntensity
import com.example.myapplication.model.aiPhotoMessagePart
import com.example.myapplication.model.decodeOnlineThoughtText
import com.example.myapplication.model.dedupeRepeatedAiPhotoParts
import com.example.myapplication.model.emojiMessagePart
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.isActionPart
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.isSpecialPlayPart
import com.example.myapplication.model.locationMessagePart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.pokeMessagePart
import com.example.myapplication.model.punishMessagePart
import com.example.myapplication.model.taskMessagePart
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
    private val specialPlayTagPattern = Regex("""(?is)<play(?:-update)?\b[^>]*?/>""")
    private val protocolAttributeNames = listOf(
        "id",
        "type",
        "target",
        "place",
        "time",
        "note",
        "description",
        "desc",
        "caption",
        "prompt",
        "content",
        "text",
        "message",
        "dialogue",
        "speech",
        "action",
        "action_type",
        "actionType",
        "kind",
        "event",
        "message_id",
        "reply_preview",
        "preview",
        "reply_speaker",
        "speaker_name",
        "op",
        "path",
        "value",
        "delta",
        "from",
        "title",
        "name",
        "objective",
        "reward",
        "deadline",
        "method",
        "count",
        "intensity",
        "reason",
        "item",
        "counterparty",
        "amount",
        "direction",
        "status",
        "ref",
        "ref_id",
        "transfer_id",
        "duration_seconds",
        "durationSeconds",
        "locationName",
        "location_name",
        "location",
        "coordinates",
        "address",
        "suffix",
    )
    private val protocolAttributeNamePattern = protocolAttributeNames.joinToString(separator = "|")
    private val malformedProtocolAttributePattern = Regex(
        """(?i)\b(?:$protocolAttributeNamePattern)\s*=\s*(?:"[^"]*"|'[^']*'|[^<>\s,，/]*)?""",
    )
    private val malformedProtocolColonKeyPattern = Regex(
        """(?i)\b(?:$protocolAttributeNamePattern)\s*:\s*""",
    )
    private val protocolAttributeResidualLinePattern = Regex(
        """(?i)^\s*(?:$protocolAttributeNamePattern)\s*=\s*.*$""",
    )
    private val protocolPunctuationResidualLinePattern = Regex("""^[\s"'=:：/\\,，<>-]+$""")

    fun parse(
        rawContent: String,
        characterName: String,
    ): OnlineActionProtocolParseResult? {
        // 优先尝试标准 JSON 数组解析
        val candidate = prepareProtocolArrayCandidate(rawContent)
        if (candidate != null) {
            val parsedRoot = parseJsonElement(candidate)
            if (parsedRoot != null && parsedRoot.isJsonArray) {
                return parseArray(
                    array = parsedRoot.asJsonArray,
                    characterName = characterName,
                ).takeIf { it.parts.isNotEmpty() || it.directives.isNotEmpty() }
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
     * 群聊首版保留真实文字气泡、语音和照片。
     * 心声、状态栏、转账等更偏单聊或系统沉浸的动作仍会被丢弃，
     * 避免群聊里混入不可渲染或不合群聊语境的协议残留。
     */
    fun parseGroupTextOnlyWithFallback(
        rawContent: String,
        characterName: String,
    ): OnlineActionProtocolParseResult? {
        val parsed = parse(rawContent, characterName)
        if (parsed != null) {
            return parsed.toGroupAllowed()
        }
        return fallbackGroupPlainText(rawContent)
    }

    fun extractGroupTextStreamingPreview(rawContent: String): String {
        val candidate = extractCompleteArrayPrefix(
            stripMarkdownCodeFence(rawContent).normalizeLooseJsonQuoteChars(),
        ) ?: return ""
        val parsedRoot = parseJsonElement(candidate) ?: return ""
        if (!parsedRoot.isJsonArray) {
            return ""
        }
        val result = parseArray(
            array = parsedRoot.asJsonArray,
            characterName = "角色",
        ).toGroupAllowed() ?: return ""
        return result.parts.map { part -> part.toStreamingPreviewText() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    /**
     * 模型有时输出单个 JSON 对象而非数组，如 {"type":"thought","content":"..."} 。
     * 尝试解析为单对象，成功则包装为数组处理。
     */
    private fun trySingleJsonObject(rawContent: String, characterName: String): OnlineActionProtocolParseResult? {
        val stripped = stripMarkdownCodeFence(rawContent).normalizeLooseJsonQuoteChars()
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
        val parsedObject = parseJsonElement(objectCandidate)
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
            .stripOnlineControlArtifactSections()
            .replace(Regex("```[\\s\\S]*?```"), "")
            // 线上模式中的特殊玩法标签由 GatewaySpecialPlaySupport 负责转成卡片，不应原样显示到文本气泡里
            .replace(specialPlayTagPattern, "\n")
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
            .map(::sanitizeProtocolResidualTextLine)
            .flatMap(::splitFallbackBubbleText)
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return null
        }
        val parts = lines.map { textMessagePart(it) }
        return OnlineActionProtocolParseResult(
            parts = normalizeChatMessageParts(parts),
        )
    }

    private fun fallbackGroupPlainText(rawContent: String): OnlineActionProtocolParseResult? {
        if (looksLikeGroupForbiddenOutput(rawContent)) {
            return null
        }
        val fallback = fallbackPlainText(rawContent) ?: return null
        val textParts = fallback.parts
            .filter { part -> part.type == ChatMessagePartType.TEXT }
            .mapNotNull { part ->
                val text = part.text.trim()
                text.takeIf { it.isNotBlank() && !looksLikeNarrationForGroup(it) }
                    ?.let { part.copy(text = it.take(MAX_GROUP_TEXT_FALLBACK_CHARS)) }
            }
        if (textParts.isEmpty()) {
            return null
        }
        return OnlineActionProtocolParseResult(parts = normalizeChatMessageParts(textParts))
    }

    fun extractStreamingPreview(rawContent: String): String {
        val candidate = extractCompleteArrayPrefix(
            stripMarkdownCodeFence(rawContent).normalizeLooseJsonQuoteChars(),
        ) ?: return ""
        val parsedRoot = parseJsonElement(candidate) ?: return ""
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

    fun extractFallbackStreamingPreview(rawContent: String): String {
        val trimmed = stripMarkdownCodeFence(rawContent).trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            return ""
        }
        val result = fallbackPlainText(rawContent) ?: return ""
        return result.parts.map { part -> part.toStreamingPreviewText() }
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    fun sanitizeProtocolResidualText(rawContent: String): String {
        return rawContent.lines()
            .map(::sanitizeProtocolResidualTextLine)
            .filter { it.isNotBlank() }
            .joinToString(separator = "\n")
            .trim()
    }

    fun isProtocolResidualText(rawContent: String): Boolean {
        val normalized = sanitizeProtocolResidualText(rawContent)
        return normalized.isBlank() && rawContent.trim().isNotBlank()
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
            parts = normalizeChatMessageParts(dedupeRepeatedAiPhotoParts(parts)),
            directives = directives.distinct(),
        )
    }

    private fun parseObject(
        item: JsonObject,
        characterName: String,
        parts: MutableList<ChatMessagePart>,
        directives: MutableList<OnlineActionDirective>,
    ) {
        when (item.protocolType()) {
            "reply_to" -> {
                val content = sanitizeOnlineDialogueText(item.contentValue())
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
                item.contentValue()
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
                item.descriptionValue()
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += emojiMessagePart(description)
                    }
            }

            "voice_message" -> {
                item.contentValue()
                    .takeIf { it.isNotBlank() }
                    ?.let { content ->
                        val durationSeconds = item.numericStringValue("duration_seconds")
                            .ifBlank { item.numericStringValue("durationSeconds") }
                            .toIntOrNull()
                        parts += voiceMessageActionPart(
                            content = content,
                            durationSeconds = durationSeconds,
                        )
                    }
            }

            "ai_photo" -> {
                item.descriptionValue()
                    .takeIf { it.isNotBlank() }
                    ?.let { description ->
                        parts += aiPhotoMessagePart(description)
                    }
            }

            "location" -> {
                val locationName = item.firstStringValue(
                    "locationName",
                    "name",
                    "location_name",
                    "place",
                    "location",
                )
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

            // AI 主动输出的特殊玩法类型
            "task", "委托" -> {
                val title = item.stringValue("title")
                    .ifBlank { item.stringValue("name") }
                val objective = item.stringValue("objective")
                    .ifBlank { item.contentValue() }
                    .ifBlank { item.stringValue("description") }
                if (title.isNotBlank() && objective.isNotBlank()) {
                    parts += taskMessagePart(
                        title = title,
                        objective = objective,
                        reward = item.stringValue("reward"),
                        deadline = item.stringValue("deadline"),
                    )
                }
            }

            "invite", "邀约" -> {
                val place = item.stringValue("place")
                    .ifBlank { item.stringValue("location") }
                val time = item.stringValue("time")
                if (place.isNotBlank() && time.isNotBlank()) {
                    parts += inviteMessagePart(
                        target = item.stringValue("target").ifBlank { characterName },
                        place = place,
                        time = time,
                        note = item.stringValue("note"),
                    )
                }
            }

            "gift", "礼物" -> {
                val itemName = item.stringValue("item")
                    .ifBlank { item.stringValue("name") }
                    .ifBlank { item.stringValue("gift") }
                if (itemName.isNotBlank()) {
                    parts += giftMessagePart(
                        target = item.stringValue("target").ifBlank { "用户" },
                        item = itemName,
                        note = item.stringValue("note"),
                    )
                }
            }

            "punish", "惩罚" -> {
                val method = item.stringValue("method")
                    .ifBlank { item.contentValue() }
                if (method.isNotBlank()) {
                    parts += punishMessagePart(
                        method = method,
                        count = item.stringValue("count").ifBlank { "1" },
                        intensity = PunishIntensity.fromStorageValue(
                            item.stringValue("intensity"),
                        ) ?: PunishIntensity.MEDIUM,
                        reason = item.stringValue("reason"),
                        note = item.stringValue("note"),
                    )
                }
            }

            // 未知类型兜底：尝试提取 content 作为纯文本，避免内容丢失
            else -> {
                val content = item.contentValue()
                if (content.isNotBlank()) {
                    parts += textMessagePart(sanitizeOnlineDialogueText(content))
                }
            }
        }
    }

    private fun prepareProtocolArrayCandidate(rawContent: String): String? {
        val stripped = stripMarkdownCodeFence(rawContent).normalizeLooseJsonQuoteChars()
        val candidate = extractFirstCompleteJsonArray(stripped)
            ?: extractCompleteArrayPrefix(stripped)
            ?: return null
        return removeTrailingCommas(candidate).trim()
            .takeIf { it.startsWith("[") && it.endsWith("]") }
    }

    private fun parseJsonElement(candidate: String): JsonElement? {
        val strictCandidate = removeTrailingCommas(candidate).trim()
        return runCatching { JsonParser.parseString(strictCandidate) }.getOrNull()
            ?: repairLooseJsonCandidate(strictCandidate)
                .takeIf { it != strictCandidate }
                ?.let { repaired ->
                    runCatching { JsonParser.parseString(repaired) }.getOrNull()
                }
    }

    private fun repairLooseJsonCandidate(candidate: String): String {
        return candidate
            .normalizeLooseJsonQuoteChars()
            .replaceStructuralFullWidthPunctuation()
            .quoteUnquotedObjectKeys()
            .convertSingleQuotedStringsToJsonStrings()
            .let(::removeTrailingCommas)
            .trim()
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
        var quoteChar: Char? = null
        var escaped = false
        var depth = 0
        for (index in startIndex until rawContent.length) {
            val char = rawContent[index]
            if (quoteChar != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quoteChar -> quoteChar = null
                }
                continue
            }
            when (char) {
                '"', '\'' -> quoteChar = char
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
        var quoteChar: Char? = null
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
            if (quoteChar == null && objectDepth == 0 && nestedArrayDepth == 0 && (char == ',' || char == ']')) {
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
            if (quoteChar != null) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quoteChar -> quoteChar = null
                }
                index += 1
                continue
            }
            when (char) {
                '"', '\'' -> quoteChar = char
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
        if (itemStart != -1 && quoteChar == null && objectDepth == 0 && nestedArrayDepth == 0) {
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
        var quoteChar: Char? = null
        var escaped = false
        var index = 0
        while (index < rawContent.length) {
            val char = rawContent[index]
            if (quoteChar != null) {
                builder.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quoteChar -> quoteChar = null
                }
                index += 1
                continue
            }
            if (char == '"' || char == '\'') {
                quoteChar = char
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

    private fun String.normalizeLooseJsonQuoteChars(): String {
        return map { char ->
            when (char) {
                '“', '”', '„', '‟' -> '"'
                '‘', '’', '‚', '‛' -> '\''
                else -> char
            }
        }.joinToString(separator = "")
    }

    private fun String.replaceStructuralFullWidthPunctuation(): String {
        val builder = StringBuilder(length)
        var quoteChar: Char? = null
        var escaped = false
        for (char in this) {
            if (quoteChar != null) {
                builder.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quoteChar -> quoteChar = null
                }
                continue
            }
            when (char) {
                '"', '\'' -> {
                    quoteChar = char
                    builder.append(char)
                }
                '：' -> builder.append(':')
                '，' -> builder.append(',')
                else -> builder.append(char)
            }
        }
        return builder.toString()
    }

    private fun String.quoteUnquotedObjectKeys(): String {
        val builder = StringBuilder(length)
        var index = 0
        var quoteChar: Char? = null
        var escaped = false
        while (index < length) {
            val char = this[index]
            if (quoteChar != null) {
                builder.append(char)
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == quoteChar -> quoteChar = null
                }
                index += 1
                continue
            }
            if (char == '"' || char == '\'') {
                quoteChar = char
                builder.append(char)
                index += 1
                continue
            }
            if (char == '{' || char == ',') {
                builder.append(char)
                index += 1
                val whitespaceStart = index
                while (index < length && this[index].isWhitespace()) {
                    builder.append(this[index])
                    index += 1
                }
                val keyStart = index
                if (index < length && (this[index].isLetter() || this[index] == '_')) {
                    index += 1
                    while (
                        index < length &&
                        (this[index].isLetterOrDigit() || this[index] == '_' || this[index] == '-')
                    ) {
                        index += 1
                    }
                    val key = substring(keyStart, index)
                    var colonIndex = index
                    while (colonIndex < length && this[colonIndex].isWhitespace()) {
                        colonIndex += 1
                    }
                    if (
                        colonIndex < length &&
                        this[colonIndex] == ':' &&
                        key.lowercase() in looseJsonProtocolKeyNames
                    ) {
                        builder.append('"')
                        builder.append(key)
                        builder.append('"')
                        builder.append(':')
                        index = colonIndex + 1
                        continue
                    }
                    builder.append(substring(keyStart, colonIndex))
                    index = colonIndex
                    continue
                }
                if (whitespaceStart == index) {
                    continue
                }
                continue
            }
            builder.append(char)
            index += 1
        }
        return builder.toString()
    }

    private fun String.convertSingleQuotedStringsToJsonStrings(): String {
        val builder = StringBuilder(length)
        var quoteChar: Char? = null
        var escaped = false
        for (char in this) {
            when (quoteChar) {
                null -> {
                    when (char) {
                        '"' -> {
                            quoteChar = '"'
                            builder.append(char)
                        }
                        '\'' -> {
                            quoteChar = '\''
                            builder.append('"')
                        }
                        else -> builder.append(char)
                    }
                }
                '"' -> {
                    builder.appendJsonStringChar(
                        char = char,
                        quoteChar = '"',
                        escaped = escaped,
                    )
                    when {
                        escaped -> escaped = false
                        char == '\\' -> escaped = true
                        char == '"' -> quoteChar = null
                    }
                }
                '\'' -> {
                    when {
                        escaped -> {
                            builder.appendEscapedSingleQuotedJsonChar(char)
                            escaped = false
                        }
                        char == '\\' -> escaped = true
                        char == '\'' -> {
                            builder.append('"')
                            quoteChar = null
                        }
                        else -> builder.appendJsonStringChar(
                            char = char,
                            quoteChar = '\'',
                            escaped = false,
                        )
                    }
                }
            }
        }
        return builder.toString()
    }

    private fun StringBuilder.appendJsonStringChar(
        char: Char,
        quoteChar: Char,
        escaped: Boolean,
    ) {
        when {
            escaped -> append(char)
            char == '\n' -> append("\\n")
            char == '\r' -> append("\\r")
            quoteChar == '\'' && char == '"' -> append("\\\"")
            else -> append(char)
        }
    }

    private fun StringBuilder.appendEscapedSingleQuotedJsonChar(char: Char) {
        when (char) {
            '\'', '"' -> append(char)
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            'n', 'r', 't', 'b', 'f', 'u', '\\', '/' -> {
                append('\\')
                append(char)
            }
            else -> append(char)
        }
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

    private fun splitFallbackBubbleText(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.length < MIN_FALLBACK_SPLIT_CHARS || normalized.contains("://")) {
            return listOf(normalized)
        }
        val sentences = fallbackSentencePattern.findAll(normalized)
            .map { it.value.trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (sentences.size < 2 || sentences.joinToString(separator = "").length != normalized.length) {
            return listOf(normalized)
        }
        if (sentences.any { it.length > MAX_FALLBACK_SENTENCE_CHARS }) {
            return listOf(normalized)
        }
        if (sentences.size <= MAX_FALLBACK_BUBBLE_COUNT) {
            return sentences
        }
        return buildList {
            addAll(sentences.take(MAX_FALLBACK_BUBBLE_COUNT - 1))
            add(sentences.drop(MAX_FALLBACK_BUBBLE_COUNT - 1).joinToString(separator = ""))
        }
    }

    private fun OnlineActionProtocolParseResult.toGroupAllowed(): OnlineActionProtocolParseResult? {
        val allowedParts = parts.filter { part ->
            when {
                part.type == ChatMessagePartType.TEXT -> !part.isOnlineThoughtPart()
                part.type == ChatMessagePartType.ACTION -> part.actionType in groupAllowedActionTypes
                else -> false
            }
        }
        if (allowedParts.isEmpty()) {
            return null
        }
        return OnlineActionProtocolParseResult(
            parts = normalizeChatMessageParts(allowedParts),
            directives = emptyList(),
        )
    }

    private val groupAllowedActionTypes = setOf(
        ChatActionType.VOICE_MESSAGE,
        ChatActionType.AI_PHOTO,
    )

    private fun looksLikeGroupForbiddenOutput(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.isBlank()) {
            return false
        }
        return groupForbiddenMarkers.any { marker -> normalized.contains(marker, ignoreCase = true) }
    }

    private fun looksLikeNarrationForGroup(text: String): Boolean {
        val normalized = text.trim()
        if (normalized.length <= MAX_GROUP_TEXT_FALLBACK_CHARS) {
            return false
        }
        return groupNarrationMarkers.any { marker -> normalized.contains(marker) }
    }

    private const val MAX_GROUP_TEXT_FALLBACK_CHARS = 120
    private val groupForbiddenMarkers = listOf(
        "状态栏",
        "【状态】",
        "【状态栏】",
        "心声",
        "[[rp_online_thought]]",
        "\"type\":\"thought\"",
        "\"type\": \"thought\"",
        "\"type\":\"location\"",
        "\"type\": \"location\"",
        "\"type\":\"transfer\"",
        "\"type\": \"transfer\"",
        "\"type\":\"transfer_action\"",
        "\"type\": \"transfer_action\"",
        "\"type\":\"poke\"",
        "\"type\": \"poke\"",
        "\"type\":\"video_call\"",
        "\"type\": \"video_call\"",
        "\"type\":\"invite\"",
        "\"type\": \"invite\"",
        "\"type\":\"gift\"",
        "\"type\": \"gift\"",
        "\"type\":\"task\"",
        "\"type\": \"task\"",
        "\"type\":\"punish\"",
        "\"type\": \"punish\"",
        "<status",
    )
    private val groupNarrationMarkers = listOf(
        "灯光",
        "屏幕",
        "画面",
        "背景",
        "镜头",
        "坐在",
        "靠在",
        "映出",
        "隐约",
        "指尖",
    )

    private fun JsonObject.protocolType(): String {
        val rawType = firstStringValue("type", "action_type", "actionType", "kind", "event")
            .ifBlank {
                stringValue("action")
                    .takeIf { it.normalizeProtocolTypeToken() in actionValueProtocolTypeAliases }
                    .orEmpty()
            }
        val normalizedType = rawType.normalizeProtocolTypeToken()
        return protocolTypeAliases[normalizedType] ?: normalizedType
    }

    private fun String.sanitizeMalformedProtocolLeak(): String {
        return trim()
            .replace(Regex("""(?i)\b(?:thought|ai[-_]?photo|voice[-_]?message|reply[-_]?to|play)\b\s*,?"""), "")
            .replace(malformedProtocolAttributePattern, "")
            .replace(malformedProtocolColonKeyPattern, "")
            .replace(Regex("""/?>"""), "")
            .replace(Regex("""[<>]"""), "")
            .trim(',', '，', '"', '\'', '=', '/', ' ', '\t')
            .trim()
    }

    private fun String.looksLikeProtocolLeak(): Boolean {
        val normalized = trim()
        if (normalized.isBlank()) {
            return false
        }
        if (
            protocolAttributeResidualLinePattern.matches(normalized) ||
            protocolPunctuationResidualLinePattern.matches(normalized)
        ) {
            return true
        }
        return protocolLeakMarkers.any { marker ->
            normalized.contains(marker, ignoreCase = true)
        }
    }

    private fun sanitizeProtocolResidualTextLine(rawLine: String): String {
        val sanitized = rawLine.sanitizeMalformedProtocolLeak()
            .removeSurrounding("\"")
            .trim()
            .removeSuffix(",")
            .trim()
        return sanitized.takeUnless { line ->
            line.isBlank() ||
                line.looksLikeProtocolLeak() ||
                line.looksLikeControlArtifactLine()
        }.orEmpty()
    }

    private val protocolLeakMarkers = listOf(
        "\"type\"",
        "\"op\"",
        "\"path\"",
        "\"value\"",
        "\"delta\"",
        "{",
        "}",
        "<play",
        "play id=",
        "message_id",
        "duration_seconds",
        "ai_photo",
        "ai-photo",
        "voice_message",
        "reply_to",
        "transfer_action",
    )

    private val controlArtifactSectionPattern = Regex(
        """(?is)<\s*(?:UpdateVariable|Analysis|JSONPatch|status_current_variables)\b[^>]*>.*?(?:<\s*/\s*(?:UpdateVariable|Analysis|JSONPatch|status_current_variables)\s*>|$)""",
    )
    private val controlArtifactClosingTagPattern = Regex(
        """(?is)<\s*/\s*(?:UpdateVariable|Analysis|JSONPatch|status_current_variables)\s*>""",
    )
    private val controlArtifactLinePattern = Regex(
        """(?i)^\s*(?:[-*]\s*)?(?:time\s+passed|dramatic\s+updates?|variables?\s+update|变量更新|current\s+variables|status_current_variables|ntk\s+detected|state\s+initializes|resource\s+exchange|relationship\s+starts)\b.*""",
    )
    private val jsonPatchMarkerPattern = Regex("""(?i)"?\s*(?:op|path|value|delta|from)\s*"?\s*:""")
    private val jsonPatchFragmentWords = setOf(
        "op",
        "path",
        "value",
        "values",
        "delta",
        "from",
        "old",
        "new",
        "replace",
        "add",
        "remove",
        "test",
    )

    private fun String.stripOnlineControlArtifactSections(): String {
        val withoutXmlBlocks = replace(controlArtifactSectionPattern, "\n")
            .replace(controlArtifactClosingTagPattern, "\n")
        val lines = withoutXmlBlocks.lines()
        val artifactStart = lines.indices.firstOrNull { index ->
            val line = lines[index].trim()
            line.looksLikeControlArtifactLine() ||
                (
                    line.startsWith("[") &&
                        lines.drop(index).take(10).any { jsonPatchMarkerPattern.containsMatchIn(it) }
                    )
        } ?: return withoutXmlBlocks
        return lines.take(artifactStart).joinToString(separator = "\n")
    }

    private fun String.looksLikeControlArtifactLine(): Boolean {
        val raw = trim()
        if (raw.isBlank()) {
            return false
        }
        if (controlArtifactLinePattern.containsMatchIn(raw) || jsonPatchMarkerPattern.containsMatchIn(raw)) {
            return true
        }
        val compact = raw
            .trim(',', '，', ':', '：', '"', '\'', '[', ']', '{', '}', ' ', '\t')
            .lowercase()
        return compact in jsonPatchFragmentWords
    }

    private fun JsonObject.stringValue(key: String): String {
        return runCatching { valueElement(key)?.protocolTextValue().orEmpty() }
            .getOrDefault("")
            .trim()
    }

    private fun JsonObject.numericStringValue(key: String): String {
        val element = valueElement(key) ?: return ""
        return when {
            element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> element.asNumber.toString()
            element.isJsonPrimitive && element.asJsonPrimitive.isString -> element.asString.trim()
            else -> ""
        }
    }

    private fun JsonObject.firstStringValue(vararg keys: String): String {
        return keys.firstNotNullOfOrNull { key ->
            stringValue(key).takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun JsonObject.contentValue(): String {
        return firstStringValue("content", "text", "message", "dialogue", "speech")
    }

    private fun JsonObject.descriptionValue(): String {
        return firstStringValue("description", "desc", "caption", "prompt", "content", "text", "message")
    }

    private fun JsonObject.valueElement(key: String): JsonElement? {
        return get(key) ?: entrySet().firstOrNull { entry ->
            entry.key.equals(key, ignoreCase = true)
        }?.value
    }

    private fun JsonElement.protocolTextValue(): String {
        return when {
            isJsonNull -> ""
            isJsonPrimitive -> asString
            isJsonArray -> asJsonArray
                .map { element -> element.protocolTextValue() }
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n")
            isJsonObject -> {
                val objectValue = asJsonObject
                objectValue.contentValue()
                    .ifBlank { objectValue.descriptionValue() }
                    .ifBlank {
                        objectValue.entrySet()
                            .joinToString(separator = "\n") { entry -> entry.value.protocolTextValue() }
                    }
            }
            else -> ""
        }.trim()
    }

    private fun String.normalizeProtocolTypeToken(): String {
        return trim()
            .lowercase()
            .replace('-', '_')
            .replace(' ', '_')
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

    private val looseJsonProtocolKeyNames = protocolAttributeNames
        .map { it.lowercase() }
        .toSet()

    private val protocolTypeAliases = mapOf(
        "reply" to "reply_to",
        "replyto" to "reply_to",
        "voice" to "voice_message",
        "audio" to "voice_message",
        "photo" to "ai_photo",
        "image" to "ai_photo",
        "ai_image" to "ai_photo",
        "transfer_receipt" to "transfer_action",
        "transfer_status" to "transfer_action",
        "video" to "video_call",
        "call" to "video_call",
    )
    private val actionValueProtocolTypeAliases = protocolTypeAliases.keys + setOf(
        "reply_to",
        "thought",
        "recall",
        "emoji",
        "voice_message",
        "ai_photo",
        "location",
        "transfer",
        "transfer_action",
        "poke",
        "video_call",
        "task",
        "invite",
        "gift",
        "punish",
    )
    private val fallbackSentencePattern = Regex("""[^。！？!?；;…]+[。！？!?；;…]*""")
    private const val MIN_FALLBACK_SPLIT_CHARS = 30
    private const val MAX_FALLBACK_SENTENCE_CHARS = 48
    private const val MAX_FALLBACK_BUBBLE_COUNT = 4
}
