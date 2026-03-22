package com.example.myapplication.testutil

import com.example.myapplication.data.local.ConversationStore
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeConversationStore(
    conversations: List<Conversation> = emptyList(),
    messagesByConversation: Map<String, List<ChatMessage>> = emptyMap(),
) : ConversationStore {
    private val conversationsState = MutableStateFlow(conversations.sortedByDescending { it.updatedAt })
    private val messagesState = MutableStateFlow(messagesByConversation)

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationsState
    }

    override fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>> {
        return conversationsState.map { conversations ->
            conversations.filter { it.assistantId == assistantId }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return messagesState.map { it[conversationId].orEmpty().sortedBy { message -> message.createdAt } }
    }

    override suspend fun listConversations(): List<Conversation> {
        return conversationsState.value
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationsState.value.firstOrNull { it.id == conversationId }
    }

    override suspend fun listMessages(conversationId: String): List<ChatMessage> {
        return messagesState.value[conversationId].orEmpty().sortedBy { it.createdAt }
    }

    override suspend fun upsertConversation(conversation: Conversation) {
        val updated = conversationsState.value.filterNot { it.id == conversation.id } + conversation
        conversationsState.value = updated.sortedByDescending { it.updatedAt }
    }

    override suspend fun replaceMessages(conversationId: String, messages: List<ChatMessage>) {
        messagesState.value = messagesState.value.toMutableMap().apply {
            this[conversationId] = messages.sortedBy { it.createdAt }
        }
    }

    override suspend fun saveConversationWithMessages(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        upsertConversation(conversation)
        replaceMessages(conversationId, messages)
    }

    override suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    ) {
        clearMessages(conversationId)
        upsertConversation(conversation)
    }

    override suspend fun appendMessage(message: ChatMessage) {
        val currentMessages = messagesState.value[message.conversationId].orEmpty()
        replaceMessages(message.conversationId, currentMessages + message)
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversationsState.value = conversationsState.value
            .filterNot { it.id == conversationId }
            .sortedByDescending { it.updatedAt }
        messagesState.value = messagesState.value.toMutableMap().apply {
            remove(conversationId)
        }
    }

    override suspend fun clearMessages(conversationId: String) {
        messagesState.value = messagesState.value.toMutableMap().apply {
            this[conversationId] = emptyList()
        }
    }
}
