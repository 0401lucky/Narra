package com.example.myapplication.viewmodel

import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode

/**
 * T7.3 — 从 SettingsViewModel 抽出的 14 个 provider function-model setter
 * （每个功能的 modelId 与 Mode 各一个）。
 *
 * 以同包扩展函数实现，UI 侧通过 `import com.example.myapplication.viewmodel.*`
 * 即可保持绑定方法引用。
 */
private val CUSTOM = ProviderFunctionModelMode.CUSTOM

private fun SettingsViewModel.selectFunctionProvider(function: ProviderFunction, providerId: String) {
    updateFunctionModelProviderIds { it.withProviderId(function, providerId) }
}

fun SettingsViewModel.updateProviderTitleSummaryModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.TITLE_SUMMARY, providerId)
    updateProvider(providerId) { it.copy(titleSummaryModel = modelId, titleSummaryModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderChatSuggestionModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.CHAT_SUGGESTION, providerId)
    updateProvider(providerId) { it.copy(chatSuggestionModel = modelId, chatSuggestionModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderMemoryModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.MEMORY, providerId)
    updateProvider(providerId) { it.copy(memoryModel = modelId, memoryModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderTranslationModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.TRANSLATION, providerId)
    updateProvider(providerId) { it.copy(translationModel = modelId, translationModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderPhoneSnapshotModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.PHONE_SNAPSHOT, providerId)
    updateProvider(providerId) { it.copy(phoneSnapshotModel = modelId, phoneSnapshotModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderSearchModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.SEARCH, providerId)
    updateProvider(providerId) { it.copy(searchModel = modelId, searchModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderGiftImageModel(providerId: String, modelId: String) {
    selectFunctionProvider(ProviderFunction.GIFT_IMAGE, providerId)
    updateProvider(providerId) { it.copy(giftImageModel = modelId, giftImageModelMode = CUSTOM) }
}

fun SettingsViewModel.updateProviderTitleSummaryModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.TITLE_SUMMARY, providerId)
    updateProvider(providerId) {
        it.copy(titleSummaryModelMode = mode, titleSummaryModel = if (mode == CUSTOM) it.titleSummaryModel else "")
    }
}

fun SettingsViewModel.updateProviderChatSuggestionModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.CHAT_SUGGESTION, providerId)
    updateProvider(providerId) {
        it.copy(chatSuggestionModelMode = mode, chatSuggestionModel = if (mode == CUSTOM) it.chatSuggestionModel else "")
    }
}

fun SettingsViewModel.updateProviderMemoryModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.MEMORY, providerId)
    updateProvider(providerId) {
        it.copy(memoryModelMode = mode, memoryModel = if (mode == CUSTOM) it.memoryModel else "")
    }
}

fun SettingsViewModel.updateProviderTranslationModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.TRANSLATION, providerId)
    updateProvider(providerId) {
        it.copy(translationModelMode = mode, translationModel = if (mode == CUSTOM) it.translationModel else "")
    }
}

fun SettingsViewModel.updateProviderPhoneSnapshotModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.PHONE_SNAPSHOT, providerId)
    updateProvider(providerId) {
        it.copy(phoneSnapshotModelMode = mode, phoneSnapshotModel = if (mode == CUSTOM) it.phoneSnapshotModel else "")
    }
}

fun SettingsViewModel.updateProviderSearchModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.SEARCH, providerId)
    updateProvider(providerId) {
        it.copy(searchModelMode = mode, searchModel = if (mode == CUSTOM) it.searchModel else "")
    }
}

fun SettingsViewModel.updateProviderGiftImageModelMode(providerId: String, mode: ProviderFunctionModelMode) {
    selectFunctionProvider(ProviderFunction.GIFT_IMAGE, providerId)
    updateProvider(providerId) {
        it.copy(giftImageModelMode = mode, giftImageModel = if (mode == CUSTOM) it.giftImageModel else "")
    }
}
