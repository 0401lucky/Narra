package com.example.myapplication.data.local

import androidx.room.withTransaction
import com.example.myapplication.data.local.chat.ConversationDao
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.ConversationEntity
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.normalizeChatMessageParts
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationStore(
    private val database: ChatDatabase,
) : ConversationStore {
    private val conversationDao: ConversationDao = database.conversationDao()
    private val gson = Gson()
    private val attachmentListType = object : TypeToken<List<MessageAttachment>>() {}.type
    private val partListType = object : TypeToken<List<ChatMessagePart>>() {}.type
    private val citationListType = object : TypeToken<List<MessageCitation>>() {}.type

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeConversations().map { conversations ->
            conversations.map { it.toDomain() }
        }
    }

    override fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>> {
        return conversationDao.observeConversationsByAssistant(assistantId).map { conversations ->
            conversations.map { it.toDomain() }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return conversationDao.observeMessages(conversationId).map { messages ->
            messages.map { it.toDomain() }
        }
    }

    override suspend fun listConversations(): List<Conversation> {
        return conversationDao.listConversations().map { it.toDomain() }
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getConversation(conversationId)?.toDomain()
    }

    override suspend fun listMessages(conversationId: String): List<ChatMessage> {
        return conversationDao.listMessages(conversationId).map { it.toDomain() }
    }

    override suspend fun upsertConversationMetadata(conversation: Conversation) {
        conversationDao.upsertConversationEntity(conversation.toEntity())
    }

    override suspend fun replaceConversationSnapshot(
        conversation: Conversation,
        conversationId: String,
        messages: List<ChatMessage>,
    ) {
        conversationDao.replaceConversationSnapshot(
            conversation = conversation.toEntity(),
            conversationId = conversationId,
            messages = messages.map { it.toEntity() },
        )
    }

    override suspend fun updateConversationMessages(
        conversation: Conversation,
        conversationId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ): List<ChatMessage> {
        return database.withTransaction {
            val existingMessages = conversationDao.listMessages(conversationId).map { it.toDomain() }
            val updatedMessages = transform(existingMessages)
            if (updatedMessages == existingMessages) {
                return@withTransaction existingMessages
            }
            conversationDao.upsertConversationEntity(conversation.toEntity())
            val changedMessages = resolveMessagesToUpsert(
                existingMessages = existingMessages,
                nextMessages = updatedMessages,
            )
            if (changedMessages.isNotEmpty()) {
                conversationDao.insertMessages(changedMessages.map { it.toEntity() })
            }
            updatedMessages
        }
    }

    override suspend fun upsertConversationWithMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
    ) {
        conversationDao.upsertConversationWithMessages(
            conversation = conversation.toEntity(),
            messages = messages.map { it.toEntity() },
        )
    }

    override suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    ) {
        conversationDao.clearMessagesAndUpdateConversation(
            conversationId = conversationId,
            conversation = conversation.toEntity(),
        )
    }

    override suspend fun upsertMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) {
            return
        }
        conversationDao.insertMessages(messages.map { it.toEntity() })
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
    }

    override suspend fun clearMessages(conversationId: String) {
        conversationDao.clearMessages(conversationId)
    }

    private fun ConversationEntity.toDomain(): Conversation {
        return Conversation(
            id = id,
            title = title,
            model = model,
            createdAt = createdAt,
            updatedAt = updatedAt,
            assistantId = assistantId,
            searchEnabled = searchEnabled,
        )
    }

    private fun Conversation.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            title = title,
            model = model,
            createdAt = createdAt,
            updatedAt = updatedAt,
            assistantId = assistantId,
            searchEnabled = searchEnabled,
        )
    }

    private fun MessageEntity.toDomain(): ChatMessage {
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
            content = content,
            status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.COMPLETED),
            createdAt = createdAt,
            modelName = modelName,
            reasoningContent = reasoningContent,
            attachments = runCatching {
                gson.fromJson<List<MessageAttachment>>(attachmentsJson, attachmentListType).orEmpty()
            }.getOrDefault(emptyList()),
            parts = normalizeChatMessageParts(
                runCatching {
                    gson.fromJson<List<ChatMessagePart>>(partsJson, partListType).orEmpty()
                }.getOrDefault(emptyList()),
            ),
            citations = runCatching {
                gson.fromJson<List<MessageCitation>>(citationsJson, citationListType).orEmpty()
            }.getOrDefault(emptyList()),
        )
    }

    private fun ChatMessage.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role.name,
            content = content,
            status = status.name,
            createdAt = createdAt,
            modelName = modelName,
            reasoningContent = reasoningContent,
            attachmentsJson = gson.toJson(attachments),
            partsJson = gson.toJson(normalizeChatMessageParts(parts)),
            citationsJson = gson.toJson(citations),
        )
    }

    private fun resolveMessagesToUpsert(
        existingMessages: List<ChatMessage>,
        nextMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val existingById = existingMessages.associateBy(ChatMessage::id)
        return nextMessages.filter { nextMessage ->
            existingById[nextMessage.id] != nextMessage
        }
    }
}
