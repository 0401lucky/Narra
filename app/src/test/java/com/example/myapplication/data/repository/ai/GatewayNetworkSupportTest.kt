package com.example.myapplication.data.repository.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class GatewayNetworkSupportTest {
    @Test
    fun retrofitFailure_redactsErrorBodySecrets() {
        val response = Response.error<Unit>(
            401,
            secretErrorBody().toResponseBody("application/json".toMediaType()),
        )

        val error = GatewayNetworkSupport.retrofitFailure("聊天请求失败", response)

        assertTrue(error.message.orEmpty().contains("聊天请求失败：401"))
        assertErrorMessageIsRedacted(error.message)
    }

    @Test
    fun okhttpFailure_redactsErrorBodySecrets() {
        val response = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://example.com/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(401)
            .message("Unauthorized")
            .body(secretErrorBody().toResponseBody("application/json".toMediaType()))
            .build()

        val error = response.use {
            GatewayNetworkSupport.okhttpFailure("聊天请求失败", it)
        }

        assertTrue(error.message.orEmpty().contains("聊天请求失败：401"))
        assertErrorMessageIsRedacted(error.message)
    }

    @Test
    fun okhttpFailure_marksContentSafetyError() {
        val response = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://example.com/v1/chat/completions").build())
            .protocol(Protocol.HTTP_1_1)
            .code(400)
            .message("Bad Request")
            .body(
                """
                {
                  "code": "data_inspection_failed",
                  "message": "Output data may contain inappropriate content."
                }
                """.trimIndent().toResponseBody("application/json".toMediaType()),
            )
            .build()

        val error = response.use {
            GatewayNetworkSupport.okhttpFailure("聊天请求失败", it)
        }

        val message = error.message.orEmpty()
        assertTrue(message.contains("聊天请求失败：400"))
        assertTrue(message.contains("内容被模型或供应商安全策略拦截"))
    }

    private fun secretErrorBody(): String {
        return """
            {
              "error": "Authorization: Bearer sk-secret
              api-key: header-secret",
              "api_key": "json-secret",
              "token": "token-secret",
              "url": "https://example.com/v1?api_key=query-secret&signature=sig-secret",
              "image": "data:image/png;base64,QUJDRA=="
            }
        """.trimIndent()
    }

    private fun assertErrorMessageIsRedacted(message: String?) {
        val text = message.orEmpty()
        assertFalse(text.contains("sk-secret"))
        assertFalse(text.contains("header-secret"))
        assertFalse(text.contains("json-secret"))
        assertFalse(text.contains("token-secret"))
        assertFalse(text.contains("query-secret"))
        assertFalse(text.contains("sig-secret"))
        assertFalse(text.contains("QUJDRA=="))
        assertTrue(text.contains("[REDACTED]"))
    }
}
