package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.AppColorTheme
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ThemeMode
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AiSettingsRepositoryTest {
    @Test
    fun themeSettingsFlow_ignoresNonThemeChangesAndEmitsThemeChanges() = runTest {
        val settingsStore = ThemeTestSettingsStore()
        val repository = DefaultAiSettingsRepository(settingsStore)
        val emissions = mutableListOf<ThemeSettings>()
        val collectJob = launch(start = CoroutineStart.UNDISPATCHED) {
            repository.themeSettingsFlow.take(3).toList(emissions)
        }
        advanceUntilIdle()

        settingsStore.emit(AppSettings())
        settingsStore.emit(AppSettings(selectedModel = "model-b"))
        settingsStore.emit(AppSettings(themeMode = ThemeMode.DARK))
        settingsStore.emit(
            AppSettings(
                themeMode = ThemeMode.DARK,
                appColorTheme = AppColorTheme.OCEAN,
            ),
        )
        advanceUntilIdle()

        assertEquals(
            listOf(
                ThemeSettings(ThemeMode.SYSTEM, AppColorTheme.MATCHA),
                ThemeSettings(ThemeMode.DARK, AppColorTheme.MATCHA),
                ThemeSettings(ThemeMode.DARK, AppColorTheme.OCEAN),
            ),
            emissions,
        )
        collectJob.join()
    }
}

private class ThemeTestSettingsStore : SettingsStore {
    private val flow = MutableSharedFlow<AppSettings>()
    override val settingsFlow: Flow<AppSettings> = flow

    suspend fun emit(settings: AppSettings) {
        flow.emit(settings)
    }

    override suspend fun saveSettings(baseUrl: String, apiKey: String, selectedModel: String) =
        unsupported()

    override suspend fun saveProviderSettings(
        providers: List<com.example.myapplication.model.ProviderSettings>,
        selectedProviderId: String,
    ) = unsupported()

    override suspend fun saveFunctionModelProviderIds(
        functionModelProviderIds: com.example.myapplication.model.FunctionModelProviderIds,
    ) = unsupported()

    override suspend fun saveDisplaySettings(
        themeMode: ThemeMode,
        appColorTheme: AppColorTheme,
        messageTextScale: Float,
        reasoningExpandedByDefault: Boolean,
        showThinkingContent: Boolean,
        autoCollapseThinking: Boolean,
        autoPreviewImages: Boolean,
        codeBlockAutoWrap: Boolean,
        codeBlockAutoCollapse: Boolean,
        showRoleplayAiHelper: Boolean,
        roleplayLongformTargetChars: Int,
        showRoleplayPresenceStrip: Boolean,
        showRoleplayStatusStrip: Boolean,
        showOnlineRoleplayNarration: Boolean,
        enableRoleplayNetMeme: Boolean,
        roleplayImmersiveMode: com.example.myapplication.model.RoleplayImmersiveMode,
        roleplayHighContrast: Boolean,
        roleplayLineHeightScale: com.example.myapplication.model.RoleplayLineHeightScale,
        roleplayNoBackgroundSkin: com.example.myapplication.model.RoleplayNoBackgroundSkinSettings,
    ) = unsupported()

    override suspend fun saveScreenTranslationSettings(
        settings: com.example.myapplication.model.ScreenTranslationSettings,
    ) = unsupported()

    override suspend fun saveSearchSettings(settings: com.example.myapplication.model.SearchSettings) =
        unsupported()

    override suspend fun saveVoiceSynthesisSettings(
        settings: com.example.myapplication.model.VoiceSynthesisSettings,
    ) = unsupported()

    override suspend fun saveMomentsSettings(settings: com.example.myapplication.model.MomentsSettings) =
        unsupported()

    override suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    ) = unsupported()

    override suspend fun saveUserPersonaMasks(
        masks: List<com.example.myapplication.model.UserPersonaMask>,
        defaultMaskId: String,
    ) = unsupported()

    override suspend fun saveAssistants(
        assistants: List<com.example.myapplication.model.Assistant>,
        selectedAssistantId: String,
    ) = unsupported()

    override suspend fun saveDefaultPresetId(presetId: String) = unsupported()

    override suspend fun saveTranslationHistory(
        history: List<com.example.myapplication.model.TranslationHistoryEntry>,
    ) = unsupported()

    override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) = unsupported()

    override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) = unsupported()

    override suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String) =
        unsupported()

    override suspend fun saveMemoryInjectionPosition(
        position: com.example.myapplication.model.MemoryInjectionPosition,
    ) = unsupported()

    override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) = unsupported()

    private fun unsupported(): Nothing = error("本测试不应写入设置")
}
