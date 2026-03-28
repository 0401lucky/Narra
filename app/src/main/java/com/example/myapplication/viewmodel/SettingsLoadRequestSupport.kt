package com.example.myapplication.viewmodel

import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings

data class SettingsModelLoadRequest(
    val providerId: String,
    val baseUrl: String,
    val apiKey: String,
    val selectedModel: String,
    val apiProtocol: ProviderApiProtocol,
    val persistResult: Boolean,
    val persistedProviders: List<ProviderSettings>,
    val persistedSelectedProviderId: String,
)

object SettingsLoadRequestSupport {
    fun resolveCurrentProviderRequest(
        state: SettingsUiState,
        providerId: String? = state.currentProvider?.id,
    ): SettingsModelLoadRequest? {
        val targetProviderId = providerId ?: return null
        val provider = state.providers.firstOrNull { it.id == targetProviderId } ?: return null
        return SettingsModelLoadRequest(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
            apiProtocol = provider.resolvedApiProtocol(),
            persistResult = false,
            persistedProviders = emptyList(),
            persistedSelectedProviderId = "",
        )
    }

    fun resolveSavedProviderRequest(
        state: SettingsUiState,
        providerId: String? = null,
    ): SettingsModelLoadRequest? {
        val savedProviders = SettingsProviderDraftSupport.ensureProviders(state.savedSettings.resolvedProviders())
        val provider = if (providerId == null) {
            val resolvedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
                providers = savedProviders,
                selectedProviderId = state.savedSettings.selectedProviderId,
            )
            savedProviders.firstOrNull { it.id == resolvedSelectedProviderId }
                ?: state.savedSettings.activeProvider()
                ?: state.currentProvider
        } else {
            savedProviders.firstOrNull { it.id == providerId }
        } ?: return null

        return SettingsModelLoadRequest(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
            apiProtocol = provider.resolvedApiProtocol(),
            persistResult = true,
            persistedProviders = savedProviders,
            persistedSelectedProviderId = providerId ?: SettingsProviderDraftSupport.resolveSelectedProviderId(
                providers = savedProviders,
                selectedProviderId = state.savedSettings.selectedProviderId,
            ).ifBlank { provider.id },
        )
    }
}
