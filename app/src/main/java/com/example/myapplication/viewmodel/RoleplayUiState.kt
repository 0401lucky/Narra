package com.example.myapplication.viewmodel

import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ContextGovernanceSnapshot
import com.example.myapplication.model.MemoryProposalHistoryItem
import com.example.myapplication.model.PendingMemoryProposal
import com.example.myapplication.model.RoleplayChatSummary
import com.example.myapplication.model.RoleplayContextStatus
import com.example.myapplication.model.RoleplayDiaryEntry
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.model.RoleplaySuggestionUiModel

data class RoleplayUiState(
    val settings: AppSettings = AppSettings(),
    val scenarios: List<RoleplayScenario> = emptyList(),
    val chatSummaries: List<RoleplayChatSummary> = emptyList(),
    val currentScenario: RoleplayScenario? = null,
    val currentSession: RoleplaySession? = null,
    val currentAssistant: Assistant? = null,
    val contextStatus: RoleplayContextStatus = RoleplayContextStatus(),
    val scenarioSessionIds: Set<String> = emptySet(),
    val messages: List<RoleplayMessageUiModel> = emptyList(),
    val suggestions: List<RoleplaySuggestionUiModel> = emptyList(),
    val input: String = "",
    val isSending: Boolean = false,
    val isGeneratingSuggestions: Boolean = false,
    val isScenarioLoading: Boolean = false,
    val showAssistantMismatchDialog: Boolean = false,
    val previousAssistantName: String = "",
    val currentAssistantName: String = "",
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
    val latestPromptDebugDump: String = "",
    val contextGovernance: ContextGovernanceSnapshot? = null,
    val streamingContent: String = "",
    val suggestionErrorMessage: String? = null,
    val pendingMemoryProposal: PendingMemoryProposal? = null,
    val recentMemoryProposalHistory: List<MemoryProposalHistoryItem> = emptyList(),
    val diaryEntries: List<RoleplayDiaryEntry> = emptyList(),
    val isGeneratingDiary: Boolean = false,
    val currentModel: String = "",
    val currentProviderId: String = "",
    val inputFocusToken: Long = 0L,
    val replyToMessageId: String = "",
    val replyToPreview: String = "",
    val replyToSpeakerName: String = "",
    val activeVideoCallSessionId: String = "",
    val activeVideoCallStartedAt: Long = 0L,
) {
    val isVideoCallActive: Boolean
        get() = activeVideoCallSessionId.isNotBlank() && activeVideoCallStartedAt > 0L
}
