package com.example.myapplication.data.repository.ai

import okhttp3.Headers

internal object PromptExtrasResponseSupport {
    private val ContentSafetyHints = listOf(
        "data_inspection_failed",
        "content_filter",
        "inappropriate content",
        "responsibleaipolicyviolation",
        "content management policy",
        "safety policy",
        "policy violation",
        "moderation",
        "blocked by",
        "risk control",
        "sensitive content",
        "unsafe content",
        "违规",
        "敏感内容",
        "安全策略",
        "内容安全",
        "风控",
        "审核",
        "拦截",
    )

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
        val guidance = contentSafetyGuidance(errorDetail) ?: when (code) {
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
                val normalizedErrorDetail = AiErrorRedaction.redact(errorDetail)
                if (normalizedErrorDetail.isNotBlank()) {
                    append('\n')
                    append(normalizedErrorDetail)
                }
            },
        )
    }

    fun contentSafetyGuidance(errorDetail: String): String? {
        val normalized = errorDetail.lowercase()
        if (normalized.isBlank()) {
            return null
        }
        return if (ContentSafetyHints.any(normalized::contains)) {
            "内容被模型或供应商安全策略拦截，请调整输入、预设或角色设定后再试；这不是 Base URL 或模型名配置问题"
        } else {
            null
        }
    }

    private fun extractContentPartText(contentPart: Any?): String {
        return when (contentPart) {
            is String -> contentPart
            is Map<*, *> -> (contentPart["text"] as? String).orEmpty()
            else -> ""
        }
    }
}
