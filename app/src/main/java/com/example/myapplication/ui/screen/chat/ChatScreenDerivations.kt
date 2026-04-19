package com.example.myapplication.ui.screen.chat

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import com.example.myapplication.R
import com.example.myapplication.conversation.ChatConversationSupport
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.reasoningBudgetSupportHint
import com.example.myapplication.model.resolveReasoningBudgetLabel
import com.example.myapplication.model.supportsThinkingBudgetControl
import com.example.myapplication.viewmodel.ChatUiState

// ChatScreen 派生态聚合：将 ChatScreen 内部约 150 行的 provider/model/user/search/reasoning/last-message
// 纯派生逻辑集中到单个 @Immutable data class 中，让主 composable 只剩容器装配代码。
// 每个字段对应原先在 ChatScreen 里用独立 `remember` 缓存的派生值，语义与原实现一致。
@Immutable
internal data class ChatScreenDerivations(
    val providerOptions: List<ProviderSettings>,
    val activeProvider: ProviderSettings?,
    val currentProviderId: String,
    val currentModel: String,
    val currentConversation: Conversation?,
    val availableModelInfos: List<ModelInfo>,
    val currentModelAbilities: Set<ModelAbility>,
    val currentModelSupportsReasoning: Boolean,
    val currentModelSupportsVision: Boolean,
    val currentModelIsImageGeneration: Boolean,
    val canAttachImages: Boolean,
    val canAttachFiles: Boolean,
    val canUseSpecialPlay: Boolean,
    val canAdjustThinkingBudget: Boolean,
    val currentAssistantName: String,
    val userDisplayName: String,
    val userPersonaPrompt: String,
    val userAvatarUri: String,
    val userAvatarUrl: String,
    val hasBaseCredentials: Boolean,
    val hasRequiredConfig: Boolean,
    val selectedSearchSource: SearchSourceConfig?,
    val selectedSearchProvider: ProviderSettings?,
    val normalizedSearchSettings: SearchSettings,
    val searchEnabled: Boolean,
    val searchAvailable: Boolean,
    val searchUnavailableMessage: String,
    val reasoningBudgetHint: String,
    val reasoningActionLabel: String,
    val lastMessage: ChatMessage?,
    val lastMessageContentLength: Int?,
    val lastReasoningContentLength: Int?,
    val lastMessagePartCount: Int,
)

