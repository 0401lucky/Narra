package com.example.myapplication.roleplay

import androidx.compose.runtime.Immutable

@Immutable
enum class RoleplayLongformSpanType {
    NARRATION,
    CHARACTER_SPEECH,
    THOUGHT,
}

@Immutable
enum class RoleplayLongformParagraphAlignment {
    START,
    CENTER,
    END,
}

@Immutable
enum class RoleplayLongformParagraphTone {
    DEFAULT,
    MUTED,
    ACCENT,
}

@Immutable
data class RoleplayLongformParagraphStyle(
    val alignment: RoleplayLongformParagraphAlignment = RoleplayLongformParagraphAlignment.START,
    val tone: RoleplayLongformParagraphTone = RoleplayLongformParagraphTone.DEFAULT,
    val fontScale: Float = 1.0f,
)

@Immutable
data class RoleplayLongformSpan(
    val text: String,
    val type: RoleplayLongformSpanType,
)

@Immutable
data class RoleplayLongformParagraph(
    val spans: List<RoleplayLongformSpan>,
    val style: RoleplayLongformParagraphStyle = RoleplayLongformParagraphStyle(),
) {
    val plainText: String
        get() = spans.joinToString(separator = "") { it.text }
}

object RoleplayLongformMarkupParser {
    private val supportedTagRegex = Regex("""(?is)</?(char|thought)>""")
    private val parserTagRegex = Regex("""(?is)<(/?)(char|thought)>""")
    private val danglingTagRegex = Regex("""(?is)<[^>\n]*$""")
    private val htmlParagraphRegex = Regex("""(?is)<p\b([^>]*)>(.*?)</p>""")
    private val htmlBreakRegex = Regex("""(?is)<br\s*/?>""")
    private val remainingHtmlTagRegex = Regex("""(?is)</?(?:p|span|div|section|center|font|small|b|strong|i|em)\b[^>]*>""")
    private val htmlStyleAttributeRegex = Regex("""(?is)\bstyle\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""")
    private val htmlAlignAttributeRegex = Regex("""(?is)\balign\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)""")
    private val htmlFontSizeRegex = Regex("""(?is)font-size\s*:\s*([0-9.]+)\s*(em|rem|%)?""")
    private val unsupportedProtocolTagRegex = Regex("""(?is)</?(dialogue|narration)\b[^>]*>""")
    private val protocolAttributeNoisePattern = Regex(
        """(?is)\b(speaker|emotion|reply_to|reply_preview|reply_speaker)\s*=\s*("[^"]*"|'[^']*'|[^\s<>]+)?""",
    )
    private val llmControlTagPattern = Regex("""(?is)<(?:\||｜)[^>\n]{0,80}(?:\||｜)>""")
    private val llmControlWordPattern = Regex(
        """(?is)\b(?:begin_of_text|end_of_text|begin_of_sentence|end_of_sentence|eot_id|eom_id|bos|eos)\b\s*\|>""",
    )
    private val paragraphBreakRegex = Regex("""\n\s*\n+""")
    private val strongBoundaryChars = setOf('。', '！', '？', '!', '?', '；', ';')
    private val weakBoundaryChars = setOf('，', '、', '：')
    private val weakBoundaryCueWords = listOf(
        "他", "她", "我", "你", "对方", "随后", "接着", "紧接着", "然后",
        "可", "但", "却", "而", "于是", "片刻", "一时间", "下一秒", "与此同时", "忽然", "突然",
    )

    private const val SmartParagraphMinLength = 48
    private const val SmartParagraphSoftMax = 92
    private const val SmartParagraphHardMax = 144

    private data class NormalizedBlock(
        val text: String,
        val style: RoleplayLongformParagraphStyle = RoleplayLongformParagraphStyle(),
    )

    fun parseParagraphs(rawContent: String): List<RoleplayLongformParagraph> {
        val normalizedBlocks = normalizeParagraphBlocks(
            rawContent = rawContent,
            stripSupportedTags = false,
        )
        val shouldApplySmartFallback = normalizedBlocks.size <= 1
        return normalizedBlocks
            .flatMap { block ->
                val paragraph = parseParagraph(block)
                if (shouldApplySmartFallback) {
                    splitParagraphIfNeeded(paragraph)
                } else {
                    listOf(paragraph)
                }
            }
            .filter { paragraph -> paragraph.spans.isNotEmpty() && paragraph.plainText.isNotBlank() }
    }

    fun stripMarkupForDisplay(rawContent: String): String {
        return normalizeParagraphBlocks(
            rawContent = rawContent,
            stripSupportedTags = true,
        ).joinToString(separator = "\n\n") { block -> block.text }
            .trim()
    }

