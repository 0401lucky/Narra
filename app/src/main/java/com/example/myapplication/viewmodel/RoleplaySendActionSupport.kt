package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.ConversationTransferCoordinator
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayRoundTripSupport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class RoleplaySendActionSupport(
    private val scope: CoroutineScope,
    private val uiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val currentRawMessages: MutableStateFlow<List<ChatMessage>>,
    private val roleplayRepository: com.example.myapplication.data.repository.roleplay.RoleplayRepository,
    private val transferCoordinator: ConversationTransferCoordinator,
    private val roundTripExecutor: RoleplayRoundTripExecutor,
    private val scenarioActionSupport: RoleplayScenarioActionSupport,
    private val nowProvider: () -> Long,
    private val messageIdProvider: () -> String,
    private val cancelSuggestionGeneration: (Boolean) -> Unit,
    private val onSendingFinished: () -> Unit,
) {
    fun retryTurn(sourceMessageId: String): Job? {
        val state = uiState()
        val scenario = state.currentScenario ?: return null
        val session = state.currentSession ?: return null
        if (state.isSending || sourceMessageId.isBlank()) {
            return null
        }

        val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
        val assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId)
        return scope.launch {
            try {
                val currentMessages = RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                )
                val preparedRetry = RoleplayRoundTripSupport.prepareRetryTurn(
                    currentMessages = currentMessages,
                    sourceMessageId = sourceMessageId,
                ) ?: return@launch

                updateUiState { current ->
                    RoleplayStateSupport.beginSending(current, current.input)
                }
                currentRawMessages.value = preparedRetry.initialMessages
                roundTripExecutor.execute(
                    state = state,
                    scenario = scenario,
                    session = session,
                    selectedModel = selectedModel,
                    assistant = assistant,
                    requestMessages = preparedRetry.requestMessages,
                    initialPersistence = RoundTripInitialPersistence.ReplaceSnapshot(
                        messages = preparedRetry.initialMessages,
                    ),
                    loadingMessage = preparedRetry.loadingMessage,
                    buildFinalMessages = { completedAssistant ->
                        preparedRetry.baseMessages + completedAssistant
                    },
                )
            } finally {
                onSendingFinished()
            }
        }
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ): Job? {
        val state = uiState()
        val scenario = state.currentScenario
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景不存在")
            }
            return null
        }
        if (!state.settings.hasRequiredConfig()) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请先完成模型配置后再开始剧情互动")
            }
            return null
        }
        if (state.isSending) {
            return null
        }

        val normalizedAmount = amount.trim()
        if (normalizedAmount.isBlank()) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请输入转账金额")
            }
            return null
        }

        val normalizedCounterparty = counterparty.trim().ifBlank {
            RoleplayConversationSupport.resolveRoleplayNames(
                scenario = scenario,
                assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId),
                settings = state.settings,
            ).second
        }
        val transferPart = transferMessagePart(
            direction = TransferDirection.USER_TO_ASSISTANT,
            status = TransferStatus.PENDING,
            counterparty = normalizedCounterparty,
            amount = normalizedAmount,
            note = note.trim(),
        )

        return startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(transferPart),
            nextInput = state.input,
        )
    }

    fun confirmTransferReceipt(specialId: String) {
        val state = uiState()
        val session = state.currentSession
        if (specialId.isBlank() || session == null) {
            return
        }

        scope.launch {
            val updatedMessages = transferCoordinator.confirmReceipt(
                conversationId = session.conversationId,
                specialId = specialId,
                selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
                currentMessages = currentRawMessages.value,
            ) ?: return@launch
            currentRawMessages.value = updatedMessages
            updateUiState { current ->
                RoleplayStateSupport.applyTransferReceiptNotice(current)
            }
        }
    }

    fun sendMessage(): Job? {
        val state = uiState()
        val text = state.input.trim()
        val scenario = state.currentScenario
        if (text.isBlank()) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请输入剧情内容")
            }
            return null
        }
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景不存在")
            }
            return null
        }
        if (!state.settings.hasRequiredConfig()) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请先完成模型配置后再开始剧情互动")
            }
            return null
        }
        if (state.isSending) {
            return null
        }

        return startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(textMessagePart(text)),
            nextInput = "",
        )
    }

    private fun startRoleplaySend(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        userParts: List<ChatMessagePart>,
        nextInput: String,
    ): Job {
        cancelSuggestionGeneration(false)
        updateUiState { current ->
            RoleplayStateSupport.beginSending(current, nextInput)
        }

        return scope.launch {
            try {
                val startResult = if (state.currentSession == null) {
                    roleplayRepository.startScenario(scenario.id)
                } else {
                    null
                }
                startResult?.let {
                    scenarioActionSupport.applySessionStartResult(
                        startResult = it,
                        scenario = scenario,
                    )
                }
                if (startResult?.assistantMismatch == true) {
                    val restoredInput = userParts.toPlainText().ifBlank { nextInput }
                    updateUiState { current ->
                        RoleplayStateSupport.restoreInputAfterAssistantMismatch(current, restoredInput)
                    }
                    return@launch
                }

                val session = startResult?.session ?: state.currentSession
                    ?: error("当前剧情会话不存在")
                val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
                val assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId)
                val baseMessages = startResult?.conversationMessages ?: RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                )
                val preparedRoundTrip = RoleplayRoundTripSupport.prepareOutgoingRoundTrip(
                    baseMessages = baseMessages,
                    conversationId = session.conversationId,
                    userParts = userParts,
                    selectedModel = selectedModel,
                    nowProvider = nowProvider,
                    messageIdProvider = messageIdProvider,
                )
                currentRawMessages.value = preparedRoundTrip.initialMessages

                roundTripExecutor.execute(
                    state = state,
                    scenario = scenario,
                    session = session,
                    selectedModel = selectedModel,
                    assistant = assistant,
                    requestMessages = preparedRoundTrip.requestMessages,
                    initialPersistence = RoundTripInitialPersistence.Append(
                        messages = listOf(preparedRoundTrip.userMessage, preparedRoundTrip.loadingMessage),
                    ),
                    loadingMessage = preparedRoundTrip.loadingMessage,
                    buildFinalMessages = { completedAssistant ->
                        preparedRoundTrip.baseMessages + preparedRoundTrip.userMessage + completedAssistant
                    },
                )
            } finally {
                onSendingFinished()
            }
        }
    }
}
