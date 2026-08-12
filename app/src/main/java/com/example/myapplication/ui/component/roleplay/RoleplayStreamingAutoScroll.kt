package com.example.myapplication.ui.component.roleplay

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.example.myapplication.system.performance.PerformanceTraceContract
import com.example.myapplication.system.performance.traceAsync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal const val RoleplayStreamingFollowWindowMillis = 64L

private const val RoleplayBottomFollowTolerancePx = 48
private const val RoleplayStreamingMaxScrollStepPx = 600

internal data class RoleplayStreamingScrollSignal(
    val lastUiItemId: String?,
    val itemCount: Int,
    val isStreaming: Boolean,
    val contentVersion: Int,
)

internal enum class RoleplayAutoFollowMode {
    FOLLOWING,
    PAUSED_BY_USER,
}

internal enum class RoleplayAutoScrollAction {
    NONE,
    INITIAL_POSITION,
    FOLLOW_STREAMING,
    FOLLOW_COMPLETION,
}

internal fun nextRoleplayAutoFollowMode(
    currentMode: RoleplayAutoFollowMode,
    isScrollInProgress: Boolean,
    isProgrammaticScroll: Boolean,
    isNearBottom: Boolean,
): RoleplayAutoFollowMode {
    return when {
        isScrollInProgress && !isProgrammaticScroll -> RoleplayAutoFollowMode.PAUSED_BY_USER
        !isScrollInProgress && isNearBottom -> RoleplayAutoFollowMode.FOLLOWING
        else -> currentMode
    }
}

internal fun decideRoleplayAutoScrollAction(
    hasInitiallyPositioned: Boolean,
    followMode: RoleplayAutoFollowMode,
    isNearBottom: Boolean,
    previousSignal: RoleplayStreamingScrollSignal?,
    currentSignal: RoleplayStreamingScrollSignal,
): RoleplayAutoScrollAction {
    if (currentSignal.itemCount <= 0) {
        return RoleplayAutoScrollAction.NONE
    }
    if (!hasInitiallyPositioned) {
        return RoleplayAutoScrollAction.INITIAL_POSITION
    }
    if (previousSignal == currentSignal ||
        followMode != RoleplayAutoFollowMode.FOLLOWING ||
        !isNearBottom
    ) {
        return RoleplayAutoScrollAction.NONE
    }
    return when {
        currentSignal.isStreaming -> RoleplayAutoScrollAction.FOLLOW_STREAMING
        previousSignal?.isStreaming == true -> RoleplayAutoScrollAction.FOLLOW_COMPLETION
        previousSignal?.lastUiItemId != currentSignal.lastUiItemId -> {
            RoleplayAutoScrollAction.FOLLOW_COMPLETION
        }

        else -> RoleplayAutoScrollAction.NONE
    }
}

internal fun coalescedRoleplayScrollDueAtMillis(
    lastFollowAtMillis: Long?,
    observedAtMillis: Long,
    windowMillis: Long = RoleplayStreamingFollowWindowMillis,
): Long {
    return lastFollowAtMillis
        ?.plus(windowMillis)
        ?.coerceAtLeast(observedAtMillis)
        ?: observedAtMillis
}

internal data class RoleplayListMeasuredItem(
    val index: Int,
    val offset: Int,
    val size: Int,
)

internal fun isRoleplayListNearBottom(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<RoleplayListMeasuredItem>,
    canScrollForward: Boolean,
    tolerancePx: Int = RoleplayBottomFollowTolerancePx,
): Boolean {
    if (totalItems == 0 || !canScrollForward) {
        return true
    }
    val boundaryStartIndex = (totalItems - 2).coerceAtLeast(0)
    val boundaryItem = visibleItems.lastOrNull { item -> item.index >= boundaryStartIndex }
        ?: return false
    return abs(boundaryItem.offset + boundaryItem.size - viewportEndOffset) <= tolerancePx
}

internal fun roleplayConversationEndDeltaPx(
    totalItems: Int,
    viewportEndOffset: Int,
    visibleItems: List<RoleplayListMeasuredItem>,
): Int? {
    if (totalItems == 0) {
        return 0
    }
    val lastItem = visibleItems.lastOrNull { item -> item.index == totalItems - 1 }
        ?: return null
    return (lastItem.offset + lastItem.size - viewportEndOffset).coerceAtLeast(0)
}

