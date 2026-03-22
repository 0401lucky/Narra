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

    suspend fun upsertConversation(conversation: Conversation)

    suspend fun replaceMessages(conversationId: String, messages: List<ChatMessage>)

    suspend fun saveConversationWithMessages(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    )

    suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    )

    suspend fun appendMessage(message: ChatMessage)

    suspend fun deleteConversation(conversationId: String)

    suspend fun clearMessages(conversationId: String)
}
