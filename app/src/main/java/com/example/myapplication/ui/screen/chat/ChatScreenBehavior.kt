package com.example.myapplication.ui.screen.chat

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.platform.LocalDensity

private const val ChatStreamFollowScrollWindowMillis = 64L

internal class ChatAutoFollowState {
    var shouldAutoFollowStreaming by mutableStateOf(true)
    var isProgrammaticScroll by mutableStateOf(false)
    var wasSending by mutableStateOf(false)
    var pendingCompletionFollow by mutableStateOf(false)
    var userDisabledAutoFollow by mutableStateOf(false)
    var lastStreamFollowAtMillis by mutableLongStateOf(0L)

    suspend fun scrollToConversationEnd(
        listState: LazyListState,
        bottomAnchorIndex: Int,
        animated: Boolean,
    ) {
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

    suspend fun keepConversationEndInView(
        listState: LazyListState,
        bottomAnchorIndex: Int,
    ) {
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
}

@Composable
internal fun rememberChatAutoFollowState(
    conversationId: String,
): ChatAutoFollowState {
    return remember(conversationId) { ChatAutoFollowState() }
}

@Composable
internal fun rememberIsListNearBottom(
    listState: LazyListState,
): Boolean {
    val isNearBottom by remember(listState) {
        derivedStateOf {
            isListNearBottom(listState)
        }
    }
    return isNearBottom
}

@Composable
internal fun ChatAutoFollowEffects(
    state: ChatAutoFollowState,
    listState: LazyListState,
    displayedConversationId: String,
    messageCount: Int,
    isSending: Boolean,
    isNearBottom: Boolean,
    lastMessageContentLength: Int?,
    lastReasoningContentLength: Int?,
    lastMessagePartCount: Int,
    lastMessageId: String?,
    lastMessageStatus: com.example.myapplication.model.MessageStatus?,
) {
    val bottomAnchorIndex = messageCount

    LaunchedEffect(
        listState.isScrollInProgress,
        isNearBottom,
        state.isProgrammaticScroll,
        displayedConversationId,
    ) {
        if (listState.isScrollInProgress && !state.isProgrammaticScroll) {
            state.pendingCompletionFollow = false
            state.shouldAutoFollowStreaming = false
            state.userDisabledAutoFollow = true
        }
    }

    LaunchedEffect(
        listState.isScrollInProgress,
        isNearBottom,
        state.userDisabledAutoFollow,
        state.isProgrammaticScroll,
        displayedConversationId,
    ) {
        if (!listState.isScrollInProgress &&
            !state.isProgrammaticScroll &&
            isNearBottom &&
            state.userDisabledAutoFollow
        ) {
            state.shouldAutoFollowStreaming = true
            state.userDisabledAutoFollow = false
        }
    }

    LaunchedEffect(displayedConversationId) {
        state.shouldAutoFollowStreaming = true
        state.userDisabledAutoFollow = false
        if (messageCount > 0) {
            state.scrollToConversationEnd(listState, bottomAnchorIndex, animated = false)
        }
    }

    LaunchedEffect(messageCount, displayedConversationId) {
        if (messageCount == 0) {
            return@LaunchedEffect
        }
        if (!state.userDisabledAutoFollow && (state.shouldAutoFollowStreaming || isNearBottom)) {
            state.scrollToConversationEnd(
                listState = listState,
                bottomAnchorIndex = bottomAnchorIndex,
                animated = !isSending,
            )
        }
    }

    LaunchedEffect(
        lastMessageContentLength,
        lastReasoningContentLength,
        lastMessagePartCount,
        isSending,
        displayedConversationId,
    ) {
        if (!isSending || messageCount == 0) {
            return@LaunchedEffect
        }
        if (state.userDisabledAutoFollow) {
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        if (state.shouldAutoFollowStreaming &&
            now - state.lastStreamFollowAtMillis >= ChatStreamFollowScrollWindowMillis
        ) {
            state.lastStreamFollowAtMillis = now
            state.keepConversationEndInView(listState, bottomAnchorIndex)
        }
    }

    LaunchedEffect(isSending, displayedConversationId) {
        if (state.wasSending && !isSending) {
            if (!state.userDisabledAutoFollow && (state.shouldAutoFollowStreaming || isNearBottom)) {
                state.pendingCompletionFollow = true
            }
            if (isNearBottom) {
                state.userDisabledAutoFollow = false
            }
        }
        state.wasSending = isSending
    }

    LaunchedEffect(
        state.pendingCompletionFollow,
        isSending,
        lastMessageId,
        lastMessageStatus,
        displayedConversationId,
    ) {
        if (!state.pendingCompletionFollow || isSending || messageCount == 0) {
            return@LaunchedEffect
        }

        repeat(4) {
            withFrameNanos { }
            if (listState.isScrollInProgress && !state.isProgrammaticScroll) {
                state.pendingCompletionFollow = false
                state.shouldAutoFollowStreaming = false
                state.userDisabledAutoFollow = true
                return@LaunchedEffect
            }
            if (state.userDisabledAutoFollow) {
                state.pendingCompletionFollow = false
                return@LaunchedEffect
            }
            state.scrollToConversationEnd(listState, bottomAnchorIndex, animated = false)
        }
        state.shouldAutoFollowStreaming = true
        state.pendingCompletionFollow = false
    }
}

@Composable
internal fun ChatImeAutoFollowEffect(
    state: ChatAutoFollowState,
    listState: LazyListState,
    displayedConversationId: String,
    enabled: Boolean,
    isNearBottom: Boolean,
) {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime
    val latestIsNearBottom by rememberUpdatedState(isNearBottom)
    val latestShouldAutoFollow by rememberUpdatedState(state.shouldAutoFollowStreaming)
    val latestUserDisabledAutoFollow by rememberUpdatedState(state.userDisabledAutoFollow)
    var previousImeBottom by remember(displayedConversationId) { mutableIntStateOf(0) }

    LaunchedEffect(displayedConversationId, enabled, listState, density) {
        if (!enabled) {
            previousImeBottom = 0
            return@LaunchedEffect
        }

        snapshotFlow { imeInsets.getBottom(density) }
            .collect { imeBottom ->
                val delta = imeBottom - previousImeBottom
                previousImeBottom = imeBottom
                if (delta <= 0) {
                    return@collect
                }
                if (latestUserDisabledAutoFollow && !latestIsNearBottom) {
                    return@collect
                }
                if (!latestIsNearBottom && !latestShouldAutoFollow) {
                    return@collect
                }
                if (listState.isScrollInProgress && !state.isProgrammaticScroll) {
                    return@collect
                }

                state.isProgrammaticScroll = true
                try {
                    listState.scrollBy(delta.toFloat())
                } finally {
                    state.isProgrammaticScroll = false
                }
            }
    }
}

@Composable
internal fun ChatFeedbackEffects(
    snackbarHostState: SnackbarHostState,
    errorMessage: String?,
    noticeMessage: String?,
    currentModelSupportsReasoning: Boolean,
    onClearErrorMessage: () -> Unit,
    onClearNoticeMessage: () -> Unit,
    onHideReasoningSheet: () -> Unit,
) {
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearErrorMessage()
        }
    }

    LaunchedEffect(noticeMessage) {
        noticeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearNoticeMessage()
        }
    }

    LaunchedEffect(currentModelSupportsReasoning) {
        if (!currentModelSupportsReasoning) {
            onHideReasoningSheet()
        }
    }
}