private fun LazyListState.visibleRoleplayItems(): List<RoleplayListMeasuredItem> {
    return layoutInfo.visibleItemsInfo.map { item ->
        RoleplayListMeasuredItem(
            index = item.index,
            offset = item.offset,
            size = item.size,
        )
    }
}

internal fun isRoleplayListNearBottom(
    listState: LazyListState,
): Boolean {
    val layoutInfo = listState.layoutInfo
    return isRoleplayListNearBottom(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleRoleplayItems(),
        canScrollForward = listState.canScrollForward,
    )
}

private fun roleplayConversationEndDeltaPx(
    listState: LazyListState,
): Int? {
    val layoutInfo = listState.layoutInfo
    return roleplayConversationEndDeltaPx(
        totalItems = layoutInfo.totalItemsCount,
        viewportEndOffset = layoutInfo.viewportEndOffset,
        visibleItems = listState.visibleRoleplayItems(),
    )
}

@Stable
internal class RoleplayStreamingAutoScrollState {
    var followMode by mutableStateOf(RoleplayAutoFollowMode.FOLLOWING)
        private set

    private var programmaticScrollCount by mutableIntStateOf(0)
    internal val isProgrammaticScroll: Boolean
        get() = programmaticScrollCount > 0

    internal var hasInitiallyPositioned: Boolean = false
        private set
    internal var lastHandledSignal: RoleplayStreamingScrollSignal? = null
        private set
    internal var lastStreamingFollowAtMillis: Long? = null
        private set

    fun pauseForUser() {
        followMode = RoleplayAutoFollowMode.PAUSED_BY_USER
    }

    fun resumeFollowing() {
        followMode = RoleplayAutoFollowMode.FOLLOWING
    }

    internal fun updateFollowMode(
        isScrollInProgress: Boolean,
        isNearBottom: Boolean,
    ) {
        followMode = nextRoleplayAutoFollowMode(
            currentMode = followMode,
            isScrollInProgress = isScrollInProgress,
            isProgrammaticScroll = isProgrammaticScroll,
            isNearBottom = isNearBottom,
        )
    }

    internal fun resetForEmptyList(signal: RoleplayStreamingScrollSignal) {
        hasInitiallyPositioned = false
        lastHandledSignal = signal
        lastStreamingFollowAtMillis = null
    }

    internal fun markHandled(signal: RoleplayStreamingScrollSignal) {
        lastHandledSignal = signal
    }

    internal fun markStreamingFollowed(atMillis: Long) {
        lastStreamingFollowAtMillis = atMillis
    }

    suspend fun scrollToEnd(
        listState: LazyListState,
        lastIndex: Int,
        animated: Boolean,
        resumeAutoFollow: Boolean,
    ): Boolean {
        if (lastIndex < 0) {
            return false
        }
        if (resumeAutoFollow) {
            resumeFollowing()
        }
        val completed = runProgrammaticScroll {
            if (animated) {
                listState.animateScrollToItem(lastIndex)
            } else {
                listState.scrollToItem(lastIndex)
            }
        }
        if (completed) {
            hasInitiallyPositioned = true
        }
        return completed
    }

    internal suspend fun keepEndInView(
        listState: LazyListState,
        lastIndex: Int,
    ): Boolean {
        if (lastIndex < 0) {
            return false
        }
        withFrameNanos { }
        return runProgrammaticScroll {
            var remainingPasses = 4
            while (remainingPasses > 0) {
                val delta = roleplayConversationEndDeltaPx(listState)
                when {
                    delta == null -> {
                        listState.scrollToItem(lastIndex)
                        return@runProgrammaticScroll
                    }

                    delta <= 0 -> return@runProgrammaticScroll
                    else -> {
                        listState.scrollBy(
                            delta.coerceAtMost(RoleplayStreamingMaxScrollStepPx).toFloat(),
                        )
                        remainingPasses -= 1
                        if (remainingPasses > 0) {
                            withFrameNanos { }
                        }
                    }
                }
            }
            if (!isRoleplayListNearBottom(listState)) {
                listState.scrollToItem(lastIndex)
            }
        }
    }

    private suspend fun runProgrammaticScroll(
        block: suspend () -> Unit,
    ): Boolean {
        return PerformanceTraceContract.ROLEPLAY_AUTO_SCROLL.traceAsync {
            programmaticScrollCount += 1
            try {
                block()
                true
            } catch (cancellation: CancellationException) {
                if (!currentCoroutineContext().isActive) {
                    throw cancellation
                }
                false
            } finally {
                programmaticScrollCount -= 1
            }
        }
    }
}

