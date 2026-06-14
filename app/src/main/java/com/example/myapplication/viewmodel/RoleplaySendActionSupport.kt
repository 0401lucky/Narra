package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.ConversationTransferCoordinator
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatSpecialPlayDraft
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.GiftPlayDraft
import com.example.myapplication.model.InvitePlayDraft
import com.example.myapplication.model.MAX_GROUP_AUTO_REPLIES
import com.example.myapplication.model.PunishPlayDraft
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayGroupReplyMode
import com.example.myapplication.model.RoleplayMessageUiModel
import com.example.myapplication.model.RoleplaySpeaker
import com.example.myapplication.model.TaskPlayDraft
import com.example.myapplication.model.TransferPlayDraft
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.VoiceMessageDraft
import com.example.myapplication.model.giftMessagePart
import com.example.myapplication.model.inviteMessagePart
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.isGiftPart
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.ProviderFunction
import com.example.myapplication.model.pokeMessagePart
import com.example.myapplication.model.punishMessagePart
import com.example.myapplication.model.resolveVoiceMessageDurationSeconds
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.taskMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.toPlainText
import com.example.myapplication.model.toActionCopyText
import com.example.myapplication.model.voiceMessageActionPart
import com.example.myapplication.model.withGiftImageGenerating
import com.example.myapplication.model.hasSendableContent
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayGroupDirector
import com.example.myapplication.roleplay.RoleplayGroupDirectorRequest
import com.example.myapplication.roleplay.RoleplayGroupMemberContext
import com.example.myapplication.roleplay.RoleplayGroupReplyPlanner
import com.example.myapplication.roleplay.RoleplayGroupReplyTurn
import com.example.myapplication.roleplay.buildRoleplayGroupSpeakerDirectorNote
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
    private val conversationRepository: ConversationRepository,
    private val transferCoordinator: ConversationTransferCoordinator,
    private val roundTripExecutor: RoleplayRoundTripExecutor,
    private val groupDirector: RoleplayGroupDirector,
    private val scenarioActionSupport: RoleplayScenarioActionSupport,
    private val nowProvider: () -> Long,
    private val messageIdProvider: () -> String,
    private val cancelSuggestionGeneration: (Boolean) -> Unit,
    private val beginSendingRun: () -> Long,
    private val isSendingRunActive: (Long) -> Boolean,
    private val onSendingFinished: (Long) -> Unit,
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
        val sendingRunId = beginSendingRun()
        return scope.launch {
            try {
                if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
                    return@launch
                }
                val currentMessages = RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                )
                val preparedRetry = RoleplayRoundTripSupport.prepareRetryTurn(
                    currentMessages = currentMessages,
                    sourceMessageId = sourceMessageId,
                ) ?: return@launch

                if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
                    return@launch
                }
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
                    cancelledMessages = preparedRetry.baseMessages,
                    initialPersistence = RoundTripInitialPersistence.ReplaceSnapshot(
                        messages = preparedRetry.initialMessages,
                    ),
                    loadingMessage = preparedRetry.loadingMessage,
                    buildFinalMessages = { completedAssistant ->
                        preparedRetry.baseMessages + completedAssistant
                    },
                    isRoundTripActive = { isSendingRunActive(sendingRunId) },
                )
            } finally {
                onSendingFinished(sendingRunId)
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

            is PunishPlayDraft -> {
                val normalizedMethod = draft.method.trim()
                if (normalizedMethod.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入惩罚方式")
                    }
                    return null
                }
                val normalizedCount = draft.count.trim()
                if (normalizedCount.isBlank()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请输入惩罚次数")
                    }
                    return null
                }
                punishMessagePart(
                    method = normalizedMethod,
                    count = normalizedCount,
                    intensity = draft.intensity,
                    reason = draft.reason.trim(),
                    note = draft.note.trim(),
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

    fun sendVoiceMessage(
        draft: VoiceMessageDraft,
    ): Job? {
        val state = uiState()
        val scenario = state.currentScenario
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景不存在")
            }
            return null
        }
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景暂不支持语音消息")
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

        val normalizedContent = draft.content.trim()
        if (normalizedContent.isBlank()) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "请先输入语音内容")
            }
            return null
        }

        return startRoleplaySend(
            state = state,
            scenario = scenario,
            userParts = listOf(
                voiceMessageActionPart(
                    content = normalizedContent,
                    durationSeconds = resolveVoiceMessageDurationSeconds(
                        content = normalizedContent,
                        preferredDurationSeconds = draft.durationSeconds,
                    ),
                ),
            ),
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

    fun sendAvatarPoke(message: RoleplayMessageUiModel): Job? {
        val state = uiState()
        val scenario = state.currentScenario
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景不存在")
            }
            return null
        }
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "拍一拍只支持线上手机模式")
            }
            return null
        }
        if (state.isSending) {
            return null
        }

        return when (message.speaker) {
            RoleplaySpeaker.CHARACTER -> {
                if (!state.settings.hasRequiredConfig()) {
                    updateUiState { current ->
                        RoleplayStateSupport.applyErrorMessage(current, "请先完成模型配置后再开始剧情互动")
                    }
                    return null
                }
                val targetName = message.speakerName.trim()
                    .ifBlank {
                        RoleplayConversationSupport.resolveRoleplayNames(
                            scenario = scenario,
                            assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId),
                            settings = state.settings,
                        ).second
                    }
                    .ifBlank { "对方" }
                startRoleplaySend(
                    state = state,
                    scenario = scenario,
                    userParts = listOf(pokeMessagePart(target = targetName)),
                    nextInput = state.input,
                )
            }

            RoleplaySpeaker.USER -> appendCharacterAvatarPokeToUser(
                state = state,
                scenario = scenario,
            )

            RoleplaySpeaker.NARRATOR,
            RoleplaySpeaker.SYSTEM,
            -> null
        }
    }

    fun sendMessage(): Job? {
        return sendMessageText(uiState().input)
    }

    fun sendMessageText(rawText: String): Job? {
        val state = uiState()
        val text = rawText.trim()
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

    fun requestGroupParticipantReply(participantId: String): Job? {
        val state = uiState()
        val scenario = state.currentScenario
        if (participantId.isBlank()) {
            return null
        }
        if (scenario == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前场景不存在")
            }
            return null
        }
        if (!scenario.isGroupChat || scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "快捷发言仅支持线上群聊")
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

        val sendingRunId = beginSendingRun()
        cancelSuggestionGeneration(false)
        updateUiState { current ->
            RoleplayStateSupport.beginSending(current, current.input)
        }

        return scope.launch {
            try {
                if (!isSendingRunActive(sendingRunId) || !isScenarioActive(scenario.id)) {
                    return@launch
                }
                val startResult = if (state.currentSession == null) {
                    roleplayRepository.startScenario(scenario.id)
                } else {
                    null
                }
                if (!isSendingRunActive(sendingRunId) || !isScenarioActive(scenario.id)) {
                    return@launch
                }
                startResult?.let {
                    scenarioActionSupport.applySessionStartResult(
                        startResult = it,
                        scenario = scenario,
                    )
                }
                if (startResult?.assistantMismatch == true) {
                    updateUiState { current ->
                        RoleplayStateSupport.restoreInputAfterAssistantMismatch(current, current.input)
                    }
                    return@launch
                }

                val session = startResult?.session ?: state.currentSession
                    ?: error("当前剧情会话不存在")
                if (!isConversationActive(session.conversationId)) {
                    return@launch
                }
                val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
                val baseMessages = startResult?.conversationMessages ?: RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                )
                executeGroupParticipantQuickReply(
                    state = state,
                    scenario = scenario,
                    session = session,
                    selectedModel = selectedModel,
                    baseMessages = baseMessages,
                    participantId = participantId,
                    sendingRunId = sendingRunId,
                )
            } finally {
                onSendingFinished(sendingRunId)
            }
        }
    }

    private fun appendCharacterAvatarPokeToUser(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
    ): Job? {
        val session = state.currentSession
        if (session == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "当前剧情会话不存在")
            }
            return null
        }
        val actor = resolveAvatarPokeActor(
            state = state,
            scenario = scenario,
            conversationId = session.conversationId,
        )
        if (actor == null) {
            updateUiState { current ->
                RoleplayStateSupport.applyErrorMessage(current, "还没有可拍你的角色")
            }
            return null
        }
        return scope.launch {
            val part = pokeMessagePart(target = "用户")
            val pokeMessage = ChatMessage(
                id = messageIdProvider(),
                conversationId = session.conversationId,
                role = MessageRole.ASSISTANT,
                content = part.toActionCopyText(),
                createdAt = nowProvider(),
                parts = listOf(part),
                speakerId = actor.assistantId,
                speakerName = actor.displayName,
                speakerAvatarUri = actor.avatarUri,
                roleplayInteractionMode = RoleplayInteractionMode.ONLINE_PHONE,
            )
            conversationRepository.appendMessages(
                conversationId = session.conversationId,
                messages = listOf(pokeMessage),
                selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings),
            )
            currentRawMessages.value = RoleplayRoundTripSupport.currentConversationMessages(
                messages = currentRawMessages.value,
                conversationId = session.conversationId,
            ) + pokeMessage
        }
    }

    private fun resolveAvatarPokeActor(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        conversationId: String,
    ): AvatarPokeActor? {
        if (scenario.isGroupChat) {
            val latestSpeaker = currentRawMessages.value.asReversed().firstOrNull { message ->
                message.conversationId == conversationId &&
                    message.role == MessageRole.ASSISTANT &&
                    message.status == MessageStatus.COMPLETED &&
                    message.speakerName.isNotBlank()
            }
            if (latestSpeaker != null) {
                return AvatarPokeActor(
                    assistantId = latestSpeaker.speakerId,
                    displayName = latestSpeaker.speakerName,
                    avatarUri = latestSpeaker.speakerAvatarUri,
                )
            }
            val assistants = state.settings.resolvedAssistants()
            val participant = state.currentGroupParticipants
                .filterNot { it.isMuted }
                .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
                .firstOrNull()
                ?: return null
            val assistant = assistants.firstOrNull { it.id == participant.assistantId }
            return AvatarPokeActor(
                assistantId = participant.assistantId,
                displayName = participant.displayNameOverride.trim()
                    .ifBlank { assistant?.name.orEmpty() }
                    .ifBlank { "角色" },
                avatarUri = participant.avatarUriOverride.trim()
                    .ifBlank { assistant?.avatarUri.orEmpty() },
            )
        }
        val assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId)
        return AvatarPokeActor(
            assistantId = scenario.assistantId,
            displayName = scenario.characterDisplayNameOverride.trim()
                .ifBlank { assistant?.name.orEmpty() }
                .ifBlank { "角色" },
            avatarUri = scenario.characterPortraitUri.trim()
                .ifBlank { assistant?.avatarUri.orEmpty() },
        )
    }

    private fun startRoleplaySend(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        userParts: List<ChatMessagePart>,
        nextInput: String,
    ): Job {
        val sendingRunId = beginSendingRun()
        val giftProvider = state.settings.resolveFunctionProvider(ProviderFunction.GIFT_IMAGE)
        val originalGiftPart = normalizeChatMessageParts(userParts).firstOrNull { it.isGiftPart() }
        val giftImageModelId = state.settings.resolveFunctionModel(ProviderFunction.GIFT_IMAGE)
            .trim()
        val shouldGenerateGiftImage = originalGiftPart != null &&
            giftProvider != null &&
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
                if (!isSendingRunActive(sendingRunId) || !isScenarioActive(scenario.id)) {
                    return@launch
                }
                val startResult = if (state.currentSession == null) {
                    roleplayRepository.startScenario(scenario.id)
                } else {
                    null
                }
                if (!isSendingRunActive(sendingRunId) || !isScenarioActive(scenario.id)) {
                    return@launch
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
                if (!isConversationActive(session.conversationId)) {
                    return@launch
                }
                val selectedModel = RoleplayConversationSupport.resolveSelectedModelId(state.settings)
                val assistant = RoleplayConversationSupport.resolveAssistant(state.settings, scenario.assistantId)
                val baseMessages = startResult?.conversationMessages ?: RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                )
                val scriptAdjustedUserParts = applyBeforeSendScriptIfPureText(
                    scenario = scenario,
                    session = session,
                    assistant = assistant,
                    userParts = resolvedUserParts,
                )
                if (scenario.isGroupChat) {
                    executeGroupRoleplaySend(
                        state = state,
                        scenario = scenario,
                        session = session,
                        selectedModel = selectedModel,
                        baseMessages = baseMessages,
                        userParts = scriptAdjustedUserParts,
                        nextInput = nextInput,
                        replyToMessageId = state.replyToMessageId,
                        replyToPreview = state.replyToPreview,
                        replyToSpeakerName = state.replyToSpeakerName,
                        sendingRunId = sendingRunId,
                    )
                    return@launch
                }
                val preparedRoundTrip = RoleplayRoundTripSupport.prepareOutgoingRoundTrip(
                    baseMessages = baseMessages,
                    conversationId = session.conversationId,
                    userParts = scriptAdjustedUserParts,
                    replyToMessageId = state.replyToMessageId,
                    replyToPreview = state.replyToPreview,
                    replyToSpeakerName = state.replyToSpeakerName,
                    selectedModel = selectedModel,
                    roleplayOutputFormat = RoleplayMessageFormatSupport.resolveScenarioOutputFormat(scenario),
                    roleplayInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario),
                    nowProvider = nowProvider,
                    messageIdProvider = messageIdProvider,
                )
                val giftImageRequest = if (shouldGenerateGiftImage) {
                    val giftPart = normalizeChatMessageParts(scriptAdjustedUserParts).firstOrNull { it.isGiftPart() }
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
                            provider = giftProvider,
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
                if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
                    return@launch
                }
                currentRawMessages.value = preparedRoundTrip.initialMessages

                roundTripExecutor.execute(
                    state = state,
                    scenario = scenario,
                    session = session,
                    selectedModel = selectedModel,
                    assistant = assistant,
                    requestMessages = preparedRoundTrip.requestMessages,
                    cancelledMessages = preparedRoundTrip.baseMessages + preparedRoundTrip.userMessage,
                    initialPersistence = RoundTripInitialPersistence.Append(
                        messages = listOf(preparedRoundTrip.userMessage, preparedRoundTrip.loadingMessage),
                    ),
                    loadingMessage = preparedRoundTrip.loadingMessage,
                    buildFinalMessages = { completedAssistant ->
                        preparedRoundTrip.baseMessages + preparedRoundTrip.userMessage + completedAssistant
                    },
                    giftImageRequest = giftImageRequest,
                    isRoundTripActive = { isSendingRunActive(sendingRunId) },
                )
            } finally {
                onSendingFinished(sendingRunId)
            }
        }
    }

    private suspend fun applyBeforeSendScriptIfPureText(
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        assistant: Assistant?,
        userParts: List<ChatMessagePart>,
    ): List<ChatMessagePart> {
        val normalizedParts = normalizeChatMessageParts(userParts)
        val textPart = normalizedParts.singleOrNull()
            ?.takeIf { part -> part.type == ChatMessagePartType.TEXT }
            ?: return userParts
        val originalText = textPart.text.trim()
        if (originalText.isBlank()) {
            return userParts
        }
        val rewrittenText = roundTripExecutor.rewriteOutgoingTextWithScripts(
            session = session,
            scenario = scenario,
            assistant = assistant,
            text = originalText,
        ).trim()
        if (rewrittenText.isBlank() || rewrittenText == originalText) {
            return userParts
        }
        return listOf(textMessagePart(rewrittenText))
    }

    private suspend fun loadGroupMemberContexts(
        scenarioId: String,
        assistants: List<Assistant>,
    ): List<RoleplayGroupMemberContext> {
        return roleplayRepository.listGroupParticipants(scenarioId)
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
            .map { participant ->
                RoleplayGroupMemberContext(
                    participant = participant,
                    assistant = assistants.firstOrNull { it.id == participant.assistantId },
                )
            }
    }

    private suspend fun executeGroupParticipantQuickReply(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        baseMessages: List<ChatMessage>,
        participantId: String,
        sendingRunId: Long,
    ) {
        if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
            return
        }
        val members = loadGroupMemberContexts(
            scenarioId = scenario.id,
            assistants = state.settings.resolvedAssistants(),
        )
        val member = members.firstOrNull { it.participant.id == participantId }
        if (member == null) {
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyErrorMessage(current, "这个群成员不存在"),
                    errorMessage = "这个群成员不存在",
                )
            }
            return
        }
        if (member.participant.isMuted) {
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyErrorMessage(current, "${member.displayName} 已禁言"),
                    errorMessage = "${member.displayName} 已禁言",
                )
            }
            return
        }
        val turn = RoleplayGroupReplyTurn(
            participantId = member.participant.id,
            assistantId = member.participant.assistantId,
            displayName = member.displayName,
            intent = "用户点击了「${member.displayName}」快捷发言按钮。请基于当前群聊上下文和你的人设自然接话：可以回应用户，也可以接其他角色刚才的话；不要复述“我被点名了”。",
            reason = "用户希望指定群成员主动接当前群聊。",
        )
        executeGroupReplyTurns(
            state = state,
            scenario = scenario,
            session = session,
            selectedModel = selectedModel,
            members = members,
            turns = listOf(turn),
            initialMessages = baseMessages,
            emptyNotice = "这一轮暂时没有角色接话",
            sendingRunId = sendingRunId,
        )
    }

    private suspend fun executeGroupRoleplaySend(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        baseMessages: List<ChatMessage>,
        userParts: List<ChatMessagePart>,
        nextInput: String,
        replyToMessageId: String,
        replyToPreview: String,
        replyToSpeakerName: String,
        sendingRunId: Long,
    ) {
        if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
            return
        }
        if (scenario.interactionMode != RoleplayInteractionMode.ONLINE_PHONE) {
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyErrorMessage(current, "群聊首版仅支持线上手机模式"),
                    errorMessage = "群聊首版仅支持线上手机模式",
                )
            }
            return
        }
        val assistants = state.settings.resolvedAssistants()
        val members = loadGroupMemberContexts(
            scenarioId = scenario.id,
            assistants = assistants,
        )
        if (members.isEmpty()) {
            val userMessage = persistGroupUserMessage(
                scenario = scenario,
                session = session,
                selectedModel = selectedModel,
                baseMessages = baseMessages,
                userParts = userParts,
                replyToMessageId = replyToMessageId,
                replyToPreview = replyToPreview,
                replyToSpeakerName = replyToSpeakerName,
            )
            currentRawMessages.value = baseMessages + userMessage
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyErrorMessage(current, "请先给群聊添加角色"),
                    errorMessage = "请先给群聊添加角色",
                )
            }
            return
        }
        val userMessage = persistGroupUserMessage(
            scenario = scenario,
            session = session,
            selectedModel = selectedModel,
            baseMessages = baseMessages,
            userParts = userParts,
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
        )
        val messagesWithUser = baseMessages + userMessage
        currentRawMessages.value = messagesWithUser
        val mutedMention = members.firstOrNull { member ->
            member.participant.isMuted && userMessage.content.mentionsGroupMember(member.displayName)
        }
        if (mutedMention != null) {
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyErrorMessage(current, "${mutedMention.displayName} 已禁言"),
                    errorMessage = "${mutedMention.displayName} 已禁言",
                )
            }
            return
        }
        val plan = when (scenario.groupReplyMode) {
            RoleplayGroupReplyMode.NATURAL -> groupDirector.plan(
                RoleplayGroupDirectorRequest(
                    scenario = scenario,
                    members = members,
                    recentMessages = messagesWithUser,
                    latestUserMessage = userMessage,
                ),
            )

            RoleplayGroupReplyMode.ALL_MEMBERS,
            RoleplayGroupReplyMode.MANUAL_ONLY,
            -> RoleplayGroupReplyPlanner.plan(
                mode = scenario.groupReplyMode,
                members = members,
                latestUserInput = userMessage.content,
                maxTurns = scenario.maxGroupAutoReplies.coerceIn(1, MAX_GROUP_AUTO_REPLIES),
            )
        }
        val effectiveTurns = if (scenario.enableGroupMentionAutoReply) {
            plan.turns
        } else {
            plan.turns.filter { turn -> userMessage.content.mentionsGroupMember(turn.displayName) }
                .ifEmpty { plan.turns.take(1) }
        }
        executeGroupReplyTurns(
            state = state,
            scenario = scenario,
            session = session,
            selectedModel = selectedModel,
            members = members,
            turns = effectiveTurns,
            initialMessages = messagesWithUser,
            emptyNotice = plan.notice.ifBlank { "这一轮暂时没有角色接话" },
            sendingRunId = sendingRunId,
        )
    }

    private suspend fun executeGroupReplyTurns(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        members: List<RoleplayGroupMemberContext>,
        turns: List<RoleplayGroupReplyTurn>,
        initialMessages: List<ChatMessage>,
        emptyNotice: String,
        sendingRunId: Long,
    ) {
        currentRawMessages.value = initialMessages
        if (turns.isEmpty()) {
            updateUiState { current ->
                RoleplayStateSupport.finishSending(
                    RoleplayStateSupport.applyNoticeMessage(
                        current,
                        emptyNotice.ifBlank { "这一轮暂时没有角色接话" },
                    ),
                    errorMessage = null,
                )
            }
            return
        }
        var firstTurnError: String? = null
        var successfulTurnCount = 0
        val failedTurnNames = mutableListOf<String>()
        for ((index, turn) in turns.withIndex()) {
            if (!isSendingRunActive(sendingRunId) || !isConversationActive(session.conversationId)) {
                return
            }
            val member = members.firstOrNull { it.participant.id == turn.participantId }
                ?: continue
            val speakerAssistant = member.assistant
            val speakerScenario = scenario.copy(
                assistantId = turn.assistantId,
                characterDisplayNameOverride = turn.displayName,
                characterPortraitUri = member.avatarUri,
                characterPortraitUrl = "",
            )
            val timeline = RoleplayRoundTripSupport.currentConversationMessages(
                messages = currentRawMessages.value,
                conversationId = session.conversationId,
            )
            val loadingMessage = RoleplayRoundTripSupport.buildAssistantLoadingMessage(
                conversationId = session.conversationId,
                nowProvider = nowProvider,
                messageIdProvider = messageIdProvider,
                modelName = selectedModel,
                roleplayOutputFormat = RoleplayMessageFormatSupport.resolveScenarioOutputFormat(scenario),
                roleplayInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario),
                speakerId = member.participant.assistantId,
                speakerName = turn.displayName,
                speakerAvatarUri = member.avatarUri,
                afterCreatedAt = timeline.maxOfOrNull { it.createdAt },
            )
            var attempt = 0
            var turnError: String? = null
            while (
                attempt < GROUP_TURN_MAX_ATTEMPTS &&
                isSendingRunActive(sendingRunId) &&
                isConversationActive(session.conversationId)
            ) {
                val attemptTimeline = RoleplayRoundTripSupport.currentConversationMessages(
                    messages = currentRawMessages.value,
                    conversationId = session.conversationId,
                ).filterNot { it.id == loadingMessage.id }
                val loadingForAttempt = loadingMessage.copy(
                    content = "",
                    status = MessageStatus.LOADING,
                    reasoningContent = "",
                    reasoningSteps = emptyList(),
                    parts = emptyList(),
                )
                currentRawMessages.value = attemptTimeline + loadingForAttempt
                val requestMessagesForTurn = buildGroupQuickReplyRequestMessages(
                    timeline = attemptTimeline,
                    session = session,
                    member = member,
                    turn = turn,
                )
                val outcome = roundTripExecutor.execute(
                    state = state,
                    scenario = speakerScenario,
                    session = session,
                    selectedModel = selectedModel,
                    assistant = speakerAssistant,
                    requestMessages = requestMessagesForTurn,
                    cancelledMessages = attemptTimeline,
                    initialPersistence = if (attempt == 0) {
                        RoundTripInitialPersistence.Append(messages = listOf(loadingForAttempt))
                    } else {
                        RoundTripInitialPersistence.Upsert(messages = listOf(loadingForAttempt))
                    },
                    loadingMessage = loadingForAttempt,
                    buildFinalMessages = { completedAssistant ->
                        attemptTimeline + completedAssistant
                    },
                    extraDirectorNote = buildRoleplayGroupSpeakerDirectorNote(
                        turn = turn,
                        members = members,
                        recentMessages = attemptTimeline,
                    ),
                    finishSendingOnCompletion = false,
                    isRoundTripActive = { isSendingRunActive(sendingRunId) },
                )
                turnError = outcome.errorMessage
                if (turnError.isNullOrBlank()) {
                    successfulTurnCount += 1
                    break
                }
                attempt += 1
            }
            if (!turnError.isNullOrBlank()) {
                if (firstTurnError == null) {
                    firstTurnError = turnError
                }
                failedTurnNames += turn.displayName
            }
            if (index < turns.lastIndex) {
                updateUiState { current ->
                    current.copy(streamingContent = "")
                }
            }
        }
        if (isSendingRunActive(sendingRunId) && isConversationActive(session.conversationId)) {
            updateUiState { current ->
                val errorMessage = if (successfulTurnCount == 0) firstTurnError else null
                val finished = RoleplayStateSupport.finishSending(current, errorMessage = errorMessage)
                if (failedTurnNames.isNotEmpty() && successfulTurnCount > 0) {
                    RoleplayStateSupport.applyNoticeMessage(
                        finished,
                        "${failedTurnNames.joinToString("、")} 回复失败，已继续其他角色",
                    )
                } else {
                    finished
                }
            }
        }
    }

    private suspend fun persistGroupUserMessage(
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        baseMessages: List<ChatMessage>,
        userParts: List<ChatMessagePart>,
        replyToMessageId: String,
        replyToPreview: String,
        replyToSpeakerName: String,
    ): ChatMessage {
        val userMessage = RoleplayRoundTripSupport.buildUserMessage(
            conversationId = session.conversationId,
            parts = userParts,
            replyToMessageId = replyToMessageId,
            replyToPreview = replyToPreview,
            replyToSpeakerName = replyToSpeakerName,
            roleplayInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario),
            nowProvider = nowProvider,
            messageIdProvider = messageIdProvider,
        )
        conversationRepository.appendMessages(
            conversationId = session.conversationId,
            messages = listOf(userMessage),
            selectedModel = selectedModel,
        )
        currentRawMessages.value = baseMessages + userMessage
        return userMessage
    }

    private fun buildGroupQuickReplyRequestMessages(
        timeline: List<ChatMessage>,
        session: com.example.myapplication.model.RoleplaySession,
        member: RoleplayGroupMemberContext,
        turn: RoleplayGroupReplyTurn,
    ): List<ChatMessage> {
        val sendableMessages = timeline.filter {
            it.status == MessageStatus.COMPLETED && it.hasSendableContent()
        }
        if (sendableMessages.any { it.role == MessageRole.USER }) {
            return sendableMessages
        }
        val createdAt = (sendableMessages.maxOfOrNull(ChatMessage::createdAt) ?: nowProvider()) + 1L
        val openingInstruction = buildString {
            append("【用户操作】用户点击了「")
            append(member.displayName)
            append("」快捷发言按钮，希望由该角色在当前群聊中自然接话。")
            if (sendableMessages.isEmpty()) {
                append("当前群聊还没有前置消息，请根据角色人设、关系和场景自然开启第一句。")
            } else {
                append("当前没有可引用的用户发言，请结合已有群聊内容自然回应或推进互动。")
            }
            append("不要复述“我被点名了”，也不要说明这是按钮触发。")
        }
        val requestOnlyUserMessage = ChatMessage(
            id = "group-quick-reply-request-${turn.participantId}-$createdAt",
            conversationId = session.conversationId,
            role = MessageRole.USER,
            content = openingInstruction,
            status = MessageStatus.COMPLETED,
            createdAt = createdAt,
            parts = listOf(textMessagePart(openingInstruction)),
        )
        return sendableMessages + requestOnlyUserMessage
    }

    private fun String.mentionsGroupMember(displayName: String): Boolean {
        val normalizedName = displayName.trim()
        if (normalizedName.isBlank()) {
            return false
        }
        return contains("@$normalizedName", ignoreCase = true) ||
            contains(normalizedName, ignoreCase = true)
    }

    private fun isScenarioActive(scenarioId: String): Boolean {
        return uiState().currentScenario?.id == scenarioId
    }

    private fun isConversationActive(conversationId: String): Boolean {
        return uiState().currentSession?.conversationId == conversationId
    }
}

private data class AvatarPokeActor(
    val assistantId: String,
    val displayName: String,
    val avatarUri: String,
)

private const val GROUP_TURN_MAX_ATTEMPTS = 2
