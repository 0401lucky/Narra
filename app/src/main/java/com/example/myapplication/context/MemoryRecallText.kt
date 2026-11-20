package com.example.myapplication.context

import java.util.Locale

/** 只用于注入预算，不声称等同于供应商分词或账单。 */
object MemoryRecallText {
    fun estimateTokens(text: String): Int {
        var wide = 0
        var narrow = 0
        var index = 0
        while (index < text.length) {
            val point = text.codePointAt(index)
            if (point > 0x2e7f) wide++ else narrow++
            index += Character.charCount(point)
        }
        return wide + (narrow + 3) / 4
    }

    fun safeBoundary(text: String, offset: Int): Int {
        val bound = offset.coerceIn(0, text.length)
        return if (bound in 1 until text.length && text[bound - 1].isHighSurrogate() && text[bound].isLowSurrogate()) {
            bound - 1
        } else bound
    }

    fun chunks(text: String, maxCharacters: Int = 640, overlap: Int = 80): List<IntRange> {
        require(maxCharacters > overlap && overlap >= 0)
        val spans = mutableListOf<IntRange>()
        var start = 0
        while (start < text.length) {
            var end = safeBoundary(text, start + maxCharacters)
            if (end < text.length) {
                val paragraph = text.lastIndexOf('\n', end - 1)
                if (paragraph >= start + maxCharacters / 2) end = paragraph + 1
            }
            if (end <= start) end = text.length
            spans += start until end
            if (end == text.length) break
            start = safeBoundary(text, end - overlap).coerceAtLeast(start + 1)
        }
        return spans
    }

    fun terms(text: String): List<String> = buildList {
        val normalized = text.lowercase(Locale.ROOT)
        WORD.findAll(normalized).forEach { add(it.value) }
        CJK.findAll(normalized).forEach { match ->
            val value = match.value
            if (value.length in 2..8 && value !in STOP_WORDS) add(value)
            for (index in 0 until value.length - 1) {
                val pair = value.substring(index, index + 2)
                if (pair !in STOP_WORDS) add(pair)
            }
        }
    }.filterNot { it in STOP_WORDS }

    fun datesAndNumbers(text: String): Set<String> = buildSet {
        NUMBER.findAll(text).forEach { add(it.value.lowercase(Locale.ROOT)) }
        DATE.findAll(text).forEach { match ->
            add(
                match.groupValues.drop(1).filter(String::isNotBlank)
                    .joinToString("-") { it.toInt().toString() })
        }
    }

    private val WORD = Regex("[a-z][a-z0-9_-]*|[0-9]+(?:[.:-][0-9]+)*")
    private val CJK = Regex("[\\p{IsHan}]+")
    private val NUMBER = Regex("[0-9]+(?:[.:-][0-9]+)*")
    private val DATE = Regex("(?:([0-9]{4})[年/.-])?([0-9]{1,2})[月/.-]([0-9]{1,2})日?")
    private val STOP_WORDS = setOf(
        "你好",
        "您好",
        "早安",
        "晚安",
        "谢谢",
        "好的",
        "好呀",
        "哈哈",
        "嗯嗯",
        "知道",
        "就是",
        "这个",
        "那个",
        "我们",
        "你们",
        "他们",
        "什么",
        "怎么",
        "怎样",
        "可以",
        "还是",
        "一下",
        "还有",
        "记得",
        "现在",
        "之前",
        "了吗",
        "的吗",
        "的呢",
        "是谁",
        "是谁呢",
        "hello",
        "hi",
        "thanks",
        "ok",
        "okay",
        "the",
        "is",
        "are",
        "a",
        "an",
        "and",
    )
}
