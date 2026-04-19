package com.example.myapplication.ui.screen.chat

import com.example.myapplication.ui.component.*

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.ui.component.ChatMessagePerformanceMode
import com.example.myapplication.viewmodel.ChatUiState
import kotlinx.coroutines.launch

private val SectionSpacing = 12.dp
private const val StreamFollowScrollWindowMillis = 64L
private const val BottomFollowTolerancePx = 48
private const val StreamBottomAnchorKey = "stream-bottom-anchor"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    uiState: ChatUiState,
    isLoadingModels: Boolean,
    loadingProviderId: String,
    isSavingModel: Boolean,
    callbacks: ChatScreenCallbacks,
) {
    val onInputChange = callbacks.message.onInputChange
    val onSend = callbacks.message.onSend
    val onRetryMessage = callbacks.message.onRetryMessage
    val onEditUserMessage = callbacks.message.onEditUserMessage
    val onToggleMemoryMessage = callbacks.message.onToggleMemoryMessage
    val onCancelSending = callbacks.message.onCancelSending
    val onAddPendingParts = callbacks.message.onAddPendingParts
    val onRemovePendingPart = callbacks.message.onRemovePendingPart
    val onSendSpecialPlay = callbacks.message.onSendSpecialPlay
    val onConfirmTransferReceipt = callbacks.message.onConfirmTransferReceipt
    val onCreateConversation = callbacks.conversation.onCreateConversation
    val onSelectConversation = callbacks.conversation.onSelectConversation
    val onClearConversation = callbacks.conversation.onClearConversation
    val onDeleteConversation = callbacks.conversation.onDeleteConversation
    val onClearCurrentConversation = callbacks.conversation.onClearCurrentConversation
    val onRefreshConversationSummary = callbacks.conversation.onRefreshConversationSummary
    val onToggleSearch = callbacks.search.onToggleSearch
    val onSelectSearchSource = callbacks.search.onSelectSearchSource
    val onUpdateSearchResultCount = callbacks.search.onUpdateSearchResultCount
    val onTranslateDraft = callbacks.translation.onTranslateDraft
    val onTranslateMessage = callbacks.translation.onTranslateMessage
    val onDismissTranslationSheet = callbacks.translation.onDismissTranslationSheet
    val onApplyTranslationToInput = callbacks.translation.onApplyTranslationToInput
    val onSendTranslationAsMessage = callbacks.translation.onSendTranslationAsMessage
    val onSelectProvider = callbacks.model.onSelectProvider
    val onSelectModel = callbacks.model.onSelectModel
    val onUpdateThinkingBudget = callbacks.model.onUpdateThinkingBudget
    val onSaveUserProfile = callbacks.profile.onSaveUserProfile
    val onSelectAssistant = callbacks.profile.onSelectAssistant
    val onOpenAssistantDetail = callbacks.profile.onOpenAssistantDetail
    val onOpenSettings = callbacks.navigation.onOpenSettings
    val onOpenHome = callbacks.navigation.onOpenHome
    val onOpenTranslator = callbacks.navigation.onOpenTranslator
    val onOpenRoleplay = callbacks.navigation.onOpenRoleplay
    val onOpenPhoneCheck = callbacks.navigation.onOpenPhoneCheck
    val onOpenProviderDetail = callbacks.navigation.onOpenProviderDetail
    val onClearErrorMessage = callbacks.ui.onClearErrorMessage
    val onClearNoticeMessage = callbacks.ui.onClearNoticeMessage

    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val resources = LocalResources.current
    val listState = remember(uiState.displayedConversationId) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val autoFollowState = rememberChatAutoFollowState(uiState.displayedConversationId)
    val chatMessagePerformanceMode = ChatMessagePerformanceMode.SCROLLING_LIGHT
    val colorScheme = MaterialTheme.colorScheme
    val derivations = rememberChatScreenDerivations(uiState, resources)
    val localState = rememberChatScreenLocalState(
        userDisplayName = derivations.userDisplayName,
        userPersonaPrompt = derivations.userPersonaPrompt,
        userAvatarUri = derivations.userAvatarUri,
        userAvatarUrl = derivations.userAvatarUrl,
    )
    val launchers = rememberChatScreenLaunchers(
        context = context,
        scope = scope,
        resources = resources,
        localState = localState,
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        currentAssistantName = derivations.currentAssistantName,
        onAddPendingParts = onAddPendingParts,
        onSaveUserProfile = onSaveUserProfile,
    )
    val isNearBottom = rememberIsListNearBottom(listState)

    ChatAutoFollowEffects(
        state = autoFollowState,
        listState = listState,
        displayedConversationId = uiState.displayedConversationId,
        messageCount = uiState.messages.size,
        isSending = uiState.isSending,
        isNearBottom = isNearBottom,
        lastMessageContentLength = derivations.lastMessageContentLength,
        lastReasoningContentLength = derivations.lastReasoningContentLength,
        lastMessagePartCount = derivations.lastMessagePartCount,
        lastMessageId = derivations.lastMessage?.id,
        lastMessageStatus = derivations.lastMessage?.status,
    )

    ChatFeedbackEffects(
        snackbarHostState = snackbarHostState,
        errorMessage = uiState.errorMessage,
        noticeMessage = uiState.noticeMessage,
        currentModelSupportsReasoning = derivations.currentModelSupportsReasoning,
        onClearErrorMessage = onClearErrorMessage,
        onClearNoticeMessage = onClearNoticeMessage,
        onHideReasoningSheet = { localState.setShowReasoningSheet(false) },
    )

    ChatScreenChrome(
        uiState = uiState,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        currentProviderName = derivations.activeProvider?.name.orEmpty(),
        currentModel = derivations.currentModel,
        userDisplayName = derivations.userDisplayName,
        userAvatarUri = derivations.userAvatarUri,
        userAvatarUrl = derivations.userAvatarUrl,
        currentAssistant = uiState.currentAssistant,
        onSelectAssistant = onSelectAssistant,
        onOpenAssistantDetail = { assistantId ->
            onOpenAssistantDetail(assistantId)
            scope.launch { drawerState.close() }
        },
        onCreateConversation = {
            onCreateConversation()
            scope.launch { drawerState.close() }
        },
        onSelectConversation = { conversationId ->
            onSelectConversation(conversationId)
            scope.launch { drawerState.close() }
        },
        onClearConversation = { conversationId ->
            onClearConversation(conversationId)
            scope.launch { drawerState.close() }
        },
        onClearCurrentConversation = {
            onClearCurrentConversation()
            scope.launch { drawerState.close() }
        },
        onDeleteConversation = { conversationId ->
            onDeleteConversation(conversationId)
            scope.launch { drawerState.close() }
        },
        onOpenTranslator = {
            onOpenTranslator()
            scope.launch { drawerState.close() }
        },
        onOpenRoleplay = {
            onOpenRoleplay()
            scope.launch { drawerState.close() }
        },
        onOpenSettings = {
            onOpenSettings()
            scope.launch { drawerState.close() }
        },
        onEditProfile = localState.openProfileSheet,
        onOpenConversationDrawer = {
            scope.launch { drawerState.open() }
        },
        onOpenPromptDebugSheet = { localState.setShowPromptDebugSheet(true) },
        onOpenExportSheet = { localState.setShowExportSheet(true) },
    ) { contentModifier ->
        Column(modifier = contentModifier) {
            ChatConversationPane(
                uiState = uiState,
                hasBaseCredentials = derivations.hasBaseCredentials,
                hasRequiredConfig = derivations.hasRequiredConfig,
                currentModelIsImageGeneration = derivations.currentModelIsImageGeneration,
                availableModelInfos = derivations.availableModelInfos,
                listState = listState,
                isNearBottom = isNearBottom,
                shouldAutoFollowStreaming = autoFollowState.shouldAutoFollowStreaming,
                performanceMode = chatMessagePerformanceMode,
                isSavingModel = isSavingModel,
                currentModel = derivations.currentModel,
                canAttachImages = derivations.canAttachImages,
                canAttachFiles = derivations.canAttachFiles,
                canUseSpecialPlay = derivations.canUseSpecialPlay,
                currentModelSupportsReasoning = derivations.currentModelSupportsReasoning,
                searchEnabled = derivations.searchEnabled,
                searchAvailable = derivations.searchAvailable,
                reasoningActionLabel = derivations.reasoningActionLabel,
                currentAssistantName = derivations.currentAssistantName,
                searchSettings = derivations.normalizedSearchSettings,
                selectedSearchSource = derivations.selectedSearchSource,
                selectedSearchProvider = derivations.selectedSearchProvider,
                onInputChange = onInputChange,
                onSend = onSend,
                onOpenConversationDrawer = {
                    scope.launch { drawerState.open() }
                },
                onRetryMessage = onRetryMessage,
                onOpenMessageActions = { messageId ->
                    localState.setActiveMessageActionId(messageId)
                },
                onOpenUrlPreview = { url, title ->
                    localState.setMessagePreviewPayload(
                        ChatMessagePreviewPayload.ExternalUrlPreview(
                            title = title,
                            url = url,
                        ),
                    )
                },
                onToggleMemoryMessage = onToggleMemoryMessage,
                onTranslateMessage = onTranslateMessage,
                onConfirmTransferReceipt = onConfirmTransferReceipt,
                onToggleSearch = onToggleSearch,
                onSelectSearchSource = onSelectSearchSource,
                onUpdateSearchResultCount = onUpdateSearchResultCount,
                onSearchUnavailable = {
                    scope.launch {
                        snackbarHostState.showSnackbar(derivations.searchUnavailableMessage)
                    }
                },
                onOpenSearchPicker = { localState.setShowSearchPickerSheet(true) },
                onTranslateDraft = onTranslateDraft,
                onPickImageClick = launchers.pickImages,
                onPickFileClick = launchers.pickFile,
                onOpenModelPicker = { localState.setShowModelSheet(true) },
                onOpenReasoningPicker = { localState.setShowReasoningSheet(true) },
                onOpenSpecialPlayClick = {
                    localState.setShowSpecialPlaySheet(true)
                },
                onRemovePendingPart = onRemovePendingPart,
                onCancelSending = onCancelSending,
                onJumpToBottom = {
                    scope.launch {
                        autoFollowState.userDisabledAutoFollow = false
                        autoFollowState.shouldAutoFollowStreaming = true
                        autoFollowState.scrollToConversationEnd(
                            listState = listState,
                            bottomAnchorIndex = uiState.messages.size,
                            animated = true,
                        )
                    }
                },
            )
        }
    }

    ChatScreenOverlays(
        uiState = uiState,
        showProfileSheet = localState.showProfileSheet,
        draftUserDisplayName = localState.draftUserDisplayName,
        draftUserPersonaPrompt = localState.draftUserPersonaPrompt,
        draftUserAvatarUri = localState.draftUserAvatarUri,
        draftUserAvatarUrl = localState.draftUserAvatarUrl,
        onDisplayNameChange = localState.setDraftUserDisplayName,
        onPersonaPromptChange = localState.setDraftUserPersonaPrompt,
        onAvatarUrlChange = {
            localState.setDraftUserAvatarUrl(it)
            if (it.isNotBlank()) {
                localState.setDraftUserAvatarUri("")
            }
        },
        onPickLocalAvatar = launchers.pickAvatar,
        onClearAvatar = {
            localState.setDraftUserAvatarUri("")
            localState.setDraftUserAvatarUrl("")
        },
        onDismissProfileSheet = { localState.setShowProfileSheet(false) },
        onSaveProfile = launchers.saveProfileDraft,
        showSpecialPlaySheet = localState.showSpecialPlaySheet,
        onDismissSpecialPlaySheet = { localState.setShowSpecialPlaySheet(false) },
        onOpenSpecialPlayEditor = { type ->
            localState.setShowSpecialPlaySheet(false)
            launchers.primeSpecialPlayDraft(type)
            localState.setActiveSpecialPlayType(type)
        },
        onOpenPhoneCheck = {
            localState.setShowSpecialPlaySheet(false)
            uiState.currentConversationId
                .takeIf { it.isNotBlank() }
                ?.let(onOpenPhoneCheck)
        },
        activeSpecialPlayDraft = localState.activeSpecialPlayDraft,
        onSpecialPlayDraftChange = { localState.updateActiveSpecialPlayDraft(it) },
        onDismissSpecialPlayEditor = { localState.setActiveSpecialPlayType(null) },
        onConfirmSpecialPlay = {
            val activeDraft = localState.activeSpecialPlayDraft
            if (activeDraft != null) {
                onSendSpecialPlay(activeDraft)
                launchers.resetSpecialPlayDraft(activeDraft.type)
                localState.setActiveSpecialPlayType(null)
            }
        },
        showModelSheet = localState.showModelSheet,
        providerOptions = derivations.providerOptions,
        currentProviderId = derivations.currentProviderId,
        currentModel = derivations.currentModel,
        isLoadingModels = isLoadingModels,
        loadingProviderId = loadingProviderId,
        isSavingModel = isSavingModel,
        onDismissModelSheet = { localState.setShowModelSheet(false) },
        onSelectProvider = onSelectProvider,
        onOpenProviderDetail = onOpenProviderDetail,
        onSelectModel = onSelectModel,
        showReasoningSheet = localState.showReasoningSheet,
        activeProvider = derivations.activeProvider,
        reasoningBudgetHint = derivations.reasoningBudgetHint,
        onDismissReasoningSheet = { localState.setShowReasoningSheet(false) },
        onUpdateThinkingBudget = onUpdateThinkingBudget,
        showSearchPickerSheet = localState.showSearchPickerSheet,
        searchEnabled = derivations.searchEnabled,
        searchAvailable = derivations.searchAvailable,
        searchSettings = derivations.normalizedSearchSettings,
        currentModelIsImageGeneration = derivations.currentModelIsImageGeneration,
        currentModelSupportsTools = com.example.myapplication.model.ModelAbility.TOOL in derivations.currentModelAbilities,
        selectedSearchSource = derivations.selectedSearchSource,
        selectedSearchProvider = derivations.selectedSearchProvider,
        onDismissSearchPickerSheet = { localState.setShowSearchPickerSheet(false) },
        onToggleSearch = onToggleSearch,
        onSelectSearchSource = onSelectSearchSource,
        onUpdateSearchResultCount = onUpdateSearchResultCount,
        onOpenSearchSettings = {
            localState.setShowSearchPickerSheet(false)
            onOpenSettings()
        },
        showPromptDebugSheet = localState.showPromptDebugSheet,
        onDismissPromptDebugSheet = { localState.setShowPromptDebugSheet(false) },
        onRefreshConversationSummary = onRefreshConversationSummary,
        onDismissTranslationSheet = onDismissTranslationSheet,
        onApplyTranslationToInput = onApplyTranslationToInput,
        onSendTranslationAsMessage = onSendTranslationAsMessage,
        showExportSheet = localState.showExportSheet,
        exportOptions = localState.exportOptions,
        onDismissExportSheet = { localState.setShowExportSheet(false) },
        onUpdateExportOptions = localState.setExportOptions,
        onExportMarkdown = {
            launchers.exportMarkdown()
            localState.setShowExportSheet(false)
        },
        onCopyPlainText = {
            val plainText = buildConversationPlainText(
                title = uiState.currentConversationTitle,
                messages = uiState.messages,
                options = localState.exportOptions,
            )
            scope.copyPlainTextToClipboard(clipboard, "conversation-plain-text", plainText)
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.chat_copy_plain_text_done))
            }
            localState.setShowExportSheet(false)
        },
        onShareConversation = {
            val shareText = buildConversationMarkdown(
                title = uiState.currentConversationTitle,
                messages = uiState.messages,
                options = localState.exportOptions,
            )
            context.startActivity(
                Intent.createChooser(
                    buildShareIntent(shareText),
                    resources.getString(R.string.chat_share_conversation),
                ),
            )
            localState.setShowExportSheet(false)
        },
        activeMessageAction = localState.activeMessageActionId?.let { messageId ->
            uiState.messages.firstOrNull { it.id == messageId }
        },
        onDismissMessageAction = { localState.setActiveMessageActionId(null) },
        onSelectMessageCopy = { message ->
            localState.setMessageSelectionPayload(buildMessageSelectionPayload(message))
            localState.setActiveMessageActionId(null)
        },
        onOpenMessagePreviewPayload = { message ->
            if (messageHasPreviewableText(message)) {
                localState.setMessagePreviewPayload(
                    buildMessagePreviewPayload(
                        message = message,
                        colorScheme = colorScheme,
                    ),
                )
            }
            localState.setActiveMessageActionId(null)
        },
        onExportMessageMarkdown = { message ->
            localState.setPendingMessageExport(
                ChatMessageExportPayload(
                    fileName = buildMessageExportFileName(message),
                    markdown = buildMessageMarkdown(message),
                ),
            )
            launchers.exportMessageMarkdown(buildMessageExportFileName(message))
            localState.setActiveMessageActionId(null)
        },
        onShareMessage = { message ->
            context.startActivity(
                Intent.createChooser(
                    buildShareIntent(buildMessageMarkdown(message)),
                    resources.getString(R.string.chat_share_message),
                ),
            )
            localState.setActiveMessageActionId(null)
        },
        onEditUserMessage = { message ->
            onEditUserMessage(message.id)
            localState.setActiveMessageActionId(null)
        },
        onRetryMessage = { message ->
            onRetryMessage(message.id)
            localState.setActiveMessageActionId(null)
        },
        messageSelectionPayload = localState.messageSelectionPayload,
        onDismissMessageSelection = { localState.setMessageSelectionPayload(null) },
        messagePreviewPayload = localState.messagePreviewPayload,
        onDismissMessagePreview = { localState.setMessagePreviewPayload(null) },
        searchResultPreviewPayload = localState.searchResultPreviewPayload,
        onDismissSearchResultPreview = { localState.setSearchResultPreviewPayload(null) },
        onOpenUrlPreview = { url, title ->
            localState.setMessagePreviewPayload(
                ChatMessagePreviewPayload.ExternalUrlPreview(
                    title = title,
                    url = url,
                ),
            )
            localState.setSearchResultPreviewPayload(null)
        },
        onOpenSearchResultPreview = { message ->
            buildSearchResultPreviewPayload(message)?.let(localState.setSearchResultPreviewPayload)
            localState.setActiveMessageActionId(null)
        },
    )
}
