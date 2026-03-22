package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageDetectionTest {
    @Test
    fun detectLanguageLabel_detectsChinese() {
        assertEquals("中文", detectLanguageLabel("你好，世界"))
    }

    @Test
    fun detectLanguageLabel_detectsEnglish() {
        assertEquals("英语", detectLanguageLabel("Hello world"))
    }

    @Test
    fun detectLanguageLabel_detectsJapanese() {
        assertEquals("日语", detectLanguageLabel("こんにちは"))
    }

    @Test
    fun detectLanguageLabel_detectsMixedText() {
        assertEquals("混合文本", detectLanguageLabel("你好 hello"))
    }
}
