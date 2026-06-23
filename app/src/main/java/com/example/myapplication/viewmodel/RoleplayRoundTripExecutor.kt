package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.AssistantRoundTripOutcome
import com.example.myapplication.conversation.AssistantRoundTripRequest
import com.example.myapplication.conversation.AssistantRoundTripResult
import com.example.myapplication.conversation.AiPhotoGenerationRequest
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.ContextGovernanceSupport
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.conversation.StreamingReplyBuffer
import com.example.myapplication.conversation.VoiceSynthesisRequest
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.data.repository.roleplay.script.EmptyRoleplayScriptRepository
import com.example.myapplication.data.repository.roleplay.script.RoleplayScriptRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatActionType
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.ContextLogSection
import com.example.myapplication.model.ContextLogSourceType
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.RoleplayInteractionMode
import com.example.myapplication.model.RoleplayOutputFormat
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.isOnlineThoughtPart
import com.example.myapplication.model.isGroupChat
import com.example.myapplication.model.normalizeChatMessageParts
import com.example.myapplication.model.normalizedOnlineReplyRange
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.model.transferResultText
import com.example.myapplication.roleplay.OnlineActionDirective
import com.example.myapplication.roleplay.OnlineActionProtocolParseResult
import com.example.myapplication.roleplay.OnlineActionProtocolParser
import com.example.myapplication.roleplay.OnlineMessageReferenceCandidate
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayFormatReminderSupport
import com.example.myapplication.roleplay.RoleplayLongformMarkupParser
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayOnlineReferenceSupport
import com.example.myapplication.roleplay.RoleplayOutputLeakSanitizer
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import com.example.myapplication.roleplay.RoleplaySummaryWindowSupport
import com.example.myapplication.roleplay.script.DisabledRoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEngine
import com.example.myapplication.roleplay.script.RoleplayScriptEvent
import com.example.myapplication.roleplay.script.RoleplayScriptExecutionResult
import com.example.myapplication.system.security.SensitiveTextRedactor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal data class RoleplayRoundTripExecutionOutcome(
    val errorMessage: String? = null,
)

