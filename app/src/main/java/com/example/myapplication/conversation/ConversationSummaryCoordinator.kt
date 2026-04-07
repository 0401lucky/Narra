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

data class SummaryUpdateResult(
    val updated: Boolean = false,
    val summaryText: String = "",
    val coveredMessageCount: Int = 0,
)

internal fun resolveConversationSummaryModel(settings: AppSettings): String {
    val activeProvider = settings.activeProvider() ?: return ""
    return activeProvider.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
}

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
        forceRefresh: Boolean = false,
        buildSummaryInput: (List<ChatMessage>) -> String,
        generateSummary: suspend (conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol) -> String,
    ): SummaryUpdateResult {
        if (completedMessages.size <= config.triggerMessageCount) {
            return SummaryUpdateResult()
        }
        val activeProvider = settings.activeProvider() ?: return SummaryUpdateResult()
        val summaryModel = resolveConversationSummaryModel(settings)
        if (summaryModel.isBlank()) {
            return SummaryUpdateResult()
        }
        val olderMessages = completedMessages.dropLast(config.recentMessageWindow)
        if (olderMessages.size < config.minCoveredMessageCount) {
            return SummaryUpdateResult()
        }
        val existingSummary = conversationSummaryRepository.getSummary(conversationId)
        if (!forceRefresh &&
            existingSummary != null &&
            existingSummary.coveredMessageCount >= olderMessages.size
        ) {
            return SummaryUpdateResult(
                updated = false,
                summaryText = existingSummary.summary,
                coveredMessageCount = existingSummary.coveredMessageCount,
            )
        }
        val summaryInput = buildSummaryInput(olderMessages)
        if (summaryInput.isBlank()) {
            return SummaryUpdateResult()
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
        return SummaryUpdateResult(
            updated = true,
            summaryText = summaryText,
            coveredMessageCount = olderMessages.size,
        )
    }
}
