package com.example.myapplication.viewmodel

import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode

object SettingsPreferenceDraftSupport {
    fun updateThemeMode(current: SettingsUiState, themeMode: ThemeMode): SettingsUiState {
        return current.copy(themeMode = themeMode, message = null)
    }

    fun updateMessageTextScale(current: SettingsUiState, messageTextScale: Float): SettingsUiState {
        return current.copy(
            messageTextScale = messageTextScale.coerceIn(0.85f, 1.25f),
            message = null,
        )
    }

    fun updateReasoningExpandedByDefault(current: SettingsUiState, expanded: Boolean): SettingsUiState {
        return current.copy(reasoningExpandedByDefault = expanded, message = null)
    }

    fun updateShowThinkingContent(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(showThinkingContent = enabled, message = null)
    }

    fun updateAutoCollapseThinking(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(autoCollapseThinking = enabled, message = null)
    }

    fun updateAutoPreviewImages(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(autoPreviewImages = enabled, message = null)
    }

    fun updateCodeBlockAutoWrap(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(codeBlockAutoWrap = enabled, message = null)
    }

    fun updateCodeBlockAutoCollapse(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(codeBlockAutoCollapse = enabled, message = null)
    }

    fun updateShowRoleplayAiHelper(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(showRoleplayAiHelper = enabled, message = null)
    }

    fun updateRoleplayLongformTargetChars(current: SettingsUiState, value: Int): SettingsUiState {
        return current.copy(
            roleplayLongformTargetChars = value.coerceIn(300, 2000),
            message = null,
        )
    }

    fun updateShowRoleplayPresenceStrip(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(showRoleplayPresenceStrip = enabled, message = null)
    }

    fun updateShowRoleplayStatusStrip(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(showRoleplayStatusStrip = enabled, message = null)
    }

    fun updateShowOnlineRoleplayNarration(current: SettingsUiState, enabled: Boolean): SettingsUiState {
        return current.copy(showOnlineRoleplayNarration = enabled, message = null)
    }

    fun updateRoleplayImmersiveMode(
        current: SettingsUiState,
        mode: com.example.myapplication.model.RoleplayImmersiveMode,
    ): SettingsUiState {
        return current.copy(roleplayImmersiveMode = mode, message = null)
    }

    fun updateRoleplayHighContrast(
        current: SettingsUiState,
        enabled: Boolean,
    ): SettingsUiState {
        return current.copy(roleplayHighContrast = enabled, message = null)
    }

    fun updateRoleplayLineHeightScale(
        current: SettingsUiState,
        scale: com.example.myapplication.model.RoleplayLineHeightScale,
    ): SettingsUiState {
        return current.copy(roleplayLineHeightScale = scale, message = null)
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
}
