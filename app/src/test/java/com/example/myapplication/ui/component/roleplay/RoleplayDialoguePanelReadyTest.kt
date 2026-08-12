package com.example.myapplication.ui.component.roleplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleplayDialoguePanelReadyTest {
    @Test
    fun offlineReplayViewportReady_末项可见且已停止到底_返回就绪() {
        assertTrue(
            isRoleplayOfflineReplayViewportReady(
                isLastStoryMessageVisible = true,
                canScrollForward = false,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun offlineReplayViewportReady_仍可下滚_返回未就绪() {
        assertFalse(
            isRoleplayOfflineReplayViewportReady(
                isLastStoryMessageVisible = true,
                canScrollForward = true,
                isScrollInProgress = false,
            ),
        )
    }

    @Test
    fun offlineReplayViewportReady_末项不可见或仍在滚动_返回未就绪() {
        assertFalse(
            isRoleplayOfflineReplayViewportReady(
                isLastStoryMessageVisible = false,
                canScrollForward = false,
                isScrollInProgress = false,
            ),
        )
        assertFalse(
            isRoleplayOfflineReplayViewportReady(
                isLastStoryMessageVisible = true,
                canScrollForward = false,
                isScrollInProgress = true,
            ),
        )
    }
}
