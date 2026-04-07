package com.example.myapplication.viewmodel

import com.example.myapplication.conversation.AssistantRoundTripOutcome
import com.example.myapplication.conversation.AssistantRoundTripRequest
import com.example.myapplication.conversation.AssistantRoundTripResult
import com.example.myapplication.conversation.ConversationSummaryDebugSupport
import com.example.myapplication.conversation.ConversationAssistantRoundTripRunner
import com.example.myapplication.conversation.ContextGovernanceSupport
import com.example.myapplication.conversation.ConversationMessageTransforms
import com.example.myapplication.conversation.GiftImageGenerationRequest
import com.example.myapplication.conversation.RoundTripInitialPersistence
import com.example.myapplication.conversation.StreamedAssistantPayload
import com.example.myapplication.context.PromptContextAssembler
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.context.ConversationSummaryRepository
import com.example.myapplication.model.Assistant
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolRuntimeContext
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.toContentMirror
import com.example.myapplication.roleplay.RoleplayConversationSupport
import com.example.myapplication.roleplay.RoleplayOutputParser
import com.example.myapplication.roleplay.RoleplayPromptDecorator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

internal class RoleplayRoundTripExecutor(
    private val aiGateway: AiGateway,
    private val conversationRepository: ConversationRepository,
    private val conversationSummaryRepository: ConversationSummaryRepository,
    private val promptContextAssembler: PromptContextAssembler,
    private val assistantRoundTripRunner: ConversationAssistantRoundTripRunner,
    private val outputParser: RoleplayOutputParser,
    private val nowProvider: () -> Long,
    private val currentUiState: () -> RoleplayUiState,
    private val updateUiState: ((RoleplayUiState) -> RoleplayUiState) -> Unit,
    private val updateRawMessages: (List<ChatMessage>) -> Unit,
    private val launchGiftImageGeneration: (GiftImageGenerationRequest, (List<ChatMessage>) -> Unit) -> Unit,
    private val launchConversationSummaryGeneration: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
    private val launchAutomaticMemoryExtraction: (String, List<ChatMessage>, com.example.myapplication.model.AppSettings, Assistant?, com.example.myapplication.model.RoleplayScenario) -> Unit,
) {
    suspend fun execute(
        state: RoleplayUiState,
        scenario: com.example.myapplication.model.RoleplayScenario,
        session: com.example.myapplication.model.RoleplaySession,
        selectedModel: String,
        assistant: Assistant?,
        requestMessages: List<ChatMessage>,
        initialPersistence: RoundTripInitialPersistence,
        loadingMessage: ChatMessage,
        buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
        giftImageRequest: GiftImageGenerationRequest? = null,
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
            val promptContext = promptContextAssembler.assemble(
                settings = state.settings,
                assistant = assistant,
                conversation = conversation,
                userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
                recentMessages = requestMessages,
                promptMode = PromptMode.ROLEPLAY,
            )
            val directorNote = RoleplayConversationSupport.buildDynamicDirectorNote(
                messages = requestMessages,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                outputParser = outputParser,
            )
            val decoratedPrompt = RoleplayPromptDecorator.decorate(
                baseSystemPrompt = promptContext.systemPrompt,
                scenario = scenario,
                assistant = assistant,
                settings = state.settings,
                includeOpeningNarrationReference = requestMessages.none { it.role == MessageRole.USER },
                directorNote = directorNote,
            )
            val effectiveRequestMessages = resolveRequestMessagesForRoundTrip(
                conversation = conversation,
                assistant = assistant,
                requestMessages = requestMessages,
            )
            val toolingOptions = GatewayToolingOptions.localContextOnly(
                GatewayToolRuntimeContext(
                    promptMode = PromptMode.ROLEPLAY,
                    assistant = assistant,
                    conversation = conversation,
                    userInputText = RoleplayConversationSupport.resolveLatestUserInputText(requestMessages),
                    recentMessages = requestMessages,
                ),
            )
            val debugDump = buildString {
                append(
                    ConversationSummaryDebugSupport.appendStatusLine(
                        debugDump = promptContext.debugDump,
                        hasSummary = promptContext.summaryCoveredMessageCount > 0,
                        coveredMessageCount = promptContext.summaryCoveredMessageCount,
                        completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED },
                        triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                    ),
                )
                if (decoratedPrompt.isNotBlank()) {
                    append("\n\n【RP 装饰后提示词】\n")
                    append(decoratedPrompt)
                }
            }
            updateUiState { current ->
                RoleplayStateSupport.applyPromptContext(
                    current = current,
                    summaryCoveredMessageCount = promptContext.summaryCoveredMessageCount,
                    worldBookHitCount = promptContext.worldBookHitCount,
                    memoryInjectionCount = promptContext.memoryInjectionCount,
                    debugDump = debugDump,
                    contextGovernance = ContextGovernanceSupport.buildSnapshot(
                        settings = state.settings,
                        assistant = assistant,
                        promptMode = PromptMode.ROLEPLAY,
                        selectedModel = selectedModel,
                        requestMessages = requestMessages,
                        effectiveRequestMessages = effectiveRequestMessages,
                        promptContext = promptContext,
                        completedMessageCount = requestMessages.count { it.status == MessageStatus.COMPLETED },
                        triggerMessageCount = SUMMARY_TRIGGER_MESSAGE_COUNT,
                        recentWindow = resolveSummaryRecentWindow(assistant),
                        minCoveredMessageCount = SUMMARY_MIN_COVERED_MESSAGE_COUNT,
                        toolingOptions = toolingOptions,
                        rawDebugDump = debugDump,
                    ),
                )
            }
            val fullContent = StringBuilder()
            val fullReasoning = StringBuilder()
            val fullParts = mutableListOf<ChatMessagePart>()

            when (
                val result = assistantRoundTripRunner.execute(
                    AssistantRoundTripRequest(
                        conversationId = session.conversationId,
                        selectedModel = selectedModel,
                        requestMessages = effectiveRequestMessages,
                        loadingMessage = loadingMessage,
                        buildFinalMessages = buildFinalMessages,
                        systemPrompt = decoratedPrompt,
                        streamReply = { messages, systemPrompt ->
                            streamRoleplayAssistantReply(
                                requestMessages = messages,
                                systemPrompt = systemPrompt,
                                fullContent = fullContent,
                                fullReasoning = fullReasoning,
                                fullParts = fullParts,
                                toolingOptions = toolingOptions,
                            )
                        },
                        currentPayload = {
                            StreamedAssistantPayload(
                                content = fullContent.toString().trim(),
                                reasoning = fullReasoning.toString(),
                                parts = fullParts.toList(),
                                citations = emptyList(),
                            )
                        },
                        onCompleted = { payload, parsedOutput, loading ->
                            loading.copy(
                                content = parsedOutput.content.takeIf { it.isNotBlank() }
                                    ?: if (parsedOutput.transferUpdates.isNotEmpty()) {
                                        "已收款"
                                    } else {
                                        parsedOutput.parts.toContentMirror(
                                            imageFallback = "角色返回了图片",
                                            specialFallback = "角色已回应",
                                        ).ifBlank { "模型未返回有效内容" }
                                    },
                                status = MessageStatus.COMPLETED,
                                reasoningContent = payload.reasoning,
                                parts = parsedOutput.parts,
                            )
                        },
                        onCancelled = { _, _ ->
                            null
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
                    updateRawMessages(result.messages)
                    updateUiState { current ->
                        RoleplayStateSupport.finishSending(current, errorMessage = null)
                    }
                    launchConversationSummaryGeneration(
                        session.conversationId,
                        result.messages,
                        state.settings,
                        assistant,
                        scenario,
                    )
                    launchAutomaticMemoryExtraction(
                        session.conversationId,
                        result.messages,
                        state.settings,
                        assistant,
                        scenario,
                    )
                }

                is AssistantRoundTripResult.Cancelled -> {
                    updateRawMessages(result.messages)
                    updateUiState { current ->
                        RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                    }
                }

                is AssistantRoundTripResult.Failed -> {
                    updateRawMessages(result.messages)
                    updateUiState { current ->
                        RoleplayStateSupport.finishSending(current, errorMessage = result.errorMessage)
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            if (currentUiState().isSending) {
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
            updateUiState { current ->
                RoleplayStateSupport.finishSending(current, errorMessage = errorText)
            }
            conversationRepository.upsertMessages(
                conversationId = session.conversationId,
                messages = listOf(failedAssistant),
                selectedModel = selectedModel,
            )
        }
    }

    private suspend fun streamRoleplayAssistantReply(
        requestMessages: List<ChatMessage>,
        systemPrompt: String,
        fullContent: StringBuilder,
        fullReasoning: StringBuilder,
        fullParts: MutableList<ChatMessagePart>,
        toolingOptions: GatewayToolingOptions,
    ) {
        aiGateway.sendMessageStream(
            messages = requestMessages,
            systemPrompt = systemPrompt,
            promptMode = PromptMode.ROLEPLAY,
            toolingOptions = toolingOptions,
        ).collect { event ->
            when (event) {
                is ChatStreamEvent.ContentDelta -> {
                    fullContent.append(event.value)
                    updateUiState { current ->
                        RoleplayStateSupport.applyStreamingContent(
                            current,
                            outputParser.stripMarkup(fullContent.toString()),
                        )
                    }
                }

                is ChatStreamEvent.ReasoningDelta -> fullReasoning.append(event.value)
                is ChatStreamEvent.ImageDelta -> {
                    fullParts += imageMessagePart(
                        uri = event.part.uri,
                        mimeType = event.part.mimeType,
                        fileName = event.part.fileName,
                    )
                }

                is ChatStreamEvent.Citations -> Unit
                ChatStreamEvent.Completed -> Unit
            }
        }
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

    private companion object {
        private const val SUMMARY_TRIGGER_MESSAGE_COUNT = 12
        private const val SUMMARY_MIN_COVERED_MESSAGE_COUNT = 4
        private const val SUMMARY_RECENT_MESSAGE_WINDOW = 8
    }
}
