package com.example.myapplication.data.local.chat

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE assistantId = :assistantId ORDER BY updatedAt DESC")
    fun observeConversationsByAssistant(assistantId: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    suspend fun listConversations(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE id = :conversationId LIMIT 1")
    suspend fun getConversation(conversationId: String): ConversationEntity?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun listMessages(conversationId: String): List<MessageEntity>

    @Upsert
    suspend fun upsertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearMessages(conversationId: String)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)

    @Transaction
    suspend fun replaceMessagesTransaction(conversationId: String, messages: List<MessageEntity>) {
        clearMessages(conversationId)
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }

    @Transaction
    suspend fun saveConversationWithMessages(
        conversation: ConversationEntity,
        conversationId: String,
        messages: List<MessageEntity>,
    ) {
        upsertConversation(conversation)
        clearMessages(conversationId)
        if (messages.isNotEmpty()) {
            insertMessages(messages)
        }
    }

    @Transaction
    suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: ConversationEntity,
    ) {
        clearMessages(conversationId)
        upsertConversation(conversation)
    }
}
