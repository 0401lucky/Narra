package com.example.myapplication.system.security

object SensitiveTextRedactor {
    private const val DEFAULT_UI_ERROR_MAX_LENGTH = 160

    private val headerPattern = Regex(
        pattern = """(?i)\b(authorization|api-key|x-api-key|x-subscription-token)\s*:\s*(Bearer\s+)?[^\s,;"}]+""",
    )
    private val bearerPattern = Regex("""(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+""")
    private val nakedSecretPattern = Regex("""(?i)\b(?:sk|rk|pk|sess)-[A-Za-z0-9._~+/=-]{8,}\b""")
    private val jsonSecretPattern = Regex(
        pattern = """(?i)"(api_key|apiKey|apikey|api-key|token|access_token|client_secret|password|secret|signature|b64_json|data)"\s*:\s*"[^"]*"""",
    )
    private val querySecretPattern = Regex(
        pattern = """(?i)([?&](?:key|api_key|apikey|api-key|token|access_token|client_secret|password|secret|signature)=)[^&#\s]+""",
    )
    private val dataUriPattern = Regex(
        pattern = """(?i)data:(audio|image)/[^;,\s]+;base64,[A-Za-z0-9+/=\r\n]+""",
    )
    private val localPathPattern = Regex(
        pattern = """(?i)([A-Z]:\\(?:[^\\/:*?"<>|\r\n]+\\?)+|/(?:data/user/0|storage/emulated|Users|home|var|tmp)/[^\s,;"]+)""",
    )
    private val sqlPattern = Regex(
        pattern = """(?is)\b(?:select|insert|update|delete|create|drop|alter)\b.{0,120}\b(?:from|into|set|table|where|values)\b""",
    )
    private val stackTracePattern = Regex(
        pattern = """(?m)^\s*at\s+[\w.$]+\(.*:\d+\)\s*$""",
    )

    fun redact(
        text: String,
        maxLength: Int = 160,
    ): String {
        val redacted = text
            .replace(dataUriPattern) { match ->
                "data:${match.groupValues[1].lowercase()}/redacted;base64,[REDACTED]"
            }
            .replace(headerPattern) { match ->
                val header = match.groupValues[1]
                if (match.groupValues[2].isBlank()) {
                    "$header: [REDACTED]"
                } else {
                    "$header: Bearer [REDACTED]"
                }
            }
            .replace(jsonSecretPattern) { match ->
                val key = match.groupValues[1]
                """"$key":"[REDACTED]""""
            }
            .replace(querySecretPattern) { match ->
                "${match.groupValues[1]}[REDACTED]"
            }
            .replace(bearerPattern, "Bearer [REDACTED]")
            .replace(nakedSecretPattern, "[REDACTED]")
        return if (redacted.length > maxLength) {
            redacted.take(maxLength)
        } else {
            redacted
        }
    }

    fun throwableMessageForUi(
        throwable: Throwable,
        fallback: String,
        maxLength: Int = DEFAULT_UI_ERROR_MAX_LENGTH,
    ): String {
        val raw = throwable.message
            ?: throwable.localizedMessage
            ?: throwable::class.java.simpleName
        val redacted = redact(raw, maxLength = maxLength).trim()
        if (redacted.isBlank()) {
            return fallback
        }
        return if (containsInternalDetails(raw) || containsInternalDetails(redacted)) {
            fallback
        } else {
            redacted
        }
    }

    private fun containsInternalDetails(text: String): Boolean {
        return localPathPattern.containsMatchIn(text) ||
            sqlPattern.containsMatchIn(text) ||
            stackTracePattern.containsMatchIn(text)
    }
}
