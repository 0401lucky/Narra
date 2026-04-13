package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplayScenario
import com.example.myapplication.model.RoleplaySession
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayMessageUiMapper
import com.example.myapplication.roleplay.RoleplayOutputParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal object RoleplayObservationSupport {
    fun observeSettings(
        scope: CoroutineScope,
        settings: StateFlow<AppSettings>,
        uiState: MutableStateFlow<RoleplayUiState>,
    ) {
        scope.launch {
            settings.collect { currentSettings ->
                val scenario = uiState.value.currentScenario
                val currentAssistant = scenario?.let {
                    RoleplayConversationSupport.resolveAssistant(currentSettings, it.assistantId)
                }
                val activeProvider = currentSettings.activeProvider()
                uiState.update { current ->
                    RoleplayStateSupport.applySettings(
                        current = current,
                        settings = currentSettings,
                        currentAssistant = currentAssistant,
                        currentModel = activeProvider?.selectedModel.orEmpty(),
                        currentProviderId = activeProvider?.id.orEmpty(),
                    )
                }
            }
        }
    }

    fun observeScenarios(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
        currentScenarioId: StateFlow<String?>,
    ) {
        scope.launch {
            roleplayRepository.observeScenarios().collect { scenarios ->
                uiState.update { state ->
                    RoleplayStateSupport.applyScenarios(
                        current = state,
                        scenarios = scenarios,
                        currentScenarioId = currentScenarioId.value,
                    )
                }
            }
        }
    }

    fun observeSessions(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
    ) {
        scope.launch {
            roleplayRepository.observeSessions().collect { sessions ->
                uiState.update { current ->
                    RoleplayStateSupport.updateScenarioSessionIds(
                        current,
                        sessions.mapTo(linkedSetOf()) { it.scenarioId },
                    )
                }
            }
        }
    }

    fun observeCurrentScenario(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
        currentScenarioId: StateFlow<String?>,
    ) {
        scope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(null)
                    } else {
                        roleplayRepository.observeScenario(scenarioId)
                    }
                }
                .collect { scenario ->
                    uiState.update { state ->
                        RoleplayStateSupport.applyCurrentScenario(
                            current = state,
                            scenario = scenario,
                            currentAssistant = scenario?.let {
                                RoleplayConversationSupport.resolveAssistant(state.settings, it.assistantId)
                            },
                        )
                    }
                }
        }
    }

    fun observeCurrentSession(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
        currentScenarioId: StateFlow<String?>,
        onSessionObserved: (RoleplaySession?) -> Unit,
    ) {
        scope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(null)
                    } else {
                        roleplayRepository.observeSessionByScenario(scenarioId)
                    }
                }
                .collect { session ->
                    uiState.update { state ->
                        RoleplayStateSupport.applyCurrentSession(
                            current = state,
                            session = session,
                        )
                    }
                    onSessionObserved(session)
                }
        }
    }

    fun observeCurrentMessages(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        currentRawMessages: MutableStateFlow<List<ChatMessage>>,
        currentScenarioId: StateFlow<String?>,
    ) {
        scope.launch {
            currentScenarioId
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isNullOrBlank()) {
                        flowOf(emptyList())
                    } else {
                        roleplayRepository.observeConversationMessages(scenarioId)
                    }
                }
                .collect { messages ->
                    currentRawMessages.value = messages
                }
        }
    }

    fun observeMappedMessages(
        scope: CoroutineScope,
        currentRawMessages: MutableStateFlow<List<ChatMessage>>,
        settings: StateFlow<AppSettings>,
        uiState: MutableStateFlow<RoleplayUiState>,
        outputParser: RoleplayOutputParser,
        nowProvider: () -> Long,
    ) {
        scope.launch {
            combine(currentRawMessages, settings, uiState) { rawMessages, settingsState, uiStateState ->
                Triple(rawMessages, settingsState, uiStateState)
            }.collect { (rawMessages, settingsState, uiStateState) ->
                val mappedMessages = runCatching {
                    RoleplayMessageUiMapper.mapMessages(
                        scenario = uiStateState.currentScenario,
                        assistant = uiStateState.currentAssistant,
                        settings = settingsState,
                        rawMessages = rawMessages,
                        streamingContent = uiStateState.streamingContent.takeIf { uiStateState.isSending },
                        outputParser = outputParser,
                        nowProvider = nowProvider,
                    )
                }.getOrElse { throwable ->
                    uiState.update { current ->
                        if (current.errorMessage.isNullOrBlank()) {
                            current.copy(errorMessage = throwable.message ?: "剧情消息渲染失败")
                        } else {
                            current
                        }
                    }
                    emptyList()
                }
                uiState.update { current ->
                    RoleplayStateSupport.applyMappedMessages(current, mappedMessages)
                }
            }
        }
    }
}
