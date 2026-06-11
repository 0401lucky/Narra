package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.system.security.SensitiveTextRedactor

internal object MemoryToolPayloadPolicy {
    const val MAX_CONTENT_LENGTH = 600
    const val MAX_REASON_LENGTH = 240
    const val MAX_QUERY_LENGTH = 120

    fun normalizeContent(value: String): String {
        return sanitize(value).take(MAX_CONTENT_LENGTH)
    }

    fun normalizeReason(value: String): String {
        return sanitize(value).take(MAX_REASON_LENGTH)
    }

    fun normalizeQuery(value: String): String {
        return sanitize(value).take(MAX_QUERY_LENGTH)
    }

    fun isContentTooLong(value: String): Boolean {
        return sanitize(value).length > MAX_CONTENT_LENGTH
    }

    fun isReasonTooLong(value: String): Boolean {
        return sanitize(value).length > MAX_REASON_LENGTH
    }

    private fun sanitize(value: String): String {
        val normalizedWhitespace = value
            .replace("\r\n", "\n")
            .trim()
            .replace(Regex("\\s+"), " ")
            .removePrefix("-")
            .removePrefix("•")
            .trim()
        return SensitiveTextRedactor.redact(normalizedWhitespace, maxLength = Int.MAX_VALUE).trim()
    }
}