internal class RoleplayRoundTripExecutor(
    private val aiGateway: AiGateway,
    private val conversationRepository: ConversationRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val roleplayRepository: RoleplayRepository,
    private val roleplayScriptRepository: RoleplayScriptRepository = EmptyRoleplayScriptRepository,
    private val roleplayScriptEngine: RoleplayScriptEngine = DisabledRoleplayScriptEngine(),
    private val promptContextAssembler: PromptContextAssembler,
    private val assistantRoundTripRunner: ConversationAssistantRoundTripRunner,
    private val outputParser: RoleplayOutputParser,
    private val nowProvider: () -> Long,
    private val currentUiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val updateRawMessages: (List<ChatMessage>) -> Unit,
    private val canUpdateConversation: (String) -> Boolean = { true },
    private val launchGiftImageGeneration: (GiftImageGenerationRequest, (List<ChatMessage>) -> Unit) -> Unit,
    private val launchVoiceSynthesis: (VoiceSynthesisRequest, (List<ChatMessage>) -> Unit) -> Unit,
    private val launchAiPhotoGeneration: (AiPhotoGenerationRequest, (List<ChatMessage>) -> Unit) -> Unit = { _, _ -> },
    private val launchConversationSummaryGeneration: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
    private val launchAutomaticMemoryExtraction: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
    private val contextLogStore: com.example.myapplication.data.repository.context.ContextLogStore,
    private val buildEconomyPromptContext: suspend (String) -> String = { "" },
    private val settleTransfer: suspend (String) -> Unit = {},
    private val releaseTransfer: suspend (String) -> Unit = {},
) {
    private val memoryExtractionGate = MemoryExtractionGate()
    private val scriptEventCoordinator = RoleplayScriptEventCoordinator(
        scriptRepository = roleplayScriptRepository,
        scriptEngine = roleplayScriptEngine,
    )

    suspend fun execute(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
        cancelledMessages: List<ChatMessage>,
        initialPersistence: RoundTripInitialPersistence,
        loadingMessage: ChatMessage,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
        giftImageRequest: GiftImageGenerationRequest? = null,
        extraDirectorNote: String = "",
        finishSendingOnCompletion: Boolean = true,
        isRoundTripActive: () -> Boolean = { true },
    ): RoleplayRoundTripExecutionOutcome {
        fun canApplyUiUpdate(): Boolean {
            return canUpdateConversation(session.conversationId) && isRoundTripActive()
        }

        fun applyRawMessages(messages: List<ChatMessage>) {
            if (canUpdateConversation(session.conversationId)) {
                updateRawMessages(messages)
            }
        }

        fun applyActiveRawMessages(messages: List<ChatMessage>) {
            if (canApplyUiUpdate()) {
                updateRawMessages(messages)
            }
        }

        try {
            when (initialPersistence) {
                is RoundTripInitialPersistence.Append -> {
                    conversationRepository.appendMessages(
                        conversationId = session.conversationId,
                        messages = initialPersistence.messages,
                        selectedModel = selectedModel,
                    )
                }

                is RoundTripInitialPersistence.Upsert -> {
                    conversationRepository.upsertMessages(
                        conversationId = session.conversationId,
                        messages = initialPersistence.messages,
                        selectedModel = selectedModel,
                    )
                }

                is RoundTripInitialPersistence.ReplaceSnapshot -> {
                    conversationRepository.replaceConversationSnapshot(
                        conversationId = session.conversationId,
                        messages = initialPersistence.messages,
                        selectedModel = selectedModel,
                    )
                }
            }
            giftImageRequest?.let { request ->
                launchGiftImageGeneration(request, ::applyRawMessages)
            }
            val conversation = conversationRepository.getConversation(session.conversationId)
                ?: Conversation(
                    id = session.conversationId,
                    createdAt = nowProvider(),
                    updatedAt = nowProvider(),
                    assistantId = scenario.assistantId,
                )
            val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
                conversation = conversation,
                assistant = assistant,
                requestMessages = requestMessages,
            )
            val currentInteractionMode = RoleplayMessageFormatSupport.resolveScenarioInteractionMode(scenario)
            val useVideoCallMode = currentInteractionMode == RoleplayInteractionMode.ONLINE_PHONE &&
                state.isVideoCallActive
            val onlineSystemEventContext = if (currentInteractionMode == RoleplayInteractionMode.ONLINE_PHONE) {
                RoleplayOnlineReferenceSupport.buildSystemEventPromptContext(
                    messages = effectiveRequestMessages,
                    outputParser = outputParser,
                )
            } else {
                ""
            }
            val requestMessagesForModel = RoleplayFormatReminderSupport.injectIntoLatestUser(
                messages = RoleplayOnlineReferenceSupport.sanitizeRequestMessages(
                    messages = effectiveRequestMessages,
                    scenario = scenario,
                    assistant = assistant,
                    settings = state.settings,
                    outputParser = outputParser,
                ),
                scenario = scenario,
            )
            val latestUserInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessagesForModel)
            val promptContext = promptContextAssembler.assemble(
                settings = RoleplayConversationSupport.resolvePromptSettings(scenario, state.settings),
                assistant = RoleplayConversationSupport.resolvePromptAssistant(scenario, assistant),
                conversation = conversation,
                userInputText = latestUserInputText,
                recentMessages = requestMessagesForModel,
                promptMode = PromptMode.ROLEPLAY,
            )
            val beforePromptScriptResult = scriptEventCoordinator.execute(
                RoleplayScriptEventRequest(
                    event = RoleplayScriptEvent.BEFORE_PROMPT,
                    sessionId = session.id,
                    scenarioId = scenario.id,
                    characterId = assistant?.id ?: scenario.assistantId,
                    userText = latestUserInputText,
                    promptText = promptContext.systemPrompt,
                ),
            )
            val scriptDirectorNote = formatScriptPromptAdditions(beforePromptScriptResult.promptAdditions)
            val economyDirectorNote = runCatching {
                buildEconomyPromptContext(scenario.id)
            }.getOrDefault("")
            val generationPromptEnvelope = if (scenario.isGroupChat) {
                promptContext.promptEnvelope.copy(
                    statusCardsEnabled = false,
                    hideStatusBlocksInBubble = true,
                )
            } else {
                promptContext.promptEnvelope
            }
            val referenceCandidates = RoleplayOnlineReferenceSupport.buildCandidates(
                messages = requestMessagesForModel,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                outputParser = outputParser,
            )
            val directorNote = RoleplayConversationSupport.buildDynamicDirectorNote(
                messages = requestMessagesForModel,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                outputParser = outputParser,
                isVideoCallActive = useVideoCallMode,
                referenceCandidates = referenceCandidates,
            )
            val composedDirectorNote = buildString {
                onlineSystemEventContext.takeIf { it.isNotBlank() }?.let { context ->
                    append(context)
                }
                directorNote.takeIf { it.isNotBlank() }?.let { note ->
                    if (isNotBlank()) {
                        append("\n\n")
                    }
                    append(note)
                }
                scriptDirectorNote.takeIf { it.isNotBlank() }?.let { note ->
                    if (isNotBlank()) {
                        append("\n\n")
                    }
                    append(note)
                }
                economyDirectorNote.takeIf { it.isNotBlank() }?.let { note ->
                    if (isNotBlank()) {
                        append("\n\n")
                    }
                    append(note)
                }
                extraDirectorNote.takeIf { it.isNotBlank() }?.let { note ->
                    if (isNotBlank()) {
                        append("\n\n")
                    }
                    append(note)
                }
            }
            val decoratedPrompt = RoleplayPromptDecorator.decorate(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                includeOpeningNarrationReference = requestMessages.none { it.role == MessageRole.USER },
                isVideoCallActive = useVideoCallMode,
                directorNote = composedDirectorNote,
                modelId = selectedModel,
            )
            val runtimeDecoration = RoleplayPromptDecorator.buildRuntimeDecoration(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                includeOpeningNarrationReference = requestMessages.none { it.role == MessageRole.USER },
                isVideoCallActive = useVideoCallMode,
                directorNote = composedDirectorNote,
                modelId = selectedModel,
            )
            val runtimeDecorationSections = runtimeDecoration
                .takeIf { it.isNotBlank() }
                ?.let { decoration ->
                    listOf(
                        ContextLogSection(
                            sourceType = ContextLogSourceType.SYSTEM_RULE,
                            title = "运行时导演注记",
                            content = decoration,
                        ),
                    )
                }
                ?: emptyList()
            val toolingOptions = GatewayToolingOptions.localContextOnly(
                GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = assistant,
                    conversation = conversation,
                    userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
                    recentMessages = requestMessagesForModel,
                ),
            )
            val completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED }
            val debugDump = ContextGovernanceSupport.buildActualPromptDebugDump(
                promptContextDebugDump = promptContext.debugDump,
                actualSystemPrompt = decoratedPrompt,
                hasSummary = promptContext.summaryCoveredMessageCount > 0,
                coveredMessageCount = promptContext.summaryCoveredMessageCount,
                completedMessageCount = completedMessageCount,
                triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
            )
            updateUiState { current ->
                val contextSnapshot = ContextGovernanceSupport.buildSnapshot(
                    settings = state.settings,
                    assistant = assistant,
                    promptMode = PromptMode.ROLEPLAY,
                    selectedModel = selectedModel,
                    requestMessages = requestMessages,
                    effectiveRequestMessages = requestMessagesForModel,
                    promptContext = promptContext,
                    completedMessageCount = completedMessageCount,
                    triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                    recentWindow = resolveSummaryRecentWindow(assistant),
                    minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                    toolingOptions = toolingOptions,
                    rawDebugDump = debugDump,
                    extraContextSections = runtimeDecorationSections,
                )
                contextLogStore.push(contextSnapshot)
                RoleplayStateSupport.applyPromptContext(
                    current = current,
                    summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
                    worldBookHitCount = promptContext.worldBookHitCount,
                    memoryInjectionCount = promptContext.memoryInjectionCount,
                    debugDump = debugDump,
                    contextGovernance = contextSnapshot,
                )
            }
            val fullContent = StringBuilder()
            val fullParts = mutableListOf<ChatMessagePart>()
            val fullReasoningSteps = mutableListOf<ChatReasoningStep>()
            var onlineProtocolResult: OnlineActionProtocolParseResult? = null
            var parsedTransferUpdates: List<TransferUpdateDirective> = emptyList()

            when (
                val result = assistantRoundTripRunner.execute(
                    AssistantRoundTripRequest(
                        conversationId = session.conversationId,
                        selectedModel = selectedModel,
                        requestMessages = requestMessagesForModel,
                        loadingMessage = loadingMessage,
                        buildFinalMessages = buildFinalMessages,
                        systemPrompt = decoratedPrompt,
                        statusCardsEnabled = generationPromptEnvelope.statusCardsEnabled,
                        hideStatusBlocksInBubble = generationPromptEnvelope.hideStatusBlocksInBubble,
                        streamReply = { messages, systemPrompt ->
                            streamRoleplayAssistantReply(
                                requestMessages = messages,
                                systemPrompt = systemPrompt,
                                promptEnvelope = generationPromptEnvelope,
                                fullContent = fullContent,
                                fullReasoningSteps = fullReasoningSteps,
                                fullParts = fullParts,
                                toolingOptions = toolingOptions,
                                expectedInteractionMode = loadingMessage.roleplayInteractionMode
                                    ?: scenario.interactionMode,
                                isGroupChat = scenario.isGroupChat,
                                canApplyUiUpdate = ::canApplyUiUpdate,
                            )
                        },
                        currentPayload = {
                            val interactionMode = loadingMessage.roleplayInteractionMode
                                ?: scenario.interactionMode
                            StreamedAssistantPayload(
                                content = RoleplayOutputLeakSanitizer.sanitize(
                                    rawContent = fullContent.toString().trim(),
                                    interactionMode = interactionMode,
                                ),
                                reasoning = reasoningStepsToContent(fullReasoningSteps),
                                reasoningSteps = fullReasoningSteps.toList(),
                                parts = RoleplayOutputLeakSanitizer.sanitizeParts(
                                    parts = fullParts.toList(),
                                    interactionMode = interactionMode,
                                ),
                                citations = emptyList(),
                            )
                        },
                        canPersistResult = ::canApplyUiUpdate,
                        onCompleted = { payload, parsedOutput, loading ->
                            parsedTransferUpdates = parsedOutput.transferUpdates
                            onlineProtocolResult = scenario.takeIf {
                                it.interactionMode == com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE
                            }?.let {
                                val characterName = roleplayStateCharacterName(
                                    scenario = scenario,
                                    assistant = assistant,
                                )
                                if (scenario.isGroupChat) {
                                    OnlineActionProtocolParser.parseGroupTextOnlyWithFallback(
                                        rawContent = payload.content,
                                        characterName = characterName,
                                    )
                                } else {
                                    OnlineActionProtocolParser.parseWithFallback(
                                        rawContent = payload.content,
                                        characterName = characterName,
                                    )
                                }
                            }
                            val rawCompletedParts = resolveRoleplayCompletedParts(
                                onlineProtocolResult = onlineProtocolResult,
                                parsedOutput = parsedOutput,
                                scenario = scenario,
                                referenceCandidates = referenceCandidates,
                            )
                            val completedParts = limitOnlineReplyParts(rawCompletedParts, scenario)
                            val resolvedContent = completedParts.toContentMirror(
                                imageFallback = "角色返回了图片",
                                specialFallback = "角色已回应",
                            )
                            val protocolDirectiveContent = onlineProtocolResult
                                ?.directives
                                ?.toDirectiveResultText()
                                .orEmpty()
                            val shouldUseParsedTextContent = parsedOutput.content.isNotBlank() &&
                                onlineProtocolResult == null &&
                                scenario.interactionMode != com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE
                            val hasResolvedAssistantContent = completedParts.isNotEmpty() ||
                                shouldUseParsedTextContent ||
                                parsedOutput.transferUpdates.isNotEmpty() ||
                                protocolDirectiveContent.isNotBlank()
                            val isEmptyCompensationOpening = !hasResolvedAssistantContent &&
                                loading.systemEventKind == com.example.myapplication.model.RoleplayOnlineEventKind.COMPENSATION_OPENING
                            loading.copy(
                                content = if (isEmptyCompensationOpening) {
                                    ""
                                } else {
                                    parsedOutput.content.takeIf { shouldUseParsedTextContent }
                                        ?: parsedOutput.transferUpdates.lastOrNull()?.status?.transferResultText()
                                        ?: protocolDirectiveContent.takeIf { it.isNotBlank() }
                                        ?: resolvedContent.ifBlank { "模型未返回有效内容" }
                                },
                                status = when {
                                    hasResolvedAssistantContent -> MessageStatus.COMPLETED
                                    isEmptyCompensationOpening -> MessageStatus.COMPLETED
                                    else -> MessageStatus.ERROR
                                },
                                reasoningContent = payload.reasoning,
                                reasoningSteps = payload.reasoningSteps,
                                parts = if (isEmptyCompensationOpening) emptyList() else completedParts,
                            )
                        },
                        onCancelled = { payload, loading ->
                            val partialContent = payload.content.trim()
                            if (partialContent.isBlank() && payload.parts.isEmpty()) {
                                // 没有任何已生成内容：回退到发送前快照
                                AssistantRoundTripOutcome(
                                    messages = cancelledMessages,
                                    errorMessage = null,
                                )
                            } else {
                                // 用户主动停止：保留已生成内容并落为 COMPLETED（非失败，不进重试集合）
                                AssistantRoundTripOutcome(
                                    messages = buildFinalMessages(
                                        loading.copy(
                                            content = partialContent,
                                            status = MessageStatus.COMPLETED,
                                            reasoningContent = payload.reasoning,
                                            reasoningSteps = payload.reasoningSteps,
                                            parts = payload.parts,
                                        ),
                                    ),
                                    errorMessage = null,
                                )
                            }
                        },
                        onFailed = { _, throwable, loading ->
                            val errorText = SensitiveTextRedactor.throwableMessageForUi(
                                throwable = throwable,
                                fallback = ROLEPLAY_SEND_FAILURE_MESSAGE,
                            )
                            AssistantRoundTripOutcome(
                                messages = buildFinalMessages(
                                    loading.copy(
                                        content = ROLEPLAY_SEND_FAILURE_MESSAGE,
                                        status = MessageStatus.ERROR,
                                        parts = emptyList(),
                                    ),
                                ),
                                errorMessage = errorText,
                            )
                        },
                    ),
                )
            ) {
                is AssistantRoundTripResult.Completed -> {
                    val completionError = result.messages
                        .assistantMessageError(loadingMessage.id)
                    if (!canApplyUiUpdate()) {
                        val cleanedMessages = removeStaleAssistantMessage(
                            conversationId = session.conversationId,
                            loadingMessage = loadingMessage,
                            selectedModel = selectedModel,
                        )
                        applyRawMessages(cleanedMessages)
                        return RoleplayRoundTripExecutionOutcome(errorMessage = completionError)
                    }
                    val handledTransferUpdates = mutableSetOf<Pair<String, TransferStatus>>()
                    settleParsedTransferUpdatesIfNeeded(
                        updates = parsedTransferUpdates,
                        handledTransferUpdates = handledTransferUpdates,
                    )
                    val postDirectiveMessages = applyOnlineProtocolDirectivesIfNeeded(
                        conversationId = session.conversationId,
                        selectedModel = selectedModel,
                        messages = result.messages,
                        completedAssistantId = result.messages.lastOrNull {
                            it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED
                        }?.id.orEmpty(),
                        protocolResult = onlineProtocolResult,
                        handledTransferUpdates = handledTransferUpdates,
                    )
                    applyActiveRawMessages(postDirectiveMessages)
                    val completedAssistantMessage = postDirectiveMessages.lastOrNull {
                        it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED
                    }
                    executeAfterAssistantScripts(
                        session = session,
                        scenario = scenario,
                        assistant = assistant,
                        userText = latestUserInputText,
                        assistantMessage = completedAssistantMessage,
                    )
                    markPhoneObservationConsumedIfNeeded(
                        conversationId = session.conversationId,
                        assistantMessage = completedAssistantMessage,
                    )
                    if (finishSendingOnCompletion && canApplyUiUpdate()) {
                        updateUiState { current ->
                            RoleplayStateSupport.finishSending(current, errorMessage = null)
                        }
                    }
                    launchConversationSummaryGeneration(
                        session.conversationId,
                        postDirectiveMessages,
                        state.settings,
                        assistant,
                        scenario,
                    )
                    launchVoiceSynthesis(
                        VoiceSynthesisRequest(
                            conversationId = session.conversationId,
                            selectedModel = selectedModel,
                            messages = postDirectiveMessages,
                            settings = state.settings,
                            fallbackAssistantId = scenario.assistantId,
                        ),
                        ::applyRawMessages,
                    )
                    launchAiPhotoGeneration(
                        AiPhotoGenerationRequest(
                            conversationId = session.conversationId,
                            selectedModel = selectedModel,
                            messages = postDirectiveMessages,
                            settings = state.settings,
                            assistant = assistant,
                            scenario = scenario,
                        ),
                        ::applyRawMessages,
                    )
                    val completedCount = postDirectiveMessages.count { it.status == MessageStatus.COMPLETED }
                    val memoryWindow = state.settings.memoryAutoSummaryEvery
                    // 累计水位线：自上次提取以来新增 completed ≥ window 即触发，避免计数跳变跨过倍数点时永久丢失窗口
                    if (memoryExtractionGate.shouldExtract(session.conversationId, completedCount, memoryWindow)) {
                        launchAutomaticMemoryExtraction(
                            session.conversationId,
                            postDirectiveMessages,
                            state.settings,
                            assistant,
                            scenario,
                        )
                    }
                    return RoleplayRoundTripExecutionOutcome(errorMessage = completionError)
                }

                is AssistantRoundTripResult.Cancelled -> {
                    val cancelledSnapshot = resolveCancellationSnapshot(
                        conversationId = session.conversationId,
                        loadingMessage = loadingMessage,
                        cancelledMessages = result.messages,
                        selectedModel = selectedModel,
                    )
                    applyRawMessages(cancelledSnapshot)
                    if (finishSendingOnCompletion && canApplyUiUpdate()) {
                        updateUiState { current ->
                            RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                        }
                    }
                    return RoleplayRoundTripExecutionOutcome(errorMessage = result.errorMessage)
                }

                is AssistantRoundTripResult.Failed -> {
                    if (!canApplyUiUpdate()) {
                        val cleanedMessages = removeStaleAssistantMessage(
                            conversationId = session.conversationId,
                            loadingMessage = loadingMessage,
                            selectedModel = selectedModel,
                        )
                        applyRawMessages(cleanedMessages)
                        return RoleplayRoundTripExecutionOutcome(errorMessage = result.errorMessage)
                    }
                    applyActiveRawMessages(result.messages)
                    if (finishSendingOnCompletion && canApplyUiUpdate()) {
                        updateUiState { current ->
                            RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                        }
                    }
                    return RoleplayRoundTripExecutionOutcome(errorMessage = result.errorMessage)
                }
            }
        } catch (cancellation: CancellationException) {
            val cancelledSnapshot = resolveCancellationSnapshot(
                conversationId = session.conversationId,
                loadingMessage = loadingMessage,
                cancelledMessages = cancelledMessages,
                selectedModel = selectedModel,
            )
            applyRawMessages(cancelledSnapshot)
            if (finishSendingOnCompletion && canApplyUiUpdate() && currentUiState().isSending) {
                updateUiState { current ->
                    RoleplayStateSupport.finishSending(current, errorMessage = null)
                }
            }
            throw cancellation
        } catch (throwable: Throwable) {
            val errorText = SensitiveTextRedactor.throwableMessageForUi(
                throwable = throwable,
                fallback = ROLEPLAY_SEND_FAILURE_MESSAGE,
            )
            val failedAssistant = loadingMessage.copy(
                content = ROLEPLAY_SEND_FAILURE_MESSAGE,
                status = MessageStatus.ERROR,
                parts = emptyList(),
            )
            val failedMessages = buildFinalMessages(failedAssistant)
            if (!canApplyUiUpdate()) {
                val cleanedMessages = removeStaleAssistantMessage(
                    conversationId = session.conversationId,
                    loadingMessage = loadingMessage,
                    selectedModel = selectedModel,
                )
                applyRawMessages(cleanedMessages)
                return RoleplayRoundTripExecutionOutcome(errorMessage = errorText)
            }
            applyActiveRawMessages(failedMessages)
            if (finishSendingOnCompletion && canApplyUiUpdate()) {
                updateUiState { current ->
                    RoleplayStateSupport.finishSending(current, errorMessage = errorText)
                }
            }
            conversationRepository.upsertMessages(
                conversationId = session.conversationId,
                messages = listOf(failedAssistant),
                selectedModel = selectedModel,
            )
            return RoleplayRoundTripExecutionOutcome(errorMessage = errorText)
        }
    }

    suspend fun rewriteOutgoingTextWithScripts(
        session: com.example.myapplication.model.RoleplaySession,
        scenario: com.example.myapplication.model.RoleplayScenario,
        assistant: Assistant?,
        text: String,
    ): String {
        val normalizedText = text.trim()
        if (normalizedText.isBlank()) {
            return text
        }
        val result = scriptEventCoordinator.execute(
            RoleplayScriptEventRequest(
                event = RoleplayScriptEvent.BEFORE_SEND,
                sessionId = session.id,
                scenarioId = scenario.id,
                characterId = assistant?.id ?: scenario.assistantId,
                userText = normalizedText,
            ),
        )
        return result.outgoingMessage
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: text
    }

    private suspend fun removeStaleAssistantMessage(
        conversationId: String,
        loadingMessage: ChatMessage,
        selectedModel: String,
    ): List<ChatMessage> {
        val cleanedMessages = conversationRepository.listMessages(conversationId)
            .filterNot { it.id == loadingMessage.id }
        conversationRepository.replaceConversationSnapshot(
            conversationId = conversationId,
            messages = cleanedMessages,
            selectedModel = selectedModel,
        )
        return cleanedMessages
    }

    private suspend fun resolveCancellationSnapshot(
        conversationId: String,
        loadingMessage: ChatMessage,
        cancelledMessages: List<ChatMessage>,
        selectedModel: String,
    ): List<ChatMessage> = withContext(NonCancellable) {
        // 该函数在已取消的协程里被调用：真实 Room 的挂起点在取消态会抛 CancellationException，
        // 导致清理（读取 + 落库）半途中断、loading 占位永久残留。用 NonCancellable 保证清理跑完。
        val persistedMessages = conversationRepository.listMessages(conversationId)
        val loadingExists = persistedMessages.any { it.id == loadingMessage.id }
        if (!loadingExists) {
            return@withContext persistedMessages.ifEmpty { cancelledMessages }
        }
        val cancelledMessageIds = cancelledMessages.mapTo(linkedSetOf()) { it.id }
        val hasNewerExternalMessages = persistedMessages.any { message ->
            message.id != loadingMessage.id && message.id !in cancelledMessageIds
        }
        val snapshot = if (hasNewerExternalMessages) {
            persistedMessages.filterNot { it.id == loadingMessage.id }
        } else {
            cancelledMessages
        }
        conversationRepository.replaceConversationSnapshot(
            conversationId = conversationId,
            messages = snapshot,
            selectedModel = selectedModel,
        )
        snapshot
    }

    private fun List<ChatMessage>.assistantMessageError(loadingMessageId: String): String? {
        return firstOrNull { it.id == loadingMessageId && it.status == MessageStatus.ERROR }
            ?.content
            ?.takeIf { it.isNotBlank() }
    }

    private fun limitOnlineReplyParts(
        parts: List<ChatMessagePart>,
        scenario: com.example.myapplication.model.RoleplayScenario,
    ): List<ChatMessagePart> {
        if (scenario.interactionMode != com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE) {
            return parts
        }
        val maxVisibleReplyCount = scenario.normalizedOnlineReplyRange().last
        var visibleReplyCount = 0
        var hasThought = false
        return parts.filter { part ->
            if (part.isOnlineThoughtPart()) {
                if (hasThought) {
                    false
                } else {
                    hasThought = true
                    true
                }
            } else {
                visibleReplyCount += 1
                visibleReplyCount <= maxVisibleReplyCount
            }
        }
    }

    private suspend fun markPhoneObservationConsumedIfNeeded(
        conversationId: String,
        assistantMessage: ChatMessage?,
    ) {
        val observation = phoneSnapshotRepository.getObservation(conversationId) ?: return
        if (observation.ownerType != PhoneSnapshotOwnerType.USER || observation.hasVisibleFeedback) {
            return
        }
        val feedbackMessage = assistantMessage ?: return
        phoneSnapshotRepository.upsertObservation(
            observation.copy(
                hasVisibleFeedback = true,
                feedbackMessageId = feedbackMessage.id,
                usedFindingKeys = observation.keyFindings,
                updatedAt = nowProvider(),
            ),
        )
        roleplayRepository.updateOnlineMeta(conversationId) { meta ->
            com.example.myapplication.model.RoleplayOnlineMeta(
                conversationId = conversationId,
                lastCompensationBucket = meta?.lastCompensationBucket.orEmpty(),
                lastConsumedObservationUpdatedAt = observation.updatedAt,
                lastSystemEventToken = meta?.lastSystemEventToken.orEmpty(),
                activeVideoCallSessionId = meta?.activeVideoCallSessionId.orEmpty(),
                activeVideoCallStartedAt = meta?.activeVideoCallStartedAt ?: 0L,
                updatedAt = nowProvider(),
            )
        }
    }

    private suspend fun streamRoleplayAssistantReply(
        requestMessages: List<ChatMessage>,
        systemPrompt: String,
        promptEnvelope: PromptEnvelope,
        fullContent: StringBuilder,
        fullReasoningSteps: MutableList<ChatReasoningStep>,
        fullParts: MutableList<ChatMessagePart>,
        toolingOptions: GatewayToolingOptions,
        expectedInteractionMode: com.example.myapplication.model.RoleplayInteractionMode,
        isGroupChat: Boolean,
        canApplyUiUpdate: () -> Boolean,
    ) {
        val streamBuffer = StreamingReplyBuffer()
        ChatStreamingSupport.collectStreamingReply(
            streamBuffer = streamBuffer,
            streamEvents = aiGateway.sendMessageStream(
                messages = requestMessages,
                systemPrompt = systemPrompt,
                promptMode = PromptMode.ROLEPLAY,
                promptEnvelope = promptEnvelope,
                toolingOptions = toolingOptions,
            ),
            publishFrame = { content, _, reasoningSteps, parts ->
                if (!canApplyUiUpdate()) {
                    return@collectStreamingReply
                }
                fullContent.setLength(0)
                fullContent.append(content)
                fullReasoningSteps.clear()
                fullReasoningSteps += reasoningSteps
                fullParts.clear()
                fullParts += parts

                updateUiState { current ->
                    val streamingText = when (expectedInteractionMode) {
                        com.example.myapplication.model.RoleplayInteractionMode.OFFLINE_LONGFORM -> {
                            RoleplayLongformMarkupParser.stripMarkupForDisplay(
                                RoleplayOutputLeakSanitizer.sanitize(
                                    rawContent = content,
                                    interactionMode = expectedInteractionMode,
                                ),
                            )
                        }
                        com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE -> {
                            if (isGroupChat) {
                                OnlineActionProtocolParser.extractGroupTextStreamingPreview(content)
                            } else {
                                OnlineActionProtocolParser.extractStreamingPreview(content)
                                    .ifBlank { OnlineActionProtocolParser.extractFallbackStreamingPreview(content) }
                            }
                        }
                        com.example.myapplication.model.RoleplayInteractionMode.OFFLINE_DIALOGUE -> {
                            outputParser.stripMarkup(
                                RoleplayOutputLeakSanitizer.sanitize(
                                    rawContent = content,
                                    interactionMode = expectedInteractionMode,
                                ),
                            )
                        }
                    }
                    RoleplayStateSupport.applyStreamingContent(
                        current,
                        streamingText,
                    )
                }
            },
        )
    }

    private suspend fun resolveRequestMessagesForRoundTrip(
        conversation: Conversation,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
    ): List<ChatMessage> {
        val summary = conversationSummaryRepository.getSummary(conversation.id)
        return ConversationMessageTransforms.trimRequestMessagesWithSummary(
            requestMessages = requestMessages,
            completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED },
            summaryCoveredMessageCount = summary
                ?.takeIf { it.summary.isNotBlank() }
                ?.coveredMessageCount
                ?: 0,
            recentWindow = resolveSummaryRecentWindow(assistant),
        )
    }

    private fun resolveSummaryRecentWindow(
        assistant: Assistant?,
    ): Int {
        return RoleplaySummaryWindowSupport.resolveRecentWindow(assistant)
    }

    private fun formatScriptPromptAdditions(additions: List<String>): String {
        val content = additions
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(separator = "\n\n")
        if (content.isBlank()) {
            return ""
        }
        return "【脚本追加提示】\n$content"
    }

    private suspend fun executeAfterAssistantScripts(
        session: com.example.myapplication.model.RoleplaySession,
        scenario: com.example.myapplication.model.RoleplayScenario,
        assistant: Assistant?,
        userText: String,
        assistantMessage: ChatMessage?,
    ) {
        val assistantText = assistantMessage?.content?.trim().orEmpty()
        if (assistantText.isBlank()) {
            return
        }
        val request = RoleplayScriptEventRequest(
            event = RoleplayScriptEvent.AFTER_ASSISTANT,
            sessionId = session.id,
            scenarioId = scenario.id,
            characterId = assistant?.id ?: scenario.assistantId,
            userText = userText,
            assistantText = assistantText,
        )
        applyScriptUiDirectives(scriptEventCoordinator.execute(request))
        applyScriptUiDirectives(
            scriptEventCoordinator.execute(
                request.copy(event = RoleplayScriptEvent.RENDER_STATE),
            ),
        )
    }

    private fun applyScriptUiDirectives(result: RoleplayScriptExecutionResult) {
        val message = result.uiDirectives
            .firstOrNull { directive ->
                directive.type.lowercase() in setOf("notice", "status", "toast")
            }
            ?.payload
            ?.trim()
            ?.let { payload -> SensitiveTextRedactor.redact(payload, maxLength = SCRIPT_NOTICE_MAX_LENGTH) }
            ?.takeIf(String::isNotBlank)
            ?: return
        updateUiState { current ->
            RoleplayStateSupport.applyNoticeMessage(current, message)
        }
    }

    private suspend fun applyOnlineProtocolDirectivesIfNeeded(
        conversationId: String,
        selectedModel: String,
        messages: List<ChatMessage>,
        completedAssistantId: String,
        protocolResult: OnlineActionProtocolParseResult?,
        handledTransferUpdates: MutableSet<Pair<String, TransferStatus>> = mutableSetOf(),
    ): List<ChatMessage> {
        val directives = protocolResult?.directives.orEmpty()
        if (directives.isEmpty()) {
            return messages
        }
        var updatedMessages = messages
        if (OnlineActionDirective.RecallPreviousAssistant in directives) {
            updatedMessages = conversationRepository.recallLatestAssistantMessage(
                conversationId = conversationId,
                selectedModel = selectedModel,
                excludingMessageId = completedAssistantId,
            )
        }
        directives.filterIsInstance<OnlineActionDirective.UpdateTransferStatus>()
            .forEach { directive ->
                val targetTransferId = directive.refId.ifBlank {
                    findLatestPendingIncomingTransferId(updatedMessages)
                }
                if (targetTransferId.isBlank()) {
                    return@forEach
                }
                updatedMessages = conversationRepository.applyTransferUpdates(
                    conversationId = conversationId,
                    updates = listOf(
                        TransferUpdateDirective(
                            refId = targetTransferId,
                            status = directive.status,
                        ),
                    ),
                    selectedModel = selectedModel,
                )
                when (directive.status) {
                    TransferStatus.RECEIVED -> if (handledTransferUpdates.add(targetTransferId to directive.status)) {
                        settleTransfer(targetTransferId)
                    }
                    TransferStatus.REJECTED -> if (handledTransferUpdates.add(targetTransferId to directive.status)) {
                        releaseTransfer(targetTransferId)
                    }
                    TransferStatus.PENDING -> Unit
                }
            }
        return updatedMessages
    }

    private suspend fun settleParsedTransferUpdatesIfNeeded(
        updates: List<TransferUpdateDirective>,
        handledTransferUpdates: MutableSet<Pair<String, TransferStatus>>,
    ) {
        updates.forEach { update ->
            val refId = update.refId.trim()
            if (refId.isBlank()) {
                return@forEach
            }
            when (update.status) {
                TransferStatus.RECEIVED -> if (handledTransferUpdates.add(refId to update.status)) {
                    settleTransfer(refId)
                }
                TransferStatus.REJECTED -> if (handledTransferUpdates.add(refId to update.status)) {
                    releaseTransfer(refId)
                }
                TransferStatus.PENDING -> Unit
            }
        }
    }

    private fun findLatestPendingIncomingTransferId(
        messages: List<ChatMessage>,
    ): String {
        return messages.asReversed()
            .firstNotNullOfOrNull { message ->
                message.parts.asReversed().firstOrNull { part ->
                    part.specialDirection == TransferDirection.USER_TO_ASSISTANT &&
                        part.specialStatus == TransferStatus.PENDING &&
                        part.specialId.isNotBlank()
                }?.specialId
            }
            .orEmpty()
    }

    private fun List<OnlineActionDirective>.toDirectiveResultText(): String {
        return mapNotNull { directive ->
            when (directive) {
                OnlineActionDirective.RecallPreviousAssistant -> "已撤回上一条回复"
                is OnlineActionDirective.UpdateTransferStatus -> directive.status.transferResultText()
            }
        }.distinct()
            .joinToString(separator = "\n")
    }

    private fun roleplayStateCharacterName(
        scenario: com.example.myapplication.model.RoleplayScenario,
        assistant: Assistant?,
    ): String {
        return scenario.characterDisplayNameOverride.trim()
            .ifBlank { assistant?.name?.trim().orEmpty() }
            .ifBlank { "角色" }
    }

    private companion object {
        private const val ROLEPLAY_SEND_FAILURE_MESSAGE = "发送失败，请检查网络或模型配置后重试"
        private const val SCRIPT_NOTICE_MAX_LENGTH = 160
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 12
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 4
    }
}

