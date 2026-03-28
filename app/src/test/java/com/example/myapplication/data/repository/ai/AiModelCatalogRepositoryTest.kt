package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.data.remote.OpenAiCompatibleApi
import com.example.myapplication.model.ChatCompletionRequest
import com.example.myapplication.model.ChatCompletionResponse
import com.example.myapplication.model.ModelAbility
import com.example.myapplication.model.ModelsResponse
import com.example.myapplication.model.ProviderApiProtocol
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.net.UnknownHostException

class AiModelCatalogRepositoryTest {
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
        val repository = DefaultAiModelCatalogRepository(ApiServiceFactory())

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
        val repository = DefaultAiModelCatalogRepository(
            apiServiceFactory = ApiServiceFactory(),
            apiServiceProvider = { _, _ ->
                object : OpenAiCompatibleApi {
                    override suspend fun listModels(): Response<ModelsResponse> {
                        throw UnknownHostException("www.lucky04.dpdns.org")
                    }

                    override suspend fun createChatCompletion(request: ChatCompletionRequest): Response<ChatCompletionResponse> {
                        error("不应调用聊天接口")
                    }

                    override suspend fun createChatCompletionAt(url: String, request: ChatCompletionRequest): Response<ChatCompletionResponse> = createChatCompletion(request)

                    override suspend fun createResponseAt(url: String, request: com.example.myapplication.model.ResponseApiRequest): Response<com.example.myapplication.model.ResponseApiResponse> {
                        error("不应调用 responses 接口")
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
    fun fetchModels_supportsAnthropicProtocol() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {
                  "data": [
                    {"id": "claude-sonnet-4-20250514", "display_name": "Claude Sonnet 4"}
                  ]
                }
                """.trimIndent(),
            ),
        )
        val repository = DefaultAiModelCatalogRepository(ApiServiceFactory())

        val models = repository.fetchModels(
            baseUrl = server.url("/v1").toString(),
            apiKey = "anthropic-key",
            apiProtocol = ProviderApiProtocol.ANTHROPIC,
        )

        assertEquals(listOf("claude-sonnet-4-20250514"), models)
        val request = server.takeRequest()
        assertEquals("/v1/models", request.path)
        assertEquals("anthropic-key", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
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
        val repository = DefaultAiModelCatalogRepository(ApiServiceFactory())

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
}
