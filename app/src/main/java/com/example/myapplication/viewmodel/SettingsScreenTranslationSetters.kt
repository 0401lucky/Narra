package com.example.myapplication.viewmodel

/**
 * T7.5 — 从 SettingsViewModel 抽出的 7 个屏幕翻译设置 setter。
 *
 * 以同包扩展函数实现，UI 侧通过 `import com.example.myapplication.viewmodel.*`
 * 即可保持绑定方法引用。
 */
fun SettingsViewModel.updateScreenTranslationServiceEnabled(enabled: Boolean) =
    updateScreenTranslationDraft { it.copy(serviceEnabled = enabled) }

fun SettingsViewModel.updateScreenTranslationOverlayEnabled(enabled: Boolean) =
    updateScreenTranslationDraft { it.copy(overlayEnabled = enabled) }

fun SettingsViewModel.updateScreenTranslationTargetLanguage(language: String) =
    updateScreenTranslationDraft { it.copy(targetLanguage = language) }

fun SettingsViewModel.updateScreenTranslationSelectedTextEnabled(enabled: Boolean) =
    updateScreenTranslationDraft { it.copy(selectedTextEnabled = enabled) }

fun SettingsViewModel.updateScreenTranslationShowSourceText(enabled: Boolean) =
    updateScreenTranslationDraft { it.copy(showSourceText = enabled) }

fun SettingsViewModel.updateScreenTranslationVendorGuideDismissed(dismissed: Boolean) =
    updateScreenTranslationDraft { it.copy(vendorGuideDismissed = dismissed) }

fun SettingsViewModel.updateScreenTranslationOverlayOffset(
    x: Float,
    y: Float,
) = updateScreenTranslationDraft {
    it.copy(
        overlayOffsetX = x.coerceIn(0f, 1f),
        overlayOffsetY = y.coerceIn(0f, 1f),
    )
}
