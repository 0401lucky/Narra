package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction

data class ChatSuggestionResult(
    val suggestions: List<String>,
    val modelName: String,
)

class ChatSuggestionCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
) {
    suspend fun generateSuggestions(
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): ChatSuggestionResult? {
        val activeProvider = settings.activeProvider() ?: return null
        val suggestionModel = activeProvider.resolveFunctionModel(ProviderFunction.CHAT_SUGGESTION)
        if (suggestionModel.isBlank()) {
            return null
        }
        val lastMessages = messages.takeLast(4)
        val summary = lastMessages.joinToString("\n") { msg ->
            val role = if (msg.role == MessageRole.USER) "用户" else "助手"
            "$role: ${msg.content.take(200)}"
        }
        val suggestions = aiPromptExtrasService.generateChatSuggestions(
            conversationSummary = summary,
            baseUrl = activeProvider.baseUrl,
            apiKey = activeProvider.apiKey,
            modelId = suggestionModel,
            apiProtocol = activeProvider.resolvedApiProtocol(),
            provider = activeProvider,
        )
        return ChatSuggestionResult(
            suggestions = suggestions,
            modelName = suggestionModel,
        )
    }
}
