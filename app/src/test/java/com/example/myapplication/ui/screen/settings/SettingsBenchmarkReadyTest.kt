package com.example.myapplication.ui.screen.settings

import com.example.myapplication.model.createDefaultProvider
import com.example.myapplication.viewmodel.SettingsUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBenchmarkReadyTest {
    @Test
    fun isSettingsBenchmarkReady_defaultStateIsNotReady() {
        assertFalse(isSettingsBenchmarkReady(SettingsUiState()))
    }

    @Test
    fun isSettingsBenchmarkReady_requiresHydratedSelectedProvider() {
        val provider = createDefaultProvider(id = "benchmark-provider")

        assertFalse(
            isSettingsBenchmarkReady(
                SettingsUiState(providers = listOf(provider)),
            ),
        )
        assertTrue(
            isSettingsBenchmarkReady(
                SettingsUiState(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
            ),
        )
    }
}
