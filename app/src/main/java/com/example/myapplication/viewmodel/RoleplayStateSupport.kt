package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel

object RoleplayStateSupport {
    fun applyErrorMessage(
        current: RoleplayUiState,
        message: String,
    ): RoleplayUiState {
        return current.copy(errorMessage = message)
    }

    fun clearErrorMessage(current: RoleplayUiState): RoleplayUiState {
        return current.copy(errorMessage = null)
    }

    fun applyNoticeMessage(
        current: RoleplayUiState,
        message: String,
    ): RoleplayUiState {
        return current.copy(
            errorMessage = null,
            noticeMessage = message,
        )
    }

    fun clearNoticeMessage(current: RoleplayUiState): RoleplayUiState {
        return current.copy(noticeMessage = null)
    }

    fun updateInput(
        current: RoleplayUiState,
        value: String,
    ): RoleplayUiState {
        val shouldClearSuggestions = value.isNotBlank() && value != current.input
        return current.copy(
            input = value,
            errorMessage = null,
            suggestions = if (shouldClearSuggestions) emptyList() else current.suggestions,
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
        )
    }

    fun enterScenario(
        current: RoleplayUiState,
        scenarioId: String,
    ): RoleplayUiState {
        return current.copy(
            currentScenario = current.currentScenario?.takeIf { scenario -> scenario.id == scenarioId },
            currentSession = null,
            messages = emptyList(),
            suggestions = emptyList(),
            input = "",
            isScenarioLoading = true,
            isGeneratingSuggestions = false,
            showAssistantMismatchDialog = false,
            previousAssistantName = "",
            currentAssistantName = "",
            errorMessage = null,
            noticeMessage = null,
            latestPromptDebugDump = "",
            contextGovernance = null,
            streamingContent = "",
            suggestionErrorMessage = null,
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
            contextStatus = RoleplayContextStatus(),
        )
    }

