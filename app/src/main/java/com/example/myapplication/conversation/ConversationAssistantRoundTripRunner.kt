package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.MessageCitation
import kotlinx.coroutines.CancellationException

data class StreamedAssistantPayload(
    val content: String,
    val reasoning: String,
    val parts: List<ChatMessagePart>,
    val citations: List<MessageCitation>,
)

data class AssistantRoundTripOutcome(
    val messages: List<ChatMessage>,
    val errorMessage: String?,
)

sealed class AssistantRoundTripResult {
    data class Completed(
        val messages: List<ChatMessage>,
    ) : AssistantRoundTripResult()

    data class Cancelled(
        val messages: List<ChatMessage>,
        val errorMessage: String?,
    ) : AssistantRoundTripResult()

    data class Failed(
        val messages: List<ChatMessage>,
        val errorMessage: String,
    ) : AssistantRoundTripResult()
}

data class AssistantRoundTripRequest(
    val conversationId: String,
    val selectedModel: String,
    val requestMessages: List<ChatMessage>,
    val loadingMessage: ChatMessage,
    val buildFinalMessages: (ChatMessage) -> List<ChatMessage>,
    val systemPrompt: String,
    val streamReply: suspend (requestMessages: List<ChatMessage>, systemPrompt: String) -> Unit,
    val currentPayload: () -> StreamedAssistantPayload,
    val onCompleted: (
        payload: StreamedAssistantPayload,
        parsedOutput: ParsedAssistantSpecialOutput,
        loadingMessage: ChatMessage,
    ) -> ChatMessage,
    val onCancelled: (
        payload: StreamedAssistantPayload,
        loadingMessage: ChatMessage,
    ) -> AssistantRoundTripOutcome?,
    val onFailed: (
        payload: StreamedAssistantPayload,
        throwable: Throwable,
        loadingMessage: ChatMessage,
    ) -> AssistantRoundTripOutcome,
)

class ConversationAssistantRoundTripRunner(
    private val conversationRepository: ConversationRepository,
    private val aiGateway: AiGateway,
) {
    suspend fun execute(request: AssistantRoundTripRequest): AssistantRoundTripResult {
        try {
            request.streamReply(request.requestMessages, request.systemPrompt)
            val payload = request.currentPayload()
            val parsedOutput = aiGateway.parseAssistantSpecialOutput(
                content = payload.content,
                existingParts = payload.parts,
            )
            val completedAssistant = request.onCompleted(
                payload,
                parsedOutput,
                request.loadingMessage,
            )
            conversationRepository.upsertMessages(
                conversationId = request.conversationId,
                messages = listOf(completedAssistant),
                selectedModel = request.selectedModel,
            )
            val completedMessages = if (parsedOutput.transferUpdates.isEmpty()) {
                conversationRepository.listMessages(request.conversationId)
            } else {
                conversationRepository.applyTransferUpdates(
                    conversationId = request.conversationId,
                    updates = parsedOutput.transferUpdates,
                    selectedModel = request.selectedModel,
                )
            }
            return AssistantRoundTripResult.Completed(completedMessages)
        } catch (cancellation: CancellationException) {
            val outcome = request.onCancelled(
                request.currentPayload(),
                request.loadingMessage,
            ) ?: throw cancellation
            persistOutcomeAssistantMessage(
                request = request,
                messages = outcome.messages,
            )
            return AssistantRoundTripResult.Cancelled(
                messages = outcome.messages,
                errorMessage = outcome.errorMessage,
            )
        } catch (throwable: Throwable) {
            val outcome = request.onFailed(
                request.currentPayload(),
                throwable,
                request.loadingMessage,
            )
            persistOutcomeAssistantMessage(
                request = request,
                messages = outcome.messages,
            )
            return AssistantRoundTripResult.Failed(
                messages = outcome.messages,
                errorMessage = outcome.errorMessage ?: (throwable.message ?: "发送失败"),
            )
        }
    }

    private suspend fun persistOutcomeAssistantMessage(
        request: AssistantRoundTripRequest,
        messages: List<ChatMessage>,
    ) {
        val assistantMessage = messages.firstOrNull { it.id == request.loadingMessage.id } ?: return
        conversationRepository.upsertMessages(
            conversationId = request.conversationId,
            messages = listOf(assistantMessage),
            selectedModel = request.selectedModel,
        )
    }
}
