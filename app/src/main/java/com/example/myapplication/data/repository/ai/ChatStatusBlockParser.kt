package com.example.myapplication.data.repository.ai

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.statusMessagePart
import com.example.myapplication.model.textMessagePart

internal object ChatStatusBlockParser {
    private val tagRegex = Regex(
        pattern = """<\s*(StatusBlock|user_status|status)\s*>(.*?)<\s*/\s*\1\s*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
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
        return parts.flatMap { part ->
            if (part.type == ChatMessagePartType.TEXT) {
                extract(part.text, hideStatusBlocksInBubble)
            } else {
                listOf(part)
            }
        }
    }

    fun extract(
        text: String,
        hideStatusBlocksInBubble: Boolean = true,
    ): List<ChatMessagePart> {
        if (text.isBlank()) {
            return emptyList()
        }
        val matches = buildList {
            tagRegex.findAll(text).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                        title = match.groupValues.getOrNull(1)
                            .orEmpty()
                            .statusTitle(),
                        rawText = match.groupValues.getOrNull(2).orEmpty(),
                    ),
                )
            }
            codeBlockRegex.findAll(text).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                        title = match.groupValues.getOrNull(1)
                            .orEmpty()
                            .statusTitle(),
                        rawText = match.groupValues.getOrNull(2).orEmpty(),
                    ),
                )
            }
            paragraphRegex.findAll(text).forEach { match ->
                add(
                    StatusMatch(
                        range = match.range,
                        title = "状态栏",
                        rawText = match.groupValues.getOrNull(1).orEmpty(),
                    ),
                )
            }
            bracketStatusRegex.findAll(text).forEach { match ->
                val rawStatus = match.groupValues.getOrNull(1).orEmpty().trim()
                if (rawStatus.isLikelyStatusBlock()) {
                    add(
                        StatusMatch(
                            range = match.range,
                            title = "状态栏",
                            rawText = rawStatus,
                        ),
                    )
                }
            }
        }
            .sortedBy { it.range.first }
            .fold(mutableListOf<StatusMatch>()) { accepted, candidate ->
                if (accepted.none { it.range.overlaps(candidate.range) }) {
                    accepted += candidate
                }
                accepted
            }

        if (matches.isEmpty()) {
            return listOf(textMessagePart(text))
        }

        if (!hideStatusBlocksInBubble) {
            return listOf(textMessagePart(text)) + matches.mapNotNull { match ->
                match.rawText
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { raw -> statusMessagePart(rawText = raw, title = match.title) }
            }
        }

        val result = mutableListOf<ChatMessagePart>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                text.substring(cursor, match.range.first)
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { result += textMessagePart(it) }
            }
            match.rawText
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { raw -> result += statusMessagePart(rawText = raw, title = match.title) }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            text.substring(cursor)
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { result += textMessagePart(it) }
        }
        return result
    }

    private data class StatusMatch(
        val range: IntRange,
        val title: String,
        val rawText: String,
    )

    private fun IntRange.overlaps(other: IntRange): Boolean {
        return first <= other.last && other.first <= last
    }

    private fun String.statusTitle(): String {
        return when (trim().lowercase()) {
            "statusblock" -> "状态块"
            "user_status" -> "用户状态"
            "statusbar", "状态栏" -> "状态栏"
            "tracker" -> "状态追踪"
            else -> "状态"
        }
    }

    private fun String.isLikelyStatusBlock(): Boolean {
        val normalized = trim()
        if (normalized.length < 12) {
            return false
        }
        val hitCount = listOf(
            "时间",
            "日期",
            "地点",
            "天气",
            "状态",
            "心情",
            "位置",
            "记忆",
            "世界书",
            "摘要",
        ).count { keyword -> keyword in normalized }
        return hitCount >= 2 && (
            '|' in normalized ||
                '：' in normalized ||
                ':' in normalized ||
                normalized.lines().size >= 2
        )
    }
}
