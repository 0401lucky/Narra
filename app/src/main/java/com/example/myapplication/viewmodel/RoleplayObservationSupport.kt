package com.example.myapplication.viewmodel

import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.RoleplayDiaryEntry
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal object RoleplayObservationSupport {
    private data class MapperInputs(
        val currentScenario: RoleplayScenario?,
        val currentAssistant: Assistant?,
        val streamingContent: String?,
        val isSending: Boolean,
    )

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

    fun observeChatSummaries(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
    ) {
        scope.launch {
            roleplayRepository.observeChatSummaries().collect { summaries ->
                uiState.update { current ->
                    current.copy(chatSummaries = summaries)
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

    fun observeCurrentGroupParticipants(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
        currentScenarioId: StateFlow<String?>,
    ) {
        scope.launch {
            currentScenarioId
                .map { it.orEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { scenarioId ->
                    if (scenarioId.isBlank()) {
                        flowOf(emptyList())
                    } else {
                        roleplayRepository.observeGroupParticipants(scenarioId)
                    }
                }
                .collect { participants ->
                    uiState.update { current ->
                        RoleplayStateSupport.applyCurrentGroupParticipants(current, participants)
                    }
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
            val mapperInputs = uiState.map { state ->
                MapperInputs(
                    currentScenario = state.currentScenario,
                    currentAssistant = state.currentAssistant,
                    streamingContent = state.streamingContent.takeIf { state.isSending },
                    isSending = state.isSending,
                )
            }.distinctUntilChanged()
            combine(currentRawMessages, settings, mapperInputs) { rawMessages, settingsState, inputs ->
                Triple(rawMessages, settingsState, inputs)
            }.collect { (rawMessages, settingsState, inputs) ->
                val mappedMessages = runCatching {
                    RoleplayMessageUiMapper.mapMessages(
                        scenario = inputs.currentScenario,
                        assistant = inputs.currentAssistant,
                        settings = settingsState,
                        rawMessages = rawMessages,
                        streamingContent = inputs.streamingContent.takeIf { inputs.isSending },
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

    fun observeDiaryEntries(
        scope: CoroutineScope,
        roleplayRepository: RoleplayRepository,
        uiState: MutableStateFlow<RoleplayUiState>,
    ) {
        scope.launch {
            uiState
                .map { it.currentSession?.conversationId.orEmpty() }
                .distinctUntilChanged()
                .flatMapLatest { conversationId ->
                    if (conversationId.isBlank()) {
                        flowOf(emptyList<RoleplayDiaryEntry>())
                    } else {
                        roleplayRepository.observeDiaryEntries(conversationId)
                    }
                }
                .collect { entries ->
                    uiState.update { current ->
                        RoleplayStateSupport.applyDiaryEntries(current, entries)
                    }
                }
        }
    }
}
