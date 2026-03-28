package com.example.myapplication.data.repository.ai

import android.graphics.Rect
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.AppSettings
import com.example.myapplication.model.ChatChoiceDto
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.OpenAiTextApiMode
import com.example.myapplication.model.ProviderSettings
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ScreenTextBlock
import com.example.myapplication.model.ScreenTranslationRequest
import com.example.myapplication.model.TranslationSourceType
import com.example.myapplication.testutil.FakeSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AiTranslationServiceTest {
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
    fun translateText_supportsResponsesApiMode() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "resp_1",
                  "output": [
                    {
                      "type": "message",
                      "content": [
                        {"type": "output_text", "text": "Hello from responses"}
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-responses-translate",
            name = "Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gpt-4.1-mini",
            translationModel = "gpt-4.1-mini",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val translated = service.translateText("你好")

        assertEquals("Hello from responses", translated)
        assertEquals("/v1/responses", server.takeRequest().path)
    }

    @Test
    fun translateText_supportsAnthropicProtocol() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "id": "msg_1",
                  "model": "claude-sonnet-4-20250514",
                  "content": [
                    {
                      "type": "text",
                      "text": "Hello, Claude!"
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-claude-translate",
            name = "Claude Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "anthropic-key",
            selectedModel = "claude-sonnet-4-20250514",
            translationModel = "claude-sonnet-4-20250514",
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
        )
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val translated = service.translateText("你好")

        assertEquals("Hello, Claude!", translated)
        val request = server.takeRequest()
        assertEquals("/v1/messages", request.path)
        assertEquals("anthropic-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
    }

    @Test
    fun translateText_usesCustomChatCompletionsPathWhenConfigured() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Hello from custom path"
                      }
                    }
                  ]
                }
                """.trimIndent(),
            ),
        )
        val provider = ProviderSettings(
            id = "provider-translate-custom",
            name = "Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "deepseek-chat",
            translationModel = "gpt-4o-mini",
            chatCompletionsPath = "/custom/chat/completions",
        )
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val translated = service.translateText("你好")

        assertEquals("Hello from custom path", translated)
        assertEquals("/v1/custom/chat/completions", server.takeRequest().path)
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
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val translated = service.translateText("你好，世界")

        assertEquals("Hello, world!", translated)
        val requestBody = com.google.gson.JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals("gpt-4o-mini", requestBody["model"].asString)
    }

    @Test
    fun translateStructuredSegments_parsesStructuredResponse() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "1\tHello\n2\tWorld"
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
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val result = service.translateStructuredSegments(
            ScreenTranslationRequest(
                sourceType = TranslationSourceType.SCREEN_CAPTURE,
                appPackage = "pkg",
                appLabel = "应用",
                targetLanguage = "英语",
                segments = listOf(
                    ScreenTextBlock(id = "s1", text = "你好", bounds = Rect(0, 0, 10, 10), orderIndex = 0),
                    ScreenTextBlock(id = "s2", text = "世界", bounds = Rect(10, 0, 20, 10), orderIndex = 1),
                ),
            ),
        )

        assertEquals(listOf("Hello", "World"), result.translatedSegments.map { it.translatedText })
        assertEquals("Hello\nWorld", result.fullTranslation)
    }

    @Test
    fun translateTextStream_supportsResponsesApiMode() = runBlocking {
        val sseBody = buildString {
            append("data: {\"type\":\"response.output_text.delta\",\"delta\":\"Hello\"}\n\n")
            append("data: {\"type\":\"response.output_text.delta\",\"delta\":\" world\"}\n\n")
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
            name = "Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "gpt-4.1-mini",
            translationModel = "gpt-4.1-mini",
            openAiTextApiMode = OpenAiTextApiMode.RESPONSES,
        )
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val deltas = service.translateTextStream("你好").toList()

        assertEquals(listOf("Hello", "Hello world"), deltas)
        assertEquals("/v1/responses", server.takeRequest().path)
    }

    @Test
    fun translateTextStream_emitsDeltaSequence() = runBlocking {
        val sseBody = buildString {
            append("data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"},\"index\":0}]}\n\n")
            append("data: {\"choices\":[{\"delta\":{\"content\":\" world\"},\"index\":0}]}\n\n")
            append("data: [DONE]\n\n")
        }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody),
        )
        val provider = ProviderSettings(
            id = "provider-translate",
            name = "Translate",
            baseUrl = server.url("/v1/").toString(),
            apiKey = "saved-key",
            selectedModel = "deepseek-chat",
            translationModel = "gpt-4o-mini",
        )
        val service = createService(
            settings = AppSettings(
                providers = listOf(provider),
                selectedProviderId = provider.id,
            ),
        )

        val deltas = service.translateTextStream("你好").toList()

        assertEquals(listOf("Hello", "Hello world"), deltas)
    }

    private fun createService(settings: AppSettings): DefaultAiTranslationService {
        val settingsStore = FakeSettingsStore(settings)
        return DefaultAiTranslationService(
            settingsStore = settingsStore,
            apiServiceFactory = ApiServiceFactory(),
            ioDispatcher = Dispatchers.Unconfined,
        )
    }
}
