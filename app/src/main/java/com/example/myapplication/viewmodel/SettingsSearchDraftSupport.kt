package com.example.myapplication.viewmodel

import com.example.myapplication.model.SearchSettings

object SettingsSearchDraftSupport {
    fun updateSearchSettings(
        current: SettingsUiState,
        transform: (SearchSettings) -> SearchSettings,
    ): SettingsUiState {
        return current.copy(
            searchSettings = transform(current.searchSettings).normalized(),
            message = null,
        )
    }
}
