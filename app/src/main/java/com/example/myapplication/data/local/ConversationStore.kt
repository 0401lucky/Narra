package com.example.myapplication.data.local

import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import kotlinx.coroutines.flow.Flow

interface ConversationStore {
    fun observeConversations(): Flow<List<Conversation>>

    fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>>

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>

    suspend fun listConversations(): List<Conversation>

    suspend fun getConversation(conversationId: String): Conversation?

    suspend fun listMessages(conversationId: String): List<ChatMessage>

    suspend fun upsertConversationMetadata(conversation: Conversation)

    suspend fun replaceConversationSnapshot(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    )

    suspend fun updateConversationMessages(
        conversation: Conversation,
        conversationId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ): List<ChatMessage>

    suspend fun upsertConversationWithMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
    )

    suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    )

    suspend fun upsertMessages(messages: List<ChatMessage>)

    suspend fun deleteConversation(conversationId: String)

    suspend fun clearMessages(conversationId: String)
}
