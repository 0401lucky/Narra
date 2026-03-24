package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.AiRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.BUILTIN_ASSISTANTS
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.createDefaultProvider
import com.example.myapplication.model.inferredModelInfo
import com.example.myapplication.model.mergeModelInfosPreservingOverrides
import com.example.myapplication.model.withAbilityOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val providers: List<ProviderSettings> = emptyList(),
    val selectedProviderId: String = "",
    val isLoadingModels: Boolean = false,
    val loadingProviderId: String = "",
    val isSaving: Boolean = false,
    val message: String? = null,
    val savedSettings: AppSettings = AppSettings(),
    val connectionHealthMap: Map<String, ConnectionHealth> = emptyMap(),
    val showTemplateDialog: Boolean = false,
    val pendingFetchedModels: List<ModelInfo> = emptyList(),
    val pendingFetchProviderId: String = "",
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
    val screenTranslationSettings: ScreenTranslationSettings = ScreenTranslationSettings(),
) {
    val currentProvider: ProviderSettings?
        get() = providers.firstOrNull { it.id == selectedProviderId } ?: providers.firstOrNull()

    val baseUrl: String
        get() = currentProvider?.baseUrl.orEmpty()

    val apiKey: String
        get() = currentProvider?.apiKey.orEmpty()

    val selectedModel: String
        get() = currentProvider?.selectedModel.orEmpty()

    val availableModels: List<String>
        get() = currentProvider?.availableModels.orEmpty()

    val selectedProviderName: String
        get() = currentProvider?.name.orEmpty()

    val savedAvailableModels: List<String>
        get() = savedSettings.activeProvider()?.availableModels.orEmpty()

    fun hasDraftChanges(): Boolean {
        val savedProviders = savedSettings.resolvedProviders()
        val resolvedSavedProviderId = savedSettings.selectedProviderId
            .ifBlank { savedProviders.firstOrNull()?.id.orEmpty() }
        return providers != savedProviders ||
            selectedProviderId != resolvedSavedProviderId ||
            themeMode != savedSettings.themeMode ||
            messageTextScale != savedSettings.messageTextScale ||
            reasoningExpandedByDefault != savedSettings.reasoningExpandedByDefault ||
            showThinkingContent != savedSettings.showThinkingContent ||
            autoCollapseThinking != savedSettings.autoCollapseThinking ||
            autoPreviewImages != savedSettings.autoPreviewImages ||
            codeBlockAutoWrap != savedSettings.codeBlockAutoWrap ||
            codeBlockAutoCollapse != savedSettings.codeBlockAutoCollapse ||
            showRoleplayAiHelper != savedSettings.showRoleplayAiHelper ||
            roleplayLongformTargetChars != savedSettings.roleplayLongformTargetChars ||
            showRoleplayPresenceStrip != savedSettings.showRoleplayPresenceStrip ||
            showRoleplayStatusStrip != savedSettings.showRoleplayStatusStrip ||
            screenTranslationSettings != savedSettings.screenTranslationSettings
    }
}

