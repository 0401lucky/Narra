package com.example.myapplication.ui.component.roleplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayStreamingAutoScrollTest {
    @Test
    fun decideRoleplayAutoScrollAction_firstLoadedList_positionsOnce() {
        assertEquals(
            RoleplayAutoScrollAction.INITIAL_POSITION,
            decideRoleplayAutoScrollAction(
                hasInitiallyPositioned = false,
                followMode = RoleplayAutoFollowMode.FOLLOWING,
                isNearBottom = false,
                previousSignal = null,
                currentSignal = signal(contentVersion = 1),
            ),
        )
    }

    @Test
    fun decideRoleplayAutoScrollAction_streamingGrowth_followsNearBottom() {
        assertEquals(
            RoleplayAutoScrollAction.FOLLOW_STREAMING,
            decideRoleplayAutoScrollAction(
                hasInitiallyPositioned = true,
                followMode = RoleplayAutoFollowMode.FOLLOWING,
                isNearBottom = true,
                previousSignal = signal(contentVersion = 10),
                currentSignal = signal(contentVersion = 18),
            ),
        )
    }

    @Test
    fun decideRoleplayAutoScrollAction_userAwayFromBottom_doesNotFollow() {
        assertEquals(
            RoleplayAutoScrollAction.NONE,
            decideRoleplayAutoScrollAction(
                hasInitiallyPositioned = true,
                followMode = RoleplayAutoFollowMode.PAUSED_BY_USER,
                isNearBottom = false,
                previousSignal = signal(contentVersion = 10),
                currentSignal = signal(contentVersion = 18),
            ),
        )
    }

    @Test
    fun nextRoleplayAutoFollowMode_userLeavesAndReturns_recoversFollowing() {
        val paused = nextRoleplayAutoFollowMode(
            currentMode = RoleplayAutoFollowMode.FOLLOWING,
            isScrollInProgress = true,
            isProgrammaticScroll = false,
            isNearBottom = false,
        )
        val recovered = nextRoleplayAutoFollowMode(
            currentMode = paused,
            isScrollInProgress = false,
            isProgrammaticScroll = false,
            isNearBottom = true,
        )

        assertEquals(RoleplayAutoFollowMode.PAUSED_BY_USER, paused)
        assertEquals(RoleplayAutoFollowMode.FOLLOWING, recovered)
    }

    @Test
    fun coalescedRoleplayScrollDueAtMillis_signalsInsideWindow_shareDeadline() {
        val firstDueAt = coalescedRoleplayScrollDueAtMillis(
            lastFollowAtMillis = 100L,
            observedAtMillis = 120L,
        )
        val laterSignalDueAt = coalescedRoleplayScrollDueAtMillis(
            lastFollowAtMillis = 100L,
            observedAtMillis = 150L,
        )

        assertEquals(164L, firstDueAt)
        assertEquals(firstDueAt, laterSignalDueAt)
        assertEquals(
            220L,
            coalescedRoleplayScrollDueAtMillis(
                lastFollowAtMillis = 100L,
                observedAtMillis = 220L,
            ),
        )
    }

    @Test
    fun listBoundarySupport_distinguishesNearBottomAndMissingLastItem() {
        val visibleItems = listOf(
            RoleplayListMeasuredItem(index = 8, offset = 100, size = 300),
        )

        assertTrue(
            isRoleplayListNearBottom(
                totalItems = 10,
                viewportEndOffset = 420,
                visibleItems = visibleItems,
                canScrollForward = true,
            ),
        )
        assertNull(
            roleplayConversationEndDeltaPx(
                totalItems = 10,
                viewportEndOffset = 420,
                visibleItems = visibleItems,
            ),
        )
        assertFalse(
            isRoleplayListNearBottom(
                totalItems = 10,
                viewportEndOffset = 520,
                visibleItems = visibleItems,
                canScrollForward = true,
            ),
        )
    }

    private fun signal(
        contentVersion: Int,
        isStreaming: Boolean = true,
        lastUiItemId: String = "assistant:streaming-preview",
    ): RoleplayStreamingScrollSignal {
        return RoleplayStreamingScrollSignal(
            lastUiItemId = lastUiItemId,
            itemCount = 12,
            isStreaming = isStreaming,
            contentVersion = contentVersion,
        )
    }
}
