package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.ProviderFunction

class ConversationTitleCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val conversationRepository: ConversationRepository,
) {
    suspend fun updateConversationTitle(
        conversationId: String,
        messages: List<ChatMessage>,
        settings: AppSettings,
    ): String? {
        val titleProvider = settings.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY) ?: return null
        val titleModel = settings.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
        if (titleModel.isBlank()) {
            return null
        }
        val firstUserMessage = messages.firstOrNull { it.role == MessageRole.USER }
            ?.content
            ?.trim()
            .orEmpty()
        if (firstUserMessage.isBlank()) {
            return null
        }
        val aiTitle = aiPromptExtrasService.generateTitle(
            firstUserMessage = firstUserMessage,
            baseUrl = titleProvider.baseUrl,
            apiKey = titleProvider.apiKey,
            modelId = titleModel,
            apiProtocol = titleProvider.resolvedApiProtocol(),
            provider = titleProvider,
        )
        conversationRepository.updateConversationTitle(conversationId, aiTitle)
        return titleModel
    }
}
