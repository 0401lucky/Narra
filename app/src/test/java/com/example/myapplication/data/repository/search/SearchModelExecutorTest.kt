package com.example.myapplication.data.repository.search

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.testutil.FakeSettingsStore
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SearchModelExecutorTest {
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
    fun search_usesResponsesWebSearchWithSearchModel() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "{\"query\":\"今天天气\",\"answer\":\"今天整体以晴为主。\",\"items\":[{\"id\":\"w001\",\"title\":\"天气网\",\"url\":\"https://weather.example.com\",\"text\":\"今日晴天\",\"sourceLabel\":\"LLM 搜索\"}]}",
                          "annotations": [
                            {
                              "type": "url_citation",
                              "url": "https://weather.example.com",
                              "title": "天气网"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-responses",
            name = "xAI",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "search-key",
            selectedModel = "grok-chat",
            searchModel = "grok-4.20-reasoning",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val executor = SearchModelExecutor(
            settingsStore = FakeSettingsStore(
                AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
            ),
            apiServiceFactory = ApiServiceFactory(),
        )

        val result = executor.search(
            source = com.example.myapplication.model.SearchSourceConfig(
                id = com.example.myapplication.model.SearchSourceIds.LLM_SEARCH,
                type = com.example.myapplication.model.SearchSourceType.LLM_SEARCH,
                name = "LLM 搜索",
                enabled = true,
                providerId = provider.id,
            ),
            query = "今天天气",
            resultCount = 3,
        )

        assertEquals("今天天气", result.query)
        assertEquals("今天整体以晴为主。", result.answer)
        assertEquals(1, result.items.size)
        assertEquals("w001", result.items.single().id)
        assertEquals("https://weather.example.com", result.items.single().url)
        val request = server.takeRequest()
        assertEquals("/v1/responses", request.path)
        val requestJson = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("grok-4.20-reasoning", requestJson["model"].asString)
        assertEquals("web_search", requestJson.getAsJsonArray("tools")[0].asJsonObject["type"].asString)
    }

    @Test
    fun search_usesAnthropicWebSearchWithSearchModel() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_1",
                  "content": [
                    {
                      "type": "web_search_tool_result",
                      "tool_use_id": "srvtoolu_1",
                      "content": [
                        {
                          "type": "web_search_result",
                          "url": "https://news.example.com",
                          "title": "新闻站"
                        }
                      ]
                    },
                    {
                      "type": "text",
                      "text": "{\"query\":\"今日新闻\",\"answer\":\"今天有一条重点新闻。\",\"items\":[{\"id\":\"n001\",\"title\":\"新闻站\",\"url\":\"https://news.example.com\",\"text\":\"今日头条摘要\",\"sourceLabel\":\"LLM 搜索\"}]}",
                      "citations": [
                        {
                          "type": "web_search_result_location",
                          "url": "https://news.example.com",
                          "title": "新闻站",
                          "cited_text": "今日头条摘要"
                        }
                      ]
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
            apiKey = "anthropic-key",
            selectedModel = "claude-chat",
            searchModel = "claude-opus-4-6",
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
        )
        val executor = SearchModelExecutor(
            settingsStore = FakeSettingsStore(
                AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
            ),
            apiServiceFactory = ApiServiceFactory(),
        )

        val result = executor.search(
            source = com.example.myapplication.model.SearchSourceConfig(
                id = com.example.myapplication.model.SearchSourceIds.LLM_SEARCH,
                type = com.example.myapplication.model.SearchSourceType.LLM_SEARCH,
                name = "LLM 搜索",
                enabled = true,
                providerId = provider.id,
            ),
            query = "今日新闻",
            resultCount = 3,
        )

        assertEquals("今天有一条重点新闻。", result.answer)
        assertEquals(1, result.items.size)
        assertEquals("n001", result.items.single().id)
        assertEquals("https://news.example.com", result.items.single().url)
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        val requestJson = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("claude-opus-4-6", requestJson["model"].asString)
        assertEquals("web_search_20250305", requestJson.getAsJsonArray("tools")[0].asJsonObject["type"].asString)
        assertTrue(request.getHeader("anthropic-version").orEmpty().isNotBlank())
    }

    @Test
    fun search_fallsBackToCitationsWhenStructuredItemsMissing() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {
                          "type": "output_text",
                          "text": "今天的结论已经整理好了。",
                          "annotations": [
                            {
                              "type": "url_citation",
                              "url": "https://weather.example.com/today",
                              "title": "天气网今日页"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-responses",
            name = "xAI",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "search-key",
            selectedModel = "grok-chat",
            searchModel = "grok-4.20-reasoning",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val executor = SearchModelExecutor(
            settingsStore = FakeSettingsStore(
                AppSettings(
                    providers = listOf(provider),
                    selectedProviderId = provider.id,
                ),
            ),
            apiServiceFactory = ApiServiceFactory(),
        )

        val result = executor.search(
            source = com.example.myapplication.model.SearchSourceConfig(
                id = com.example.myapplication.model.SearchSourceIds.LLM_SEARCH,
                type = com.example.myapplication.model.SearchSourceType.LLM_SEARCH,
                name = "LLM 搜索",
                enabled = true,
                providerId = provider.id,
            ),
            query = "今天天气",
            resultCount = 3,
        )

        assertEquals("今天的结论已经整理好了。", result.answer)
        assertEquals(1, result.items.size)
        assertTrue(result.items.single().id.isNotBlank())
        assertEquals("https://weather.example.com/today", result.items.single().url)
    }
}
