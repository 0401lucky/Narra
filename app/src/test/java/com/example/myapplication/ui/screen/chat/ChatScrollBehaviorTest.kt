package com.example.myapplication.ui.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatScrollBehaviorTest {
    @Test
    fun isListNearBottom_treatsLastMessageAsBoundaryWhenAnchorIsNotVisible() {
        val visibleItems = listOf(
            ChatListMeasuredItem(
                index = 8,
                offset = 120,
                size = 300,
            ),
        )

        assertTrue(
            isListNearBottom(
                totalItems = 10,
                viewportEndOffset = 430,
                visibleItems = visibleItems,
            ),
        )
    }

    @Test
    fun isListNearBottom_returnsFalseWhenBoundaryItemIsFarFromViewportEnd() {
        val visibleItems = listOf(
            ChatListMeasuredItem(
                index = 8,
                offset = 24,
                size = 220,
            ),
        )

        assertFalse(
            isListNearBottom(
                totalItems = 10,
                viewportEndOffset = 420,
                visibleItems = visibleItems,
            ),
        )
    }

    @Test
    fun conversationEndDeltaPx_returnsOverflowForPartiallyHiddenLastMessage() {
        val visibleItems = listOf(
            ChatListMeasuredItem(
                index = 8,
                offset = 120,
                size = 360,
            ),
        )

        assertEquals(
            60,
            conversationEndDeltaPx(
                totalItems = 10,
                viewportEndOffset = 420,
                visibleItems = visibleItems,
            ),
        )
    }

    @Test
    fun conversationEndDeltaPx_returnsZeroWhenBottomAnchorAlreadyFitsViewport() {
        val visibleItems = listOf(
            ChatListMeasuredItem(
                index = 9,
                offset = 410,
                size = 10,
            ),
        )

        assertEquals(
            0,
            conversationEndDeltaPx(
                totalItems = 10,
                viewportEndOffset = 420,
                visibleItems = visibleItems,
            ),
        )
    }
}