    fun splitDisplayParagraphs(rawContent: String): List<String> {
        val normalizedBlocks = normalizeParagraphBlocks(
            rawContent = rawContent,
            stripSupportedTags = true,
        )
        val shouldApplySmartFallback = normalizedBlocks.size <= 1
        return normalizedBlocks
            .flatMap { block ->
                if (shouldApplySmartFallback) {
                    splitNarrationText(block.text)
                } else {
                    listOf(block.text)
                }
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun parseParagraph(block: NormalizedBlock): RoleplayLongformParagraph {
        if (block.text.isBlank()) {
            return RoleplayLongformParagraph(emptyList())
        }

        val spans = mutableListOf<RoleplayLongformSpan>()
        var activeType = RoleplayLongformSpanType.NARRATION
        var cursor = 0
        val stack = ArrayDeque<RoleplayLongformSpanType>()

        parserTagRegex.findAll(block.text).forEach { match ->
            if (match.range.first > cursor) {
                appendSpan(
                    target = spans,
                    text = block.text.substring(cursor, match.range.first),
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

        if (cursor < block.text.length) {
            appendSpan(
                target = spans,
                text = block.text.substring(cursor),
                type = activeType,
            )
        }

        return RoleplayLongformParagraph(
            spans = spans,
            style = block.style,
        )
    }

    private fun splitParagraphIfNeeded(
        paragraph: RoleplayLongformParagraph,
    ): List<RoleplayLongformParagraph> {
        if (paragraph.plainText.length < SmartParagraphHardMax ||
            paragraph.spans.none { it.type == RoleplayLongformSpanType.NARRATION }
        ) {
            return listOf(paragraph)
        }

        data class ParagraphUnit(
            val span: RoleplayLongformSpan,
            val prefersBoundaryAfter: Boolean = false,
        )

        val units = buildList {
            paragraph.spans.forEach { span ->
                if (span.type != RoleplayLongformSpanType.NARRATION) {
                    add(ParagraphUnit(span))
                    return@forEach
                }
                val chunks = splitNarrationText(span.text)
                chunks.forEachIndexed { index, chunk ->
                    add(
                        ParagraphUnit(
                            span = span.copy(text = chunk),
                            prefersBoundaryAfter = index < chunks.lastIndex,
                        ),
                    )
                }
            }
        }
        if (units.none { it.prefersBoundaryAfter }) {
            return listOf(paragraph)
        }

        val paragraphs = mutableListOf<RoleplayLongformParagraph>()
        val currentSpans = mutableListOf<RoleplayLongformSpan>()
        var currentLength = 0

        fun flushCurrentParagraph() {
            if (currentSpans.isEmpty()) {
                return
            }
            paragraphs += RoleplayLongformParagraph(
                spans = currentSpans.toList(),
                style = paragraph.style,
            )
            currentSpans.clear()
            currentLength = 0
        }

        units.forEachIndexed { index, unit ->
            currentSpans += unit.span
            currentLength += unit.span.text.length
            val nextUnit = units.getOrNull(index + 1)
            val shouldFlush = when {
                unit.prefersBoundaryAfter &&
                    currentLength >= SmartParagraphMinLength &&
                    (nextUnit == null ||
                        nextUnit.span.type == RoleplayLongformSpanType.NARRATION ||
                        currentLength >= SmartParagraphSoftMax) -> true

                currentLength >= SmartParagraphHardMax &&
                    currentSpans.any { it.type == RoleplayLongformSpanType.NARRATION } -> true

                else -> false
            }
            if (shouldFlush) {
                flushCurrentParagraph()
            }
        }
        flushCurrentParagraph()
        return mergeTinyTrailingParagraphs(paragraphs)
    }

    private fun appendSpan(
        target: MutableList<RoleplayLongformSpan>,
        text: String,
        type: RoleplayLongformSpanType,
    ) {
        if (text.isBlank()) {
            return
        }

        val normalized = sanitizeLongformArtifacts(text)
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

    private fun normalizeParagraphBlocks(
        rawContent: String,
        stripSupportedTags: Boolean,
    ): List<NormalizedBlock> {
        var normalized = sanitizeLongformArtifacts(rawContent)
            .replace(danglingTagRegex, "")
            .trim()
        if (stripSupportedTags) {
            normalized = normalized.replace(supportedTagRegex, "")
        }
        if (normalized.isBlank()) {
            return emptyList()
        }

        val htmlBlocks = splitHtmlParagraphBlocks(normalized, stripSupportedTags)
        if (htmlBlocks != null) {
            return htmlBlocks
        }

        return normalizePlainParagraphBlocks(normalized)
    }

    private fun normalizePlainParagraphBlocks(
        normalized: String,
        style: RoleplayLongformParagraphStyle = RoleplayLongformParagraphStyle(),
    ): List<NormalizedBlock> {
        val explicitBlocks = normalized
            .split(paragraphBreakRegex)
            .mapNotNull { block ->
                normalizeInlineLineBreaks(block)
                    .takeIf { it.isNotBlank() }
                    ?.let { NormalizedBlock(it, style) }
            }
        if (explicitBlocks.size > 1) {
            return explicitBlocks
        }

        val nonBlankLines = normalized.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (looksLikeIntentionalSingleLineParagraphs(nonBlankLines)) {
            return nonBlankLines.map { NormalizedBlock(it, style) }
        }

        return listOf(NormalizedBlock(normalizeInlineLineBreaks(normalized), style))
    }

    private fun splitHtmlParagraphBlocks(
        rawContent: String,
        stripSupportedTags: Boolean,
    ): List<NormalizedBlock>? {
        val matches = htmlParagraphRegex.findAll(rawContent).toList()
        if (matches.isEmpty()) {
            return null
        }

        val blocks = mutableListOf<NormalizedBlock>()
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                val leadingText = rawContent.substring(cursor, match.range.first)
                blocks += normalizeHtmlFreeTextBlocks(
                    text = leadingText,
                    stripSupportedTags = stripSupportedTags,
                )
            }

            val attributes = match.groupValues[1]
            val innerText = match.groupValues[2]
            val style = parseHtmlParagraphStyle(attributes)
            val cleanedInnerText = normalizeHtmlText(innerText)
            blocks += normalizePlainParagraphBlocks(cleanedInnerText, style)
            cursor = match.range.last + 1
        }
        if (cursor < rawContent.length) {
            blocks += normalizeHtmlFreeTextBlocks(
                text = rawContent.substring(cursor),
                stripSupportedTags = stripSupportedTags,
            )
        }

        return blocks.filter { it.text.isNotBlank() }
    }

    private fun normalizeHtmlFreeTextBlocks(
        text: String,
        stripSupportedTags: Boolean,
    ): List<NormalizedBlock> {
        var cleaned = normalizeHtmlText(text).trim()
        if (stripSupportedTags) {
            cleaned = cleaned.replace(supportedTagRegex, "")
        }
        if (cleaned.isBlank()) {
            return emptyList()
        }
        return normalizePlainParagraphBlocks(cleaned)
    }

    private fun normalizeHtmlText(
        text: String,
    ): String {
        return decodeBasicHtmlEntities(text)
            .replace(htmlBreakRegex, "\n")
            .replace(remainingHtmlTagRegex, " ")
    }

    private fun parseHtmlParagraphStyle(
        attributes: String,
    ): RoleplayLongformParagraphStyle {
        val styleText = htmlStyleAttributeRegex.find(attributes)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimAttributeQuotes()
            .orEmpty()
            .lowercase()
        val alignText = htmlAlignAttributeRegex.find(attributes)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimAttributeQuotes()
            .orEmpty()
            .lowercase()
        val alignment = when {
            "text-align:center" in styleText || "text-align: center" in styleText || alignText == "center" ->
                RoleplayLongformParagraphAlignment.CENTER
            "text-align:right" in styleText || "text-align: right" in styleText || alignText == "right" ->
                RoleplayLongformParagraphAlignment.END
            else -> RoleplayLongformParagraphAlignment.START
        }
        val tone = when {
            "color:gray" in styleText ||
                "color: grey" in styleText ||
                "color:gray" in styleText ||
                "color: #808080" in styleText ||
                "color:#808080" in styleText ||
                "color: #888" in styleText ||
                "color:#888" in styleText -> RoleplayLongformParagraphTone.MUTED
            "color:primary" in styleText || "color: accent" in styleText -> RoleplayLongformParagraphTone.ACCENT
            else -> RoleplayLongformParagraphTone.DEFAULT
        }
        val fontScale = htmlFontSizeRegex.find(styleText)
            ?.let { match ->
                val value = match.groupValues[1].toFloatOrNull() ?: return@let null
                when (match.groupValues.getOrNull(2).orEmpty().lowercase()) {
                    "%" -> value / 100f
                    else -> value
                }
            }
            ?.coerceIn(0.72f, 1.32f)
            ?: 1.0f

        return RoleplayLongformParagraphStyle(
            alignment = alignment,
            tone = tone,
            fontScale = fontScale,
        )
    }

    private fun normalizeInlineLineBreaks(
        rawBlock: String,
    ): String {
        return rawBlock.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(separator = " ")
            .replace(Regex("""[ \t]{2,}"""), " ")
            .trim()
    }

    private fun looksLikeIntentionalSingleLineParagraphs(
        lines: List<String>,
    ): Boolean {
        if (lines.size <= 1) {
            return false
        }
        val paragraphLikeLines = lines.count { line ->
            val lastChar = line.lastOrNull()
            lastChar in strongBoundaryChars ||
                lastChar == '”' ||
                lastChar == '"' ||
                lastChar == '）' ||
                lastChar == ')'
        }
        return paragraphLikeLines >= (lines.size + 1) / 2
    }

    private fun splitNarrationText(
        rawText: String,
    ): List<String> {
        val text = rawText.replace(Regex("""\s+"""), " ").trim()
        if (text.length <= SmartParagraphHardMax) {
            return listOf(text)
        }

        val result = mutableListOf<String>()
        var start = 0
        var lastStrongBoundary = -1
        var lastWeakBoundary = -1

        fun cutAt(endExclusive: Int) {
            if (endExclusive <= start) {
                return
            }
            val chunk = text.substring(start, endExclusive).trim()
            if (chunk.isNotBlank()) {
                result += chunk
            }
            start = endExclusive
            while (start < text.length && text[start].isWhitespace()) {
                start++
            }
            if (lastStrongBoundary <= start) {
                lastStrongBoundary = -1
            }
            if (lastWeakBoundary <= start) {
                lastWeakBoundary = -1
            }
        }

        for (index in text.indices) {
            if (index < start) {
                continue
            }
            when {
                isStrongBoundary(text, index) -> lastStrongBoundary = index + 1
                isWeakBoundary(text, index) -> lastWeakBoundary = index + 1
            }

            val currentLength = index + 1 - start
            when {
                currentLength >= SmartParagraphHardMax -> {
                    val cutIndex = when {
                        lastStrongBoundary - start >= SmartParagraphMinLength -> lastStrongBoundary
                        lastWeakBoundary - start >= SmartParagraphMinLength -> lastWeakBoundary
                        else -> index + 1
                    }
                    cutAt(cutIndex)
                }

                currentLength >= SmartParagraphSoftMax &&
                    lastStrongBoundary - start >= SmartParagraphMinLength -> {
                    cutAt(lastStrongBoundary)
                }
            }
        }

        if (start < text.length) {
            cutAt(text.length)
        }

        return mergeTinyTrailingChunks(result)
    }

    private fun isStrongBoundary(
        text: String,
        index: Int,
    ): Boolean {
        val current = text[index]
        if (current in strongBoundaryChars) {
            return true
        }
        return when (current) {
            '”', '"', '）', ')' -> true
            else -> false
        }
    }

    private fun isWeakBoundary(
        text: String,
        index: Int,
    ): Boolean {
        val current = text[index]
        if (current !in weakBoundaryChars) {
            return false
        }
        val next = text.substring(index + 1).trimStart()
        return weakBoundaryCueWords.any { cue -> next.startsWith(cue) }
    }

    private fun mergeTinyTrailingChunks(
        chunks: List<String>,
    ): List<String> {
        if (chunks.size < 2) {
            return chunks
        }
        val merged = chunks.toMutableList()
        while (merged.size >= 2 && merged.last().length < 20) {
            val tail = merged.removeAt(merged.lastIndex)
            merged[merged.lastIndex] = "${merged.last()}$tail"
        }
        return merged
    }

    private fun mergeTinyTrailingParagraphs(
        paragraphs: List<RoleplayLongformParagraph>,
    ): List<RoleplayLongformParagraph> {
        if (paragraphs.size < 2) {
            return paragraphs
        }
        val merged = paragraphs.toMutableList()
        while (merged.size >= 2 && merged.last().plainText.length < 20) {
            val tail = merged.removeAt(merged.lastIndex)
            val previous = merged.removeAt(merged.lastIndex)
            if (previous.style != tail.style) {
                merged += previous
                merged += tail
                break
            }
            merged += RoleplayLongformParagraph(
                spans = previous.spans + tail.spans,
                style = previous.style,
            )
        }
        return merged
    }

    private fun String.trimAttributeQuotes(): String {
        return trim().trim('"', '\'')
    }

    private fun decodeBasicHtmlEntities(
        text: String,
    ): String {
        return text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
    }

    private fun sanitizeLongformArtifacts(
        rawContent: String,
    ): String {
        return rawContent.replace("\r\n", "\n")
            .replace(llmControlTagPattern, " ")
            .replace(llmControlWordPattern, " ")
            .replace(unsupportedProtocolTagRegex, " ")
            .replace(protocolAttributeNoisePattern, " ")
            .replace(Regex("""[ \t]{2,}"""), " ")
    }
}
