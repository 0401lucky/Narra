package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ProviderSettings

data class SettingsDraftResult(
    val providers: List<ProviderSettings>,
    val selectedProviderId: String,
)

data class SettingsPersistenceResult(
    val message: String,
    val providers: List<ProviderSettings> = emptyList(),
    val selectedProviderId: String = "",
)

class SettingsPersistenceCoordinator(
    private val settingsEditor: AiSettingsEditor,
) {
    suspend fun saveSettings(
        currentState: SettingsUiState,
    ): SettingsPersistenceResult {
        val normalizedProviders = SettingsProviderDraftSupport.normalizeProviders(currentState.providers)
        val resolvedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
            providers = normalizedProviders,
            selectedProviderId = currentState.selectedProviderId,
        )
        settingsEditor.saveProviderSettings(
            providers = normalizedProviders,
            selectedProviderId = resolvedSelectedProviderId,
        )
        settingsEditor.saveDisplaySettings(
            themeMode = currentState.themeMode,
            messageTextScale = currentState.messageTextScale,
            reasoningExpandedByDefault = currentState.reasoningExpandedByDefault,
            showThinkingContent = currentState.showThinkingContent,
            autoCollapseThinking = currentState.autoCollapseThinking,
            autoPreviewImages = currentState.autoPreviewImages,
            codeBlockAutoWrap = currentState.codeBlockAutoWrap,
            codeBlockAutoCollapse = currentState.codeBlockAutoCollapse,
            showRoleplayAiHelper = currentState.showRoleplayAiHelper,
            roleplayLongformTargetChars = currentState.roleplayLongformTargetChars,
            showRoleplayPresenceStrip = currentState.showRoleplayPresenceStrip,
            showRoleplayStatusStrip = currentState.showRoleplayStatusStrip,
            roleplayImmersiveMode = currentState.roleplayImmersiveMode,
            roleplayHighContrast = currentState.roleplayHighContrast,
            roleplayLineHeightScale = currentState.roleplayLineHeightScale,
        )
        settingsEditor.saveScreenTranslationSettings(
            currentState.screenTranslationSettings,
        )
        settingsEditor.saveSearchSettings(
            currentState.searchSettings,
        )
        return SettingsPersistenceResult(
            providers = normalizedProviders,
            selectedProviderId = resolvedSelectedProviderId,
            message = "设置已保存",
        )
    }

    suspend fun saveSelectedProvider(
        savedSettings: AppSettings,
        draftProviders: List<ProviderSettings>,
        providerId: String,
    ): SettingsPersistenceResult? {
        val providersToPersist = resolveProvidersForPersistence(savedSettings, draftProviders)
        val targetProvider = providersToPersist.firstOrNull { it.id == providerId } ?: return null
        settingsEditor.saveProviderSettings(
            providers = providersToPersist,
            selectedProviderId = targetProvider.id,
        )
        return SettingsPersistenceResult(
            providers = providersToPersist,
            selectedProviderId = targetProvider.id,
            message = "已切换到 ${targetProvider.name.ifBlank { "该提供商" }}",
        )
    }

    suspend fun saveSelectedModel(
        savedSettings: AppSettings,
        currentProviders: List<ProviderSettings>,
        currentSelectedProviderId: String,
        selectedModel: String,
    ): SettingsPersistenceResult {
        val providersToPersist = resolveProvidersForPersistence(savedSettings, currentProviders)
        val resolvedSelectedProviderId = SettingsProviderDraftSupport.resolveSelectedProviderId(
            providers = providersToPersist,
            selectedProviderId = currentSelectedProviderId,
        )
        val updatedProviders = providersToPersist.map { provider ->
            if (provider.id == resolvedSelectedProviderId) {
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = SettingsProviderDraftSupport.mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            } else {
                provider
            }
        }
        settingsEditor.saveProviderSettings(
            providers = updatedProviders,
            selectedProviderId = resolvedSelectedProviderId,
        )
        return SettingsPersistenceResult(
            providers = updatedProviders,
            selectedProviderId = resolvedSelectedProviderId,
            message = "模型已切换为 $selectedModel",
        )
    }

    suspend fun saveSelectedModelForProvider(
        savedSettings: AppSettings,
        draftProviders: List<ProviderSettings>,
        providerId: String,
        selectedModel: String,
    ): SettingsPersistenceResult? {
        val providersToPersist = resolveProvidersForPersistence(savedSettings, draftProviders)
        val targetProvider = providersToPersist.firstOrNull { it.id == providerId } ?: return null
        val updatedProviders = providersToPersist.map { provider ->
            if (provider.id == providerId) {
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = SettingsProviderDraftSupport.mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            } else {
                provider
            }
        }
        settingsEditor.saveProviderSettings(
            providers = updatedProviders,
            selectedProviderId = targetProvider.id,
        )
        return SettingsPersistenceResult(
            providers = updatedProviders,
            selectedProviderId = targetProvider.id,
            message = "模型已切换为 $selectedModel",
        )
    }

    suspend fun saveThinkingBudgetForProvider(
        savedSettings: AppSettings,
        draftProviders: List<ProviderSettings>,
        providerId: String,
        thinkingBudget: Int?,
    ): SettingsPersistenceResult? {
        val providersToPersist = resolveProvidersForPersistence(savedSettings, draftProviders)
        val targetProvider = providersToPersist.firstOrNull { it.id == providerId } ?: return null
        val updatedProviders = providersToPersist.map { provider ->
            if (provider.id == providerId) {
                provider.copy(thinkingBudget = thinkingBudget)
            } else {
                provider
            }
        }
        settingsEditor.saveProviderSettings(
            providers = updatedProviders,
            selectedProviderId = targetProvider.id,
        )
        return SettingsPersistenceResult(
            providers = updatedProviders,
            selectedProviderId = targetProvider.id,
            message = "思考预算已更新",
        )
    }

    suspend fun saveUserProfile(
        displayName: String,
        avatarUri: String,
        avatarUrl: String,
    ): SettingsPersistenceResult {
        settingsEditor.saveUserProfile(
            displayName = displayName,
            avatarUri = avatarUri,
            avatarUrl = avatarUrl,
        )
        return SettingsPersistenceResult(
            message = "个人资料已更新",
        )
    }

    private fun resolveProvidersForPersistence(
        savedSettings: AppSettings,
        draftProviders: List<ProviderSettings>,
    ): List<ProviderSettings> {
        val baseProviders = if (draftProviders.isNotEmpty()) {
            draftProviders
        } else {
            savedSettings.resolvedProviders()
        }
        return SettingsProviderDraftSupport.ensureProviders(baseProviders)
    }
}
