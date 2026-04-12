package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialPlayDraft

/**
 * ChatScreen 的所有用户交互回调，按职责分组。
 */
data class ChatScreenCallbacks(
    val conversation: ConversationCallbacks,
    val message: MessageCallbacks,
    val translation: TranslationCallbacks,
    val settings: SettingsCallbacks,
    val navigation: NavigationCallbacks,
    val ui: UiCallbacks,
)

data class ConversationCallbacks(
    val onCreate: () -> Unit,
    val onSelect: (String) -> Unit,
    val onClear: (String) -> Unit,
    val onDelete: (String) -> Unit,
    val onDeleteCurrent: () -> Unit,
    val onClearCurrent: () -> Unit,
    val onToggleSearch: () -> Unit,
)

data class MessageCallbacks(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onRetry: (String) -> Unit,
    val onEditUserMessage: (String) -> Unit,
    val onToggleMemory: (String) -> Unit,
    val onShareMessage: (String) -> Unit,
    val onExportMessageMarkdown: (String) -> Unit,
    val onOpenMessagePreview: (String) -> Unit,
    val onCancelSending: () -> Unit,
    val onAddPendingParts: (List<ChatMessagePart>) -> Unit,
    val onRemovePendingPart: (Int) -> Unit,
    val onSendSpecialPlay: (ChatSpecialPlayDraft) -> Unit,
    val onConfirmTransferReceipt: (String) -> Unit,
)

data class TranslationCallbacks(
    val onTranslateDraft: () -> Unit,
    val onTranslateMessage: (String) -> Unit,
    val onDismissSheet: () -> Unit,
    val onApplyToInput: (replace: Boolean) -> Unit,
    val onSendAsMessage: () -> Unit,
)

data class SettingsCallbacks(
    val onSelectProvider: (String) -> Unit,
    val onSelectModel: (providerId: String, modelId: String) -> Unit,
    val onUpdateThinkingBudget: (modelId: String, budget: Int?) -> Unit,
    val onSaveUserProfile: (displayName: String, personaPrompt: String, avatarUri: String, avatarUrl: String) -> Unit,
    val onSelectAssistant: (String) -> Unit,
    val onOpenAssistantDetail: (String) -> Unit,
    val onOpenProviderDetail: (String) -> Unit,
)

data class NavigationCallbacks(
    val onOpenSettings: () -> Unit,
    val onOpenHome: () -> Unit,
    val onOpenTranslator: () -> Unit,
    val onOpenRoleplay: () -> Unit,
)

data class UiCallbacks(
    val onClearErrorMessage: () -> Unit,
    val onClearNoticeMessage: () -> Unit,
)
