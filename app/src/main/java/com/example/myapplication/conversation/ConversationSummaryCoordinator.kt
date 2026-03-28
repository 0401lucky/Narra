package com.example.myapplication.conversation

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ProviderFunction

data class SummaryGenerationConfig(
    val triggerMessageCount: Int,
    val recentMessageWindow: Int,
    val minCoveredMessageCount: Int,
)

class ConversationSummaryCoordinator(
    private val aiPromptExtrasService: AiPromptExtrasService,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun updateConversationSummary(
        conversationId: String,
        assistantId: String,
        completedMessages: List<ChatMessage>,
        settings: AppSettings,
        config: SummaryGenerationConfig,
        buildSummaryInput: (List<ChatMessage>) -> String,
        generateSummary: suspend (conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol) -> String,
    ): Boolean {
        if (completedMessages.size <= config.triggerMessageCount) {
            return false
        }
        val activeProvider = settings.activeProvider() ?: return false
        val summaryModel = activeProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
        if (summaryModel.isBlank()) {
            return false
        }
        val olderMessages = completedMessages.dropLast(config.recentMessageWindow)
        if (olderMessages.size < config.minCoveredMessageCount) {
            return false
        }
        val existingSummary = conversationSummaryRepository.getSummary(conversationId)
        if (existingSummary != null && existingSummary.coveredMessageCount >= olderMessages.size) {
            return false
        }
        val summaryInput = buildSummaryInput(olderMessages)
        if (summaryInput.isBlank()) {
            return false
        }
        val summaryText = generateSummary(
            summaryInput,
            activeProvider.baseUrl,
            activeProvider.apiKey,
            summaryModel,
            activeProvider.resolvedApiProtocol(),
        )
        conversationSummaryRepository.upsertSummary(
            ConversationSummary(
                conversationId = conversationId,
                assistantId = assistantId,
                summary = summaryText,
                coveredMessageCount = olderMessages.size,
                updatedAt = nowProvider(),
            ),
        )
        return true
    }
}
