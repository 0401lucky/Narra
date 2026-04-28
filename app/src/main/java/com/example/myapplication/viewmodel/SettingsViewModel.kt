package com.example.myapplication.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.repository.ai.AiModelCatalogRepository
import com.example.myapplication.data.repository.ai.AiSettingsEditor
import com.example.myapplication.data.repository.ai.AiSettingsRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ConnectionHealth
import com.example.myapplication.model.DEFAULT_ROLEPLAY_LONGFORM_TARGET_CHARS
import com.example.myapplication.model.FunctionModelProviderIds
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderTemplate
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.ScreenTranslationSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.ThemeMode
import com.example.myapplication.model.UserPersonaMask
import com.example.myapplication.model.normalized
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
    val functionModelProviderIds: FunctionModelProviderIds = FunctionModelProviderIds(),
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
    val showOnlineRoleplayNarration: Boolean = true,
    val enableRoleplayNetMeme: Boolean = false,
    val roleplayImmersiveMode: com.example.myapplication.model.RoleplayImmersiveMode = com.example.myapplication.model.RoleplayImmersiveMode.EDGE_TO_EDGE,
    val roleplayHighContrast: Boolean = false,
    val roleplayLineHeightScale: com.example.myapplication.model.RoleplayLineHeightScale = com.example.myapplication.model.RoleplayLineHeightScale.NORMAL,
    val screenTranslationSettings: ScreenTranslationSettings = ScreenTranslationSettings(),
    val searchSettings: SearchSettings = SearchSettings(),
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
            functionModelProviderIds != savedSettings.functionModelProviderIds.normalized(savedProviders.map(ProviderSettings::id).toSet()) ||
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
            showOnlineRoleplayNarration != savedSettings.showOnlineRoleplayNarration ||
            enableRoleplayNetMeme != savedSettings.enableRoleplayNetMeme ||
            roleplayImmersiveMode != savedSettings.roleplayImmersiveMode ||
            roleplayHighContrast != savedSettings.roleplayHighContrast ||
            roleplayLineHeightScale != savedSettings.roleplayLineHeightScale ||
            screenTranslationSettings != savedSettings.screenTranslationSettings ||
            searchSettings != savedSettings.resolvedSearchSettings()
    }
}