@Composable
internal fun rememberRoleplayStreamingAutoScrollState(
    conversationIdentity: Any,
): RoleplayStreamingAutoScrollState {
    return remember(conversationIdentity) { RoleplayStreamingAutoScrollState() }
}

@Composable
internal fun rememberRoleplayAutoFollowScrollConnection(
    state: RoleplayStreamingAutoScrollState,
): NestedScrollConnection {
    return remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput && available.y != 0f) {
                    state.pauseForUser()
                }
                return Offset.Zero
            }
        }
    }
}

private data class RoleplayAutoScrollObservation(
    val signal: RoleplayStreamingScrollSignal,
    val isScrollInProgress: Boolean,
    val isNearBottom: Boolean,
    val isProgrammaticScroll: Boolean,
)

@Composable
internal fun RoleplayStreamingAutoScrollEffect(
    state: RoleplayStreamingAutoScrollState,
    listState: LazyListState,
    conversationIdentity: Any,
    signal: RoleplayStreamingScrollSignal,
    nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    val latestSignal by rememberUpdatedState(signal)
    val latestNowMillis by rememberUpdatedState(nowMillis)

    LaunchedEffect(listState, conversationIdentity) {
        snapshotFlow {
            RoleplayAutoScrollObservation(
                signal = latestSignal,
                isScrollInProgress = listState.isScrollInProgress,
                isNearBottom = isRoleplayListNearBottom(listState),
                isProgrammaticScroll = state.isProgrammaticScroll,
            )
        }
            .distinctUntilChanged()
            .conflate()
            .collect { observation ->
                state.updateFollowMode(
                    isScrollInProgress = observation.isScrollInProgress,
                    isNearBottom = observation.isNearBottom,
                )
                if (observation.signal.itemCount <= 0) {
                    state.resetForEmptyList(observation.signal)
                    return@collect
                }

                var currentSignal = latestSignal
                var action = decideRoleplayAutoScrollAction(
                    hasInitiallyPositioned = state.hasInitiallyPositioned,
                    followMode = state.followMode,
                    isNearBottom = observation.isNearBottom,
                    previousSignal = state.lastHandledSignal,
                    currentSignal = currentSignal,
                )

                if (action == RoleplayAutoScrollAction.FOLLOW_STREAMING) {
                    val observedAtMillis = latestNowMillis()
                    val dueAtMillis = coalescedRoleplayScrollDueAtMillis(
                        lastFollowAtMillis = state.lastStreamingFollowAtMillis,
                        observedAtMillis = observedAtMillis,
                    )
                    val remainingDelayMillis = dueAtMillis - observedAtMillis
                    if (remainingDelayMillis > 0) {
                        delay(remainingDelayMillis)
                    }

                    currentSignal = latestSignal
                    val isNearBottomAfterDelay = isRoleplayListNearBottom(listState)
                    state.updateFollowMode(
                        isScrollInProgress = listState.isScrollInProgress,
                        isNearBottom = isNearBottomAfterDelay,
                    )
                    action = decideRoleplayAutoScrollAction(
                        hasInitiallyPositioned = state.hasInitiallyPositioned,
                        followMode = state.followMode,
                        isNearBottom = isNearBottomAfterDelay,
                        previousSignal = state.lastHandledSignal,
                        currentSignal = currentSignal,
                    )
                }

                when (action) {
                    RoleplayAutoScrollAction.INITIAL_POSITION -> {
                        state.scrollToEnd(
                            listState = listState,
                            lastIndex = currentSignal.itemCount - 1,
                            animated = false,
                            resumeAutoFollow = true,
                        )
                    }

                    RoleplayAutoScrollAction.FOLLOW_STREAMING -> {
                        if (!listState.isScrollInProgress || state.isProgrammaticScroll) {
                            val followed = state.keepEndInView(
                                listState = listState,
                                lastIndex = currentSignal.itemCount - 1,
                            )
                            if (followed) {
                                state.markStreamingFollowed(latestNowMillis())
                            }
                        }
                    }

                    RoleplayAutoScrollAction.FOLLOW_COMPLETION -> {
                        if (!listState.isScrollInProgress || state.isProgrammaticScroll) {
                            state.scrollToEnd(
                                listState = listState,
                                lastIndex = currentSignal.itemCount - 1,
                                animated = true,
                                resumeAutoFollow = false,
                            )
                        }
                    }

                    RoleplayAutoScrollAction.NONE -> Unit
                }
                state.markHandled(currentSignal)
            }
    }
}
