package com.example.myapplication.conversation

import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.ai.AiPromptExtrasService
import com.example.myapplication.context.MemoryArchiveIdentity
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.companionSourceEligibility
import com.example.myapplication.model.CompanionSourceEligibility
import com.example.myapplication.model.ConversationSummary
import com.example.myapplication.model.ConversationSummarySegment
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.MemoryRuntimeContext
import com.example.myapplication.model.PromptCompletionResult
import com.example.myapplication.model.PromptCompletionStatus
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunction
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        generateSummary: suspend (conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: ProviderApiProtocol) -> PromptCompletionResult,
        expectedMemoryContext: MemoryRuntimeContext? = null,
        isSourceAllowed: (ChatMessage) -> Boolean = { expectedMemoryContext == null },
    ): SummaryUpdateResult {
        if (expectedMemoryContext != null && (
                    !ConversationSummaryCoverageSupport.isValidMemoryContext(expectedMemoryContext) ||
                            expectedMemoryContext.conversationId != conversationId || expectedMemoryContext.characterId != assistantId
                    )
        ) return SummaryUpdateResult()
        // 保留真实消息位置；ERROR/LOADING 也是覆盖缺口，不能先过滤后宣称前缀连续。
        val sourceMessages = completedMessages
        if (!forceRefresh && sourceMessages.count { it.status == MessageStatus.COMPLETED } <= config.triggerMessageCount) {
            return SummaryUpdateResult()
        }
        val summaryProvider = settings.resolveFunctionProvider(ProviderFunction.TITLE_SUMMARY)
            ?: return SummaryUpdateResult()
        val summaryModel = resolveConversationSummaryModel(settings)
        if (summaryModel.isBlank()) {
            return SummaryUpdateResult()
        }
        val olderMessages = sourceMessages.dropLast(config.recentMessageWindow.coerceAtLeast(0))
        val messagesForSummary =
            if (forceRefresh && olderMessages.size < config.minCoveredMessageCount) {
                sourceMessages
            } else {
                olderMessages
            }
        if (messagesForSummary.size < config.minCoveredMessageCount &&
            !(forceRefresh && messagesForSummary.isNotEmpty())
        ) {
            return SummaryUpdateResult()
        }
        val storedSummary =
            if (expectedMemoryContext == null) conversationSummaryRepository.getSummary(
                conversationId
            )
            else conversationSummaryRepository.getSummaryForContext(expectedMemoryContext)
        val allowedIds = sourceMessages.filter(isSourceAllowed).map { it.id }.toSet()
        val existingSummary =
            storedSummary?.takeIf { summary -> summary.sourceSnapshot.all { it.messageId in allowedIds } }
        val existingSegments = conversationSummaryRepository.listSummarySegments(conversationId)
        val reusableSegments = existingSegments.filter { segment ->
            ConversationSummaryCoverageSupport.segmentRange(
                segment,
                sourceMessages,
                expectedMemoryContext
            ) != null &&
                    segment.sourceSnapshot.all { it.messageId in allowedIds } &&
                    (!forceRefresh || segment.completionStatus == PromptCompletionStatus.COMPLETED)
        }
        val reusableSummary = existingSummary.takeIf {
            !forceRefresh || reusableSegments.isNotEmpty() && it?.completionStatus == PromptCompletionStatus.COMPLETED
        }
        val processedCount = ConversationSummaryCoverageSupport.processedSummaryCoverage(
            reusableSummary,
            sourceMessages,
            expectedMemoryContext
        )
        if (!forceRefresh && processedCount >= messagesForSummary.size) return currentResult(
            existingSummary,
            sourceMessages,
            expectedMemoryContext
        )

        val alreadyProcessed = BooleanArray(messagesForSummary.size) { it < processedCount }
        reusableSegments.forEach { segment ->
            ConversationSummaryCoverageSupport.segmentRange(
                segment,
                sourceMessages,
                expectedMemoryContext
            )?.forEach { index ->
                if (index < alreadyProcessed.size) alreadyProcessed[index] = true
            }
        }
        val uncoveredRuns = mutableListOf<List<ChatMessage>>()
        var run = mutableListOf<ChatMessage>()
        for ((index, message) in messagesForSummary.withIndex()) {
            if (!ConversationSummaryCoverageSupport.canSummarize(message) || message.id !in allowedIds || alreadyProcessed[index]) {
                if (run.isNotEmpty()) uncoveredRuns += run.toList()
                run = mutableListOf()
            } else {
                run += message
            }
        }
        if (run.isNotEmpty()) uncoveredRuns += run.toList()
        val generatedSegments = mutableListOf<ConversationSummarySegment>()
        val chunks = uncoveredRuns.flatMap { messages ->
            chunkMessagesForSummary(messages, config.segmentTargetCharacterCount)
        }.take(config.maxSegmentsPerRun.coerceAtLeast(1))
        for (chunk in chunks) {
            currentCoroutineContext().ensureActive()
            val frozenSources = ConversationSummaryCoverageSupport.sourceSnapshot(chunk)
            val textChunk = chunk.filter {
                it.companionSourceEligibility() == CompanionSourceEligibility.TEXT_ALLOWED
            }
            val summaryInput = ConversationSummaryCoverageSupport.completeInput(
                chunk,
                buildSummaryInput(textChunk),
                isSourceAllowed
            )
            if (summaryInput.isBlank() && textChunk.isNotEmpty()) break
            val result = if (textChunk.isEmpty()) PromptCompletionResult(
                ConversationSummaryCoverageSupport.CONTROL_ONLY_SUMMARY,
                PromptCompletionStatus.COMPLETED,
            ) else generateSummary(
                summaryInput,
                summaryProvider.baseUrl,
                summaryProvider.apiKey,
                summaryModel,
                summaryProvider.resolvedApiProtocol(),
            )
            currentCoroutineContext().ensureActive()
            requireAcceptedCompletion(result)
            val summaryText = result.content.trim()
            if (summaryText.isBlank()) break
            val firstMessage = chunk.first()
            val lastMessage = chunk.last()
            val now = nowProvider()
            val segment = ConversationSummarySegment(
                id = existingSegments.firstOrNull {
                    it.startMessageId == firstMessage.id && it.endMessageId == lastMessage.id && it.memoryContext == expectedMemoryContext
                }?.id ?: buildSegmentId(
                    conversationId = conversationId,
                    startMessageId = firstMessage.id,
                    endMessageId = lastMessage.id,
                    memoryContext = expectedMemoryContext,
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
                sourceSnapshot = frozenSources,
                completionStatus = result.status,
                memoryContext = expectedMemoryContext,
            )
            if (!conversationSummaryRepository.saveSummarySegmentIfSourcesCurrent(
                    segment,
                    storedSummary
                )
            ) {
                return SummaryUpdateResult()
            }
            generatedSegments += segment
        }
        val candidateSegments =
            (reusableSegments + generatedSegments).associateBy { it.id }.values.toList()
        val coverage = ConversationSummaryCoverageSupport.continuousCoverage(
            sourceMessages,
            reusableSummary,
            candidateSegments,
            expectedMemoryContext = expectedMemoryContext,
        )
        if (coverage.coveredMessageCount == 0 || !forceRefresh &&
            ConversationSummaryCoverageSupport.processedSummaryCoverage(
                existingSummary,
                sourceMessages,
                expectedMemoryContext
            ) >= coverage.coveredMessageCount
        ) return currentResult(existingSummary, sourceMessages, expectedMemoryContext)

        val mergeSegments = if (forceRefresh) {
            candidateSegments.filter { segment ->
                val range = ConversationSummaryCoverageSupport.segmentRange(
                    segment,
                    sourceMessages,
                    expectedMemoryContext
                )
                range != null && range.last < coverage.coveredMessageCount
            }
        } else coverage.segments
        val result = buildUpdatedTotalSummary(
            existingSummary = coverage.summary,
            segments = mergeSegments,
            summaryProviderBaseUrl = summaryProvider.baseUrl,
            summaryProviderApiKey = summaryProvider.apiKey,
            summaryModel = summaryModel,
            summaryProviderProtocol = summaryProvider.resolvedApiProtocol(),
            generateSummary = generateSummary,
        )
        currentCoroutineContext().ensureActive()
        requireAcceptedCompletion(result)
        val summaryText = result.content.trim()
        if (summaryText.isBlank()) return currentResult(
            existingSummary,
            sourceMessages,
            expectedMemoryContext
        )
        // 一次完整合并不能替 UNKNOWN 成员补造结束证据。
        val completionStatus = if (result.status == PromptCompletionStatus.COMPLETED &&
            coverage.completionStatus == PromptCompletionStatus.COMPLETED
        ) PromptCompletionStatus.COMPLETED else PromptCompletionStatus.UNKNOWN
        val updatedCoveredMessageCount =
            if (completionStatus == PromptCompletionStatus.COMPLETED) coverage.coveredMessageCount else 0
        val summary = ConversationSummary(
            conversationId = conversationId,
            assistantId = assistantId,
            summary = summaryText,
            coveredMessageCount = updatedCoveredMessageCount,
            updatedAt = nowProvider(),
            sourceSnapshot = ConversationSummaryCoverageSupport.sourceSnapshot(
                sourceMessages.take(
                    coverage.coveredMessageCount
                )
            ),
            completionStatus = completionStatus,
            memoryContext = expectedMemoryContext,
        )
        if (!conversationSummaryRepository.saveSummaryIfSourcesCurrent(
                summary,
                storedSummary
            )
        ) return SummaryUpdateResult()
        return SummaryUpdateResult(
            updated = true,
            summaryText = summaryText,
            coveredMessageCount = updatedCoveredMessageCount,
        )
    }

    private fun currentResult(
        summary: ConversationSummary?,
        messages: List<ChatMessage>,
        expectedMemoryContext: MemoryRuntimeContext?,
    ): SummaryUpdateResult {
        val usable = ConversationSummaryCoverageSupport.usableSummary(
            summary,
            messages,
            expectedMemoryContext
        )
        return SummaryUpdateResult(
            summaryText = usable?.summary.orEmpty(),
            coveredMessageCount = usable?.coveredMessageCount ?: 0,
        )
    }

    private fun requireAcceptedCompletion(result: PromptCompletionResult) {
        check(ConversationSummaryCoverageSupport.isAcceptedStatus(result.status)) {
            "摘要响应未完整结束，已保留原摘要与消息"
        }
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
            if (current.any { it.companionSourceEligibility() == CompanionSourceEligibility.TEXT_ALLOWED } && currentLength + messageLength > safeTarget) {
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
        if (message.companionSourceEligibility() == CompanionSourceEligibility.CONTROL_NOOP) return 0
        val contentLength = ConversationSummaryCoverageSupport.sourceText(message).length
        return contentLength + message.speakerName.length + 24
    }

    private suspend fun buildUpdatedTotalSummary(
        existingSummary: ConversationSummary?,
        segments: List<ConversationSummarySegment>,
        summaryProviderBaseUrl: String,
        summaryProviderApiKey: String,
        summaryModel: String,
        summaryProviderProtocol: ProviderApiProtocol,
        generateSummary: suspend (conversationText: String, baseUrl: String, apiKey: String, modelId: String, apiProtocol: ProviderApiProtocol) -> PromptCompletionResult,
    ): PromptCompletionResult {
        val normalizedExistingSummary = existingSummary?.summary.orEmpty().trim()
        if (segments.isNotEmpty() && segments.all { segment ->
                segment.sourceSnapshot.isNotEmpty() && segment.sourceSnapshot.all {
                    it.kind == ConversationSummaryCoverageSupport.CONTROL_NOOP
                }
            }) return PromptCompletionResult(
            normalizedExistingSummary.ifBlank { ConversationSummaryCoverageSupport.CONTROL_ONLY_SUMMARY },
            existingSummary?.completionStatus ?: PromptCompletionStatus.COMPLETED,
        )
        if (normalizedExistingSummary.isBlank() && segments.size == 1) {
            return PromptCompletionResult(
                segments.single().summary.trim(),
                segments.single().completionStatus
            )
        }
        val summaryInput = buildString {
            append("【合并要求】\n")
            append("请把已有总摘要与分段摘要合并成当前可用的连续档案，优先保留已完成进展、当前状态、待办/未解问题、近期触发点。")
            append("不要把已完成事件改写成又发生了一次，也不要丢掉仍会影响下一轮的关系变化和剧情约束。\n\n")
            if (normalizedExistingSummary.isNotBlank()) {
                append("【已有总摘要】\n")
                append(normalizedExistingSummary)
                append("\n\n")
            }
            val allRecentSegments = segments.sortedWith(
                compareBy(
                    { it.startCreatedAt },
                    { it.endCreatedAt },
                    { it.id })
            )
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
        )
    }

    private fun buildSegmentId(
        conversationId: String,
        startMessageId: String,
        endMessageId: String,
        memoryContext: MemoryRuntimeContext?,
    ): String {
        return MemoryArchiveIdentity.id(
            "summary-segment",
            conversationId,
            ConversationSummaryCoverageSupport.memoryContextKey(memoryContext),
            startMessageId,
            endMessageId,
        )
    }
}
