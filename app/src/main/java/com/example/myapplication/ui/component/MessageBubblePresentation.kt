package com.example.myapplication.ui.component

private val MessageBubbleMarkdownInlineHintRegex = Regex(
    """(```|`[^`\n]+`|\[[^\]]+]\([^)]+\)|!\[[^\]]*]\([^)]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__|~~[^~\n]+~~)""",
)
internal fun shouldRenderWithMarkdown(text: String): Boolean {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return false
    }
    if ("```" in normalized) {
        return true
    }

    if (containsStructuredMarkdown(normalized)) {
        return true
    }

    return MessageBubbleMarkdownInlineHintRegex.containsMatchIn(normalized)
}

internal fun shouldRenderWithMarkdownDuringScrolling(text: String): Boolean {
    return shouldRenderWithMarkdown(text)
}
