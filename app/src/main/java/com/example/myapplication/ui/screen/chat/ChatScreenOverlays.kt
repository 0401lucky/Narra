package com.example.myapplication.ui.screen.chat

import androidx.compose.runtime.Composable
import com.example.myapplication.model.ChatMessagePart
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
    onOpenTransferFromSpecialPlay: () -> Unit,
    showTransferSheet: Boolean,
    transferDraft: TransferPlayDraft,
    onTransferDraftChange: (TransferPlayDraft) -> Unit,
    onDismissTransferSheet: () -> Unit,
    onConfirmTransfer: () -> Unit,
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
            onOpenTransfer = onOpenTransferFromSpecialPlay,
        )
    }

    if (showTransferSheet) {
        TransferPlaySheet(
            draft = transferDraft,
            onDraftChange = onTransferDraftChange,
            onDismissRequest = onDismissTransferSheet,
            onConfirm = onConfirmTransfer,
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
        PromptDebugSheet(
            debugDump = uiState.latestPromptDebugDump,
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
