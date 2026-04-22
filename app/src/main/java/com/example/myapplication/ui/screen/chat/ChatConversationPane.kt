package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.ModelInfo
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.ui.component.ChatMessagePerformanceMode
import com.example.myapplication.ui.component.MessageBubble
import com.example.myapplication.ui.component.MessageInputBar
import com.example.myapplication.ui.component.NarraFilledTonalButton
import com.example.myapplication.viewmodel.ChatUiState

private val ChatScreenPadding = 20.dp
private val ChatSectionSpacing = 12.dp
private const val ChatStreamBottomAnchorKey = "stream-bottom-anchor"

@Composable
internal fun ChatConversationPane(
    uiState: ChatUiState,
    hasBaseCredentials: Boolean,
    hasRequiredConfig: Boolean,
    currentModelIsImageGeneration: Boolean,
    availableModelInfos: List<ModelInfo>,
    listState: LazyListState,
    isNearBottom: Boolean,
    shouldAutoFollowStreaming: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    isSavingModel: Boolean,
    currentModel: String,
    canAttachImages: Boolean,
    canAttachFiles: Boolean,
    canUseSpecialPlay: Boolean,
    currentModelSupportsReasoning: Boolean,
    searchEnabled: Boolean,
    searchAvailable: Boolean,
    reasoningActionLabel: String,
    currentAssistantName: String,
    searchSettings: com.example.myapplication.model.SearchSettings,
    selectedSearchSource: com.example.myapplication.model.SearchSourceConfig?,
    selectedSearchProvider: com.example.myapplication.model.ProviderSettings?,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onOpenConversationDrawer: () -> Unit,
    onRetryMessage: (String) -> Unit,
    onOpenMessageActions: (String) -> Unit,
    onOpenImagePreview: (ChatMessage, Int) -> Unit,
    onOpenUrlPreview: (String, String) -> Unit,
    onToggleMemoryMessage: (String) -> Unit,
    onTranslateMessage: (String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onSelectSearchSource: (String) -> Unit,
    onUpdateSearchResultCount: (Int) -> Unit,
    onSearchUnavailable: () -> Unit,
    onOpenSearchPicker: () -> Unit,
    onTranslateDraft: () -> Unit,
    onPickImageClick: () -> Unit,
    onPickFileClick: () -> Unit,
    onOpenModelPicker: () -> Unit,
    onOpenReasoningPicker: () -> Unit,
    onOpenSpecialPlayClick: () -> Unit,
    onRemovePendingPart: (Int) -> Unit,
    onCancelSending: () -> Unit,
    onJumpToBottom: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = ChatScreenPadding),
            verticalArrangement = Arrangement.spacedBy(ChatSectionSpacing),
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

            if (currentModelIsImageGeneration) {
                NoticeCard(
                    title = "当前处于生图模式",
                    body = if (canAttachImages) {
                        "发送文字会直接生成图片，也可以上传参考图做修改。文件和特殊玩法仍不可用；如需普通对话，请切换到聊天模型。"
                    } else {
                        "发送文字会直接生成图片。附件和特殊玩法已临时禁用；如需普通对话，请切换到聊天模型。"
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
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
                        onPrimaryAction = onOpenConversationDrawer,
                    )
                }

                uiState.messages.isEmpty() -> {
                    Spacer(modifier = Modifier.weight(1f))
                }

                else -> {
                    ChatMessageListPane(
                        uiState = uiState,
                        listState = listState,
                        isNearBottom = isNearBottom,
                        shouldAutoFollowStreaming = shouldAutoFollowStreaming,
                        performanceMode = performanceMode,
                        onRetryMessage = onRetryMessage,
                        onOpenMessageActions = onOpenMessageActions,
                        onOpenImagePreview = onOpenImagePreview,
                        onOpenUrlPreview = onOpenUrlPreview,
                        onToggleMemoryMessage = onToggleMemoryMessage,
                        onTranslateMessage = onTranslateMessage,
                        onConfirmTransferReceipt = onConfirmTransferReceipt,
                        onJumpToBottom = onJumpToBottom,
                    )
                }
            }
        }

        ChatSuggestionsRow(
            uiState = uiState,
            onInputChange = onInputChange,
            onSend = onSend,
        )

        val summaryNotice = when (val governance = uiState.contextGovernance) {
            null -> null
            else -> when (governance.summaryState) {
                ContextSummaryState.APPLIED -> {
                    "当前仅发送最近 ${governance.recentWindow} 条消息，旧消息由摘要承接"
                }

                ContextSummaryState.STALE -> "当前上下文偏长，建议刷新摘要"
                else -> null
            }
        }

        if (!summaryNotice.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ChatScreenPadding)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Start,
            ) {
                Text(
                    text = summaryNotice,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        MessageInputBar(
            value = uiState.input,
            onValueChange = onInputChange,
            onSendClick = onSend,
            currentModel = currentModel,
            onOpenModelPicker = onOpenModelPicker,
            showReasoningAction = currentModelSupportsReasoning,
            reasoningLabel = reasoningActionLabel,
            onOpenReasoningPicker = onOpenReasoningPicker,
            searchEnabled = searchEnabled,
            searchAvailable = searchAvailable,
            onOpenSearchPicker = onOpenSearchPicker,
            onSearchUnavailableClick = onSearchUnavailable,
            onTranslateInputClick = onTranslateDraft,
            onPickImageClick = onPickImageClick,
            onPickFileClick = onPickFileClick,
            onOpenSpecialPlayClick = onOpenSpecialPlayClick,
            onRemovePart = onRemovePendingPart,
            pendingParts = uiState.pendingParts,
            enabled = !uiState.isSending && !isSavingModel && hasRequiredConfig && uiState.isConversationReady,
            allowImageAttachments = canAttachImages,
            allowFileAttachments = canAttachFiles,
            allowSpecialPlay = canUseSpecialPlay,
            isSending = uiState.isSending,
            onCancelClick = onCancelSending,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}

@Composable
private fun ColumnScope.ChatMessageListPane(
    uiState: ChatUiState,
    listState: LazyListState,
    isNearBottom: Boolean,
    shouldAutoFollowStreaming: Boolean,
    performanceMode: ChatMessagePerformanceMode,
    onRetryMessage: (String) -> Unit,
    onOpenMessageActions: (String) -> Unit,
    onOpenImagePreview: (ChatMessage, Int) -> Unit,
    onOpenUrlPreview: (String, String) -> Unit,
    onToggleMemoryMessage: (String) -> Unit,
    onTranslateMessage: (String) -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onJumpToBottom: () -> Unit,
) {
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
                    streamingReasoningSteps = if (message.id == uiState.streamingMessageId) {
                        uiState.streamingReasoningSteps
                    } else {
                        null
                    },
                    streamingParts = if (message.id == uiState.streamingMessageId) {
                        uiState.streamingParts
                    } else {
                        null
                    },
                    onRetry = onRetryMessage,
                    onOpenMessageActions = onOpenMessageActions,
                    onOpenImagePreview = onOpenImagePreview,
                    onToggleMemory = onToggleMemoryMessage,
                    isRemembered = message.id in uiState.rememberedMessageIds,
                    onTranslate = onTranslateMessage,
                    onOpenUrlPreview = onOpenUrlPreview,
                    messageTextScale = uiState.settings.messageTextScale,
                    reasoningExpandedByDefault = uiState.settings.reasoningExpandedByDefault,
                    showThinkingContent = uiState.settings.showThinkingContent,
                    autoCollapseThinking = uiState.settings.autoCollapseThinking,
                    autoPreviewImages = uiState.settings.autoPreviewImages,
                    codeBlockAutoWrap = uiState.settings.codeBlockAutoWrap,
                    codeBlockAutoCollapse = uiState.settings.codeBlockAutoCollapse,
                    performanceMode = performanceMode,
                    reduceVisualEffects = performanceMode != ChatMessagePerformanceMode.FULL,
                    onConfirmTransferReceipt = onConfirmTransferReceipt,
                )
            }

            item(key = ChatStreamBottomAnchorKey) {
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 1.dp),
                )
            }
        }

        if (uiState.isSending && !shouldAutoFollowStreaming && !isNearBottom) {
            NarraFilledTonalButton(
                onClick = onJumpToBottom,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 12.dp),
            ) {
                Text("回到底部")
            }
        }
    }
}

@Composable
private fun ChatSuggestionsRow(
    uiState: ChatUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    if (uiState.chatSuggestions.isEmpty() || uiState.isSending) {
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ChatScreenPadding),
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
