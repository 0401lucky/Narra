package com.example.myapplication.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiServiceFactoryTest {
    private val factory = ApiServiceFactory()

    @Test
    fun normalizeBaseUrl_trimsAndAppendsTrailingSlash() {
        val normalized = factory.normalizeBaseUrl("  https://api.openai.com/v1  ")

        assertEquals("https://api.openai.com/v1/", normalized)
    }

    @Test
    fun normalizeBaseUrl_keepsExistingTrailingSlash() {
        val normalized = factory.normalizeBaseUrl("https://api.openai.com/v1/")

        assertEquals("https://api.openai.com/v1/", normalized)
    }

    @Test
    fun normalizeBaseUrl_rejectsBlankInput() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            factory.normalizeBaseUrl("   ")
        }

        assertEquals("请先填写 Base URL", error.message)
    }
}
