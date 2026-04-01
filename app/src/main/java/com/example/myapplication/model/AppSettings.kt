package com.example.myapplication.model

data class AppSettings(
    val baseUrl: String = "",
    val apiKey: String = "",
    val selectedModel: String = "",
    val providers: List<ProviderSettings> = emptyList(),
    val selectedProviderId: String = "",
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
    val suppressRoleplayAssistantMismatchDialog: Boolean = false,
    val userDisplayName: String = DEFAULT_USER_DISPLAY_NAME,
    val userAvatarUri: String = "",
    val userAvatarUrl: String = "",
    val translationHistory: List<TranslationHistoryEntry> = emptyList(),
    val assistants: List<Assistant> = emptyList(),
    val selectedAssistantId: String = DEFAULT_ASSISTANT_ID,
    val screenTranslationSettings: ScreenTranslationSettings = ScreenTranslationSettings(),
    val searchSettings: SearchSettings = SearchSettings(),
) {
    fun resolvedAssistants(): List<Assistant> {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        val customAssistants = assistants.filter { it.id !in builtinIds }
        return BUILTIN_ASSISTANTS + customAssistants
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
