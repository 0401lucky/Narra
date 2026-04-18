package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ai.AnthropicProtocolSupport
import com.example.myapplication.data.repository.ai.GatewayAssistantOutputParser
import com.example.myapplication.data.repository.ai.GatewayToolSupport
import com.example.myapplication.data.repository.ai.GatewayNetworkSupport
import com.example.myapplication.data.repository.ai.PromptExtrasResponseSupport
import com.example.myapplication.data.repository.ai.ResponseApiSupport
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.AssistantReply
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.MessageCitation
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.PromptMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ResponseApiReasoningDto
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ThinkingConfigDto
import com.example.myapplication.model.ThinkingRequestConfig
import com.example.myapplication.model.legacyReasoningStepsFromContent
import com.example.myapplication.model.normalizeChatReasoningSteps
import com.example.myapplication.model.reasoningStepsToContent
import com.example.myapplication.system.json.AppJson
import com.google.gson.Gson

private const val MAX_TOOL_ROUNDS = 4

private data class ToolExecutionRecord(
    val invocation: ToolInvocation,
    val result: ToolExecutionResult,
)

private data class TranscriptToolState(
    val transcript: List<ChatMessageDto>,
    val citations: List<MessageCitation> = emptyList(),
    val toolRoundCount: Int = 0,
)

data class ToolLoopChatRequestSpec(
    val model: String,
    val messages: List<ChatMessageDto>,
    val baseUrl: String,
    val apiProtocol: ProviderApiProtocol,
    val stream: Boolean = false,
    val reasoningEffort: String? = null,
    val thinking: ThinkingConfigDto? = null,
    val promptMode: PromptMode = PromptMode.CHAT,
    val tools: List<com.example.myapplication.model.ChatToolDto> = emptyList(),
    val toolChoice: String? = null,
)

