package com.example.myapplication.model

data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val providers: List<ProviderSettings> = emptyList(),
    val selectedProviderId: String = "",
    val functionModelProviderIds: FunctionModelProviderIds = FunctionModelProviderIds(),
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val messageTextScale: Float = 1f,
    val reasoningExpandedByDefault: Boolean = true,
    val showThinkingContent: Boolean = true,
    val autoCollapseThinking: Boolean = true,
    val autoPreviewImages: Boolean = true,
    val codeBlockAutoWrap: Boolean = false,
    val codeBlockAutoCollapse: Boolean = false,
    val showRoleplayAiHelper: Boolean = true,
    val roleplayLongformTargetChars: Int = DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS,
    val showRoleplayPresenceStrip: Boolean = true,
    val showRoleplayStatusStrip: Boolean = false,
    val showOnlineRoleplayNarration: Boolean = true,
    val enableRoleplayNetMeme: Boolean = false,
    val suppressRoleplayAssistantMismatchDialog: Boolean = false,
    val roleplayImmersiveMode: RoleplayImmersiveMode = RoleplayImmersiveMode.EDGE_TO_EDGE,
    val roleplayHighContrast: Boolean = false,
    val roleplayLineHeightScale: RoleplayLineHeightScale = RoleplayLineHeightScale.NORMAL,
    val roleplayNoBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
    val userDisplayName: String = DEFAULT_USER_DISPLAY_NAME,
    val userPersonaPrompt: String = "",
    val userAvatarUri: String = "",
    val userAvatarUrl: String = "",
    val userPersonaMasks: List<UserPersonaMask> = emptyList(),
    val defaultUserPersonaMaskId: String = "",
    val translationHistory: List<TranslationHistoryEntry> = emptyList(),
    val assistants: List<Assistant> = emptyList(),
    val selectedAssistantId: String = DEFAULT_ASSISTANT_ID,
    val screenTranslationSettings: ScreenTranslationSettings = ScreenTranslationSettings(),
    val searchSettings: SearchSettings = SearchSettings(),
    val memoryAutoSummaryEvery: Int = DEFAULT_MEMORY_AUTO_SUMMARY_EVERY,
    val memoryCapacity: Int = DEFAULT_MEMORY_CAPACITY,
    val memoryExtractionPrompt: String = "",
    val memoryInjectionPrompt: String = "",
    val memoryInjectionPosition: MemoryInjectionPosition = MemoryInjectionPosition.AFTER_WORLD_BOOK,
    val contextLogEnabled: Boolean = true,
    val contextLogCapacity: Int = DEFAULT_CONTEXT_LOG_CAPACITY,
) {
    fun resolvedAssistants(): List<Assistant> {
        val customAssistantsById = assistants.associateBy { it.id }
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        val mergedBuiltins = BUILTIN_ASSISTANTS.map { builtin ->
            customAssistantsById[builtin.id]?.copy(isBuiltin = builtin.isBuiltin) ?: builtin
        }
        val customAssistants = assistants.filter { it.id !in builtinIds }
        return mergedBuiltins + customAssistants
    }

    fun activeAssistant(): Assistant? {
        return resolvedAssistants().firstOrNull { it.id == selectedAssistantId }
            ?: resolvedAssistants().firstOrNull()
    }

    fun resolvedProviders(): List<ProviderSettings> {
        return if (providers.isNotEmpty()) {
            providers
        } else {
            legacyProviderOrNull()?.let(::listOf).orEmpty()
        }
    }

    fun enabledProviders(): List<ProviderSettings> {
        return resolvedProviders().filter { it.enabled }
    }

    fun activeProvider(): ProviderSettings? {
        val resolvedProviders = resolvedProviders()
        if (resolvedProviders.isEmpty()) {
            return legacyProviderOrNull()
        }

        val enabledProviders = resolvedProviders.filter { it.enabled }
        return enabledProviders.firstOrNull { it.id == selectedProviderId }
            ?: enabledProviders.firstOrNull()
    }

    fun resolveFunctionProvider(function: ProviderFunction): ProviderSettings? {
        if (function == ProviderFunction.CHAT) {
            return activeProvider()
        }
        val enabledProviders = enabledProviders()
        val assignedProviderId = functionModelProviderIds
            .normalized(resolvedProviders().map(ProviderSettings::id).toSet())
            .providerIdFor(function)
        return enabledProviders.firstOrNull { it.id == assignedProviderId }
            ?: activeProvider()
    }

    fun resolveFunctionModel(function: ProviderFunction): String {
        return resolveFunctionProvider(function)
            ?.resolveFunctionModel(function)
            .orEmpty()
    }

    fun resolveFunctionModelMode(function: ProviderFunction): ProviderFunctionModelMode {
        return resolveFunctionProvider(function)
            ?.resolveFunctionModelMode(function)
            ?: if (function == ProviderFunction.GIFT_IMAGE) {
                ProviderFunctionModelMode.DISABLED
            } else {
                ProviderFunctionModelMode.FOLLOW_DEFAULT
            }
    }

    fun providerCount(): Int = resolvedProviders().size

    fun hasBaseCredentials(): Boolean {
        val resolvedProviders = resolvedProviders()
        if (resolvedProviders.isNotEmpty()) {
            return activeProvider()?.hasBaseCredentials() == true
        }

        return baseUrl.isNotBlank() && apiKey.isNotBlank()
    }

    fun hasRequiredConfig(): Boolean {
        val resolvedProviders = resolvedProviders()
        if (resolvedProviders.isNotEmpty()) {
            return activeProvider()?.hasRequiredConfig() == true
        }

        return hasBaseCredentials() && selectedModel.isNotBlank()
    }

    fun resolvedUserDisplayName(): String {
        return userDisplayName.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME }
    }

    fun resolvedUserAvatar(): String {
        return userAvatarUrl.trim().ifBlank { userAvatarUri.trim() }
    }

    fun normalizedUserPersonaMasks(): List<UserPersonaMask> {
        val masks = userPersonaMasks
            .map(UserPersonaMask::normalized)
            .distinctBy(UserPersonaMask::id)
        val profileMask = profileBackedPersonaMask()
            ?.takeIf { profile -> masks.none { it.id == profile.id } }
        return listOfNotNull(profileMask) + masks
    }

    fun resolvedDefaultUserPersonaMask(): UserPersonaMask? {
        val masks = normalizedUserPersonaMasks()
        if (masks.isEmpty()) {
            return null
        }
        return masks.firstOrNull { it.id == defaultUserPersonaMaskId.trim() }
            ?: masks.first()
    }

    fun resolveUserPersona(
        maskId: String = "",
        displayNameOverride: String = "",
        personaPromptOverride: String = "",
        avatarUriOverride: String = "",
        avatarUrlOverride: String = "",
    ): ResolvedUserPersona {
        val masks = normalizedUserPersonaMasks()
        val selectedMask = masks.firstOrNull { it.id == maskId.trim() }
            ?: resolvedDefaultUserPersonaMask()
        return ResolvedUserPersona(
            displayName = displayNameOverride.trim()
                .ifBlank { selectedMask?.name.orEmpty() }
                .ifBlank { resolvedUserDisplayName() },
            personaPrompt = personaPromptOverride.replace("\r\n", "\n").trim()
                .ifBlank { selectedMask?.personaPrompt.orEmpty() }
                .ifBlank { userPersonaPrompt.replace("\r\n", "\n").trim() },
            avatarUri = avatarUriOverride.trim()
                .ifBlank { selectedMask?.avatarUri.orEmpty() }
                .ifBlank { userAvatarUri.trim() },
            avatarUrl = avatarUrlOverride.trim()
                .ifBlank { selectedMask?.avatarUrl.orEmpty() }
                .ifBlank { userAvatarUrl.trim() },
            sourceMaskId = selectedMask?.id.orEmpty(),
        )
    }

    private fun profileBackedPersonaMask(): UserPersonaMask? {
        val displayName = resolvedUserDisplayName()
        val persona = userPersonaPrompt.replace("\r\n", "\n").trim()
        val avatarUri = userAvatarUri.trim()
        val avatarUrl = userAvatarUrl.trim()
        val hasProfileContent = displayName != DEFAULT_USER_DISPLAY_NAME ||
            persona.isNotBlank() ||
            avatarUri.isNotBlank() ||
            avatarUrl.isNotBlank()
        if (!hasProfileContent) {
            return null
        }
        return UserPersonaMask(
            id = USER_PROFILE_PERSONA_MASK_ID,
            name = displayName,
            avatarUri = avatarUri,
            avatarUrl = avatarUrl,
            personaPrompt = persona,
            note = "由现有个人资料自动打包",
            createdAt = 1L,
            updatedAt = 1L,
        )
    }

    fun resolvedSearchSettings(): SearchSettings {
        return searchSettings.normalized()
    }

    fun activeSearchSource(
        activeProvider: ProviderSettings? = activeProvider(),
    ): SearchSourceConfig? {
        val source = resolvedSearchSettings().selectedSourceOrNull() ?: return null
        return when (source.type) {
            SearchSourceType.LLM_SEARCH -> {
                val provider = resolveSearchSourceProvider(source)
                source.takeIf {
                    it.enabled &&
                        provider?.hasBaseCredentials() == true &&
                        provider.supportsLlmSearchSource() &&
                        provider.resolveFunctionModel(ProviderFunction.SEARCH).isNotBlank()
                }
            }

            else -> source.takeIf(SearchSourceConfig::isConfigured)
        }
    }

    fun hasConfiguredSearchSource(
        activeProvider: ProviderSettings? = activeProvider(),
    ): Boolean {
        return activeSearchSource(activeProvider) != null
    }

    fun resolveSearchSourceProvider(
        source: SearchSourceConfig,
    ): ProviderSettings? {
        if (source.type != SearchSourceType.LLM_SEARCH) {
            return null
        }
        return resolvedProviders().firstOrNull { provider ->
            provider.id == source.providerId && provider.enabled
        }
    }

    private fun legacyProviderOrNull(): ProviderSettings? {
        if (baseUrl.isBlank() && apiKey.isBlank() && selectedModel.isBlank()) {
            return null
        }
        return createDefaultProvider(
            id = LEGACY_PROVIDER_ID,
            baseUrl = baseUrl,
            apiKey = apiKey,
            selectedModel = selectedModel,
        )
    }
}

