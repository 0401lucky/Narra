package com.example.myapplication.model

import android.graphics.Rect

data class ScreenTranslationSettings(
    val serviceEnabled: Boolean = false,
    val overlayEnabled: Boolean = true,
    val overlayOffsetX: Float = 0.85f,
    val overlayOffsetY: Float = 0.42f,
    val targetLanguage: String = "简体中文",
    val selectedTextEnabled: Boolean = true,
    val showSourceText: Boolean = true,
    val vendorGuideDismissed: Boolean = false,
)

enum class TranslationSourceType {
    MANUAL,
    CHAT,
    SCREEN_CAPTURE,
    SELECTED_TEXT,
}

data class ScreenTextBlock(
    val id: String,
    val text: String,
    val bounds: Rect,
    val confidence: Float = 0f,
    val orderIndex: Int = 0,
)

data class ScreenTranslationRequest(
    val sourceType: TranslationSourceType,
    val appPackage: String = "",
    val appLabel: String = "",
    val targetLanguage: String,
    val segments: List<ScreenTextBlock>,
)

data class ScreenTranslationSegmentResult(
    val sourceText: String,
    val translatedText: String,
    val bounds: Rect,
    val orderIndex: Int,
)

data class ScreenTranslationResult(
    val sourceType: TranslationSourceType,
    val sourceAppPackage: String = "",
    val sourceAppLabel: String = "",
    val targetLanguage: String,
    val originalSegments: List<ScreenTextBlock> = emptyList(),
    val translatedSegments: List<ScreenTranslationSegmentResult> = emptyList(),
    val fullTranslation: String = "",
    val createdAt: Long,
)
