package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.DEFAULT_PROVIDER_NAME
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDraftStateSupportTest {
    @Test
    fun syncStoredSettings_overwritesDraftWhenNoLocalChanges() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "model-a",
        )

        val updated = SettingsDraftStateSupport.syncStoredSettings(
            current = SettingsUiState(),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                themeMode = ThemeMode.DARK,
            ),
        )

        assertEquals(listOf(provider), updated.providers)
        assertEquals(provider.id, updated.selectedProviderId)
        assertEquals(ThemeMode.DARK, updated.themeMode)
    }

    @Test
    fun syncStoredSettings_preservesTemplateDialogWhileRefreshingStoredSettings() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
        )

        val updated = SettingsDraftStateSupport.syncStoredSettings(
            current = SettingsUiState(showTemplateDialog = true),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        assertTrue(updated.showTemplateDialog)
        assertEquals(listOf(provider), updated.providers)
        assertEquals(provider.id, updated.selectedProviderId)
    }

    @Test
    fun syncStoredSettings_hydratesProvidersEvenWhenOtherDraftChangesExist() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "model-a",
        )

        val updated = SettingsDraftStateSupport.syncStoredSettings(
            current = SettingsUiState(
                themeMode = ThemeMode.DARK,
                savedSettings = AppSettings(themeMode = ThemeMode.SYSTEM),
            ),
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                themeMode = ThemeMode.SYSTEM,
            ),
        )

        assertEquals(listOf(provider), updated.providers)
        assertEquals(provider.id, updated.selectedProviderId)
        assertEquals(ThemeMode.DARK, updated.themeMode)
    }

    @Test
    fun updateScreenTranslationSettings_updatesDraftAndClearsMessage() {
        val updated = SettingsDraftStateSupport.updateScreenTranslationSettings(
            current = SettingsUiState(
                message = "旧消息",
                screenTranslationSettings = ScreenTranslationSettings(),
            ),
        ) { it.copy(serviceEnabled = true, targetLanguage = "日语") }

        assertTrue(updated.screenTranslationSettings.serviceEnabled)
        assertEquals("日语", updated.screenTranslationSettings.targetLanguage)
        assertEquals(null, updated.message)
    }

    @Test
    fun ensureProviderDrafts_injectsDefaultProviderWhenDraftsAreEmpty() {
        val updated = SettingsDraftStateSupport.ensureProviderDrafts(
            current = SettingsUiState(),
        )

        assertEquals(1, updated.providers.size)
        assertEquals(DEFAULT_PROVIDER_NAME, updated.providers.single().name)
        assertEquals(updated.providers.single().id, updated.selectedProviderId)
    }

    @Test
    fun addProviderFromTemplate_appendsProviderAndClosesDialog() {
        val result = SettingsDraftStateSupport.addProviderFromTemplate(
            current = SettingsUiState(
                providers = listOf(
                    ProviderSettings(id = "provider-1", name = "Provider 1"),
                ),
                showTemplateDialog = true,
            ),
            template = ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )

        assertEquals(2, result.state.providers.size)
        assertFalse(result.state.showTemplateDialog)
        assertTrue(result.newProviderId.isNotBlank())
        assertEquals("OpenAI", result.state.providers.last().name)
        assertEquals(result.newProviderId, result.state.selectedProviderId)
    }

    @Test
    fun addProviderFromTemplate_preservesSavedProvidersWhenDraftListStillEmpty() {
        val savedProvider = ProviderSettings(
            id = "provider-saved",
            name = "Saved Provider",
            baseUrl = "https://saved.example.com/v1/",
            apiKey = "saved-key",
            selectedModel = "saved-model",
        )
        val result = SettingsDraftStateSupport.addProviderFromTemplate(
            current = SettingsUiState(
                providers = emptyList(),
                savedSettings = AppSettings(
                    providers = listOf(savedProvider),
                    selectedProviderId = savedProvider.id,
                ),
                showTemplateDialog = true,
            ),
            template = ProviderTemplate(
                name = "OpenAI",
                description = "官方兼容接口",
                defaultBaseUrl = "https://api.openai.com/v1/",
                type = ProviderType.OPENAI,
            ),
        )

        assertEquals(2, result.state.providers.size)
        assertEquals(savedProvider.id, result.state.providers.first().id)
        assertEquals("OpenAI", result.state.providers.last().name)
        assertEquals(result.newProviderId, result.state.selectedProviderId)
    }
}
