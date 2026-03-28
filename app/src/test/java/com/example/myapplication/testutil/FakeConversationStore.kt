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
    var listMessagesCount: Int = 0
        private set
    var replaceConversationSnapshotCount: Int = 0
        private set
    var updateConversationMessagesCount: Int = 0
        private set
    var upsertConversationWithMessagesCount: Int = 0
        private set
    var upsertMessagesCount: Int = 0
        private set

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
        listMessagesCount += 1
        return messagesState.value[conversationId].orEmpty().sortedBy { it.createdAt }
    }

    override suspend fun upsertConversationMetadata(conversation: Conversation) {
        val updated = conversationsState.value.filterNot { it.id == conversation.id } + conversation
        conversationsState.value = updated.sortedByDescending { it.updatedAt }
    }

    override suspend fun replaceConversationSnapshot(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        replaceConversationSnapshotCount += 1
        upsertConversationMetadata(conversation)
        messagesState.value = messagesState.value.toMutableMap().apply {
            this[conversationId] = messages.sortedBy { it.createdAt }
        }
    }

    override suspend fun updateConversationMessages(
        conversation: Conversation,
        conversationId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ): List<ChatMessage> {
        updateConversationMessagesCount += 1
        val existingMessages = messagesState.value[conversationId].orEmpty().sortedBy { it.createdAt }
        val updatedMessages = transform(existingMessages)
        if (updatedMessages == existingMessages) {
            return existingMessages
        }
        upsertConversationMetadata(conversation)
        upsertMessages(
            updatedMessages.filter { nextMessage ->
                existingMessages.firstOrNull { it.id == nextMessage.id } != nextMessage
            },
        )
        return updatedMessages
    }

    override suspend fun upsertConversationWithMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
    ) {
        upsertConversationWithMessagesCount += 1
        upsertConversationMetadata(conversation)
        upsertMessages(messages)
    }

    override suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    ) {
        clearMessages(conversationId)
        upsertConversationMetadata(conversation)
    }

    override suspend fun upsertMessages(messages: List<ChatMessage>) {
        upsertMessagesCount += 1
        messages.groupBy { it.conversationId }.forEach { (conversationId, scopedMessages) ->
            val currentMessages = messagesState.value[conversationId].orEmpty()
            val mergedMessages = currentMessages.filterNot { existing ->
                scopedMessages.any { incoming -> incoming.id == existing.id }
            } + scopedMessages
            messagesState.value = messagesState.value.toMutableMap().apply {
                this[conversationId] = mergedMessages.sortedBy { it.createdAt }
            }
        }
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
