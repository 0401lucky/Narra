package com.example.myapplication.data.repository.tts

import com.example.myapplication.model.MIMO_TTS_MODEL_PRESET
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_CLONE
import com.example.myapplication.model.MIMO_TTS_MODEL_VOICE_DESIGN
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MimoTtsClientTest {
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
    fun presetRequest_usesAssistantMessageAndPresetVoice() {
        val request = MimoTtsRequest.preset(
            text = " 晚安，早点睡。 ",
            voiceId = "冰糖",
        )

        assertEquals(MIMO_TTS_MODEL_PRESET, request.model)
        assertEquals(listOf(MimoTtsMessage("assistant", "晚安，早点睡。")), request.messages)
        assertEquals("wav", request.audio.format)
        assertEquals("冰糖", request.audio.voice)
    }

    @Test
    fun voiceDesignRequest_usesUserPromptAndAssistantText() {
        val request = MimoTtsRequest.voiceDesign(
            text = "你终于回我了。",
            prompt = "温柔、年轻、略带笑意的中文女声。",
        )

        assertEquals(MIMO_TTS_MODEL_VOICE_DESIGN, request.model)
        assertEquals("user", request.messages[0].role)
        assertEquals("温柔、年轻、略带笑意的中文女声。", request.messages[0].content)
        assertEquals("assistant", request.messages[1].role)
        assertEquals("你终于回我了。", request.messages[1].content)
        assertEquals("wav", request.audio.format)
        assertEquals(null, request.audio.voice)
    }

    @Test
    fun voiceCloneRequest_usesSampleDataUriAsVoice() {
        val request = MimoTtsRequest.voiceClone(
            text = "你终于来了。",
            sampleDataUri = "data:audio/wav;base64,UklGRg==",
        )

        assertEquals(MIMO_TTS_MODEL_VOICE_CLONE, request.model)
        assertEquals("user", request.messages[0].role)
        assertEquals("", request.messages[0].content)
        assertEquals("assistant", request.messages[1].role)
        assertEquals("你终于来了。", request.messages[1].content)
        assertEquals("wav", request.audio.format)
        assertEquals("data:audio/wav;base64,UklGRg==", request.audio.voice)
    }

    @Test
    fun synthesize_sendsAuthHeadersAndParsesAudioData() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"UklGRg=="}}}]}"""),
        )
        val client = MimoTtsClient(
            baseUrl = server.url("/v1/").toString(),
        )

        val result = client.synthesize(
            apiKey = "api-key: Bearer test-key",
            request = MimoTtsRequest.preset(
                text = "今晚月色很好。",
                voiceId = "茉莉",
            ),
        )

        assertEquals("UklGRg==", result.b64Audio)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("test-key", recorded.getHeader("api-key"))
        assertEquals("Bearer test-key", recorded.getHeader("Authorization"))

        val body = JsonParser.parseString(recorded.body.readUtf8()).asJsonObject
        assertEquals(MIMO_TTS_MODEL_PRESET, body.get("model").asString)
        assertEquals("assistant", body.getAsJsonArray("messages")[0].asJsonObject.get("role").asString)
        assertEquals("今晚月色很好。", body.getAsJsonArray("messages")[0].asJsonObject.get("content").asString)
        assertEquals("茉莉", body.getAsJsonObject("audio").get("voice").asString)
    }

    @Test
    fun synthesize_voiceDesignDoesNotSendPresetVoice() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"UklGRg=="}}}]}"""),
        )
        val client = MimoTtsClient(
            baseUrl = server.url("/v1/").toString(),
        )

        client.synthesize(
            apiKey = "test-key",
            request = MimoTtsRequest.voiceDesign(
                text = "我在。",
                prompt = "低声、沉稳、贴近耳边的中文男声。",
            ),
        )

        val body = JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(MIMO_TTS_MODEL_VOICE_DESIGN, body.get("model").asString)
        assertEquals("user", body.getAsJsonArray("messages")[0].asJsonObject.get("role").asString)
        assertEquals("assistant", body.getAsJsonArray("messages")[1].asJsonObject.get("role").asString)
        assertFalse(body.getAsJsonObject("audio").has("voice"))
    }

    @Test
    fun synthesize_usesCustomCompatibleBaseUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"UklGRg=="}}}]}"""),
        )
        val client = MimoTtsClient(
            baseUrl = "https://unused.example.com/v1",
        )

        client.synthesize(
            apiKey = "test-key",
            request = MimoTtsRequest.preset(
                text = "测试中转地址。",
                voiceId = "冰糖",
            ),
            baseUrl = server.url("/compatible/v1").toString(),
        )

        assertEquals("/compatible/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun synthesize_acceptsFullChatCompletionsEndpoint() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"audio":{"data":"UklGRg=="}}}]}"""),
        )
        val client = MimoTtsClient()

        client.synthesize(
            apiKey = "test-key",
            request = MimoTtsRequest.preset(
                text = "测试完整地址。",
                voiceId = "冰糖",
            ),
            baseUrl = server.url("/relay/v1/chat/completions/").toString(),
        )

        assertEquals("/relay/v1/chat/completions", server.takeRequest().path)
    }

    @Test
    fun synthesize_missingAudioDataThrowsFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{}}]}"""),
        )
        val client = MimoTtsClient(
            baseUrl = server.url("/v1/").toString(),
        )

        val failure = runCatching {
            client.synthesize(
                apiKey = "test-key",
                request = MimoTtsRequest.preset(
                    text = "我在。",
                    voiceId = "冰糖",
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure?.message.orEmpty().contains("MiMo 未返回音频数据"))
    }
}
