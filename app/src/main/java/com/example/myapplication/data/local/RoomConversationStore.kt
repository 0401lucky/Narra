package com.example.myapplication.data.local

import androidx.room.withTransaction
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.MessageDeletionResult
import com.example.myapplication.data.local.chat.ConversationDao
import com.example.myapplication.data.local.chat.ChatDatabase
import com.example.myapplication.data.local.chat.ConversationEntity
import com.example.myapplication.data.local.chat.MessageEntity
import com.example.myapplication.data.local.memory.MemoryDao
import com.example.myapplication.data.local.memory.MemorySourceStorage
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.withCompanionSourceProof
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.legacyReasoningStepsFromContent
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayOnlineEventKind
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.normalizeChatReasoningSteps
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.system.json.AppJson
import com.example.myapplication.system.logging.logFailure
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomConversationStore(
    private val database: ChatDatabase,
    nowProvider: () -> Long = System::currentTimeMillis,
) : ConversationStore {
    private val conversationDao: ConversationDao = database.conversationDao()
    private val memoryDao: MemoryDao = database.memoryDao()
    private val memorySources = MemorySourceStorage(database, nowProvider)
    private val companionSources =
        com.example.myapplication.data.local.companion.CompanionSourceStorage(database)
    private val gson = AppJson.gson
    private val attachmentListType = object : TypeToken<List<MessageAttachment>>() {}.type
    private val partListType = object : TypeToken<List<ChatMessagePart>>() {}.type
    private val reasoningStepListType = object : TypeToken<List<ChatReasoningStep>>() {}.type
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
        database.withTransaction {
            require(conversation.id == conversationId && messages.all { it.conversationId == conversationId }) { "会话快照归属不一致" }
            val retainedIds = messages.map { it.id }.toSet()
            conversationDao.listMessages(conversationId).filter { it.id !in retainedIds }.forEach {
                memorySources.invalidateMessage(conversationId, it.id, deleted = true)
                conversationDao.deleteMessage(conversationId, it.id)
            }
            conversationDao.upsertConversationEntity(conversation.toEntity())
            memorySources.writeMessages(messages.map { it.toEntity() })
        }
    }

    override suspend fun updateConversationMessages(
        conversation: Conversation,
        conversationId: String,
        transform: (List<ChatMessage>) -> List<ChatMessage>,
    ): List<ChatMessage> {
        return database.withTransaction {
            val existingMessages =
                conversationDao.listMessages(conversationId).map { it.toDomain() }
            val updatedMessages = transform(existingMessages)
            if (updatedMessages == existingMessages) {
                return@withTransaction existingMessages
            }
            conversationDao.upsertConversationEntity(conversation.toEntity())
            val changedMessages = resolveMessagesToUpsert(
                existingMessages = existingMessages,
                nextMessages = updatedMessages,
            )
            val saved = memorySources.writeMessages(changedMessages.map { it.toEntity() })
                .associateBy { it.id }
            updatedMessages.map { saved[it.id]?.toDomain() ?: it }
        }
    }

    override suspend fun deleteMessageWithAssociations(
        conversation: Conversation,
        conversationId: String,
        messageId: String,
    ): MessageDeletionResult {
        return database.withTransaction {
            val existingMessages =
                conversationDao.listMessages(conversationId).map { it.toDomain() }
            when (
                val deletionResult = ConversationMessageTransforms.deleteMessageAndClearReferences(
                    messages = existingMessages,
                    targetMessageId = messageId,
                )
            ) {
                MessageDeletionResult.NotFound -> MessageDeletionResult.NotFound
                is MessageDeletionResult.Deleted -> {
                    val changedMessages = resolveMessagesToUpsert(
                        existingMessages = existingMessages,
                        nextMessages = deletionResult.updatedMessages,
                    )
                    if (changedMessages.isNotEmpty()) {
                        memorySources.writeMessages(changedMessages.map { it.toEntity() })
                    }
                    memorySources.invalidateMessage(conversationId, messageId, deleted = true)
                    check(conversationDao.deleteMessage(conversationId, messageId) == 1) {
                        "消息删除事务未命中目标"
                    }
                    if ((database.memoryArchiveDao().getEngineState()?.activationVersion
                            ?: 0) < com.example.myapplication.model.MEMORY_ENGINE_CURRENT &&
                        (database.memoryArchiveDao().getEngineState()?.engineVersion
                            ?: 1) < com.example.myapplication.model.MEMORY_ENGINE_CURRENT
                    ) {
                        memoryDao.deleteMemoryEntriesBySourceMessageId(messageId)
                    }
                    memoryDao.deleteConversationSummary(conversationId)
                    memoryDao.deleteConversationSummarySegments(conversationId)
                    conversationDao.upsertConversationEntity(conversation.toEntity())
                    deletionResult
                }
            }
        }
    }

    override suspend fun upsertConversationWithMessages(
        conversation: Conversation,
        messages: List<ChatMessage>,
    ) {
        database.withTransaction {
            conversationDao.upsertConversationEntity(conversation.toEntity())
            require(messages.all { it.conversationId == conversation.id }) { "消息会话归属不一致" }
            memorySources.writeMessages(messages.map { it.toEntity() })
        }
    }

    override suspend fun clearMessagesAndUpdateConversation(
        conversationId: String,
        conversation: Conversation,
    ) {
        database.withTransaction {
            memorySources.clearConversation(conversationId, deletingConversation = false)
            conversationDao.clearMessagesAndUpdateConversation(
                conversationId,
                conversation.toEntity()
            )
            memoryDao.deleteConversationSummary(conversationId)
            memoryDao.deleteConversationSummarySegments(conversationId)
        }
    }

    override suspend fun upsertMessages(messages: List<ChatMessage>) {
        if (messages.isEmpty()) {
            return
        }
        database.withTransaction { memorySources.writeMessages(messages.map { it.toEntity() }) }
    }

    override suspend fun deleteConversation(conversationId: String) {
        database.withTransaction {
            memorySources.clearConversation(conversationId, deletingConversation = true)
            conversationDao.deleteConversation(conversationId)
            memoryDao.deleteConversationSummary(conversationId)
            memoryDao.deleteConversationSummarySegments(conversationId)
        }
    }

    override suspend fun clearMessages(conversationId: String) {
        database.withTransaction {
            memorySources.clearConversation(conversationId, deletingConversation = false)
            conversationDao.clearMessages(conversationId)
            memoryDao.deleteConversationSummary(conversationId)
            memoryDao.deleteConversationSummarySegments(conversationId)
        }
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

    internal suspend fun MessageEntity.toDomain(): ChatMessage {
        val reasoningSteps = normalizeChatReasoningSteps(
            runCatching {
                gson.fromJson<List<ChatReasoningStep>>(reasoningStepsJson, reasoningStepListType)
                    .orEmpty()
            }.logFailure("RoomConvStore") { "reasoningSteps fromJson failed" }
                .getOrDefault(emptyList()).ifEmpty {
                    legacyReasoningStepsFromContent(
                        reasoningContent = reasoningContent,
                        createdAt = createdAt,
                    )
                },
        )
        val companionContext = companionSources.read(this)
        return ChatMessage(
            id = id,
            conversationId = conversationId,
            role = runCatching { MessageRole.valueOf(role) }.getOrDefault(MessageRole.USER),
            content = content,
            status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.COMPLETED),
            createdAt = createdAt,
            modelName = modelName,
            reasoningContent = reasoningContent.ifBlank { reasoningStepsToContent(reasoningSteps) },
            reasoningSteps = reasoningSteps,
            attachments = runCatching {
                gson.fromJson<List<MessageAttachment>>(attachmentsJson, attachmentListType)
                    .orEmpty()
            }.logFailure("RoomConvStore") { "attachments fromJson failed" }
                .getOrDefault(emptyList()),
            parts = normalizeChatMessageParts(
                runCatching {
                    gson.fromJson<List<ChatMessagePart>>(partsJson, partListType).orEmpty()
                }.logFailure("RoomConvStore") { "parts fromJson failed" }
                    .getOrDefault(emptyList()),
            ),
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
            speakerId = speakerId,
            speakerName = speakerName,
            speakerAvatarUri = speakerAvatarUri,
            isRecalled = isRecalled,
            systemEventKind = RoleplayOnlineEventKind.fromStorageValue(systemEventKind),
            citations = runCatching {
                gson.fromJson<List<MessageCitation>>(citationsJson, citationListType).orEmpty()
            }.logFailure("RoomConvStore") { "citations fromJson failed" }
                .getOrDefault(emptyList()),
            roleplayOutputFormat = runCatching {
                RoleplayOutputFormat.valueOf(roleplayOutputFormat)
            }.getOrDefault(RoleplayOutputFormat.UNSPECIFIED),
            roleplayInteractionMode = RoleplayInteractionMode.fromStorageValueOrNull(
                roleplayInteractionMode,
            ),
            memoryRevision = memoryRevision,
            memoryRecallRequestId = memoryRecallRequestId,
            companionTurnId = companionTurnId,
            companionMemoryState = com.example.myapplication.model.CompanionMemoryState.fromStorageValue(
                companionMemoryState
            ),
            companionReplyMetadata = companionContext.replyMetadata,
        ).withCompanionSourceProof(companionContext.eligibility)
    }

    internal fun ChatMessage.toEntity(): MessageEntity {
        val normalizedReasoningSteps = normalizeChatReasoningSteps(reasoningSteps)
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            role = role.name,
            content = content,
            status = status.name,
            createdAt = createdAt,
            modelName = modelName,
            reasoningContent = reasoningContent.ifBlank {
                reasoningStepsToContent(
                    normalizedReasoningSteps
                )
            },
            reasoningStepsJson = gson.toJson(normalizedReasoningSteps),
            attachmentsJson = gson.toJson(attachments),
            partsJson = gson.toJson(normalizeChatMessageParts(parts)),
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
            speakerId = speakerId,
            speakerName = speakerName,
            speakerAvatarUri = speakerAvatarUri,
            isRecalled = isRecalled,
            systemEventKind = systemEventKind.storageValue,
            citationsJson = gson.toJson(citations),
            roleplayOutputFormat = roleplayOutputFormat.name,
            roleplayInteractionMode = roleplayInteractionMode?.storageValue.orEmpty(),
            memoryRevision = memoryRevision,
            memoryRecallRequestId = memoryRecallRequestId,
            companionTurnId = companionTurnId,
            companionMemoryState = companionMemoryState.storageValue,
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
