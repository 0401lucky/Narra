package com.example.myapplication.roleplay.companion

/** 已解析正文只分配显示段，不再次处理协议、实体或空白。 */
internal object CompanionDisplayParagraphs {
    private const val MIN_PARAGRAPH_LENGTH = 48
    private const val SOFT_MAX_PARAGRAPH_LENGTH = 92
    private const val HARD_MAX_PARAGRAPH_LENGTH = 144
    private const val MIN_TRAILING_PARAGRAPH_LENGTH = 20

    private val strongBoundaries = setOf('。', '！', '？', '!', '?', '；', ';', '”', '"', '）', ')')
    private val weakBoundaries = setOf('，', '、', '：')
    private val weakBoundaryCues = listOf(
        "他", "她", "我", "你", "对方", "随后", "接着", "紧接着", "然后",
        "可", "但", "却", "而", "于是", "片刻", "一时间", "下一秒", "与此同时", "忽然", "突然",
    )

    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        if (text.length <= HARD_MAX_PARAGRAPH_LENGTH) return listOf(text)

        val result = mutableListOf<String>()
        var start = 0
        var lastStrongBoundary = -1
        var lastWeakBoundary = -1

        fun cutAt(endExclusive: Int) {
            if (endExclusive <= start) return
            result += text.substring(start, endExclusive)
            start = endExclusive
            if (lastStrongBoundary <= start) lastStrongBoundary = -1
            if (lastWeakBoundary <= start) lastWeakBoundary = -1
        }

        // 超长单簇可超过段上限，仍只在完整簇后切段；每个 end 都严格向前推进。
        for (end in companionGraphemeEnds(text)) {
            val last = text.codePointBefore(end)
            if (last <= Char.MAX_VALUE.code && last.toChar() in strongBoundaries) {
                lastStrongBoundary = end
            }
            if (last <= Char.MAX_VALUE.code && last.toChar() in weakBoundaries) {
                var next = end
                while (next < text.length && text[next].isWhitespace()) next++
                if (weakBoundaryCues.any { text.startsWith(it, next) }) lastWeakBoundary = end
            }
            val length = end - start
            when {
                length >= HARD_MAX_PARAGRAPH_LENGTH -> cutAt(
                    when {
                        lastStrongBoundary - start >= MIN_PARAGRAPH_LENGTH -> lastStrongBoundary
                        lastWeakBoundary - start >= MIN_PARAGRAPH_LENGTH -> lastWeakBoundary
                        else -> end
                    }
                )

                length >= SOFT_MAX_PARAGRAPH_LENGTH &&
                        lastStrongBoundary - start >= MIN_PARAGRAPH_LENGTH -> cutAt(
                    lastStrongBoundary
                )
            }
        }
        if (start < text.length) cutAt(text.length)
        while (result.size >= 2 && result.last().length < MIN_TRAILING_PARAGRAPH_LENGTH) {
            val tail = result.removeAt(result.lastIndex)
            result[result.lastIndex] = result.last() + tail
        }
        return result
    }
}
