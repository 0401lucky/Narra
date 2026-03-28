package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.AppSettings
import kotlinx.coroutines.flow.Flow

interface AiSettingsRepository {
    val settingsFlow: Flow<AppSettings>
}

class DefaultAiSettingsRepository(
    settingsStore: SettingsStore,
) : AiSettingsRepository {
    override val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow
}
