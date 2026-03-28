package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.repository.RoleplayMemoryCondenseMode
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.RoleplaySuggestionAxis
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AiPromptExtrasServiceTest {
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
    fun generateTitle_supportsResponsesApiMode() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "Responses 标题结果"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()
        val provider = ProviderSettings(
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )

        val title = service.generateTitle(
            firstUserMessage = "请帮我总结这段对话",
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelId = "gpt-4.1-mini",
            provider = provider,
        )

        assertEquals("Responses 标题结果", title)
        assertEquals("/v1/responses", server.takeRequest().path)
    }

    @Test
    fun generateTitle_trimsLongTitle() = runBlocking {
        val rawTitle = "这是一个超过二十个字符的标题文本用于验证截断"
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "$rawTitle"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()

        val title = service.generateTitle(
            firstUserMessage = "请帮我总结这段对话",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )

        assertEquals(rawTitle.take(20), title)
    }

    @Test
    fun generateChatSuggestions_usesCustomChatCompletionsPathWhenConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "继续追问动机\n补齐时间线\n确认钥匙下落"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()
        val provider = ProviderSettings(
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            chatCompletionsPath = "/custom/chat/completions",
        )

        val suggestions = service.generateChatSuggestions(
            conversationSummary = "用户正在追问钥匙下落。",
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey,
            modelId = "gpt-4o-mini",
            provider = provider,
        )

        assertEquals(listOf("继续追问动机", "补齐时间线", "确认钥匙下落"), suggestions)
        assertEquals("/v1/custom/chat/completions", server.takeRequest().path)
    }

    @Test
    fun generateChatSuggestions_trimsAndFiltersLines() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "- 继续追问动机\n· 补齐时间线\n  确认钥匙下落  \n这是一条超过五十个字的建议文本这是一条超过五十个字的建议文本这是一条超过五十个字的建议文本"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()

        val suggestions = service.generateChatSuggestions(
            conversationSummary = "用户正在追问钥匙下落。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )

        assertEquals(
            listOf("继续追问动机", "补齐时间线", "确认钥匙下落"),
            suggestions,
        )
    }

    @Test
    fun generateConversationSummary_returnsContent() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "双方已经确认线索来自钟楼，接下来要去找钥匙。"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()

        val summary = service.generateConversationSummary(
            conversationText = "用户：线索是不是在钟楼？\n助手：对，但还少一把钥匙。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "gpt-4o-mini",
        )

        assertEquals("双方已经确认线索来自钟楼，接下来要去找钥匙。", summary)
    }

    @Test
    fun generateRoleplayConversationSummary_buildsStructuredPrompt() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "【剧情进展】已经找到钟楼。\n【当前状态】正在搜钥匙。\n【关系变化】双方暂时合作。\n【未解问题】钥匙藏在哪里。\n【近期触发点】守夜人刚离开。"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()

        val summary = service.generateRoleplayConversationSummary(
            conversationText = "用户：钟楼就在前面。\n角色：钥匙可能还在守夜人手里。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertTrue(summary.contains("【剧情进展】"))
        val requestBody = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        val prompt = requestBody.getAsJsonArray("messages")[0].asJsonObject["content"].asString
        assertTrue(prompt.contains("【剧情进展】"))
        assertTrue(prompt.contains("【未解问题】"))
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
        val service = createService()

        val memories = service.generateMemoryEntries(
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
    fun generateRoleplayMemoryEntries_parsesStructuredJson() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\"persistent_memories\":[\"角色长期隐瞒钟楼钥匙线索\"],\"scene_state_memories\":[\"当前地点在钟楼外侧平台\",\"双方正在争执是否继续追守夜人\"]}"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val service = createService()

        val memories = service.generateRoleplayMemoryEntries(
            conversationExcerpt = "用户：你一直知道钥匙在哪。\n角色：我只是还不能说。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(
            listOf("角色长期隐瞒钟楼钥匙线索"),
            memories.persistentMemories,
        )
        assertEquals(
            listOf("当前地点在钟楼外侧平台", "双方正在争执是否继续追守夜人"),
            memories.sceneStateMemories,
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
        val service = createService()

        val result = service.condenseRoleplayMemories(
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
        val service = createService()

        val suggestions = service.generateRoleplaySuggestions(
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
            suggestions.map { it.label },
        )
        assertTrue(suggestions.all { it.id.isNotBlank() })
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
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
        val service = createService()

        val suggestions = service.generateRoleplaySuggestions(
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
        val service = createService()

        val suggestions = service.generateRoleplaySuggestions(
            conversationExcerpt = "用户：你有事瞒着我。\n角色：不是现在。",
            systemPrompt = "【场景设定】旧仓库对峙",
            playerStyleReference = "- 你最好现在就说。\n- 我不想再等。",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "test-key",
            modelId = "deepseek-chat",
        )

        assertEquals(3, suggestions.size)
        assertEquals(RoleplaySuggestionAxis.PLOT, suggestions[0].axis)
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
        val service = createService()

        val suggestions = service.generateRoleplaySuggestions(
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

    private fun createService(): DefaultAiPromptExtrasService {
        return DefaultAiPromptExtrasService(
            apiServiceFactory = ApiServiceFactory(),
        )
    }
}
