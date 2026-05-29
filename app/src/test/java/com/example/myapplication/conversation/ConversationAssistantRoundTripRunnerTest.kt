package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.ai.AiGateway
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePart
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.Conversation
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.PromptEnvelope
import com.example.myapplication.model.PromptMode
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationAssistantRoundTripRunnerTest {
    @Test
    fun execute_skipsCompletedPersistenceWhenRequestIsInactive() = runTest {
        val conversationId = "conv-1"
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = conversationId,
            role = MessageRole.USER,
            content = "新的一轮",
            createdAt = 1L,
        )
        val loadingMessage = ChatMessage(
            id = "assistant-loading",
            conversationId = conversationId,
            role = MessageRole.ASSISTANT,
            content = "",
            status = MessageStatus.LOADING,
            createdAt = 2L,
        )
        val store = FakeConversationStore(
            conversations = listOf(
                Conversation(
                    id = conversationId,
                    title = "剧情",
                    model = "chat-model",
                    createdAt = 1L,
                    updatedAt = 2L,
                ),
            ),
            messagesByConversation = mapOf(
                conversationId to listOf(userMessage, loadingMessage),
            ),
        )
        val runner = ConversationAssistantRoundTripRunner(
            conversationRepository = ConversationRepository(store),
            aiGateway = completedGateway(),
        )

        val result = runner.execute(
            AssistantRoundTripRequest(
                conversationId = conversationId,
                selectedModel = "chat-model",
                requestMessages = listOf(userMessage),
                loadingMessage = loadingMessage,
                buildFinalMessages = { completedAssistant -> listOf(userMessage, completedAssistant) },
                systemPrompt = "系统提示",
                streamReply = { _, _ -> },
                currentPayload = {
                    StreamedAssistantPayload(
                        content = "旧任务完成内容",
                        reasoning = "",
                        reasoningSteps = emptyList(),
                        parts = emptyList(),
                        citations = emptyList(),
                    )
                },
                canPersistResult = { false },
                onCompleted = { payload, _, loading ->
                    loading.copy(
                        content = payload.content,
                        status = MessageStatus.COMPLETED,
                    )
                },
                onCancelled = { _, _ -> null },
                onFailed = { _, throwable, loading ->
                    AssistantRoundTripOutcome(
                        messages = listOf(loading.copy(content = throwable.message.orEmpty())),
                        errorMessage = throwable.message,
                    )
                },
            ),
        )

        assertTrue(result is AssistantRoundTripResult.Completed)
        assertEquals("旧任务完成内容", (result as AssistantRoundTripResult.Completed).messages.last().content)
        assertEquals(0, store.upsertConversationWithMessagesCount)
        assertEquals(
            listOf(userMessage, loadingMessage),
            store.listMessages(conversationId),
        )
    }

    private fun completedGateway(): AiGateway {
        return object : AiGateway {
            override suspend fun generateImage(prompt: String, modelId: String): List<ImageGenerationResult> {
                return emptyList()
            }

            override suspend fun sendMessage(
                messages: List<ChatMessage>,
                systemPrompt: String,
                promptEnvelope: PromptEnvelope,
                toolingOptions: GatewayToolingOptions,
            ): AssistantReply {
                error("不应调用非流式发送")
            }

            override fun sendMessageStream(
                messages: List<ChatMessage>,
                systemPrompt: String,
                promptMode: PromptMode,
                promptEnvelope: PromptEnvelope,
                toolingOptions: GatewayToolingOptions,
            ): Flow<ChatStreamEvent> {
                return emptyFlow()
            }

            override fun parseAssistantSpecialOutput(
                content: String,
                existingParts: List<ChatMessagePart>,
                statusCardsEnabled: Boolean,
                hideStatusBlocksInBubble: Boolean,
            ): ParsedAssistantSpecialOutput {
                return ParsedAssistantSpecialOutput(
                    content = content,
                    parts = existingParts,
                )
            }
        }
    }
}
