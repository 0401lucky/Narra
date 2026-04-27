package com.example.myapplication.conversation

import com.example.myapplication.context.PromptContextResult
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.ContextPressureLevel
import com.example.myapplication.model.ContextSummaryState
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.inferModelAbilities
import com.example.myapplication.model.toPlainText

object ContextGovernanceSupport {
    fun buildSnapshot(
        settings: AppSettings,
        assistant: Assistant?,
        promptMode: PromptMode,
        selectedModel: String,
        requestMessages: List<ChatMessage>,
        effectiveRequestMessages: List<ChatMessage>,
        promptContext: PromptContextResult,
        completedMessageCount: Int,
        triggerMessageCount: Int,
        recentWindow: Int,
        minCoveredMessageCount: Int,
        toolingOptions: GatewayToolingOptions,
        rawDebugDump: String,
    ): ContextGovernanceSnapshot {
        val summaryConfigured = resolveConversationSummaryModel(settings).isNotBlank()
        val summaryState = resolveSummaryState(
            summaryConfigured = summaryConfigured,
            requestMessages = requestMessages,
            effectiveRequestMessages = effectiveRequestMessages,
            completedMessageCount = completedMessageCount,
            summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
            triggerMessageCount = triggerMessageCount,
            recentWindow = recentWindow,
        )
        return ContextGovernanceSnapshot(
            pressureLevel = resolvePressureLevel(
                requestMessages = requestMessages,
                effectiveRequestMessages = effectiveRequestMessages,
                promptContext = promptContext,
                summaryState = summaryState,
                toolingOptions = toolingOptions,
            ),
            summaryState = summaryState,
            recentWindow = recentWindow,
            summaryRefreshAvailable = resolveSummaryRefreshAvailable(
                summaryConfigured = summaryConfigured,
                completedMessageCount = completedMessageCount,
                triggerMessageCount = triggerMessageCount,
                recentWindow = recentWindow,
                minCoveredMessageCount = minCoveredMessageCount,
            ),
            sentMessageCount = effectiveRequestMessages.size,
            requestMessageCountBeforeTrim = requestMessages.size,
            summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
            completedMessageCount = completedMessageCount,
            memoryCount = promptContext.memoryInjectionCount,
            worldBookHitCount = promptContext.worldBookHitCount,
            enabledTools = resolveEnabledTools(
                settings = settings,
                assistant = assistant,
                promptMode = promptMode,
                selectedModel = selectedModel,
                promptContext = promptContext,
                toolingOptions = toolingOptions,
            ),
            summaryPreview = promptContext.summaryPreview,
            memoryItems = promptContext.memoryItems,
            worldBookItems = promptContext.worldBookItems,
            rawDebugDump = rawDebugDump,
            providerLabel = resolveProviderLabel(settings),
            modelLabel = selectedModel,
            promptModeLabel = when (promptMode) {
                PromptMode.CHAT -> "聊天"
                PromptMode.ROLEPLAY -> "沉浸"
            },
            generatedAt = System.currentTimeMillis(),
            estimatedContextTokens = promptContext.contextSections.sumOf { it.tokenEstimate },
            contextSections = promptContext.contextSections,
        )
    }

    private fun resolveSummaryState(
        summaryConfigured: Boolean,
        requestMessages: List<ChatMessage>,
        effectiveRequestMessages: List<ChatMessage>,
        completedMessageCount: Int,
        summaryCoveredMessageCount: Int,
        triggerMessageCount: Int,
        recentWindow: Int,
    ): ContextSummaryState {
        if (!summaryConfigured) {
            return ContextSummaryState.DISABLED
        }
        val hasSummary = summaryCoveredMessageCount > 0
        val hasSufficientCoverage = ConversationMessageTransforms.hasSufficientSummaryCoverage(
            completedMessageCount = completedMessageCount,
            recentWindow = recentWindow,
            summaryCoveredMessageCount = summaryCoveredMessageCount,
        )
        val summaryIsStale = if (hasSummary) {
            !hasSufficientCoverage
        } else {
            completedMessageCount > triggerMessageCount
        }
        return when {
            summaryIsStale -> ContextSummaryState.STALE
            effectiveRequestMessages.size < requestMessages.size -> ContextSummaryState.APPLIED
            else -> ContextSummaryState.READY_IDLE
        }
    }

