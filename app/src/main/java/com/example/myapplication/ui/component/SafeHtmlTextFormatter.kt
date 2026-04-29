package com.example.myapplication.ui.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign

internal data class SafeHtmlTextBlock(
    val text: String,
    val textAlign: TextAlign? = null,
    val color: Color? = null,
    val fontScale: Float = 1f,
    val divider: Boolean = false,
)

private val SafeHtmlHintRegex = Regex(
    pattern = """(?is)<\s*/?\s*(content|p|div|span|center|br|hr|strong|b|em|i|u|s|small|font|h[1-6]|blockquote|ul|ol|li)\b[^>]*>""",
)
private val DangerousHtmlBlockRegex = Regex(
    pattern = """(?is)<\s*(script|style|iframe|object|embed|canvas|svg)\b.*?>.*?<\s*/\s*\1\s*>""",
)
private val BlockTagRegex = Regex(
    pattern = """(?is)<\s*(p|div|center|h[1-6]|blockquote)\b([^>]*)>(.*?)<\s*/\s*\1\s*>""",
)
private val AttributeRegex = Regex("""(?is)([a-zA-Z][\w:-]*)\s*=\s*(['"])(.*?)\2""")
private val StyleRuleRegex = Regex("""(?is)([a-zA-Z-]+)\s*:\s*([^;]+)""")
private val RemainingTagRegex = Regex("""(?is)<[^>]+>""")

internal fun parseSafeHtmlTextBlocks(rawText: String): List<SafeHtmlTextBlock>? {
    val normalized = rawText.replace("\r\n", "\n").trim()
    if (normalized.isBlank() || "```" in normalized || !SafeHtmlHintRegex.containsMatchIn(normalized)) {
        return null
    }

    val safeText = DangerousHtmlBlockRegex
        .replace(normalized, "")
        .replace(Regex("""(?is)<\s*/?\s*content\b[^>]*>"""), "\n\n")

    val blocks = mutableListOf<SafeHtmlTextBlock>()
    var cursor = 0
    BlockTagRegex.findAll(safeText).forEach { match ->
        if (match.range.first > cursor) {
            appendPlainSafeHtmlBlocks(
                target = blocks,
                raw = safeText.substring(cursor, match.range.first),
            )
        }
        val tag = match.groupValues.getOrNull(1).orEmpty().lowercase()
        val attributes = match.groupValues.getOrNull(2).orEmpty().parseAttributes()
        val inner = match.groupValues.getOrNull(3).orEmpty()
        val text = stripSafeHtmlTags(inner).trim()
        if (text.isNotBlank()) {
            blocks += SafeHtmlTextBlock(
                text = text,
                textAlign = resolveTextAlign(tag, attributes),
                color = resolveColor(attributes),
                fontScale = resolveFontScale(tag, attributes),
            )
        }
        cursor = match.range.last + 1
    }
    if (cursor < safeText.length) {
        appendPlainSafeHtmlBlocks(
            target = blocks,
            raw = safeText.substring(cursor),
        )
    }

    val normalizedBlocks = blocks
        .filter { it.divider || it.text.isNotBlank() }
        .fold(mutableListOf<SafeHtmlTextBlock>()) { result, block ->
            if (block.divider || result.lastOrNull()?.divider == true || result.isEmpty()) {
                result += block
            } else if (
                block.textAlign == null &&
                block.color == null &&
                block.fontScale == 1f &&
                result.last().textAlign == null &&
                result.last().color == null &&
                result.last().fontScale == 1f
            ) {
                result[result.lastIndex] = result.last().copy(
                    text = listOf(result.last().text, block.text)
                        .filter(String::isNotBlank)
                        .joinToString(separator = "\n\n"),
                )
            } else {
                result += block
            }
            result
        }

    return normalizedBlocks.takeIf { blocksForDisplay ->
        blocksForDisplay.any { block ->
            block.divider ||
                block.textAlign != null ||
                block.color != null ||
                block.fontScale != 1f
        } || stripSafeHtmlTags(normalized) != normalized
    }
}

private fun appendPlainSafeHtmlBlocks(
    target: MutableList<SafeHtmlTextBlock>,
    raw: String,
) {
    val stripped = stripSafeHtmlTags(raw)
    stripped
        .split(Regex("""\n{2,}"""))
        .map(String::trim)
        .filter(String::isNotBlank)
        .forEach { text ->
            if (text == "---") {
                target += SafeHtmlTextBlock(text = "", divider = true)
            } else {
                target += SafeHtmlTextBlock(text = text)
            }
        }
}

