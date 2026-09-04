package com.example.myapplication.data.repository.asr

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只覆盖错误码到中文文案的纯映射。
 * [SystemSpeechRecognitionService] 本身依赖真实的 SpeechRecognizer 与麦克风，
 * 在 JVM 单测里无法可靠伪造，需真机验证。
 */
class SystemRecognitionErrorMessageTest {
    @Test
    fun errorMessage_mapsKnownCodesToActionableChineseText() {
        assertEquals("没听清，再说一次？", systemRecognitionErrorMessage(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(
            "没有检测到说话内容",
            systemRecognitionErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT),
        )
        assertEquals("网络不可用，系统识别失败", systemRecognitionErrorMessage(SpeechRecognizer.ERROR_NETWORK))
        assertEquals(
            "网络超时，系统识别失败",
            systemRecognitionErrorMessage(SpeechRecognizer.ERROR_NETWORK_TIMEOUT),
        )
        assertEquals("录音出错，请检查麦克风", systemRecognitionErrorMessage(SpeechRecognizer.ERROR_AUDIO))
        assertEquals(
            "未获得录音权限",
            systemRecognitionErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS),
        )
        assertEquals(
            "识别服务正忙，稍后再试",
            systemRecognitionErrorMessage(SpeechRecognizer.ERROR_RECOGNIZER_BUSY),
        )
    }

    @Test
    fun errorMessage_unknownCodeStillReadableAndCarriesCode() {
        val message = systemRecognitionErrorMessage(9999)

        assertTrue(message.contains("9999"))
        assertTrue(message.startsWith("系统语音识别失败"))
    }

    @Test
    fun errorMessage_neverLeaksEnglishStackTraceStyleText() {
        val codes = listOf(
            SpeechRecognizer.ERROR_NO_MATCH,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER,
            9999,
        )

        codes.forEach { code ->
            val message = systemRecognitionErrorMessage(code)
            assertTrue("错误文案不应为空：$code", message.isNotBlank())
            assertFalse("错误文案不应包含异常类名：$message", message.contains("Exception"))
        }
    }
}
