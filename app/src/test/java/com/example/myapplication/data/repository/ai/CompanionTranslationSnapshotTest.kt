package com.example.myapplication.data.repository.ai

import com.example.myapplication.data.remote.ApiServiceFactory
import com.example.myapplication.model.*
import com.example.myapplication.roleplay.companion.CompanionSpeechRequestKeys
import com.example.myapplication.testutil.FakeSettingsStore
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CompanionTranslationSnapshotTest {
    private lateinit var server: MockWebServer
    @Before
    fun before() {
        server = MockWebServer(); server.start()
    }

    @After
    fun after() = server.shutdown()

    @Test
    fun frozenA_afterStoreSwitchToB_keepsActualEndpointModelMessagesAndKey() = runBlocking<Unit> {
        val a = provider("a", "translator-a", "/a/chat/completions")
        val b = provider("b", "translator-b", "/b/chat/completions")
        val settings = AppSettings(providers = listOf(a), selectedProviderId = a.id)
        val store = FakeSettingsStore(settings)
        val service = DefaultAiTranslationService(
            store,
            ApiServiceFactory(),
            ioDispatcher = Dispatchers.Unconfined
        )
        val frozen = service.freeze(settings)
        val key = CompanionSpeechRequestKeys.translation(frozen, " 原文 ", " 英语 ", " 中文 ")
        store.saveProviderSettings(listOf(b), b.id)
        server.enqueue(completion("translated-a"))
        assertEquals("translated-a", service.translateText(frozen, " 原文 ", " 英语 ", " 中文 "))
        val request = server.takeRequest()
        assertEquals("/v1/a/chat/completions", request.path)
        assertEquals("Bearer fake-a", request.getHeader("Authorization"))
        val body = JsonParser.parseString(request.body.readUtf8()).asJsonObject
        assertEquals("translator-a", body["model"].asString)
        val messages = body["messages"].asJsonArray
        assertEquals("原文", messages[1].asJsonObject["content"].asString)
        assertTrue(messages[0].asJsonObject["content"].asString.contains("中文准确翻译为英语"))
        assertEquals(key, CompanionSpeechRequestKeys.translation(frozen, "原文", "英语", "中文"))
        val fresh = service.freeze(AppSettings(providers = listOf(b), selectedProviderId = b.id))
        assertNotEquals(key, CompanionSpeechRequestKeys.translation(fresh, "原文", "英语", "中文"))
        assertFalse(frozen.toString().contains("fake-a"))
    }

    @Test
    fun explicitTranslationPurposeProvider_isUsedInsteadOfActiveChatProvider() = runBlocking<Unit> {
        val chat = provider("chat", "chat-model", "/chat-path")
        val translation = provider("translation", "translation-model", "/translation-path")
        val settings = AppSettings(
            providers = listOf(chat, translation),
            selectedProviderId = chat.id,
            functionModelProviderIds = FunctionModelProviderIds(translationProviderId = translation.id)
        )
        val service = DefaultAiTranslationService(FakeSettingsStore(settings), ApiServiceFactory())
        val snapshot = service.freeze(settings)
        server.enqueue(completion("purpose translation"))
        service.translateText(snapshot, "正文", "英语", "自动检测")
        val request = server.takeRequest()
        assertEquals("/v1/translation-path", request.path)
        assertEquals("translation", snapshot.identity.providerId)
        assertEquals(
            "translation-model",
            JsonParser.parseString(request.body.readUtf8()).asJsonObject["model"].asString
        )
    }

    @Test
    fun snapshotRequests_supportChatCompletionsResponsesAndAnthropic() = runBlocking<Unit> {
        val chat = provider("chat", "model-chat", "/custom/chat")
        val responses = provider(
            "responses",
            "model-responses",
            "/ignored"
        ).copy(openAiTextApiMode = OpenAiTextApiMode.RESPONSES)
        val anthropic = provider(
            "anthropic",
            "model-anthropic",
            "/ignored"
        ).copy(apiProtocol = ProviderApiProtocol.ANTHROPIC)
        val cases = listOf(
            Triple(chat, "/v1/custom/chat", completion("chat text")),
            Triple(
                responses,
                "/v1/responses",
                MockResponse().setBody("""{"output":[{"type":"message","content":[{"type":"output_text","text":"responses text"}]}]}""")
            ),
            Triple(
                anthropic,
                "/v1/messages",
                MockResponse().setBody("""{"content":[{"type":"text","text":"anthropic text"}]}""")
            ),
        )
        cases.forEach { (provider, path, response) ->
            val settings =
                AppSettings(providers = listOf(provider), selectedProviderId = provider.id)
            val service =
                DefaultAiTranslationService(FakeSettingsStore(settings), ApiServiceFactory())
            val snapshot = service.freeze(settings)
            server.enqueue(response)
            assertTrue(service.translateText(snapshot, "原文", "英语", "自动检测").endsWith("text"))
            val request = server.takeRequest()
            assertEquals(path, request.path)
            assertEquals(server.url(path).toString(), snapshot.identity.endpoint)
            assertEquals(
                provider.translationModel,
                JsonParser.parseString(request.body.readUtf8()).asJsonObject["model"].asString
            )
            if (provider.apiProtocol == ProviderApiProtocol.ANTHROPIC) assertEquals(
                "fake-anthropic",
                request.getHeader("x-api-key")
            )
        }
    }

    @Test
    fun errorAndMissingModel_doNotExposeCredentialsOrCountAsSuccess() = runBlocking<Unit> {
        val provider = provider("failure", "translator", "/chat")
        val settings = AppSettings(providers = listOf(provider), selectedProviderId = provider.id)
        val service = DefaultAiTranslationService(FakeSettingsStore(settings), ApiServiceFactory())
        val snapshot = service.freeze(settings)
        server.enqueue(
            MockResponse().setResponseCode(401)
                .setBody("""{"api_key":"json-secret","error":"Authorization: Bearer sk-synthetic-secret-token"}""")
        )
        val failure = runCatching {
            service.translateText(
                snapshot,
                "原文",
                "英语",
                "自动检测"
            )
        }.exceptionOrNull()!!
        assertFalse(failure.message.orEmpty().contains("json-secret"))
        assertFalse(failure.message.orEmpty().contains("sk-synthetic-secret-token"))
        val disabled = provider.copy(
            translationModel = "",
            translationModelMode = ProviderFunctionModelMode.DISABLED
        )
        assertTrue(runCatching {
            service.freeze(
                AppSettings(
                    providers = listOf(disabled),
                    selectedProviderId = disabled.id
                )
            )
        }.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun newSnapshotEndpointWithEmbeddedCredential_isRejectedWithoutPersistingIt() {
        val pathKey = "synthetic-secret-token"
        val providers = listOf(
            provider(
                "query",
                "translator",
                "/chat"
            ).copy(baseUrl = server.url("/v1/?api_key=synthetic-query-secret").toString()),
            provider("path-query", "translator", "/chat?api_key=synthetic-query-secret"),
            provider("raw-base-path", "translator", "/chat").copy(
                apiKey = pathKey,
                baseUrl = "https://proxy.test/$pathKey/v1",
            ),
            provider("encoded-base-path", "translator", "/chat").copy(
                apiKey = pathKey,
                baseUrl = "https://proxy.test/synthetic%2Dsecret%2Dtoken/v1",
            ),
            provider("raw-endpoint-path", "translator", "/$pathKey/chat").copy(apiKey = pathKey),
            provider("encoded-endpoint-path", "translator", "/synthetic%2Dsecret%2Dtoken/chat").copy(apiKey = pathKey),
        )
        providers.forEach { provider ->
            val settings =
                AppSettings(providers = listOf(provider), selectedProviderId = provider.id)
            val service =
                DefaultAiTranslationService(FakeSettingsStore(settings), ApiServiceFactory())
            val failure = runCatching { service.freeze(settings) }.exceptionOrNull()!!
            assertTrue(failure is IllegalArgumentException)
            assertFalse(failure.message.orEmpty().contains("synthetic-query-secret"))
            assertFalse(failure.message.orEmpty().contains(pathKey))
        }
        val safe = provider("safe-loopback", "translator", "/chat").copy(apiKey = pathKey)
        val safeSettings = AppSettings(providers = listOf(safe), selectedProviderId = safe.id)
        val safeSnapshot = DefaultAiTranslationService(FakeSettingsStore(safeSettings), ApiServiceFactory()).freeze(safeSettings)
        assertEquals(server.url("/v1/chat").toString(), safeSnapshot.identity.endpoint)
        assertEquals(0, server.requestCount)
    }

    private fun provider(id: String, model: String, path: String) = ProviderSettings(
        id = id,
        baseUrl = server.url("/v1/").toString(),
        apiKey = "fake-$id",
        selectedModel = "chat-default",
        translationModel = model,
        translationModelMode = ProviderFunctionModelMode.CUSTOM,
        chatCompletionsPath = path,
    )

    private fun completion(text: String) =
        MockResponse().setBody("""{"choices":[{"index":0,"message":{"role":"assistant","content":"$text"}}]}""")
}
