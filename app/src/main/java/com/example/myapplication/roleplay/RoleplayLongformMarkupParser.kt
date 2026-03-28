package com.example.myapplication.roleplay

import androidx.compose.runtime.Immutable

@Immutable
enum class RoleplayLongformSpanType {
    NARRATION,
    CHARACTER_SPEECH,
    THOUGHT,
}

@Immutable
data class RoleplayLongformSpan(
    val text: String,
    val type: RoleplayLongformSpanType,
)

@Immutable
data class RoleplayLongformParagraph(
    val spans: List<RoleplayLongformSpan>,
) {
    val plainText: String
        get() = spans.joinToString(separator = "") { it.text }
}

object RoleplayLongformMarkupParser {
    private val supportedTagRegex = Regex("""(?is)</?(char|thought)>""")
    private val parserTagRegex = Regex("""(?is)<(/?)(char|thought)>""")
    private val danglingTagRegex = Regex("""(?is)<[^>\n]*$""")

    fun parseParagraphs(rawContent: String): List<RoleplayLongformParagraph> {
        return rawContent.replace("\r\n", "\n")
            .split("\n")
            .map { paragraph -> parseParagraph(paragraph.trim()) }
            .filter { paragraph -> paragraph.spans.isNotEmpty() && paragraph.plainText.isNotBlank() }
    }

    fun stripMarkupForDisplay(rawContent: String): String {
        return rawContent.replace("\r\n", "\n")
            .replace(danglingTagRegex, "")
            .replace(supportedTagRegex, "")
            .trim()
    }

    private fun parseParagraph(paragraph: String): RoleplayLongformParagraph {
        if (paragraph.isBlank()) {
            return RoleplayLongformParagraph(emptyList())
        }

        val spans = mutableListOf<RoleplayLongformSpan>()
        var activeType = RoleplayLongformSpanType.NARRATION
        var cursor = 0
        val stack = ArrayDeque<RoleplayLongformSpanType>()

        parserTagRegex.findAll(paragraph).forEach { match ->
            if (match.range.first > cursor) {
                appendSpan(
                    target = spans,
                    text = paragraph.substring(cursor, match.range.first),
                    type = activeType,
                )
            }

            val isClosing = match.groupValues[1] == "/"
            val tagType = when (match.groupValues[2].lowercase()) {
                "char" -> RoleplayLongformSpanType.CHARACTER_SPEECH
                "thought" -> RoleplayLongformSpanType.THOUGHT
                else -> RoleplayLongformSpanType.NARRATION
            }

            if (isClosing) {
                if (stack.isNotEmpty()) {
                    stack.removeLast()
                }
                activeType = stack.lastOrNull() ?: RoleplayLongformSpanType.NARRATION
            } else {
                stack.addLast(tagType)
                activeType = tagType
            }

            cursor = match.range.last + 1
        }

        if (cursor < paragraph.length) {
            appendSpan(
                target = spans,
                text = paragraph.substring(cursor),
                type = activeType,
            )
        }

        return RoleplayLongformParagraph(spans)
    }

    private fun appendSpan(
        target: MutableList<RoleplayLongformSpan>,
        text: String,
        type: RoleplayLongformSpanType,
    ) {
        if (text.isBlank()) {
            return
        }

        val normalized = text
            .replace(danglingTagRegex, "")
            .replace(supportedTagRegex, "")
        if (normalized.isBlank()) {
            return
        }

        val previous = target.lastOrNull()
        if (previous != null && previous.type == type) {
            target[target.lastIndex] = previous.copy(text = previous.text + normalized)
        } else {
            target += RoleplayLongformSpan(
                text = normalized,
                type = type,
            )
        }
    }
}
