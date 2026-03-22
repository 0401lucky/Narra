package com.example.myapplication.model

fun detectLanguageLabel(text: String): String {
    val normalized = text.trim()
    if (normalized.isBlank()) {
        return "待检测"
    }

    var hasChinese = false
    var hasJapanese = false
    var hasKorean = false
    var hasLatin = false

    normalized.forEach { char ->
        when {
            char in '\u3040'..'\u30ff' -> hasJapanese = true
            char in '\uac00'..'\ud7af' -> hasKorean = true
            char in '\u4e00'..'\u9fff' -> hasChinese = true
            char.isLetter() && char.code < 128 -> hasLatin = true
        }
    }

    val languageCount = listOf(hasChinese, hasJapanese, hasKorean, hasLatin).count { it }
    if (languageCount > 1) {
        return "混合文本"
    }

    return when {
        hasJapanese -> "日语"
        hasKorean -> "韩语"
        hasChinese -> "中文"
        hasLatin -> "英语"
        else -> "未知"
    }
}
