package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.ui.component.*

import android.widget.Toast
import com.example.myapplication.BuildConfig
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.model.MessageStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.myapplication.model.RoleplayContentType
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayNoBackgroundSkinSettings
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.R
import com.example.myapplication.system.performance.BenchmarkActionIds
import com.example.myapplication.system.performance.BenchmarkOfflineReplaySpec
import com.example.myapplication.system.performance.BenchmarkReadyIds
import com.example.myapplication.system.performance.BenchmarkContract
import com.example.myapplication.system.performance.BenchmarkScenarioId
import com.example.myapplication.ui.performance.LocalBenchmarkRuntimePolicy
import com.example.myapplication.ui.performance.benchmarkActionTag
import com.example.myapplication.ui.performance.benchmarkReadyTag
import com.example.myapplication.ui.component.TransferPlayCard
import com.example.myapplication.viewmodel.RoleplayOfflineReplayPhase

private enum class RoleplayJumpIndicator {
    TOP,
    BOTTOM,
}

@Composable
internal fun RoleplayDialoguePanel(
    conversationIdentity: String,
    backdropState: ImmersiveBackdropState,
    messages: List<RoleplayMessageUiModel>,
    suggestions: List<RoleplaySuggestionUiModel>,
    isGeneratingSuggestions: Boolean,
    suggestionErrorMessage: String?,
    showAiHelper: Boolean,
    input: String,
    inputFocusToken: Long,
    isSending: Boolean,
    onInputChange: (String) -> Unit,
    onGenerateSuggestions: () -> Unit,
    onApplySuggestion: (String) -> Unit,
    onClearSuggestions: () -> Unit,
    onRetryTurn: (String) -> Unit,
    onRetryAiPhoto: (String, String) -> Unit,
    onEditUserMessage: (String) -> Unit,
    onDeleteMessage: ((String) -> Unit)? = null,
    onOpenSpecialPlay: () -> Unit,
    quickActions: List<RoleplayInputQuickAction> = emptyList(),
    pendingImageParts: List<ChatMessagePart> = emptyList(),
    onPickImage: (() -> Unit)? = null,
    onRemovePendingImagePart: (ChatMessagePart) -> Unit = {},
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    offlineReplayPhase: RoleplayOfflineReplayPhase = RoleplayOfflineReplayPhase.DISABLED,
    isOfflineReplayFixtureReady: Boolean = false,
    onStartOfflineReplay: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    noBackgroundSkin: RoleplayNoBackgroundSkinSettings = RoleplayNoBackgroundSkinSettings(),
    onToggleTopBar: () -> Unit = {},
) {
    val colors = rememberImmersiveRoleplayColors(backdropState, noBackgroundSkin)
    val storyMessages = messages.filter { it.contentType != RoleplayContentType.SYSTEM }
    val listState = rememberLazyListState()
    val autoScrollState = rememberRoleplayStreamingAutoScrollState(conversationIdentity)
    val lastStoryMessage = storyMessages.lastOrNull()
    RoleplayStreamingAutoScrollEffect(
        state = autoScrollState,
        listState = listState,
        conversationIdentity = conversationIdentity,
        signal = RoleplayStreamingScrollSignal(
            lastUiItemId = lastStoryMessage?.uiItemId,
            itemCount = storyMessages.size,
            isStreaming = lastStoryMessage?.isStreaming == true,
            contentVersion = lastStoryMessage?.content?.length ?: 0,
        ),
    )
    val offlineReplaySpec = BenchmarkOfflineReplaySpec.DEFAULT
    val isOfflineReplayActionInstalled =
        isOfflineReplayFixtureReady &&
            offlineReplayPhase == RoleplayOfflineReplayPhase.READY &&
            onStartOfflineReplay != null
    val benchmarkRuntimePolicy = if (
        BuildConfig.APP_CHANNEL == BenchmarkContract.BASELINE_CHANNEL
    ) {
        LocalBenchmarkRuntimePolicy.current
    } else {
        null
    }
    val benchmarkSourceMessageCount = if (
        benchmarkRuntimePolicy?.scenarioId == BenchmarkScenarioId.F2_ROLEPLAY_SCROLL
    ) {
        storyMessages.asSequence()
            .map { message -> message.sourceMessageId }
            .filter(String::isNotBlank)
            .distinct()
            .count()
    } else {
        0
    }
    val isRoleplayScrollFixtureReady =
        benchmarkRuntimePolicy?.scenarioId == BenchmarkScenarioId.F2_ROLEPLAY_SCROLL &&
            benchmarkSourceMessageCount ==
            BenchmarkScenarioId.F2_ROLEPLAY_SCROLL.fixtureId.roleplayMessageCount
    val isAtTop by remember(listState, storyMessages.size) {
        derivedStateOf {
            storyMessages.isEmpty() ||
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset <= 8)
        }
    }
    val isAtBottomExact by remember(listState, storyMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
            storyMessages.isEmpty() || (
                lastVisibleItem != null &&
                    lastVisibleItem.index >= layoutInfo.totalItemsCount - 1 &&
                    !listState.canScrollForward &&
                    (lastVisibleItem.offset + lastVisibleItem.size) <= layoutInfo.viewportEndOffset + 8
                )
        }
    }
    val isLastStoryMessageVisible by remember(listState, storyMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastIndex = storyMessages.lastIndex
            lastIndex >= 0 && layoutInfo.visibleItemsInfo.any { item ->
                item.index == lastIndex &&
                    item.offset < layoutInfo.viewportEndOffset &&
                    item.offset + item.size > layoutInfo.viewportStartOffset
            }
        }
    }
    val isOfflineReplayViewportReady = isRoleplayOfflineReplayViewportReady(
        isLastStoryMessageVisible = isLastStoryMessageVisible,
        canScrollForward = listState.canScrollForward,
        isScrollInProgress = listState.isScrollInProgress,
    )
    val isOfflineReplayInitialReady =
        isOfflineReplayActionInstalled &&
            lastStoryMessage?.isStreaming == true &&
            lastStoryMessage.messageStatus == MessageStatus.LOADING &&
            offlineReplaySpec.isInitialContent(lastStoryMessage.content) &&
            isOfflineReplayViewportReady
    val isOfflineReplayCompleteReady =
        isOfflineReplayFixtureReady &&
            offlineReplayPhase == RoleplayOfflineReplayPhase.COMPLETED &&
            !isOfflineReplayActionInstalled &&
            // 当前观察层在回放完成后仍保留末尾流式预览，最终状态由后续收口统一清理。
            lastStoryMessage?.isStreaming == true &&
            lastStoryMessage.messageStatus == MessageStatus.LOADING &&
            offlineReplaySpec.isCompletedContent(lastStoryMessage.content) &&
            isOfflineReplayViewportReady
    var jumpIndicator by remember(listState, storyMessages.size) {
        mutableStateOf<RoleplayJumpIndicator?>(null)
    }
    LaunchedEffect(storyMessages.firstOrNull()?.sourceMessageId, storyMessages.firstOrNull()?.createdAt, storyMessages.size) {
        jumpIndicator = null
    }
    val scrollHintConnection = remember(storyMessages.size, isAtTop, isAtBottomExact) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && storyMessages.isNotEmpty()) {
                    autoScrollState.pauseForUser()
                    jumpIndicator = when {
                        available.y > 1f && !isAtTop -> RoleplayJumpIndicator.TOP
                        available.y < -1f && !isAtBottomExact -> RoleplayJumpIndicator.BOTTOM
                        else -> jumpIndicator
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState.isScrollInProgress, jumpIndicator, storyMessages.size) {
        if (!listState.isScrollInProgress && jumpIndicator != null) {
            delay(1200)
            if (!listState.isScrollInProgress) {
                jumpIndicator = null
            }
        }
    }
    LaunchedEffect(isOfflineReplayActionInstalled, lastStoryMessage?.uiItemId) {
        if (isOfflineReplayActionInstalled && storyMessages.isNotEmpty()) {
            // READY 操作条会压缩列表视口；仅在基准控制条安装时重新确定性贴底一次。
            autoScrollState.scrollToEnd(
                listState = listState,
                lastIndex = storyMessages.lastIndex,
                animated = false,
                resumeAutoFollow = true,
            )
        }
    }
    val scrollScope = rememberCoroutineScope()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (storyMessages.isEmpty()) {
            // 空白区域点击切换顶栏，避免手势实现停留在占位状态
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { onToggleTopBar() },
                            onDoubleTap = { onToggleTopBar() },
                        )
                    },
            ) {
                EmptyDialogueState(
                    colors = colors,
                    modifier = Modifier.matchParentSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
                    .nestedScroll(scrollHintConnection)
                    .benchmarkReadyTag(
                        readyId = BenchmarkReadyIds.ROLEPLAY_LIST_TOP_READY,
                        ready = isRoleplayScrollFixtureReady && isAtTop,
                    )
                    .benchmarkReadyTag(
                        readyId = BenchmarkReadyIds.ROLEPLAY_LIST_BOTTOM_READY,
                        ready = isRoleplayScrollFixtureReady && isAtBottomExact,
                    ),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(
                        items = storyMessages,
                        key = { _, message -> message.uiItemId },
                        contentType = { _, message -> roleplayLazyContentType(message) },
                    ) { _, message ->
                        RoleplayMessageItem(
                            message = message,
                            colors = colors,
                            backdropState = backdropState,
                            onRetryTurn = onRetryTurn,
                            onRetryAiPhoto = onRetryAiPhoto,
                            onEditUserMessage = onEditUserMessage,
                            onDeleteMessage = onDeleteMessage,
                            onConfirmTransferReceipt = onConfirmTransferReceipt,
                            lineHeightScale = lineHeightScale,
                            noBackgroundSkin = noBackgroundSkin,
                        )
                    }
                }

                if (jumpIndicator == RoleplayJumpIndicator.TOP && !isAtTop) {
                    NarraIconButton(
                        onClick = {
                            scrollScope.launch {
                                listState.animateScrollToItem(0)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 6.dp)
                            .size(RoleplayInteractiveIconButtonSize),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colors.panelBackgroundStrong,
                            contentColor = colors.textPrimary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowUpward,
                            contentDescription = stringResource(id = R.string.roleplay_jump_to_top),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                if (jumpIndicator == RoleplayJumpIndicator.BOTTOM && !isAtBottomExact) {
                    NarraIconButton(
                        onClick = {
                            scrollScope.launch {
                                autoScrollState.scrollToEnd(
                                    listState = listState,
                                    lastIndex = storyMessages.lastIndex,
                                    animated = true,
                                    resumeAutoFollow = true,
                                )
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 6.dp, bottom = 12.dp)
                            .size(RoleplayInteractiveIconButtonSize),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = colors.characterAccent.copy(alpha = 0.88f),
                            contentColor = Color.Black.copy(alpha = 0.88f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = stringResource(id = R.string.roleplay_jump_to_bottom),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        if (showAiHelper) {
            RoleplaySuggestionSection(
                colors = colors,
                backdropState = backdropState,
                suggestions = suggestions,
                isGeneratingSuggestions = isGeneratingSuggestions,
                suggestionErrorMessage = suggestionErrorMessage,
                isSending = isSending,
                onGenerateSuggestions = onGenerateSuggestions,
                onApplySuggestion = onApplySuggestion,
                onClearSuggestions = onClearSuggestions,
            )
        }
        if (isOfflineReplayActionInstalled || (isSending && onCancel != null)) {
            ImmersiveGlassSurface(
                backdropState = backdropState,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                blurRadius = 16.dp,
                overlayColor = colors.panelBackgroundStrong,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = colors.characterAccent,
                    )
                    Text(
                        text = if (isOfflineReplayActionInstalled) {
                            "离线流式重放已就绪"
                        } else {
                            stringResource(id = R.string.roleplay_sending)
                        },
                        color = colors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .weight(1f)
                            .benchmarkReadyTag(
                                readyId = BenchmarkReadyIds.ROLEPLAY_STREAM_READY,
                                ready = isOfflineReplayInitialReady,
                            )
                            .benchmarkReadyTag(
                                readyId = BenchmarkReadyIds.ROLEPLAY_STREAM_COMPLETE_READY,
                                ready = isOfflineReplayCompleteReady,
                            ),
                    )
                    if (isOfflineReplayActionInstalled) {
                        onStartOfflineReplay?.let { startOfflineReplay ->
                            NarraTextButton(
                                onClick = startOfflineReplay,
                                modifier = Modifier.benchmarkActionTag(
                                    BenchmarkActionIds.START_OFFLINE_REPLAY,
                                ),
                            ) {
                                Text(
                                    text = "开始离线重放",
                                    color = colors.characterAccent,
                                )
                            }
                        }
                    } else {
                        onCancel?.let { cancel ->
                            NarraTextButton(onClick = cancel) {
                                Text(
                                    text = stringResource(id = R.string.common_cancel),
                                    color = colors.errorText,
                                )
                            }
                        }
                    }
                }
            }
        }
        RoleplayInputBar(
            colors = colors,
            backdropState = backdropState,
            input = input,
            inputFocusToken = inputFocusToken,
            isSending = isSending,
            onInputChange = onInputChange,
            onSend = onSend,
            onCancel = onCancel,
            onOpenSpecialPlay = onOpenSpecialPlay,
            quickActions = quickActions,
            pendingImageParts = pendingImageParts,
            onPickImage = onPickImage,
            onRemovePendingImagePart = onRemovePendingImagePart,
        )
    }
}

internal fun isRoleplayOfflineReplayViewportReady(
    isLastStoryMessageVisible: Boolean,
    canScrollForward: Boolean,
    isScrollInProgress: Boolean,
): Boolean {
    return isLastStoryMessageVisible && !canScrollForward && !isScrollInProgress
}