    private fun resolveSummaryRefreshAvailable(
        summaryConfigured: Boolean,
        completedMessageCount: Int,
        triggerMessageCount: Int,
        recentWindow: Int,
        minCoveredMessageCount: Int,
    ): Boolean {
        // Tavo 语义：手动总结随时可点；只要模型配置好且当前对话有至少一条已完成消息就允许触发，
        // 真正的"是否值得总结"由后续摘要服务自身判断
        return summaryConfigured && completedMessageCount > 0
    }

    private fun resolvePressureLevel(
        requestMessages: List<ChatMessage>,
        effectiveRequestMessages: List<ChatMessage>,
        promptContext: PromptContextResult,
        summaryState: ContextSummaryState,
        toolingOptions: GatewayToolingOptions,
    ): ContextPressureLevel {
        val totalTextLength = requestMessages.sumOf { message ->
            message.parts.toPlainText()
                .ifBlank { message.content }
                .trim()
                .length
        }
        val attachmentCount = requestMessages.sumOf { message ->
            message.attachments.size +
                message.parts.count { part -> part.type != ChatMessagePartType.TEXT }
        }
        var score = 0
        if (promptContext.systemPrompt.length > 1_500) score += 1
        if (promptContext.systemPrompt.length > 3_500) score += 1
        if (requestMessages.size > 8) score += 1
        if (requestMessages.size > 16) score += 1
        if (effectiveRequestMessages.size > 8) score += 1
        if (totalTextLength > 2_500) score += 1
        if (totalTextLength > 6_000) score += 1
        if (promptContext.memoryInjectionCount > 0) score += 1
        if (promptContext.worldBookHitCount > 0) score += 1
        if (toolingOptions.enabledToolNames.isNotEmpty()) score += 1
        if (attachmentCount > 0) score += 1
        if (summaryState == ContextSummaryState.STALE) score += 2
        return when {
            score >= 7 -> ContextPressureLevel.HIGH
            score >= 3 -> ContextPressureLevel.MEDIUM
            else -> ContextPressureLevel.LOW
        }
    }

    private fun resolveEnabledTools(
        settings: AppSettings,
        assistant: Assistant?,
        promptMode: PromptMode,
        selectedModel: String,
        promptContext: PromptContextResult,
        toolingOptions: GatewayToolingOptions,
    ): List<String> {
        val resolvedAbilities = settings.activeProvider()
            ?.resolveModelAbilities(selectedModel)
            ?: inferModelAbilities(selectedModel)
        return buildList {
            if (toolingOptions.searchEnabled) {
                add("网页搜索")
            }
            if (promptContext.summaryCoveredMessageCount > 0) {
                add("读取摘要")
            }
            if (promptContext.memoryInjectionCount > 0) {
                add("读取记忆")
            }
            if (promptContext.worldBookHitCount > 0) {
                add("检索世界书")
            }
            if (promptMode == PromptMode.ROLEPLAY &&
                assistant?.memoryEnabled == true &&
                ModelAbility.TOOL in resolvedAbilities
            ) {
                add("写入记忆")
            }
        }.distinct()
    }

    private fun resolveProviderLabel(settings: AppSettings): String {
        val provider = settings.activeProvider() ?: return ""
        val trimmedBaseUrl = provider.baseUrl.trim()
        if (trimmedBaseUrl.isBlank()) {
            return provider.name
        }
        return try {
            val normalized = if ("://" in trimmedBaseUrl) trimmedBaseUrl else "https://$trimmedBaseUrl"
            java.net.URI(normalized).host?.ifBlank { provider.name } ?: provider.name
        } catch (_: Exception) {
            trimmedBaseUrl
        }
    }
}
