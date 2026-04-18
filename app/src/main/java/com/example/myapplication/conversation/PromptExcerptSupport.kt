package com.example.myapplication.conversation

internal object PromptExcerptSupport {
    private val headerDelimiters = listOf(": ", "：")

    fun joinLatestSegments(
        segments: List<String>,
        maxLength: Int,
        separator: String = "\n",
    ): String {
        if (maxLength <= 0) {
            return ""
        }
        val normalizedSegments = segments
            .map { segment -> segment.trim() }
            .filter { segment -> segment.isNotEmpty() }
        if (normalizedSegments.isEmpty()) {
            return ""
        }

        val selectedSegments = ArrayDeque<String>()
        var totalLength = 0
        for (segment in normalizedSegments.asReversed()) {
            val extraLength = if (selectedSegments.isEmpty()) {
                segment.length
            } else {
                separator.length + segment.length
            }
            if (totalLength + extraLength <= maxLength) {
                selectedSegments.addFirst(segment)
                totalLength += extraLength
                continue
            }
            if (selectedSegments.isEmpty()) {
                selectedSegments.addFirst(trimSegmentToLatestContent(segment, maxLength))
            }
            break
        }
        return selectedSegments.joinToString(separator = separator)
    }

    private fun trimSegmentToLatestContent(
        segment: String,
        maxLength: Int,
    ): String {
        if (segment.length <= maxLength) {
            return segment
        }
        val delimiter = headerDelimiters.firstOrNull { segment.contains(it) }
        if (delimiter != null) {
            val delimiterIndex = segment.indexOf(delimiter)
            if (delimiterIndex >= 0) {
                val header = segment.substring(0, delimiterIndex + delimiter.length)
                val content = segment.substring(delimiterIndex + delimiter.length)
                if (header.length + 2 <= maxLength) {
                    val contentTailLength = maxLength - header.length - 1
                    return header + "…" + content.takeLast(contentTailLength)
                }
            }
        }
        return if (maxLength == 1) {
            segment.takeLast(1)
        } else {
            "…" + segment.takeLast(maxLength - 1)
        }
    }
}
