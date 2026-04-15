package com.example.myapplication.viewmodel

object SettingsUiMutationSupport {
    fun toggleProviderEnabled(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        return current.copy(
            providers = SettingsProviderDraftSupport.toggleProviderEnabled(
                providers = current.providers,
                providerId = providerId,
            ),
            message = null,
        )
    }

    fun deleteProvider(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        val update = SettingsProviderDraftSupport.deleteProvider(
            providers = current.providers,
            selectedProviderId = current.selectedProviderId,
            providerId = providerId,
        )
        return current.copy(
            providers = update.providers,
            selectedProviderId = update.selectedProviderId,
            message = null,
        )
    }

    fun beginSaving(current: SettingsUiState): SettingsUiState {
        return current.copy(
            isSaving = true,
            message = null,
        )
    }

    fun applySaveSuccess(
        current: SettingsUiState,
        result: SettingsPersistenceResult,
        sourceState: SettingsUiState,
    ): SettingsUiState {
        return current.copy(
            providers = result.providers,
            selectedProviderId = result.selectedProviderId,
            themeMode = sourceState.themeMode,
            messageTextScale = sourceState.messageTextScale,
            reasoningExpandedByDefault = sourceState.reasoningExpandedByDefault,
            showThinkingContent = sourceState.showThinkingContent,
            autoCollapseThinking = sourceState.autoCollapseThinking,
            autoPreviewImages = sourceState.autoPreviewImages,
            codeBlockAutoWrap = sourceState.codeBlockAutoWrap,
            codeBlockAutoCollapse = sourceState.codeBlockAutoCollapse,
            showRoleplayAiHelper = sourceState.showRoleplayAiHelper,
            roleplayLongformTargetChars = sourceState.roleplayLongformTargetChars,
            showRoleplayPresenceStrip = sourceState.showRoleplayPresenceStrip,
            showRoleplayStatusStrip = sourceState.showRoleplayStatusStrip,
            showOnlineRoleplayNarration = sourceState.showOnlineRoleplayNarration,
            enableRoleplayNetMeme = sourceState.enableRoleplayNetMeme,
            roleplayImmersiveMode = sourceState.roleplayImmersiveMode,
            roleplayHighContrast = sourceState.roleplayHighContrast,
            roleplayLineHeightScale = sourceState.roleplayLineHeightScale,
            screenTranslationSettings = sourceState.screenTranslationSettings,
            searchSettings = sourceState.searchSettings,
            isSaving = false,
            message = result.message,
        )
    }

    fun applySaveFailure(
        current: SettingsUiState,
        errorMessage: String,
    ): SettingsUiState {
        return current.copy(
            isSaving = false,
            message = errorMessage,
        )
    }

    fun applyMessageError(
        current: SettingsUiState,
        errorMessage: String,
    ): SettingsUiState {
        return current.copy(message = errorMessage)
    }

    fun applyPersistenceSuccess(
        current: SettingsUiState,
        result: SettingsPersistenceResult,
    ): SettingsUiState {
        return current.copy(
            providers = result.providers.ifEmpty { current.providers },
            selectedProviderId = result.selectedProviderId.ifBlank { current.selectedProviderId },
            message = result.message,
        )
    }

    fun consumeMessage(current: SettingsUiState): SettingsUiState {
        return current.copy(message = null)
    }

    fun confirmFetchedModels(
        current: SettingsUiState,
        providerId: String,
        selectedModelIds: Set<String>,
    ): SettingsUiState {
        val update = SettingsProviderDraftSupport.applyFetchedModelSelection(
            currentProviders = current.providers,
            providerId = providerId,
            fetchedModels = current.pendingFetchedModels,
            selectedModelIds = selectedModelIds,
        )
        return current.copy(
            providers = update.providers,
            pendingFetchedModels = emptyList(),
            pendingFetchProviderId = "",
            message = update.message,
        )
    }

    fun dismissFetchedModels(current: SettingsUiState): SettingsUiState {
        return current.copy(
            pendingFetchedModels = emptyList(),
            pendingFetchProviderId = "",
        )
    }

    fun removeModelFromProvider(
        current: SettingsUiState,
        providerId: String,
        modelId: String,
    ): SettingsUiState {
        return current.copy(
            providers = SettingsProviderDraftSupport.removeModelFromProvider(
                providers = current.providers,
                providerId = providerId,
                modelId = modelId,
            ),
            message = null,
        )
    }

    fun beginLoadingModels(
        current: SettingsUiState,
        providerId: String,
    ): SettingsUiState {
        return current.copy(
            isLoadingModels = true,
            loadingProviderId = providerId,
            message = null,
        )
    }

    fun applyLoadModelsSuccess(
        current: SettingsUiState,
        result: SettingsModelLoadResult,
    ): SettingsUiState {
        return current.copy(
            isLoadingModels = false,
            loadingProviderId = "",
            pendingFetchedModels = result.pendingFetchedModels,
            pendingFetchProviderId = result.pendingFetchProviderId,
            message = result.message,
        )
    }

    fun applyLoadModelsFailure(
        current: SettingsUiState,
        errorMessage: String,
    ): SettingsUiState {
        return current.copy(
            isLoadingModels = false,
            loadingProviderId = "",
            message = errorMessage,
        )
    }
}