const val DEFAULT_USER_DISPLAY_NAME = "用户"
const val DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS = 900
const val DEFAULT_MEMORY_AUTO_SUMMARY_EVERY = 10
const val DEFAULT_MEMORY_CAPACITY = 1000
const val DEFAULT_CONTEXT_LOG_CAPACITY = 15
const val MEMORY_AUTO_SUMMARY_EVERY_MIN = 0
const val MEMORY_AUTO_SUMMARY_EVERY_MAX = 50
const val MEMORY_CAPACITY_MIN = 50
const val MEMORY_CAPACITY_MAX = 5000
const val CONTEXT_LOG_CAPACITY_MIN = 5
const val CONTEXT_LOG_CAPACITY_MAX = 50

enum class RoleplayImmersiveMode(val storageValue: String) {
    EDGE_TO_EDGE("edge_to_edge"),
    HIDE_SYSTEM_BARS("hide_system_bars"),
    NONE("none");

    companion object {
        fun fromStorageValue(value: String): RoleplayImmersiveMode {
            return entries.find { it.storageValue == value } ?: EDGE_TO_EDGE
        }
    }
}

enum class RoleplayLineHeightScale(val storageValue: String, val scaleFactor: Float) {
    COMPACT("compact", 0.85f),
    NORMAL("normal", 1.0f),
    RELAXED("relaxed", 1.15f);

    companion object {
        fun fromStorageValue(value: String): RoleplayLineHeightScale {
            return entries.find { it.storageValue == value } ?: NORMAL
        }
    }
}

enum class MemoryInjectionPosition(val storageValue: String, val label: String) {
    BEFORE_PROMPT("before_prompt", "靠前 · 系统提示词后"),
    AFTER_WORLD_BOOK("after_world_book", "默认 · 世界书后"),
    AT_END("at_end", "末尾 · 所有内容后");

    companion object {
        fun fromStorageValue(value: String): MemoryInjectionPosition {
            return entries.find { it.storageValue == value } ?: AFTER_WORLD_BOOK
        }
    }
}
