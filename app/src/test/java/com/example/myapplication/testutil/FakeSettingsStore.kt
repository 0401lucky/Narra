package com.example.myapplication.testutil

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplayImmersiveMode
import com.example.myapplication.model.RoleplayLineHeightScale
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsStore(
    settings: AppSettings,
) : SettingsStore {
    private val state = MutableStateFlow(settings)

    override val settingsFlow: Flow<AppSettings> = state

    override suspend fun saveSettings(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
    ) {
        state.value = state.value.copy(
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
        )
    }

    override suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) {
        val selectedProvider = providers.firstOrNull { it.id == selectedProviderId }
            ?: providers.firstOrNull()
        val activeProvider = providers.firstOrNull { it.id == selectedProvider?.id && it.enabled }
            ?: providers.firstOrNull { it.enabled }
            ?: selectedProvider
        state.value = AppSettings(
            baseUrl = activeProvider?.baseUrl.orEmpty(),
            apiKey = activeProvider?.apiKey.orEmpty(),
            selectedModel = activeProvider?.selectedModel.orEmpty(),
            providers = providers,
            selectedProviderId = selectedProvider?.id.orEmpty(),
            themeMode = state.value.themeMode,
            messageTextScale = state.value.messageTextScale,
            reasoningExpandedByDefault = state.value.reasoningExpandedByDefault,
            showThinkingContent = state.value.showThinkingContent,
            autoCollapseThinking = state.value.autoCollapseThinking,
            autoPreviewImages = state.value.autoPreviewImages,
            codeBlockAutoWrap = state.value.codeBlockAutoWrap,
            codeBlockAutoCollapse = state.value.codeBlockAutoCollapse,
            showRoleplayAiHelper = state.value.showRoleplayAiHelper,
            roleplayLongformTargetChars = state.value.roleplayLongformTargetChars,
            showRoleplayPresenceStrip = state.value.showRoleplayPresenceStrip,
            showRoleplayStatusStrip = state.value.showRoleplayStatusStrip,
            showOnlineRoleplayNarration = state.value.showOnlineRoleplayNarration,
            roleplayImmersiveMode = state.value.roleplayImmersiveMode,
            roleplayHighContrast = state.value.roleplayHighContrast,
            roleplayLineHeightScale = state.value.roleplayLineHeightScale,
            suppressRoleplayAssistantMismatchDialog = state.value.suppressRoleplayAssistantMismatchDialog,
            userDisplayName = state.value.userDisplayName,
            userPersonaPrompt = state.value.userPersonaPrompt,
            userAvatarUri = state.value.userAvatarUri,
            userAvatarUrl = state.value.userAvatarUrl,
            translationHistory = state.value.translationHistory,
            assistants = state.value.assistants,
            selectedAssistantId = state.value.selectedAssistantId,
            screenTranslationSettings = state.value.screenTranslationSettings,
            searchSettings = state.value.searchSettings,
        )
    }

    override suspend fun saveDisplaySettings(
        themeMode: ThemeMode,
        messageTextScale: Float,
        reasoningExpandedByDefault: Boolean,
        showThinkingContent: Boolean,
        autoCollapseThinking: Boolean,
        autoPreviewImages: Boolean,
        codeBlockAutoWrap: Boolean,
        codeBlockAutoCollapse: Boolean,
        showRoleplayAiHelper: Boolean,
        roleplayLongformTargetChars: Int,
        showRoleplayPresenceStrip: Boolean,
        showRoleplayStatusStrip: Boolean,
        showOnlineRoleplayNarration: Boolean,
        roleplayImmersiveMode: RoleplayImmersiveMode,
        roleplayHighContrast: Boolean,
        roleplayLineHeightScale: RoleplayLineHeightScale,
    ) {
        state.value = state.value.copy(
            themeMode = themeMode,
            messageTextScale = messageTextScale,
            reasoningExpandedByDefault = reasoningExpandedByDefault,
            showThinkingContent = showThinkingContent,
            autoCollapseThinking = autoCollapseThinking,
            autoPreviewImages = autoPreviewImages,
            codeBlockAutoWrap = codeBlockAutoWrap,
            codeBlockAutoCollapse = codeBlockAutoCollapse,
            showRoleplayAiHelper = showRoleplayAiHelper,
            roleplayLongformTargetChars = roleplayLongformTargetChars
                .coerceIn(300, 2000)
                .takeIf { it > 0 }
                ?: DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS,
            showRoleplayPresenceStrip = showRoleplayPresenceStrip,
            showRoleplayStatusStrip = showRoleplayStatusStrip,
            showOnlineRoleplayNarration = showOnlineRoleplayNarration,
            roleplayImmersiveMode = roleplayImmersiveMode,
            roleplayHighContrast = roleplayHighContrast,
            roleplayLineHeightScale = roleplayLineHeightScale,
        )
    }

    override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) {
        state.value = state.value.copy(
            screenTranslationSettings = settings,
        )
    }

    override suspend fun saveSearchSettings(settings: SearchSettings) {
        state.value = state.value.copy(
            searchSettings = settings.normalized(),
        )
    }

    override suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    ) {
        state.value = state.value.copy(
            userDisplayName = displayName,
            userPersonaPrompt = personaPrompt,
            userAvatarUri = avatarUri,
            userAvatarUrl = avatarUrl,
        )
    }

    override suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    ) {
        state.value = state.value.copy(
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
        )
    }

    override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) {
        state.value = state.value.copy(
            translationHistory = history,
        )
    }

    override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) {
        state.value = state.value.copy(
            suppressRoleplayAssistantMismatchDialog = suppressed,
        )
    }
}
