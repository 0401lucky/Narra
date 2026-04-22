package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.ai.tooling.GetConversationSummaryTool
import com.example.myapplication.data.repository.ai.tooling.ReadMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SaveMemoryTool
import com.example.myapplication.data.repository.ai.tooling.SearchWebTool
import com.example.myapplication.data.repository.ai.tooling.SearchWorldBookTool
import com.example.myapplication.data.repository.ai.tooling.ToolAvailabilityResolver
import com.example.myapplication.data.repository.ai.tooling.ToolRegistry
import com.example.myapplication.data.repository.context.EmptyConversationSummaryRepository
import com.example.myapplication.data.repository.context.EmptyMemoryRepository
import com.example.myapplication.data.repository.context.EmptyWorldBookRepository
import com.example.myapplication.data.repository.search.SearchRepository
import com.example.myapplication.data.repository.search.SearchResult
import com.example.myapplication.data.repository.search.SearchResultItem
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.GatewayToolingOptions
import com.example.myapplication.model.ImageGenerationRequest
import com.example.myapplication.model.ImageGenerationResponse
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.SearchSettings
import com.example.myapplication.model.SearchSourceConfig
import com.example.myapplication.model.SearchSourceIds
import com.example.myapplication.model.SearchSourceType
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.specialMetadataValue
import com.example.myapplication.model.fileMessagePart
import com.example.myapplication.model.imageMessagePart
import com.example.myapplication.model.textMessagePart
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.testutil.FakeSettingsStore
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

class AiGatewayTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun sendMessage_postsFilteredConversationAndReturnsAssistantContent() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "你好，我可以帮你。"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = gateway.sendMessage(
            listOf(
                ChatMessage(id = "1", role = MessageRole.USER, content = "你好"),
                ChatMessage(id = "2", role = MessageRole.ASSISTANT, content = "", status = MessageStatus.LOADING),
                ChatMessage(id = "3", role = MessageRole.ASSISTANT, content = "上一条回复"),
            ),
        )

        assertEquals("你好，我可以帮你。", reply.content)
        assertEquals("", reply.reasoningContent)
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        val requestBody = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        val messages = requestBody.getAsJsonArray("messages")
        assertEquals(3, messages.size())
        assertEquals("system", messages[0].asJsonObject["role"].asString)
        assertFalse(messages[0].asJsonObject["content"].asString.contains("仅限聊天内展示的“转账”特殊玩法"))
        assertEquals("user", messages[1].asJsonObject["role"].asString)
        assertEquals("assistant", messages[2].asJsonObject["role"].asString)
    }

    @Test
    fun sendMessage_supportsResponsesApiMode() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "reasoning",
                      "summary": [
                        {"type": "summary_text", "text": "先整理上下文"}
                      ]
                    },
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "这是 responses 回复"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-responses",
            name = "OpenAI Proxy",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gpt-4.1-mini",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        assertEquals("这是 responses 回复", reply.content)
        assertEquals("先整理上下文", reply.reasoningContent)
        val request = server.takeRequest()
        assertEquals("/v1/responses", request.path)
    }

    @Test
    fun sendMessage_supportsResponsesApiToolLoop() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_tool_1",
                  "output": [
                    {
                      "type": "function_call",
                      "call_id": "call_resp_1",
                      "name": "search_web",
                      "arguments": "{\"query\":\"今日黄金价格\"}"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_tool_2",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "这是带搜索结果的 responses 回复"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-responses-search",
            name = "OpenAI Proxy",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gpt-4.1-mini",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                searchSettings = configuredSearchSettings(),
            ),
            searchRepository = fakeSearchRepository(),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "帮我查一下")),
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertEquals("这是带搜索结果的 responses 回复", reply.content)
        assertEquals(1, reply.citations.size)
        val firstRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertTrue(firstRequest.getAsJsonArray("tools").size() > 0)
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("resp_tool_1", secondRequest["previous_response_id"].asString)
        assertEquals("function_call_output", secondRequest.getAsJsonArray("input")[0].asJsonObject["type"].asString)
    }

    @Test
    fun sendMessage_usesCustomChatCompletionsPathWhenConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "来自自定义路径"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-custom-path",
            name = "Proxy",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "deepseek-chat",
            chatCompletionsPath = "/custom/chat/completions",
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        assertEquals("来自自定义路径", reply.content)
        assertEquals("/v1/custom/chat/completions", server.takeRequest().path)
    }

    @Test
    fun sendMessage_parsesReasoningContentFromResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "最终答案",
                        "reasoning_content": "先分析，再回答"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        assertEquals("最终答案", reply.content)
        assertEquals("先分析，再回答", reply.reasoningContent)
    }

    @Test
    fun sendMessage_mergesCustomSystemPromptWithFormattingPrompt() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        gateway.sendMessage(
            messages = listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "帮我总结")),
            systemPrompt = "你是一名历史老师。",
        )

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val systemPrompt = requestBody
            .getAsJsonArray("messages")[0]
            .asJsonObject["content"]
            .asString
        assertTrue(systemPrompt.startsWith("你是一名历史老师。"))
        assertTrue(systemPrompt.contains("Markdown 排版回答"))
    }

    @Test
    fun sendMessage_splitsThinkTagsIntoReasoningContent() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "<think>先分析问题</think>最终答案"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "grok-4.2beta",
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        assertEquals("最终答案", reply.content)
        assertEquals("先分析问题", reply.reasoningContent)
    }

    @Test
    fun sendMessage_includesReasoningEffortWhenThinkingBudgetConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-o3",
            name = "OpenAI",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "o3-mini",
            thinkingBudget = 16_000,
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"reasoning_effort\":\"medium\""))
        assertTrue(requestBody.contains("\"model\":\"o3-mini\""))
    }

    @Test
    fun sendMessage_includesGeminiReasoningEffortWhenThinkingBudgetConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-gemini",
            name = "Gemini",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gemini-2.5-pro",
            thinkingBudget = 16_000,
            type = ProviderType.GOOGLE,
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"reasoning_effort\":\"medium\""))
        assertTrue(requestBody.contains("\"model\":\"gemini-2.5-pro\""))
        assertFalse(requestBody.contains("\"thinking\""))
    }

    @Test
    fun sendMessage_mapsGemini3StandardBudgetToHigh() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-gemini3",
            name = "Gemini",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gemini-3-flash-preview",
            thinkingBudget = 16_000,
            type = ProviderType.GOOGLE,
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"reasoning_effort\":\"high\""))
    }

    @Test
    fun sendMessage_supportsAnthropicProtocol() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_1",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {
                      "type": "thinking",
                      "thinking": "先分析上下文"
                    },
                    {
                      "type": "text",
                      "text": "这是 Claude 回复"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-claude-chat",
            name = "Claude",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "anthropic-key",
            selectedModel = "claude-sonnet-4-20250514",
            apiProtocol = com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC,
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        assertEquals("这是 Claude 回复", reply.content)
        assertEquals("先分析上下文", reply.reasoningContent)
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("anthropic-key", request.getHeader("x-api-key"))
    }

    @Test
    fun sendMessage_supportsAnthropicToolLoop() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_tool_1",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {
                      "type": "tool_use",
                      "id": "toolu_1",
                      "name": "search_web",
                      "input": {
                        "query": "今日原油"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_tool_2",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {
                      "type": "text",
                      "text": "这是 Claude 搜索后的回复"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-claude-search",
            name = "Claude",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "anthropic-key",
            selectedModel = "claude-sonnet-4-20250514",
            apiProtocol = com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC,
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                searchSettings = configuredSearchSettings(),
            ),
            searchRepository = fakeSearchRepository(),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "查一下油价")),
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertEquals("这是 Claude 搜索后的回复", reply.content)
        assertEquals(1, reply.citations.size)
        val firstRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertTrue(firstRequest.getAsJsonArray("tools").size() > 0)
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val toolResult = secondRequest.getAsJsonArray("messages")
            .last().asJsonObject
            .getAsJsonArray("content")[0].asJsonObject
        assertEquals("tool_result", toolResult["type"].asString)
    }

    @Test
    fun sendMessage_includesAnthropicThinkingBudgetWhenConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_1",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {
                      "type": "text",
                      "text": "收到"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-claude",
            name = "Claude",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "claude-sonnet-4-20250514",
            thinkingBudget = 16_000,
            type = ProviderType.ANTHROPIC,
            apiProtocol = com.example.myapplication.model.ProviderApiProtocol.ANTHROPIC,
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":16000}"))
        assertFalse(requestBody.contains("\"reasoning_effort\""))
    }

    @Test
    fun sendMessage_withImageAttachmentBuildsContentParts() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "看到了图片"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            imagePayloadResolver = { "data:image/png;base64,ZmFrZQ==" },
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "请描述这张图",
                    attachments = listOf(
                        MessageAttachment(
                            type = AttachmentType.IMAGE,
                            uri = "content://picked/image",
                            mimeType = "image/png",
                            fileName = "cat.png",
                        ),
                    ),
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"text\":\"请描述这张图\""))
        assertTrue(requestBody.contains("\"type\":\"image_url\""))
        assertTrue(requestBody.contains("data:image/png;base64"))
    }

    @Test
    fun sendMessage_withImagePartBuildsContentParts() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "看到了图片"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            imagePayloadResolver = { "data:image/png;base64,ZmFrZQ==" },
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "图片已发送",
                    parts = listOf(
                        textMessagePart("请描述这张图"),
                        imageMessagePart(
                            uri = "content://picked/image",
                            mimeType = "image/png",
                            fileName = "cat.png",
                        ),
                    ),
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"text\":\"请描述这张图\""))
        assertTrue(requestBody.contains("\"type\":\"image_url\""))
        assertTrue(requestBody.contains("data:image/png;base64"))
    }

    @Test
    fun sendMessage_withFilePartBuildsContentParts() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "看到了文件"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            filePromptResolver = { "文件内容：第一行\\n第二行" },
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "文件已附加",
                    parts = listOf(
                        textMessagePart("请结合文件总结"),
                        fileMessagePart(
                            uri = "content://picked/file",
                            mimeType = "text/plain",
                            fileName = "notes.txt",
                        ),
                    ),
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"text\":\"请结合文件总结\""))
        assertTrue(requestBody.contains("文件内容：第一行\\\\n第二行"))
    }

    @Test
    fun sendMessage_withTransferPartBuildsProtocolPrompt() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "转账",
                    parts = listOf(
                        transferMessagePart(
                            id = "transfer-1",
                            direction = TransferDirection.USER_TO_ASSISTANT,
                            status = TransferStatus.PENDING,
                            counterparty = "陆宴清",
                            amount = "88.00",
                            note = "买奶茶",
                        ),
                    ),
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("仅限聊天内展示的“特殊玩法卡片”协议"))
        assertTrue(requestBody.contains("transfer-1"))
        assertTrue(requestBody.contains("user_to_assistant"))
        assertTrue(requestBody.contains("88.00"))
        assertTrue(requestBody.contains("陆宴清"))
        assertTrue(requestBody.contains("买奶茶"))
    }

    @Test
    fun sendMessage_withPunishPartBuildsProtocolPrompt() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "……知道了。"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "惩罚",
                    parts = listOf(
                        com.example.myapplication.model.punishMessagePart(
                            id = "punish-1",
                            method = "戒尺",
                            count = "三下",
                            intensity = com.example.myapplication.model.PunishIntensity.MEDIUM,
                            reason = "撒谎",
                            note = "边抽边认错",
                        ),
                    ),
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("punish-1"))
        assertTrue(requestBody.contains("戒尺"))
        assertTrue(requestBody.contains("三下"))
        assertTrue(requestBody.contains("medium"))
        assertTrue(requestBody.contains("不要主动生成或回发惩罚卡"))
    }

    @Test
    fun sendMessage_injectsTransferPromptWhenPendingTransferExistsInHistory() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "history-transfer",
                    role = MessageRole.USER,
                    content = "先给你转账",
                    parts = listOf(
                        transferMessagePart(
                            id = "transfer-pending",
                            direction = TransferDirection.USER_TO_ASSISTANT,
                            status = TransferStatus.PENDING,
                            counterparty = "陆宴清",
                            amount = "66.00",
                            note = "先垫付",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "follow-up",
                    role = MessageRole.USER,
                    content = "你先确认一下",
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("仅限聊天内展示的“特殊玩法卡片”协议"))
        assertTrue(requestBody.contains("transfer-pending"))
    }

    @Test
    fun sendMessage_doesNotInjectTransferPromptForClosedTransferHistory() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "收到"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        gateway.sendMessage(
            listOf(
                ChatMessage(
                    id = "history-transfer",
                    role = MessageRole.USER,
                    content = "转账已完成",
                    parts = listOf(
                        transferMessagePart(
                            id = "transfer-received",
                            direction = TransferDirection.USER_TO_ASSISTANT,
                            status = TransferStatus.RECEIVED,
                            counterparty = "陆宴清",
                            amount = "66.00",
                            note = "已确认",
                        ),
                    ),
                ),
                ChatMessage(
                    id = "follow-up",
                    role = MessageRole.USER,
                    content = "我们继续聊别的",
                ),
            ),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertFalse(requestBody.contains("仅限聊天内展示的“转账”特殊玩法"))
        assertTrue(requestBody.contains("transfer-received"))
    }

    @Test
    fun sendMessage_extractsStructuredImagePartsIntoParts() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": [
                          {
                            "type": "text",
                            "text": "这是生成结果"
                          },
                          {
                            "type": "image_url",
                            "image_url": {
                              "url": "https://cdn.example.com/generated/no-extension-signed"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "发张图")),
        )

        assertEquals("这是生成结果", reply.content)
        assertEquals(2, reply.parts.size)
        assertEquals(ChatMessagePartType.TEXT, reply.parts[0].type)
        assertEquals("这是生成结果", reply.parts[0].text)
        assertEquals(ChatMessagePartType.IMAGE, reply.parts[1].type)
        assertEquals("https://cdn.example.com/generated/no-extension-signed", reply.parts[1].uri)
    }

    @Test
    fun sendMessage_extractsTopLevelImagesIntoParts() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "这是生成结果",
                        "images": [
                          {
                            "type": "image_url",
                            "image_url": {
                              "url": "https://cdn.example.com/generated/from-images-field"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "发张图")),
        )

        assertEquals("这是生成结果", reply.content)
        assertEquals(2, reply.parts.size)
        assertEquals(ChatMessagePartType.TEXT, reply.parts[0].type)
        assertEquals(ChatMessagePartType.IMAGE, reply.parts[1].type)
        assertEquals("https://cdn.example.com/generated/from-images-field", reply.parts[1].uri)
    }

    @Test
    fun sendMessage_mapsWrappedUnknownHostToReadableChineseMessage() {
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = "https://www.lucky04.dpdns.org/v1/",
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            apiServiceProvider = { _, _ ->
                object : com.example.myapplication.testutil.TestOpenAiCompatibleApi() {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        throw IllegalStateException(
                            "调用失败",
                            UnknownHostException("www.lucky04.dpdns.org"),
                        )
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
                    }

                    override suspend fun generateImage(request: ImageGenerationRequest): Response<ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                gateway.sendMessage(
                    listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
                )
            }
        }

        assertEquals(
            "无法解析服务地址，请检查设备网络、DNS 设置，并确认 Base URL 是否可访问",
            error.message,
        )
        assertEquals(IllegalStateException::class.java, error.cause?.javaClass)
        assertEquals(UnknownHostException::class.java, error.cause?.cause?.javaClass)
    }

    @Test
    fun sendMessage_rejectsMissingSavedConfig() {
        val gateway = createGateway(settings = AppSettings())

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                gateway.sendMessage(
                    listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
                )
            }
        }

        assertEquals("请先完成设置并选择模型", error.message)
    }

    @Test
    fun sendMessageStream_emitsDeltasFromSseResponse() = runBlocking {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"你好\"},\"index\":0}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"世界\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val deltas = gateway.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(
            listOf(
                ChatStreamEvent.ContentDelta("你好"),
                ChatStreamEvent.ContentDelta("世界"),
                ChatStreamEvent.Completed,
            ),
            deltas,
        )
    }

    @Test
    fun sendMessageStream_supportsResponsesApiMode() = runBlocking {
        val sseBody = buildString {
            append("data: {\"type\":\"response.reasoning_text.delta\",\"delta\":\"先分析\"}\n\n")
            append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"最终答案\"}\n\n")
            append("data: {\"type\":\"response.completed\"}\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        val provider = ProviderSettings(
            id = "provider-responses-stream",
            name = "OpenAI Proxy",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "stream-key",
            selectedModel = "gpt-4.1-mini",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val deltas = gateway.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(5, deltas.size)
        assertTrue(deltas[0] is ChatStreamEvent.ReasoningStepStarted)
        assertEquals(
            "先分析",
            (deltas[1] as ChatStreamEvent.ReasoningStepDelta).value,
        )
        assertTrue(deltas[2] is ChatStreamEvent.ReasoningStepCompleted)
        assertEquals(ChatStreamEvent.ContentDelta("最终答案"), deltas[3])
        assertEquals(ChatStreamEvent.Completed, deltas[4])
        assertEquals("/v1/responses", server.takeRequest().path)
    }

    @Test
    fun sendMessage_chatCompletionsSearchToolReturnsCitations() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "search_web",
                              "arguments": "{\"query\":\"今日美股行情\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "这是搜索后的最终回答"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-search-stream",
            name = "OpenAI",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "stream-key",
            selectedModel = "gpt-4.1-mini",
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
                searchSettings = configuredSearchSettings(),
            ),
            searchRepository = fakeSearchRepository(),
        )

        val reply = gateway.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "查一下今天的美股")),
            toolingOptions = GatewayToolingOptions.searchOnly(true),
        )

        assertEquals("这是搜索后的最终回答", reply.content)
        assertEquals(1, reply.citations.size)
        val firstRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertTrue(firstRequest.getAsJsonArray("tools").size() > 0)
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val messages = secondRequest.getAsJsonArray("messages")
        assertEquals("tool", messages[messages.size() - 1].asJsonObject["role"].asString)
    }

    @Test
    fun sendMessageStream_splitsThinkTagsIntoReasoningAndContentDeltas() = runBlocking {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"<think>先分析问题</think>最终\"},\"index\":0}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"答案\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "grok-4.2beta",
            ),
        )

        val deltas = gateway.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(5, deltas.size)
        assertTrue(deltas[0] is ChatStreamEvent.ReasoningStepStarted)
        assertEquals(
            "先分析问题",
            (deltas[1] as ChatStreamEvent.ReasoningStepDelta).value,
        )
        assertTrue(deltas[2] is ChatStreamEvent.ReasoningStepCompleted)
        assertEquals(ChatStreamEvent.ContentDelta("最终答案"), deltas[3])
        assertEquals(ChatStreamEvent.Completed, deltas[4])
    }

    @Test
    fun sendMessageStream_emitsReasoningContentAndImageDeltas() = runBlocking {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先看图\"},\"index\":0}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"这是结果\",\"images\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://cdn.example.com/test.png\"}}]},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val deltas = gateway.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(6, deltas.size)
        assertTrue(deltas[0] is ChatStreamEvent.ReasoningStepStarted)
        assertEquals(
            "先看图",
            (deltas[1] as ChatStreamEvent.ReasoningStepDelta).value,
        )
        assertTrue(deltas[2] is ChatStreamEvent.ReasoningStepCompleted)
        assertEquals(ChatStreamEvent.ContentDelta("这是结果"), deltas[3])
        assertEquals(
            ChatStreamEvent.ImageDelta(
                com.example.myapplication.model.imageMessagePart(
                    uri = "https://cdn.example.com/test.png",
                ),
            ),
            deltas[4],
        )
        assertEquals(ChatStreamEvent.Completed, deltas[5])
    }

    @Test
    fun sendMessageStream_throwsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))
        val gateway = createGateway(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                gateway.sendMessageStream(
                    listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
                ).toList()
            }
        }

        assertEquals("聊天请求失败：500", error.message)
    }

    @Test
    fun generateImage_returnsImageGenerationResult() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {
                      "b64_json": "abc123",
                      "url": "https://cdn.example.com/out.png",
                      "revised_prompt": "修订后的提示词"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-image",
            name = "Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "img-key",
            selectedModel = "image-model",
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val results = gateway.generateImage("一只猫")

        assertEquals(1, results.size)
        assertEquals("abc123", results.first().b64Data)
        assertEquals("https://cdn.example.com/out.png", results.first().url)
        assertEquals("修订后的提示词", results.first().revisedPrompt)
    }

    @Test
    fun editImage_postsImagesEditsRequestAndReturnsImageGenerationResult() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {
                      "b64_json": "edited123",
                      "url": "https://cdn.example.com/edited.png",
                      "revised_prompt": "修订后的改图提示词"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-image-edit",
            name = "Provider",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "img-key",
            selectedModel = "gpt-image-1",
        )
        val gateway = createGateway(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
            imagePayloadResolver = { attachment ->
                assertEquals("ref.png", attachment.fileName)
                "data:image/png;base64,ref-image"
            },
        )

        val results = gateway.editImage(
            prompt = "把这张图改成复古海报",
            images = listOf(
                MessageAttachment(
                    type = AttachmentType.IMAGE,
                    uri = "content://image/1",
                    mimeType = "image/png",
                    fileName = "ref.png",
                ),
            ),
        )

        assertEquals(1, results.size)
        assertEquals("edited123", results.first().b64Data)
        assertEquals("https://cdn.example.com/edited.png", results.first().url)
        assertEquals("修订后的改图提示词", results.first().revisedPrompt)

        val request = server.takeRequest()
        assertEquals("/v1/images/edits", request.path)
        val requestBody = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("gpt-image-1", requestBody["model"].asString)
        assertEquals("把这张图改成复古海报", requestBody["prompt"].asString)
        val images = requestBody.getAsJsonArray("images")
        assertEquals(1, images.size())
        assertEquals("data:image/png;base64,ref-image", images[0].asJsonObject["image_url"].asString)
    }

    @Test
    fun parseAssistantSpecialOutput_extractsPlayAndUpdateTags() {
        val gateway = createGateway(settings = AppSettings())

        val parsed = gateway.parseAssistantSpecialOutput(
            content = "给你转账啦<play id=\"t1\" type=\"transfer\" direction=\"assistant_to_user\" amount=\"66.00\" counterparty=\"用户\" note=\"晚饭\" status=\"pending\" />顺手收一下<play-update ref=\"t0\" status=\"received\" />",
            existingParts = emptyList(),
        )

        assertEquals("给你转账啦\n\n顺手收一下", parsed.content)
        assertEquals(3, parsed.parts.size)
        assertEquals(ChatMessagePartType.TEXT, parsed.parts[0].type)
        assertEquals(ChatMessagePartType.SPECIAL, parsed.parts[1].type)
        assertEquals("t1", parsed.parts[1].specialId)
        assertEquals(1, parsed.transferUpdates.size)
        assertEquals("t0", parsed.transferUpdates.first().refId)
        assertEquals(TransferStatus.RECEIVED, parsed.transferUpdates.first().status)
    }

    @Test
    fun parseAssistantSpecialOutput_extractsInviteCard() {
        val gateway = createGateway(settings = AppSettings())

        val parsed = gateway.parseAssistantSpecialOutput(
            content = "今晚见我吧<play id=\"i1\" type=\"invite\" target=\"用户\" place=\"江边步道\" time=\"今晚九点\" note=\"别迟到\" />",
            existingParts = emptyList(),
        )

        assertEquals("今晚见我吧", parsed.content)
        assertEquals(ChatMessagePartType.SPECIAL, parsed.parts.last().type)
        assertEquals("invite", parsed.parts.last().specialType?.protocolValue)
        assertEquals("江边步道", parsed.parts.last().specialMetadataValue("place"))
    }

    @Test
    fun parseAssistantSpecialOutput_extractsPunishCard() {
        val gateway = createGateway(settings = AppSettings())

        val parsed = gateway.parseAssistantSpecialOutput(
            content = "你自己选的<play id=\"p1\" type=\"punish\" method=\"戒尺\" count=\"三下\" intensity=\"heavy\" reason=\"撒谎\" note=\"边抽边认错\" />",
            existingParts = emptyList(),
        )

        assertEquals("你自己选的", parsed.content)
        assertEquals(ChatMessagePartType.SPECIAL, parsed.parts.last().type)
        assertEquals("punish", parsed.parts.last().specialType?.protocolValue)
        assertEquals("戒尺", parsed.parts.last().specialMetadataValue("method"))
        assertEquals("三下", parsed.parts.last().specialMetadataValue("count"))
        assertEquals("heavy", parsed.parts.last().specialMetadataValue("intensity"))
    }

    @Test
    fun parseAssistantSpecialOutput_extractsTaskCardWithSingleQuotedAttributes() {
        val gateway = createGateway(settings = AppSettings())

        val parsed = gateway.parseAssistantSpecialOutput(
            content = "去吧。<play id='task_lunch_99' type='task' title='牛骨汤令' objective='帮园汇报午餐内容，不许胡嗦。' reward='晚上的惩罚豁免令' deadline='13:00' />",
            existingParts = emptyList(),
        )

        assertEquals("去吧。", parsed.content)
        assertEquals(ChatMessagePartType.SPECIAL, parsed.parts.last().type)
        assertEquals("task", parsed.parts.last().specialType?.protocolValue)
        assertEquals("task_lunch_99", parsed.parts.last().specialId)
        assertEquals("牛骨汤令", parsed.parts.last().specialMetadataValue("title"))
        assertEquals("帮园汇报午餐内容，不许胡嗦。", parsed.parts.last().specialMetadataValue("objective"))
        assertEquals("晚上的惩罚豁免令", parsed.parts.last().specialMetadataValue("reward"))
        assertEquals("13:00", parsed.parts.last().specialMetadataValue("deadline"))
    }

    private fun createGateway(
        settings: AppSettings,
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
        streamClientProvider: ((String, String) -> OkHttpClient)? = null,
        imagePayloadResolver: suspend (MessageAttachment) -> String = { error("不应解析图片") },
        filePromptResolver: suspend (MessageAttachment) -> String = { error("不应解析文件") },
        searchRepository: SearchRepository = object : SearchRepository {
            override suspend fun search(
                source: SearchSourceConfig,
                query: String,
                resultCount: Int,
            ) = error("不应执行搜索")
        },
    ): DefaultAiGateway {
        val settingsStore = FakeSettingsStore(settings)
        val apiServiceFactory = ApiServiceFactory()
        val toolRegistry = ToolRegistry(
            listOf(
                ReadMemoryTool(),
                GetConversationSummaryTool(),
                SearchWorldBookTool(),
                SaveMemoryTool(),
                SearchWebTool(),
            ),
        )
        val toolAvailabilityResolver = ToolAvailabilityResolver(
            searchRepository = searchRepository,
            memoryRepository = EmptyMemoryRepository,
            worldBookRepository = EmptyWorldBookRepository,
            conversationSummaryRepository = EmptyConversationSummaryRepository,
        )
        return DefaultAiGateway(
            settingsStore = settingsStore,
            apiServiceFactory = apiServiceFactory,
            apiServiceProvider = apiServiceProvider ?: { baseUrl, apiKey ->
                apiServiceFactory.create(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                )
            },
            streamClientProvider = streamClientProvider ?: { _, _ ->
                OkHttpClient.Builder().build()
            },
            imagePayloadResolver = imagePayloadResolver,
            filePromptResolver = filePromptResolver,
            searchRepository = searchRepository,
            memoryRepository = EmptyMemoryRepository,
            worldBookRepository = EmptyWorldBookRepository,
            conversationSummaryRepository = EmptyConversationSummaryRepository,
            toolAvailabilityResolver = toolAvailabilityResolver,
            toolRegistry = toolRegistry,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun configuredSearchSettings(): SearchSettings {
        return SearchSettings(
            sources = listOf(
                SearchSourceConfig(
                    id = SearchSourceIds.BRAVE,
                    type = SearchSourceType.BRAVE,
                    name = "Brave 搜索",
                    enabled = true,
                    apiKey = "search-key",
                ),
            ),
            selectedSourceId = SearchSourceIds.BRAVE,
            defaultResultCount = 3,
        )
    }

    private fun fakeSearchRepository(): SearchRepository {
        return object : SearchRepository {
            override suspend fun search(
                source: SearchSourceConfig,
                query: String,
                resultCount: Int,
            ): SearchResult {
                return SearchResult(
                    query = query,
                    answer = "这是搜索摘要结论",
                    items = listOf(
                        SearchResultItem(
                            id = "demo001",
                            title = "示例来源",
                            url = "https://example.com/search",
                            snippet = "这是搜索摘要",
                            sourceLabel = source.name,
                        ),
            ),
        )
    }
        }
    }
}
