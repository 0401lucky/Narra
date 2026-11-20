package com.example.myapplication.data.repository.embedding

import com.example.myapplication.context.MemoryEmbeddingChunker
import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.MemoryEmbeddingPurpose
import com.example.myapplication.model.ProviderApiProtocol
import com.example.myapplication.model.ProviderFunctionModelMode
import com.example.myapplication.model.ProviderSettings
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OpenAiMemoryEmbeddingClientTest {
    private lateinit var server: MockWebServer
    private lateinit var factory: ApiServiceFactory
    private lateinit var client: OpenAiMemoryEmbeddingClient

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        factory = ApiServiceFactory()
        client = OpenAiMemoryEmbeddingClient(factory)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun embed_usesDedicatedEndpointAuthModelAndReordersIndexes() = runTest {
        server.enqueue(response("""[{"index":1,"embedding":[0,2]},{"index":0,"embedding":[3,0]}]"""))
        val result = client.embed(
            settings(),
            listOf("  合成首段  ", "合成尾段"),
            MemoryEmbeddingPurpose.INDEX,
            null
        )
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v1/embeddings", request.path)
        assertEquals("Bearer synthetic-key", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals(setOf("model", "input", "encoding_format"), body.keySet())
        assertEquals("embedding-only", body["model"].asString)
        assertEquals("float", body["encoding_format"].asString)
        assertEquals(
            listOf("  合成首段  ", "合成尾段"),
            body["input"].asJsonArray.map { it.asString })
        assertEquals(2, result.dimensions)
        assertArrayEquals(floatArrayOf(1f, 0f), result.vectors[0], 0f)
        assertArrayEquals(floatArrayOf(0f, 1f), result.vectors[1], 0f)
    }

    @Test
    fun embed_missingReturnedItem_rejectsWholeBatch() = runTest {
        assertProtocolFailure(
            """[{"index":0,"embedding":[1,0]}]""",
            inputs = listOf("首段", "尾段")
        )
    }

    @Test
    fun embed_duplicateIndex_rejectsWholeBatch() = runTest {
        assertProtocolFailure(
            """[{"index":0,"embedding":[1,0]},{"index":0,"embedding":[0,1]}]""",
            inputs = listOf("首段", "尾段")
        )
    }

    @Test
    fun embed_missingFractionalStringOrOutOfRangeIndex_rejectsResponse() = runTest {
        listOf(
            "{}",
            "{\"index\":0.25}",
            "{\"index\":\"0\"}",
            "{\"index\":-1}",
            "{\"index\":1}",
            "{\"index\":1e400}"
        ).forEach { raw ->
            val item = JsonParser.parseString(raw).asJsonObject.apply {
                add(
                    "embedding",
                    JsonParser.parseString("[1,0]")
                )
            }
            assertProtocolFailure("[$item]")
        }
    }

    @Test
    fun embed_nonFiniteOverflowZeroAndNonNumericComponents_rejectResponse() = runTest {
        listOf(
            "[NaN,1]",
            "[Infinity,1]",
            "[1e400,1]",
            "[1e50,1]",
            "[0,0]",
            "[\"1\",0]",
            "[null,1]",
            "[]"
        )
            .forEach { vector -> assertProtocolFailure("""[{"index":0,"embedding":$vector}]""") }
    }

    @Test
    fun embed_inconsistentDimensions_rejectsBatchAndExistingSpaceMismatch() = runTest {
        assertProtocolFailure(
            """[{"index":0,"embedding":[1,0]},{"index":1,"embedding":[1,0,0]}]""",
            inputs = listOf("首段", "尾段")
        )
        assertProtocolFailure("""[{"index":0,"embedding":[1,0,0]}]""", dimensions = 2)
    }

    @Test
    fun embed_missingDataOrMalformedJson_failsSafely() = runTest {
        listOf("{}", "{\"data\":null}", "{\"data\":{}}", "null", "{\"data\":[").forEach { body ->
            server.enqueue(MockResponse().setBody(body))
            val failure = captureFailure {
                client.embed(
                    settings(),
                    listOf("合成输入"),
                    MemoryEmbeddingPurpose.QUERY,
                    null
                )
            }
            assertEquals(MemoryEmbeddingError.PROTOCOL, failure.code)
            assertNull(failure.cause)
        }
    }

    @Test
    fun embed_httpError_neverRetriesOrExposesResponseText() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
                .setBody("Authorization: Bearer synthetic-key; 私密原料哨兵; SELECT secret FROM users")
        )
        val failure = captureFailure {
            client.embed(
                settings(),
                listOf("私密原料哨兵"),
                MemoryEmbeddingPurpose.INDEX,
                null
            )
        }
        assertEquals(MemoryEmbeddingError.HTTP, failure.code)
        assertEquals(429, failure.httpStatus)
        assertEquals(1, server.requestCount)
        assertFalse(failure.toString().contains("synthetic-key"))
        assertFalse(failure.toString().contains("私密原料哨兵"))
        assertFalse(failure.toString().contains("SELECT"))
        assertNull(failure.cause)
    }

    @Test
    fun embed_redirect_doesNotForwardCredentials() = runTest {
        val other = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse().setResponseCode(302).setHeader("Location", other.url("/capture"))
            )
            val failure = captureFailure {
                client.embed(
                    settings(),
                    listOf("合成输入"),
                    MemoryEmbeddingPurpose.QUERY,
                    null
                )
            }
            assertEquals(302, failure.httpStatus)
            assertEquals(0, other.requestCount)
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun embed_cancelledRequest_propagatesCancellation() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        var cancelled = false
        val job = launch(Dispatchers.IO) {
            try {
                client.embed(settings(), listOf("合成输入"), MemoryEmbeddingPurpose.INDEX, null)
            } catch (expected: CancellationException) {
                cancelled = true
                throw expected
            }
        }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(job.isCancelled)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun embed_defaultDisabledMissingCredentialsAndWrongProtocol_makeNoRequests() = runTest {
        val configured = settings()
        listOf(
            configured.copy(
                embeddingModel = "",
                embeddingModelMode = ProviderFunctionModelMode.DISABLED
            ),
            configured.copy(embeddingModelMode = ProviderFunctionModelMode.FOLLOW_DEFAULT),
            configured.copy(enabled = false),
            configured.copy(apiKey = ""),
            configured.copy(apiProtocol = ProviderApiProtocol.ANTHROPIC),
            configured.copy(embeddingModel = ""),
        ).forEach { provider ->
            assertEquals(
                MemoryEmbeddingError.CONFIGURATION,
                captureFailure {
                    client.embed(
                        provider,
                        listOf("合成输入"),
                        MemoryEmbeddingPurpose.INDEX,
                        null
                    )
                }.code
            )
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun embed_remoteHttp_isRejectedBeforeRequest() = runTest {
        val failure = captureFailure {
            client.embed(
                settings().copy(baseUrl = "http://example.invalid/v1"),
                listOf("合成输入"),
                MemoryEmbeddingPurpose.QUERY,
                null
            )
        }
        assertEquals(MemoryEmbeddingError.CONFIGURATION, failure.code)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun embed_emptyOrOversizedInputs_areRejectedBeforeRequest() = runTest {
        listOf(
            emptyList(),
            listOf("  "),
            listOf("甲".repeat(MemoryEmbeddingChunker.MAX_INPUT_CHARS + 1)),
            List(33) { "段" })
            .forEach { input ->
                assertEquals(
                    MemoryEmbeddingError.INVALID_INPUT,
                    captureFailure {
                        client.embed(
                            settings(),
                            input,
                            MemoryEmbeddingPurpose.QUERY,
                            null
                        )
                    }.code
                )
            }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun factory_indexAndQuery_useSeparateCachedClients() {
        val provider = settings()
        val query = factory.createEmbedding(provider.baseUrl, provider.apiKey)
        val index = factory.createEmbedding(provider.baseUrl, provider.apiKey, true)
        assertNotSame(query, index)
        assertSame(query, factory.createEmbedding(provider.baseUrl, provider.apiKey))
        assertSame(index, factory.createEmbedding(provider.baseUrl, provider.apiKey, true))
    }

    private fun settings() = ProviderSettings(
        id = "embedding-provider", baseUrl = server.url("/v1/").toString().removeSuffix("/"),
        apiKey = " synthetic-key ", selectedModel = "chat-must-not-be-used",
        embeddingModel = "embedding-only", embeddingModelMode = ProviderFunctionModelMode.CUSTOM,
    )

    private fun response(items: String) = MockResponse().setBody("{\"data\":$items}")

    private suspend fun assertProtocolFailure(
        items: String,
        inputs: List<String> = listOf("合成输入"),
        dimensions: Int? = null
    ) {
        server.enqueue(response(items))
        val failure = captureFailure {
            client.embed(
                settings(),
                inputs,
                MemoryEmbeddingPurpose.QUERY,
                dimensions
            )
        }
        assertEquals(MemoryEmbeddingError.PROTOCOL, failure.code)
        assertNull(failure.cause)
    }

    private suspend fun captureFailure(block: suspend () -> Unit): MemoryEmbeddingException {
        try {
            block()
        } catch (failure: MemoryEmbeddingException) {
            return failure
        }
        throw AssertionError("应拒绝无效的向量请求或响应")
    }
}
