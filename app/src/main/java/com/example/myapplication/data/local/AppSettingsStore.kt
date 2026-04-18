package com.example.myapplication.data.local

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.DEFAULT_ASSISTANT_ICON
import com.example.myapplication.model.DEFAULT_MEMORY_MAX_ITEMS
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.DEFAULT_WORLD_BOOK_MAX_ENTRIES
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.TranslationHistoryEntry
import com.example.myapplication.model.createDefaultProvider
import com.example.myapplication.model.inferredModelInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "app_settings")

interface SettingsStore {
    val settingsFlow: Flow<AppSettings>

    suspend fun saveSettings(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
    )

    suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
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

    suspend fun saveScreenTranslationSettings(
        settings: ScreenTranslationSettings,
    )

    suspend fun saveSearchSettings(settings: SearchSettings)

    suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    )

    suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    )

    suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>)

    suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean)
}

private const val TAG = "AppSettingsStore"

class AppSettingsStore(
    private val context: Context,
) : SettingsStore {
    private val gson = AppJson.gson
    private val providerListType = object : TypeToken<List<ProviderSettings>>() {}.type
    private val assistantListType = object : TypeToken<List<Assistant>>() {}.type
    private val translationHistoryType = object : TypeToken<List<TranslationHistoryEntry>>() {}.type
    private val searchSettingsType = object : TypeToken<SearchSettings>() {}.type
    private val secureValueStore = SecureValueStore(context)

    override val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        val legacyBaseUrl = preferences[PreferencesKeys.baseUrl].orEmpty()
        val legacyApiKey = secureValueStore.getString(SecureKeys.legacyApiKey)
            .ifBlank { preferences[PreferencesKeys.apiKey].orEmpty() }
        val legacySelectedModel = preferences[PreferencesKeys.selectedModel].orEmpty()
        val secureProviderApiKeys = secureValueStore.getStringMap(SecureKeys.providerApiKeys)
        val secureSearchApiKeys = secureValueStore.getStringMap(SecureKeys.searchSourceApiKeys)
        val storedProviders = decodeProviders(preferences[PreferencesKeys.providersJson].orEmpty())
            .map { provider ->
                normalizeProviderFields(provider).copy(
                    apiKey = secureProviderApiKeys[provider.id]
                        .orEmpty()
                        .ifBlank { provider.apiKey.trim() },
                )
            }
        val resolvedProviders = if (storedProviders.isNotEmpty()) {
            storedProviders
        } else {
            buildLegacyProviders(
                baseUrl = legacyBaseUrl,
                apiKey = legacyApiKey,
                selectedModel = legacySelectedModel,
            )
        }
        val resolvedSelectedProviderId = preferences[PreferencesKeys.selectedProviderId]
            .orEmpty()
            .ifBlank { resolvedProviders.firstOrNull()?.id.orEmpty() }
        val effectiveActiveProvider = resolveEffectiveProvider(
            providers = resolvedProviders,
            selectedProviderId = resolvedSelectedProviderId,
        )
        val storedSearchSettings = decodeSearchSettings(
            preferences[PreferencesKeys.searchSettingsJson].orEmpty(),
        )
        val searchSettings = storedSearchSettings.copy(
            sources = storedSearchSettings.sources.map { source ->
                source.copy(
                    apiKey = secureSearchApiKeys[source.id]
                        .orEmpty()
                        .ifBlank { source.apiKey.trim() },
                )
            },
        ).normalized()

        AppSettings(
            baseUrl = effectiveActiveProvider?.baseUrl ?: legacyBaseUrl,
            apiKey = effectiveActiveProvider?.apiKey ?: legacyApiKey,
            selectedModel = effectiveActiveProvider?.selectedModel ?: legacySelectedModel,
            providers = resolvedProviders,
            selectedProviderId = resolvedSelectedProviderId,
            themeMode = ThemeMode.fromStorageValue(
                preferences[PreferencesKeys.themeMode].orEmpty(),
            ),
            messageTextScale = preferences[PreferencesKeys.messageTextScale] ?: 1f,
            reasoningExpandedByDefault = preferences[PreferencesKeys.reasoningExpandedByDefault] ?: true,
            showThinkingContent = preferences[PreferencesKeys.showThinkingContent] ?: true,
            autoCollapseThinking = preferences[PreferencesKeys.autoCollapseThinking] ?: true,
            autoPreviewImages = preferences[PreferencesKeys.autoPreviewImages] ?: true,
            codeBlockAutoWrap = preferences[PreferencesKeys.codeBlockAutoWrap] ?: false,
            codeBlockAutoCollapse = preferences[PreferencesKeys.codeBlockAutoCollapse] ?: false,
            showRoleplayAiHelper = preferences[PreferencesKeys.showRoleplayAiHelper] ?: true,
            roleplayLongformTargetChars = (preferences[PreferencesKeys.roleplayLongformTargetChars]
                ?: DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS).coerceIn(300, 2000),
            showRoleplayPresenceStrip = preferences[PreferencesKeys.showRoleplayPresenceStrip] ?: true,
            showRoleplayStatusStrip = preferences[PreferencesKeys.showRoleplayStatusStrip] ?: false,
            showOnlineRoleplayNarration = preferences[PreferencesKeys.showOnlineRoleplayNarration] ?: true,
            enableRoleplayNetMeme = preferences[PreferencesKeys.enableRoleplayNetMeme] ?: false,
            roleplayImmersiveMode = com.example.myapplication.model.RoleplayImmersiveMode.fromStorageValue(
                preferences[PreferencesKeys.roleplayImmersiveMode].orEmpty(),
            ),
            roleplayHighContrast = preferences[PreferencesKeys.roleplayHighContrast] ?: false,
            roleplayLineHeightScale = com.example.myapplication.model.RoleplayLineHeightScale.fromStorageValue(
                preferences[PreferencesKeys.roleplayLineHeightScale].orEmpty(),
            ),
            suppressRoleplayAssistantMismatchDialog = preferences[PreferencesKeys.suppressRoleplayAssistantMismatchDialog] ?: false,
            userDisplayName = preferences[PreferencesKeys.userDisplayName]
                .orEmpty()
                .ifBlank { com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME },
            userPersonaPrompt = preferences[PreferencesKeys.userPersonaPrompt].orEmpty(),
            userAvatarUri = preferences[PreferencesKeys.userAvatarUri].orEmpty(),
            userAvatarUrl = preferences[PreferencesKeys.userAvatarUrl].orEmpty(),
            translationHistory = decodeTranslationHistory(
                preferences[PreferencesKeys.translationHistoryJson].orEmpty(),
            ),
            assistants = decodeAssistants(preferences[PreferencesKeys.assistantsJson].orEmpty()),
            selectedAssistantId = preferences[PreferencesKeys.selectedAssistantId]
                .orEmpty()
                .ifBlank { com.example.myapplication.model.DEFAULT_ASSISTANT_ID },
            screenTranslationSettings = ScreenTranslationSettings(
                serviceEnabled = preferences[PreferencesKeys.screenTranslationServiceEnabled] ?: false,
                overlayEnabled = preferences[PreferencesKeys.screenTranslationOverlayEnabled] ?: true,
                overlayOffsetX = preferences[PreferencesKeys.screenTranslationOverlayOffsetX] ?: 0.85f,
                overlayOffsetY = preferences[PreferencesKeys.screenTranslationOverlayOffsetY] ?: 0.42f,
                targetLanguage = preferences[PreferencesKeys.screenTranslationTargetLanguage]
                    .orEmpty()
                    .ifBlank { "简体中文" },
                selectedTextEnabled = preferences[PreferencesKeys.screenTranslationSelectedTextEnabled] ?: true,
                showSourceText = preferences[PreferencesKeys.screenTranslationShowSourceText] ?: true,
                vendorGuideDismissed = preferences[PreferencesKeys.screenTranslationVendorGuideDismissed] ?: false,
            ),
            searchSettings = searchSettings,
        )
    }

    override suspend fun saveSettings(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
    ) {
        context.dataStore.edit { preferences ->
            val secureProviderApiKeys = secureValueStore.getStringMap(SecureKeys.providerApiKeys)
            val currentProviders = decodeProviders(
                preferences[PreferencesKeys.providersJson].orEmpty(),
            ).map { provider ->
                normalizeProviderFields(provider).copy(
                    apiKey = secureProviderApiKeys[provider.id]
                        .orEmpty()
                        .ifBlank { provider.apiKey.trim() },
                )
            }.ifEmpty { listOf(createDefaultProvider()) }

            val currentSelectedProviderId = preferences[PreferencesKeys.selectedProviderId]
                .orEmpty()
                .ifBlank { currentProviders.firstOrNull()?.id.orEmpty() }

            val updatedProviders = currentProviders.toMutableList()
            val activeIndex = updatedProviders.indexOfFirst { it.id == currentSelectedProviderId }
            val activeProvider = updatedProviders.getOrNull(activeIndex)?.copy(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
            ) ?: createDefaultProvider(
                baseUrl = baseUrl,
                apiKey = apiKey,
                selectedModel = selectedModel,
            )
            if (activeIndex >= 0) {
                updatedProviders[activeIndex] = activeProvider
            } else {
                updatedProviders.add(0, activeProvider)
            }

            val normalizedProviders = updatedProviders.mapIndexed { index, provider ->
                normalizeProvider(
                    provider = provider,
                    index = index,
                )
            }
            val effectiveActiveProvider = resolveEffectiveProvider(
                providers = normalizedProviders,
                selectedProviderId = activeProvider.id,
            )
            persistSecureProviderApiKeys(
                providers = normalizedProviders,
                selectedProviderId = activeProvider.id,
            )

            preferences[PreferencesKeys.baseUrl] = effectiveActiveProvider?.baseUrl.orEmpty()
            preferences[PreferencesKeys.apiKey] = ""
            preferences[PreferencesKeys.selectedModel] = effectiveActiveProvider?.selectedModel.orEmpty()
            preferences[PreferencesKeys.providersJson] = gson.toJson(
                normalizedProviders.map(::stripSensitiveProviderFields),
                providerListType,
            )
            preferences[PreferencesKeys.selectedProviderId] = activeProvider.id
        }
    }

    override suspend fun saveProviderSettings(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) {
        val normalizedProviders = providers.mapIndexed { index, provider ->
            normalizeProvider(provider, index)
        }
        val selectedProvider = normalizedProviders.firstOrNull { it.id == selectedProviderId }
            ?: normalizedProviders.firstOrNull()
        val effectiveActiveProvider = resolveEffectiveProvider(
            providers = normalizedProviders,
            selectedProviderId = selectedProvider?.id.orEmpty(),
        )
        persistSecureProviderApiKeys(
            providers = normalizedProviders,
            selectedProviderId = selectedProvider?.id.orEmpty(),
        )

        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.baseUrl] = effectiveActiveProvider?.baseUrl.orEmpty()
            preferences[PreferencesKeys.apiKey] = ""
            preferences[PreferencesKeys.selectedModel] = effectiveActiveProvider?.selectedModel.orEmpty()
            preferences[PreferencesKeys.providersJson] = gson.toJson(
                normalizedProviders.map(::stripSensitiveProviderFields),
                providerListType,
            )
            preferences[PreferencesKeys.selectedProviderId] = selectedProvider?.id.orEmpty()
        }
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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.themeMode] = themeMode.storageValue
            preferences[PreferencesKeys.messageTextScale] = messageTextScale.coerceIn(0.85f, 1.25f)
            preferences[PreferencesKeys.reasoningExpandedByDefault] = reasoningExpandedByDefault
            preferences[PreferencesKeys.showThinkingContent] = showThinkingContent
            preferences[PreferencesKeys.autoCollapseThinking] = autoCollapseThinking
            preferences[PreferencesKeys.autoPreviewImages] = autoPreviewImages
            preferences[PreferencesKeys.codeBlockAutoWrap] = codeBlockAutoWrap
            preferences[PreferencesKeys.codeBlockAutoCollapse] = codeBlockAutoCollapse
            preferences[PreferencesKeys.showRoleplayAiHelper] = showRoleplayAiHelper
            preferences[PreferencesKeys.roleplayLongformTargetChars] =
                roleplayLongformTargetChars.coerceIn(300, 2000)
            preferences[PreferencesKeys.showRoleplayPresenceStrip] = showRoleplayPresenceStrip
            preferences[PreferencesKeys.showRoleplayStatusStrip] = showRoleplayStatusStrip
            preferences[PreferencesKeys.showOnlineRoleplayNarration] = showOnlineRoleplayNarration
            preferences[PreferencesKeys.enableRoleplayNetMeme] = enableRoleplayNetMeme
            preferences[PreferencesKeys.roleplayImmersiveMode] = roleplayImmersiveMode.storageValue
            preferences[PreferencesKeys.roleplayHighContrast] = roleplayHighContrast
            preferences[PreferencesKeys.roleplayLineHeightScale] = roleplayLineHeightScale.storageValue
        }
    }

    override suspend fun saveScreenTranslationSettings(
        settings: ScreenTranslationSettings,
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.screenTranslationServiceEnabled] = settings.serviceEnabled
            preferences[PreferencesKeys.screenTranslationOverlayEnabled] = settings.overlayEnabled
            preferences[PreferencesKeys.screenTranslationOverlayOffsetX] = settings.overlayOffsetX
            preferences[PreferencesKeys.screenTranslationOverlayOffsetY] = settings.overlayOffsetY
            preferences[PreferencesKeys.screenTranslationTargetLanguage] = settings.targetLanguage
            preferences[PreferencesKeys.screenTranslationSelectedTextEnabled] = settings.selectedTextEnabled
            preferences[PreferencesKeys.screenTranslationShowSourceText] = settings.showSourceText
            preferences[PreferencesKeys.screenTranslationVendorGuideDismissed] = settings.vendorGuideDismissed
        }
    }

    override suspend fun saveSearchSettings(settings: SearchSettings) {
        val normalizedSettings = settings.normalized()
        persistSecureSearchApiKeys(normalizedSettings.sources)
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.searchSettingsJson] = gson.toJson(
                normalizedSettings.copy(
                    sources = normalizedSettings.sources.map(::stripSensitiveSearchFields),
                ),
                searchSettingsType,
            )
        }
    }

    override suspend fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.userDisplayName] = displayName
                .trim()
                .ifBlank { com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME }
            preferences[PreferencesKeys.userPersonaPrompt] = personaPrompt
                .replace("\r\n", "\n")
                .trim()
            preferences[PreferencesKeys.userAvatarUri] = avatarUri.trim()
            preferences[PreferencesKeys.userAvatarUrl] = avatarUrl.trim()
        }
    }

    override suspend fun saveAssistants(
        assistants: List<Assistant>,
        selectedAssistantId: String,
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.assistantsJson] = gson.toJson(assistants, assistantListType)
            preferences[PreferencesKeys.selectedAssistantId] = selectedAssistantId
        }
    }

    override suspend fun saveTranslationHistory(history: List<TranslationHistoryEntry>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.translationHistoryJson] = gson.toJson(
                history.sortedByDescending(TranslationHistoryEntry::createdAt).take(MAX_TRANSLATION_HISTORY),
                translationHistoryType,
            )
        }
    }

    override suspend fun saveRoleplayAssistantMismatchDialogPreference(suppressed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.suppressRoleplayAssistantMismatchDialog] = suppressed
        }
    }

    suspend fun migrateSensitiveData() {
        context.dataStore.edit { preferences ->
            val currentSecureProviderApiKeys = secureValueStore.getStringMap(SecureKeys.providerApiKeys).toMutableMap()
            val currentSecureSearchApiKeys = secureValueStore.getStringMap(SecureKeys.searchSourceApiKeys)
            val legacyPlainApiKey = preferences[PreferencesKeys.apiKey].orEmpty()
            val storedProviders = decodeProviders(preferences[PreferencesKeys.providersJson].orEmpty())
            val migratedSearchSettings = SearchSettingsSensitiveMigrationSupport.migrate(
                rawJson = preferences[PreferencesKeys.searchSettingsJson].orEmpty(),
                existingSecureApiKeys = currentSecureSearchApiKeys,
            )

            var updated = false

            if (legacyPlainApiKey.isNotBlank() &&
                secureValueStore.getString(SecureKeys.legacyApiKey).isBlank()
            ) {
                secureValueStore.putString(SecureKeys.legacyApiKey, legacyPlainApiKey)
                updated = true
            }

            val sanitizedProviders = storedProviders.map { provider ->
                val trimmedApiKey = provider.apiKey.trim()
                if (trimmedApiKey.isNotBlank() &&
                    currentSecureProviderApiKeys[provider.id].isNullOrBlank()
                ) {
                    currentSecureProviderApiKeys[provider.id] = trimmedApiKey
                    updated = true
                }
                if (provider.apiKey.isNotBlank()) {
                    updated = true
                }
                stripSensitiveProviderFields(provider)
            }

            if (!updated) {
                if (migratedSearchSettings == null) {
                    return@edit
                }
            }

            if (migratedSearchSettings != null) {
                secureValueStore.putStringMap(
                    SecureKeys.searchSourceApiKeys,
                    migratedSearchSettings.mergedSecureApiKeys,
                )
                preferences[PreferencesKeys.searchSettingsJson] = migratedSearchSettings.sanitizedJson
            }

            secureValueStore.putStringMap(SecureKeys.providerApiKeys, currentSecureProviderApiKeys)
            preferences[PreferencesKeys.apiKey] = ""
            preferences[PreferencesKeys.providersJson] = gson.toJson(sanitizedProviders, providerListType)
        }
    }

    private fun decodeProviders(rawJson: String): List<ProviderSettings> {
        if (rawJson.isBlank()) {
            return emptyList()
        }
        return runCatching {
            gson.fromJson<List<ProviderSettings>>(rawJson, providerListType).orEmpty()
        }.logFailure(TAG) { "decodeProviders fromJson failed, raw.len=${rawJson.length}" }
            .getOrDefault(emptyList())
    }

    private fun buildLegacyProviders(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
    ): List<ProviderSettings> {
        if (baseUrl.isBlank() && apiKey.isBlank() && selectedModel.isBlank()) {
            return emptyList()
        }
        return listOf(
            normalizeProviderFields(
                createDefaultProvider(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    selectedModel = selectedModel,
                ),
            ),
        )
    }

    private fun decodeSearchSettings(rawJson: String): SearchSettings {
        if (rawJson.isBlank()) {
            return SearchSettings()
        }
        return runCatching {
            gson.fromJson<SearchSettings>(rawJson, searchSettingsType)
                ?.normalized()
                ?: SearchSettings()
        }.logFailure(TAG) { "decodeSearchSettings fromJson failed, raw.len=${rawJson.length}" }
            .getOrDefault(SearchSettings())
    }

    /** Gson 反序列化旧 JSON 时新字段为 null，补充 type、apiProtocol、OpenAI text api mode 和 models。 */
    private fun normalizeProviderFields(provider: ProviderSettings): ProviderSettings {
        val resolvedType = provider.type ?: ProviderType.fromBaseUrl(provider.baseUrl)
        val resolvedProtocol = provider.apiProtocol
            ?: inferProviderApiProtocol(
                baseUrl = provider.baseUrl,
                providerType = resolvedType,
            )
        val resolvedTextApiMode = provider.openAiTextApiMode ?: OpenAiTextApiMode.CHAT_COMPLETIONS
        val resolvedModels = provider.models ?: provider.availableModels.map(::inferredModelInfo)
        val normalizedTitleSummaryModel = (provider.titleSummaryModel as String?).orEmpty().trim()
        val normalizedChatSuggestionModel = (provider.chatSuggestionModel as String?).orEmpty().trim()
        val normalizedMemoryModel = (provider.memoryModel as String?).orEmpty().trim()
        val normalizedTranslationModel = (provider.translationModel as String?).orEmpty().trim()
        val normalizedPhoneSnapshotModel = (provider.phoneSnapshotModel as String?).orEmpty().trim()
        val normalizedSearchModel = (provider.searchModel as String?).orEmpty().trim()
        val normalizedGiftImageModel = (provider.giftImageModel as String?).orEmpty().trim()
        return provider.copy(
            baseUrl = normalizeProviderBaseUrl(provider.baseUrl, resolvedType, resolvedProtocol),
            type = resolvedType,
            apiProtocol = resolvedProtocol,
            openAiTextApiMode = resolvedTextApiMode,
            chatCompletionsPath = normalizeChatCompletionsPath(provider.chatCompletionsPath),
            models = resolvedModels,
            titleSummaryModel = normalizedTitleSummaryModel,
            titleSummaryModelMode = normalizeFunctionModelMode(
                rawMode = provider.titleSummaryModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedTitleSummaryModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            chatSuggestionModel = normalizedChatSuggestionModel,
            chatSuggestionModelMode = normalizeFunctionModelMode(
                rawMode = provider.chatSuggestionModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedChatSuggestionModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            memoryModel = normalizedMemoryModel,
            memoryModelMode = normalizeFunctionModelMode(
                rawMode = provider.memoryModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedMemoryModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            translationModel = normalizedTranslationModel,
            translationModelMode = normalizeFunctionModelMode(
                rawMode = provider.translationModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedTranslationModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            phoneSnapshotModel = normalizedPhoneSnapshotModel,
            phoneSnapshotModelMode = normalizeFunctionModelMode(
                rawMode = provider.phoneSnapshotModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedPhoneSnapshotModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            searchModel = normalizedSearchModel,
            searchModelMode = normalizeFunctionModelMode(
                rawMode = provider.searchModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedSearchModel,
                blankMode = ProviderFunctionModelMode.FOLLOW_DEFAULT,
            ),
            giftImageModel = normalizedGiftImageModel,
            giftImageModelMode = normalizeFunctionModelMode(
                rawMode = provider.giftImageModelMode as ProviderFunctionModelMode?,
                rawModel = normalizedGiftImageModel,
                blankMode = ProviderFunctionModelMode.DISABLED,
            ),
        )
    }

    private fun normalizeFunctionModelMode(
        rawMode: ProviderFunctionModelMode?,
        rawModel: String,
        blankMode: ProviderFunctionModelMode,
    ): ProviderFunctionModelMode {
        val normalizedModel = rawModel.trim()
        return when (rawMode ?: if (normalizedModel.isNotBlank()) ProviderFunctionModelMode.CUSTOM else blankMode) {
            ProviderFunctionModelMode.CUSTOM -> {
                if (normalizedModel.isBlank()) {
                    blankMode
                } else {
                    ProviderFunctionModelMode.CUSTOM
                }
            }

            ProviderFunctionModelMode.FOLLOW_DEFAULT -> ProviderFunctionModelMode.FOLLOW_DEFAULT
            ProviderFunctionModelMode.DISABLED -> ProviderFunctionModelMode.DISABLED
        }
    }

    private fun normalizeProviderBaseUrl(
        baseUrl: String,
        providerType: ProviderType,
        apiProtocol: com.example.myapplication.model.ProviderApiProtocol,
    ): String {
        val trimmed = baseUrl.trim()
        if (trimmed.isBlank()) {
            return ""
        }
        val normalizedBaseUrl = if (trimmed.endsWith('/')) trimmed else "$trimmed/"
        if (providerType != ProviderType.GOOGLE || apiProtocol != com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE) {
            return normalizedBaseUrl
        }
        val lower = normalizedBaseUrl.lowercase()
        return if (lower.contains("/openai/")) {
            normalizedBaseUrl
        } else {
            "${normalizedBaseUrl}openai/"
        }
    }

    private fun inferProviderApiProtocol(
        baseUrl: String,
        providerType: ProviderType,
    ): com.example.myapplication.model.ProviderApiProtocol {
        val lower = baseUrl.trim().lowercase()
        return when {
            "api.anthropic.com" in lower -> com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC
            lower.isBlank() && providerType == ProviderType.ANTHROPIC -> com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC
            else -> com.example.myapplication.model.ProviderApiProtocol.OPENAI_COMPATIBLE
        }
    }

    private fun normalizeChatCompletionsPath(path: String): String {
        val trimmed = path.trim().ifBlank { com.example.myapplication.model.DEFAULT_CHAT_COMPLETIONS_PATH }
        val prefixed = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return prefixed.replace(Regex("/{2,}"), "/")
    }

    private fun normalizeProvider(
        provider: ProviderSettings,
        index: Int,
    ): ProviderSettings {
        return normalizeProviderFields(
            provider.copy(
                name = provider.name.trim().ifBlank {
                    "${com.example.myapplication.model.DEFAULT_PROVIDER_NAME} ${index + 1}"
                },
                baseUrl = provider.baseUrl.trim(),
                apiKey = provider.apiKey.trim(),
                selectedModel = provider.selectedModel.trim(),
                titleSummaryModel = (provider.titleSummaryModel as String?).orEmpty().trim(),
                chatSuggestionModel = (provider.chatSuggestionModel as String?).orEmpty().trim(),
                memoryModel = (provider.memoryModel as String?).orEmpty().trim(),
                translationModel = (provider.translationModel as String?).orEmpty().trim(),
                phoneSnapshotModel = (provider.phoneSnapshotModel as String?).orEmpty().trim(),
                searchModel = (provider.searchModel as String?).orEmpty().trim(),
                giftImageModel = (provider.giftImageModel as String?).orEmpty().trim(),
                chatCompletionsPath = normalizeChatCompletionsPath(provider.chatCompletionsPath),
            ),
        )
    }

    private fun stripSensitiveProviderFields(provider: ProviderSettings): ProviderSettings {
        return provider.copy(apiKey = "")
    }

    private fun persistSecureProviderApiKeys(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) {
        val secureApiKeys = providers.associate { provider ->
            provider.id to provider.apiKey.trim()
        }
        secureValueStore.putStringMap(SecureKeys.providerApiKeys, secureApiKeys)
        val activeProvider = resolveEffectiveProvider(
            providers = providers,
            selectedProviderId = selectedProviderId,
        )
        secureValueStore.putString(
            SecureKeys.legacyApiKey,
            activeProvider?.apiKey.orEmpty(),
        )
    }

    private fun persistSecureSearchApiKeys(
        sources: List<SearchSourceConfig>,
    ) {
        val secureApiKeys = sources.associate { source ->
            source.id to source.apiKey.trim()
        }
        secureValueStore.putStringMap(
            SecureKeys.searchSourceApiKeys,
            secureApiKeys,
        )
    }

    private fun stripSensitiveSearchFields(
        source: SearchSourceConfig,
    ): SearchSourceConfig {
        return source.copy(apiKey = "")
    }

    private fun resolveEffectiveProvider(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ): ProviderSettings? {
        return providers.firstOrNull { it.id == selectedProviderId && it.enabled }
            ?: providers.firstOrNull { it.enabled }
            ?: providers.firstOrNull()
    }

    private fun decodeAssistants(rawJson: String): List<Assistant> {
        if (rawJson.isBlank()) {
            return emptyList()
        }
        return runCatching {
            gson.fromJson<List<Assistant>>(rawJson, assistantListType).orEmpty()
                .map(::normalizeAssistant)
        }.logFailure(TAG) { "decodeAssistants fromJson failed, raw.len=${rawJson.length}" }
            .getOrDefault(emptyList())
    }

    private fun normalizeAssistant(assistant: Assistant): Assistant {
        return assistant.copy(
            iconName = sanitizeText(assistant.iconName as String?).ifBlank { DEFAULT_ASSISTANT_ICON },
            avatarUri = sanitizeText(assistant.avatarUri as String?),
            name = sanitizeText(assistant.name as String?),
            description = sanitizeText(assistant.description as String?),
            systemPrompt = sanitizeMultilineText(assistant.systemPrompt as String?),
            scenario = sanitizeMultilineText(assistant.scenario as String?),
            greeting = sanitizeMultilineText(assistant.greeting as String?),
            exampleDialogues = sanitizeStringList(assistant.exampleDialogues as? List<*>),
            creatorNotes = sanitizeMultilineText(assistant.creatorNotes as String?),
            linkedWorldBookIds = sanitizeStringList(assistant.linkedWorldBookIds as? List<*>),
            linkedWorldBookBookIds = sanitizeStringList(assistant.linkedWorldBookBookIds as? List<*>),
            memoryMaxItems = assistant.memoryMaxItems.takeIf { it > 0 } ?: DEFAULT_MEMORY_MAX_ITEMS,
            worldBookMaxEntries = assistant.worldBookMaxEntries.takeIf { it > 0 }
                ?: DEFAULT_WORLD_BOOK_MAX_ENTRIES,
            tags = sanitizeStringList(assistant.tags as? List<*>),
        )
    }

    private fun sanitizeText(value: String?): String {
        return value?.trim().orEmpty()
    }

    private fun sanitizeMultilineText(value: String?): String {
        return value
            ?.replace("\r\n", "\n")
            ?.trim()
            .orEmpty()
    }

    private fun sanitizeStringList(values: List<*>?): List<String> {
        return values.orEmpty()
            .mapNotNull { value ->
                (value as? String)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
            }
    }

    private fun decodeTranslationHistory(rawJson: String): List<TranslationHistoryEntry> {
        if (rawJson.isBlank()) {
            return emptyList()
        }
        return runCatching {
            gson.fromJson<List<TranslationHistoryEntry>>(rawJson, translationHistoryType).orEmpty()
        }.logFailure(TAG) { "decodeTranslationHistory fromJson failed, raw.len=${rawJson.length}" }
            .getOrDefault(emptyList())
    }

    private object PreferencesKeys {
        val baseUrl = stringPreferencesKey("base_url")
        val apiKey = stringPreferencesKey("api_key")
        val selectedModel = stringPreferencesKey("selected_model")
        val providersJson = stringPreferencesKey("providers_json")
        val selectedProviderId = stringPreferencesKey("selected_provider_id")
        val themeMode = stringPreferencesKey("theme_mode")
        val messageTextScale = floatPreferencesKey("message_text_scale")
        val reasoningExpandedByDefault = booleanPreferencesKey("reasoning_expanded_by_default")
        val showThinkingContent = booleanPreferencesKey("show_thinking_content")
        val autoCollapseThinking = booleanPreferencesKey("auto_collapse_thinking")
        val autoPreviewImages = booleanPreferencesKey("auto_preview_images")
        val codeBlockAutoWrap = booleanPreferencesKey("code_block_auto_wrap")
        val codeBlockAutoCollapse = booleanPreferencesKey("code_block_auto_collapse")
        val showRoleplayAiHelper = booleanPreferencesKey("show_roleplay_ai_helper")
        val roleplayLongformTargetChars = PreferencesKeysCompat.intPreferencesKey("roleplay_longform_target_chars")
        val showRoleplayPresenceStrip = booleanPreferencesKey("show_roleplay_presence_strip")
        val showRoleplayStatusStrip = booleanPreferencesKey("show_roleplay_status_strip")
        val showOnlineRoleplayNarration = booleanPreferencesKey("show_online_roleplay_narration")
        val enableRoleplayNetMeme = booleanPreferencesKey("enable_roleplay_net_meme")
        val roleplayImmersiveMode = stringPreferencesKey("roleplay_immersive_mode")
        val roleplayHighContrast = booleanPreferencesKey("roleplay_high_contrast")
        val roleplayLineHeightScale = stringPreferencesKey("roleplay_line_height_scale")
        val suppressRoleplayAssistantMismatchDialog = booleanPreferencesKey("suppress_roleplay_assistant_mismatch_dialog")
        val userDisplayName = stringPreferencesKey("user_display_name")
        val userPersonaPrompt = stringPreferencesKey("user_persona_prompt")
        val userAvatarUri = stringPreferencesKey("user_avatar_uri")
        val userAvatarUrl = stringPreferencesKey("user_avatar_url")
        val translationHistoryJson = stringPreferencesKey("translation_history_json")
        val assistantsJson = stringPreferencesKey("assistants_json")
        val selectedAssistantId = stringPreferencesKey("selected_assistant_id")
        val screenTranslationServiceEnabled = booleanPreferencesKey("screen_translation_service_enabled")
        val screenTranslationOverlayEnabled = booleanPreferencesKey("screen_translation_overlay_enabled")
        val screenTranslationOverlayOffsetX = floatPreferencesKey("screen_translation_overlay_offset_x")
        val screenTranslationOverlayOffsetY = floatPreferencesKey("screen_translation_overlay_offset_y")
        val screenTranslationTargetLanguage = stringPreferencesKey("screen_translation_target_language")
        val screenTranslationSelectedTextEnabled = booleanPreferencesKey("screen_translation_selected_text_enabled")
        val screenTranslationShowSourceText = booleanPreferencesKey("screen_translation_show_source_text")
        val screenTranslationVendorGuideDismissed = booleanPreferencesKey("screen_translation_vendor_guide_dismissed")
        val searchSettingsJson = stringPreferencesKey("search_settings_json")
    }

    private object SecureKeys {
        const val legacyApiKey = "legacy_api_key"
        const val providerApiKeys = "provider_api_keys"
        const val searchSourceApiKeys = "search_source_api_keys"
    }

    private companion object {
        const val MAX_TRANSLATION_HISTORY = 20
    }
}

private object PreferencesKeysCompat {
    fun intPreferencesKey(name: String) = androidx.datastore.preferences.core.intPreferencesKey(name)
}

internal data class SearchSettingsSensitiveMigrationResult(
    val sanitizedJson: String,
    val mergedSecureApiKeys: Map<String, String>,
)

internal object SearchSettingsSensitiveMigrationSupport {
    private val gson = AppJson.gson
    private val searchSettingsType = object : TypeToken<SearchSettings>() {}.type

    fun migrate(
        rawJson: String,
        existingSecureApiKeys: Map<String, String>,
    ): SearchSettingsSensitiveMigrationResult? {
        if (rawJson.isBlank()) {
            return null
        }
        val normalizedSettings = runCatching {
            gson.fromJson<SearchSettings>(rawJson, searchSettingsType)
                ?.normalized()
        }.logFailure("SearchSensMigrate") { "fromJson failed, raw.len=${rawJson.length}" }
            .getOrNull() ?: return null

        var updated = false
        val mergedSecureApiKeys = existingSecureApiKeys
            .mapValues { (_, value) -> value.trim() }
            .filterValues { it.isNotBlank() }
            .toMutableMap()

        normalizedSettings.sources.forEach { source ->
            val trimmedApiKey = source.apiKey.trim()
            if (trimmedApiKey.isNotBlank() && mergedSecureApiKeys[source.id].isNullOrBlank()) {
                mergedSecureApiKeys[source.id] = trimmedApiKey
                updated = true
            }
            if (source.apiKey.isNotBlank()) {
                updated = true
            }
        }

        if (!updated) {
            return null
        }

        return SearchSettingsSensitiveMigrationResult(
            sanitizedJson = gson.toJson(
                normalizedSettings.copy(
                    sources = normalizedSettings.sources.map { source ->
                        source.copy(apiKey = "")
                    },
                ),
                searchSettingsType,
            ),
            mergedSecureApiKeys = mergedSecureApiKeys,
        )
    }
}
