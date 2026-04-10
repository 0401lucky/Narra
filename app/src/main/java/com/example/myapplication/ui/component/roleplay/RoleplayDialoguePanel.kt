package com.example.myapplication.ui.component.roleplay

import com.example.myapplication.ui.component.*

import android.widget.Toast
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
import androidx.compose.foundation.lazy.items
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
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.R
import com.example.myapplication.ui.component.TransferPlayCard

private enum class RoleplayJumpIndicator {
    TOP,
    BOTTOM,
}

@Composable
fun RoleplayDialoguePanel(
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
    onEditUserMessage: (String) -> Unit,
    onOpenSpecialPlay: () -> Unit,
    onConfirmTransferReceipt: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null,
    lineHeightScale: Float = 1.0f,
    onToggleTopBar: () -> Unit = {},
) {
    val colors = rememberImmersiveRoleplayColors(backdropState)
    val storyMessages = messages.filter { it.contentType != RoleplayContentType.SYSTEM }
    val listState = rememberLazyListState()
    LaunchedEffect(
        storyMessages.firstOrNull()?.sourceMessageId,
        storyMessages.firstOrNull()?.createdAt,
    ) {
        if (storyMessages.isNotEmpty()) {
            listState.scrollToItem(storyMessages.lastIndex)
        }
    }
    val shouldStickToBottom by remember(listState, storyMessages.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            if (storyMessages.isEmpty()) {
                true
            } else {
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
            }
        }
    }
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
                    (lastVisibleItem.offset + lastVisibleItem.size) <= layoutInfo.viewportEndOffset + 8
                )
        }
    }
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
    LaunchedEffect(storyMessages.size, storyMessages.lastOrNull()?.content?.length, storyMessages.lastOrNull()?.isStreaming) {
        if (storyMessages.isNotEmpty() && shouldStickToBottom) {
            listState.animateScrollToItem(storyMessages.lastIndex)
        }
    }
    val scrollScope = rememberCoroutineScope()
    Column(
        modifier = modifier.fillMaxWidth()
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
                    .nestedScroll(scrollHintConnection),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(storyMessages, key = { "${it.sourceMessageId}-${it.createdAt}-${it.contentType}-${it.copyText.hashCode()}" }) { message ->
                        RoleplayMessageItem(
                            message = message,
                            colors = colors,
                            backdropState = backdropState,
                            onRetryTurn = onRetryTurn,
                            onEditUserMessage = onEditUserMessage,
                            onConfirmTransferReceipt = onConfirmTransferReceipt,
                            lineHeightScale = lineHeightScale,
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
                                listState.animateScrollToItem(storyMessages.lastIndex)
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
        )
    }
}

