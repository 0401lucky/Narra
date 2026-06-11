package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceSynthesisSettingsTest {
    @Test
    fun normalizeMimoBaseUrl_blankInputFallsBackToDefault() {
        assertEquals(MIMO_DEFAULT_BASE_URL, normalizeMimoBaseUrl("   "))
    }

    @Test
    fun resolveMimoChatCompletionsEndpoint_appendsChatCompletionsPath() {
        val endpoint = resolveMimoChatCompletionsEndpoint("https://api.example.com/v1")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    @Test
    fun resolveMimoChatCompletionsEndpoint_keepsFullEndpoint() {
        val endpoint = resolveMimoChatCompletionsEndpoint("https://api.example.com/v1/chat/completions/")

        assertEquals("https://api.example.com/v1/chat/completions", endpoint)
    }

    @Test
    fun requireSecureMimoBaseUrl_rejectsRemoteHttpEndpoint() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            requireSecureMimoBaseUrl("http://api.example.com/v1")
        }

        assertEquals("MiMo Base URL 必须使用 https://，本机调试地址除外", error.message)
    }

    @Test
    fun requireSecureMimoBaseUrl_allowsLoopbackHttpEndpointForLocalTesting() {
        val normalized = requireSecureMimoBaseUrl("http://127.0.0.1:8080/v1")

        assertEquals("http://127.0.0.1:8080/v1", normalized)
    }
}
