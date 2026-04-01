package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.createDefaultProvider

data class SettingsTemplateAddResult(
    val state: SettingsUiState,
    val newProviderId: String,
)

object SettingsDraftStateSupport {
    fun syncStoredSettings(
        current: SettingsUiState,
        settings: AppSettings,
    ): SettingsUiState {
        val resolvedProviders = SettingsProviderDraftSupport.ensureProviders(settings.resolvedProviders())
        val resolvedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
            providers = resolvedProviders,
            selectedProviderId = settings.selectedProviderId,
        )
        return if (current.hasDraftChanges()) {
            val hydratedProviders = if (current.providers.isEmpty()) {
                resolvedProviders
            } else {
                current.providers
            }
            val hydratedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
                providers = hydratedProviders,
                selectedProviderId = current.selectedProviderId.ifBlank { resolvedSelectedProviderId },
            )
            current.copy(
                providers = hydratedProviders,
                selectedProviderId = hydratedSelectedProviderId,
                savedSettings = settings,
            )
        } else {
            current.copy(
                providers = resolvedProviders,
                selectedProviderId = resolvedSelectedProviderId,
                savedSettings = settings,
                themeMode = settings.themeMode,
                messageTextScale = settings.messageTextScale,
                reasoningExpandedByDefault = settings.reasoningExpandedByDefault,
                showThinkingContent = settings.showThinkingContent,
                autoCollapseThinking = settings.autoCollapseThinking,
                autoPreviewImages = settings.autoPreviewImages,
                codeBlockAutoWrap = settings.codeBlockAutoWrap,
                codeBlockAutoCollapse = settings.codeBlockAutoCollapse,
                showRoleplayAiHelper = settings.showRoleplayAiHelper,
                roleplayLongformTargetChars = settings.roleplayLongformTargetChars,
                showRoleplayPresenceStrip = settings.showRoleplayPresenceStrip,
                showRoleplayStatusStrip = settings.showRoleplayStatusStrip,
                screenTranslationSettings = settings.screenTranslationSettings,
                searchSettings = settings.resolvedSearchSettings(),
            )
        }
    }

    fun updateProvider(
        current: SettingsUiState,
        providerId: String,
        transform: (ProviderSettings) -> ProviderSettings,
    ): SettingsUiState {
        return current.copy(
            providers = current.providers.map { provider ->
                if (provider.id == providerId) {
                    transform(provider)
                } else {
                    provider
                }
            },
            message = null,
        )
    }

    fun updateCurrentProvider(
        current: SettingsUiState,
        transform: (ProviderSettings) -> ProviderSettings,
    ): SettingsUiState {
        val providerId = current.currentProvider?.id ?: return current
        return updateProvider(current, providerId, transform)
    }

    fun updateScreenTranslationSettings(
        current: SettingsUiState,
        transform: (ScreenTranslationSettings) -> ScreenTranslationSettings,
    ): SettingsUiState {
        return current.copy(
            screenTranslationSettings = transform(current.screenTranslationSettings),
            message = null,
        )
    }

    fun selectProvider(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        return current.copy(
            selectedProviderId = providerId,
            message = null,
        )
    }

    fun addProvider(current: SettingsUiState): SettingsUiState {
        val baseProviders = current.providers.ifEmpty {
            SettingsProviderDraftSupport.ensureProviders(current.savedSettings.resolvedProviders())
        }
        val newProvider = createDefaultProvider(
            name = "提供商 ${baseProviders.size + 1}",
        )
        return current.copy(
            providers = baseProviders + newProvider,
            selectedProviderId = newProvider.id,
            message = null,
        )
    }

    fun ensureProviderDrafts(current: SettingsUiState): SettingsUiState {
        if (current.providers.isNotEmpty()) {
            return current
        }
        val resolvedProviders = SettingsProviderDraftSupport.ensureProviders(
            current.savedSettings.resolvedProviders(),
        )
        val resolvedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
            providers = resolvedProviders,
            selectedProviderId = current.selectedProviderId.ifBlank {
                current.savedSettings.selectedProviderId
            },
        )
        return current.copy(
            providers = resolvedProviders,
            selectedProviderId = resolvedSelectedProviderId,
            message = null,
        )
    }

    fun showAddProviderDialog(current: SettingsUiState): SettingsUiState {
        return current.copy(showTemplateDialog = true)
    }

    fun dismissAddProviderDialog(current: SettingsUiState): SettingsUiState {
        return current.copy(showTemplateDialog = false)
    }

    fun addProviderFromTemplate(
        current: SettingsUiState,
        template: ProviderTemplate,
    ): SettingsTemplateAddResult {
        val baseProviders = current.providers.ifEmpty {
            SettingsProviderDraftSupport.ensureProviders(current.savedSettings.resolvedProviders())
        }
        val newProvider = createDefaultProvider(
            name = template.name,
            baseUrl = template.defaultBaseUrl,
            type = template.type,
            apiProtocol = template.defaultApiProtocol,
        )
        return SettingsTemplateAddResult(
            state = current.copy(
                providers = baseProviders + newProvider,
                selectedProviderId = newProvider.id,
                showTemplateDialog = false,
                message = null,
            ),
            newProviderId = newProvider.id,
        )
    }
}
