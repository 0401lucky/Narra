package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.AssistantRoundTripOutcome
import com.example.myapplication.conversation.AssistantRoundTripRequest
import com.example.myapplication.conversation.AssistantRoundTripResult
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.ContextGovernanceSupport
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.conversation.StreamingReplyBuffer
import com.example.myapplication.conversation.VoiceSynthesisRequest
import com.example.myapplication.data.repository.TransferUpdateDirective
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.data.repository.phone.PhoneSnapshotRepository
import com.example.myapplication.data.repository.roleplay.RoleplayRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatReasoningStep
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PhoneSnapshotOwnerType
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
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
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayFormatReminderSupport
import com.example.myapplication.roleplay.RoleplayLongformMarkupParser
import com.example.myapplication.roleplay.RoleplayMessageFormatSupport
import com.example.myapplication.roleplay.RoleplayOnlineReferenceSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import kotlinx.coroutines.CancellationException

internal class RoleplayRoundTripExecutor(
    private val aiGateway: AiGateway,
    private val conversationRepository: ConversationRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val phoneSnapshotRepository: PhoneSnapshotRepository,
    private val roleplayRepository: RoleplayRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val assistantRoundTripRunner: ConversationAssistantRoundTripRunner,
    private val outputParser: RoleplayOutputParser,
    private val nowProvider: () -> Long,
    private val currentUiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val updateRawMessages: (List<ChatMessage>) -> Unit,
    private val launchGiftImageGeneration: (GiftImageGenerationRequest, (List<ChatMessage>) -> Unit) -> Unit,
    private val launchVoiceSynthesis: (VoiceSynthesisRequest, (List<ChatMessage>) -> Unit) -> Unit,
    private val launchConversationSummaryGeneration: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
    private val launchAutomaticMemoryExtraction: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
    private val contextLogStore: com.example.myapplication.data.repository.context.ContextLogStore,
) {
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
    ) {
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
                launchGiftImageGeneration(request, updateRawMessages)
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
            val onlineSystemEventContext = RoleplayOnlineReferenceSupport.buildSystemEventPromptContext(
                messages = effectiveRequestMessages,
                outputParser = outputParser,
            )
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
            val promptContext = promptContextAssembler.assemble(
                settings = RoleplayConversationSupport.resolvePromptSettings(scenario, state.settings),
                assistant = RoleplayConversationSupport.resolvePromptAssistant(scenario, assistant),
                conversation = conversation,
                userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessagesForModel),
                recentMessages = requestMessagesForModel,
                promptMode = PromptMode.ROLEPLAY,
            )
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
                isVideoCallActive = state.isVideoCallActive,
                referenceCandidates = referenceCandidates,
            )
            val decoratedPrompt = RoleplayPromptDecorator.decorate(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                includeOpeningNarrationReference = requestMessages.none { it.role == MessageRole.USER },
                isVideoCallActive = state.isVideoCallActive,
                directorNote = buildString {
                    onlineSystemEventContext.takeIf { it.isNotBlank() }?.let { context ->
                        append(context)
                    }
                    directorNote.takeIf { it.isNotBlank() }?.let { note ->
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
                },
                modelId = selectedModel,
            )
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
                            )
                        },
                        currentPayload = {
                            StreamedAssistantPayload(
                                content = fullContent.toString().trim(),
                                reasoning = reasoningStepsToContent(fullReasoningSteps),
                                reasoningSteps = fullReasoningSteps.toList(),
                                parts = fullParts.toList(),
                                citations = emptyList(),
                            )
                        },
                        onCompleted = { payload, parsedOutput, loading ->
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
                            val rawCompletedParts = if (onlineProtocolResult != null) {
                                onlineProtocolResult
                                    ?.parts
                                    ?.let { onlineParts ->
                                        normalizeChatMessageParts(
                                            RoleplayOnlineReferenceSupport.resolveReplyTargets(
                                                parts = onlineParts,
                                                candidates = referenceCandidates,
                                            ) + if (scenario.isGroupChat) {
                                                emptyList()
                                            } else {
                                                parsedOutput.parts.filter { part ->
                                                    part.type != ChatMessagePartType.TEXT
                                                }
                                            },
                                        )
                                    }
                                    .orEmpty()
                            } else if (scenario.interactionMode == com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE) {
                                parsedOutput.parts.filter { part -> part.type != ChatMessagePartType.TEXT }
                            } else if (scenario.isGroupChat) {
                                parsedOutput.parts.filter { part -> part.type == ChatMessagePartType.TEXT }
                            } else {
                                parsedOutput.parts
                            }
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
                        onCancelled = { _, _ ->
                            AssistantRoundTripOutcome(
                                messages = cancelledMessages,
                                errorMessage = null,
                            )
                        },
                        onFailed = { _, throwable, loading ->
                            val errorText = throwable.message ?: "发送失败"
                            AssistantRoundTripOutcome(
                                messages = buildFinalMessages(
                                    loading.copy(
                                        content = errorText,
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
                    val postDirectiveMessages = applyOnlineProtocolDirectivesIfNeeded(
                        conversationId = session.conversationId,
                        selectedModel = selectedModel,
                        messages = result.messages,
                        completedAssistantId = result.messages.lastOrNull {
                            it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED
                        }?.id.orEmpty(),
                        protocolResult = onlineProtocolResult,
                    )
                    updateRawMessages(postDirectiveMessages)
                    markPhoneObservationConsumedIfNeeded(
                        conversationId = session.conversationId,
                        assistantMessage = postDirectiveMessages.lastOrNull { it.role == MessageRole.ASSISTANT && it.status == MessageStatus.COMPLETED },
                    )
                    if (finishSendingOnCompletion) {
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
                        updateRawMessages,
                    )
                    val completedCount = postDirectiveMessages.count { it.status == MessageStatus.COMPLETED }
                    val memoryWindow = state.settings.memoryAutoSummaryEvery
                    // Tavo 语义：每 N 条 completed 消息触发一次记忆提取，而不是每条都触发
                    if (memoryWindow > 0 && completedCount > 0 && completedCount % memoryWindow == 0) {
                        launchAutomaticMemoryExtraction(
                            session.conversationId,
                            postDirectiveMessages,
                            state.settings,
                            assistant,
                            scenario,
                        )
                    }
                }

                is AssistantRoundTripResult.Cancelled -> {
                    conversationRepository.replaceConversationSnapshot(
                        conversationId = session.conversationId,
                        messages = result.messages,
                        selectedModel = selectedModel,
                    )
                    updateRawMessages(result.messages)
                    if (finishSendingOnCompletion) {
                        updateUiState { current ->
                            RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                        }
                    }
                }

                is AssistantRoundTripResult.Failed -> {
                    updateRawMessages(result.messages)
                    if (finishSendingOnCompletion) {
                        updateUiState { current ->
                            RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                        }
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            conversationRepository.replaceConversationSnapshot(
                conversationId = session.conversationId,
                messages = cancelledMessages,
                selectedModel = selectedModel,
            )
            updateRawMessages(cancelledMessages)
            if (finishSendingOnCompletion && currentUiState().isSending) {
                updateUiState { current ->
                    RoleplayStateSupport.finishSending(current, errorMessage = null)
                }
            }
            throw cancellation
        } catch (throwable: Throwable) {
            val errorText = throwable.message ?: "发送失败"
            val failedAssistant = loadingMessage.copy(
                content = errorText,
                status = MessageStatus.ERROR,
                parts = emptyList(),
            )
            val failedMessages = buildFinalMessages(failedAssistant)
            updateRawMessages(failedMessages)
            if (finishSendingOnCompletion) {
                updateUiState { current ->
                    RoleplayStateSupport.finishSending(current, errorMessage = errorText)
                }
            }
            conversationRepository.upsertMessages(
                conversationId = session.conversationId,
                messages = listOf(failedAssistant),
                selectedModel = selectedModel,
            )
        }
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
        val meta = roleplayRepository.getOnlineMeta(conversationId)
        roleplayRepository.upsertOnlineMeta(
            com.example.myapplication.model.RoleplayOnlineMeta(
                conversationId = conversationId,
                lastCompensationBucket = meta?.lastCompensationBucket.orEmpty(),
                lastConsumedObservationUpdatedAt = observation.updatedAt,
                lastSystemEventToken = meta?.lastSystemEventToken.orEmpty(),
                activeVideoCallSessionId = meta?.activeVideoCallSessionId.orEmpty(),
                activeVideoCallStartedAt = meta?.activeVideoCallStartedAt ?: 0L,
                updatedAt = nowProvider(),
            ),
        )
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
                fullContent.setLength(0)
                fullContent.append(content)
                fullReasoningSteps.clear()
                fullReasoningSteps += reasoningSteps
                fullParts.clear()
                fullParts += parts

                updateUiState { current ->
                    val streamingText = when (expectedInteractionMode) {
                        com.example.myapplication.model.RoleplayInteractionMode.OFFLINE_LONGFORM -> {
                            RoleplayLongformMarkupParser.stripMarkupForDisplay(content)
                        }
                        com.example.myapplication.model.RoleplayInteractionMode.ONLINE_PHONE -> {
                            if (isGroupChat) {
                                OnlineActionProtocolParser.extractGroupTextStreamingPreview(content)
                            } else {
                                OnlineActionProtocolParser.extractStreamingPreview(content)
                                    .ifBlank { outputParser.stripMarkup(content) }
                            }
                        }
                        com.example.myapplication.model.RoleplayInteractionMode.OFFLINE_DIALOGUE -> {
                            outputParser.stripMarkup(content)
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
        return assistant?.contextMessageSize?.takeIf { it > 0 }
            ?: SUMMARY_RECENT_MESSAGE_WINDOW
    }

    private suspend fun applyOnlineProtocolDirectivesIfNeeded(
        conversationId: String,
        selectedModel: String,
        messages: List<ChatMessage>,
        completedAssistantId: String,
        protocolResult: OnlineActionProtocolParseResult?,
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
            }
        return updatedMessages
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
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 12
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 4
        private const val SUMMARY_RECENT_MESSAGE_WINDOW = 8
    }
}
