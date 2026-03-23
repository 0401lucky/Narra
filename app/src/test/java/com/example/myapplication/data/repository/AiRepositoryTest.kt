package com.example.myapplication.data.repository

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.AttachmentType
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ChatMessage
import com.example.myapplication.model.ChatMessagePartType
import com.example.myapplication.model.ChatStreamEvent
import com.example.myapplication.model.MessageAttachment
import com.example.myapplication.model.MessageRole
import com.example.myapplication.model.MessageStatus
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderType
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.example.myapplication.model.RoleplaySuggestionUiModel
import com.example.myapplication.model.TransferDirection
import com.example.myapplication.model.TransferStatus
import com.example.myapplication.model.transferMessagePart
import com.example.myapplication.testutil.FakeSettingsStore
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

class AiRepositoryTest {
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
    fun fetchModels_returnsModelIdsFromApi() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {"id": "gpt-4o-mini"},
                    {"id": "deepseek-chat"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(
            settings = AppSettings(),
        )

        val models = repository.fetchModels(
            baseUrl = server.url("/v1").toString(),
            apiKey = "test-key",
        )

        assertEquals(listOf("gpt-4o-mini", "deepseek-chat"), models)
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("Bearer test-key", request.getHeader("Authorization"))
    }

    @Test
    fun fetchModels_mapsUnknownHostToReadableChineseMessage() {
        val repository = createRepository(
            settings = AppSettings(),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        throw UnknownHostException("www.lucky04.dpdns.org")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        error("不应调用聊天接口")
                    }

                    override suspend fun generateImage(request: com.example.myapplication.model.ImageGenerationRequest): Response<com.example.myapplication.model.ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.fetchModels(
                    baseUrl = "https://www.lucky04.dpdns.org/v1/",
                    apiKey = "test-key",
                )
            }
        }

        assertEquals(
            "无法解析服务地址，请检查设备网络、DNS 设置，并确认 Base URL 是否可访问",
            error.message,
        )
        assertEquals(UnknownHostException::class.java, error.cause?.javaClass)
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = repository.sendMessage(
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
        assertEquals("POST", request.method)
        assertEquals("Bearer saved-key", request.getHeader("Authorization"))
        val requestBody = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("deepseek-chat", requestBody["model"].asString)
        assertEquals(false, requestBody["stream"].asBoolean)
        val messages = requestBody.getAsJsonArray("messages")
        assertEquals(3, messages.size())
        assertEquals("system", messages[0].asJsonObject["role"].asString)
        assertTrue(messages[0].asJsonObject["content"].asString.contains("Markdown 排版回答"))
        assertEquals("user", messages[1].asJsonObject["role"].asString)
        assertEquals("你好", messages[1].asJsonObject["content"].asString)
        assertEquals("assistant", messages[2].asJsonObject["role"].asString)
        assertEquals("上一条回复", messages[2].asJsonObject["content"].asString)
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "grok-4.2beta",
            ),
        )

        val reply = repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        repository.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"reasoning_effort\":\"medium\""))
        assertTrue(requestBody.contains("\"model\":\"gemini-2.5-pro\""))
        assertTrue(!requestBody.contains("\"thinking\""))
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        repository.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"reasoning_effort\":\"high\""))
    }

    @Test
    fun sendMessage_includesAnthropicThinkingBudgetWhenConfigured() = runBlocking {
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
            id = "provider-claude",
            name = "Claude",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "claude-sonnet-4-20250514",
            thinkingBudget = 16_000,
            type = ProviderType.ANTHROPIC,
        )
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                selectedModel = provider.selectedModel,
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        repository.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        )

        val requestBody = server.takeRequest().body.readUtf8()
        assertTrue(requestBody.contains("\"thinking\":{\"type\":\"enabled\",\"budget_tokens\":16000}"))
        assertTrue(!requestBody.contains("\"reasoning_effort\""))
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            imagePayloadResolver = { "data:image/png;base64,ZmFrZQ==" },
        )

        repository.sendMessage(
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
        assertTrue(requestBody.contains("\"model\":\"deepseek-chat\""))
        assertTrue(requestBody.contains("\"role\":\"user\""))
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            imagePayloadResolver = { "data:image/png;base64,ZmFrZQ==" },
        )

        repository.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "图片已发送",
                    parts = listOf(
                        com.example.myapplication.model.textMessagePart("请描述这张图"),
                        com.example.myapplication.model.imageMessagePart(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            filePromptResolver = { "文件内容：第一行\\n第二行" },
        )

        repository.sendMessage(
            listOf(
                ChatMessage(
                    id = "1",
                    role = MessageRole.USER,
                    content = "文件已附加",
                    parts = listOf(
                        com.example.myapplication.model.textMessagePart("请结合文件总结"),
                        com.example.myapplication.model.fileMessagePart(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        repository.sendMessage(
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
        assertTrue(requestBody.contains("transfer-1"))
        assertTrue(requestBody.contains("user_to_assistant"))
        assertTrue(requestBody.contains("88.00"))
        assertTrue(requestBody.contains("陆宴清"))
        assertTrue(requestBody.contains("买奶茶"))
    }

    @Test
    fun parseAssistantSpecialOutput_extractsTransferAndUpdateTags() = runBlocking {
        val repository = createRepository(settings = AppSettings())

        val parsed = repository.parseAssistantSpecialOutput(
            content = "给你转账啦<transfer id=\"t1\" direction=\"assistant_to_user\" amount=\"66.00\" counterparty=\"用户\" note=\"晚饭\" />顺手收一下<transfer-update ref=\"t0\" status=\"received\" />",
            existingParts = emptyList(),
        )

        assertEquals("给你转账啦\n\n顺手收一下", parsed.content)
        assertEquals(3, parsed.parts.size)
        assertEquals(ChatMessagePartType.TEXT, parsed.parts[0].type)
        assertEquals(ChatMessagePartType.SPECIAL, parsed.parts[1].type)
        assertEquals("t1", parsed.parts[1].specialId)
        assertEquals(ChatMessagePartType.TEXT, parsed.parts[2].type)
        assertEquals(1, parsed.transferUpdates.size)
        assertEquals("t0", parsed.transferUpdates.first().refId)
        assertEquals(TransferStatus.RECEIVED, parsed.transferUpdates.first().status)
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val reply = repository.sendMessage(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "发张图")),
        )

        assertEquals("这是生成结果", reply.content)
        assertEquals(2, reply.parts.size)
        assertEquals(ChatMessagePartType.TEXT, reply.parts[0].type)
        assertEquals(ChatMessagePartType.IMAGE, reply.parts[1].type)
        assertEquals("https://cdn.example.com/generated/from-images-field", reply.parts[1].uri)
    }

    @Test
    fun generateRoleplaySuggestions_parsesJsonArrayResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "[{\"axis\":\"plot\",\"label\":\"逼近真相\",\"text\":\"*我抬眼看向他* 你刚才那句话，到底是什么意思？\"},{\"axis\":\"info\",\"label\":\"追问细节\",\"text\":\"我先压住情绪，低声问：这里之前到底发生过什么？\"},{\"axis\":\"emotion\",\"label\":\"压住退路\",\"text\":\"*我没有后退* 既然你早就知道，就别再瞒着我。\"}]"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(
            settings = AppSettings(),
        )

        val suggestions = repository.generateRoleplaySuggestions(
            conversationExcerpt = "用户：你看起来不太对劲。\n角色：我没事。",
            systemPrompt = "【场景设定】雨夜对峙",
            playerStyleReference = "- 你最好把话说清楚。\n- 我不想再猜了。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(3, suggestions.size)
        assertEquals(
            listOf("逼近真相", "追问细节", "压住退路"),
            suggestions.map(RoleplaySuggestionUiModel::label),
        )
        assertTrue(suggestions.all { it.id.isNotBlank() })
        assertTrue(suggestions.first().text.contains("你刚才那句话"))
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
        assertEquals(RoleplaySuggestionAxis.INFO, suggestions[1].axis)
        assertEquals(RoleplaySuggestionAxis.EMOTION, suggestions[2].axis)

        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("deepseek-chat", requestBody["model"].asString)
        assertEquals(0.9f, requestBody["temperature"].asFloat)
        assertEquals(0.92f, requestBody["top_p"].asFloat)
        val messages = requestBody.getAsJsonArray("messages")
        assertEquals("system", messages[0].asJsonObject["role"].asString)
        assertTrue(messages[0].asJsonObject["content"].asString.contains("只输出 JSON 数组"))
        assertTrue(messages[1].asJsonObject["content"].asString.contains("【剧情设定与上下文】"))
        assertTrue(messages[1].asJsonObject["content"].asString.contains("【玩家口吻参考】"))
        assertTrue(messages[1].asJsonObject["content"].asString.contains("【最近剧情】"))
    }

    @Test
    fun generateRoleplaySuggestions_fallsBackFromCodeFenceAndPlainTextLines() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "```json\n试探推进：*我盯着他* 你现在还打算继续瞒我吗？\n信息探索：先告诉我，这里到底发生了什么。\n情绪拉扯：*我攥紧衣角* 你明知道我会来，对不对？\n```"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(
            settings = AppSettings(),
        )

        val suggestions = repository.generateRoleplaySuggestions(
            conversationExcerpt = "用户：你在骗我吗？\n角色：我只是没说全。",
            systemPrompt = "【场景设定】旧宅密谈",
            playerStyleReference = "- 你别糊弄我。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(3, suggestions.size)
        assertEquals("试探推进", suggestions[0].label)
        assertEquals("*我盯着他* 你现在还打算继续瞒我吗？", suggestions[0].text)
        assertEquals("信息探索", suggestions[1].label)
        assertEquals("情绪拉扯", suggestions[2].label)
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
    }

    @Test
    fun generateRoleplaySuggestions_retriesWhenSuggestionsAreTooSimilar() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "[{\"axis\":\"plot\",\"label\":\"试探推进\",\"text\":\"你现在最好解释清楚。\"},{\"axis\":\"info\",\"label\":\"继续追问\",\"text\":\"你现在最好解释清楚。\"},{\"axis\":\"emotion\",\"label\":\"压迫拉扯\",\"text\":\"你现在最好解释清楚。\"}]"
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
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "[{\"axis\":\"plot\",\"label\":\"先逼近一步\",\"text\":\"*我往前半步* 别绕了，你现在就把关键那句说完。\"},{\"axis\":\"info\",\"label\":\"补齐细节\",\"text\":\"先告诉我，雨停之前这里到底发生了什么。\"},{\"axis\":\"emotion\",\"label\":\"压住颤意\",\"text\":\"*我盯着他不放* 你明知道我会在意，为什么还要瞒着我？\"}]"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(settings = AppSettings())

        val suggestions = repository.generateRoleplaySuggestions(
            conversationExcerpt = "用户：你有事瞒着我。\n角色：不是现在。",
            systemPrompt = "【场景设定】旧仓库对峙",
            playerStyleReference = "- 你最好现在就说。\n- 我不想再等。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(3, suggestions.size)
        assertTrue(suggestions.map { it.text }.distinct().size == 3)
        val firstRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertTrue(secondRequest.getAsJsonArray("messages")[1].asJsonObject["content"].asString.contains("【上一批建议（不要沿用这些句式）】"))
        assertTrue(firstRequest.getAsJsonArray("messages")[1].asJsonObject["content"].asString.contains("【玩家口吻参考】"))
    }

    @Test
    fun generateRoleplaySuggestions_fallsBackWhenProviderRejectsSamplingParameters() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(400).setBody(
                """
                {"error":{"message":"unknown parameter: temperature"}}
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
                        "content": "[{\"axis\":\"plot\",\"label\":\"推进一步\",\"text\":\"你刚才那句没说完，继续。\"},{\"axis\":\"info\",\"label\":\"追问现场\",\"text\":\"这里之前到底出了什么事？\"},{\"axis\":\"emotion\",\"label\":\"逼近情绪\",\"text\":\"*我没有躲开* 你是不是从一开始就在骗我？\"}]"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(settings = AppSettings())

        val suggestions = repository.generateRoleplaySuggestions(
            conversationExcerpt = "用户：你还有多少事瞒着我？\n角色：别逼我。",
            systemPrompt = "【场景设定】雨夜桥下",
            playerStyleReference = "- 你别再躲了。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(3, suggestions.size)
        val firstRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val secondRequest = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertTrue(firstRequest.has("temperature"))
        assertTrue(firstRequest.has("top_p"))
        assertFalse(secondRequest.has("temperature"))
        assertFalse(secondRequest.has("top_p"))
    }

    @Test
    fun sendMessage_mapsWrappedUnknownHostToReadableChineseMessage() {
        val repository = createRepository(
            settings = AppSettings(
                baseUrl = "https://www.lucky04.dpdns.org/v1/",
                apiKey = "saved-key",
                selectedModel = "deepseek-chat",
            ),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        error("不应调用模型接口")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        throw IllegalStateException(
                            "调用失败",
                            UnknownHostException("www.lucky04.dpdns.org"),
                        )
                    }

                    override suspend fun generateImage(request: com.example.myapplication.model.ImageGenerationRequest): Response<com.example.myapplication.model.ImageGenerationResponse> {
                        error("不应调用生图接口")
                    }
                }
            },
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.sendMessage(
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
        val repository = createRepository(
            settings = AppSettings(),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repository.sendMessage(
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

        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val deltas = repository.sendMessageStream(
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
        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("POST", request.method)
    }

    @Test
    fun sendMessageStream_emitsReasoningAndContentDeltas() = runBlocking {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"先看图\"},\"index\":0}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\"这是答案\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val deltas = repository.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(
            listOf(
                ChatStreamEvent.ReasoningDelta("先看图"),
                ChatStreamEvent.ContentDelta("这是答案"),
                ChatStreamEvent.Completed,
            ),
            deltas,
        )
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

        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "grok-4.2beta",
            ),
        )

        val deltas = repository.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
        ).toList()

        assertEquals(
            listOf(
                ChatStreamEvent.ReasoningDelta("先分析问题"),
                ChatStreamEvent.ContentDelta("最终答案"),
                ChatStreamEvent.Completed,
            ),
            deltas,
        )
    }

    @Test
    fun sendMessageStream_emitsImageDeltasAsStructuredParts() = runBlocking {
        val sseBody = buildString {
            append(
                "data: {\"choices\":[{\"delta\":{\"content\":\"这是生成结果\",\"images\":[{\"type\":\"image_url\",\"image_url\":{\"url\":\"https://cdn.example.com/generated/from-stream\"}}]},\"index\":0}]}\n\n",
            )
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )

        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val deltas = repository.sendMessageStream(
            listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "发张图")),
        ).toList()

        assertEquals(
            listOf(
                ChatStreamEvent.ContentDelta("这是生成结果"),
                ChatStreamEvent.ImageDelta(
                    com.example.myapplication.model.imageMessagePart(
                        uri = "https://cdn.example.com/generated/from-stream",
                    ),
                ),
                ChatStreamEvent.Completed,
            ),
            deltas,
        )
    }

    @Test
    fun generateMemoryEntries_parsesJsonArrayResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "[\"用户喜欢短句回复\", \"用户正在调查白塔城失窃案\"]"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(settings = AppSettings())

        val memories = repository.generateMemoryEntries(
            conversationExcerpt = "用户：我喜欢短句。\n助手：好的。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )

        assertEquals(
            listOf("用户喜欢短句回复", "用户正在调查白塔城失窃案"),
            memories,
        )
    }

    @Test
    fun condenseRoleplayMemories_mergesMultipleItemsIntoCompactList() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "[\"角色已经承认自己知道密门位置。\",\"当前剧情焦点是追问钟楼密门与钥匙去向。\"]"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(settings = AppSettings())

        val result = repository.condenseRoleplayMemories(
            memoryItems = listOf(
                "角色知道密门位置。",
                "角色承认自己知道密门位置。",
                "当前剧情在追问钟楼密门。",
                "用户正在逼问钥匙去向。",
            ),
            mode = RoleplayMemoryCondenseMode.SCENE,
            maxItems = 2,
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "memory-model",
        )

        assertEquals(
            listOf("角色已经承认自己知道密门位置。", "当前剧情焦点是追问钟楼密门与钥匙去向。"),
            result,
        )
        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("memory-model", requestBody["model"].asString)
        assertTrue(requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString.contains("剧情状态记忆整理器"))
    }

    @Test
    fun sendMessageStream_throwsOnHttpError() {
        server.enqueue(MockResponse().setResponseCode(500))

        val repository = createRepository(
            settings = AppSettings(
                baseUrl = server.url("/v1/").toString(),
                apiKey = "stream-key",
                selectedModel = "deepseek-chat",
            ),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            runBlocking {
                repository.sendMessageStream(
                    listOf(ChatMessage(id = "1", role = MessageRole.USER, content = "你好")),
                ).toList()
            }
        }

        assertEquals("聊天请求失败：500", error.message)
    }

    @Test
    fun fetchModelInfos_returnsModelInfosWithAbilities() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {"id": "gpt-4o"},
                    {"id": "deepseek-coder"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = createRepository(
            settings = AppSettings(),
        )

        val infos = repository.fetchModelInfos(
            baseUrl = server.url("/v1").toString(),
            apiKey = "test-key",
        )

        assertEquals(2, infos.size)
        assertEquals("gpt-4o", infos[0].modelId)
        assertTrue(infos[0].abilities.contains(ModelAbility.VISION))
        assertTrue(infos[0].abilities.contains(ModelAbility.TOOL))
        assertEquals("deepseek-coder", infos[1].modelId)
        assertTrue(infos[1].abilities.contains(ModelAbility.TOOL))
    }

    @Test
    fun translateText_usesConfiguredTranslationModel() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello, world!"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-translate",
            name = "Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "deepseek-chat",
            translationModel = "gpt-4o-mini",
        )
        val repository = createRepository(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val translated = repository.translateText("你好，世界")

        assertEquals("Hello, world!", translated)
        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("gpt-4o-mini", requestBody["model"].asString)
    }

    private fun createRepository(
        settings: AppSettings,
        apiServiceProvider: ((String, String) -> OpenAiCompatibleApi)? = null,
        imagePayloadResolver: suspend (MessageAttachment) -> String = { error("不应解析图片") },
        filePromptResolver: suspend (MessageAttachment) -> String = { error("不应解析文件") },
    ): AiRepository {
        return AiRepository(
            settingsStore = FakeSettingsStore(settings),
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = apiServiceProvider ?: { baseUrl, apiKey ->
                ApiServiceFactory().create(
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                )
            },
            streamClientProvider = { _, _ ->
                OkHttpClient.Builder().build()
            },
            imagePayloadResolver = imagePayloadResolver,
            filePromptResolver = filePromptResolver,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
    }
}
