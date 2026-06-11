package com.example.myapplication.data.repository.ai.tooling

import com.example.myapplication.data.remote.AnthropicApi
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AnthropicMessageRequest
import com.example.myapplication.model.AnthropicMessageResponse
import com.example.myapplication.model.AnthropicModelsResponse
import com.example.myapplication.model.AssistantMessageDto
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessageDto
import com.example.myapplication.model.ChatToolCallDto
import com.example.myapplication.model.ChatToolFunctionDto
import com.example.myapplication.model.ImageEditRequest
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ResponseApiRequest
import com.example.myapplication.model.ResponseApiResponse
import com.example.myapplication.model.ThinkingRequestConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ToolEngineTest {
    @Test
    fun runOpenAiChatCompletionToolLoop_returnsRecoverableReplyWhenToolRoundsExceeded() = runBlocking {
        val responses = ArrayDeque<Response<ChatCompletionResponse>>().apply {
            repeat(4) { index ->
                add(
                    Response.success(
                        ChatCompletionResponse(
                            choices = listOf(
                                ChatChoiceDto(
                                    message = AssistantMessageDto(
                                        role = "assistant",
                                        content = "",
                                        toolCalls = listOf(
                                            ChatToolCallDto(
                                                id = "call-$index",
                                                function = ChatToolFunctionDto(
                                                    name = EchoTool.NAME,
                                                    arguments = "{}",
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            }
        }
        val api = fakeOpenAiApi { responses.removeFirst() }
        val engine = createEngine(
            tools = listOf(EchoTool()),
            api = api,
        )

        val outcome = engine.runOpenAiChatCompletionToolLoop(
            baseUrl = "https://example.com/v1/chat/completions",
            apiKey = "test-key",
            selectedModel = "model",
            requestMessages = listOf(ChatMessageDto(role = "user", content = "继续")),
            activeProvider = null,
            thinkingRequestConfig = ThinkingRequestConfig(),
            enabledToolNames = setOf(EchoTool.NAME),
            toolContext = ToolContext(searchRepository = fakeSearchRepository()),
            promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
            promptEnvelope = com.example.myapplication.model.PromptEnvelope(),
        )

        assertEquals(4, outcome.toolRoundCount)
        assertEquals("我先不继续调用工具，基于当前已知信息继续推进。", outcome.finalReply?.content)
        assertTrue(outcome.continuation is ToolContinuation.Transcript)
    }

    @Test
    fun runOpenAiChatCompletionToolLoop_returnsToolErrorPayloadWhenToolThrows() = runBlocking {
        val requests = mutableListOf<ChatCompletionRequest>()
        val responses = ArrayDeque<Response<ChatCompletionResponse>>().apply {
            add(
                Response.success(
                    ChatCompletionResponse(
                        choices = listOf(
                            ChatChoiceDto(
                                message = AssistantMessageDto(
                                    role = "assistant",
                                    content = "",
                                    toolCalls = listOf(
                                        ChatToolCallDto(
                                            id = "call-1",
                                            function = ChatToolFunctionDto(
                                                name = ThrowingTool.NAME,
                                                arguments = "{}",
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            add(
                Response.success(
                    ChatCompletionResponse(
                        choices = listOf(
                            ChatChoiceDto(
                                message = AssistantMessageDto(
                                    role = "assistant",
                                    content = "继续剧情",
                                ),
                            ),
                        ),
                    ),
                ),
            )
        }
        val api = fakeOpenAiApi { request ->
            requests += request
            responses.removeFirst()
        }
        val engine = createEngine(
            tools = listOf(ThrowingTool()),
            api = api,
        )

        val outcome = engine.runOpenAiChatCompletionToolLoop(
            baseUrl = "https://example.com/v1/chat/completions",
            apiKey = "test-key",
            selectedModel = "model",
            requestMessages = listOf(ChatMessageDto(role = "user", content = "继续")),
            activeProvider = null,
            thinkingRequestConfig = ThinkingRequestConfig(),
            enabledToolNames = setOf(ThrowingTool.NAME),
            toolContext = ToolContext(searchRepository = fakeSearchRepository()),
            promptMode = com.example.myapplication.model.PromptMode.ROLEPLAY,
            promptEnvelope = com.example.myapplication.model.PromptEnvelope(),
        )

        assertEquals("继续剧情", outcome.finalReply?.content)
        assertEquals(2, requests.size)
        val toolMessage = requests[1].messages.last()
        assertEquals("tool", toolMessage.role)
        val toolPayload = toolMessage.content.toString()
        assertTrue(toolPayload.contains("工具执行失败"))
        assertTrue(toolPayload.contains("[REDACTED]"))
        assertFalse(toolPayload.contains("tool-secret"))
    }

    private fun createEngine(
        tools: List<AppTool>,
        api: OpenAiCompatibleApi,
    ): ToolEngine {
        return ToolEngine(
            toolRegistry = ToolRegistry(tools),
            apiServiceProvider = { _, _ -> api },
            anthropicApiProvider = { _, _ -> fakeAnthropicApi() },
            openAiTextUrlBuilder = { baseUrl, _ -> baseUrl },
            requestBuilder = { spec ->
                ChatCompletionRequest(
                    model = spec.model,
                    messages = spec.messages,
                    tools = spec.tools,
                    toolChoice = spec.toolChoice,
                )
            },
        )
    }

    private fun fakeOpenAiApi(
        completionResponder: suspend (ChatCompletionRequest) -> Response<ChatCompletionResponse>,
    ): OpenAiCompatibleApi {
        return object : OpenAiCompatibleApi {
            override suspend fun listModels(): Response<ModelsResponse> = error("不应调用")

            override suspend fun createChatCompletion(
                request: ChatCompletionRequest,
            ): Response<ChatCompletionResponse> = error("不应调用")

            override suspend fun createChatCompletionAt(
                url: String,
                request: ChatCompletionRequest,
            ): Response<ChatCompletionResponse> {
                return completionResponder(request)
            }

            override suspend fun createResponseAt(
                url: String,
                request: ResponseApiRequest,
            ): Response<ResponseApiResponse> = error("不应调用")

            override suspend fun generateImage(
                request: ImageGenerationRequest,
            ): Response<ImageGenerationResponse> = error("不应调用")

            override suspend fun editImage(
                request: ImageEditRequest,
            ): Response<ImageGenerationResponse> = error("不应调用")
        }
    }

    private class EchoTool : AppTool {
        override val name: String = NAME
        override val description: String = "回显测试工具"
        override val inputSchema: Map<String, Any> = mapOf("type" to "object")

        override suspend fun execute(
            invocation: ToolInvocation,
            context: ToolContext,
        ): ToolExecutionResult {
            return ToolExecutionResult(payload = "{}")
        }

        companion object {
            const val NAME = "echo_tool"
        }
    }

    private class ThrowingTool : AppTool {
        override val name: String = NAME
        override val description: String = "测试工具"
        override val inputSchema: Map<String, Any> = mapOf("type" to "object")

        override suspend fun execute(
            invocation: ToolInvocation,
            context: ToolContext,
        ): ToolExecutionResult {
            error("Authorization: Bearer tool-secret")
        }

        companion object {
            const val NAME = "throwing_tool"
        }
    }

    private fun fakeAnthropicApi(): AnthropicApi {
        return object : AnthropicApi {
            override suspend fun listModels(): Response<AnthropicModelsResponse> = error("不应调用")

            override suspend fun createMessage(
                request: AnthropicMessageRequest,
            ): Response<AnthropicMessageResponse> = error("不应调用")
        }
    }

    private fun fakeSearchRepository(): com.example.myapplication.data.repository.search.SearchRepository {
        return object : com.example.myapplication.data.repository.search.SearchRepository {
            override suspend fun search(
                source: com.example.myapplication.model.SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): com.example.myapplication.data.repository.search.SearchResult {
                error("测试不应执行搜索")
            }
        }
    }
}
