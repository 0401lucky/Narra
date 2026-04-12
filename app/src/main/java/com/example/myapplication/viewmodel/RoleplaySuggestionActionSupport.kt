package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.RoleplaySuggestionCoordinator
import com.example.myapplication.conversation.RoleplaySuggestionRequest
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class RoleplaySuggestionActionSupport(
    private val scope: CoroutineScope,
    private val uiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val currentRawMessages: MutableStateFlow<List<ChatMessage>>,
    private val suggestionCoordinator: RoleplaySuggestionCoordinator,
    private val outputParser: RoleplayOutputParser,
    private val recentMessageWindow: Int,
) {
    private var suggestionJob: Job? = null

    fun generateSuggestions() {
        requestRoleplaySuggestions(fillInputWithFirstSuggestion = false)
    }

    fun generateDraftInput() {
        requestRoleplaySuggestions(fillInputWithFirstSuggestion = true)
    }

    fun clearSuggestions() {
        cancelSuggestionGeneration(resetState = false)
        updateUiState { current ->
            RoleplayStateSupport.clearSuggestions(current)
        }
    }

    fun cancelSuggestionGeneration(resetState: Boolean = true) {
        suggestionJob?.cancel()
        suggestionJob = null
        if (resetState) {
            updateUiState { current ->
                RoleplayStateSupport.resetSuggestionGeneration(current)
            }
        }
    }

    private fun requestRoleplaySuggestions(
        fillInputWithFirstSuggestion: Boolean,
    ) {
        val state = uiState()
        val scenario = state.currentScenario
        val session = state.currentSession
        if (state.isSending) {
            return
        }
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applySuggestionValidationError(current, "当前场景不存在")
            }
            return
        }
        if (session == null) {
            updateUiState { current ->
                RoleplayStateSupport.applySuggestionValidationError(current, "当前剧情尚未初始化完成")
            }
            return
        }
        if (!state.settings.hasRequiredConfig()) {
            updateUiState { current ->
                RoleplayStateSupport.applySuggestionValidationError(current, "请先完成模型配置后再生成建议")
            }
            return
        }
        if (RoleplayConversationSupport.resolveSuggestionModelId(state.settings).isBlank()) {
            updateUiState { current ->
                RoleplayStateSupport.applySuggestionValidationError(current, "请先在模型页开启聊天建议模型")
            }
            return
        }

        cancelSuggestionGeneration(resetState = false)
        updateUiState { current ->
            RoleplayStateSupport.beginSuggestionGeneration(current)
        }

        suggestionJob = scope.launch {
            val runningSuggestionJob = currentCoroutineContext()[Job]
            try {
                val latestState = uiState()
                val result = suggestionCoordinator.generateSuggestions(
                    request = RoleplaySuggestionRequest(
                        scenario = scenario,
                        settings = latestState.settings,
                        currentInput = latestState.input,
                        isVideoCallActive = latestState.isVideoCallActive,
                        session = session,
                        recentMessageWindow = recentMessageWindow,
                        conversationMessages = currentRawMessages.value,
                        resolveAssistant = RoleplayConversationSupport::resolveAssistant,
                        resolveRoleplayNames = RoleplayConversationSupport::resolveRoleplayNames,
                        resolveSuggestionModelId = RoleplayConversationSupport::resolveSuggestionModelId,
                        buildDynamicDirectorNote = { messages, currentScenario, currentAssistant, currentSettings ->
                            RoleplayConversationSupport.buildDynamicDirectorNote(
                                messages = messages,
                                scenario = currentScenario,
                                assistant = currentAssistant,
                                settings = currentSettings,
                                outputParser = outputParser,
                                isVideoCallActive = latestState.isVideoCallActive,
                            )
                        },
                    ),
                )
                if (!isSuggestionTargetStillCurrent(scenario.id, session.conversationId)) {
                    return@launch
                }
                if (fillInputWithFirstSuggestion) {
                    val draftText = result.suggestions.firstOrNull()
                        ?.text
                        .orEmpty()
                        .trim()
                    if (draftText.isBlank()) {
                        updateUiState { current ->
                            RoleplayStateSupport.applySuggestionDraftFailure(current)
                        }
                    } else {
                        updateUiState { current ->
                            RoleplayStateSupport.applySuggestionDraft(current, draftText)
                        }
                    }
                } else {
                    updateUiState { current ->
                        RoleplayStateSupport.applySuggestionResult(current, result.suggestions)
                    }
                }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) {
                    throw throwable
                }
                if (!isSuggestionTargetStillCurrent(scenario.id, session.conversationId)) {
                    return@launch
                }
                updateUiState { current ->
                    RoleplayStateSupport.applySuggestionFailure(
                        current,
                        throwable.message ?: "建议生成失败",
                    )
                }
            } finally {
                if (suggestionJob === runningSuggestionJob) {
                    suggestionJob = null
                } else if (!uiState().isGeneratingSuggestions) {
                    suggestionJob = null
                }
            }
        }
    }

    private fun isSuggestionTargetStillCurrent(
        scenarioId: String,
        conversationId: String,
    ): Boolean {
        val state = uiState()
        return state.currentScenario?.id == scenarioId &&
            state.currentSession?.conversationId == conversationId
    }
}
