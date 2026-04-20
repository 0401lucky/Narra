package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.RoleplaySessionStartResult
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.roleplay.RoleplayConversationSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class RoleplayScenarioActionSupport(
    private val scope: CoroutineScope,
    private val uiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val currentScenarioIdFlow: MutableStateFlow<String?>,
    private val currentRawMessagesFlow: MutableStateFlow<List<ChatMessage>>,
    private val roleplayRepository: RoleplayRepository,
    private val conversationRepository: ConversationRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val nowProvider: () -> Long,
    private val refreshContextStatus: (String?, Boolean, Int, Int) -> Unit,
    private val clearConversationScopedContext: suspend (String) -> Unit,
) {
    private var enterScenarioJob: Job? = null

    fun enterScenario(scenarioId: String) {
        if (scenarioId.isBlank()) {
            return
        }
        enterScenarioJob?.cancel()
        currentScenarioIdFlow.value = scenarioId
        updateUiState { current ->
            RoleplayStateSupport.enterScenario(current, scenarioId)
        }
        enterScenarioJob = scope.launch {
            val runningJob = currentCoroutineContext()[Job]
            try {
                val scenario = roleplayRepository.getScenario(scenarioId)
                    ?: error("场景不存在")
                val startResult = roleplayRepository.startScenario(scenarioId)
                if (currentScenarioIdFlow.value != scenarioId) {
                    return@launch
                }
                applySessionStartResult(
                    startResult = startResult,
                    scenario = scenario,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (currentScenarioIdFlow.value != scenarioId) {
                    return@launch
                }
                updateUiState { current ->
                    RoleplayStateSupport.applyScenarioLoadFailure(
                        current,
                        throwable.message ?: "启动场景失败",
                    )
                }
            } finally {
                if (enterScenarioJob === runningJob) {
                    enterScenarioJob = null
                }
            }
        }
    }

    fun leaveScenario() {
        enterScenarioJob?.cancel()
        enterScenarioJob = null
        currentScenarioIdFlow.value = null
        currentRawMessagesFlow.value = emptyList()
        updateUiState { current ->
            RoleplayStateSupport.leaveScenario(current)
        }
    }

    fun upsertScenario(
        scenario: RoleplayScenario,
        onSuccess: (() -> Unit)? = null,
    ) {
        val existed = uiState().scenarios.any { it.id == scenario.id }
        scope.launch {
            runCatching {
                roleplayRepository.upsertScenario(scenario)
            }.onSuccess {
                updateUiState { current ->
                    RoleplayStateSupport.applyNoticeMessage(
                        current,
                        if (existed) "场景已保存" else "场景已创建",
                    )
                }
                onSuccess?.invoke()
            }.onFailure { throwable ->
                updateUiState { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "保存场景失败")
                }
            }
        }
    }

    fun deleteScenario(
        scenarioId: String,
        onSuccess: (() -> Unit)? = null,
    ) {
        scope.launch {
            runCatching {
                roleplayRepository.deleteScenario(scenarioId)
            }.onSuccess {
                if (currentScenarioIdFlow.value == scenarioId) {
                    leaveScenario()
                }
                updateUiState { current ->
                    RoleplayStateSupport.applyNoticeMessage(current, "场景已删除")
                }
                onSuccess?.invoke()
            }.onFailure { throwable ->
                updateUiState { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "删除场景失败")
                }
            }
        }
    }

    fun editUserMessage(sourceMessageId: String) {
        val state = uiState()
        val session = state.currentSession ?: return
        if (state.isSending || sourceMessageId.isBlank()) {
            return
        }

        scope.launch {
            val currentMessages = com.example.myapplication.roleplay.RoleplayRoundTripSupport.currentConversationMessages(
                messages = currentRawMessagesFlow.value,
                conversationId = session.conversationId,
            )
            val preparedEdit = com.example.myapplication.roleplay.RoleplayRoundTripSupport.prepareUserEdit(
                currentMessages = currentMessages,
                sourceMessageId = sourceMessageId,
            ) ?: return@launch

            conversationRepository.replaceConversationSnapshot(
                conversationId = session.conversationId,
                messages = preparedEdit.rewoundMessages,
                selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
            )
            conversationSummaryRepository.deleteSummary(session.conversationId)
            currentRawMessagesFlow.value = preparedEdit.rewoundMessages
            updateUiState { current ->
                RoleplayStateSupport.applyPreparedEdit(
                    current = current,
                    restoredInput = preparedEdit.restoredInput,
                    inputFocusToken = nowProvider(),
                )
            }
            refreshContextStatus(
                session.conversationId,
                preparedEdit.rewoundMessages.isNotEmpty(),
                0,
                0,
            )
        }
    }

    fun resetCurrentSession(onSuccess: () -> Unit = {}) {
        val state = uiState()
        val session = state.currentSession ?: return
        if (state.isSending) {
            return
        }
        scope.launch {
            runCatching {
                conversationRepository.clearConversation(
                    conversationId = session.conversationId,
                    selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
                )
                roleplayRepository.deleteOnlineMeta(session.conversationId)
                roleplayRepository.deleteDiaryEntriesForConversation(session.conversationId)
                clearConversationScopedContext(session.conversationId)
            }.onSuccess {
                currentRawMessagesFlow.value = emptyList()
                updateUiState { current ->
                    RoleplayStateSupport.applyResetSessionSuccess(current)
                }
                onSuccess()
            }.onFailure { throwable ->
                updateUiState { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "清空剧情失败")
                }
            }
        }
    }

    fun restartCurrentSession(onSuccess: () -> Unit = {}) {
        val state = uiState()
        val session = state.currentSession
        val scenario = state.currentScenario
        if (session == null || scenario == null || state.isSending) {
            return
        }
        scope.launch {
            runCatching {
                val oldConversationId = session.conversationId
                val startResult = roleplayRepository.restartScenario(scenario.id)
                roleplayRepository.deleteOnlineMeta(oldConversationId)
                roleplayRepository.deleteDiaryEntriesForConversation(oldConversationId)
                clearConversationScopedContext(oldConversationId)
                applySessionStartResult(
                    startResult = startResult,
                    scenario = scenario,
                )
                updateUiState { current ->
                    RoleplayStateSupport.applyRestartSessionSuccess(current)
                }
                onSuccess()
            }.onFailure { throwable ->
                updateUiState { current ->
                    RoleplayStateSupport.applyErrorMessage(current, throwable.message ?: "重开剧情失败")
                }
            }
        }
    }

    fun applySessionStartResult(
        startResult: RoleplaySessionStartResult,
        scenario: RoleplayScenario,
    ) {
        val state = uiState()
        val currentAssistantName = RoleplayConversationSupport.resolveRoleplayNames(
            scenario = scenario,
            assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId),
            settings = state.settings,
        ).second
        val previousAssistantName = state.settings.resolvedAssistants()
            .firstOrNull { assistant: Assistant -> assistant.id == startResult.conversationAssistantId }
            ?.name
            ?.trim()
            .orEmpty()
            .ifBlank { "原角色" }

        updateUiState {
            RoleplayStateSupport.applySessionStartResult(
                current = it,
                startResult = startResult,
                previousAssistantName = previousAssistantName,
                currentAssistantName = currentAssistantName,
            )
        }
        refreshContextStatus(
            startResult.session.conversationId,
            startResult.hasHistory,
            uiState().contextStatus.worldBookHitCount,
            uiState().contextStatus.memoryInjectionCount,
        )
    }
}
