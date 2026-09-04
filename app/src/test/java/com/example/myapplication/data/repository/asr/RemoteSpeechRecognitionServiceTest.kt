package com.example.myapplication.data.repository.asr

import com.example.myapplication.model.TranscriptionProtocol
import com.example.myapplication.model.VoiceRecognitionEngine
import com.example.myapplication.model.VoiceRecognitionSettings
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RemoteSpeechRecognitionServiceTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

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
    fun recognize_successReturnsTrimmedTextAndSendsExpectedMultipartFields() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"  今天天气不错  "}"""),
        )
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Final("今天天气不错"), event)

        val recorded = server.takeRequest()
        assertEquals("/v1/audio/transcriptions", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("whisper-1"))
        assertTrue(body.contains("name=\"response_format\""))
        assertTrue(body.contains("name=\"language\""))
        assertTrue(body.contains("name=\"file\""))
    }

    @Test
    fun recognize_blankLanguageOmitsLanguageField() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":"hi"}"""))
        val service = createService()

        service.recognize(createAudioFile(), remoteSettings(language = "")).first()

        val body = server.takeRequest().body.readUtf8()
        assertFalse(body.contains("name=\"language\""))
    }

    @Test
    fun recognize_missingTextFieldFailsWithChineseMessage() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"duration":1.2}"""))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("识别服务没有返回文本内容"), event)
    }

    @Test
    fun recognize_malformedJsonFailsInsteadOfCrashing() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("这不是 JSON"))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("识别服务没有返回文本内容"), event)
    }

    @Test
    fun recognize_unauthorizedReportsCredentialProblem() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":{"message":"invalid api key"}}"""),
        )
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        val failed = event as RecognitionEvent.Failed
        assertTrue(failed.message.startsWith("语音识别鉴权失败，请检查 API Key"))
    }

    @Test
    fun recognize_notFoundReportsBaseUrlProblem() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody(""))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertEquals(
            RecognitionEvent.Failed("语音识别地址不存在，请检查 Base URL"),
            event,
        )
    }

    @Test
    fun recognize_serverErrorReportsServiceProblem() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
        val service = createService()

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        val failed = event as RecognitionEvent.Failed
        assertTrue(failed.message.startsWith("语音识别服务异常（HTTP 500）"))
    }

    @Test
    fun recognize_readTimeoutFailsWithoutThrowing() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"text":"太慢了"}""")
                .setBodyDelay(2, TimeUnit.SECONDS),
        )
        val service = createService(
            httpClient = OkHttpClient.Builder()
                .readTimeout(200, TimeUnit.MILLISECONDS)
                .build(),
        )

        val event = service.recognize(createAudioFile(), remoteSettings()).first()

        assertTrue(event is RecognitionEvent.Failed)
    }

    @Test
    fun recognize_missingAudioFileFailsBeforeAnyRequest() = runTest {
        val service = createService()

        val event = service.recognize(null, remoteSettings()).first()

        assertEquals(RecognitionEvent.Failed("没有可上传的录音文件"), event)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun recognize_blankApiKeyFailsBeforeAnyRequest() = runTest {
        val service = createService()

        val event = service
            .recognize(createAudioFile(), remoteSettings(apiKey = "   "))
            .first()

        assertEquals(RecognitionEvent.Failed("请先填写语音识别 API Key"), event)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun recognize_insecureBaseUrlFailsWithChineseMessage() = runTest {
        val service = createService()

        val event = service
            .recognize(createAudioFile(), remoteSettings(baseUrl = "http://api.example.com/v1"))
            .first()

        assertEquals(
            RecognitionEvent.Failed("语音识别 Base URL 必须使用 https://，本机调试地址除外"),
            event,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun testConnection_treatsMissingFileRejectionAsReachableEndpoint() = runTest {
        // 400/422 说明端点存在且鉴权通过，只是缺音频文件——这正是探测想要的信号
        listOf(400, 422).forEach { code ->
            server.enqueue(MockResponse().setResponseCode(code).setBody("""{"error":"no file"}"""))
            val result = createService().testConnection(remoteSettings())

            assertTrue("HTTP $code 应判为可达", result.isSuccess)
            assertTrue(result.getOrThrow().contains("鉴权通过"))
        }
    }

    @Test
    fun testConnection_successfulResponseIsAlsoReachable() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"text":""}"""))

        val result = createService().testConnection(remoteSettings())

        assertTrue(result.isSuccess)
    }

    @Test
    fun testConnection_unauthorizedAndNotFoundAreDistinguished() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody(""))
        val unauthorized = createService().testConnection(remoteSettings())
        assertEquals("鉴权失败，请检查 API Key", unauthorized.exceptionOrNull()?.message)

        server.enqueue(MockResponse().setResponseCode(404).setBody(""))
        val notFound = createService().testConnection(remoteSettings())
        assertEquals("地址不存在，请检查 Base URL", notFound.exceptionOrNull()?.message)
    }

    @Test
    fun testConnection_serverErrorSurfacesHttpDetail() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("upstream down"))

        val result = createService().testConnection(remoteSettings())

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.startsWith("语音识别服务异常（HTTP 503）"))
    }

    @Test
    fun testConnection_blankKeyOrInvalidUrlFailsBeforeAnyRequest() = runTest {
        val service = createService()

        val blankKey = service.testConnection(remoteSettings(apiKey = "  "))
        assertEquals("请先填写语音识别 API Key", blankKey.exceptionOrNull()?.message)

        val insecure = service.testConnection(remoteSettings(baseUrl = "http://api.example.com/v1"))
        assertEquals(
            "语音识别 Base URL 必须使用 https://，本机调试地址除外",
            insecure.exceptionOrNull()?.message,
        )
        assertEquals(0, server.requestCount)
    }

    @Test
    fun testConnection_neverUploadsAudioFile() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(""))

        createService().testConnection(remoteSettings())

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertFalse("探测请求不应带音频", body.contains("name=\"file\""))
    }

    @Test
    fun multimodalProtocol_postsAudioToChatCompletionsAndParsesContent() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":"  今天天气不错  "}}]}"""),
        )
        val service = createService()

        val event = service
            .recognize(createAudioFile(), multimodalSettings())
            .first()

        assertEquals(RecognitionEvent.Final("今天天气不错"), event)

        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertEquals("Bearer sk-test-key", recorded.getHeader("Authorization"))
        val body = recorded.body.readUtf8()
        assertTrue(body.contains("\"model\":\"gemini-3-flash\""))
        assertTrue(body.contains("input_audio"))
        assertTrue("音频应以 base64 内联", body.contains("\"data\""))
        assertTrue("应带转写指令", body.contains("转写"))
    }

    @Test
    fun multimodalProtocol_emptyContentIsTreatedAsFailure() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"content":""}}]}"""),
        )

        val event = createService()
            .recognize(createAudioFile(), multimodalSettings())
            .first()

        assertEquals(RecognitionEvent.Failed("识别服务没有返回文本内容"), event)
    }

    @Test
    fun transcriptionProtocol_notImplementedSuggestsSwitchingToMultimodal() = runTest {
        // 真实场景：中转无法把转写请求转成 gemini 格式，返回 not implemented
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"error":{"message":"not implemented","code":"convert_request_failed"}}"""),
        )

        val event = createService()
            .recognize(createAudioFile(), remoteSettings())
            .first()

        val message = (event as RecognitionEvent.Failed).message
        assertTrue("应提示切换到多模态对话，实际：$message", message.contains("多模态对话"))
    }

    @Test
    fun multimodalProtocol_unsupportedResponseHintsAudioOrFormatProblem() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"error":{"message":"unsupported content type"}}"""),
        )

        val event = createService()
            .recognize(createAudioFile(), multimodalSettings())
            .first()

        val message = (event as RecognitionEvent.Failed).message
        assertTrue("应提示音频或格式问题，实际：$message", message.contains("m4a"))
    }

    @Test
    fun testConnection_multimodalProtocolProbesChatCompletions() = runTest {
        server.enqueue(MockResponse().setResponseCode(400).setBody(""))

        val result = createService().testConnection(multimodalSettings())

        assertTrue(result.isSuccess)
        val recorded = server.takeRequest()
        assertEquals("/v1/chat/completions", recorded.path)
        assertFalse("探测请求不应带音频", recorded.body.readUtf8().contains("input_audio"))
    }

    @Test
    fun isAvailable_followsCredentialCompleteness() {
        val service = createService()

        assertTrue(service.isAvailable(remoteSettings()))
        assertFalse(service.isAvailable(remoteSettings(apiKey = "")))
    }

    private fun createService(
        httpClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .build(),
    ): RemoteSpeechRecognitionService {
        return RemoteSpeechRecognitionService(httpClient = httpClient)
    }

    private fun remoteSettings(
        apiKey: String = "sk-test-key",
        baseUrl: String = server.url("/v1").toString(),
        language: String = "zh",
    ): VoiceRecognitionSettings {
        return VoiceRecognitionSettings(
            enabled = true,
            engine = VoiceRecognitionEngine.REMOTE_MODEL,
            apiKey = apiKey,
            baseUrl = baseUrl,
            language = language,
        )
    }

    private fun multimodalSettings(): VoiceRecognitionSettings {
        return remoteSettings().copy(
            model = "gemini-3-flash",
            protocol = TranscriptionProtocol.CHAT_MULTIMODAL,
        )
    }

    private fun createAudioFile(): File {
        return temporaryFolder.newFile("asr-sample.m4a").apply {
            writeBytes(ByteArray(64) { it.toByte() })
        }
    }
}