    fun leaveScenario(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            currentScenario = null,
            currentSession = null,
            currentAssistant = null,
            contextStatus = RoleplayContextStatus(),
            messages = emptyList(),
            suggestions = emptyList(),
            input = "",
            isSending = false,
            isGeneratingSuggestions = false,
            isScenarioLoading = false,
            showAssistantMismatchDialog = false,
            previousAssistantName = "",
            currentAssistantName = "",
            errorMessage = null,
            noticeMessage = null,
            latestPromptDebugDump = "",
            contextGovernance = null,
            streamingContent = "",
            suggestionErrorMessage = null,
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
        )
    }

    fun applyScenarioLoadFailure(
        current: RoleplayUiState,
        errorMessage: String,
    ): RoleplayUiState {
        return current.copy(
            isScenarioLoading = false,
            errorMessage = errorMessage,
            isSending = false,
        )
    }

    fun dismissAssistantMismatchDialog(
        current: RoleplayUiState,
        suppressFuturePrompt: Boolean = false,
    ): RoleplayUiState {
        return current.copy(
            showAssistantMismatchDialog = false,
            previousAssistantName = "",
            currentAssistantName = "",
            noticeMessage = if (suppressFuturePrompt) {
                "继续沿用旧剧情，后续不再提示"
            } else {
                "继续沿用旧剧情"
            },
        )
    }

    fun beginSuggestionGeneration(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            isGeneratingSuggestions = true,
            suggestionErrorMessage = null,
        )
    }

    fun applySuggestionValidationError(
        current: RoleplayUiState,
        errorMessage: String,
    ): RoleplayUiState {
        return current.copy(suggestionErrorMessage = errorMessage)
    }

    fun applySuggestionResult(
        current: RoleplayUiState,
        suggestions: List<RoleplaySuggestionUiModel>,
    ): RoleplayUiState {
        return current.copy(
            suggestions = suggestions,
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
        )
    }

    fun applySuggestionDraft(
        current: RoleplayUiState,
        draftText: String,
    ): RoleplayUiState {
        return current.copy(
            input = draftText,
            suggestions = emptyList(),
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
            errorMessage = null,
        )
    }

    fun applySuggestionDraftFailure(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            suggestions = emptyList(),
            isGeneratingSuggestions = false,
            suggestionErrorMessage = "AI 没有生成可用草稿",
        )
    }

    fun applySuggestionFailure(
        current: RoleplayUiState,
        errorMessage: String,
    ): RoleplayUiState {
        return current.copy(
            isGeneratingSuggestions = false,
            suggestionErrorMessage = errorMessage,
        )
    }

    fun clearSuggestions(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            suggestions = emptyList(),
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
        )
    }

    fun resetSuggestionGeneration(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
        )
    }

    fun beginSending(
        current: RoleplayUiState,
        nextInput: String,
    ): RoleplayUiState {
        return current.copy(
            suggestions = emptyList(),
            input = nextInput,
            isSending = true,
            isGeneratingSuggestions = false,
            errorMessage = null,
            streamingContent = "",
            suggestionErrorMessage = null,
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
        )
    }

    fun restoreInputAfterAssistantMismatch(
        current: RoleplayUiState,
        restoredInput: String,
    ): RoleplayUiState {
        return current.copy(
            input = restoredInput,
            isSending = false,
            streamingContent = "",
        )
    }

    fun applyTransferReceiptNotice(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            noticeMessage = "已收款",
            errorMessage = null,
        )
    }

    fun applyResetSessionSuccess(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            messages = emptyList(),
            suggestions = emptyList(),
            streamingContent = "",
            latestPromptDebugDump = "",
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
            contextStatus = current.contextStatus.copy(
                hasSummary = false,
                summaryCoveredMessageCount = 0,
                memoryInjectionCount = 0,
                worldBookHitCount = 0,
                isContinuingSession = false,
            ),
            contextGovernance = null,
            noticeMessage = "剧情已清空",
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
        )
    }

    fun applyRestartSessionSuccess(current: RoleplayUiState): RoleplayUiState {
        return current.copy(
            messages = emptyList(),
            suggestions = emptyList(),
            streamingContent = "",
            latestPromptDebugDump = "",
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
            noticeMessage = "已重开剧情",
            showAssistantMismatchDialog = false,
            previousAssistantName = "",
            currentAssistantName = "",
            contextGovernance = null,
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
        )
    }

    fun applyPreparedEdit(
        current: RoleplayUiState,
        restoredInput: String,
        inputFocusToken: Long,
    ): RoleplayUiState {
        return current.copy(
            input = restoredInput,
            suggestions = emptyList(),
            isGeneratingSuggestions = false,
            suggestionErrorMessage = null,
            errorMessage = null,
            streamingContent = "",
            latestPromptDebugDump = "",
            contextGovernance = null,
            replyToMessageId = "",
            replyToPreview = "",
            replyToSpeakerName = "",
            inputFocusToken = inputFocusToken,
        )
    }

    fun applyPromptContext(
        current: RoleplayUiState,
        summaryCoveredMessageCount: Int,
        worldBookHitCount: Int,
        memoryInjectionCount: Int,
        debugDump: String,
        contextGovernance: ContextGovernanceSnapshot?,
    ): RoleplayUiState {
        return current.copy(
            contextStatus = current.contextStatus.copy(
                hasSummary = summaryCoveredMessageCount > 0,
                summaryCoveredMessageCount = summaryCoveredMessageCount,
                worldBookHitCount = worldBookHitCount,
                memoryInjectionCount = memoryInjectionCount,
            ),
            latestPromptDebugDump = debugDump,
            contextGovernance = contextGovernance,
        )
    }

    fun applyContextStatus(
        current: RoleplayUiState,
        contextStatus: RoleplayContextStatus,
    ): RoleplayUiState {
        return current.copy(contextStatus = contextStatus)
    }

    fun updatePendingMemoryProposal(
        current: RoleplayUiState,
        proposal: PendingMemoryProposal?,
    ): RoleplayUiState {
        return current.copy(pendingMemoryProposal = proposal)
    }

    fun updateMemoryProposalHistory(
        current: RoleplayUiState,
        history: List<MemoryProposalHistoryItem>,
    ): RoleplayUiState {
        return current.copy(recentMemoryProposalHistory = history)
    }

    fun applyPendingMemoryProposalSaved(
        current: RoleplayUiState,
        content: String,
    ): RoleplayUiState {
        return current.copy(
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = current.recentMemoryProposalHistory,
            noticeMessage = "已记住：$content",
            errorMessage = null,
        )
    }

    fun applyPendingMemoryProposalRejected(
        current: RoleplayUiState,
    ): RoleplayUiState {
        return current.copy(
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = current.recentMemoryProposalHistory,
            noticeMessage = "已忽略这条长期记忆建议",
            errorMessage = null,
        )
    }

    fun applyStreamingContent(
        current: RoleplayUiState,
        content: String,
    ): RoleplayUiState {
        return current.copy(streamingContent = content)
    }

    fun updateScenarioSessionIds(
        current: RoleplayUiState,
        scenarioSessionIds: Set<String>,
    ): RoleplayUiState {
        return current.copy(scenarioSessionIds = scenarioSessionIds)
    }

    fun applyMappedMessages(
        current: RoleplayUiState,
        messages: List<RoleplayMessageUiModel>,
    ): RoleplayUiState {
        return current.copy(messages = messages)
    }

    fun finishSending(
        current: RoleplayUiState,
        errorMessage: String?,
    ): RoleplayUiState {
        return current.copy(
            isSending = false,
            streamingContent = "",
            errorMessage = errorMessage,
        )
    }

    fun applySettings(
        current: RoleplayUiState,
        settings: AppSettings,
        currentAssistant: Assistant?,
        currentModel: String,
        currentProviderId: String,
    ): RoleplayUiState {
        return current.copy(
            settings = settings,
            currentAssistant = currentAssistant,
            currentModel = currentModel,
            currentProviderId = currentProviderId,
        )
    }

    fun applyScenarios(
        current: RoleplayUiState,
        scenarios: List<RoleplayScenario>,
        currentScenarioId: String?,
    ): RoleplayUiState {
        val resolvedCurrentScenario = current.currentScenario?.let { selected ->
            scenarios.firstOrNull { it.id == selected.id }
        }
        return current.copy(
            scenarios = scenarios,
            currentScenario = resolvedCurrentScenario
                ?: current.currentScenario?.takeIf { currentScenarioId == it.id },
        )
    }

    fun applyCurrentScenario(
        current: RoleplayUiState,
        scenario: RoleplayScenario?,
        currentAssistant: Assistant?,
    ): RoleplayUiState {
        return current.copy(
            currentScenario = scenario,
            currentAssistant = currentAssistant,
            isScenarioLoading = false,
        )
    }

    fun applyCurrentSession(
        current: RoleplayUiState,
        session: RoleplaySession?,
    ): RoleplayUiState {
        return current.copy(
            currentSession = session,
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
            contextStatus = if (session == null) {
                RoleplayContextStatus()
            } else {
                current.contextStatus
            },
            latestPromptDebugDump = if (session == null) "" else current.latestPromptDebugDump,
            contextGovernance = if (session == null) null else current.contextGovernance,
        )
    }

    fun applySessionStartResult(
        current: RoleplayUiState,
        startResult: com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult,
        previousAssistantName: String,
        currentAssistantName: String,
    ): RoleplayUiState {
        val shouldShowMismatchDialog = startResult.assistantMismatch &&
            !current.settings.suppressRoleplayAssistantMismatchDialog
        return current.copy(
            currentSession = startResult.session,
            isScenarioLoading = false,
            showAssistantMismatchDialog = shouldShowMismatchDialog,
            previousAssistantName = if (shouldShowMismatchDialog) previousAssistantName else "",
            currentAssistantName = if (shouldShowMismatchDialog) currentAssistantName else "",
            pendingMemoryProposal = null,
            recentMemoryProposalHistory = emptyList(),
            contextStatus = current.contextStatus.copy(
                isContinuingSession = startResult.hasHistory,
            ),
        )
    }
}