class ToolEngine(
    private val toolRegistry: ToolRegistry,
    private val apiServiceProvider: (String, String) -> OpenAiCompatibleApi,
    private val anthropicApiProvider: (String, String) -> AnthropicApi,
    private val openAiTextUrlBuilder: (String, ProviderSettings?) -> String,
    private val requestBuilder: (ToolLoopChatRequestSpec) -> ChatCompletionRequest,
    private val gson: Gson = AppJson.gson,
) {
    suspend fun runOpenAiChatCompletionToolLoop(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        activeProvider: ProviderSettings?,
        thinkingRequestConfig: ThinkingRequestConfig,
        enabledToolNames: Set<String>,
        toolContext: ToolContext,
        promptMode: PromptMode,
    ): ToolLoopOutcome {
        val enabledTools = toolRegistry.resolve(enabledToolNames)
        var state = TranscriptToolState(transcript = requestMessages)
        repeat(MAX_TOOL_ROUNDS) {
            val request = requestBuilder(
                ToolLoopChatRequestSpec(
                    model = selectedModel,
                    messages = state.transcript,
                    baseUrl = baseUrl,
                    apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                    reasoningEffort = thinkingRequestConfig.reasoningEffort,
                    thinking = thinkingRequestConfig.thinking,
                    promptMode = promptMode,
                    tools = GatewayToolSupport.toOpenAiTools(enabledTools),
                    toolChoice = "auto",
                ),
            )
            val response = apiServiceProvider(
                baseUrl,
                apiKey,
            ).createChatCompletionAt(
                openAiTextUrlBuilder(baseUrl, activeProvider?.copy(openAiTextApiMode = OpenAiTextApiMode.CHAT_COMPLETIONS)),
                request,
            )
            if (!response.isSuccessful) {
                throw GatewayNetworkSupport.retrofitFailure("聊天请求失败", response)
            }
            val assistantMessage = response.body()
                ?.choices
                ?.firstOrNull()
                ?.message
            val invocations = assistantMessage?.toolCalls
                .orEmpty()
                .mapNotNull { toolCall ->
                    GatewayToolSupport.parseToolCall(
                        function = toolCall.function,
                        id = toolCall.id,
                    )
                }
            if (invocations.isEmpty()) {
                return ToolLoopOutcome(
                    finalReply = assistantReplyFromOpenAiMessage(
                        assistantMessage = assistantMessage,
                        citations = state.citations,
                    ),
                    citations = state.citations,
                    toolRoundCount = state.toolRoundCount,
                    continuation = ToolContinuation.Transcript(state.transcript),
                )
            }

            val executed = executeInvocations(invocations, toolContext)
            val assistantToolMessage = ChatMessageDto(
                role = "assistant",
                content = assistantMessage?.content ?: "",
                toolCalls = assistantMessage?.toolCalls.orEmpty(),
            )
            val toolMessages = executed.map { record ->
                ChatMessageDto(
                    role = "tool",
                    content = record.result.payload,
                    name = record.invocation.name,
                    toolCallId = record.invocation.id,
                )
            }
            state = state.copy(
                transcript = state.transcript + assistantToolMessage + toolMessages,
                citations = mergeCitations(state.citations, executed),
                toolRoundCount = state.toolRoundCount + 1,
            )
        }
        throw IllegalStateException("工具调用轮次过多，请稍后重试")
    }

    suspend fun runAnthropicToolLoop(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        thinkingRequestConfig: ThinkingRequestConfig,
        enabledToolNames: Set<String>,
        toolContext: ToolContext,
        promptMode: PromptMode,
    ): ToolLoopOutcome {
        val enabledTools = toolRegistry.resolve(enabledToolNames)
        var state = TranscriptToolState(transcript = requestMessages)
        repeat(MAX_TOOL_ROUNDS) {
            val request = requestBuilder(
                ToolLoopChatRequestSpec(
                    model = selectedModel,
                    messages = state.transcript,
                    baseUrl = baseUrl,
                    apiProtocol = ProviderApiProtocol.ANTHROPIC,
                    thinking = thinkingRequestConfig.thinking,
                    promptMode = promptMode,
                    tools = GatewayToolSupport.toOpenAiTools(enabledTools),
                ),
            )
            val response = anthropicApiProvider(baseUrl, apiKey).createMessage(
                AnthropicProtocolSupport.buildMessageRequest(request),
            )
            if (!response.isSuccessful) {
                throw PromptExtrasResponseSupport.buildHttpFailure(
                    operation = "聊天请求失败",
                    code = response.code(),
                    errorDetail = response.errorBody()?.string().orEmpty(),
                    headers = response.headers(),
                )
            }
            val body = response.body() ?: throw IllegalStateException("响应体为空")
            val invocations = body.content.mapNotNull { item ->
                if (!item.type.equals("tool_use", ignoreCase = true)) {
                    return@mapNotNull null
                }
                GatewayToolSupport.parseToolCall(
                    name = item.name,
                    input = item.input,
                    id = item.id,
                )
            }
            if (invocations.isEmpty()) {
                return ToolLoopOutcome(
                    finalReply = ensureAssistantReplyHasContent(
                        AnthropicProtocolSupport.toAssistantReply(body).copy(
                            citations = state.citations,
                        ),
                    ),
                    citations = state.citations,
                    toolRoundCount = state.toolRoundCount,
                    continuation = ToolContinuation.Transcript(state.transcript),
                )
            }

            val assistantToolMessage = ChatMessageDto(
                role = "assistant",
                content = body.content.map { item ->
                    when {
                        item.type.equals("tool_use", ignoreCase = true) -> mapOf(
                            "type" to "tool_use",
                            "id" to item.id.orEmpty(),
                            "name" to item.name.orEmpty(),
                            "input" to item.input.orEmpty(),
                        )

                        item.type.equals("thinking", ignoreCase = true) -> mapOf(
                            "type" to "text",
                            "text" to item.thinking.orEmpty(),
                        )

                        else -> mapOf(
                            "type" to "text",
                            "text" to item.text.orEmpty(),
                        )
                    }
                },
            )
            val executed = executeInvocations(invocations, toolContext)
            val toolResultMessage = ChatMessageDto(
                role = "user",
                content = executed.map { record ->
                    buildMap<String, Any> {
                        put("type", "tool_result")
                        put("tool_use_id", record.invocation.id)
                        put("content", record.result.payload)
                        if (record.result.isError) {
                            put("is_error", true)
                        }
                    }
                },
            )
            state = state.copy(
                transcript = state.transcript + assistantToolMessage + toolResultMessage,
                citations = mergeCitations(state.citations, executed),
                toolRoundCount = state.toolRoundCount + 1,
            )
        }
        throw IllegalStateException("工具调用轮次过多，请稍后重试")
    }

    suspend fun runResponsesToolLoop(
        baseUrl: String,
        apiKey: String,
        selectedModel: String,
        requestMessages: List<ChatMessageDto>,
        activeProvider: ProviderSettings?,
        thinkingRequestConfig: ThinkingRequestConfig,
        enabledToolNames: Set<String>,
        toolContext: ToolContext,
        promptMode: PromptMode,
    ): ToolLoopOutcome {
        val enabledTools = toolRegistry.resolve(enabledToolNames)
        val requestTemplate = requestBuilder(
            ToolLoopChatRequestSpec(
                model = selectedModel,
                messages = requestMessages,
                baseUrl = baseUrl,
                apiProtocol = ProviderApiProtocol.OPENAI_COMPATIBLE,
                reasoningEffort = thinkingRequestConfig.reasoningEffort,
                thinking = thinkingRequestConfig.thinking,
                promptMode = promptMode,
                tools = GatewayToolSupport.toOpenAiTools(enabledTools),
            ),
        )
        var citations = emptyList<MessageCitation>()
        var previousResponseId: String? = null
        var nextInput: List<Any> = ResponseApiSupport.buildRequest(requestTemplate).input
        var toolRoundCount = 0
        repeat(MAX_TOOL_ROUNDS) {
            val request = ResponseApiRequest(
                model = selectedModel,
                input = nextInput,
                stream = false,
                temperature = requestTemplate.temperature,
                topP = requestTemplate.topP,
                reasoning = thinkingRequestConfig.reasoningEffort?.let { effort ->
                    ResponseApiReasoningDto(
                        effort = effort,
                        summary = "auto",
                    )
                },
                tools = GatewayToolSupport.toResponseApiTools(enabledTools),
                previousResponseId = previousResponseId,
            )
            val response = apiServiceProvider(
                baseUrl,
                apiKey,
            ).createResponseAt(
                openAiTextUrlBuilder(baseUrl, activeProvider?.copy(openAiTextApiMode = OpenAiTextApiMode.RESPONSES)),
                request,
            )
            if (!response.isSuccessful) {
                throw PromptExtrasResponseSupport.buildHttpFailure(
                    operation = "聊天请求失败",
                    code = response.code(),
                    errorDetail = response.errorBody()?.string().orEmpty(),
                    headers = response.headers(),
                )
            }
            val body = response.body() ?: throw IllegalStateException("响应体为空")
            val invocations = body.output.mapNotNull { output ->
                if (!output.type.equals("function_call", ignoreCase = true)) {
                    return@mapNotNull null
                }
                GatewayToolSupport.parseToolCall(
                    name = output.name,
                    arguments = output.arguments,
                    id = output.callId,
                )
            }
            if (invocations.isEmpty()) {
                val parsed = ResponseApiSupport.parseResponse(body)
                return ToolLoopOutcome(
                    finalReply = ensureAssistantReplyHasContent(
                        AssistantReply(
                            content = parsed.content,
                            reasoningContent = parsed.reasoning,
                            citations = citations,
                        ),
                    ),
                    citations = citations,
                    toolRoundCount = toolRoundCount,
                    continuation = ToolContinuation.Responses(
                        input = nextInput,
                        previousResponseId = previousResponseId,
                        temperature = requestTemplate.temperature,
                        topP = requestTemplate.topP,
                    ),
                )
            }

            val executed = executeInvocations(invocations, toolContext)
            citations = mergeCitations(citations, executed)
            previousResponseId = body.id
            nextInput = executed.map { record ->
                mapOf(
                    "type" to "function_call_output",
                    "call_id" to record.invocation.id,
                    "output" to record.result.payload,
                )
            }
            toolRoundCount += 1
        }
        throw IllegalStateException("工具调用轮次过多，请稍后重试")
    }

    private suspend fun executeInvocations(
        invocations: List<ToolInvocation>,
        toolContext: ToolContext,
    ): List<ToolExecutionRecord> {
        return invocations.map { invocation ->
            val tool = toolRegistry.find(invocation.name)
            val result = if (tool == null) {
                ToolExecutionResult(
                    payload = gson.toJson(
                        mapOf(
                            "error" to "工具不存在：${invocation.name}",
                        ),
                    ),
                    isError = true,
                )
            } else {
                tool.execute(invocation, toolContext)
            }
            ToolExecutionRecord(
                invocation = invocation,
                result = result,
            )
        }
    }

    private fun mergeCitations(
        current: List<MessageCitation>,
        executed: List<ToolExecutionRecord>,
    ): List<MessageCitation> {
        return (current + executed.flatMap { it.result.citations }).distinctBy(MessageCitation::url)
    }

    private fun assistantReplyFromOpenAiMessage(
        assistantMessage: AssistantMessageDto?,
        citations: List<MessageCitation> = emptyList(),
    ): AssistantReply {
        val extractedOutput = GatewayAssistantOutputParser.extractAssistantOutput(assistantMessage)
        return ensureAssistantReplyHasContent(
            AssistantReply(
                content = extractedOutput.content,
                reasoningContent = extractedOutput.reasoning,
                parts = extractedOutput.parts,
                citations = citations,
            ),
        )
    }

    private fun ensureAssistantReplyHasContent(
        reply: AssistantReply,
    ): AssistantReply {
        val normalizedReply = reply.copy(
            reasoningSteps = normalizeChatReasoningSteps(
                reply.reasoningSteps.ifEmpty {
                    legacyReasoningStepsFromContent(
                        reasoningContent = reply.reasoningContent,
                        createdAt = System.currentTimeMillis(),
                        finishedAt = System.currentTimeMillis(),
                        idPrefix = "tool-reply",
                    )
                },
            ),
        ).let { normalized ->
            normalized.copy(
                reasoningContent = normalized.reasoningContent.ifBlank {
                    reasoningStepsToContent(normalized.reasoningSteps)
                },
            )
        }
        if (normalizedReply.content.isBlank() && normalizedReply.parts.isEmpty()) {
            throw IllegalStateException("模型未返回有效内容")
        }
        return normalizedReply
    }
}
