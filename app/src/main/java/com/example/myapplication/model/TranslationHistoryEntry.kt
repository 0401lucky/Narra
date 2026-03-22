package com.example.myapplication.model

data class TranslationHistoryEntry(
    val id: String,
    val sourceText: String,
    val translatedText: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val modelName: String,
    val createdAt: Long,
    val sourceType: TranslationSourceType? = null,
    val sourceAppPackage: String? = null,
    val sourceAppLabel: String? = null,
)

fun TranslationHistoryEntry.resolvedSourceType(): TranslationSourceType {
    return sourceType ?: TranslationSourceType.MANUAL
}

fun TranslationHistoryEntry.resolvedSourceAppPackage(): String {
    return sourceAppPackage.orEmpty()
}

fun TranslationHistoryEntry.resolvedSourceAppLabel(): String {
    return sourceAppLabel.orEmpty()
}