class SettingsViewModel(
    private val settingsRepository: AiSettingsRepository,
    private val settingsEditor: AiSettingsEditor,
    private val modelCatalogRepository: AiModelCatalogRepository,
    private val imageFileCleaner: suspend (String?) -> Boolean = { false },
) : ViewModel() {
    val storedSettings: StateFlow<AppSettings> = settingsRepository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = AppSettings(),
    )

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private val persistenceCoordinator = SettingsPersistenceCoordinator(settingsEditor)
    private val modelLoadCoordinator = SettingsModelLoadCoordinator(
        modelCatalogRepository = modelCatalogRepository,
        settingsEditor = settingsEditor,
    )
    private val assistantCoordinator = SettingsAssistantCoordinator(settingsEditor, imageFileCleaner)
    private val healthCoordinator = SettingsHealthCoordinator(modelCatalogRepository)

    init {
        viewModelScope.launch {
            // 这里直接订阅仓库真实设置流，避免 stateIn 初始空值先生成默认草稿，
            // 后续真实设置到达时又被误判成“已有未保存改动”。
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update { current ->
                    SettingsDraftStateSupport.syncStoredSettings(
                        current = current,
                        settings = settings,
                    )
                }
            }
        }
    }

    fun updateBaseUrl(value: String) = updateCurrentProviderAndClearHealth { it.copy(baseUrl = value) }

    fun updateApiKey(value: String) = updateCurrentProviderAndClearHealth { it.copy(apiKey = value) }

    fun updateSelectedModel(value: String) = updateCurrentProvider { provider ->
        provider.copy(
            selectedModel = value,
            availableModels = SettingsProviderDraftSupport.mergeModels(
                currentModels = provider.availableModels,
                selectedModel = value,
            ),
        )
    }

    fun updateProviderName(providerId: String, value: String) =
        updateProvider(providerId) { it.copy(name = value) }

    fun updateProviderBaseUrl(providerId: String, value: String) =
        updateProviderAndClearHealth(providerId) { it.copy(baseUrl = value) }

    fun updateProviderApiKey(providerId: String, value: String) =
        updateProviderAndClearHealth(providerId) { it.copy(apiKey = value) }

    fun updateProviderApiProtocol(
        providerId: String,
        apiProtocol: ProviderApiProtocol,
    ) = updateProviderAndClearHealth(providerId) { provider ->
        val normalizedBaseUrl = when (apiProtocol) {
            ProviderApiProtocol.OPENAI_COMPATIBLE -> when (provider.resolvedType()) {
                ProviderType.ANTHROPIC -> ""
                else -> provider.baseUrl
            }
            ProviderApiProtocol.ANTHROPIC -> provider.baseUrl.ifBlank { ProviderType.ANTHROPIC.defaultBaseUrl }
        }
        provider.copy(
            apiProtocol = apiProtocol,
            baseUrl = normalizedBaseUrl,
            openAiTextApiMode = if (apiProtocol == ProviderApiProtocol.OPENAI_COMPATIBLE) {
                provider.resolvedOpenAiTextApiMode()
            } else {
                provider.openAiTextApiMode
            },
        )
    }

    fun updateProviderOpenAiTextApiMode(
        providerId: String,
        textApiMode: com.example.myapplication.model.OpenAiTextApiMode,
    ) = updateProvider(providerId) { it.copy(openAiTextApiMode = textApiMode) }

    fun updateProviderChatCompletionsPath(providerId: String, path: String) =
        updateProvider(providerId) { it.copy(chatCompletionsPath = path) }

    fun updateProviderSelectedModel(providerId: String, value: String) =
        updateProvider(providerId) { provider ->
            provider.copy(
                selectedModel = value,
                availableModels = SettingsProviderDraftSupport.mergeModels(
                    currentModels = provider.availableModels,
                    selectedModel = value,
                ),
            )
        }

    fun updateProviderModelAbilities(
        providerId: String,
        modelId: String,
        abilities: Set<ModelAbility>?,
    ) = updateProvider(providerId) { provider ->
        SettingsProviderDraftSupport.updateProviderModelAbilities(
            provider = provider,
            modelId = modelId,
            abilities = abilities,
        )
    }

    fun selectProvider(providerId: String) = updateUiState { current ->
        SettingsDraftStateSupport.selectProvider(current = current, providerId = providerId)
    }

    fun addProvider() {
        val updatedState = SettingsDraftStateSupport.addProvider(_uiState.value)
        _uiState.value = updatedState
        persistProviderDrafts(
            providers = updatedState.providers,
            selectedProviderId = updatedState.selectedProviderId,
        )
    }

    fun ensureProviderDrafts() {
        val updatedState = SettingsDraftStateSupport.ensureProviderDrafts(_uiState.value)
        if (updatedState == _uiState.value) return
        _uiState.value = updatedState
        persistProviderDrafts(
            providers = updatedState.providers,
            selectedProviderId = updatedState.selectedProviderId,
        )
    }

    /** 显示模板选择对话框。 */
    fun showAddProviderDialog() = updateUiState { SettingsDraftStateSupport.showAddProviderDialog(it) }

    /** 关闭模板选择对话框。 */
    fun dismissAddProviderDialog() = updateUiState { SettingsDraftStateSupport.dismissAddProviderDialog(it) }

    /** 根据模板创建新提供商，返回新提供商 ID。 */
    fun addProviderFromTemplate(template: ProviderTemplate): String {
        val result = SettingsDraftStateSupport.addProviderFromTemplate(
            current = _uiState.value,
            template = template,
        )
        _uiState.value = result.state
        persistProviderDrafts(
            providers = result.state.providers,
            selectedProviderId = result.state.selectedProviderId,
        )
        return result.newProviderId
    }

    /** 检测单个提供商的连接健康状态。 */
    fun checkProviderHealth(providerId: String) {
        val provider = _uiState.value.providers.firstOrNull { it.id == providerId } ?: return
        if (!provider.hasBaseCredentials()) return
        viewModelScope.launch {
            updateUiState { SettingsHealthStateSupport.markChecking(it, providerId) }
            val result = healthCoordinator.checkProviderHealth(provider)
            updateUiState { current ->
                val updated = SettingsHealthStateSupport.markResult(current, providerId, result.health)
                if (result.message.isNullOrBlank()) updated
                else SettingsUiMutationSupport.applyMessageError(updated, result.message)
            }
        }
    }

    /** 批量检测所有有凭据的提供商连接状态。 */
    fun checkAllProviderHealth() {
        _uiState.value.providers.forEach { provider ->
            if (provider.hasBaseCredentials()) checkProviderHealth(provider.id)
        }
    }

    /** 切换提供商启用/禁用。 */
    fun toggleProviderEnabled(providerId: String) = updateUiState { current ->
        SettingsUiMutationSupport.toggleProviderEnabled(current, providerId)
    }

    fun deleteProvider(providerId: String) = updateUiState { current ->
        SettingsUiMutationSupport.deleteProvider(current, providerId)
    }

    fun loadModels() {
        val request = SettingsLoadRequestSupport.resolveCurrentProviderRequest(_uiState.value) ?: return
        loadModelsForProvider(request)
    }

    fun loadModels(providerId: String) {
        val request = SettingsLoadRequestSupport.resolveCurrentProviderRequest(
            state = _uiState.value,
            providerId = providerId,
        ) ?: return
        loadModelsForProvider(request)
    }

    fun loadSavedModels() {
        val request = SettingsLoadRequestSupport.resolveSavedProviderRequest(_uiState.value) ?: return
        loadModelsForProvider(request)
    }

    fun loadSavedModelsForProvider(providerId: String) {
        val request = SettingsLoadRequestSupport.resolveSavedProviderRequest(
            state = _uiState.value,
            providerId = providerId,
        ) ?: return
        loadModelsForProvider(request)
    }

    fun saveSettings(onSaved: () -> Unit) {
        viewModelScope.launch {
            val currentState = _uiState.value
            updateUiState { SettingsUiMutationSupport.beginSaving(it) }
            runCatching {
                persistenceCoordinator.saveSettings(currentState = currentState)
            }.onSuccess { result ->
                updateUiState { current ->
                    SettingsUiMutationSupport.applySaveSuccess(
                        current = current,
                        result = result,
                        sourceState = currentState,
                    )
                }
                onSaved()
            }.onFailure { throwable ->
                updateUiState { current ->
                    SettingsUiMutationSupport.applySaveFailure(
                        current = current,
                        errorMessage = throwable.message ?: "设置保存失败",
                    )
                }
            }
        }
    }

    fun saveSelectedModel(selectedModel: String) {
        val currentState = _uiState.value
        updateUiState { current ->
            SettingsDraftStateSupport.updateCurrentProvider(current) { provider ->
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = SettingsProviderDraftSupport.mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            }
        }
        launchPersistenceMutation(
            defaultErrorMessage = "模型切换失败",
            action = {
                persistenceCoordinator.saveSelectedModel(
                    savedSettings = currentState.savedSettings,
                    currentProviders = _uiState.value.providers,
                    currentSelectedProviderId = _uiState.value.selectedProviderId,
                    selectedModel = selectedModel,
                )
            },
        )
    }

    fun saveSelectedProvider(providerId: String) {
        val currentState = _uiState.value
        val candidateProviders = SettingsProviderDraftSupport.ensureProviders(
            currentState.providers.ifEmpty { currentState.savedSettings.resolvedProviders() },
        )
        val targetProvider = candidateProviders.firstOrNull { it.id == providerId } ?: return
        if (!targetProvider.enabled) {
            updateUiState { SettingsUiMutationSupport.applyMessageError(it, "该提供商已停用，请先启用后再切换") }
            return
        }

        updateUiState { SettingsDraftStateSupport.selectProvider(it, targetProvider.id) }

        launchPersistenceMutation(
            defaultErrorMessage = "提供商切换失败",
            action = {
                persistenceCoordinator.saveSelectedProvider(
                    savedSettings = currentState.savedSettings,
                    draftProviders = _uiState.value.providers,
                    providerId = targetProvider.id,
                )
            },
        )
    }

    fun saveSelectedModelForProvider(providerId: String, selectedModel: String) {
        val currentState = _uiState.value
        updateUiState { current ->
            val updated = SettingsDraftStateSupport.updateProvider(
                current = current,
                providerId = providerId,
            ) { provider ->
                provider.copy(
                    selectedModel = selectedModel,
                    availableModels = SettingsProviderDraftSupport.mergeModels(
                        currentModels = provider.availableModels,
                        selectedModel = selectedModel,
                    ),
                )
            }
            SettingsDraftStateSupport.selectProvider(updated, providerId)
        }
        launchPersistenceMutation(
            defaultErrorMessage = "模型切换失败",
            action = {
                persistenceCoordinator.saveSelectedModelForProvider(
                    savedSettings = currentState.savedSettings,
                    draftProviders = _uiState.value.providers,
                    providerId = providerId,
                    selectedModel = selectedModel,
                )
            },
        )
    }

    fun saveThinkingBudgetForProvider(providerId: String, thinkingBudget: Int?) {
        val currentState = _uiState.value
        updateUiState { current ->
            SettingsDraftStateSupport.updateProvider(
                current = current,
                providerId = providerId,
            ) { it.copy(thinkingBudget = thinkingBudget) }
        }
        launchPersistenceMutation(
            defaultErrorMessage = "思考预算保存失败",
            action = {
                persistenceCoordinator.saveThinkingBudgetForProvider(
                    savedSettings = currentState.savedSettings,
                    draftProviders = _uiState.value.providers,
                    providerId = providerId,
                    thinkingBudget = thinkingBudget,
                )
            },
        )
    }

    fun saveUserProfile(
        displayName: String,
        personaPrompt: String,
        avatarUri: String,
        avatarUrl: String,
    ) = launchPersistenceMutation(
        defaultErrorMessage = "个人资料保存失败",
        action = {
            persistenceCoordinator.saveUserProfile(
                displayName = displayName,
                personaPrompt = personaPrompt,
                avatarUri = avatarUri,
                avatarUrl = avatarUrl,
            )
        },
    )

    fun upsertUserPersonaMask(mask: UserPersonaMask) = launchPersistenceMutation(
        defaultErrorMessage = "面具保存失败",
        action = {
            val settings = storedSettings.value
            val now = System.currentTimeMillis()
            val normalizedMask = mask.normalized(now).copy(updatedAt = now)
            val currentMasks = settings.normalizedUserPersonaMasks()
            val updatedMasks = if (currentMasks.any { it.id == normalizedMask.id }) {
                currentMasks.map { current ->
                    if (current.id == normalizedMask.id) {
                        normalizedMask.copy(
                            createdAt = current.createdAt.takeIf { it > 0L } ?: normalizedMask.createdAt,
                        )
                    } else {
                        current
                    }
                }
            } else {
                currentMasks + normalizedMask.copy(createdAt = now, updatedAt = now)
            }
            persistenceCoordinator.saveUserPersonaMasks(
                masks = updatedMasks,
                defaultMaskId = settings.defaultUserPersonaMaskId
                    .takeIf { id -> updatedMasks.any { it.id == id } }
                    ?: settings.resolvedDefaultUserPersonaMask()
                        ?.id
                        ?.takeIf { id -> updatedMasks.any { it.id == id } }
                    ?: normalizedMask.id,
            )
        },
    )

    fun deleteUserPersonaMask(maskId: String) = launchPersistenceMutation(
        defaultErrorMessage = "面具删除失败",
        action = {
            val settings = storedSettings.value
            val updatedMasks = settings.normalizedUserPersonaMasks()
                .filterNot { it.id == maskId }
            val nextDefaultId = settings.defaultUserPersonaMaskId
                .takeIf { id -> updatedMasks.any { it.id == id } }
                ?: updatedMasks.firstOrNull()?.id.orEmpty()
            persistenceCoordinator.saveUserPersonaMasks(
                masks = updatedMasks,
                defaultMaskId = nextDefaultId,
            )
        },
    )

    fun setDefaultUserPersonaMask(maskId: String) = launchPersistenceMutation(
        defaultErrorMessage = "默认面具设置失败",
        action = {
            val settings = storedSettings.value
            persistenceCoordinator.saveUserPersonaMasks(
                masks = settings.normalizedUserPersonaMasks(),
                defaultMaskId = maskId,
            )
        },
    )

    fun consumeMessage() = updateUiState { SettingsUiMutationSupport.consumeMessage(it) }

    fun confirmFetchedModels(providerId: String, selectedModelIds: Set<String>) = updateUiState { current ->
        SettingsUiMutationSupport.confirmFetchedModels(
            current = current,
            providerId = providerId,
            selectedModelIds = selectedModelIds,
        )
    }

    fun dismissFetchedModels() = updateUiState { SettingsUiMutationSupport.dismissFetchedModels(it) }

    fun removeModelFromProvider(providerId: String, modelId: String) = updateUiState { current ->
        SettingsUiMutationSupport.removeModelFromProvider(
            current = current,
            providerId = providerId,
            modelId = modelId,
        )
    }

    // ── T7.1：5 个助手 op 统一走 launchAssistantOp helper。 ──
    fun addAssistant(assistant: Assistant) =
        launchAssistantOp { addAssistant(it, assistant) }

    fun updateAssistant(assistant: Assistant) =
        launchAssistantOp { updateAssistant(it, assistant) }

    fun removeAssistant(assistantId: String) =
        launchAssistantOp { removeAssistant(it, assistantId) }

    fun duplicateAssistant(assistantId: String) =
        launchAssistantOp { duplicateAssistant(it, assistantId) }

    fun selectAssistant(assistantId: String) =
        launchAssistantOp { selectAssistant(it, assistantId) }

    // ── 记忆与上下文日志：直接落库（子页面无"保存"按钮，操作即时反馈） ──

    fun updateMemoryAutoSummaryEvery(value: Int) {
        val capacity = storedSettings.value.memoryCapacity
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveMemorySettings(
                    autoSummaryEvery = value,
                    capacity = capacity,
                )
            }
        }
    }

    fun updateMemoryCapacity(value: Int) {
        val autoEvery = storedSettings.value.memoryAutoSummaryEvery
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveMemorySettings(
                    autoSummaryEvery = autoEvery,
                    capacity = value,
                )
            }
        }
    }

    fun updateMemoryExtractionPrompt(value: String) {
        val injection = storedSettings.value.memoryInjectionPrompt
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveMemoryPromptSettings(
                    extractionPrompt = value,
                    injectionPrompt = injection,
                )
            }
        }
    }

    fun updateMemoryInjectionPrompt(value: String) {
        val extraction = storedSettings.value.memoryExtractionPrompt
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveMemoryPromptSettings(
                    extractionPrompt = extraction,
                    injectionPrompt = value,
                )
            }
        }
    }

    fun updateMemoryInjectionPosition(position: com.example.myapplication.model.MemoryInjectionPosition) {
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveMemoryInjectionPosition(position)
            }
        }
    }

    fun updateContextLogEnabled(value: Boolean) {
        val capacity = storedSettings.value.contextLogCapacity
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveContextLogSettings(
                    enabled = value,
                    capacity = capacity,
                )
            }
        }
    }

    fun updateContextLogCapacity(value: Int) {
        val enabled = storedSettings.value.contextLogEnabled
        viewModelScope.launch {
            runCatching {
                settingsEditor.saveContextLogSettings(
                    enabled = enabled,
                    capacity = value,
                )
            }
        }
    }

    // ── 内部 helper（部分暴露为 internal，供同包 setter 扩展文件访问）。 ──

    /** 供同包扩展函数更新 [SettingsUiState]。 */
    internal fun updateUiState(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update(transform)
    }

    /** 供同包扩展函数更新单个提供商草稿。 */
    internal fun updateProvider(
        providerId: String,
        transform: (ProviderSettings) -> ProviderSettings,
    ) = updateUiState { current ->
        SettingsDraftStateSupport.updateProvider(
            current = current,
            providerId = providerId,
            transform = transform,
        )
    }

    internal fun updateFunctionModelProviderIds(
        transform: (FunctionModelProviderIds) -> FunctionModelProviderIds,
    ) = updateUiState { current ->
        current.copy(
            functionModelProviderIds = transform(current.functionModelProviderIds)
                .normalized(current.providers.map(ProviderSettings::id).toSet()),
            message = null,
        )
    }

    /** 供同包 SettingsScreenTranslationSetters 扩展访问。 */
    internal fun updateScreenTranslationDraft(
        transform: (ScreenTranslationSettings) -> ScreenTranslationSettings,
    ) = updateUiState { current ->
        SettingsPreferenceDraftSupport.updateScreenTranslationSettings(
            current = current,
            transform = transform,
        )
    }

    private fun updateCurrentProvider(transform: (ProviderSettings) -> ProviderSettings) =
        updateUiState { current ->
            SettingsDraftStateSupport.updateCurrentProvider(
                current = current,
                transform = transform,
            )
        }

    private fun updateCurrentProviderAndClearHealth(transform: (ProviderSettings) -> ProviderSettings) =
        updateUiState { current ->
            val providerId = current.currentProvider?.id ?: return@updateUiState current
            val updated = SettingsDraftStateSupport.updateCurrentProvider(
                current = current,
                transform = transform,
            )
            SettingsHealthStateSupport.clearProviderHealth(updated, providerId)
        }

    private fun updateProviderAndClearHealth(
        providerId: String,
        transform: (ProviderSettings) -> ProviderSettings,
    ) = updateUiState { current ->
        val updated = SettingsDraftStateSupport.updateProvider(
            current = current,
            providerId = providerId,
            transform = transform,
        )
        SettingsHealthStateSupport.clearProviderHealth(updated, providerId)
    }

    private fun launchAssistantOp(
        block: suspend SettingsAssistantCoordinator.(AppSettings) -> Unit,
    ) {
        viewModelScope.launch {
            runCatching {
                assistantCoordinator.block(storedSettings.value)
            }.onFailure { throwable ->
                updateUiState {
                    SettingsUiMutationSupport.applyMessageError(
                        current = it,
                        errorMessage = throwable.message ?: "助手操作失败",
                    )
                }
            }
        }
    }

    private fun launchPersistenceMutation(
        defaultErrorMessage: String,
        action: suspend () -> SettingsPersistenceResult?,
    ) {
        launchUiMutation(
            defaultErrorMessage = defaultErrorMessage,
            action = action,
            onSuccess = { current, result ->
                result?.let { SettingsUiMutationSupport.applyPersistenceSuccess(current, it) } ?: current
            },
        )
    }

    private fun <T> launchUiMutation(
        defaultErrorMessage: String,
        action: suspend () -> T,
        onSuccess: (SettingsUiState, T) -> SettingsUiState,
    ) {
        viewModelScope.launch {
            runCatching {
                action()
            }.onSuccess { result ->
                updateUiState { current -> onSuccess(current, result) }
            }.onFailure { throwable ->
                updateUiState { current ->
                    SettingsUiMutationSupport.applyMessageError(
                        current,
                        throwable.message ?: defaultErrorMessage,
                    )
                }
            }
        }
    }

    // ── T7.2：persistProviderDrafts 复用 launchUiMutation，去掉独立 launch/runCatching。 ──
    private fun persistProviderDrafts(
        providers: List<ProviderSettings>,
        selectedProviderId: String,
    ) = launchUiMutation(
        defaultErrorMessage = "提供商保存失败",
        action = {
            settingsEditor.saveProviderSettings(
                providers = providers,
                selectedProviderId = selectedProviderId,
            )
        },
        onSuccess = { current, _ -> current },
    )

    private fun loadModelsForProvider(request: SettingsModelLoadRequest) {
        viewModelScope.launch {
            updateUiState { SettingsUiMutationSupport.beginLoadingModels(it, request.providerId) }
            runCatching {
                modelLoadCoordinator.loadModelsForProvider(
                    providerId = request.providerId,
                    baseUrl = request.baseUrl,
                    apiKey = request.apiKey,
                    selectedModel = request.selectedModel,
                    apiProtocol = request.apiProtocol,
                    persistResult = request.persistResult,
                    persistedProviders = request.persistedProviders,
                    persistedSelectedProviderId = request.persistedSelectedProviderId,
                )
            }.onSuccess { result ->
                updateUiState { SettingsUiMutationSupport.applyLoadModelsSuccess(it, result) }
            }.onFailure { throwable ->
                updateUiState { SettingsUiMutationSupport.applyLoadModelsFailure(it, throwable.message ?: "模型拉取失败") }
            }
        }
    }

    companion object {
        fun factory(
            settingsRepository: AiSettingsRepository,
            settingsEditor: AiSettingsEditor,
            modelCatalogRepository: AiModelCatalogRepository,
            imageFileCleaner: suspend (String?) -> Boolean = { false },
        ): ViewModelProvider.Factory {
            return typedViewModelFactory {
                SettingsViewModel(
                    settingsRepository = settingsRepository,
                    settingsEditor = settingsEditor,
                    modelCatalogRepository = modelCatalogRepository,
                    imageFileCleaner = imageFileCleaner,
                )
            }
        }
    }
}
