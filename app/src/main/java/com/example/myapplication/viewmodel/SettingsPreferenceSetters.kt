package com.example.myapplication.viewmodel

import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.ThemeMode

/**
 * T7.4 — 从 SettingsViewModel 抽出的 20 个偏好 setter。
 *
 * 以同包扩展函数实现，UI 侧通过 `import com.example.myapplication.viewmodel.*`
 * 或显式导入即可保持 `settingsViewModel::updateThemeMode` 形式的绑定方法引用。
 */
fun SettingsViewModel.updateThemeMode(themeMode: ThemeMode) =
    updateUiState { SettingsPreferenceDraftSupport.updateThemeMode(it, themeMode) }

fun SettingsViewModel.updateMessageTextScale(messageTextScale: Float) =
    updateUiState { SettingsPreferenceDraftSupport.updateMessageTextScale(it, messageTextScale) }

fun SettingsViewModel.updateReasoningExpandedByDefault(expanded: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateReasoningExpandedByDefault(it, expanded) }

fun SettingsViewModel.updateShowThinkingContent(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateShowThinkingContent(it, enabled) }

fun SettingsViewModel.updateAutoCollapseThinking(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateAutoCollapseThinking(it, enabled) }

fun SettingsViewModel.updateAutoPreviewImages(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateAutoPreviewImages(it, enabled) }

fun SettingsViewModel.updateCodeBlockAutoWrap(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateCodeBlockAutoWrap(it, enabled) }

fun SettingsViewModel.updateCodeBlockAutoCollapse(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateCodeBlockAutoCollapse(it, enabled) }

fun SettingsViewModel.updateShowRoleplayAiHelper(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateShowRoleplayAiHelper(it, enabled) }

fun SettingsViewModel.updateRoleplayLongformTargetChars(value: Int) =
    updateUiState { SettingsPreferenceDraftSupport.updateRoleplayLongformTargetChars(it, value) }

fun SettingsViewModel.updateShowRoleplayPresenceStrip(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateShowRoleplayPresenceStrip(it, enabled) }

fun SettingsViewModel.updateShowRoleplayStatusStrip(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateShowRoleplayStatusStrip(it, enabled) }

fun SettingsViewModel.updateShowOnlineRoleplayNarration(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateShowOnlineRoleplayNarration(it, enabled) }

fun SettingsViewModel.updateEnableRoleplayNetMeme(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateEnableRoleplayNetMeme(it, enabled) }

fun SettingsViewModel.updateRoleplayImmersiveMode(mode: RoleplayImmersiveMode) =
    updateUiState { SettingsPreferenceDraftSupport.updateRoleplayImmersiveMode(it, mode) }

fun SettingsViewModel.updateRoleplayHighContrast(enabled: Boolean) =
    updateUiState { SettingsPreferenceDraftSupport.updateRoleplayHighContrast(it, enabled) }

fun SettingsViewModel.updateRoleplayLineHeightScale(scale: RoleplayLineHeightScale) =
    updateUiState { SettingsPreferenceDraftSupport.updateRoleplayLineHeightScale(it, scale) }

fun SettingsViewModel.updateRoleplayNoBackgroundSkin(skin: RoleplayNoBackgroundSkinSettings) =
    updateUiState { SettingsPreferenceDraftSupport.updateRoleplayNoBackgroundSkin(it, skin) }
