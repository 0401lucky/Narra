package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.ConversationTransferCoordinator
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.isGiftPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.taskMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.withGiftImageGenerating
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
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

    fun sendSpecialPlay(
        draft: ChatSpecialPlayDraft,
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

        val defaultCharacterName = RoleplayConversationSupport.resolveRoleplayNames(
            scenario = scenario,
            assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId),
            settings = state.settings,
        ).second
        val specialPart = when (draft) {
            is TransferPlayDraft -> {
                val normalizedAmount = draft.amount.trim()
                if (normalizedAmount.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入转账金额")
                    }
                    return null
                }
                transferMessagePart(
                    direction = TransferDirection.USER_TO_ASSISTANT,
                    status = TransferStatus.PENDING,
                    counterparty = draft.counterparty.trim().ifBlank { defaultCharacterName },
                    amount = normalizedAmount,
                    note = draft.note.trim(),
                )
            }

            is InvitePlayDraft -> {
                val normalizedPlace = draft.place.trim()
                if (normalizedPlace.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入邀约地点")
                    }
                    return null
                }
                val normalizedTime = draft.time.trim()
                if (normalizedTime.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入邀约时间")
                    }
                    return null
                }
                inviteMessagePart(
                    target = draft.target.trim().ifBlank { defaultCharacterName },
                    place = normalizedPlace,
                    time = normalizedTime,
                    note = draft.note.trim(),
                )
            }

            is GiftPlayDraft -> {
                val normalizedItem = draft.item.trim()
                if (normalizedItem.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入礼物内容")
                    }
                    return null
                }
                giftMessagePart(
                    target = draft.target.trim().ifBlank { defaultCharacterName },
                    item = normalizedItem,
                    note = draft.note.trim(),
                )
            }

            is TaskPlayDraft -> {
                val normalizedTitle = draft.title.trim()
                if (normalizedTitle.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入委托标题")
                    }
                    return null
                }
                val normalizedObjective = draft.objective.trim()
                if (normalizedObjective.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入委托目标")
                    }
                    return null
                }
                taskMessagePart(
                    title = normalizedTitle,
                    objective = normalizedObjective,
                    reward = draft.reward.trim(),
                    deadline = draft.deadline.trim(),
                )
            }
        }

        return startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(specialPart),
            nextInput = state.input,
        )
    }

    fun sendTransferPlay(
        counterparty: String,
        amount: String,
        note: String,
    ): Job? {
        return sendSpecialPlay(
            TransferPlayDraft(
                counterparty = counterparty,
                amount = amount,
                note = note,
            ),
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
        val activeProvider = state.settings.activeProvider()
        val originalGiftPart = normalizeChatMessageParts(userParts).firstOrNull { it.isGiftPart() }
        val giftImageModelId = activeProvider
            ?.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
            .orEmpty()
            .trim()
        val shouldGenerateGiftImage = originalGiftPart != null &&
            activeProvider != null &&
            giftImageModelId.isNotBlank()
        val resolvedUserParts = if (shouldGenerateGiftImage) {
            userParts.map { part ->
                if (part.specialId == originalGiftPart?.specialId) {
                    part.withGiftImageGenerating()
                } else {
                    part
                }
            }
        } else {
            userParts
        }
        cancelSuggestionGeneration(false)
        updateUiState { current ->
            RoleplayStateSupport.beginSending(current, nextInput).let { updated ->
                if (originalGiftPart != null && !shouldGenerateGiftImage) {
                    RoleplayStateSupport.applyNoticeMessage(updated, "未配置礼物生图模型，已按普通礼物卡发送")
                } else {
                    updated
                }
            }
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
                    userParts = resolvedUserParts,
                    selectedModel = selectedModel,
                    roleplayOutputFormat = RoleplayMessageFormatSupport.resolveScenarioOutputFormat(scenario),
                    nowProvider = nowProvider,
                    messageIdProvider = messageIdProvider,
                )
                val giftImageRequest = if (shouldGenerateGiftImage) {
                    val giftPart = normalizeChatMessageParts(resolvedUserParts).firstOrNull { it.isGiftPart() }
                    val (userName, characterName) = RoleplayConversationSupport.resolveRoleplayNames(
                        scenario = scenario,
                        assistant = assistant,
                        settings = state.settings,
                    )
                    if (giftPart == null) {
                        null
                    } else {
                        GiftImageGenerationRequest(
                            conversationId = session.conversationId,
                            selectedModel = selectedModel,
                            provider = activeProvider,
                            specialId = giftPart.specialId,
                            giftName = giftPart.specialMetadataValue("item"),
                            recipientName = giftPart.specialMetadataValue("target"),
                            userName = userName,
                            assistantName = characterName,
                            contextExcerpt = RoleplayConversationSupport.buildTranscriptInput(
                                messages = preparedRoundTrip.requestMessages.takeLast(8),
                                scenario = scenario,
                                assistant = assistant,
                                settings = state.settings,
                                maxLength = 600,
                            ),
                        )
                    }
                } else {
                    null
                }
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
                    giftImageRequest = giftImageRequest,
                )
            } finally {
                onSendingFinished()
            }
        }
    }
}
