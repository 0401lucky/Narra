package com.example.myapplication.viewmodel

import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPreferenceDraftSupportTest {
    @Test
    fun updateThemeAndDisplayFlags_updatesStateAndClearsMessage() {
        val updated = SettingsPreferenceDraftSupport.updateCodeBlockAutoCollapse(
            SettingsPreferenceDraftSupport.updateAutoPreviewImages(
                SettingsPreferenceDraftSupport.updateThemeMode(
                    current = SettingsUiState(message = "旧消息"),
                    themeMode = ThemeMode.DARK,
                ),
                enabled = false,
            ),
            enabled = true,
        )

        assertEquals(ThemeMode.DARK, updated.themeMode)
        assertFalse(updated.autoPreviewImages)
        assertTrue(updated.codeBlockAutoCollapse)
        assertEquals(null, updated.message)
    }

    @Test
    fun updateScreenTranslationSettings_updatesNestedState() {
        val updated = SettingsPreferenceDraftSupport.updateScreenTranslationSettings(
            current = SettingsUiState(
                screenTranslationSettings = ScreenTranslationSettings(),
            ),
        ) {
            it.copy(
                serviceEnabled = true,
                overlayOffsetX = 0.2f,
                overlayOffsetY = 0.8f,
            )
        }

        assertTrue(updated.screenTranslationSettings.serviceEnabled)
        assertEquals(0.2f, updated.screenTranslationSettings.overlayOffsetX)
        assertEquals(0.8f, updated.screenTranslationSettings.overlayOffsetY)
    }
}
