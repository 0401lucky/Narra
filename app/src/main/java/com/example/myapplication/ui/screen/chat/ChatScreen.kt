package com.example.myapplication.ui.screen.chat

import com.example.myapplication.ui.component.*

import android.content.Context
import com.example.myapplication.conversation.ChatConversationSupport
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import com.example.myapplication.ui.component.AppSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatSpecialType
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ReasoningBudgetPreset
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.reasoningBudgetSupportHint
import com.example.myapplication.model.resolveReasoningBudgetLabel
import com.example.myapplication.model.supportsThinkingBudgetControl
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.ui.component.AssistantAvatar
import com.example.myapplication.ui.component.ChatMessagePerformanceMode
import com.example.myapplication.ui.component.MessageBubble
import com.example.myapplication.ui.component.MessageInputBar
import com.example.myapplication.ui.component.ModelIcon
import com.example.myapplication.ui.component.UserAvatarLoadState
import com.example.myapplication.ui.component.UserAvatarSource
import com.example.myapplication.ui.component.UserProfileAvatar
import com.example.myapplication.ui.component.rememberUserProfileAvatarState
import com.example.myapplication.viewmodel.ChatUiState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs


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
    val providerOptions = remember(uiState.settings) {
        uiState.settings.enabledProviders()
    }
    val activeProvider = remember(uiState.settings) {
        uiState.settings.activeProvider()
    }
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
    val colorScheme = MaterialTheme.colorScheme
    val localState = rememberChatScreenLocalState(
        userDisplayName = userDisplayName,
        userPersonaPrompt = userPersonaPrompt,
        userAvatarUri = userAvatarUri,
        userAvatarUrl = userAvatarUrl,
    )
    val availableModelInfos = remember(activeProvider) {
        buildList {
            addAll(activeProvider?.resolvedModels().orEmpty())
            val selectedModelId = activeProvider?.selectedModel.orEmpty()
            if (selectedModelId.isNotBlank() && none { it.modelId == selectedModelId }) {
                add(
                    ModelInfo(
                        modelId = selectedModelId,
                        displayName = selectedModelId,
                    ),
                )
            }
        }
    }

    fun handlePickedAttachment(uri: Uri, type: AttachmentType) {
        // 部分 ContentProvider 不支持持久化 URI 权限（如相机拍照），可安全忽略异常
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        runCatching {
            resolveSelectedAttachment(context, uri, type)
        }.onSuccess { attachment ->
            onAddPendingParts(listOf(attachment.toChatMessagePart()))
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    throwable.message ?: resources.getString(R.string.chat_error_read_attachment),
                )
            }
        }
    }

    fun saveProfileDraft() {
        val normalizedName = localState.draftUserDisplayName.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME }
        val normalizedPersonaPrompt = localState.draftUserPersonaPrompt
            .replace("\r\n", "\n")
            .trim()
        val normalizedUrl = localState.draftUserAvatarUrl.trim()
        if (normalizedUrl.isNotBlank() && !isSupportedAvatarUrl(normalizedUrl)) {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.chat_error_avatar_url_http))
            }
            return
        }
        val normalizedUri = if (normalizedUrl.isBlank()) {
            localState.draftUserAvatarUri.trim()
        } else {
            ""
        }
        onSaveUserProfile(normalizedName, normalizedPersonaPrompt, normalizedUri, normalizedUrl)
        localState.setShowProfileSheet(false)
    }

    fun primeSpecialPlayDraft(type: ChatSpecialType) {
        when (type) {
            ChatSpecialType.TRANSFER -> {
                if (localState.transferDraft.counterparty.isBlank()) {
                    localState.setTransferDraft(localState.transferDraft.copy(counterparty = currentAssistantName))
                }
            }

            ChatSpecialType.INVITE -> {
                if (localState.inviteDraft.target.isBlank()) {
                    localState.setInviteDraft(localState.inviteDraft.copy(target = currentAssistantName))
                }
            }

            ChatSpecialType.GIFT -> {
                if (localState.giftDraft.target.isBlank()) {
                    localState.setGiftDraft(localState.giftDraft.copy(target = currentAssistantName))
                }
            }

            ChatSpecialType.TASK,
            ChatSpecialType.PUNISH,
            -> Unit
        }
    }

    fun resetSpecialPlayDraft(type: ChatSpecialType) {
        when (type) {
            ChatSpecialType.TRANSFER -> localState.setTransferDraft(
                TransferPlayDraft(counterparty = currentAssistantName),
            )
            ChatSpecialType.INVITE -> localState.setInviteDraft(
                InvitePlayDraft(target = currentAssistantName),
            )
            ChatSpecialType.GIFT -> localState.setGiftDraft(
                GiftPlayDraft(target = currentAssistantName),
            )
            ChatSpecialType.TASK -> localState.setTaskDraft(TaskPlayDraft())
            ChatSpecialType.PUNISH -> localState.setPunishDraft(PunishPlayDraft())
        }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val attachments = uris.mapNotNull { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            runCatching {
                resolveSelectedAttachment(context, uri, AttachmentType.IMAGE)
            }.getOrNull()
        }
        if (attachments.isNotEmpty()) {
            onAddPendingParts(attachments.map(MessageAttachment::toChatMessagePart))
        }
    }

    val avatarPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        localState.setDraftUserAvatarUri(uri.toString())
        localState.setDraftUserAvatarUrl("")
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        handlePickedAttachment(uri, AttachmentType.FILE)
    }
    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val markdown = buildConversationMarkdown(
            title = uiState.currentConversationTitle,
            messages = uiState.messages,
            options = localState.exportOptions,
        )
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(markdown.toByteArray(Charsets.UTF_8))
            } ?: error(resources.getString(R.string.chat_error_write_export))
        }.onSuccess {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.chat_export_msg_md_success))
            }
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    throwable.message ?: resources.getString(R.string.chat_error_export_md_fail),
                )
            }
        }
    }

    val hasBaseCredentials = uiState.settings.hasBaseCredentials()
    val hasRequiredConfig = uiState.settings.hasRequiredConfig()
    val currentModelAbilities = remember(activeProvider, currentModel) {
        activeProvider?.resolveModelAbilities(currentModel) ?: inferModelAbilities(currentModel)
    }
    val currentModelSupportsReasoning = remember(currentModelAbilities, currentModel) {
        currentModel.isNotBlank() &&
            ModelAbility.REASONING in currentModelAbilities
    }
    val currentModelSupportsVision = remember(currentModelAbilities, currentModel) {
        currentModel.isNotBlank() &&
            ModelAbility.VISION in currentModelAbilities
    }
    val currentModelIsImageGeneration = remember(uiState.settings, currentModel) {
        ChatConversationSupport.supportsImageGeneration(uiState.settings, currentModel)
    }
    val canAttachImages = remember(currentModelSupportsVision, currentModelIsImageGeneration) {
        currentModelSupportsVision && !currentModelIsImageGeneration
    }
    val canAttachFiles = remember(currentModelIsImageGeneration) {
        !currentModelIsImageGeneration
    }
    val canUseSpecialPlay = remember(currentModelIsImageGeneration) {
        !currentModelIsImageGeneration
    }
    val canAdjustThinkingBudget = remember(activeProvider, currentModel) {
        activeProvider?.let { supportsThinkingBudgetControl(it, currentModel) } == true
    }
    val selectedSearchSource = remember(uiState.settings) {
        uiState.settings.resolvedSearchSettings().selectedSourceOrNull()
    }
    val selectedSearchProvider = remember(uiState.settings, selectedSearchSource) {
        selectedSearchSource?.let(uiState.settings::resolveSearchSourceProvider)
    }
    val normalizedSearchSettings = remember(uiState.settings) {
        uiState.settings.resolvedSearchSettings()
    }
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
    val searchUnavailableMessage =
        when {
            currentConversation == null -> resources.getString(R.string.chat_search_unavail_no_conversation)
            !hasRequiredConfig -> resources.getString(R.string.chat_search_unavail_no_config)
            currentModel.isBlank() -> resources.getString(R.string.chat_search_unavail_no_model)
            currentModelIsImageGeneration -> resources.getString(R.string.chat_search_unavail_image_model)
            ModelAbility.TOOL !in currentModelAbilities -> resources.getString(R.string.chat_search_unavail_no_tool)
            !uiState.settings.hasConfiguredSearchSource() -> {
                if (selectedSearchSource?.type == com.example.myapplication.model.SearchSourceType.LLM_SEARCH) {
                    when {
                        selectedSearchSource.providerId.isBlank() -> resources.getString(R.string.chat_search_unavail_no_provider)
                        selectedSearchProvider == null -> resources.getString(R.string.chat_search_unavail_provider_disabled)
                        !selectedSearchProvider.supportsLlmSearchSource() -> {
                            resources.getString(R.string.chat_search_unavail_need_responses)
                        }
                        selectedSearchProvider.resolveFunctionModel(com.example.myapplication.model.ProviderFunction.SEARCH).isBlank() -> {
                            resources.getString(R.string.chat_search_unavail_model_off)
                        }
                        selectedSearchProvider.resolveFunctionModelMode(com.example.myapplication.model.ProviderFunction.SEARCH) ==
                            com.example.myapplication.model.ProviderFunctionModelMode.FOLLOW_DEFAULT -> {
                            resources.getString(R.string.chat_search_unavail_follow_chat)
                        }
                        else -> resources.getString(R.string.chat_search_unavail_enable_source)
                    }
                } else {
                    resources.getString(R.string.chat_search_unavail_configure_source)
                }
            }
            else -> resources.getString(R.string.chat_search_unavail_unknown)
        }
    val messageMarkdownExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri ->
        val payload = localState.pendingMessageExport
        if (uri == null || payload == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(payload.markdown.toByteArray(Charsets.UTF_8))
            } ?: error(resources.getString(R.string.chat_error_write_export))
        }.onSuccess {
            scope.launch {
                snackbarHostState.showSnackbar(resources.getString(R.string.chat_export_msg_md_success))
            }
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    throwable.message ?: resources.getString(R.string.chat_error_export_msg_md_fail),
                )
            }
        }
        localState.setPendingMessageExport(null)
    }
    val reasoningBudgetHint = remember(activeProvider, currentModel) {
        activeProvider?.let { reasoningBudgetSupportHint(it, currentModel) }.orEmpty()
    }
    val reasoningActionLabel =
        if (canAdjustThinkingBudget) {
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
    val isNearBottom = rememberIsListNearBottom(listState)

    ChatAutoFollowEffects(
        state = autoFollowState,
        listState = listState,
        displayedConversationId = uiState.displayedConversationId,
        messageCount = uiState.messages.size,
        isSending = uiState.isSending,
        isNearBottom = isNearBottom,
        lastMessageContentLength = lastMessageContentLength,
        lastReasoningContentLength = lastReasoningContentLength,
        lastMessagePartCount = lastMessagePartCount,
        lastMessageId = lastMessage?.id,
        lastMessageStatus = lastMessage?.status,
    )

    ChatFeedbackEffects(
        snackbarHostState = snackbarHostState,
        errorMessage = uiState.errorMessage,
        noticeMessage = uiState.noticeMessage,
        currentModelSupportsReasoning = currentModelSupportsReasoning,
        onClearErrorMessage = onClearErrorMessage,
        onClearNoticeMessage = onClearNoticeMessage,
        onHideReasoningSheet = { localState.setShowReasoningSheet(false) },
    )

    ChatScreenChrome(
        uiState = uiState,
        drawerState = drawerState,
        snackbarHostState = snackbarHostState,
        currentProviderName = activeProvider?.name.orEmpty(),
        currentModel = currentModel,
        userDisplayName = userDisplayName,
        userAvatarUri = userAvatarUri,
        userAvatarUrl = userAvatarUrl,
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
                hasBaseCredentials = hasBaseCredentials,
                hasRequiredConfig = hasRequiredConfig,
                currentModelIsImageGeneration = currentModelIsImageGeneration,
                availableModelInfos = availableModelInfos,
                listState = listState,
                isNearBottom = isNearBottom,
                shouldAutoFollowStreaming = autoFollowState.shouldAutoFollowStreaming,
                performanceMode = chatMessagePerformanceMode,
                isSavingModel = isSavingModel,
                currentModel = currentModel,
                canAttachImages = canAttachImages,
                canAttachFiles = canAttachFiles,
                canUseSpecialPlay = canUseSpecialPlay,
                currentModelSupportsReasoning = currentModelSupportsReasoning,
                searchEnabled = searchEnabled,
                searchAvailable = searchAvailable,
                reasoningActionLabel = reasoningActionLabel,
                currentAssistantName = currentAssistantName,
                searchSettings = normalizedSearchSettings,
                selectedSearchSource = selectedSearchSource,
                selectedSearchProvider = selectedSearchProvider,
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
                        snackbarHostState.showSnackbar(searchUnavailableMessage)
                    }
                },
                onOpenSearchPicker = { localState.setShowSearchPickerSheet(true) },
                onTranslateDraft = onTranslateDraft,
                onPickImageClick = {
                    imagePickerLauncher.launch(arrayOf("image/*"))
                },
                onPickFileClick = {
                    filePickerLauncher.launch(arrayOf("*/*"))
                },
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
        onPickLocalAvatar = {
            avatarPickerLauncher.launch(arrayOf("image/*"))
        },
        onClearAvatar = {
            localState.setDraftUserAvatarUri("")
            localState.setDraftUserAvatarUrl("")
        },
        onDismissProfileSheet = { localState.setShowProfileSheet(false) },
        onSaveProfile = ::saveProfileDraft,
        showSpecialPlaySheet = localState.showSpecialPlaySheet,
        onDismissSpecialPlaySheet = { localState.setShowSpecialPlaySheet(false) },
        onOpenSpecialPlayEditor = { type ->
            localState.setShowSpecialPlaySheet(false)
            primeSpecialPlayDraft(type)
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
                resetSpecialPlayDraft(activeDraft.type)
                localState.setActiveSpecialPlayType(null)
            }
        },
        showModelSheet = localState.showModelSheet,
        providerOptions = providerOptions,
        currentProviderId = currentProviderId,
        currentModel = currentModel,
        isLoadingModels = isLoadingModels,
        loadingProviderId = loadingProviderId,
        isSavingModel = isSavingModel,
        onDismissModelSheet = { localState.setShowModelSheet(false) },
        onSelectProvider = onSelectProvider,
        onOpenProviderDetail = onOpenProviderDetail,
        onSelectModel = onSelectModel,
        showReasoningSheet = localState.showReasoningSheet,
        activeProvider = activeProvider,
        reasoningBudgetHint = reasoningBudgetHint,
        onDismissReasoningSheet = { localState.setShowReasoningSheet(false) },
        onUpdateThinkingBudget = onUpdateThinkingBudget,
        showSearchPickerSheet = localState.showSearchPickerSheet,
        searchEnabled = searchEnabled,
        searchAvailable = searchAvailable,
        searchSettings = normalizedSearchSettings,
        currentModelIsImageGeneration = currentModelIsImageGeneration,
        currentModelSupportsTools = ModelAbility.TOOL in currentModelAbilities,
        selectedSearchSource = selectedSearchSource,
        selectedSearchProvider = selectedSearchProvider,
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
            exportMarkdownLauncher.launch(
                buildExportFileName(uiState.currentConversationTitle),
            )
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
            messageMarkdownExportLauncher.launch(buildMessageExportFileName(message))
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

