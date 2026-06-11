package com.example.myapplication.system.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveTextRedactorTest {
    @Test
    fun redact_masksHeadersJsonQueryAndDataUri() {
        val raw = """
            HTTP 401 request-id=req_123 retry-after=3
            Authorization: Bearer sk-secret
            api-key: mimo-secret
            {"api_key":"json-secret","token":"token-secret","b64_json":"AAAA"}
            https://example.com/v1?api_key=query-secret&signature=sig-secret&access_token=access-secret&client_secret=client-secret
            data:audio/wav;base64,UklGRgAAAAAAAAAA
            sk-naked-secret-token
        """.trimIndent()

        val redacted = SensitiveTextRedactor.redact(raw, maxLength = 2_000)

        assertFalse(redacted.contains("sk-secret"))
        assertFalse(redacted.contains("mimo-secret"))
        assertFalse(redacted.contains("json-secret"))
        assertFalse(redacted.contains("token-secret"))
        assertFalse(redacted.contains("query-secret"))
        assertFalse(redacted.contains("sig-secret"))
        assertFalse(redacted.contains("access-secret"))
        assertFalse(redacted.contains("client-secret"))
        assertFalse(redacted.contains("UklGRgAAAAAAAAAA"))
        assertFalse(redacted.contains("sk-naked-secret-token"))
        assertTrue(redacted.contains("HTTP 401"))
        assertTrue(redacted.contains("request-id=req_123"))
        assertTrue(redacted.contains("retry-after=3"))
    }

    @Test
    fun throwableMessageForUi_fallsBackForInternalDetails() {
        val fallback = "发送失败，请检查网络或模型配置后重试"

        val pathMessage = SensitiveTextRedactor.throwableMessageForUi(
            throwable = IllegalStateException("""failed at C:\Users\me\.config\secret.json"""),
            fallback = fallback,
        )
        val sqlMessage = SensitiveTextRedactor.throwableMessageForUi(
            throwable = IllegalStateException("SELECT api_key FROM provider_tokens WHERE id = 1"),
            fallback = fallback,
        )

        assertTrue(pathMessage == fallback)
        assertTrue(sqlMessage == fallback)
    }

    @Test
    fun throwableMessageForUi_redactsSecretButKeepsSafeContext() {
        val message = SensitiveTextRedactor.throwableMessageForUi(
            throwable = IllegalStateException("HTTP 401 Authorization: Bearer sk-secret-token request-id=req_1"),
            fallback = "发送失败",
            maxLength = 2_000,
        )

        assertFalse(message.contains("sk-secret-token"))
        assertTrue(message.contains("HTTP 401"))
        assertTrue(message.contains("Bearer [REDACTED]"))
        assertTrue(message.contains("request-id=req_1"))
    }
}
