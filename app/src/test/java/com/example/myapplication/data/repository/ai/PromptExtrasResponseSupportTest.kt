package com.example.myapplication.data.repository.ai

import okhttp3.Headers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptExtrasResponseSupportTest {
    @Test
    fun buildHttpFailure_redactsErrorDetailAndKeepsRequestMetadata() {
        val error = PromptExtrasResponseSupport.buildHttpFailure(
            operation = "聊天请求失败",
            code = 429,
            errorDetail = """
                Authorization: Bearer sk-secret
                {"api_key":"json-secret","token":"token-secret"}
                https://example.com/v1?api_key=query-secret&signature=sig-secret
            """.trimIndent(),
            headers = Headers.Builder()
                .add("x-request-id", "req_123")
                .add("retry-after", "3")
                .build(),
        )

        val message = error.message.orEmpty()
        assertTrue(message.contains("聊天请求失败：429"))
        assertTrue(message.contains("request-id: req_123"))
        assertTrue(message.contains("retry-after: 3"))
        assertFalse(message.contains("sk-secret"))
        assertFalse(message.contains("json-secret"))
        assertFalse(message.contains("token-secret"))
        assertFalse(message.contains("query-secret"))
        assertFalse(message.contains("sig-secret"))
        assertTrue(message.contains("[REDACTED]"))
    }

    @Test
    fun buildHttpFailure_marksContentSafetyError() {
        val error = PromptExtrasResponseSupport.buildHttpFailure(
            operation = "聊天请求失败",
            code = 400,
            errorDetail = """
                {
                  "code": "data_inspection_failed",
                  "message": "Input data may contain inappropriate content."
                }
            """.trimIndent(),
            headers = Headers.Builder().build(),
        )

        val message = error.message.orEmpty()
        assertTrue(message.contains("聊天请求失败：400"))
        assertTrue(message.contains("内容被模型或供应商安全策略拦截"))
        assertFalse(message.contains("请求参数或供应商兼容性问题"))
    }
}
