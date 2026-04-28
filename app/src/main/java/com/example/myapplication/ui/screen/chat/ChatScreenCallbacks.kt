package com.example.myapplication.ui.screen.chat

import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatSpecialPlayDraft

data class ChatScreenCallbacks(
    val message: ChatMessageCallbacks,
    val conversation: ChatConversationCallbacks,
    val search: ChatSearchCallbacks,
    val translation: ChatTranslationCallbacks,
    val model: ChatModelCallbacks,
    val profile: ChatProfileCallbacks,
    val navigation: ChatNavigationCallbacks,
    val ui: ChatUiCallbacks,
)

data class ChatMessageCallbacks(
    val onInputChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onRetryMessage: (String) -> Unit,
    val onEditUserMessage: (String) -> Unit,
    val onToggleMemoryMessage: (String) -> Unit,
    val onCancelSending: () -> Unit,
    val onAddPendingParts: (List<ChatMessagePart>) -> Unit,
    val onRemovePendingPart: (Int) -> Unit,
    val onSendSpecialPlay: (ChatSpecialPlayDraft) -> Unit,
    val onConfirmTransferReceipt: (String) -> Unit,
)

data class ChatConversationCallbacks(
    val onCreateConversation: () -> Unit,
    val onSelectConversation: (String) -> Unit,
    val onClearConversation: (String) -> Unit,
    val onDeleteConversation: (String) -> Unit,
    val onClearCurrentConversation: () -> Unit,
    val onRefreshConversationSummary: () -> Unit,
)

data class ChatSearchCallbacks(
    val onToggleSearch: () -> Unit,
    val onSelectSearchSource: (String) -> Unit,
    val onUpdateSearchResultCount: (Int) -> Unit,
)

data class ChatTranslationCallbacks(
    val onTranslateDraft: () -> Unit,
    val onTranslateMessage: (String) -> Unit,
    val onDismissTranslationSheet: () -> Unit,
    val onApplyTranslationToInput: (Boolean) -> Unit,
    val onSendTranslationAsMessage: () -> Unit,
)

data class ChatModelCallbacks(
    val onSelectProvider: (String) -> Unit,
    val onSelectModel: (String, String) -> Unit,
    val onUpdateThinkingBudget: (String, Int?) -> Unit,
)

data class ChatProfileCallbacks(
    val onSaveUserProfile: (String, String, String, String) -> Unit,
    val onOpenUserMasks: () -> Unit,
    val onSetDefaultUserPersonaMask: (String) -> Unit,
    val onSelectAssistant: (String) -> Unit,
    val onOpenAssistantDetail: (String) -> Unit,
)

data class ChatNavigationCallbacks(
    val onOpenSettings: () -> Unit,
    val onOpenHome: () -> Unit,
    val onOpenTranslator: () -> Unit,
    val onOpenRoleplay: () -> Unit,
    val onOpenPhoneCheck: (String) -> Unit,
    val onOpenProviderDetail: (String) -> Unit,
    val onOpenContextLog: () -> Unit,
)

data class ChatUiCallbacks(
    val onClearErrorMessage: () -> Unit,
    val onClearNoticeMessage: () -> Unit,
)
