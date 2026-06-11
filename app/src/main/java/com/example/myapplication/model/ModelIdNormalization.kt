package com.example.myapplication.model

private val MODEL_ALIAS_SEPARATOR_PATTERN = Regex("[\\s_]+")
private val MODEL_ALIAS_DASH_PATTERN = Regex("-+")
private val GOOGLE_GEMINI_DISPLAY_PREFIX_PATTERN = Regex("^google[\\s_-]+gemini\\b")

/**
 * 将常见显示名写法归一化为接口可用的模型 ID。
 */
fun normalizeKnownModelId(modelId: String): String {
    val trimmed = modelId.trim()
    if (trimmed.isBlank()) {
        return ""
    }

    val lower = trimmed.lowercase()
    val normalizedGemini = lower
        .replace(GOOGLE_GEMINI_DISPLAY_PREFIX_PATTERN, "gemini")
        .replace(MODEL_ALIAS_SEPARATOR_PATTERN, "-")
        .replace(MODEL_ALIAS_DASH_PATTERN, "-")
        .trim('-')

    return if (normalizedGemini == "gemini" || normalizedGemini.startsWith("gemini-")) {
        normalizedGemini
    } else {
        trimmed
    }
}
