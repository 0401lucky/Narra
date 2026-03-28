package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.context.MemoryRepository
import com.example.myapplication.model.MemoryEntry
import com.example.myapplication.model.MemoryScopeType
import com.example.myapplication.model.toPlainText
import java.util.UUID

sealed interface ChatMemoryToggleResult {
    data class Error(
        val message: String,
        val shouldEnsureConversation: Boolean = false,
    ) : ChatMemoryToggleResult

    data class Notice(val message: String) : ChatMemoryToggleResult

    data object NoOp : ChatMemoryToggleResult
}

class ChatMessageMemoryCoordinator(
    private val memoryRepository: MemoryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
    private val entryIdProvider: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun toggle(
        state: ChatUiState,
        fallbackAssistantId: String,
        messageId: String,
    ): ChatMemoryToggleResult {
        if (!state.isConversationReady) {
            return ChatMemoryToggleResult.Error("会话切换中，请稍后再操作记忆")
        }
        val conversationId = state.currentConversationId.takeIf { it.isNotBlank() }
            ?: return ChatMemoryToggleResult.Error(
                message = "会话初始化中，请稍后重试",
                shouldEnsureConversation = true,
            )
        val targetMessage = state.messages
            .filter { it.conversationId == conversationId }
            .firstOrNull { it.id == messageId }
            ?: return ChatMemoryToggleResult.NoOp
        val memoryContent = targetMessage.parts.toPlainText()
            .ifBlank { targetMessage.content.trim() }
            .trim()
        if (memoryContent.isBlank()) {
            return ChatMemoryToggleResult.Error("当前消息没有可记忆的文本内容")
        }

        val assistantId = state.currentAssistant?.id
            ?.trim()
            .orEmpty()
            .ifBlank { fallbackAssistantId }
        val scope = if (state.currentAssistant?.useGlobalMemory == true) {
            MemoryScopeType.GLOBAL to ""
        } else if (assistantId.isNotBlank()) {
            MemoryScopeType.ASSISTANT to assistantId
        } else {
            MemoryScopeType.CONVERSATION to conversationId
        }

        val existingEntry = memoryRepository.findEntryBySourceMessage(
            scopeType = scope.first,
            scopeId = scope.second,
            sourceMessageId = messageId,
        )
        if (existingEntry != null) {
            memoryRepository.deleteEntry(existingEntry.id)
            return ChatMemoryToggleResult.Notice("已取消记忆")
        }

        val timestamp = nowProvider()
        memoryRepository.upsertEntry(
            MemoryEntry(
                id = entryIdProvider(),
                scopeType = scope.first,
                scopeId = scope.second,
                content = memoryContent,
                importance = 80,
                pinned = true,
                sourceMessageId = messageId,
                lastUsedAt = timestamp,
                createdAt = timestamp,
                updatedAt = timestamp,
            ),
        )
        return ChatMemoryToggleResult.Notice("已记住这条")
    }
}