@Composable
internal fun rememberChatScreenDerivations(
    uiState: ChatUiState,
    resources: Resources,
): ChatScreenDerivations {
    val providerOptions = remember(uiState.settings) { uiState.settings.enabledProviders() }
    val activeProvider = remember(uiState.settings) { uiState.settings.activeProvider() }
    val currentProviderId = activeProvider?.id.orEmpty()
    val currentModel = activeProvider?.selectedModel.orEmpty()
    val currentConversation = remember(uiState.currentConversationId, uiState.conversations) {
        uiState.conversations.firstOrNull { it.id == uiState.currentConversationId }
    }
    val currentAssistantName = uiState.currentAssistant?.name.orEmpty().ifBlank {
        resources.getString(R.string.chat_default_other_name)
    }
    val userDisplayName = uiState.settings.resolvedUserDisplayName()
    val userPersonaPrompt = uiState.settings.userPersonaPrompt
    val userAvatarUri = uiState.settings.userAvatarUri
    val userAvatarUrl = uiState.settings.userAvatarUrl
    val availableModelInfos = remember(activeProvider) {
        buildList {
            addAll(activeProvider?.resolvedModels().orEmpty())
            val selectedModelId = activeProvider?.selectedModel.orEmpty()
            if (selectedModelId.isNotBlank() && none { it.modelId == selectedModelId }) {
                add(ModelInfo(modelId = selectedModelId, displayName = selectedModelId))
            }
        }
    }

    val hasBaseCredentials = uiState.settings.hasBaseCredentials()
    val hasRequiredConfig = uiState.settings.hasRequiredConfig()
    val currentModelAbilities = remember(activeProvider, currentModel) {
        activeProvider?.resolveModelAbilities(currentModel) ?: inferModelAbilities(currentModel)
    }
    val currentModelSupportsReasoning = remember(currentModelAbilities, currentModel) {
        currentModel.isNotBlank() && ModelAbility.REASONING in currentModelAbilities
    }
    val currentModelSupportsVision = remember(currentModelAbilities, currentModel) {
        currentModel.isNotBlank() && ModelAbility.VISION in currentModelAbilities
    }
    val currentModelIsImageGeneration = remember(uiState.settings, currentModel) {
        ChatConversationSupport.supportsImageGeneration(uiState.settings, currentModel)
    }
    val canAttachImages = remember(currentModelSupportsVision, currentModelIsImageGeneration) {
        currentModelSupportsVision && !currentModelIsImageGeneration
    }
    val canAttachFiles = remember(currentModelIsImageGeneration) { !currentModelIsImageGeneration }
    val canUseSpecialPlay = remember(currentModelIsImageGeneration) { !currentModelIsImageGeneration }
    val canAdjustThinkingBudget = remember(activeProvider, currentModel) {
        activeProvider?.let { supportsThinkingBudgetControl(it, currentModel) } == true
    }

    val selectedSearchSource = remember(uiState.settings) {
        uiState.settings.resolvedSearchSettings().selectedSourceOrNull()
    }
    val selectedSearchProvider = remember(uiState.settings, selectedSearchSource) {
        selectedSearchSource?.let(uiState.settings::resolveSearchSourceProvider)
    }
    val normalizedSearchSettings = remember(uiState.settings) { uiState.settings.resolvedSearchSettings() }
    val searchEnabled = currentConversation?.searchEnabled == true
    val searchAvailable = remember(
        currentConversation,
        hasRequiredConfig,
        currentModel,
        currentModelAbilities,
        currentModelIsImageGeneration,
        uiState.settings,
        selectedSearchProvider,
    ) {
        currentConversation != null &&
            hasRequiredConfig &&
            currentModel.isNotBlank() &&
            !currentModelIsImageGeneration &&
            ModelAbility.TOOL in currentModelAbilities &&
            uiState.settings.hasConfiguredSearchSource()
    }
    val searchUnavailableMessage = when {
        currentConversation == null -> resources.getString(R.string.chat_search_unavail_no_conversation)
        !hasRequiredConfig -> resources.getString(R.string.chat_search_unavail_no_config)
        currentModel.isBlank() -> resources.getString(R.string.chat_search_unavail_no_model)
        currentModelIsImageGeneration -> resources.getString(R.string.chat_search_unavail_image_model)
        ModelAbility.TOOL !in currentModelAbilities -> resources.getString(R.string.chat_search_unavail_no_tool)
        !uiState.settings.hasConfiguredSearchSource() -> {
            if (selectedSearchSource?.type == SearchSourceType.LLM_SEARCH) {
                when {
                    selectedSearchSource.providerId.isBlank() -> resources.getString(R.string.chat_search_unavail_no_provider)
                    selectedSearchProvider == null -> resources.getString(R.string.chat_search_unavail_provider_disabled)
                    !selectedSearchProvider.supportsLlmSearchSource() -> resources.getString(R.string.chat_search_unavail_need_responses)
                    selectedSearchProvider.resolveFunctionModel(ProviderFunction.SEARCH).isBlank() -> resources.getString(R.string.chat_search_unavail_model_off)
                    selectedSearchProvider.resolveFunctionModelMode(ProviderFunction.SEARCH) == ProviderFunctionModelMode.FOLLOW_DEFAULT -> resources.getString(R.string.chat_search_unavail_follow_chat)
                    else -> resources.getString(R.string.chat_search_unavail_enable_source)
                }
            } else {
                resources.getString(R.string.chat_search_unavail_configure_source)
            }
        }
        else -> resources.getString(R.string.chat_search_unavail_unknown)
    }

    val reasoningBudgetHint = remember(activeProvider, currentModel) {
        activeProvider?.let { reasoningBudgetSupportHint(it, currentModel) }.orEmpty()
    }
    val reasoningActionLabel = if (canAdjustThinkingBudget) {
        resources.getString(
            R.string.chat_thinking_with_budget,
            resolveReasoningBudgetLabel(activeProvider?.thinkingBudget),
        )
    } else {
        resources.getString(R.string.chat_thinking)
    }

    val lastMessage = uiState.messages.lastOrNull()
    val lastMessageContentLength = when {
        lastMessage?.id == uiState.streamingMessageId -> uiState.streamingContent.length
        else -> lastMessage?.content?.length
    }
    val lastReasoningContentLength = when {
        lastMessage?.id == uiState.streamingMessageId -> uiState.streamingReasoningContent.length
        else -> lastMessage?.reasoningContent?.length
    }
    val lastMessagePartCount = when {
        lastMessage?.id == uiState.streamingMessageId -> uiState.streamingParts.size
        else -> lastMessage?.parts?.size ?: 0
    }

    return ChatScreenDerivations(
        providerOptions = providerOptions,
        activeProvider = activeProvider,
        currentProviderId = currentProviderId,
        currentModel = currentModel,
        currentConversation = currentConversation,
        availableModelInfos = availableModelInfos,
        currentModelAbilities = currentModelAbilities,
        currentModelSupportsReasoning = currentModelSupportsReasoning,
        currentModelSupportsVision = currentModelSupportsVision,
        currentModelIsImageGeneration = currentModelIsImageGeneration,
        canAttachImages = canAttachImages,
        canAttachFiles = canAttachFiles,
        canUseSpecialPlay = canUseSpecialPlay,
        canAdjustThinkingBudget = canAdjustThinkingBudget,
        currentAssistantName = currentAssistantName,
        userDisplayName = userDisplayName,
        userPersonaPrompt = userPersonaPrompt,
        userAvatarUri = userAvatarUri,
        userAvatarUrl = userAvatarUrl,
        hasBaseCredentials = hasBaseCredentials,
        hasRequiredConfig = hasRequiredConfig,
        selectedSearchSource = selectedSearchSource,
        selectedSearchProvider = selectedSearchProvider,
        normalizedSearchSettings = normalizedSearchSettings,
        searchEnabled = searchEnabled,
        searchAvailable = searchAvailable,
        searchUnavailableMessage = searchUnavailableMessage,
        reasoningBudgetHint = reasoningBudgetHint,
        reasoningActionLabel = reasoningActionLabel,
        lastMessage = lastMessage,
        lastMessageContentLength = lastMessageContentLength,
        lastReasoningContentLength = lastReasoningContentLength,
        lastMessagePartCount = lastMessagePartCount,
    )
}
