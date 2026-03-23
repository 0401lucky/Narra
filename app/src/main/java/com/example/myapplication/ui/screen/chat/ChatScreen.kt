package com.example.myapplication.ui.screen.chat

import com.example.myapplication.ui.component.*

import android.content.Context
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalConfiguration
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_USER_DISPLAY_NAME
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ReasoningBudgetPreset
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.reasoningBudgetSupportHint
import com.example.myapplication.model.resolveReasoningBudgetLabel
import com.example.myapplication.model.supportsThinkingBudgetControl
import com.example.myapplication.model.toChatMessagePart
import com.example.myapplication.ui.component.AssistantAvatar
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

private val ScreenPadding = 20.dp
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
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onCreateConversation: () -> Unit,
    onSelectConversation: (String) -> Unit,
    onClearConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onDeleteCurrentConversation: () -> Unit,
    onClearCurrentConversation: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onToggleMemoryMessage: (String) -> Unit,
    onTranslateDraft: () -> Unit,
    onTranslateMessage: (String) -> Unit,
    onDismissTranslationSheet: () -> Unit,
    onApplyTranslationToInput: (Boolean) -> Unit,
    onSendTranslationAsMessage: () -> Unit,
    onSelectProvider: (String) -> Unit,
    onSelectModel: (String, String) -> Unit,
    onUpdateThinkingBudget: (String, Int?) -> Unit,
    onSaveUserProfile: (String, String, String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHome: () -> Unit,
    onOpenTranslator: () -> Unit,
    onOpenRoleplay: () -> Unit,
    onOpenProviderDetail: (String) -> Unit,
    onClearErrorMessage: () -> Unit,
    onClearNoticeMessage: () -> Unit,
    onCancelSending: () -> Unit = {},
    onAddPendingParts: (List<ChatMessagePart>) -> Unit,
    onRemovePendingPart: (Int) -> Unit,
    onSendTransferPlay: (String, String, String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSelectAssistant: (String) -> Unit = {},
    onOpenAssistantDetail: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val listState = remember(uiState.displayedConversationId) { LazyListState() }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var showReasoningSheet by rememberSaveable { mutableStateOf(false) }
    var showProfileSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showPromptDebugSheet by rememberSaveable { mutableStateOf(false) }
    var showSpecialPlaySheet by rememberSaveable { mutableStateOf(false) }
    var showTransferSheet by rememberSaveable { mutableStateOf(false) }
    var exportOptions by remember { mutableStateOf(ConversationExportOptions()) }
    var transferCounterparty by rememberSaveable { mutableStateOf("") }
    var transferAmount by rememberSaveable { mutableStateOf("") }
    var transferNote by rememberSaveable { mutableStateOf("") }
    var draftUserDisplayName by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUri by rememberSaveable { mutableStateOf("") }
    var draftUserAvatarUrl by rememberSaveable { mutableStateOf("") }
    var shouldAutoFollowStreaming by remember(uiState.displayedConversationId) { mutableStateOf(true) }
    var isProgrammaticScroll by remember(uiState.displayedConversationId) { mutableStateOf(false) }
    var wasSending by remember(uiState.displayedConversationId) { mutableStateOf(false) }
    var pendingCompletionFollow by remember(uiState.displayedConversationId) { mutableStateOf(false) }
    var userDisabledAutoFollow by remember(uiState.displayedConversationId) { mutableStateOf(false) }
    val providerOptions = remember(uiState.settings) {
        uiState.settings.enabledProviders()
    }
    val activeProvider = remember(uiState.settings) {
        uiState.settings.activeProvider()
    }
    val currentProviderId = activeProvider?.id.orEmpty()
    val currentModel = activeProvider?.selectedModel.orEmpty()
    val currentAssistantName = uiState.currentAssistant?.name.orEmpty().ifBlank { "对方" }
    val transferDraft = TransferPlayDraft(
        counterparty = transferCounterparty,
        amount = transferAmount,
        note = transferNote,
    )
    val userDisplayName = uiState.settings.resolvedUserDisplayName()
    val userAvatarUri = uiState.settings.userAvatarUri
    val userAvatarUrl = uiState.settings.userAvatarUrl
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
                    throwable.message ?: "读取附件失败，请稍后重试",
                )
            }
        }
    }

    fun openProfileSheet() {
        draftUserDisplayName = userDisplayName
        draftUserAvatarUri = userAvatarUri
        draftUserAvatarUrl = userAvatarUrl
        showProfileSheet = true
    }

    fun saveProfileDraft() {
        val normalizedName = draftUserDisplayName.trim().ifBlank { DEFAULT_USER_DISPLAY_NAME }
        val normalizedUrl = draftUserAvatarUrl.trim()
        if (normalizedUrl.isNotBlank() && !isSupportedAvatarUrl(normalizedUrl)) {
            scope.launch {
                snackbarHostState.showSnackbar("头像链接仅支持 http 或 https 地址")
            }
            return
        }
        val normalizedUri = if (normalizedUrl.isBlank()) {
            draftUserAvatarUri.trim()
        } else {
            ""
        }
        onSaveUserProfile(normalizedName, normalizedUri, normalizedUrl)
        showProfileSheet = false
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
        draftUserAvatarUri = uri.toString()
        draftUserAvatarUrl = ""
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
            options = exportOptions,
        )
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(markdown.toByteArray(Charsets.UTF_8))
            } ?: error("无法写入导出文件")
        }.onSuccess {
            scope.launch {
                snackbarHostState.showSnackbar("Markdown 已导出")
            }
        }.onFailure { throwable ->
            scope.launch {
                snackbarHostState.showSnackbar(throwable.message ?: "导出 Markdown 失败")
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
    val canAdjustThinkingBudget = remember(activeProvider, currentModel) {
        activeProvider?.let { supportsThinkingBudgetControl(it, currentModel) } == true
    }
    val reasoningBudgetHint = remember(activeProvider, currentModel) {
        activeProvider?.let { reasoningBudgetSupportHint(it, currentModel) }.orEmpty()
    }
    val reasoningActionLabel = remember(activeProvider?.thinkingBudget, canAdjustThinkingBudget) {
        if (canAdjustThinkingBudget) {
            "思考 · ${resolveReasoningBudgetLabel(activeProvider?.thinkingBudget)}"
        } else {
            "思考"
        }
    }

    val lastMessage = uiState.messages.lastOrNull()
    val bottomAnchorIndex = uiState.messages.size
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
    val isNearBottom by remember(listState) {
        derivedStateOf {
            isListNearBottom(listState)
        }
    }
    var lastStreamFollowAtMillis by remember(uiState.displayedConversationId) { mutableLongStateOf(0L) }

    suspend fun scrollToConversationEnd(animated: Boolean) {
        isProgrammaticScroll = true
        try {
            if (animated) {
                listState.animateScrollToItem(bottomAnchorIndex)
            } else {
                listState.scrollToItem(bottomAnchorIndex)
            }
        } finally {
            isProgrammaticScroll = false
        }
    }

    suspend fun keepConversationEndInView() {
        withFrameNanos { }
        isProgrammaticScroll = true
        try {
            val delta = conversationEndDeltaPx(listState)
            when {
                delta > 0 -> listState.scrollBy(delta.toFloat())
                !isListNearBottom(listState) -> listState.scrollToItem(bottomAnchorIndex)
            }
        } finally {
            isProgrammaticScroll = false
        }
    }

    // 用户拖动离开底部时关闭自动跟随，避免在边界位置来回误触发。
    LaunchedEffect(
        listState.isScrollInProgress,
        isNearBottom,
        isProgrammaticScroll,
        uiState.displayedConversationId,
    ) {
        if (listState.isScrollInProgress &&
            !isProgrammaticScroll &&
            !isNearBottom
        ) {
            shouldAutoFollowStreaming = false
            userDisabledAutoFollow = true
        }
    }

    // 仅在用户停止滚动且重新回到底部后恢复自动跟随，避免边界抖动和回弹。
    LaunchedEffect(
        listState.isScrollInProgress,
        isNearBottom,
        userDisabledAutoFollow,
        isProgrammaticScroll,
        uiState.displayedConversationId,
    ) {
        if (!listState.isScrollInProgress &&
            !isProgrammaticScroll &&
            isNearBottom &&
            userDisabledAutoFollow
        ) {
            shouldAutoFollowStreaming = true
            userDisabledAutoFollow = false
        }
    }

    // 会话切换时滚到底部
    LaunchedEffect(uiState.displayedConversationId) {
        shouldAutoFollowStreaming = true
        userDisabledAutoFollow = false
        if (uiState.messages.isNotEmpty()) {
            scrollToConversationEnd(animated = false)
        }
    }

    // 消息数量变化时（新消息到达）
    LaunchedEffect(uiState.messages.size, uiState.displayedConversationId) {
        if (uiState.messages.isEmpty()) {
            return@LaunchedEffect
        }
        if (!userDisabledAutoFollow && (shouldAutoFollowStreaming || isNearBottom)) {
            if (uiState.isSending) {
                scrollToConversationEnd(animated = false)
            } else {
                scrollToConversationEnd(animated = true)
            }
        }
    }

    // 流式内容增长时跟随滚动（受 userDisabledAutoFollow 门控）
    LaunchedEffect(
        lastMessageContentLength,
        lastReasoningContentLength,
        lastMessagePartCount,
        uiState.isSending,
        uiState.displayedConversationId,
    ) {
        if (!uiState.isSending || uiState.messages.isEmpty()) {
            return@LaunchedEffect
        }
        if (userDisabledAutoFollow) {
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        if (shouldAutoFollowStreaming &&
            now - lastStreamFollowAtMillis >= StreamFollowScrollWindowMillis
        ) {
            lastStreamFollowAtMillis = now
            keepConversationEndInView()
        }
    }

    // 发送结束后的最终滚动
    LaunchedEffect(uiState.isSending, uiState.displayedConversationId) {
        if (wasSending && !uiState.isSending) {
            if (!userDisabledAutoFollow && (shouldAutoFollowStreaming || isNearBottom)) {
                pendingCompletionFollow = true
            }
            userDisabledAutoFollow = false
        }
        wasSending = uiState.isSending
    }

    LaunchedEffect(
        pendingCompletionFollow,
        uiState.isSending,
        lastMessage?.id,
        lastMessage?.status,
        uiState.displayedConversationId,
    ) {
        if (!pendingCompletionFollow || uiState.isSending || uiState.messages.isEmpty()) {
            return@LaunchedEffect
        }

        repeat(4) {
            withFrameNanos { }
            scrollToConversationEnd(animated = false)
        }
        shouldAutoFollowStreaming = true
        pendingCompletionFollow = false
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearErrorMessage()
        }
    }

    LaunchedEffect(uiState.noticeMessage) {
        uiState.noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearNoticeMessage()
        }
    }

    LaunchedEffect(currentModelSupportsReasoning) {
        if (!currentModelSupportsReasoning) {
            showReasoningSheet = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ConversationDrawerContent(
                conversations = uiState.conversations,
                currentConversationId = uiState.currentConversationId,
                currentProviderName = activeProvider?.name.orEmpty(),
                currentModel = currentModel,
                userDisplayName = userDisplayName,
                userAvatarUri = userAvatarUri,
                userAvatarUrl = userAvatarUrl,
                currentAssistant = uiState.currentAssistant,
                assistants = uiState.settings.resolvedAssistants(),
                onSelectAssistant = { assistantId ->
                    onSelectAssistant(assistantId)
                },
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
                onEditProfile = ::openProfileSheet,
            )
        },
    ) {
        Scaffold(
            topBar = {
                ChatTopBar(
                    title = uiState.currentConversationTitle,
                    onOpenConversationDrawer = {
                        scope.launch { drawerState.open() }
                    },
                    onOpenPromptDebugSheet = { showPromptDebugSheet = true },
                    onOpenExportSheet = { showExportSheet = true },
                    onCreateConversation = onCreateConversation,
                )
            },
            snackbarHost = {
                AppSnackbarHost(hostState = snackbarHostState)
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = ScreenPadding),
                    verticalArrangement = Arrangement.spacedBy(SectionSpacing),
                ) {
                    if (!hasRequiredConfig) {
                        NoticeCard(
                            title = if (hasBaseCredentials) "还缺少模型配置" else "聊天配置尚未完成",
                            body = if (hasBaseCredentials) {
                                "当前已经保存基础连接信息，请先刷新或选择模型，完成后即可在底部输入栏直接发起对话。"
                            } else {
                                "请先进入设置页保存 Base URL、API Key 和模型配置。保存成功后，聊天页会自动恢复发送能力。"
                            },
                            containerColor = if (hasBaseCredentials) {
                                MaterialTheme.colorScheme.tertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                            contentColor = if (hasBaseCredentials) {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                        )
                    }

                    if (availableModelInfos.isEmpty() && hasBaseCredentials) {
                        NoticeCard(
                            title = "暂无可用模型",
                            body = "可以点击顶栏模型名称打开模型选择面板刷新，或进入设置页检查当前连接与权限配置。",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }

                    when {
                        !uiState.isConversationReady && hasRequiredConfig -> {
                            EmptyConversationState(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                title = "正在切换会话",
                                body = "新的会话内容正在加载，加载完成后会恢复发送与滚动。",
                                primaryActionLabel = "打开历史记录",
                                onPrimaryAction = {
                                    scope.launch { drawerState.open() }
                                },
                            )
                        }

                        uiState.messages.isEmpty() -> {
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        else -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            ) {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 8.dp),
                                ) {
                                    items(
                                        items = uiState.messages,
                                        key = { it.id },
                                        contentType = { messageContentType(it) },
                                    ) { message ->
                                        MessageBubble(
                                            message = message,
                                            streamingContent = if (message.id == uiState.streamingMessageId) {
                                                uiState.streamingContent
                                            } else {
                                                null
                                            },
                                            streamingReasoningContent = if (message.id == uiState.streamingMessageId) {
                                                uiState.streamingReasoningContent
                                            } else {
                                                null
                                            },
                                            streamingParts = if (message.id == uiState.streamingMessageId) {
                                                uiState.streamingParts
                                            } else {
                                                null
                                            },
                                            onRetry = onRetryMessage,
                                            onToggleMemory = onToggleMemoryMessage,
                                            isRemembered = message.id in uiState.rememberedMessageIds,
                                            onTranslate = onTranslateMessage,
                                            messageTextScale = uiState.settings.messageTextScale,
                                            reasoningExpandedByDefault = uiState.settings.reasoningExpandedByDefault,
                                            showThinkingContent = uiState.settings.showThinkingContent,
                                            autoCollapseThinking = uiState.settings.autoCollapseThinking,
                                            autoPreviewImages = uiState.settings.autoPreviewImages,
                                            codeBlockAutoWrap = uiState.settings.codeBlockAutoWrap,
                                            codeBlockAutoCollapse = uiState.settings.codeBlockAutoCollapse,
                                            reduceVisualEffects = listState.isScrollInProgress,
                                            onConfirmTransferReceipt = onConfirmTransferReceipt,
                                        )
                                    }

                                    item(key = StreamBottomAnchorKey) {
                                        Spacer(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 1.dp),
                                        )
                                    }
                                }

                                if (uiState.isSending && !shouldAutoFollowStreaming && !isNearBottom) {
                                    NarraFilledTonalButton(
                                        onClick = {
                                            scope.launch {
                                                userDisabledAutoFollow = false
                                                shouldAutoFollowStreaming = true
                                                scrollToConversationEnd(animated = true)
                                            }
                                        },
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(bottom = 12.dp),
                                    ) {
                                        Text("回到底部")
                                    }
                                }
                            }
                        }
                    }
                }

                if (uiState.chatSuggestions.isNotEmpty() && !uiState.isSending) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = ScreenPadding),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = buildString {
                                append("建议")
                                if (uiState.chatSuggestionsModelName.isNotBlank()) {
                                    append(" · ")
                                    append(uiState.chatSuggestionsModelName)
                                }
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(
                                items = uiState.chatSuggestions,
                                key = { it },
                            ) { suggestion ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        onInputChange(suggestion)
                                        onSend()
                                    },
                                    label = {
                                        Text(
                                            text = suggestion,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }

                MessageInputBar(
                    value = uiState.input,
                    onValueChange = onInputChange,
                    onSendClick = onSend,
                    currentModel = currentModel,
                    onOpenModelPicker = { showModelSheet = true },
                    showReasoningAction = currentModelSupportsReasoning,
                    reasoningLabel = reasoningActionLabel,
                    onOpenReasoningPicker = { showReasoningSheet = true },
                    onTranslateInputClick = onTranslateDraft,
                    onPickImageClick = {
                        imagePickerLauncher.launch(arrayOf("image/*"))
                    },
                    onPickFileClick = {
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onOpenSpecialPlayClick = {
                        if (transferCounterparty.isBlank()) {
                            transferCounterparty = currentAssistantName
                        }
                        showSpecialPlaySheet = true
                    },
                    onRemovePart = onRemovePendingPart,
                    pendingParts = uiState.pendingParts,
                    enabled = !uiState.isSending && !isSavingModel && hasRequiredConfig && uiState.isConversationReady,
                    isSending = uiState.isSending,
                    onCancelClick = onCancelSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                )
            }
        }
    }

    if (showProfileSheet) {
        ProfileEditorSheet(
            displayName = draftUserDisplayName,
            avatarUri = draftUserAvatarUri,
            avatarUrl = draftUserAvatarUrl,
            onDisplayNameChange = { draftUserDisplayName = it },
            onAvatarUrlChange = {
                draftUserAvatarUrl = it
                if (it.isNotBlank()) {
                    draftUserAvatarUri = ""
                }
            },
            onPickLocalAvatar = {
                avatarPickerLauncher.launch(arrayOf("image/*"))
            },
            onClearAvatar = {
                draftUserAvatarUri = ""
                draftUserAvatarUrl = ""
            },
            onDismissRequest = { showProfileSheet = false },
            onSave = ::saveProfileDraft,
        )
    }

    if (showSpecialPlaySheet) {
        SpecialPlaySheet(
            onDismissRequest = { showSpecialPlaySheet = false },
            onOpenTransfer = {
                showSpecialPlaySheet = false
                if (transferCounterparty.isBlank()) {
                    transferCounterparty = currentAssistantName
                }
                showTransferSheet = true
            },
        )
    }

    if (showTransferSheet) {
        TransferPlaySheet(
            draft = transferDraft,
            onDraftChange = {
                transferCounterparty = it.counterparty
                transferAmount = it.amount
                transferNote = it.note
            },
            onDismissRequest = { showTransferSheet = false },
            onConfirm = {
                onSendTransferPlay(
                    transferDraft.counterparty.ifBlank { currentAssistantName },
                    transferDraft.amount,
                    transferDraft.note,
                )
                transferCounterparty = currentAssistantName
                transferAmount = ""
                transferNote = ""
                showTransferSheet = false
            },
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
            onDismissRequest = { showModelSheet = false },
            onSelectProvider = onSelectProvider,
            onOpenProviderDetail = onOpenProviderDetail,
            onSelectModel = { providerId, model ->
                onSelectModel(providerId, model)
                showModelSheet = false
            },
        )
    }

    if (showReasoningSheet) {
        ReasoningBudgetSheet(
            provider = activeProvider,
            currentModel = currentModel,
            isSavingModel = isSavingModel,
            reasoningBudgetHint = reasoningBudgetHint,
            onDismissRequest = { showReasoningSheet = false },
            onUpdateThinkingBudget = { providerId, budget ->
                onUpdateThinkingBudget(providerId, budget)
                showReasoningSheet = false
            },
        )
    }

    if (showPromptDebugSheet) {
        PromptDebugSheet(
            debugDump = uiState.latestPromptDebugDump,
            onDismissRequest = { showPromptDebugSheet = false },
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
            onDismissRequest = { showExportSheet = false },
            onUpdateOptions = { exportOptions = it },
            onExportMarkdown = {
                exportMarkdownLauncher.launch(
                    buildExportFileName(uiState.currentConversationTitle),
                )
                showExportSheet = false
            },
            onCopyPlainText = {
                val plainText = buildConversationPlainText(
                    title = uiState.currentConversationTitle,
                    messages = uiState.messages,
                    options = exportOptions,
                )
                clipboardManager.setText(AnnotatedString(plainText))
                scope.launch {
                    snackbarHostState.showSnackbar("会话纯文本已复制")
                }
                showExportSheet = false
            },
            onShareConversation = {
                val shareText = buildConversationMarkdown(
                    title = uiState.currentConversationTitle,
                    messages = uiState.messages,
                    options = exportOptions,
                )
                context.startActivity(
                    Intent.createChooser(
                        buildShareIntent(shareText),
                        "分享会话",
                    ),
                )
                showExportSheet = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatTopBar(
    title: String,
    onOpenConversationDrawer: () -> Unit,
    onOpenPromptDebugSheet: () -> Unit,
    onOpenExportSheet: () -> Unit,
    onCreateConversation: () -> Unit,
) {
    TopAppBar(
        navigationIcon = {
            NarraIconButton(onClick = onOpenConversationDrawer) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "历史记录",
                )
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            NarraIconButton(onClick = onOpenPromptDebugSheet) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = "查看上下文调试信息",
                )
            }
            NarraIconButton(onClick = onOpenExportSheet) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "导出与分享",
                )
            }
            NarraIconButton(onClick = onCreateConversation) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "新建会话",
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromptDebugSheet(
    debugDump: String,
    onDismissRequest: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = 20.dp,
                top = 8.dp,
                end = 20.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = "本轮上下文",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                Text(
                    text = debugDump.ifBlank { "当前还没有可展示的上下文调试信息。发送一条消息后再查看这里。" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun buildExportFileName(title: String): String {
    val normalized = title.trim()
        .ifBlank { "conversation" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "-")
    return "$normalized.md"
}

private fun resolveSelectedAttachment(
    context: Context,
    uri: Uri,
    type: AttachmentType,
): MessageAttachment {
    val displayName = resolveDisplayName(context, uri).ifBlank {
        if (type == AttachmentType.IMAGE) "已选图片" else "已选文件"
    }
    val mimeType = context.contentResolver.getType(uri).orEmpty().ifBlank {
        if (type == AttachmentType.IMAGE) "image/*" else "text/plain"
    }
    if (type == AttachmentType.FILE) {
        requireLikelySupportedTextFile(displayName, mimeType)
    }

    return MessageAttachment(
        type = type,
        uri = uri.toString(),
        mimeType = mimeType,
        fileName = displayName,
    )
}

private fun resolveDisplayName(
    context: Context,
    uri: Uri,
): String {
    return context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            cursor.getString(nameIndex)
        } else {
            null
        }
    }.orEmpty()
}

private fun requireLikelySupportedTextFile(
    fileName: String,
    mimeType: String,
) {
    val normalizedName = fileName.lowercase()
    val normalizedMimeType = mimeType.lowercase()
    if (normalizedMimeType.startsWith("image/")) {
        throw IllegalStateException("图片请使用图片上传入口")
    }
    if (normalizedName.endsWith(".pdf") ||
        normalizedName.endsWith(".doc") ||
        normalizedName.endsWith(".docx") ||
        normalizedName.endsWith(".xls") ||
        normalizedName.endsWith(".xlsx") ||
        normalizedName.endsWith(".ppt") ||
        normalizedName.endsWith(".pptx") ||
        normalizedName.endsWith(".zip") ||
        normalizedName.endsWith(".rar") ||
        normalizedName.endsWith(".7z") ||
        normalizedName.endsWith(".apk")
    ) {
        throw IllegalStateException("当前仅支持上传文本类文件和图片")
    }
    if (normalizedMimeType == "application/pdf" ||
        normalizedMimeType.contains("word") ||
        normalizedMimeType.contains("excel") ||
        normalizedMimeType.contains("spreadsheet") ||
        normalizedMimeType.contains("presentation") ||
        normalizedMimeType.contains("zip")
    ) {
        throw IllegalStateException("当前仅支持上传文本类文件和图片")
    }
}

private fun isSupportedAvatarUrl(url: String): Boolean {
    return url.startsWith("https://") || url.startsWith("http://")
}

private fun messageContentType(message: com.example.myapplication.model.ChatMessage): String {
    return when {
        message.role == com.example.myapplication.model.MessageRole.USER &&
            message.parts.any { it.type != com.example.myapplication.model.ChatMessagePartType.TEXT } ->
            "user-rich"
        message.role == com.example.myapplication.model.MessageRole.USER ->
            "user-text"
        message.status == com.example.myapplication.model.MessageStatus.LOADING ->
            "assistant-loading"
        message.status == com.example.myapplication.model.MessageStatus.ERROR ->
            "assistant-error"
        message.reasoningContent.isNotBlank() ->
            "assistant-reasoning"
        message.parts.any { it.type != com.example.myapplication.model.ChatMessagePartType.TEXT } ||
            message.attachments.isNotEmpty() ->
            "assistant-rich"
        else ->
            "assistant-text"
    }
}

internal data class ChatListMeasuredItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun isListNearBottom(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<ChatListMeasuredItem>,
    tolerancePx: Int = BottomFollowTolerancePx,
): Boolean {
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return true
    }

    // 列表末尾固定追加了一个极小的底部锚点，因此需要把“最后一条消息”也视作有效边界。
    val boundaryStartIndex = (totalItems - 2).coerceAtLeast(0)
    val boundaryItem = visibleItems.lastOrNull { it.index >= boundaryStartIndex } ?: return false
    val distance = abs(boundaryItem.offset + boundaryItem.size - viewportEndOffset)
    return distance <= tolerancePx
}

internal fun conversationEndDeltaPx(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<ChatListMeasuredItem>,
): Int {
    if (totalItems == 0 || visibleItems.isEmpty()) {
        return 0
    }

    val boundaryStartIndex = (totalItems - 2).coerceAtLeast(0)
    val boundaryItem = visibleItems.lastOrNull { it.index >= boundaryStartIndex } ?: return 0
    return (boundaryItem.offset + boundaryItem.size - viewportEndOffset).coerceAtLeast(0)
}

private fun LazyListState.visibleMeasuredItems(): List<ChatListMeasuredItem> {
    return layoutInfo.visibleItemsInfo.map { item ->
        ChatListMeasuredItem(
            index = item.index,
            offset = item.offset,
            size = item.size,
        )
    }
}

private fun isListNearBottom(
    listState: LazyListState,
): Boolean {
    val layoutInfo = listState.layoutInfo
    return isListNearBottom(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleMeasuredItems(),
    )
}

private fun conversationEndDeltaPx(
    listState: LazyListState,
): Int {
    val layoutInfo = listState.layoutInfo
    return conversationEndDeltaPx(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleMeasuredItems(),
    )
}
