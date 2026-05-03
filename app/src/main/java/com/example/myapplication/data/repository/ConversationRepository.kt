package com.example.myapplication.data.repository

import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.data.local.ConversationStore
import com.example.myapplication.data.repository.phone.EmptyPhoneSnapshotRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.RoleplayOnlineEventKind
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ConversationRepository(
    private val conversationStore: ConversationStore,
    private val phoneSnapshotRepository: PhoneSnapshotRepository = EmptyPhoneSnapshotRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun observeConversations(): Flow<List<Conversation>> {
        return conversationStore.observeConversations()
    }

    fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>> {
        return conversationStore.observeConversationsByAssistant(assistantId)
    }

    fun observeMessages(conversationId: String): Flow<List<ChatMessage>> {
        return conversationStore.observeMessages(conversationId)
    }

    suspend fun ensureActiveConversation(
        currentConversationId: String?,
        assistantId: String = DEFAULT_ASSISTANT_ID,
    ): Conversation {
        val existingConversations = conversationStore.listConversations()
            .filter { it.matchesAssistant(assistantId) }
        if (existingConversations.isEmpty()) {
            return createConversation(assistantId = assistantId)
        }
        currentConversationId?.let { conversationId ->
            existingConversations.firstOrNull { it.id == conversationId }?.let { return it }
        }
        return existingConversations.first()
    }

    suspend fun createConversation(
        selectedModel: String = "",
        assistantId: String = DEFAULT_ASSISTANT_ID,
        searchEnabled: Boolean = false,
    ): Conversation {
        val timestamp = nowProvider()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = DEFAULT_CONVERSATION_TITLE,
            model = selectedModel,
            createdAt = timestamp,
            updatedAt = timestamp,
            assistantId = assistantId,
            searchEnabled = searchEnabled,
        )
        conversationStore.upsertConversationMetadata(conversation)
        return conversation
    }

    suspend fun getConversation(conversationId: String): Conversation? {
        return conversationStore.getConversation(conversationId)
    }

    suspend fun listMessages(conversationId: String): List<ChatMessage> {
        return conversationStore.listMessages(conversationId)
    }

    suspend fun appendMessages(
        conversationId: String,
        messages: List<ChatMessage>,
        selectedModel: String,
    ): Conversation {
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = resolvePartialConversationTitle(
                currentConversation = currentConversation,
                messages = messages,
            ),
        )
        conversationStore.upsertConversationWithMessages(
            conversation = updatedConversation,
            messages = messages,
        )
        return updatedConversation
    }

    suspend fun upsertMessages(
        conversationId: String,
        messages: List<ChatMessage>,
        selectedModel: String,
    ): Conversation {
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = resolvePartialConversationTitle(
                currentConversation = currentConversation,
                messages = messages,
            ),
        )
        conversationStore.upsertConversationWithMessages(
            conversation = updatedConversation,
            messages = messages,
        )
        return updatedConversation
    }

    suspend fun applyTransferUpdates(
        conversationId: String,
        updates: List<TransferUpdateDirective>,
        selectedModel: String,
    ): List<ChatMessage> {
        if (updates.isEmpty()) {
            return conversationStore.listMessages(conversationId)
        }
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
        )
        return conversationStore.updateConversationMessages(
            conversation = updatedConversation,
            conversationId = conversationId,
        ) { existingMessages ->
            ConversationMessageTransforms.applyTransferUpdates(
                messages = existingMessages,
                updates = updates,
            )
        }
    }

    suspend fun updateGiftImagePart(
        conversationId: String,
        specialId: String,
        selectedModel: String,
        transform: (ChatMessagePart) -> ChatMessagePart,
    ): List<ChatMessage> {
        if (conversationId.isBlank() || specialId.isBlank()) {
            return conversationStore.listMessages(conversationId)
        }
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
        )
        return conversationStore.updateConversationMessages(
            conversation = updatedConversation,
            conversationId = conversationId,
        ) { existingMessages ->
            ConversationMessageTransforms.applyGiftImageUpdate(
                messages = existingMessages,
                specialId = specialId,
                transform = transform,
            )
        }
    }

    suspend fun updateVoiceMessagePart(
        conversationId: String,
        messageId: String,
        actionId: String,
        selectedModel: String,
        transform: (ChatMessagePart) -> ChatMessagePart,
    ): List<ChatMessage> {
        if (conversationId.isBlank() || messageId.isBlank() || actionId.isBlank()) {
            return conversationStore.listMessages(conversationId)
        }
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
        )
        return conversationStore.updateConversationMessages(
            conversation = updatedConversation,
            conversationId = conversationId,
        ) { existingMessages ->
            ConversationMessageTransforms.applyVoiceMessageUpdate(
                messages = existingMessages,
                messageId = messageId,
                actionId = actionId,
                transform = transform,
            )
        }
    }

    suspend fun replaceConversationSnapshot(
        conversationId: String,
        messages: List<ChatMessage>,
        selectedModel: String,
    ): Conversation {
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = buildConversationTitle(messages, currentConversation.title),
        )
        conversationStore.replaceConversationSnapshot(
            conversation = updatedConversation,
            conversationId = conversationId,
            messages = messages,
        )
        return updatedConversation
    }

    /** 异步更新会话标题（用于 AI 生成标题后回写）。 */
    suspend fun updateConversationTitle(conversationId: String, title: String) {
        val conversation = conversationStore.getConversation(conversationId) ?: return
        conversationStore.upsertConversationMetadata(
            conversation.copy(
                title = title,
                updatedAt = nowProvider(),
                assistantId = conversation.assistantId.ifBlank { DEFAULT_ASSISTANT_ID },
            ),
        )
    }

    suspend fun updateConversationSearchEnabled(
        conversationId: String,
        searchEnabled: Boolean,
    ) {
        val conversation = conversationStore.getConversation(conversationId) ?: return
        if (conversation.searchEnabled == searchEnabled) {
            return
        }
        conversationStore.upsertConversationMetadata(
            conversation.copy(
                searchEnabled = searchEnabled,
            ),
        )
    }

    suspend fun clearConversation(conversationId: String, selectedModel: String): Conversation {
        val currentConversation = conversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("当前会话不存在")
        val updatedConversation = currentConversation.copy(
            title = DEFAULT_CONVERSATION_TITLE,
            model = selectedModel,
            updatedAt = nowProvider(),
            assistantId = currentConversation.assistantId.ifBlank { DEFAULT_ASSISTANT_ID },
        )
        conversationStore.clearMessagesAndUpdateConversation(
            conversationId = conversationId,
            conversation = updatedConversation,
        )
        phoneSnapshotRepository.deleteSnapshot(conversationId)
        phoneSnapshotRepository.deleteObservation(conversationId)
        return updatedConversation
    }

    suspend fun deleteConversation(
        conversationId: String,
        selectedModel: String,
        assistantId: String = DEFAULT_ASSISTANT_ID,
    ): Conversation {
        conversationStore.deleteConversation(conversationId)
        phoneSnapshotRepository.deleteSnapshot(conversationId)
        phoneSnapshotRepository.deleteObservation(conversationId)
        val remainingConversations = conversationStore.listConversations()
            .filter { it.matchesAssistant(assistantId) }
        return remainingConversations.firstOrNull()
            ?: createConversation(selectedModel, assistantId)
    }

    suspend fun deleteConversationById(conversationId: String) {
        conversationStore.deleteConversation(conversationId)
        phoneSnapshotRepository.deleteSnapshot(conversationId)
        phoneSnapshotRepository.deleteObservation(conversationId)
    }

    suspend fun recallMessage(
        conversationId: String,
        messageId: String,
        selectedModel: String,
    ): List<ChatMessage> {
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
        )
        return conversationStore.updateConversationMessages(
            conversation = updatedConversation,
            conversationId = conversationId,
        ) { existingMessages ->
            existingMessages.map { message ->
                if (message.id == messageId) {
                    message.copy(
                        content = "你撤回了一条消息",
                        isRecalled = true,
                        systemEventKind = RoleplayOnlineEventKind.RECALL,
                        parts = emptyList(),
                        replyToMessageId = "",
                        replyToPreview = "",
                        replyToSpeakerName = "",
                    )
                } else {
                    message
                }
            }
        }
    }

    suspend fun recallLatestAssistantMessage(
        conversationId: String,
        selectedModel: String,
        excludingMessageId: String = "",
    ): List<ChatMessage> {
        val currentConversation = requireConversation(conversationId)
        val updatedConversation = buildUpdatedConversation(
            currentConversation = currentConversation,
            selectedModel = selectedModel,
            title = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE },
        )
        return conversationStore.updateConversationMessages(
            conversation = updatedConversation,
            conversationId = conversationId,
        ) { existingMessages ->
            val targetMessageId = existingMessages
                .lastOrNull { message ->
                    message.role == MessageRole.ASSISTANT &&
                        message.status == MessageStatus.COMPLETED &&
                        !message.isRecalled &&
                        message.id != excludingMessageId
                }
                ?.id
                .orEmpty()
            if (targetMessageId.isBlank()) {
                return@updateConversationMessages existingMessages
            }
            existingMessages.map { message ->
                if (message.id == targetMessageId) {
                    message.copy(
                        content = "对方撤回了一条消息",
                        isRecalled = true,
                        systemEventKind = RoleplayOnlineEventKind.RECALL,
                        parts = emptyList(),
                        replyToMessageId = "",
                        replyToPreview = "",
                        replyToSpeakerName = "",
                    )
                } else {
                    message
                }
            }
        }
    }

    suspend fun appendSystemEventMessage(
        conversationId: String,
        message: ChatMessage,
        selectedModel: String,
    ): Conversation {
        return appendMessages(
            conversationId = conversationId,
            messages = listOf(message),
            selectedModel = selectedModel,
        )
    }

    private fun buildConversationTitle(
        messages: List<ChatMessage>,
        fallbackTitle: String,
    ): String {
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.trim()
            .orEmpty()
        if (firstUserMessage.isBlank()) {
            return fallbackTitle.ifBlank { DEFAULT_CONVERSATION_TITLE }
        }
        return firstUserMessage.take(TITLE_MAX_LENGTH)
    }

    private fun resolvePartialConversationTitle(
        currentConversation: Conversation,
        messages: List<ChatMessage>,
    ): String {
        val currentTitle = currentConversation.title.ifBlank { DEFAULT_CONVERSATION_TITLE }
        if (currentTitle != DEFAULT_CONVERSATION_TITLE) {
            return currentTitle
        }
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.trim()
            .orEmpty()
        if (firstUserMessage.isBlank()) {
            return currentTitle
        }
        return firstUserMessage.take(TITLE_MAX_LENGTH)
    }

    private suspend fun requireConversation(conversationId: String): Conversation {
        return conversationStore.getConversation(conversationId)
            ?: throw IllegalStateException("当前会话不存在")
    }

    private fun buildUpdatedConversation(
        currentConversation: Conversation,
        selectedModel: String,
        title: String,
    ): Conversation {
        return currentConversation.copy(
            title = title,
            model = selectedModel,
            updatedAt = nowProvider(),
            assistantId = currentConversation.assistantId.ifBlank { DEFAULT_ASSISTANT_ID },
        )
    }

    private companion object {
        const val TITLE_MAX_LENGTH = 20
    }
}

private fun Conversation.matchesAssistant(assistantId: String): Boolean {
    return this.assistantId == assistantId ||
        (
            assistantId == DEFAULT_ASSISTANT_ID &&
                this.assistantId.isBlank()
            )
}