class SettingsViewModel(
    private val repository: AiRepository,
) : ViewModel() {
    val storedSettings: StateFlow<AppSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // 这里直接订阅仓库真实设置流，避免 stateIn 初始空值先生成默认草稿，
            // 后续真实设置到达时又被误判成“已有未保存改动”。
            repository.settingsFlow.collect { settings ->
                val resolvedProviders = ensureProviders(settings.resolvedProviders())
                val resolvedSelectedProviderId = resolveSelectedProviderId(
                    providers = resolvedProviders,
                    selectedProviderId = settings.selectedProviderId,
                )
                _uiState.update { current ->
                    if (current.hasDraftChanges()) {
                        current.copy(savedSettings = settings)
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
                        )
                    }
                }
            }
        }
    }

    fun updateBaseUrl(value: String) {
        updateCurrentProvider { it.copy(baseUrl = value) }
    }

    fun updateApiKey(value: String) {
        updateCurrentProvider { it.copy(apiKey = value) }
    }

    fun updateSelectedModel(value: String) {
        updateCurrentProvider { provider ->
            provider.copy(
                selectedModel = value,
                availableModels = mergeModels(
                    currentModels = provider.availableModels,
                    selectedModel = value,
                ),
            )
        }
    }

    fun updateProviderName(providerId: String, value: String) {
        updateProvider(providerId) { it.copy(name = value) }
    }

    fun updateProviderBaseUrl(providerId: String, value: String) {
        updateProvider(providerId) { it.copy(baseUrl = value) }
    }

    fun updateProviderApiKey(providerId: String, value: String) {
        updateProvider(providerId) { it.copy(apiKey = value) }
    }

    fun updateProviderSelectedModel(
        providerId: String,
        value: String,
    ) {
        updateProvider(providerId) { provider ->
            provider.copy(
                selectedModel = value,
                availableModels = mergeModels(
                    currentModels = provider.availableModels,
                    selectedModel = value,
                ),
            )
        }
    }

    fun updateProviderModelAbilities(
        providerId: String,
        modelId: String,
        abilities: Set<ModelAbility>?,
    ) {
        updateProvider(providerId) { provider ->
            val currentModels = provider.models.orEmpty()
            val existingModel = currentModels.firstOrNull { it.modelId == modelId }
            val updatedModel = (existingModel ?: inferredModelInfo(modelId)).withAbilityOverride(abilities)
            provider.copy(
                availableModels = mergeModels(
                    currentModels = provider.availableModels,
                    selectedModel = modelId,
                ),
                models = if (existingModel != null) {
                    currentModels.map { model ->
                        if (model.modelId == modelId) {
                            updatedModel
                        } else {
                            model
                        }
                    }
                } else {
                    currentModels + updatedModel
                },
            )
        }
    }

    fun selectProvider(providerId: String) {
        _uiState.update { it.copy(selectedProviderId = providerId, message = null) }
    }

    fun addProvider() {
        val newProvider = createDefaultProvider(
            name = "提供商 ${_uiState.value.providers.size + 1}",
        )
        _uiState.update {
            it.copy(
                providers = it.providers + newProvider,
                selectedProviderId = newProvider.id,
                message = null,
            )
        }
    }

    /** 显示模板选择对话框。 */
    fun showAddProviderDialog() {
        _uiState.update { it.copy(showTemplateDialog = true) }
    }

    /** 关闭模板选择对话框。 */
    fun dismissAddProviderDialog() {
        _uiState.update { it.copy(showTemplateDialog = false) }
    }

    /** 根据模板创建新提供商，返回新提供商 ID。 */
    fun addProviderFromTemplate(template: ProviderTemplate): String {
        val newProvider = createDefaultProvider(
            name = template.name,
            baseUrl = template.defaultBaseUrl,
            type = template.type,
        )
        _uiState.update {
            it.copy(
                providers = it.providers + newProvider,
                showTemplateDialog = false,
                message = null,
            )
        }
        return newProvider.id
    }

    /** 检测单个提供商的连接健康状态。 */
    fun checkProviderHealth(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        if (!provider.hasBaseCredentials()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(connectionHealthMap = it.connectionHealthMap + (providerId to ConnectionHealth.CHECKING))
            }
            val health = runCatching {
                repository.fetchModels(baseUrl = provider.baseUrl, apiKey = provider.apiKey)
                ConnectionHealth.HEALTHY
            }.getOrElse {
                ConnectionHealth.UNHEALTHY
            }
            _uiState.update {
                it.copy(connectionHealthMap = it.connectionHealthMap + (providerId to health))
            }
        }
    }

    /** 批量检测所有有凭据的提供商连接状态。 */
    fun checkAllProviderHealth() {
        _uiState.value.providers.forEach { provider ->
            if (provider.hasBaseCredentials()) {
                checkProviderHealth(provider.id)
            }
        }
    }

    /** 切换提供商启用/禁用。 */
    fun toggleProviderEnabled(providerId: String) {
        updateProvider(providerId) { it.copy(enabled = !it.enabled) }
    }

    fun updateProviderTitleSummaryModel(providerId: String, modelId: String) {
        updateProvider(providerId) { it.copy(titleSummaryModel = modelId) }
    }

    fun updateProviderChatSuggestionModel(providerId: String, modelId: String) {
        updateProvider(providerId) { it.copy(chatSuggestionModel = modelId) }
    }

    fun updateProviderMemoryModel(providerId: String, modelId: String) {
        updateProvider(providerId) { it.copy(memoryModel = modelId) }
    }

    fun updateProviderTranslationModel(providerId: String, modelId: String) {
        updateProvider(providerId) { it.copy(translationModel = modelId) }
    }

    fun updateThemeMode(themeMode: ThemeMode) {
        _uiState.update { it.copy(themeMode = themeMode, message = null) }
    }

    fun updateMessageTextScale(messageTextScale: Float) {
        _uiState.update {
            it.copy(
                messageTextScale = messageTextScale.coerceIn(0.85f, 1.25f),
                message = null,
            )
        }
    }

    fun updateReasoningExpandedByDefault(expanded: Boolean) {
        _uiState.update { it.copy(reasoningExpandedByDefault = expanded, message = null) }
    }

    fun updateShowThinkingContent(enabled: Boolean) {
        _uiState.update { it.copy(showThinkingContent = enabled, message = null) }
    }

    fun updateAutoCollapseThinking(enabled: Boolean) {
        _uiState.update { it.copy(autoCollapseThinking = enabled, message = null) }
    }

    fun updateAutoPreviewImages(enabled: Boolean) {
        _uiState.update { it.copy(autoPreviewImages = enabled, message = null) }
    }

    fun updateCodeBlockAutoWrap(enabled: Boolean) {
        _uiState.update { it.copy(codeBlockAutoWrap = enabled, message = null) }
    }

    fun updateCodeBlockAutoCollapse(enabled: Boolean) {
        _uiState.update { it.copy(codeBlockAutoCollapse = enabled, message = null) }
    }

    fun updateShowRoleplayAiHelper(enabled: Boolean) {
        _uiState.update { it.copy(showRoleplayAiHelper = enabled, message = null) }
    }

    fun updateRoleplayLongformTargetChars(value: Int) {
        _uiState.update {
            it.copy(
                roleplayLongformTargetChars = value.coerceIn(300, 2000),
                message = null,
            )
        }
    }

    fun updateShowRoleplayPresenceStrip(enabled: Boolean) {
        _uiState.update { it.copy(showRoleplayPresenceStrip = enabled, message = null) }
    }

    fun updateShowRoleplayStatusStrip(enabled: Boolean) {
        _uiState.update { it.copy(showRoleplayStatusStrip = enabled, message = null) }
    }

    fun updateScreenTranslationServiceEnabled(enabled: Boolean) {
        updateScreenTranslationSettings { it.copy(serviceEnabled = enabled) }
    }

    fun updateScreenTranslationOverlayEnabled(enabled: Boolean) {
        updateScreenTranslationSettings { it.copy(overlayEnabled = enabled) }
    }

    fun updateScreenTranslationTargetLanguage(language: String) {
        updateScreenTranslationSettings { it.copy(targetLanguage = language) }
    }

    fun updateScreenTranslationSelectedTextEnabled(enabled: Boolean) {
        updateScreenTranslationSettings { it.copy(selectedTextEnabled = enabled) }
    }

    fun updateScreenTranslationShowSourceText(enabled: Boolean) {
        updateScreenTranslationSettings { it.copy(showSourceText = enabled) }
    }

    fun updateScreenTranslationVendorGuideDismissed(dismissed: Boolean) {
        updateScreenTranslationSettings { it.copy(vendorGuideDismissed = dismissed) }
    }

    fun updateScreenTranslationOverlayOffset(
        x: Float,
        y: Float,
    ) {
        updateScreenTranslationSettings {
            it.copy(
                overlayOffsetX = x.coerceIn(0f, 1f),
                overlayOffsetY = y.coerceIn(0f, 1f),
            )
        }
    }

    fun deleteProvider(providerId: String) {
        _uiState.update { current ->
            val remainingProviders = current.providers.filterNot { it.id == providerId }
            val resolvedProviders = ensureProviders(remainingProviders)
            val resolvedSelectedProviderId = when {
                current.selectedProviderId == providerId -> resolvedProviders.first().id
                else -> resolveSelectedProviderId(
                    providers = resolvedProviders,
                    selectedProviderId = current.selectedProviderId,
                )
            }
            current.copy(
                providers = resolvedProviders,
                selectedProviderId = resolvedSelectedProviderId,
                message = null,
            )
        }
    }

    fun loadModels() {
        val provider = _uiState.value.currentProvider ?: return
        loadModels(provider.id)
    }

    fun loadModels(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        loadModelsForProvider(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
        )
    }

    fun loadSavedModels() {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val resolvedSelectedProviderId = resolveSelectedProviderId(
            providers = savedProviders,
            selectedProviderId = currentState.savedSettings.selectedProviderId,
        )
        val provider = savedProviders.firstOrNull { it.id == resolvedSelectedProviderId }
            ?: currentState.savedSettings.activeProvider()
            ?: currentState.currentProvider
            ?: return
        loadModelsForProvider(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
            persistResult = true,
            persistedProviders = savedProviders,
            persistedSelectedProviderId = resolvedSelectedProviderId,
        )
    }

    fun loadSavedModelsForProvider(providerId: String) {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val provider = savedProviders.firstOrNull { it.id == providerId } ?: return
        loadModelsForProvider(
            providerId = provider.id,
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            selectedModel = provider.selectedModel,
            persistResult = true,
            persistedProviders = savedProviders,
            persistedSelectedProviderId = provider.id,
        )
    }

    fun saveSettings(onSaved: () -> Unit) {
        val currentState = _uiState.value
        val normalizedProviders = normalizeProviders(currentState.providers)
        val resolvedSelectedProviderId = resolveSelectedProviderId(
            providers = normalizedProviders,
            selectedProviderId = currentState.selectedProviderId,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            runCatching {
                repository.saveProviderSettings(
                    providers = normalizedProviders,
                    selectedProviderId = resolvedSelectedProviderId,
                )
                repository.saveDisplaySettings(
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
                )
                repository.saveScreenTranslationSettings(
                    currentState.screenTranslationSettings,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        providers = normalizedProviders,
                        selectedProviderId = resolvedSelectedProviderId,
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
                        screenTranslationSettings = currentState.screenTranslationSettings,
                        isSaving = false,
                        message = "设置已保存",
                    )
                }
                onSaved()
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = throwable.message ?: "设置保存失败",
                    )
                }
            }
        }
    }

    fun saveSelectedModel(selectedModel: String) {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val resolvedSelectedProviderId = resolveSelectedProviderId(
            providers = savedProviders,
            selectedProviderId = currentState.savedSettings.selectedProviderId,
        )
        val updatedProviders = savedProviders.map { provider ->
            if (provider.id == resolvedSelectedProviderId) {
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            } else {
                provider
            }
        }

        viewModelScope.launch {
            runCatching {
                repository.saveProviderSettings(
                    providers = updatedProviders,
                    selectedProviderId = resolvedSelectedProviderId,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        providers = if (current.providers.any { it.id == resolvedSelectedProviderId }) {
                            current.providers.map { provider ->
                                if (provider.id == resolvedSelectedProviderId) {
                                    provider.copy(
                                        selectedModel = selectedModel,
                                        availableModels = mergeModels(
                                            currentModels = provider.availableModels,
                                            selectedModel = selectedModel,
                                        ),
                                    )
                                } else {
                                    provider
                                }
                            }
                        } else {
                            current.providers
                        },
                        message = "模型已切换为 $selectedModel",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "模型切换失败")
                }
            }
        }
    }

    fun saveSelectedProvider(providerId: String) {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val targetProvider = savedProviders.firstOrNull { it.id == providerId } ?: return
        if (!targetProvider.enabled) {
            _uiState.update {
                it.copy(message = "该提供商已停用，请先启用后再切换")
            }
            return
        }

        viewModelScope.launch {
            runCatching {
                repository.saveProviderSettings(
                    providers = savedProviders,
                    selectedProviderId = targetProvider.id,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        message = "已切换到 ${targetProvider.name.ifBlank { "该提供商" }}",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "提供商切换失败")
                }
            }
        }
    }

    fun saveSelectedModelForProvider(
        providerId: String,
        selectedModel: String,
    ) {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val targetProvider = savedProviders.firstOrNull { it.id == providerId } ?: return
        val updatedProviders = savedProviders.map { provider ->
            if (provider.id == providerId) {
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            } else {
                provider
            }
        }

        viewModelScope.launch {
            runCatching {
                repository.saveProviderSettings(
                    providers = updatedProviders,
                    selectedProviderId = targetProvider.id,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        message = "模型已切换为 $selectedModel",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "模型切换失败")
                }
            }
        }
    }

    fun saveThinkingBudgetForProvider(
        providerId: String,
        thinkingBudget: Int?,
    ) {
        val currentState = _uiState.value
        val savedProviders = ensureProviders(currentState.savedSettings.resolvedProviders())
        val targetProvider = savedProviders.firstOrNull { it.id == providerId } ?: return
        val updatedProviders = savedProviders.map { provider ->
            if (provider.id == providerId) {
                provider.copy(thinkingBudget = thinkingBudget)
            } else {
                provider
            }
        }

        viewModelScope.launch {
            runCatching {
                repository.saveProviderSettings(
                    providers = updatedProviders,
                    selectedProviderId = targetProvider.id,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(
                        message = "思考预算已更新",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "思考预算保存失败")
                }
            }
        }
    }

    fun saveUserProfile(
        displayName: String,
        avatarUri: String,
        avatarUrl: String,
    ) {
        viewModelScope.launch {
            runCatching {
                repository.saveUserProfile(
                    displayName = displayName,
                    avatarUri = avatarUri,
                    avatarUrl = avatarUrl,
                )
            }.onSuccess {
                _uiState.update { current ->
                    current.copy(message = "个人资料已更新")
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(message = throwable.message ?: "个人资料保存失败")
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun confirmFetchedModels(providerId: String, selectedModelIds: Set<String>) {
        val currentState = _uiState.value
        val fetchedModels = currentState.pendingFetchedModels

        _uiState.update { current ->
            current.copy(
                providers = current.providers.map { provider ->
                    if (provider.id == providerId) {
                        val existingModels = provider.models.orEmpty()
                        val existingById = existingModels.associateBy { it.modelId }
                        val fetchedById = fetchedModels.associateBy { it.modelId }

                        // 最终模型列表 = selectedIds 中的模型，保留已有的 ability overrides
                        val finalModels = selectedModelIds.mapNotNull { modelId ->
                            val existing = existingById[modelId]
                            val fetched = fetchedById[modelId]
                            when {
                                // 已有模型且被选中 → 保留（包含用户自定义的 ability overrides）
                                existing != null -> existing
                                // 新获取的模型被选中 → 添加
                                fetched != null -> fetched
                                // 既不在已有也不在获取列表中（理论上不会发生）→ 跳过
                                else -> null
                            }
                        }

                        val finalIds = finalModels.map { it.modelId }
                        val resolvedSelected = when {
                            provider.selectedModel in finalIds -> provider.selectedModel
                            finalIds.isNotEmpty() -> finalIds.first()
                            else -> ""
                        }

                        val addedCount = finalIds.count { it !in existingById }
                        val removedCount = existingById.keys.count { it !in selectedModelIds }

                        provider.copy(
                            availableModels = finalIds,
                            models = finalModels.ifEmpty { null },
                            selectedModel = resolvedSelected,
                        )
                    } else {
                        provider
                    }
                },
                pendingFetchedModels = emptyList(),
                pendingFetchProviderId = "",
                message = buildString {
                    val existingIds = current.providers
                        .firstOrNull { it.id == providerId }
                        ?.models.orEmpty()
                        .map { it.modelId }.toSet()
                    val added = selectedModelIds.count { it !in existingIds }
                    val removed = existingIds.count { it !in selectedModelIds }
                    append("模型已更新")
                    if (added > 0) append("，新增 $added")
                    if (removed > 0) append("，移除 $removed")
                },
            )
        }
    }

    fun dismissFetchedModels() {
        _uiState.update {
            it.copy(
                pendingFetchedModels = emptyList(),
                pendingFetchProviderId = "",
            )
        }
    }

    fun removeModelFromProvider(providerId: String, modelId: String) {
        _uiState.update { current ->
            current.copy(
                providers = current.providers.map { provider ->
                    if (provider.id == providerId) {
                        val updatedModels = provider.models.orEmpty().filter { it.modelId != modelId }
                        val updatedAvailable = provider.availableModels.filter { it != modelId }
                        val resolvedSelected = when {
                            provider.selectedModel != modelId -> provider.selectedModel
                            updatedModels.isNotEmpty() -> updatedModels.first().modelId
                            else -> ""
                        }
                        provider.copy(
                            availableModels = updatedAvailable,
                            models = updatedModels.ifEmpty { null },
                            selectedModel = resolvedSelected,
                        )
                    } else {
                        provider
                    }
                },
                message = null,
            )
        }
    }

    fun addAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = storedSettings.value
            val currentAssistants = settings.assistants.toMutableList()
            currentAssistants.add(assistant)
            repository.saveAssistants(currentAssistants, settings.selectedAssistantId)
        }
    }

    fun updateAssistant(assistant: Assistant) {
        viewModelScope.launch {
            val settings = storedSettings.value
            val currentAssistants = settings.assistants.toMutableList()
            val index = currentAssistants.indexOfFirst { it.id == assistant.id }
            if (index >= 0) {
                currentAssistants[index] = assistant
            } else {
                currentAssistants.add(assistant)
            }
            repository.saveAssistants(currentAssistants, settings.selectedAssistantId)
        }
    }

    fun removeAssistant(assistantId: String) {
        val builtinIds = BUILTIN_ASSISTANTS.map { it.id }.toSet()
        if (assistantId in builtinIds) return
        viewModelScope.launch {
            val settings = storedSettings.value
            val updatedAssistants = settings.assistants.filter { it.id != assistantId }
            val selectedId = if (settings.selectedAssistantId == assistantId) {
                DEFAULT_ASSISTANT_ID
            } else {
                settings.selectedAssistantId
            }
            repository.saveAssistants(updatedAssistants, selectedId)
        }
    }

    fun duplicateAssistant(assistantId: String) {
        val settings = storedSettings.value
        val source = settings.resolvedAssistants().firstOrNull { it.id == assistantId } ?: return
        val copy = source.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = "${source.name} (副本)",
            isBuiltin = false,
        )
        addAssistant(copy)
    }

    fun selectAssistant(assistantId: String) {
        viewModelScope.launch {
            val settings = storedSettings.value
            repository.saveAssistants(settings.assistants, assistantId)
        }
    }

    private fun loadModelsForProvider(
        providerId: String,
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        persistResult: Boolean = false,
        persistedProviders: List<ProviderSettings> = emptyList(),
        persistedSelectedProviderId: String = "",
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoadingModels = true,
                    loadingProviderId = providerId,
                    message = null,
                )
            }
            runCatching {
                repository.fetchModelInfos(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                )
            }.onSuccess { modelInfos ->
                if (persistResult) {
                    val mergedModelInfos = mergeModelInfosPreservingOverrides(
                        fetchedModels = modelInfos,
                        previousModels = persistedProviders
                            .firstOrNull { it.id == providerId }
                            ?.models
                            .orEmpty(),
                    )
                    val models = mergedModelInfos.map { it.modelId }
                    val resolvedSelectedModel = when {
                        selectedModel in models -> selectedModel
                        models.isNotEmpty() -> models.first()
                        else -> ""
                    }
                    val providersToPersist = persistedProviders.map { provider ->
                        if (provider.id == providerId) {
                            provider.copy(
                                availableModels = models,
                                models = mergedModelInfos,
                                selectedModel = resolvedSelectedModel,
                            )
                        } else {
                            provider
                        }
                    }
                    repository.saveProviderSettings(
                        providers = providersToPersist,
                        selectedProviderId = persistedSelectedProviderId.ifBlank { providerId },
                    )
                    _uiState.update { current ->
                        current.copy(
                            isLoadingModels = false,
                            loadingProviderId = "",
                            message = "模型已同步",
                        )
                    }
                } else {
                    _uiState.update { current ->
                        current.copy(
                            isLoadingModels = false,
                            loadingProviderId = "",
                            pendingFetchedModels = modelInfos,
                            pendingFetchProviderId = providerId,
                            message = "已获取 ${modelInfos.size} 个模型，请选择要添加的",
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoadingModels = false,
                        loadingProviderId = "",
                        message = throwable.message ?: "模型拉取失败",
                    )
                }
            }
        }
    }

    private fun updateCurrentProvider(transform: (ProviderSettings) -> ProviderSettings) {
        val providerId = _uiState.value.currentProvider?.id ?: return
        updateProvider(providerId, transform)
    }

    private fun updateProvider(
        providerId: String,
        transform: (ProviderSettings) -> ProviderSettings,
    ) {
        _uiState.update { current ->
            current.copy(
                providers = current.providers.map { provider ->
                    if (provider.id == providerId) transform(provider) else provider
                },
                message = null,
            )
        }
    }

    private fun updateScreenTranslationSettings(
        transform: (ScreenTranslationSettings) -> ScreenTranslationSettings,
    ) {
        _uiState.update { current ->
            current.copy(
                screenTranslationSettings = transform(current.screenTranslationSettings),
                message = null,
            )
        }
    }

    private fun normalizeProviders(providers: List<ProviderSettings>): List<ProviderSettings> {
        return ensureProviders(providers).mapIndexed { index, provider ->
            provider.copy(
                name = provider.name.trim().ifBlank { "提供商 ${index + 1}" },
                baseUrl = provider.baseUrl.trim(),
                apiKey = provider.apiKey.trim(),
                selectedModel = provider.selectedModel.trim(),
            )
        }
    }

    private fun ensureProviders(providers: List<ProviderSettings>): List<ProviderSettings> {
        return if (providers.isEmpty()) {
            listOf(createDefaultProvider())
        } else {
            providers
        }
    }

    private fun resolveSelectedProviderId(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ): String {
        return providers.firstOrNull { it.id == selectedProviderId }?.id
            ?: providers.firstOrNull()?.id
            ?: ""
    }

    private fun mergeModels(
        currentModels: List<String>,
        selectedModel: String,
    ): List<String> {
        return buildList {
            addAll(currentModels)
            if (selectedModel.isNotBlank() && selectedModel !in currentModels) {
                add(selectedModel)
            }
        }
    }

    companion object {
        fun factory(repository: AiRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SettingsViewModel(repository) as T
                }
            }
        }
    }
}
