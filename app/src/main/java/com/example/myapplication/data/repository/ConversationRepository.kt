package com.example.myapplication.data.repository

import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.data.local.ConversationStore
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.DEFAULT_ASSISTANT_ID
import com.example.myapplication.model.DEFAULT_CONVERSATION_TITLE
import com.example.myapplication.model.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ConversationRepository(
    private val conversationStore: ConversationStore,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    fun observeConversations(): Flow<List<Conversation>> {
        return conversationStore.observeConversations()
    }

    fun observeConversationsByAssistant(assistantId: String): Flow<List<Conversation>> {
        return conversationStore.observeConversations().map { conversations ->
            conversations.filter { conversation ->
                conversation.matchesAssistant(assistantId)
            }
        }
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
    ): Conversation {
        val timestamp = nowProvider()
        val conversation = Conversation(
            id = UUID.randomUUID().toString(),
            title = DEFAULT_CONVERSATION_TITLE,
            model = selectedModel,
            createdAt = timestamp,
            updatedAt = timestamp,
            assistantId = assistantId,
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
        return updatedConversation
    }

    suspend fun deleteConversation(
        conversationId: String,
        selectedModel: String,
        assistantId: String = DEFAULT_ASSISTANT_ID,
    ): Conversation {
        conversationStore.deleteConversation(conversationId)
        val remainingConversations = conversationStore.listConversations()
            .filter { it.matchesAssistant(assistantId) }
        return remainingConversations.firstOrNull()
            ?: createConversation(selectedModel, assistantId)
    }

    suspend fun deleteConversationById(conversationId: String) {
        conversationStore.deleteConversation(conversationId)
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
