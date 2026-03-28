package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.Conversation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatObservationSupportTest {
    @Test
    fun resolveObservedSettings_marksAssistantSwitchWhenSelectionChanges() {
        val update = ChatObservationSupport.resolveObservedSettings(
            currentAssistantId = "assistant-old",
            settings = AppSettings(
                assistants = listOf(Assistant(id = "assistant-new", name = "新助手")),
                selectedAssistantId = "assistant-new",
            ),
        )

        assertEquals("assistant-new", update.newAssistantId)
        assertEquals("assistant-new", update.activeAssistant?.id)
        assertTrue(update.shouldSwitchAssistant)
    }

    @Test
    fun resolveConversationCollection_returnsEmptyWhenNoConversationExists() {
        val update = ChatObservationSupport.resolveConversationCollection(
            conversations = emptyList(),
            currentConversationId = "c1",
        )

        assertNull(update.resolvedConversation)
        assertFalse(update.shouldActivateConversation)
    }

    @Test
    fun resolveConversationCollection_activatesFallbackConversationWhenCurrentMissing() {
        val update = ChatObservationSupport.resolveConversationCollection(
            conversations = listOf(
                Conversation(id = "c2", title = "会话二", model = "", createdAt = 1L, updatedAt = 1L),
            ),
            currentConversationId = "c1",
        )

        assertEquals("c2", update.resolvedConversation?.id)
        assertTrue(update.shouldActivateConversation)
    }

    @Test
    fun shouldIgnoreObservedMessages_onlyWhenSendingSameConversation() {
        assertTrue(
            ChatObservationSupport.shouldIgnoreObservedMessages(
                current = ChatUiState(isSending = true, currentConversationId = "c1"),
                conversationId = "c1",
            ),
        )
        assertFalse(
            ChatObservationSupport.shouldIgnoreObservedMessages(
                current = ChatUiState(isSending = true, currentConversationId = "c1"),
                conversationId = "c2",
            ),
        )
    }
}
