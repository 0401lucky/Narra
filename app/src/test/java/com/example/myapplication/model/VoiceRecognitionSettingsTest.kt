package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceRecognitionSettingsTest {
    @Test
    fun normalized_trimsFieldsAndFallsBackToDefaultModel() {
        val normalized = VoiceRecognitionSettings(
            apiKey = "  sk-key  ",
            baseUrl = "  https://api.example.com/v1  ",
            model = "   ",
            language = "  zh  ",
        ).normalized()

        assertEquals("sk-key", normalized.apiKey)
        assertEquals("https://api.example.com/v1", normalized.baseUrl)
        assertEquals(DEFAULT_TRANSCRIPTION_MODEL, normalized.model)
        assertEquals("zh", normalized.language)
    }

    @Test
    fun hasUsableCredential_systemEngineNeedsNoCredential() {
        val settings = VoiceRecognitionSettings(engine = VoiceRecognitionEngine.SYSTEM)

        assertTrue(settings.hasUsableCredential())
    }

    @Test
    fun hasUsableCredential_remoteEngineRequiresBothKeyAndBaseUrl() {
        val base = VoiceRecognitionSettings(engine = VoiceRecognitionEngine.REMOTE_MODEL)

        assertFalse(base.hasUsableCredential())
        assertFalse(base.copy(apiKey = "sk-key").hasUsableCredential())
        assertFalse(base.copy(baseUrl = "https://api.example.com/v1").hasUsableCredential())
        assertTrue(
            base.copy(apiKey = "sk-key", baseUrl = "https://api.example.com/v1")
                .hasUsableCredential(),
        )
    }

    @Test
    fun isReadyForInput_requiresEnabledFlagEvenForSystemEngine() {
        val disabled = VoiceRecognitionSettings(enabled = false)
        val enabled = VoiceRecognitionSettings(enabled = true)

        assertFalse(disabled.isReadyForInput())
        assertTrue(enabled.isReadyForInput())
    }

    @Test
    fun stripSensitiveFields_removesApiKeyAndKeepsRest() {
        val stripped = VoiceRecognitionSettings(
            enabled = true,
            engine = VoiceRecognitionEngine.REMOTE_MODEL,
            apiKey = "sk-key",
            baseUrl = "https://api.example.com/v1",
            model = "whisper-large",
            language = "en",
        ).stripSensitiveFields()

        assertEquals("", stripped.apiKey)
        assertTrue(stripped.enabled)
        assertEquals(VoiceRecognitionEngine.REMOTE_MODEL, stripped.engine)
        assertEquals("https://api.example.com/v1", stripped.baseUrl)
        assertEquals("whisper-large", stripped.model)
        assertEquals("en", stripped.language)
    }

    @Test
    fun engineFromStorageValue_isCaseInsensitiveAndFallsBackToSystem() {
        assertEquals(VoiceRecognitionEngine.SYSTEM, VoiceRecognitionEngine.fromStorageValue("system"))
        assertEquals(
            VoiceRecognitionEngine.REMOTE_MODEL,
            VoiceRecognitionEngine.fromStorageValue("  REMOTE_MODEL  "),
        )
        assertEquals(VoiceRecognitionEngine.SYSTEM, VoiceRecognitionEngine.fromStorageValue("不认识的值"))
    }

    @Test
    fun requireSecureVoiceRecognitionBaseUrl_rejectsBlankMissingSchemeAndInsecureUrl() {
        val blank = assertThrows(IllegalArgumentException::class.java) {
            requireSecureVoiceRecognitionBaseUrl("   ")
        }
        assertEquals("语音识别 Base URL 为空", blank.message)

        val noScheme = assertThrows(IllegalArgumentException::class.java) {
            requireSecureVoiceRecognitionBaseUrl("api.example.com/v1")
        }
        assertEquals("语音识别 Base URL 必须以 http:// 或 https:// 开头", noScheme.message)

        val insecure = assertThrows(IllegalArgumentException::class.java) {
            requireSecureVoiceRecognitionBaseUrl("http://api.example.com/v1")
        }
        assertEquals("语音识别 Base URL 必须使用 https://，本机调试地址除外", insecure.message)
    }

    @Test
    fun requireSecureVoiceRecognitionBaseUrl_allowsLoopbackHttpForLocalDebugging() {
        assertEquals(
            "http://127.0.0.1:8080/v1",
            requireSecureVoiceRecognitionBaseUrl("http://127.0.0.1:8080/v1"),
        )
    }

    @Test
    fun resolveTranscriptionEndpoint_appendsPathOnlyWhenMissing() {
        assertEquals(
            "https://api.example.com/v1/audio/transcriptions",
            resolveTranscriptionEndpoint("https://api.example.com/v1"),
        )
        assertEquals(
            "https://api.example.com/v1/audio/transcriptions",
            resolveTranscriptionEndpoint("https://api.example.com/v1/"),
        )
        assertEquals(
            "https://api.example.com/v1/audio/transcriptions",
            resolveTranscriptionEndpoint("https://api.example.com/v1/audio/transcriptions"),
        )
    }
}