/**
 * 汇总线上/线下扮演一轮回复最终落库的消息部件。
 *
 * 线上单聊场景下，可见文本与 ai_photo 由 [onlineProtocolResult]（JSON 动作协议）统一负责，
 * [parsedOutput] 仅用于补齐 XML `<play>` 特殊玩法卡片，避免同一张照片被两条解析路径各计一份。
 */
internal fun resolveRoleplayCompletedParts(
    onlineProtocolResult: OnlineActionProtocolParseResult?,
    parsedOutput: ParsedAssistantSpecialOutput,
    scenario: com.example.myapplication.model.RoleplayScenario,
    referenceCandidates: List<OnlineMessageReferenceCandidate>,
): List<ChatMessagePart> {
    return if (onlineProtocolResult != null) {
        normalizeChatMessageParts(
            RoleplayOnlineReferenceSupport.resolveReplyTargets(
                parts = onlineProtocolResult.parts,
                candidates = referenceCandidates,
            ) + if (scenario.isGroupChat) {
                emptyList()
            } else {
                parsedOutput.parts.filter { part ->
                    part.type != ChatMessagePartType.TEXT &&
                        part.actionType != ChatActionType.AI_PHOTO
                }
            },
        )
    } else if (scenario.interactionMode == com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE) {
        parsedOutput.parts.filter { part -> part.type != ChatMessagePartType.TEXT }
    } else if (scenario.isGroupChat) {
        parsedOutput.parts.filter { part -> part.type == ChatMessagePartType.TEXT }
    } else {
        parsedOutput.parts
    }
}
