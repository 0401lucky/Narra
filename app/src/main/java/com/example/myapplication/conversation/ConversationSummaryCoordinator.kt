package com.example.myapplication.conversation

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.toPlainText

data class SummaryGenerationConfig(
    val triggerMessageCount: Int,
    val recentMessageWindow: Int,
    val minCoveredMessageCount: Int,
    val segmentTargetCharacterCount: Int = 3_600,
    val maxSegmentsPerRun: Int = 3,
)

data class SummaryUpdateResult(
    val updated: Boolean = false,
    val summaryText: String = "",
    val coveredMessageCount: Int = 0,
)

internal fun resolveConversationSummaryModel(settings: AppSettings): String {
    return settings.resolveFunctionModel(ProviderFunction.TITLE_SUMMARY)
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
        val summaryProvider = settings.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY)
            ?: return SummaryUpdateResult()
        val summaryModel = resolveConversationSummaryModel(settings)
        if (summaryModel.isBlank()) {
            return SummaryUpdateResult()
        }
        val olderMessages = completedMessages.dropLast(config.recentMessageWindow)
        if (olderMessages.size < config.minCoveredMessageCount) {
            return SummaryUpdateResult()
        }
        val existingSummary = conversationSummaryRepository.getSummary(conversationId)
        val existingSegments = conversationSummaryRepository.listSummarySegments(conversationId)
        val existingCoveredPrefixCount = resolveCoveredPrefixCount(
            existingSummary = existingSummary,
            existingSegments = existingSegments,
            maxCoveredCount = olderMessages.size,
        )
        val coveredPrefixCount = if (forceRefresh && existingSegments.isEmpty()) {
            0
        } else {
            existingCoveredPrefixCount
        }
        if (!forceRefresh && existingSummary != null && existingCoveredPrefixCount >= olderMessages.size) {
            return SummaryUpdateResult(
                updated = false,
                summaryText = existingSummary.summary,
                coveredMessageCount = existingCoveredPrefixCount,
            )
        }
        val uncoveredMessages = olderMessages.drop(coveredPrefixCount)
        if (uncoveredMessages.isEmpty()) {
            if (forceRefresh && existingSegments.isNotEmpty()) {
                val summaryText = buildUpdatedTotalSummary(
                    existingSummary = existingSummary,
                    existingSegments = existingSegments,
                    generatedSegments = emptyList(),
                    summaryProviderBaseUrl = summaryProvider.baseUrl,
                    summaryProviderApiKey = summaryProvider.apiKey,
                    summaryModel = summaryModel,
                    summaryProviderProtocol = summaryProvider.resolvedApiProtocol(),
                    generateSummary = generateSummary,
                )
                conversationSummaryRepository.upsertSummary(
                    ConversationSummary(
                        conversationId = conversationId,
                        assistantId = assistantId,
                        summary = summaryText,
                        coveredMessageCount = existingCoveredPrefixCount,
                        updatedAt = nowProvider(),
                    ),
                )
                return SummaryUpdateResult(
                    updated = true,
                    summaryText = summaryText,
                    coveredMessageCount = existingCoveredPrefixCount,
                )
            }
            return SummaryUpdateResult()
        }
        val generatedSegments = mutableListOf<ConversationSummarySegment>()
        var generatedMessageCount = 0
        val chunks = chunkMessagesForSummary(
            messages = uncoveredMessages,
            targetCharacterCount = config.segmentTargetCharacterCount,
        ).take(config.maxSegmentsPerRun.coerceAtLeast(1))
        for (chunk in chunks) {
            val summaryInput = buildSummaryInput(chunk)
            if (summaryInput.isBlank()) {
                continue
            }
            val summaryText = generateSummary(
                summaryInput,
                summaryProvider.baseUrl,
                summaryProvider.apiKey,
                summaryModel,
                summaryProvider.resolvedApiProtocol(),
            ).trim()
            if (summaryText.isBlank()) {
                continue
            }
            val firstMessage = chunk.first()
            val lastMessage = chunk.last()
            val now = nowProvider()
            val segment = ConversationSummarySegment(
                id = buildSegmentId(
                    conversationId = conversationId,
                    startMessageId = firstMessage.id,
                    endMessageId = lastMessage.id,
                ),
                conversationId = conversationId,
                assistantId = assistantId,
                startMessageId = firstMessage.id,
                endMessageId = lastMessage.id,
                startCreatedAt = firstMessage.createdAt,
                endCreatedAt = lastMessage.createdAt,
                messageCount = chunk.size,
                summary = summaryText,
                createdAt = now,
                updatedAt = now,
            )
            conversationSummaryRepository.upsertSummarySegment(segment)
            generatedSegments += segment
            generatedMessageCount += chunk.size
        }
        if (generatedSegments.isEmpty()) {
            return SummaryUpdateResult()
        }
        val updatedCoveredMessageCount = coveredPrefixCount + generatedMessageCount
        val summaryText = buildUpdatedTotalSummary(
            existingSummary = existingSummary,
            existingSegments = existingSegments,
            generatedSegments = generatedSegments,
            summaryProviderBaseUrl = summaryProvider.baseUrl,
            summaryProviderApiKey = summaryProvider.apiKey,
            summaryModel = summaryModel,
            summaryProviderProtocol = summaryProvider.resolvedApiProtocol(),
            generateSummary = generateSummary,
        )
        conversationSummaryRepository.upsertSummary(
            ConversationSummary(
                conversationId = conversationId,
                assistantId = assistantId,
                summary = summaryText,
                coveredMessageCount = updatedCoveredMessageCount,
                updatedAt = nowProvider(),
            ),
        )
        return SummaryUpdateResult(
            updated = true,
            summaryText = summaryText,
            coveredMessageCount = updatedCoveredMessageCount,
        )
    }

    private fun resolveCoveredPrefixCount(
        existingSummary: ConversationSummary?,
        existingSegments: List<ConversationSummarySegment>,
        maxCoveredCount: Int,
    ): Int {
        val segmentCoveredCount = existingSegments.sumOf { it.messageCount.coerceAtLeast(0) }
        val summaryCoveredCount = existingSummary?.coveredMessageCount ?: 0
        return maxOf(segmentCoveredCount, summaryCoveredCount)
            .coerceIn(0, maxCoveredCount)
    }

    private fun chunkMessagesForSummary(
        messages: List<ChatMessage>,
        targetCharacterCount: Int,
    ): List<List<ChatMessage>> {
        if (messages.isEmpty()) {
            return emptyList()
        }
        val safeTarget = targetCharacterCount.coerceAtLeast(1_200)
        val chunks = mutableListOf<List<ChatMessage>>()
        val current = mutableListOf<ChatMessage>()
        var currentLength = 0
        messages.forEach { message ->
            val messageLength = estimateSummaryInputLength(message)
            if (current.isNotEmpty() && currentLength + messageLength > safeTarget) {
                chunks += current.toList()
                current.clear()
                currentLength = 0
            }
            current += message
            currentLength += messageLength
        }
        if (current.isNotEmpty()) {
            chunks += current.toList()
        }
        return chunks
    }

    private fun estimateSummaryInputLength(message: ChatMessage): Int {
        val contentLength = message.parts.toPlainText()
            .ifBlank { message.content }
            .trim()
            .length
        return contentLength + message.speakerName.length + 24
    }

    private suspend fun buildUpdatedTotalSummary(
        existingSummary: ConversationSummary?,
        existingSegments: List<ConversationSummarySegment>,
        generatedSegments: List<ConversationSummarySegment>,
        summaryProviderBaseUrl: String,
        summaryProviderApiKey: String,
        summaryModel: String,
        summaryProviderProtocol: com.example.myapplication.model.ProviderApiProtocol,
        generateSummary: suspend (conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: com.example.myapplication.model.ProviderApiProtocol) -> String,
    ): String {
        val normalizedExistingSummary = existingSummary?.summary.orEmpty().trim()
        if (normalizedExistingSummary.isBlank() && generatedSegments.size == 1 && existingSegments.isEmpty()) {
            return generatedSegments.single().summary.trim()
        }
        val summaryInput = buildString {
            if (normalizedExistingSummary.isNotBlank()) {
                append("【已有总摘要】\n")
                append(normalizedExistingSummary)
                append("\n\n")
            }
            val allRecentSegments = (existingSegments + generatedSegments)
                .sortedWith(compareBy({ it.startCreatedAt }, { it.endCreatedAt }))
                .takeLast(MAX_TOTAL_SUMMARY_SEGMENTS)
            append("【分段摘要档案】\n")
            allRecentSegments.forEachIndexed { index, segment ->
                append(index + 1)
                append(". 覆盖 ")
                append(segment.messageCount)
                append(" 条：")
                append(segment.summary.trim())
                append('\n')
            }
        }.trim()
        return generateSummary(
            summaryInput,
            summaryProviderBaseUrl,
            summaryProviderApiKey,
            summaryModel,
            summaryProviderProtocol,
        ).trim()
    }

    private fun buildSegmentId(
        conversationId: String,
        startMessageId: String,
        endMessageId: String,
    ): String {
        return "summary-segment-${(conversationId + ":" + startMessageId + ":" + endMessageId).hashCode()}"
    }

    companion object {
        private const val MAX_TOTAL_SUMMARY_SEGMENTS = 12
    }
}