private fun stripSafeHtmlTags(raw: String): String {
    return raw
        .replace(Regex("""(?is)<\s*br\s*/?\s*>"""), "\n")
        .replace(Regex("""(?is)<\s*hr\s*/?\s*>"""), "\n---\n")
        .replace(Regex("""(?is)<\s*li\b[^>]*>"""), "\n- ")
        .replace(Regex("""(?is)<\s*/\s*li\s*>"""), "\n")
        .replace(Regex("""(?is)<\s*/\s*(p|div|center|blockquote|h[1-6]|ul|ol)\s*>"""), "\n\n")
        .replace(Regex("""(?is)<\s*(ul|ol)\b[^>]*>"""), "\n")
        .replace(RemainingTagRegex, "")
        .decodeBasicHtmlEntities()
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}

private fun String.parseAttributes(): Map<String, String> {
    return AttributeRegex.findAll(this).associate { match ->
        match.groupValues[1].lowercase() to match.groupValues[3]
    }
}

private fun resolveTextAlign(
    tag: String,
    attributes: Map<String, String>,
): TextAlign? {
    val align = attributes["align"].orEmpty().ifBlank {
        attributes.styleValue("text-align")
    }.trim().lowercase()
    return when {
        tag == "center" -> TextAlign.Center
        align in setOf("center", "middle") -> TextAlign.Center
        align in setOf("right", "end") -> TextAlign.End
        align in setOf("left", "start") -> TextAlign.Start
        else -> null
    }
}

private fun resolveFontScale(
    tag: String,
    attributes: Map<String, String>,
): Float {
    val fromTag = when {
        tag == "h1" -> 1.32f
        tag == "h2" -> 1.22f
        tag == "h3" -> 1.14f
        tag == "h4" || tag == "h5" || tag == "h6" -> 1.08f
        tag == "blockquote" -> 0.96f
        else -> 1f
    }
    val fontSize = attributes.styleValue("font-size").trim().lowercase()
    if (fontSize.isBlank()) {
        return fromTag
    }
    return when {
        fontSize.endsWith("em") -> fontSize.removeSuffix("em").toFloatOrNull()
        fontSize.endsWith("rem") -> fontSize.removeSuffix("rem").toFloatOrNull()
        fontSize.endsWith("%") -> fontSize.removeSuffix("%").toFloatOrNull()?.div(100f)
        fontSize.endsWith("px") -> fontSize.removeSuffix("px").toFloatOrNull()?.div(16f)
        else -> fontSize.toFloatOrNull()
    }?.coerceIn(0.72f, 1.6f) ?: fromTag
}

private fun resolveColor(attributes: Map<String, String>): Color? {
    val raw = attributes["color"].orEmpty().ifBlank {
        attributes.styleValue("color")
    }.trim()
    return parseSafeCssColor(raw)
}

private fun Map<String, String>.styleValue(name: String): String {
    val style = this["style"].orEmpty()
    if (style.isBlank()) {
        return ""
    }
    return StyleRuleRegex.findAll(style)
        .firstOrNull { match -> match.groupValues[1].trim().equals(name, ignoreCase = true) }
        ?.groupValues
        ?.getOrNull(2)
        .orEmpty()
}

private fun parseSafeCssColor(raw: String): Color? {
    val value = raw.trim().lowercase()
    if (value.isBlank()) {
        return null
    }
    if (value.startsWith("#")) {
        val hex = value.removePrefix("#")
        val argb = when (hex.length) {
            3 -> {
                val expanded = hex.map { "$it$it" }.joinToString(separator = "")
                "ff$expanded"
            }
            6 -> "ff$hex"
            8 -> hex
            else -> return null
        }
        return argb.toLongOrNull(16)?.let { Color(it) }
    }
    return when (value) {
        "gray", "grey" -> Color(0xFF8A8A8A)
        "silver" -> Color(0xFFB8B8B8)
        "white" -> Color.White
        "black" -> Color.Black
        "red" -> Color(0xFFE05A5A)
        "blue" -> Color(0xFF5D8FE8)
        "green" -> Color(0xFF4FA363)
        "yellow" -> Color(0xFFD5A93F)
        "purple" -> Color(0xFF9A6BE8)
        "pink" -> Color(0xFFE47DA8)
        else -> null
    }
}

private fun String.decodeBasicHtmlEntities(): String {
    return replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
}
