package com.example.myapplication.model

const val WORLD_BOOK_MAX_PRIMARY_KEYWORDS: Int = 96
const val WORLD_BOOK_MAX_SECONDARY_KEYWORDS: Int = 64
const val WORLD_BOOK_MAX_KEYWORD_LENGTH: Int = 96
const val WORLD_BOOK_MAX_REGEX_LENGTH: Int = 160
const val WORLD_BOOK_REGEX_MATCH_SOURCE_MAX_CHARS: Int = 2_000

data class WorldBookRegexLiteral(
    val body: String,
    val flags: String,
)

fun normalizeWorldBookKeywords(
    values: List<String>,
    matchMode: WorldBookMatchMode,
    maxItems: Int,
): List<String> {
    val normalized = linkedSetOf<String>()
    for (rawValue in values) {
        if (normalized.size >= maxItems) break
        for (candidate in expandWorldBookKeywordCandidates(rawValue, matchMode)) {
            if (normalized.size >= maxItems) break
            val token = candidate.trim()
            if (token.isNotEmpty() && isAllowedWorldBookKeyword(token, matchMode)) {
                normalized += token
            }
        }
    }
    return normalized.toList()
}

fun expandWorldBookKeywordCandidates(
    rawValue: String,
    matchMode: WorldBookMatchMode,
): List<String> {
    val normalized = rawValue.trim()
    return when {
        normalized.isBlank() -> emptyList()
        matchMode == WorldBookMatchMode.REGEX -> listOf(normalized)
        parseWorldBookRegexLiteral(normalized) != null -> listOf(normalized)
        else -> normalized
            .split(WorldBookKeywordDelimiterRegex)
            .mapNotNull { token -> token.trim().takeIf { it.isNotEmpty() } }
    }
}

fun isAllowedWorldBookKeyword(
    keyword: String,
    matchMode: WorldBookMatchMode,
): Boolean {
    val literal = parseWorldBookRegexLiteral(keyword)
    return when {
        literal != null -> isSafeWorldBookRegexPattern(literal.body)
        matchMode == WorldBookMatchMode.REGEX -> isSafeWorldBookRegexPattern(keyword)
        else -> keyword.length <= WORLD_BOOK_MAX_KEYWORD_LENGTH
    }
}

fun parseWorldBookRegexLiteral(pattern: String): WorldBookRegexLiteral? {
    if (pattern.length < 2 || !pattern.startsWith('/')) {
        return null
    }
    var escaped = false
    var endIndex = -1
    for (index in 1 until pattern.length) {
        val current = pattern[index]
        if (current == '/' && !escaped) {
            endIndex = index
            break
        }
        escaped = current == '\\' && !escaped
        if (current != '\\') {
            escaped = false
        }
    }
    if (endIndex <= 1) {
        return null
    }
    val flags = pattern.substring(endIndex + 1)
    if (flags.any { flag -> flag.lowercaseChar() !in AllowedWorldBookRegexFlags }) {
        return null
    }
    return WorldBookRegexLiteral(
        body = pattern.substring(1, endIndex),
        flags = flags,
    )
}

fun isSafeWorldBookRegexPattern(pattern: String): Boolean {
    return pattern.length in 1..WORLD_BOOK_MAX_REGEX_LENGTH &&
        !hasWorldBookBackReference(pattern) &&
        !hasWorldBookLookaround(pattern) &&
        !hasWorldBookOversizedRepeat(pattern) &&
        !hasWorldBookNestedQuantifierRisk(pattern)
}

private fun hasWorldBookBackReference(pattern: String): Boolean {
    var escaped = false
    pattern.forEachIndexed { index, current ->
        if (escaped) {
            if (current in '1'..'9' || current == 'k') {
                return true
            }
            escaped = false
            return@forEachIndexed
        }
        if (current == '\\') {
            escaped = true
        }
        if (index == pattern.lastIndex && escaped) {
            escaped = false
        }
    }
    return false
}

private fun hasWorldBookLookaround(pattern: String): Boolean {
    return listOf("(?=", "(?!", "(?<=", "(?<!").any(pattern::contains)
}

