package com.example.myapplication.viewmodel

import com.example.myapplication.model.SearchSettings

/**
 * T7.5 — 从 SettingsViewModel 抽出的 6 个联网搜索 setter。
 *
 * 以同包扩展函数实现，UI 侧通过 `import com.example.myapplication.viewmodel.*`
 * 即可保持绑定方法引用。
 */
fun SettingsViewModel.selectSearchSource(sourceId: String) =
    updateSearchDraft { it.copy(selectedSourceId = sourceId) }

fun SettingsViewModel.updateSearchResultCount(resultCount: Int) =
    updateSearchDraft { it.copy(defaultResultCount = resultCount) }

fun SettingsViewModel.updateSearchSourceEnabled(
    sourceId: String,
    enabled: Boolean,
) = updateSearchDraft { settings ->
    settings.copy(sources = settings.sources.map { if (it.id == sourceId) it.copy(enabled = enabled) else it })
}

fun SettingsViewModel.updateSearchSourceApiKey(
    sourceId: String,
    apiKey: String,
) = updateSearchDraft { settings ->
    settings.copy(sources = settings.sources.map { if (it.id == sourceId) it.copy(apiKey = apiKey) else it })
}

fun SettingsViewModel.updateSearchSourceEngineId(
    sourceId: String,
    engineId: String,
) = updateSearchDraft { settings ->
    settings.copy(sources = settings.sources.map { if (it.id == sourceId) it.copy(engineId = engineId) else it })
}

fun SettingsViewModel.updateSearchSourceProviderId(
    sourceId: String,
    providerId: String,
) = updateSearchDraft { settings ->
    settings.copy(sources = settings.sources.map { if (it.id == sourceId) it.copy(providerId = providerId) else it })
}

private fun SettingsViewModel.updateSearchDraft(
    transform: (SearchSettings) -> SearchSettings,
) = updateUiState { SettingsSearchDraftSupport.updateSearchSettings(it, transform) }
