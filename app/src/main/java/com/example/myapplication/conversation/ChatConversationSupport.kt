package com.example.myapplication.conversation

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.toMessageAttachments
import com.example.myapplication.model.toPlainText

data class ChatPromptAssemblyInput(
    val settings: AppSettings,
    val assistant: Assistant?,
    val conversation: Conversation,
    val userInputText: String,
    val recentMessages: List<ChatMessage>,
)

data class PreparedChatRoundTrip(
    val userMessage: ChatMessage,
    val loadingMessage: ChatMessage,
    val persistedMessages: List<ChatMessage>,
    val requestMessages: List<ChatMessage>,
)

object ChatConversationSupport {
    fun currentConversationMessages(
        messages: List<ChatMessage>,
        conversationId: String,
    ): List<ChatMessage> {
        return messages.filter { it.conversationId == conversationId }
    }

    fun buildConversationExcerpt(
        messages: List<ChatMessage>,
        maxLength: Int,
        perMessageLimit: Int,
    ): String {
        return messages.joinToString(separator = "\n") { message ->
            val role = if (message.role == MessageRole.USER) "用户" else "助手"
            val content = message.parts.toPlainText()
                .ifBlank { message.content }
                .trim()
                .take(perMessageLimit)
            "$role: $content"
        }.take(maxLength)
    }

    fun resolveSelectedModelId(settings: AppSettings): String {
        return settings.activeProvider()?.selectedModel
            ?.takeIf { it.isNotBlank() }
            ?: settings.selectedModel
    }

    fun supportsImageGeneration(
        settings: AppSettings,
        modelId: String,
    ): Boolean {
        if (modelId.isBlank()) {
            return false
        }
        val abilities = settings.activeProvider()?.resolveModelAbilities(modelId)
            ?: inferModelAbilities(modelId)
        return ModelAbility.IMAGE_GENERATION in abilities
    }

    fun supportsVisionInput(
        settings: AppSettings,
        modelId: String,
    ): Boolean {
        if (modelId.isBlank()) {
            return false
        }
        val abilities = settings.activeProvider()?.resolveModelAbilities(modelId)
            ?: inferModelAbilities(modelId)
        return ModelAbility.VISION in abilities
    }

    fun validateOutgoingParts(
        settings: AppSettings,
        userParts: List<ChatMessagePart>,
    ): String? {
        val normalizedParts = normalizeChatMessageParts(userParts)
        if (normalizedParts.isEmpty()) {
            return null
        }
        val selectedModel = resolveSelectedModelId(settings)
        if (selectedModel.isBlank()) {
            return null
        }
        val hasImageParts = normalizedParts.any { it.type == ChatMessagePartType.IMAGE }
        val hasFileParts = normalizedParts.any { it.type == ChatMessagePartType.FILE }
        if (supportsImageGeneration(settings, selectedModel) && (hasImageParts || hasFileParts)) {
            return "当前模型为生图模型，仅支持文本提示词。请切换到聊天模型后再发送附件"
        }
        if (hasImageParts && !supportsVisionInput(settings, selectedModel)) {
            return "当前模型不支持图片理解，请切换到支持视觉的模型后再发送图片"
        }
        return null
    }

    fun validateTransferPlayAvailability(
        settings: AppSettings,
    ): String? {
        val selectedModel = resolveSelectedModelId(settings)
        if (selectedModel.isBlank()) {
            return null
        }
        if (!supportsImageGeneration(settings, selectedModel)) {
            return null
        }
        return "当前模型为生图模型，不支持特殊玩法。请切换到聊天模型后再继续"
    }

    fun buildUserMessageParts(
        text: String,
        pendingParts: List<ChatMessagePart>,
    ): List<ChatMessagePart> {
        return normalizeChatMessageParts(
            buildList {
                if (text.isNotBlank()) {
                    add(textMessagePart(text))
                }
                addAll(pendingParts.filter { it.type != ChatMessagePartType.TEXT })
            },
        )
    }

    fun prepareOutgoingRoundTrip(
        baseMessages: List<ChatMessage>,
        conversationId: String,
        userParts: List<ChatMessagePart>,
        selectedModel: String,
        nowProvider: () -> Long,
        messageIdProvider: () -> String,
    ): PreparedChatRoundTrip {
        val userMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.USER,
            content = userParts.toContentMirror(
                imageFallback = "图片已发送",
                fileFallback = "文件已附加",
                specialFallback = "转账",
            ),
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
            attachments = userParts.toMessageAttachments(),
            parts = userParts,
        )
        val loadingMessage = buildMessage(
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
            status = MessageStatus.LOADING,
            modelName = selectedModel,
        )
        return PreparedChatRoundTrip(
            userMessage = userMessage,
            loadingMessage = loadingMessage,
            persistedMessages = baseMessages + userMessage + loadingMessage,
            requestMessages = baseMessages.filter { it.status == MessageStatus.COMPLETED } + userMessage,
        )
    }

    fun buildPromptAssemblyInput(
        settings: AppSettings,
        currentAssistant: Assistant?,
        currentConversations: List<Conversation>,
        fallbackAssistantId: String,
        conversationId: String,
        requestMessages: List<ChatMessage>,
        nowProvider: () -> Long,
    ): ChatPromptAssemblyInput {
        val timestamp = nowProvider()
        val resolvedConversation = currentConversations.firstOrNull { it.id == conversationId }
            ?: Conversation(
                id = conversationId,
                createdAt = timestamp,
                updatedAt = timestamp,
                assistantId = fallbackAssistantId,
            )
        val latestUserMessage = requestMessages.lastOrNull { it.role == MessageRole.USER }
        return ChatPromptAssemblyInput(
            settings = settings,
            assistant = currentAssistant ?: settings.activeAssistant(),
            conversation = resolvedConversation,
            userInputText = latestUserMessage
                ?.parts
                ?.toPlainText()
                .orEmpty()
                .ifBlank { latestUserMessage?.content.orEmpty() },
            recentMessages = requestMessages,
        )
    }

    fun buildMessage(
        conversationId: String,
        role: MessageRole,
        content: String,
        nowProvider: () -> Long,
        messageIdProvider: () -> String,
        status: MessageStatus = MessageStatus.COMPLETED,
        modelName: String = "",
        reasoningContent: String = "",
        attachments: List<MessageAttachment> = emptyList(),
        parts: List<ChatMessagePart> = emptyList(),
    ): ChatMessage {
        return ChatMessage(
            id = messageIdProvider(),
            conversationId = conversationId,
            role = role,
            content = content,
            status = status,
            createdAt = nowProvider(),
            modelName = modelName,
            reasoningContent = reasoningContent,
            attachments = attachments,
            parts = normalizeChatMessageParts(parts),
        )
    }
}
