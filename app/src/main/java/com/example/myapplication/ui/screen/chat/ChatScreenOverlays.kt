package com.example.myapplication.ui.screen.chat

import androidx.compose.runtime.Composable
import com.example.myapplication.ui.component.ContextGovernanceSheet
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.viewmodel.ChatUiState

@Composable
internal fun ChatScreenOverlays(
    uiState: ChatUiState,
    showProfileSheet: Boolean,
    draftUserDisplayName: String,
    draftUserAvatarUri: String,
    draftUserAvatarUrl: String,
    onDisplayNameChange: (String) -> Unit,
    onAvatarUrlChange: (String) -> Unit,
    onPickLocalAvatar: () -> Unit,
    onClearAvatar: () -> Unit,
    onDismissProfileSheet: () -> Unit,
    onSaveProfile: () -> Unit,
    showSpecialPlaySheet: Boolean,
    onDismissSpecialPlaySheet: () -> Unit,
    onOpenSpecialPlayEditor: (ChatSpecialType) -> Unit,
    activeSpecialPlayDraft: ChatSpecialPlayDraft?,
    onSpecialPlayDraftChange: (ChatSpecialPlayDraft) -> Unit,
    onDismissSpecialPlayEditor: () -> Unit,
    onConfirmSpecialPlay: () -> Unit,
    showModelSheet: Boolean,
    providerOptions: List<com.example.myapplication.model.ProviderSettings>,
    currentProviderId: String,
    currentModel: String,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    onDismissModelSheet: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    showReasoningSheet: Boolean,
    activeProvider: com.example.myapplication.model.ProviderSettings?,
    reasoningBudgetHint: String,
    onDismissReasoningSheet: () -> Unit,
    onUpdateThinkingBudget: (String, Int?) -> Unit,
    showPromptDebugSheet: Boolean,
    onDismissPromptDebugSheet: () -> Unit,
    onRefreshConversationSummary: () -> Unit,
    onDismissTranslationSheet: () -> Unit,
    onApplyTranslationToInput: (Boolean) -> Unit,
    onSendTranslationAsMessage: () -> Unit,
    showExportSheet: Boolean,
    exportOptions: ConversationExportOptions,
    onDismissExportSheet: () -> Unit,
    onUpdateExportOptions: (ConversationExportOptions) -> Unit,
    onExportMarkdown: () -> Unit,
    onCopyPlainText: () -> Unit,
    onShareConversation: () -> Unit,
) {
    if (showProfileSheet) {
        ProfileEditorSheet(
            displayName = draftUserDisplayName,
            avatarUri = draftUserAvatarUri,
            avatarUrl = draftUserAvatarUrl,
            onDisplayNameChange = onDisplayNameChange,
            onAvatarUrlChange = onAvatarUrlChange,
            onPickLocalAvatar = onPickLocalAvatar,
            onClearAvatar = onClearAvatar,
            onDismissRequest = onDismissProfileSheet,
            onSave = onSaveProfile,
        )
    }

    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = onDismissSpecialPlaySheet,
            onOpenPlay = onOpenSpecialPlayEditor,
        )
    }

    activeSpecialPlayDraft?.let { draft ->
        SpecialPlayEditorSheet(
            draft = draft,
            onDraftChange = onSpecialPlayDraftChange,
            onDismissRequest = onDismissSpecialPlayEditor,
            onConfirm = onConfirmSpecialPlay,
        )
    }

    if (showModelSheet) {
        ModelPickerSheet(
            providerOptions = providerOptions,
            currentProviderId = currentProviderId,
            currentModel = currentModel,
            isLoadingModels = isLoadingModels,
            loadingProviderId = loadingProviderId,
            isSavingModel = isSavingModel,
            onDismissRequest = onDismissModelSheet,
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = onOpenProviderDetail,
            onSelectModel = { providerId, model ->
                onSelectModel(providerId, model)
                onDismissModelSheet()
            },
        )
    }

    if (showReasoningSheet) {
        ReasoningBudgetSheet(
            provider = activeProvider,
            currentModel = currentModel,
            isSavingModel = isSavingModel,
            reasoningBudgetHint = reasoningBudgetHint,
            onDismissRequest = onDismissReasoningSheet,
            onUpdateThinkingBudget = { providerId, budget ->
                onUpdateThinkingBudget(providerId, budget)
                onDismissReasoningSheet()
            },
        )
    }

    if (showPromptDebugSheet) {
        ContextGovernanceSheet(
            snapshot = uiState.contextGovernance,
            rawDebugDump = uiState.latestPromptDebugDump,
            onRefreshSummary = onRefreshConversationSummary,
            onDismissRequest = onDismissPromptDebugSheet,
        )
    }

    if (uiState.translation.isVisible) {
        TranslationResultSheet(
            translation = uiState.translation,
            onDismissRequest = onDismissTranslationSheet,
            onReplaceInput = { onApplyTranslationToInput(true) },
            onAppendToInput = { onApplyTranslationToInput(false) },
            onSendAsMessage = onSendTranslationAsMessage,
        )
    }

    if (showExportSheet) {
        ConversationExportSheet(
            title = uiState.currentConversationTitle,
            options = exportOptions,
            onDismissRequest = onDismissExportSheet,
            onUpdateOptions = onUpdateExportOptions,
            onExportMarkdown = onExportMarkdown,
            onCopyPlainText = onCopyPlainText,
            onShareConversation = onShareConversation,
        )
    }
}
