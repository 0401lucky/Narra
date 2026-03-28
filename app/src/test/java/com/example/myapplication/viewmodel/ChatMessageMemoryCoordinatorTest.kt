package com.example.myapplication.viewmodel

import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.Conversation
import com.example.myapplication.testutil.FakeMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageMemoryCoordinatorTest {
    @Test
    fun toggle_createsAssistantScopedMemoryByDefault() = runBlocking {
        val memoryRepository = FakeMemoryRepository()
        val coordinator = ChatMessageMemoryCoordinator(
            memoryRepository = memoryRepository,
            nowProvider = { 10L },
            entryIdProvider = { "entry-1" },
        )
        val state = ChatUiState(
            currentConversationId = "c1",
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "请记住我喜欢短句回复。",
                    createdAt = 1L,
                ),
            ),
            isConversationReady = true,
            currentAssistant = Assistant(id = "assistant-1", memoryEnabled = true),
        )

        val result = coordinator.toggle(
            state = state,
            fallbackAssistantId = "assistant-1",
            messageId = "m1",
        )

        assertEquals(ChatMemoryToggleResult.Notice("已记住这条"), result)
        val entry = memoryRepository.currentEntries().single()
        assertEquals("entry-1", entry.id)
        assertEquals(MemoryScopeType.ASSISTANT, entry.scopeType)
        assertEquals("assistant-1", entry.scopeId)
    }

    @Test
    fun toggle_requestsConversationInitializationWhenConversationMissing() = runBlocking {
        val coordinator = ChatMessageMemoryCoordinator(
            memoryRepository = FakeMemoryRepository(),
        )
        val state = ChatUiState(
            currentConversationId = "",
            currentConversationTitle = DEFAULT_CONVERSATION_TITLE,
            isConversationReady = true,
            messages = listOf(
                ChatMessage(
                    id = "m1",
                    conversationId = "c1",
                    role = MessageRole.USER,
                    content = "test",
                    createdAt = 1L,
                ),
            ),
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )

        val result = coordinator.toggle(
            state = state,
            fallbackAssistantId = "assistant-1",
            messageId = "m1",
        )

        assertTrue(result is ChatMemoryToggleResult.Error)
        assertTrue((result as ChatMemoryToggleResult.Error).shouldEnsureConversation)
        assertEquals("会话初始化中，请稍后重试", result.message)
    }
}
