package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.local.SettingsStore
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.FunctionModelProviderIds
import com.example.myapplication.model.MemoryInjectionPosition
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.UserPersonaMask

interface AiSettingsEditor {
    suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    )

    suspend fun saveFunctionModelProviderIds(
        functionModelProviderIds: FunctionModelProviderIds,
    )

    suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>)

    suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    )

    suspend fun saveDisplaySettings(
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
        enableRoleplayNetMeme: Boolean,
        roleplayImmersiveMode: com.example.myapplication.model.RoleplayImmersiveMode,
        roleplayHighContrast: Boolean,
        roleplayLineHeightScale: com.example.myapplication.model.RoleplayLineHeightScale,
    )

    suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings)

    suspend fun saveSearchSettings(settings: SearchSettings)

    suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    )

    suspend fun saveUserPersonaMasks(
        masks: List<UserPersonaMask>,
        defaultMaskId: String,
    )

    suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean)

    suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int)

    suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String)

    suspend fun saveMemoryInjectionPosition(position: MemoryInjectionPosition)

    suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int)
}

class DefaultAiSettingsEditor(
    private val settingsStore: SettingsStore,
    private val apiServiceFactory: ApiServiceFactory,
) : AiSettingsEditor {
    override suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) {
        val normalizedProviders = providers.map { provider ->
            if (provider.baseUrl.isNotBlank()) {
                provider.copy(
                    baseUrl = apiServiceFactory.normalizeBaseUrl(
                        baseUrl = provider.baseUrl,
                        apiProtocol = provider.resolvedApiProtocol(),
                    ),
                )
            } else {
                provider
            }
        }
        settingsStore.saveProviderSettings(
            providers = normalizedProviders,
            selectedProviderId = selectedProviderId,
        )
    }

    override suspend fun saveFunctionModelProviderIds(
        functionModelProviderIds: FunctionModelProviderIds,
    ) {
        settingsStore.saveFunctionModelProviderIds(functionModelProviderIds)
    }

    override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) {
        settingsStore.saveTranslationHistory(history)
    }

    override suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    ) {
        settingsStore.saveAssistants(
            assistants = assistants,
            selectedAssistantId = selectedAssistantId,
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
        enableRoleplayNetMeme: Boolean,
        roleplayImmersiveMode: com.example.myapplication.model.RoleplayImmersiveMode,
        roleplayHighContrast: Boolean,
        roleplayLineHeightScale: com.example.myapplication.model.RoleplayLineHeightScale,
    ) {
        settingsStore.saveDisplaySettings(
            themeMode = themeMode,
            messageTextScale = messageTextScale,
            reasoningExpandedByDefault = reasoningExpandedByDefault,
            showThinkingContent = showThinkingContent,
            autoCollapseThinking = autoCollapseThinking,
            autoPreviewImages = autoPreviewImages,
            codeBlockAutoWrap = codeBlockAutoWrap,
            codeBlockAutoCollapse = codeBlockAutoCollapse,
            showRoleplayAiHelper = showRoleplayAiHelper,
            roleplayLongformTargetChars = roleplayLongformTargetChars,
            showRoleplayPresenceStrip = showRoleplayPresenceStrip,
            showRoleplayStatusStrip = showRoleplayStatusStrip,
            showOnlineRoleplayNarration = showOnlineRoleplayNarration,
            enableRoleplayNetMeme = enableRoleplayNetMeme,
            roleplayImmersiveMode = roleplayImmersiveMode,
            roleplayHighContrast = roleplayHighContrast,
            roleplayLineHeightScale = roleplayLineHeightScale,
        )
    }

    override suspend fun saveScreenTranslationSettings(settings: ScreenTranslationSettings) {
        settingsStore.saveScreenTranslationSettings(settings)
    }

    override suspend fun saveSearchSettings(settings: SearchSettings) {
        settingsStore.saveSearchSettings(settings)
    }

    override suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    ) {
        settingsStore.saveUserProfile(
            displayName = displayName.trim(),
            personaPrompt = personaPrompt,
            avatarUri = avatarUri.trim(),
            avatarUrl = avatarUrl.trim(),
        )
    }

    override suspend fun saveUserPersonaMasks(
        masks: List<UserPersonaMask>,
        defaultMaskId: String,
    ) {
        settingsStore.saveUserPersonaMasks(
            masks = masks,
            defaultMaskId = defaultMaskId,
        )
    }

    override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) {
        settingsStore.saveRoleplayAssistantMismatchDialogPreference(suppressed)
    }

    override suspend fun saveMemorySettings(autoSummaryEvery: Int, capacity: Int) {
        settingsStore.saveMemorySettings(autoSummaryEvery, capacity)
    }

    override suspend fun saveMemoryPromptSettings(extractionPrompt: String, injectionPrompt: String) {
        settingsStore.saveMemoryPromptSettings(extractionPrompt, injectionPrompt)
    }

    override suspend fun saveMemoryInjectionPosition(position: MemoryInjectionPosition) {
        settingsStore.saveMemoryInjectionPosition(position)
    }

    override suspend fun saveContextLogSettings(enabled: Boolean, capacity: Int) {
        settingsStore.saveContextLogSettings(enabled, capacity)
    }
}
