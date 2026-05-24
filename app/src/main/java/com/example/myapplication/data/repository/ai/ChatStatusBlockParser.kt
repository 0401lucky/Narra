package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.textMessagePart

internal object ChatStatusBlockParser {
    private val tagRegex = Regex(
        pattern = """<\s*(StatusBlock|user_status|status)\s*>(.*?)<\s*/\s*\1\s*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val slashFenceRegex = Regex(
        pattern = """(?is)(?<![A-Za-z0-9_])(statusbar|状态栏|status)\s*/(.*?)/\s*(?:statusbar|状态栏|status)(?![A-Za-z0-9_])""",
    )
    private val codeBlockRegex = Regex(
        pattern = """(?im)^```\s*(status|statusbar|状态栏|tracker)\s*\R([\s\S]*?)\R```""",
    )
    private val paragraphRegex = Regex(
        pattern = """(?m)^\s*(?:【状态栏】|\[状态栏]|\[status]|\[Status]|状态栏[:：]|status[:：])\s*(.+)$""",
    )
    private val bracketStatusRegex = Regex(
        pattern = """(?ms)^\s*>?\s*[『「【](.*?)[』」】]""",
    )

    fun extractFromParts(
        parts: List<ChatMessagePart>,
        hideStatusBlocksInBubble: Boolean = true,
    ): List<ChatMessagePart> {
        if (parts.isEmpty()) {
            return emptyList()
        }
        return runCatching {
            parts.flatMap { part ->
                when (part.type) {
                    ChatMessagePartType.TEXT -> extract(part.text, hideStatusBlocksInBubble)
                    ChatMessagePartType.STATUS -> emptyList()
                    else -> listOf(part)
                }
            }
        }.getOrElse {
            parts.filterNot { part -> part.type == ChatMessagePartType.STATUS }
        }
    }

    fun extract(
        text: String,
        hideStatusBlocksInBubble: Boolean = true,
    ): List<ChatMessagePart> {
        return runCatching {
            extractUnsafe(text, hideStatusBlocksInBubble)
        }.getOrElse {
            text.replace("\r\n", "\n")
                .takeIf { normalized -> normalized.isNotBlank() }
                ?.let { normalized -> listOf(textMessagePart(normalized)) }
                ?: emptyList()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun extractUnsafe(
        text: String,
        hideStatusBlocksInBubble: Boolean,
    ): List<ChatMessagePart> {
        if (text.isBlank()) {
            return emptyList()
        }
        val sourceText = text.replace("\r\n", "\n")
        val matches = buildList {
            tagRegex.findAll(sourceText).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                    ),
                )
            }
            slashFenceRegex.findAll(sourceText).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                    ),
                )
            }
            codeBlockRegex.findAll(sourceText).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                    ),
                )
            }
            paragraphRegex.findAll(sourceText).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                    ),
                )
            }
            bracketStatusRegex.findAll(sourceText).forEach { match ->
                val rawStatus = match.groupValues.getOrNull(1).orEmpty().trim()
                if (rawStatus.isLikelyStatusBlock()) {
                    add(
                        StatusMatch(
                            range = match.range.expandLeadingStatusLabel(sourceText, rawStatus),
                        ),
                    )
                }
            }
            findLeadingLooseStatusBlock(sourceText)?.let(::add)
        }
            .sortedWith(
                compareBy<StatusMatch> { it.range.first }
                    .thenByDescending { it.range.last - it.range.first },
            )
            .fold(mutableListOf<StatusMatch>()) { accepted, candidate ->
                if (accepted.none { it.range.overlaps(candidate.range) }) {
                    accepted += candidate
                }
                accepted
            }

        if (matches.isEmpty()) {
            return listOf(textMessagePart(sourceText))
        }

        val result = mutableListOf<ChatMessagePart>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                sourceText.substring(cursor, match.range.first)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { result += textMessagePart(it) }
            }
            cursor = match.range.last + 1
        }
        if (cursor < sourceText.length) {
            sourceText.substring(cursor)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { result += textMessagePart(it) }
        }
        return result
    }

    private data class StatusMatch(
        val range: IntRange,
    )

    private fun findLeadingLooseStatusBlock(text: String): StatusMatch? {
        val lines = text.split('\n')
        var offset = 0
        var statusStart = -1
        var statusEnd = -1
        val statusLines = mutableListOf<String>()

        for (line in lines) {
            val lineStart = offset
            val lineEnd = lineStart + line.length
            offset = lineEnd + 1

            val trimmed = line.trim()
            if (trimmed.isBlank()) {
                if (statusLines.isEmpty()) {
                    continue
                }
                continue
            }

            val normalized = trimmed
                .removePrefix(">")
                .trim()
                .stripLooseStatusFence()
            if (normalized.startsWith("<p", ignoreCase = true)) {
                break
            }
            if (!normalized.isLooseStatusLine()) {
                break
            }
            if (statusStart < 0) {
                statusStart = lineStart
            }
            statusEnd = lineEnd
            statusLines += normalized
        }

        val rawStatus = statusLines.joinToString(separator = "\n").trim()
        if (statusStart < 0 || statusEnd <= statusStart || !rawStatus.isLikelyStatusBlock()) {
            return null
        }
        return StatusMatch(
            range = statusStart until statusEnd,
        )
    }

    private fun IntRange.overlaps(other: IntRange): Boolean {
        return first <= other.last && other.first <= last
    }

    private fun IntRange.expandLeadingStatusLabel(text: String, rawStatus: String): IntRange {
        if (first <= 0) {
            return this
        }
        val lineBreakBeforeMatch = text.lastIndexOf('\n', startIndex = first - 1)
        if (lineBreakBeforeMatch < 0 || lineBreakBeforeMatch + 1 != first) {
            return this
        }
        val previousLineBreak = text.lastIndexOf('\n', startIndex = lineBreakBeforeMatch - 1)
        val previousLineStart = previousLineBreak + 1
        val previousLine = text.substring(previousLineStart, lineBreakBeforeMatch).trim()
        if (!previousLine.isLikelyStatusLabelFor(rawStatus)) {
            return this
        }
        return previousLineStart..last
    }

    private fun String.isLikelyStatusLabelFor(rawStatus: String): Boolean {
        if (isBlank() || length > 16 || any(Char::isWhitespace)) {
            return false
        }
        val forbiddenChars = setOf(
            '。', '！', '？', '，', '、', ',', '.', '!', '?',
            '：', ':', '|', '｜', '<', '>', '[', ']', '【', '】',
            '『', '』', '「', '」', '“', '”', '"', '\'',
        )
        if (any { char -> char in forbiddenChars }) {
            return false
        }
        return rawStatus.contains(this) || rawStatus.contains("${this}状态")
    }

    private fun String.isLikelyStatusBlock(): Boolean {
        val normalized = trim()
        if (normalized.length < 12) {
            return false
        }
        val hitCount = listOf(
            "时间",
            "日期",
            "星期",
            "地点",
            "天气",
            "场景",
            "状态",
            "阶段",
            "外在",
            "眼镜",
            "领带",
            "心情",
            "心跳",
            "位置",
            "底线",
            "记忆",
            "世界书",
            "摘要",
        ).count { keyword -> keyword in normalized }
        return hitCount >= 2 && (
            '|' in normalized ||
                '｜' in normalized ||
                '├' in normalized ||
                '└' in normalized ||
                '：' in normalized ||
                ':' in normalized ||
                normalized.lines().size >= 2
        )
    }

    private fun String.isLooseStatusLine(): Boolean {
        val normalized = trim().trim('|', ' ')
        if (normalized.isBlank() || normalized.startsWith("<")) {
            return false
        }
        if ("·状态" in normalized && (
            '|' in normalized ||
                '｜' in normalized ||
                '├' in normalized ||
                '└' in normalized ||
                normalized.endsWith("·状态")
        )) {
            return true
        }
        if ((normalized.startsWith("├") || normalized.startsWith("└")) && (
            '：' in normalized ||
                ':' in normalized ||
                '=' in normalized
        )) {
            return true
        }
        val hitCount = listOf(
            "时间",
            "日期",
            "星期",
            "地点",
            "天气",
            "场景",
            "状态",
            "阶段",
            "外在",
            "眼镜",
            "领带",
            "心跳",
            "底线",
        ).count { keyword -> keyword in normalized }
        return hitCount >= 1 && (
            '：' in normalized ||
                ':' in normalized ||
                '|' in normalized ||
                '｜' in normalized ||
                '├' in normalized ||
                '└' in normalized
        )
    }

    private fun String.stripLooseStatusFence(): String {
        return trim()
            .trimStart('『', '「', '【', '[', '（', '(')
            .trimEnd('』', '」', '】', ']', '）', ')')
            .trim()
    }
}
