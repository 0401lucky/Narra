package com.example.myapplication.data.repository.ai

import okhttp3.Headers

internal object PromptExtrasResponseSupport {
    fun extractContentText(content: Any?): String {
        return when (content) {
            null -> ""
            is String -> content
            is List<*> -> content.mapNotNull(::extractContentPartText)
                .filter { it.isNotBlank() }
                .joinToString(separator = "\n\n")
            is Map<*, *> -> extractContentPartText(content)
            else -> ""
        }
    }

    fun <T> retrofitFailure(
        operation: String,
        response: retrofit2.Response<T>,
    ): IllegalStateException {
        val errorDetail = response.errorBody()?.string().orEmpty()
        return buildHttpFailure(
            operation = operation,
            code = response.code(),
            errorDetail = errorDetail,
            headers = response.headers(),
        )
    }

    fun buildHttpFailure(
        operation: String,
        code: Int,
        errorDetail: String,
        headers: Headers,
    ): IllegalStateException {
        val guidance = when (code) {
            400 -> "请求参数或供应商兼容性问题，请检查 Base URL、模型名与请求参数"
            429 -> "请求过于频繁或额度不足，请稍后重试"
            else -> ""
        }
        val requestId = headers["x-request-id"]
            ?: headers["request-id"]
            ?: headers["openai-request-id"]
            ?: headers["anthropic-request-id"]
        val retryAfter = headers["retry-after"].orEmpty()
        return IllegalStateException(
            buildString {
                append(operation)
                append('：')
                append(code)
                if (guidance.isNotBlank()) {
                    append('（')
                    append(guidance)
                    append('）')
                }
                if (!requestId.isNullOrBlank()) {
                    append("\nrequest-id: ")
                    append(requestId)
                }
                if (retryAfter.isNotBlank()) {
                    append("\nretry-after: ")
                    append(retryAfter)
                }
                val normalizedErrorDetail = errorDetail.trim()
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    private fun extractContentPartText(contentPart: Any?): String {
        return when (contentPart) {
            is String -> contentPart
            is Map<*, *> -> (contentPart["text"] as? String).orEmpty()
            else -> ""
        }
    }
}
