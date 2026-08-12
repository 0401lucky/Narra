package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.system.performance.PerformanceTraceContract
import com.example.myapplication.system.performance.trace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class ThemeSettings(
    val themeMode: ThemeMode,
    val appColorTheme: AppColorTheme,
)

interface AiSettingsRepository {
    val settingsFlow: Flow<AppSettings>

    val themeSettingsFlow: Flow<ThemeSettings>
        get() = settingsFlow.toThemeSettingsFlow()
}

class DefaultAiSettingsRepository(
    settingsStore: SettingsStore,
) : AiSettingsRepository {
    override val settingsFlow: Flow<AppSettings> = settingsStore.settingsFlow

    override val themeSettingsFlow: Flow<ThemeSettings> = settingsFlow.toThemeSettingsFlow()
}

private fun Flow<AppSettings>.toThemeSettingsFlow(): Flow<ThemeSettings> =
    map { settings ->
        PerformanceTraceContract.SETTINGS_THEME_PROJECT.trace {
            ThemeSettings(
                themeMode = settings.themeMode,
                appColorTheme = settings.appColorTheme,
            )
        }
    }.distinctUntilChanged()
