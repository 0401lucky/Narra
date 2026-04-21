package com.example.myapplication.context

/**
 * CJK 感知的整词匹配：
 * - 关键词两端都是 CJK → 退化为 contains（中文没有词边界概念）
 * - 关键词头部/尾部非 CJK（英文/数字）→ 该侧要求前后字符不是 ASCII 字母数字
 *   （中文字符算 Letter，但不与英文共享词语——所以 ASCII-only 判断是对的）
 */
internal fun matchesContainsCjkAware(
    pattern: String,
    source: String,
    caseSensitive: Boolean,
): Boolean {
    val trimmedPattern = pattern.trim()
    if (trimmedPattern.isEmpty() || source.isEmpty()) return false
    val requireHeadBoundary = !isCjkCodePoint(trimmedPattern.first().code)
    val requireTailBoundary = !isCjkCodePoint(trimmedPattern.last().code)
    if (!requireHeadBoundary && !requireTailBoundary) {
        return source.contains(trimmedPattern, ignoreCase = !caseSensitive)
    }
    val searchSource = if (caseSensitive) source else source.lowercase()
    val needle = if (caseSensitive) trimmedPattern else trimmedPattern.lowercase()
    var startIndex = 0
    while (startIndex <= searchSource.length - needle.length) {
        val hit = searchSource.indexOf(needle, startIndex)
        if (hit < 0) return false
        val leftOk = !requireHeadBoundary || hit == 0 ||
            !searchSource[hit - 1].isAsciiLetterOrDigit()
        val rightEnd = hit + needle.length
        val rightOk = !requireTailBoundary || rightEnd >= searchSource.length ||
            !searchSource[rightEnd].isAsciiLetterOrDigit()
        if (leftOk && rightOk) return true
        startIndex = hit + 1
    }
    return false
}

internal fun isCjkCodePoint(cp: Int): Boolean {
    return (cp in 0x4E00..0x9FFF) ||   // CJK Unified Ideographs
        (cp in 0x3400..0x4DBF) ||      // Extension A
        (cp in 0x20000..0x2A6DF) ||    // Extension B
        (cp in 0x3040..0x309F) ||      // Hiragana
        (cp in 0x30A0..0x30FF) ||      // Katakana
        (cp in 0xAC00..0xD7AF)         // Hangul
}

private fun Char.isAsciiLetterOrDigit(): Boolean {
    return this in '0'..'9' || this in 'A'..'Z' || this in 'a'..'z'
}
