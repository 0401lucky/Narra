package com.example.myapplication.data.repository

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MessageRole
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationRepositoryTest {
    @Test
    fun ensureActiveConversation_createsDefaultConversationWhenStoreIsEmpty() = runBlocking {
        val store = FakeConversationStore()
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 100L },
        )

        val conversation = repository.ensureActiveConversation(currentConversationId = null)

        assertEquals(DEFAULT_CONVERSATION_TITLE, conversation.title)
        assertEquals(100L, conversation.createdAt)
        assertEquals(listOf(conversation), store.listConversations())
    }

    @Test
    fun saveConversationMessages_updatesTitleAndModel() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = DEFAULT_CONVERSATION_TITLE,
                    model = "old-model",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )
        val messages = listOf(
            ChatMessage(
                id = "m1",
                conversationId = "c1",
                role = MessageRole.USER,
                content = "这是一个非常长的首条消息，用来验证标题会被截断到二十个字符以内",
                createdAt = 10L,
            ),
        )

        val updatedConversation = repository.saveConversationMessages(
            conversationId = "c1",
            messages = messages,
            selectedModel = "new-model",
        )

        assertEquals("这是一个非常长的首条消息，用来验证标题会", updatedConversation.title)
        assertEquals("new-model", updatedConversation.model)
        assertEquals(200L, updatedConversation.updatedAt)
        assertEquals(messages, store.listMessages("c1"))
    }

    @Test
    fun clearConversation_onlyClearsCurrentMessagesAndResetsTitle() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "旧标题",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
                Conversation(
                    id = "c2",
                    title = "另一个会话",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 2L,
                ),
            ),
            messagesByConversation = mapOf(
                "c1" to listOf(
                    ChatMessage(
                        id = "m1",
                        conversationId = "c1",
                        role = MessageRole.USER,
                        content = "hello",
                        createdAt = 10L,
                    ),
                ),
                "c2" to listOf(
                    ChatMessage(
                        id = "m2",
                        conversationId = "c2",
                        role = MessageRole.USER,
                        content = "world",
                        createdAt = 20L,
                    ),
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 300L },
        )

        val updatedConversation = repository.clearConversation(
            conversationId = "c1",
            selectedModel = "model-c",
        )

        assertEquals(DEFAULT_CONVERSATION_TITLE, updatedConversation.title)
        assertEquals("model-c", updatedConversation.model)
        assertTrue(store.listMessages("c1").isEmpty())
        assertEquals(1, store.listMessages("c2").size)
    }

    @Test
    fun deleteConversation_returnsRemainingConversation() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "会话1",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 20L,
                ),
                Conversation(
                    id = "c2",
                    title = "会话2",
                    model = "model-b",
                    createdAt = 2L,
                    updatedAt = 10L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 400L },
        )

        val nextConversation = repository.deleteConversation(
            conversationId = "c1",
            selectedModel = "model-c",
        )

        assertEquals("c2", nextConversation.id)
        assertEquals(listOf("c2"), store.listConversations().map { it.id })
    }

    @Test
    fun deleteConversation_createsNewConversationWhenLastOneRemoved() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "唯一会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 500L },
        )

        val nextConversation = repository.deleteConversation(
            conversationId = "c1",
            selectedModel = "model-z",
        )

        assertEquals(DEFAULT_CONVERSATION_TITLE, nextConversation.title)
        assertEquals("model-z", nextConversation.model)
        assertEquals(1, store.listConversations().size)
    }

    @Test
    fun createConversation_persistsWithDefaultTitle() = runBlocking {
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = "c1",
                    title = "已有会话",
                    model = "model-a",
                    createdAt = 1L,
                    updatedAt = 1L,
                ),
            ),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )

        val newConversation = repository.createConversation(selectedModel = "model-b")

        assertEquals(DEFAULT_CONVERSATION_TITLE, newConversation.title)
        assertEquals("model-b", newConversation.model)
        assertEquals(200L, newConversation.createdAt)
        assertEquals(2, store.listConversations().size)
        assertTrue(store.listConversations().any { it.id == newConversation.id })
    }

    @Test
    fun ensureActiveConversation_treatsLegacyBlankAssistantAsDefaultAssistant() = runBlocking {
        val legacyConversation = Conversation(
            id = "legacy",
            title = "旧会话",
            model = "model-a",
            createdAt = 1L,
            updatedAt = 1L,
            assistantId = "",
        )
        val store = FakeConversationStore(
            conversations = listOf(legacyConversation),
        )
        val repository = ConversationRepository(
            conversationStore = store,
            nowProvider = { 200L },
        )

        val resolved = repository.ensureActiveConversation(
            currentConversationId = null,
            assistantId = DEFAULT_ASSISTANT_ID,
        )

        assertEquals("legacy", resolved.id)
    }
}
