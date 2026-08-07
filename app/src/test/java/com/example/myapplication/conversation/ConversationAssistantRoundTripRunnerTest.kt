package com.example.myapplication.conversation

import com.example.myapplication.data.repository.ConversationRepository
import com.example.myapplication.data.repository.ImageGenerationResult
import com.example.myapplication.data.repository.ParsedAssistantSpecialOutput
import com.example.myapplication.data.repository.TransferUpdateDirective
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
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.testutil.FakeConversationStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun execute_failedFallbackRedactsThrowableMessage() = runTest {
        val conversationId = "conv-1"
        val userMessage = ChatMessage(
            id = "user-1",
            conversationId = conversationId,
            role = MessageRole.USER,
            content = "继续",
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
                buildFinalMessages = { failedAssistant -> listOf(userMessage, failedAssistant) },
                systemPrompt = "系统提示",
                streamReply = { _, _ ->
                    error("""HTTP 401 Authorization: Bearer sk-secret C:\Users\me\secret.json""")
                },
                currentPayload = {
                    StreamedAssistantPayload(
                        content = "",
                        reasoning = "",
                        reasoningSteps = emptyList(),
                        parts = emptyList(),
                        citations = emptyList(),
                    )
                },
                onCompleted = { _, _, loading -> loading },
                onCancelled = { _, _ -> null },
                onFailed = { _, _, loading ->
                    AssistantRoundTripOutcome(
                        messages = listOf(
                            loading.copy(
                                content = "发送失败，请检查网络或模型配置后重试",
                                status = MessageStatus.ERROR,
                            ),
                        ),
                        errorMessage = null,
                    )
                },
            ),
        )

        val failed = result as AssistantRoundTripResult.Failed
        assertEquals("发送失败，请检查网络或模型配置后重试", failed.errorMessage)
        assertFalse(failed.errorMessage.contains("sk-secret"))
        assertFalse(failed.errorMessage.contains("Users"))
    }

    @Test
    fun execute_postCommitTransferFailureKeepsCompletedMessage() = runTest {
        val conversationId = "conv-post-commit"
        val userMessage = ChatMessage(
            id = "user-post-commit",
            conversationId = conversationId,
            role = MessageRole.USER,
            content = "收下转账",
            createdAt = 1L,
        )
        val loadingMessage = ChatMessage(
            id = "assistant-post-commit",
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
        val failingStore = object : com.example.myapplication.data.local.ConversationStore by store {
            override suspend fun updateConversationMessages(
                conversation: Conversation,
                conversationId: String,
                transform: (List<ChatMessage>) -> List<ChatMessage>,
            ): List<ChatMessage> {
                error("转账状态更新失败")
            }
        }
        var postCommitFailure: Throwable? = null
        val runner = ConversationAssistantRoundTripRunner(
            conversationRepository = ConversationRepository(failingStore),
            aiGateway = completedGateway(
                ParsedAssistantSpecialOutput(
                    content = "已完成正文",
                    parts = emptyList(),
                    transferUpdates = listOf(
                        TransferUpdateDirective(
                            refId = "transfer-1",
                            status = TransferStatus.RECEIVED,
                        ),
                    ),
                ),
            ),
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
                        content = "已完成正文",
                        reasoning = "",
                        reasoningSteps = emptyList(),
                        parts = emptyList(),
                        citations = emptyList(),
                    )
                },
                onCompleted = { payload, _, loading ->
                    loading.copy(
                        content = payload.content,
                        status = MessageStatus.COMPLETED,
                    )
                },
                onPostCommitFailure = { throwable ->
                    postCommitFailure = throwable
                },
                onCancelled = { _, _ -> null },
                onFailed = { _, _, _ -> error("完成提交后不应进入生成失败路径") },
            ),
        )

        assertTrue(result is AssistantRoundTripResult.Completed)
        assertTrue(postCommitFailure is IllegalStateException)
        val savedAssistant = store.listMessages(conversationId)
            .single { it.id == loadingMessage.id }
        assertEquals(MessageStatus.COMPLETED, savedAssistant.status)
        assertEquals("已完成正文", savedAssistant.content)
    }

    private fun completedGateway(
        parsedOutput: ParsedAssistantSpecialOutput = ParsedAssistantSpecialOutput(
            content = "",
            parts = emptyList(),
        ),
    ): AiGateway {
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
                return parsedOutput.copy(
                    content = parsedOutput.content.ifBlank { content },
                    parts = parsedOutput.parts.ifEmpty { existingParts },
                )
            }
        }
    }
}