private fun hasWorldBookOversizedRepeat(pattern: String): Boolean {
    var index = 0
    while (index < pattern.length) {
        if (pattern[index] == '{') {
            val end = pattern.indexOf('}', startIndex = index + 1)
            if (end < 0) return false
            val body = pattern.substring(index + 1, end)
            val numbers = body.split(',')
                .mapNotNull { value -> value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull() }
            if (numbers.any { it > WORLD_BOOK_MAX_REPEAT_COUNT }) {
                return true
            }
            index = end
        }
        index++
    }
    return false
}

private fun hasWorldBookNestedQuantifierRisk(pattern: String): Boolean {
    val groups = ArrayDeque<WorldBookRegexGroupScan>()
    var index = 0
    var escaped = false
    var inCharClass = false
    while (index < pattern.length) {
        val current = pattern[index]
        if (escaped) {
            escaped = false
            index++
            continue
        }
        when {
            current == '\\' -> {
                escaped = true
                index++
            }
            inCharClass -> {
                if (current == ']') inCharClass = false
                index++
            }
            current == '[' -> {
                inCharClass = true
                index++
            }
            current == '(' -> {
                groups.addLast(WorldBookRegexGroupScan())
                index = findWorldBookGroupPrefixEnd(pattern, index) + 1
            }
            current == ')' -> {
                val group = groups.removeLastOrNull()
                val quantifiedGroup = isWorldBookQuantifierAt(pattern, index + 1)
                if (group != null) {
                    if ((group.hasInnerQuantifier || group.hasAlternation) && quantifiedGroup) {
                        return true
                    }
                    if (groups.isNotEmpty() && (group.hasInnerQuantifier || quantifiedGroup)) {
                        groups.last().hasInnerQuantifier = true
                    }
                }
                index++
            }
            current == '|' -> {
                if (groups.isNotEmpty()) groups.last().hasAlternation = true
                index++
            }
            isWorldBookQuantifierAt(pattern, index) -> {
                if (groups.isNotEmpty()) groups.last().hasInnerQuantifier = true
                index = findWorldBookRepeatEnd(pattern, index) ?: index
                index++
            }
            else -> index++
        }
    }
    return false
}

private fun findWorldBookGroupPrefixEnd(pattern: String, openIndex: Int): Int {
    val questionIndex = openIndex + 1
    if (pattern.getOrNull(questionIndex) != '?') {
        return openIndex
    }
    return when (pattern.getOrNull(questionIndex + 1)) {
        ':', '=', '!', '>' -> questionIndex + 1
        '<' -> when (pattern.getOrNull(questionIndex + 2)) {
            '=', '!' -> questionIndex + 2
            else -> questionIndex
        }
        else -> questionIndex
    }
}

private fun isWorldBookQuantifierAt(pattern: String, index: Int): Boolean {
    return when (pattern.getOrNull(index)) {
        '*', '+', '?' -> true
        '{' -> findWorldBookRepeatEnd(pattern, index) != null
        else -> false
    }
}

private fun findWorldBookRepeatEnd(pattern: String, openIndex: Int): Int? {
    if (pattern.getOrNull(openIndex) != '{') {
        return null
    }
    var index = openIndex + 1
    var hasDigit = false
    var hasComma = false
    while (index < pattern.length) {
        when (val current = pattern[index]) {
            in '0'..'9' -> hasDigit = true
            ',' -> {
                if (hasComma) return null
                hasComma = true
            }
            '}' -> return index.takeIf { hasDigit }
            else -> return null
        }
        index++
    }
    return null
}

private data class WorldBookRegexGroupScan(
    var hasInnerQuantifier: Boolean = false,
    var hasAlternation: Boolean = false,
)

private val WorldBookKeywordDelimiterRegex = Regex("""\s*[,，]\s*""")
private val AllowedWorldBookRegexFlags = setOf('i', 'm', 's', 'g', 'u')
private const val WORLD_BOOK_MAX_REPEAT_COUNT = 100
