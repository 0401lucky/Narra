package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLoadRequestSupportTest {
    @Test
    fun resolveCurrentProviderRequest_returnsDraftProviderRequest() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "model-a",
        )

        val request = SettingsLoadRequestSupport.resolveCurrentProviderRequest(
            SettingsUiState(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        assertEquals("provider-1", request?.providerId)
        assertEquals("model-a", request?.selectedModel)
        assertTrue(request?.persistResult == false)
    }

    @Test
    fun resolveSavedProviderRequest_returnsPersistedProviderRequest() {
        val provider = ProviderSettings(
            id = "provider-1",
            name = "Provider",
            baseUrl = "https://example.com/v1/",
            apiKey = "key",
            selectedModel = "model-a",
        )

        val request = SettingsLoadRequestSupport.resolveSavedProviderRequest(
            SettingsUiState(
                savedSettings = AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
            ),
        )

        assertEquals("provider-1", request?.providerId)
        assertTrue(request?.persistResult == true)
        assertEquals("provider-1", request?.persistedSelectedProviderId)
        assertEquals(1, request?.persistedProviders?.size)
    }
}
